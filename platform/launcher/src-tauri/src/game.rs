//! Standalone launch with signed reconciliation and native Game Session IPC.
use md5::{Digest as _, Md5};
use serde::{Deserialize, Serialize};
use sha2::Sha256;
use std::{fs::{self, File, OpenOptions}, io::{Read, Write}, path::{Component, Path, PathBuf},
    process::{Command, Stdio}, sync::{Arc, Mutex}, thread, time::{SystemTime, UNIX_EPOCH}};

#[derive(Clone, Serialize, Debug, PartialEq)]
pub enum Phase { STARTING, RUNNING, STOPPED, FAILED }
#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct GameStatus { pub state: Phase, pub pid: Option<u32>, pub message: String, pub log_path: Option<String> }
impl Default for GameStatus {
    fn default() -> Self { Self { state: Phase::STOPPED, pid: None, message: "Готово к запуску".into(), log_path: None } }
}
#[derive(Default)]
pub struct GameRuntime {
    pub account: Mutex<Option<String>>,
    status: Arc<Mutex<GameStatus>>,
    update: crate::update::SharedProgress,
}
#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct Manifest {
    schema_version: u32, minecraft_version: String, neo_forge_version: String,
    main_class: String, libraries: Vec<Artifact>, mods: Vec<Artifact>,
    runtime_artifacts: Vec<Artifact>,
    jvm: Vec<String>, game: Vec<String>, logging: String,
}
#[derive(Deserialize)]
struct Artifact { path: String, sha256: String }

pub(crate) fn root() -> Result<PathBuf, String> {
    Ok(PathBuf::from(std::env::var_os("LOCALAPPDATA").ok_or("LOCALAPPDATA отсутствует")?).join("Recreated"))
}
fn checked_file(root: &Path, relative: &str) -> Result<PathBuf, String> {
    if relative.contains('\\') || !Path::new(relative).components().all(|c| matches!(c, Component::Normal(_))) {
        return Err("Небезопасный путь в локальном manifest".into());
    }
    let file = root.join(relative).canonicalize().map_err(|_| format!("Не найден файл сборки: {relative}"))?;
    if !file.starts_with(root.canonicalize().map_err(|_| "Instance не подготовлен")?) || !file.is_file() {
        return Err("Файл за пределами Recreated".into());
    }
    Ok(file)
}
fn verify(root: &Path, artifact: &Artifact) -> Result<PathBuf, String> {
    let file = checked_file(root, &artifact.path)?;
    let mut input = File::open(&file).map_err(|_| "Не удалось открыть файл сборки")?;
    let mut hash = Sha256::new();
    let mut buffer = [0u8; 65536];
    loop {
        let length = input.read(&mut buffer).map_err(|_| "Не удалось проверить файл сборки")?;
        if length == 0 { break; }
        hash.update(&buffer[..length]);
    }
    if format!("{:x}", hash.finalize()) != artifact.sha256 { return Err(format!("Изменён файл сборки: {}", artifact.path)); }
    Ok(file)
}
fn username_valid(name: &str) -> bool {
    // Vanilla GameProfile names have a 16-character limit; platform names may be longer.
    (3..=16).contains(&name.len()) && name.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'_')
}
pub(crate) fn offline_uuid(name: &str) -> String {
    let mut bytes: [u8; 16] = Md5::digest(format!("OfflinePlayer:{name}").as_bytes()).into();
    bytes[6] = (bytes[6] & 0x0f) | 0x30;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    uuid::Uuid::from_bytes(bytes).to_string()
}
fn argfile_quote(value: &str) -> Result<String, String> {
    if value.contains(['\r', '\n', '\0']) { return Err("Некорректный launch argument".into()); }
    Ok(format!("\"{}\"", value.replace('\\', "\\\\").replace('"', "\\\"")))
}
fn replace(value: &str, variables: &[(&str, String)]) -> Result<String, String> {
    let mut result = value.to_string();
    for (key, replacement) in variables { result = result.replace(&format!("${{{key}}}"), replacement); }
    if result.contains("${") || result.to_lowercase().contains("tlauncher") || result.starts_with("-javaagent") {
        return Err("Unsupported/local third-party launch metadata".into());
    }
    Ok(result)
}
pub(crate) fn instance_lock(root: &Path) -> std::io::Result<File> {
    let mut options = OpenOptions::new();
    options.read(true).write(true).create(true).truncate(false);
    #[cfg(windows)] {
        use std::os::windows::fs::OpenOptionsExt;
        // Exclusive OS file handle inherited as child stdin: survives launcher close.
        options.share_mode(0);
    }
    let file = options.open(root.join("game.lock"))?;
    #[cfg(not(windows))] file.try_lock().map_err(std::io::Error::other)?;
    Ok(file)
}
fn busy(error: &std::io::Error) -> bool { error.raw_os_error() == Some(32) || error.kind() == std::io::ErrorKind::WouldBlock }

impl GameRuntime {
    fn status(&self) -> GameStatus {
        let status = self.status.lock().unwrap().clone();
        if matches!(status.state, Phase::STARTING | Phase::RUNNING) { return status; }
        if let Ok(root) = root() {
            if root.is_dir() {
                if let Err(error) = instance_lock(&root) {
                    if busy(&error) { return GameStatus { state: Phase::RUNNING, message: "Minecraft уже запущен из другого окна Launcher".into(), ..status }; }
                }
            }
        }
        status
    }
    #[cfg(test)]
    fn start(&self, username:String)->Result<GameStatus,String>{self.start_authenticated(username,None)}
    fn start_authenticated(&self, username: String, authentication:Option<crate::game_session::Context>) -> Result<GameStatus, String> {
        {
            let mut status = self.status.lock().unwrap();
            if matches!(status.state, Phase::STARTING | Phase::RUNNING) { return Err("Minecraft уже запущен".into()); }
            if !username_valid(&username) {
                let message = "Minecraft требует имя аккаунта длиной 3–16 символов (A–Z, 0–9, _). Имя не будет изменено автоматически.".to_string();
                *status = GameStatus { state: Phase::FAILED, message: message.clone(), ..GameStatus::default() };
                return Err(message);
            }
            *status = GameStatus { state: Phase::STARTING, message: "Проверяем локальную сборку".into(), ..GameStatus::default() };
        }
        let result = self.spawn(username,authentication);
        if let Err(message) = &result {
            let mut status = self.status.lock().unwrap();
            *status = GameStatus { state: Phase::FAILED, message: message.clone(), ..GameStatus::default() };
        }
        result
    }
    fn spawn(&self, username: String, authentication:Option<crate::game_session::Context>) -> Result<GameStatus, String> {
        let root = root()?;
        fs::create_dir_all(&root).map_err(|_| "Не удалось создать Recreated installation")?;
        let lock = instance_lock(&root).map_err(|error| if busy(&error) { "Minecraft уже запущен" } else { "Не удалось заблокировать instance" })?;
        crate::update::reconcile(&root, &crate::update::production_settings()?, &self.update)?;
        let manifest_file = checked_file(&root, "launch.json")?;
        let manifest: Manifest = serde_json::from_slice(&fs::read(manifest_file).map_err(|_| "Не удалось прочитать manifest")?).map_err(|_| "Некорректный manifest")?;
        if manifest.schema_version != 1 || manifest.minecraft_version != "1.21.1" || manifest.neo_forge_version != "21.1.251" || manifest.main_class != "cpw.mods.bootstraplauncher.BootstrapLauncher" { return Err("Несовместимый local manifest".into()); }
        let java = checked_file(&root, "runtime/java-21/bin/java.exe")?;
        let mut check = Command::new(&java);
        #[cfg(windows)] { use std::os::windows::process::CommandExt; check.creation_flags(0x08000000); }
        let version = check.arg("-version").output().map_err(|_| "Не удалось запустить Java 21")?;
        if !version.status.success() || !String::from_utf8_lossy(&version.stderr).contains("version \"21.") { return Err("Требуется Java 21".into()); }
        let classpath = manifest.libraries.iter().map(|a| verify(&root, a).map(|p| p.to_string_lossy().into_owned())).collect::<Result<Vec<_>, _>>()?.join(";");
        for artifact in &manifest.mods { verify(&root, artifact)?; }
        for artifact in &manifest.runtime_artifacts { verify(&root, artifact)?; }
        let game = root.join("instances/alpha");
        let natives = root.join("natives/neoforge-21.1.251");
        fs::create_dir_all(&natives).map_err(|_| "Не удалось подготовить natives")?;
        fs::create_dir_all(root.join("logs")).map_err(|_| "Не удалось открыть каталог logs")?;
        let variables = [
            ("classpath", classpath), ("classpath_separator", ";".into()),
            ("library_directory", root.join("libraries").to_string_lossy().into_owned()),
            ("natives_directory", natives.to_string_lossy().into_owned()),
            ("game_directory", game.to_string_lossy().into_owned()),
            ("assets_root", root.join("assets").to_string_lossy().into_owned()),
            ("assets_index_name", "17".into()), ("launcher_name", "Recreated".into()),
            ("launcher_version", env!("CARGO_PKG_VERSION").into()),
            ("version_name", "neoforge-21.1.251".into()), ("version_type", "release".into()),
            ("auth_player_name", username.clone()), ("auth_uuid", offline_uuid(&username)),
            // Offline placeholder, NOT a platform/Microsoft credential.
            ("auth_access_token", "0".into()), ("clientid", "0".into()), ("auth_xuid", "0".into()), ("user_type", "legacy".into()),
        ];
        let settings=crate::settings::load()?;
        let mut arguments = vec!["-Xms1G".to_string(), format!("-Xmx{}G",settings.memory_gb)];
        if let Some(context)=&authentication {
            arguments.push(format!("-Drecreated.game.tlsHost={}",context.host));
            arguments.push(format!("-Drecreated.game.tlsPort={}",context.port));
        }
        for value in &manifest.jvm { arguments.push(replace(value, &variables)?); }
        arguments.push(replace(&manifest.logging, &variables)?);
        arguments.push(manifest.main_class);
        for value in &manifest.game { arguments.push(replace(value, &variables)?); }
        for flag in ["--width","--height"] {while let Some(index)=arguments.iter().position(|v|v==flag){arguments.remove(index);if index<arguments.len(){arguments.remove(index);}}}
        arguments.extend(["--width".into(),settings.width.to_string(),"--height".into(),settings.height.to_string()]);
        let stamp = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis();
        let argument_path = root.join("logs").join(format!("launch-{stamp}.args"));
        let mut file = File::create(&argument_path).map_err(|_| "Не удалось создать Java argument file")?;
        for argument in arguments { writeln!(file, "{}", argfile_quote(&argument)?).map_err(|_| "Не удалось сохранить launch arguments")?; }
        drop(file);
        let log_path = root.join("logs").join(format!("game-{stamp}.log"));
        let output = File::create(&log_path).map_err(|_| "Не удалось создать game log")?;
        let mut command = Command::new(java);
        command.arg(format!("@{}", argument_path.display())).current_dir(game)
            .stdin(Stdio::piped()).stderr(Stdio::piped()).stdout(Stdio::piped());
        // Never inherit launcher credentials or JVM injection options.
        for key in ["JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH"] { command.env_remove(key); }
        #[cfg(windows)] { use std::os::windows::process::CommandExt; command.creation_flags(0x08000000); }
        let mut child = command.spawn().map_err(|_| "Не удалось запустить Minecraft; проверьте Java и logs")?;
        drop(command);
        if let Err(error)=crate::game_lock::retain(&lock,child.id()){let _=child.kill();let _=child.wait();return Err(error);}
        let stdout=child.stdout.take().ok_or("Не удалось открыть вывод игры")?;
        let stderr=child.stderr.take().ok_or("Не удалось открыть вывод игры")?;
        let mut input=child.stdin.take().ok_or("Не удалось открыть канал входа")?;
        let log=Arc::new(Mutex::new(output));let error_log=log.clone();
        thread::spawn(move||{let mut stream=stderr;let mut buffer=[0;4096];while let Ok(n)=stream.read(&mut buffer){if n==0{break;}if let Ok(mut file)=error_log.lock(){let _=file.write_all(&buffer[..n]);}}});
        let auth_status=self.status.clone();
        thread::spawn(move||{
            use std::io::{BufRead,BufReader};
            let mut authentication=authentication;
            let mut reader=BufReader::new(stdout);let mut line=Vec::new();
            loop {
                line.clear();
                // Limit each captured line. Do not allocate unbounded IPC/log input.
                let mut complete=false;
                while line.len()<16384 {
                    match reader.fill_buf(){Ok(bytes) if bytes.is_empty()=>{complete=true;break;},Ok(bytes)=>{
                        let take=bytes.iter().position(|b|*b==b'\n').map(|n|n+1).unwrap_or(bytes.len()).min(16384-line.len());
                        complete=bytes.get(take.saturating_sub(1))==Some(&b'\n');line.extend_from_slice(&bytes[..take]);reader.consume(take);if complete{break;}
                    },Err(_)=>{complete=true;break;}}
                }
                if line.is_empty(){break;}
                let text=String::from_utf8_lossy(&line);
                if crate::game_session::Context::is_protocol_line(&text){
                    if let Some(context)=authentication.as_mut(){if let Err(error)=context.respond(&text,&mut input){auth_status.lock().unwrap().message=error;}}
                }else if let Ok(mut file)=log.lock(){let _=file.write_all(&line);}
                if !complete {continue;}
            }
        });
        let running = GameStatus { state: Phase::RUNNING, pid: Some(child.id()), message: "Minecraft запущен".into(), log_path: Some(log_path.to_string_lossy().into_owned()) };
        *self.status.lock().unwrap() = running.clone();
        let status = self.status.clone();
        thread::spawn(move || {
            // Ownership of both child and exclusive handle is retained until exit.
            let _lock = lock;
            let exit = child.wait();
            let mut state = status.lock().unwrap();
            state.pid = None;
            match exit {
                Ok(code) if code.success() => { state.state = Phase::STOPPED; state.message = "Minecraft остановлен".into(); }
                Ok(code) => { state.state = Phase::FAILED; state.message = format!("Minecraft завершился с ошибкой ({code}). См. game log."); }
                Err(_) => { state.state = Phase::FAILED; state.message = "Не удалось получить состояние Minecraft".into(); }
            }
            let _ = fs::remove_file(argument_path);
        });
        Ok(running)
    }
}

#[tauri::command]
pub fn game_status(runtime: tauri::State<'_, GameRuntime>) -> GameStatus { runtime.status() }
#[tauri::command]
pub fn game_logout(runtime: tauri::State<'_, GameRuntime>) { *runtime.account.lock().unwrap() = None; }
#[tauri::command]
pub fn update_status(runtime: tauri::State<'_, GameRuntime>) -> crate::update::Progress { runtime.update.lock().unwrap().clone() }
#[tauri::command]
pub async fn game_prepare(runtime: tauri::State<'_, GameRuntime>) -> Result<crate::update::Progress, String> {
    if runtime.account.lock().unwrap().is_none() { return Err("Сначала войдите в аккаунт Launcher".into()); }
    {
        let mut status = runtime.status.lock().unwrap();
        if matches!(status.state, Phase::STARTING | Phase::RUNNING) { return Err("Игра или обновление уже запущены".into()); }
        *status = GameStatus { state: Phase::STARTING, message: "Проверка обновлений".into(), ..GameStatus::default() };
    }
    let shared = runtime.status.clone(); let progress = runtime.update.clone();
    tauri::async_runtime::spawn_blocking(move || {
        let result = (|| {
            let root = root()?;
            fs::create_dir_all(&root).map_err(|_| "Не удалось создать Recreated")?;
            let _lock = instance_lock(&root).map_err(|_| "Minecraft запущен; обновление запрещено")?;
            crate::update::reconcile(&root, &crate::update::production_settings()?, &progress)
        })();
        let mut state = shared.lock().unwrap();
        match result {
            Ok(_) => { *state = GameStatus::default(); Ok(progress.lock().unwrap().clone()) },
            Err(message) => { *state = GameStatus { state: Phase::FAILED, message: message.clone(), ..GameStatus::default() }; Err(message) },
        }
    }).await.map_err(|_| "Updater task failed".to_string())?
}
#[tauri::command]
pub async fn game_start(app:tauri::AppHandle,runtime: tauri::State<'_, GameRuntime>) -> Result<GameStatus, String> {
    use tauri::Manager;
    let hide_after_launch=crate::settings::load()?.hide_after_launch;
    let account_username=runtime.account.lock().unwrap().clone().ok_or("Сначала войдите в аккаунт Launcher")?;
    let shared = runtime.status.clone();
    let progress = runtime.update.clone();
    let result=tauri::async_runtime::spawn_blocking(move || {
        let authentication=crate::game_session::Context::authorize(&account_username)?;
        let username=authentication.join_name().to_owned();
        GameRuntime { account: Mutex::new(None), status: shared, update: progress }.start_authenticated(username,Some(authentication))
    })
        .await.map_err(|_| "Не удалось начать запуск".to_string())?;
    if hide_after_launch && result.is_ok(){if let Some(window)=app.get_webview_window("main"){let _=window.hide();}}
    result
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn offline_id_is_java_name_uuid_not_platform_id() {
        assert_eq!(offline_uuid("Notch"), "b50ad385-829d-3141-a216-7e7d7539ba7f");
        assert_ne!(offline_uuid("Notch"), offline_uuid("notch"));
    }
    #[test]
    fn name_and_argument_boundaries() {
        assert!(username_valid("drannch1k"));
        for name in ["ab", "name with space", "seventeen_chars_1", "../name", "Имя"] { assert!(!username_valid(name)); }
        assert!(argfile_quote("a\nb").is_err());
        assert_eq!(argfile_quote("C:\\a b\\c.jar").unwrap(), "\"C:\\\\a b\\\\c.jar\"");
        assert!(replace("${unknown}", &[]).is_err());
        assert!(replace("-javaagent:evil.jar", &[]).is_err());
        let runtime = GameRuntime::default();
        assert!(runtime.start("seventeen_chars_1".into()).is_err());
        assert_eq!(runtime.status.lock().unwrap().state, Phase::FAILED);
    }
    #[test]
    fn traversal_and_parallel_instance_lock_are_rejected() {
        let directory = std::env::temp_dir().join(format!("recreated-test-{}", SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_nanos()));
        fs::create_dir(&directory).unwrap();
        assert!(checked_file(&directory, "../secret").is_err());
        assert!(checked_file(&directory, "C:/secret").is_err());
        let lock = instance_lock(&directory).unwrap();
        assert!(instance_lock(&directory).is_err());
        drop(lock);
        assert!(instance_lock(&directory).is_ok());
        fs::remove_dir_all(directory).unwrap();
    }
    #[test]
    #[ignore = "Manual prepared-PC smoke: launches real Minecraft, no network account fixture"]
    fn prepared_instance_smoke() {
        let runtime = GameRuntime::default();
        let result = runtime.start("drannch1k".into()).unwrap();
        assert_eq!(result.state, Phase::RUNNING);
        assert!(runtime.start("drannch1k".into()).is_err());
        // Keep parent alive while human inspects main menu; never connects to VDS.
        for _ in 0..600 {
            thread::sleep(std::time::Duration::from_secs(1));
            let status = runtime.status();
            assert_ne!(status.state, Phase::FAILED, "{}; log: {:?}", status.message, status.log_path);
            if status.state != Phase::RUNNING { break; }
        }
    }
    #[test]
    fn modified_artifact_fails_before_java_spawn() {
        let directory = std::env::temp_dir().join(format!("recreated-hash-{}", SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_nanos()));
        fs::create_dir(&directory).unwrap();
        fs::write(directory.join("mod.jar"), b"expected").unwrap();
        let artifact = Artifact { path: "mod.jar".into(), sha256: format!("{:x}", Sha256::digest(b"expected")) };
        assert!(verify(&directory, &artifact).is_ok());
        fs::write(directory.join("mod.jar"), b"modified").unwrap();
        assert!(verify(&directory, &artifact).is_err());
        fs::remove_dir_all(directory).unwrap();
    }
}
