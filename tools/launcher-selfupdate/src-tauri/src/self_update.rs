//! Launcher-only updater. Never writes game files or Windows Credential Manager.
use ed25519_dalek::{Signature, VerifyingKey};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::{fs::{self, File, OpenOptions}, io::{Read, Write}, path::{Path, PathBuf}, process::{Command, Stdio}, sync::Mutex, time::Duration};

const ORIGIN: &str = "https://api.recreated.ru";
const URL: &str = "https://api.recreated.ru/launcher/latest/update.json";
const MAX_INSTALLER: u64 = 512 * 1024 * 1024;
#[derive(Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct Envelope { manifest: serde_json::Value, signature: String }
#[derive(Clone, Serialize, Deserialize, Debug)]
#[serde(rename_all="camelCase", deny_unknown_fields)]
struct Manifest { schema_version: u32, kind: String, version: String, sequence: u64, installer_url: String, size: u64, sha256: String, mandatory: bool, approved: bool }
#[derive(Default)]
pub struct Runtime { state: Mutex<Inner> }
#[derive(Default)]
struct Inner { busy: bool, candidate: Option<(Envelope, Manifest)>, downloaded: Option<PathBuf> }
#[derive(Serialize)]
#[serde(rename_all="camelCase")]
pub struct Info { version: &'static str, build_id: &'static str, api: &'static str, game_tls: String }
#[derive(Serialize)]
#[serde(rename_all="camelCase")]
pub struct Status { available: bool, version: String, mandatory: bool, message: String }
#[derive(Serialize, Deserialize, Default)]
#[serde(deny_unknown_fields)]
struct Receipt { sequence: u64, version: String }
#[derive(Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct Job { envelope: Envelope, target: PathBuf, parent: u32, original_hash: String }

fn hex<const N: usize>(s:&str)->Result<[u8;N],String>{
    if s.len()!=N*2 || !s.bytes().all(|b|b.is_ascii_digit() || (b'a'..=b'f').contains(&b)){return Err("Некорректная подпись обновления".into());}
    let mut out=[0;N]; for (i,b) in out.iter_mut().enumerate(){*b=u8::from_str_radix(&s[i*2..i*2+2],16).map_err(|_|"Некорректная подпись")?;} Ok(out)
}
fn verify(envelope:&Envelope, current:&str, receipt:&Receipt)->Result<Manifest,String>{
    verify_key(envelope,current,receipt,&hex::<32>(include_str!("../release-public-key.hex").trim())?)
}
fn verify_key(envelope:&Envelope,current:&str,receipt:&Receipt,public_key:&[u8;32])->Result<Manifest,String>{
    verify_policy(envelope,current,receipt,public_key,cfg!(feature="self-update-test"))
}
fn verify_policy(envelope:&Envelope,current:&str,receipt:&Receipt,public_key:&[u8;32],allow_candidate:bool)->Result<Manifest,String>{
    let key=VerifyingKey::from_bytes(public_key).map_err(|_|"Ошибка ключа подписи")?;
    key.verify_strict(&serde_json::to_vec(&envelope.manifest).map_err(|_|"Ошибка manifest")?,&Signature::from_bytes(&hex::<64>(&envelope.signature)?))
        .map_err(|_|"Подпись обновления Launcher не прошла проверку")?;
    let m:Manifest=serde_json::from_value(envelope.manifest.clone()).map_err(|_|"Некорректный manifest Launcher")?;
    let version=semver::Version::parse(&m.version).map_err(|_|"Некорректная версия")?;
    let current=semver::Version::parse(current).map_err(|_|"Некорректная установленная версия")?;
    if (!m.approved && !allow_candidate) || m.schema_version!=1 || m.kind!="launcher" || m.sequence==0 || m.sequence<receipt.sequence || version<current ||
       (m.sequence==receipt.sequence && !receipt.version.is_empty() && m.version!=receipt.version) ||
       (!receipt.version.is_empty() && version<semver::Version::parse(&receipt.version).map_err(|_|"Повреждена история обновлений")?) ||
       m.installer_url!=format!("{ORIGIN}/launcher/versions/{}/RE-CREATED-Launcher-x64-setup.exe",m.version) ||
       m.size<2 || m.size>MAX_INSTALLER || hex::<32>(&m.sha256).is_err(){return Err("Небезопасное или устаревшее обновление Launcher".into());}
    Ok(m)
}
fn safe(path:&Path)->Result<(),String>{
    for p in path.ancestors(){if let Ok(meta)=fs::symlink_metadata(p){
        if meta.file_type().is_symlink(){return Err("Небезопасный путь обновления".into());}
        #[cfg(windows)] {use std::os::windows::fs::MetadataExt;if meta.file_attributes() & 0x400!=0{return Err("Небезопасный путь обновления".into());}}
    }} Ok(())
}
fn root()->Result<PathBuf,String>{
    let root=PathBuf::from(std::env::var_os("LOCALAPPDATA").ok_or("LOCALAPPDATA отсутствует")?).join("RE-CREATED Launcher Updater");
    safe(&root)?;fs::create_dir_all(&root).map_err(|_|"Не удалось создать каталог обновления")?;Ok(root)
}
fn bytes(path:&Path,data:&[u8])->Result<(),String>{
    safe(path)?;let tmp=path.with_extension("next");safe(&tmp)?;
    let mut f=OpenOptions::new().create_new(true).write(true).open(&tmp).map_err(|_|"Не удалось записать обновление")?;
    f.write_all(data).and_then(|_|f.sync_all()).map_err(|_|"Не удалось сохранить обновление")?;drop(f);
    fs::rename(tmp,path).map_err(|_|"Не удалось завершить запись обновления".into())
}
fn receipt(root:&Path)->Result<Receipt,String>{
    let p=root.join("receipt.json");safe(&p)?;
    match fs::read(p){Ok(b)=>serde_json::from_slice(&b).map_err(|_|"Повреждена история обновлений".into()),Err(e) if e.kind()==std::io::ErrorKind::NotFound=>Ok(Receipt::default()),Err(_)=>Err("Не удалось прочитать историю обновлений".into())}
}
fn hash(path:&Path)->Result<(u64,String),String>{
    safe(path)?;let mut f=File::open(path).map_err(|_|"Не удалось открыть installer")?;let mut h=Sha256::new();let mut size=0;let mut buf=[0;65536];
    loop{let n=f.read(&mut buf).map_err(|_|"Ошибка проверки installer")?;if n==0{break;}size+=n as u64;if size>MAX_INSTALLER{return Err("Installer слишком большой".into());}h.update(&buf[..n]);}Ok((size,format!("{:x}",h.finalize())))
}
fn client()->Result<reqwest::blocking::Client,String>{reqwest::blocking::Client::builder().https_only(true).redirect(reqwest::redirect::Policy::none()).connect_timeout(Duration::from_secs(15)).timeout(Duration::from_secs(300)).build().map_err(|_|"Не удалось подключиться к обновлениям".into())}
fn endpoint()->&'static str{
    #[cfg(feature="self-update-test")] {return env!("RECREATED_SELF_UPDATE_TEST_URL");}
    #[cfg(not(feature="self-update-test"))] {URL}
}
#[tauri::command]
pub fn launcher_build_info()->Info{Info{version:env!("CARGO_PKG_VERSION"),build_id:env!("RECREATED_BUILD_ID"),api:option_env!("VITE_ACCOUNT_API_BASE_URL").unwrap_or("development"),game_tls:format!("{}:{}",option_env!("RECREATED_GAME_TLS_HOST").unwrap_or("development"),option_env!("RECREATED_GAME_TLS_PORT").unwrap_or("25565"))}}
#[tauri::command]
pub async fn launcher_update_check(runtime:tauri::State<'_,Runtime>)->Result<Status,String>{
    {let s=runtime.state.lock().map_err(|_|"Ошибка обновления")?;if s.busy{return Err("Обновление уже выполняется".into());}}
    let result=tauri::async_runtime::spawn_blocking(||{
        let root=root()?;let mut response=client()?.get(endpoint()).send().and_then(|r|r.error_for_status()).map_err(|_|"Проверка обновления недоступна; повторите позже")?;
        let mut b=Vec::new();response.by_ref().take(65537).read_to_end(&mut b).map_err(|_|"Не удалось прочитать manifest")?;if b.len()>65536{return Err("Manifest слишком большой".into());}
        let e:Envelope=serde_json::from_slice(&b).map_err(|_|"Некорректный manifest Launcher")?;let r=receipt(&root)?;let m=verify(&e,env!("CARGO_PKG_VERSION"),&r)?;
        bytes(&root.join("receipt.json"),&serde_json::to_vec(&Receipt{sequence:m.sequence,version:m.version.clone()}).unwrap())?;Ok::<_,String>((e,m))
    }).await.map_err(|_|"Ошибка проверки обновления")??;
    let available=result.1.version!=env!("CARGO_PKG_VERSION");let status=Status{available,version:result.1.version.clone(),mandatory:result.1.mandatory,message:if available{"Доступна новая версия Launcher".into()}else{"Launcher обновлён".into()}};
    let mut s=runtime.state.lock().map_err(|_|"Ошибка обновления")?;s.candidate=Some(result);s.downloaded=None;Ok(status)
}
#[tauri::command]
pub async fn launcher_update_download(runtime:tauri::State<'_,Runtime>)->Result<String,String>{
    let (e,m)={let mut s=runtime.state.lock().map_err(|_|"Ошибка обновления")?;if s.busy{return Err("Обновление уже выполняется".into());}let candidate=s.candidate.clone().ok_or("Сначала проверьте обновление")?;s.busy=true;candidate};
    let result=tauri::async_runtime::spawn_blocking(move ||{
        let root=root()?;verify(&e,env!("CARGO_PKG_VERSION"),&receipt(&root)?)?;
        let dir=root.join(format!("job-{}",uuid::Uuid::new_v4()));fs::create_dir(&dir).map_err(|_|"Не удалось подготовить обновление")?;
        let partial=dir.join("installer.partial");let mut out=OpenOptions::new().write(true).create_new(true).open(&partial).map_err(|_|"Не удалось скачать installer")?;
        let mut response=client()?.get(&m.installer_url).send().and_then(|r|r.error_for_status()).map_err(|_|"Не удалось скачать обновление")?;
        let n=std::io::copy(&mut response.by_ref().take(m.size+1),&mut out).map_err(|_|"Загрузка прервана; рабочий Launcher сохранён")?;out.sync_all().map_err(|_|"Ошибка сохранения installer")?;drop(out);
        if n!=m.size || hash(&partial)?!=(m.size,m.sha256){return Err("SHA-256 обновления не совпадает; установка отменена".into());}
        fs::rename(partial,dir.join("installer.exe")).map_err(|_|"Ошибка подготовки installer")?;
        bytes(&dir.join("envelope.json"),&serde_json::to_vec(&e).unwrap())?;Ok::<_,String>(dir)
    }).await.map_err(|_|String::from("Ошибка загрузки")).and_then(|r|r);
    let mut s=runtime.state.lock().map_err(|_|"Ошибка обновления")?;s.busy=false;s.downloaded=result.as_ref().ok().cloned();result.map(|_|"Обновление проверено. Закройте Minecraft и установите Launcher.".into())
}
#[tauri::command]
pub fn launcher_update_install(app:tauri::AppHandle,runtime:tauri::State<'_,Runtime>)->Result<(),String>{
    // Exclusive game lock also covers Java launched by a previous Launcher process.
    let _game=idle_game()?;
    let mut s=runtime.state.lock().map_err(|_|"Ошибка обновления")?;if s.busy{return Err("Обновление уже выполняется".into());}
    let dir=s.downloaded.as_ref().ok_or("Сначала скачайте обновление")?;
    let target=std::env::current_exe().map_err(|_|"Не найден Launcher")?;check_target(&target)?;
    let e:Envelope=serde_json::from_slice(&fs::read(dir.join("envelope.json")).map_err(|_|"Не найден manifest")?).map_err(|_|"Некорректный manifest")?;
    let m=verify(&e,env!("CARGO_PKG_VERSION"),&receipt(&root()?)?)?;
    if m.version==env!("CARGO_PKG_VERSION") || hash(&dir.join("installer.exe"))?!=(m.size,m.sha256){return Err("Обновление не прошло проверку".into());}
    let original_hash=hash(&target)?.1;
    fs::copy(&target,dir.join("previous.exe")).map_err(|_|"Не удалось сохранить рабочий Launcher")?;
    fs::copy(&target,dir.join("helper.exe")).map_err(|_|"Не удалось подготовить helper")?;
    bytes(&dir.join("job.json"),&serde_json::to_vec(&Job{envelope:e,target,parent:std::process::id(),original_hash}).unwrap())?;
    let mut cmd=Command::new(dir.join("helper.exe"));cmd.arg("--recreated-self-update-helper").stdin(Stdio::null()).stdout(Stdio::null()).stderr(Stdio::null());
    #[cfg(windows)] {use std::os::windows::process::CommandExt;cmd.creation_flags(0x08000000);}
    cmd.spawn().map_err(|_|"Не удалось запустить helper; Launcher не изменён")?;s.busy=true;app.exit(0);Ok(())
}
fn check_target(target:&Path)->Result<(),String>{
    safe(target)?;let base=PathBuf::from(std::env::var_os("LOCALAPPDATA").ok_or("LOCALAPPDATA отсутствует")?).join("RE-CREATED Launcher");
    if target!=base.join("create-server-launcher.exe"){return Err("Self-update доступен только установленному Launcher".into());}Ok(())
}
fn idle_game()->Result<Option<File>,String>{
    let dir=crate::game::root()?;safe(&dir)?;
    if !dir.exists(){return Ok(None);}
    crate::game::instance_lock(&dir).map(Some).map_err(|_|"Сначала закройте Minecraft".into())
}
#[tauri::command]
pub fn launcher_update_ready()->Result<(),String>{
    let target=std::env::current_exe().map_err(|_|"Не найден Launcher")?;
    let Some(dir)=std::env::args().find_map(|a|a.strip_prefix("--recreated-update-ready=").map(PathBuf::from))else{return Ok(());};
    safe(&dir)?;if dir.parent()!=Some(root()?.as_path()){return Err("Некорректное состояние обновления".into());}
    let job:Job=serde_json::from_slice(&fs::read(dir.join("job.json")).map_err(|_|"Нет состояния обновления")?).map_err(|_|"Некорректное состояние")?;
    // Readiness is not an install/check authority. Accept a signed candidate only
    // to acknowledge an explicit test-helper upgrade to this exact app/version.
    let m=verify_policy(&job.envelope,job.envelope.manifest["version"].as_str().unwrap_or(""),&receipt(&root()?)?,&hex::<32>(include_str!("../release-public-key.hex").trim())?,true)?;
    if target!=job.target || m.version!=env!("CARGO_PKG_VERSION"){return Err("Версия после установки не совпадает".into());}
    bytes(&dir.join("ready"),m.version.as_bytes())
}
pub fn helper()->bool{
    if std::env::args().any(|a|a=="--recreated-build-info") {println!("{}",serde_json::to_string(&launcher_build_info()).unwrap());return true;}
    if !std::env::args().any(|a|a=="--recreated-self-update-helper"){return false;}
    let result=run_helper();if let Err(error)=result{if let Ok(exe)=std::env::current_exe(){if let Some(dir)=exe.parent(){let _=fs::write(dir.join("failure.txt"),error);}}}true
}
fn run_helper()->Result<(),String>{
    let exe=std::env::current_exe().map_err(|_|"Не найден helper")?;let dir=exe.parent().ok_or("Нет helper directory")?;safe(dir)?;
    if dir.parent()!=Some(root()?.as_path()) || exe.file_name().and_then(|s|s.to_str())!=Some("helper.exe"){return Err("Небезопасный helper".into());}
    let job:Job=serde_json::from_slice(&fs::read(dir.join("job.json")).map_err(|_|"Нет job")?).map_err(|_|"Некорректный job")?;check_target(&job.target)?;
    let m=verify(&job.envelope,env!("CARGO_PKG_VERSION"),&receipt(&root()?)?)?;
    if hash(&dir.join("installer.exe"))?!=(m.size,m.sha256.clone()) || hash(&dir.join("previous.exe"))?.1!=job.original_hash{return Err("Проверка helper файлов не прошла".into());}
    #[cfg(windows)] unsafe{
        use windows_sys::Win32::{Foundation::CloseHandle,System::Threading::{OpenProcess,WaitForSingleObject,PROCESS_SYNCHRONIZE}};
        let p=OpenProcess(PROCESS_SYNCHRONIZE,0,job.parent);if !p.is_null(){let result=WaitForSingleObject(p,60000);CloseHandle(p);if result!=0{return Err("Launcher не завершился; установка отменена".into());}}
    }
    let _game=idle_game()?;
    // Only NSIS current-user installer; arguments follow the official NSIS contract.
    let mut cmd=Command::new(dir.join("installer.exe"));cmd.args(["/S","/UPDATE"]);
    #[cfg(windows)] {use std::os::windows::process::CommandExt;cmd.raw_arg(format!("/D={}",job.target.parent().unwrap().display()));}
    let installed=cmd.status().map(|s|s.success()).unwrap_or(false);
    if !installed{return rollback(dir,&job,"Installer завершился с ошибкой");}
    let mut new_app=match Command::new(&job.target).arg(format!("--recreated-update-ready={}",dir.display())).spawn(){Ok(child)=>child,Err(_)=>return rollback(dir,&job,"Новая версия не запустилась")};
    let deadline=std::time::Instant::now()+Duration::from_secs(90);
    loop{
        if fs::read_to_string(dir.join("ready")).ok().as_deref()==Some(&m.version){return Ok(());}
        if new_app.try_wait().map_err(|_|"Ошибка запуска")?.is_some() || std::time::Instant::now()>deadline{
            let _=new_app.kill();let _=new_app.wait();return rollback(dir,&job,"Новая версия не подтвердила запуск");
        }std::thread::sleep(Duration::from_millis(250));
    }
}
fn rollback(dir:&Path,job:&Job,error:&str)->Result<(),String>{
    check_target(&job.target)?;if hash(&dir.join("previous.exe"))?.1!=job.original_hash{return Err("Backup повреждён; требуется ручное восстановление".into());}
    fs::copy(dir.join("previous.exe"),&job.target).map_err(|_|"Не удалось восстановить Launcher; backup сохранён")?;
    let _=Command::new(&job.target).spawn();Err(format!("{error}. Восстановлен предыдущий Launcher; данные игры и авторизация не затронуты."))
}

#[cfg(test)] mod tests{
    use super::*;use ed25519_dalek::{Signer,SigningKey};
    fn signed(mut value:serde_json::Value)->Envelope{ // test verifies production validator using independent generated trust root below
        value["kind"]=serde_json::json!("launcher");Envelope{manifest:value,signature:"00".repeat(64)}
    }
    #[test] fn rejects_unsigned_manifest(){let e=signed(serde_json::json!({"version":"9.9.9"}));assert!(verify(&e,"0.1.2",&Receipt::default()).is_err());}
    #[test] fn canonical_json_stable(){let a:serde_json::Value=serde_json::from_str("{\"b\":2,\"a\":1}").unwrap();assert_eq!(serde_json::to_string(&a).unwrap(),"{\"a\":1,\"b\":2}");let k=SigningKey::from_bytes(&[7;32]);let s=k.sign(&serde_json::to_vec(&a).unwrap());assert!(k.verifying_key().verify_strict(&serde_json::to_vec(&a).unwrap(),&s).is_ok());}
    #[test] fn rejects_invalid_hex(){assert!(hex::<32>(&"A".repeat(64)).is_err());assert!(hex::<32>("00").is_err());}
    #[test] fn hash_checks_content(){let p=std::env::temp_dir().join(format!("launcher-hash-{}",uuid::Uuid::new_v4()));fs::write(&p,b"abc").unwrap();assert_eq!(hash(&p).unwrap(),(3,"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad".into()));fs::remove_file(p).unwrap();}
    fn candidate()->(Envelope,SigningKey){let key=SigningKey::from_bytes(&[9;32]);let m=Manifest{schema_version:1,kind:"launcher".into(),version:"0.1.2".into(),sequence:2,installer_url:format!("{ORIGIN}/launcher/versions/0.1.2/RE-CREATED-Launcher-x64-setup.exe"),size:20,sha256:"ab".repeat(32),mandatory:true,approved:true};let value=serde_json::to_value(m).unwrap();let sig=key.sign(&serde_json::to_vec(&value).unwrap());(Envelope{manifest:value,signature:sig.to_bytes().iter().map(|b|format!("{b:02x}")).collect()},key)}
    fn resign(e:&mut Envelope,key:&SigningKey){e.signature=key.sign(&serde_json::to_vec(&e.manifest).unwrap()).to_bytes().iter().map(|b|format!("{b:02x}")).collect();}
    #[test] fn verified_upgrade_and_same_version(){let(e,k)=candidate();assert!(verify_key(&e,"0.1.1",&Receipt::default(),&k.verifying_key().to_bytes()).unwrap().mandatory);assert!(verify_key(&e,"0.1.2",&Receipt{sequence:2,version:"0.1.2".into()},&k.verifying_key().to_bytes()).is_ok());}
    #[test] fn rejects_downgrade_sequence_and_version(){let(e,k)=candidate();let p=k.verifying_key().to_bytes();assert!(verify_key(&e,"0.1.3",&Receipt::default(),&p).is_err());assert!(verify_key(&e,"0.1.1",&Receipt{sequence:3,version:"0.1.2".into()},&p).is_err());assert!(verify_key(&e,"0.1.1",&Receipt{sequence:2,version:"0.1.3".into()},&p).is_err());}
    #[test] fn rejects_tampering_and_untrusted_origin(){let(mut e,k)=candidate();e.manifest["mandatory"]=serde_json::json!(false);assert!(verify_key(&e,"0.1.1",&Receipt::default(),&k.verifying_key().to_bytes()).is_err());resign(&mut e,&k);e.manifest["installerUrl"]=serde_json::json!("http://api.recreated.ru/evil.exe");resign(&mut e,&k);assert!(verify_key(&e,"0.1.1",&Receipt::default(),&k.verifying_key().to_bytes()).is_err());}
    #[test] fn rejects_wrong_key_and_oversize(){let(mut e,k)=candidate();assert!(verify_key(&e,"0.1.1",&Receipt::default(),&SigningKey::from_bytes(&[8;32]).verifying_key().to_bytes()).is_err());e.manifest["size"]=serde_json::json!(MAX_INSTALLER+1);resign(&mut e,&k);assert!(verify_key(&e,"0.1.1",&Receipt::default(),&k.verifying_key().to_bytes()).is_err());}
    #[test] fn production_rejects_signed_unapproved_candidate(){let(mut e,k)=candidate();e.manifest["approved"]=serde_json::json!(false);resign(&mut e,&k);assert!(verify_policy(&e,"0.1.1",&Receipt::default(),&k.verifying_key().to_bytes(),false).is_err());assert!(verify_policy(&e,"0.1.1",&Receipt::default(),&k.verifying_key().to_bytes(),true).is_ok());}
}
