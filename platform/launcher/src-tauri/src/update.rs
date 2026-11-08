//! Signed release reconciliation. File ownership is explicit, never directory sweeping.
mod cache;
mod download;
use ed25519_dalek::{Signature, VerifyingKey};
use reqwest::{blocking::Client, redirect::Policy, Url};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::{collections::HashSet, fs::{self, File, OpenOptions}, io::{Read, Write}, path::{Path, PathBuf}, sync::{Arc, Mutex}, time::Duration};

#[derive(Clone, Default, Serialize, Debug)]
#[serde(rename_all = "camelCase")]
pub struct Progress {
    pub phase: String, pub version: Option<String>, pub checked_files: u64,
    pub total_files: u64, pub completed_files: u64, pub changed_files: u64,
    pub downloaded_bytes: u64, pub total_bytes: u64, pub message: String,
    pub cached_files:u64,pub verified_bytes:u64,
}
pub type SharedProgress = Arc<Mutex<Progress>>;
#[derive(Clone, Deserialize, Serialize)]
#[serde(deny_unknown_fields)]
pub struct Envelope { pub manifest: serde_json::Value, pub signature: String }
#[derive(Clone, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Manifest {
    schema_version: u32, pub version: String, sequence: u64, platform: String,
    minecraft_version: String, neo_forge_version: String, files: Vec<Entry>,
}
#[derive(Clone, Deserialize, Serialize)]
#[serde(deny_unknown_fields)]
struct Entry { path: String, size: u64, sha256: String, url: String, mode: String }
pub struct Settings { pub manifest_url: Url, pub key: [u8; 32] }
pub fn production_settings() -> Result<Settings, String> {
    let manifest_url = Url::parse(option_env!("RECREATED_RELEASE_MANIFEST_URL").unwrap_or("https://api.recreated.ru/releases/channels/alpha/manifest.json")).map_err(|_| "Некорректный release URL")?;
    Ok(Settings { manifest_url, key: unhex(include_str!("../release-public-key.hex").trim())? })
}
fn unhex<const N: usize>(text: &str) -> Result<[u8; N], String> {
    if text.len() != N * 2 || !text.bytes().all(|c| c.is_ascii_hexdigit() && !c.is_ascii_uppercase()) { return Err("Некорректная подпись/hash".into()); }
    let mut bytes = [0; N];
    for (i, b) in bytes.iter_mut().enumerate() { *b = u8::from_str_radix(&text[i * 2..i * 2 + 2], 16).map_err(|_| "Некорректная подпись")?; }
    Ok(bytes)
}
fn secure_url(url: &Url) -> bool {
    (url.scheme() == "https" || (url.scheme() == "http" && matches!(url.host_str(), Some("127.0.0.1" | "localhost" | "[::1]")))) &&
        url.username().is_empty() && url.password().is_none() && url.query().is_none() && url.fragment().is_none()
}
fn path_policy(relative: &str, mode: &str) -> bool {
    if !relative.is_ascii() || relative.len() > 220 || relative.contains(['\\', ':', '\0']) { return false; }
    for segment in relative.split('/') {
        let lower = segment.to_ascii_lowercase();
        if segment.is_empty() || segment == "." || segment == ".." || segment.ends_with(['.', ' ']) ||
            segment.bytes().any(|b| b < 32 || b"<>\"|?*".contains(&b)) ||
            matches!(lower.as_str(), "saves" | "screenshots" | "logs" | "crash-reports" | "cache" | "caches" | "world" | "accounts" | "tokens" | "secrets" | ".git" | ".recreated" | "options.txt" | "servers.dat" | "usercache.json" | "usernamecache.json") ||
            lower.contains("fingerprint") || lower.ends_with(".bak") ||
            matches!(lower.split('.').next().unwrap_or(""), "con" | "prn" | "aux" | "nul" | "com1" | "com2" | "com3" | "com4" | "com5" | "com6" | "com7" | "com8" | "com9" | "lpt1" | "lpt2" | "lpt3" | "lpt4" | "lpt5" | "lpt6" | "lpt7" | "lpt8" | "lpt9") { return false; }
    }
    match mode {
        "managed" => relative == "launch.json" || ["libraries/", "assets/", "versions/", "runtime/java-21/", "instances/alpha/resourcepacks/CreateServerQuests/"].iter().any(|p| relative.starts_with(p)) ||
            (relative.starts_with("instances/alpha/mods/") && relative.ends_with(".jar") && relative[21..].split('/').count() == 1),
        "seed" => relative.starts_with("instances/alpha/config/") || relative.starts_with("instances/alpha/defaultconfigs/"),
        _ => false,
    }
}
// Reject symlinks/junctions at EVERY ancestor, including .recreated and the root.
fn is_link(meta: &fs::Metadata) -> bool {
    if meta.file_type().is_symlink() { return true; }
    #[cfg(windows)] { use std::os::windows::fs::MetadataExt; if meta.file_attributes() & 0x400 != 0 { return true; } }
    false
}
pub(crate) fn safe(root: &Path, relative: &str) -> Result<PathBuf, String> {
    if relative.split('/').any(|p| p.is_empty() || p == "." || p == "..") || relative.contains(['\\', ':']) { return Err("Небезопасный путь обновления".into()); }
    let mut current = root.to_path_buf();
    if is_link(&fs::symlink_metadata(&current).map_err(|_| "Instance недоступен")?) { return Err("Instance является ссылкой/junction".into()); }
    for segment in relative.split('/') {
        current.push(segment);
        match fs::symlink_metadata(&current) {
            Ok(meta) if is_link(&meta) => return Err("Ссылка/junction в управляемом пути".into()),
            Ok(_) => {},
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => {},
            Err(_) => return Err("Не удалось проверить путь обновления".into()),
        }
    }
    Ok(current)
}
fn digest(path: &Path) -> Result<(u64, String), String> {
    let mut file = File::open(path).map_err(|_| "Не удалось прочитать управляемый файл")?;
    let mut hash = Sha256::new(); let mut size = 0; let mut buffer = vec![0; 256 * 1024];
    loop { let n = file.read(&mut buffer).map_err(|_| "Ошибка проверки файла")?; if n == 0 { break; } hash.update(&buffer[..n]); size += n as u64; }
    Ok((size, format!("{:x}", hash.finalize())))
}
fn matches(path: &Path, entry: &Entry) -> Result<bool, String> {
    match fs::symlink_metadata(path) {
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(false),
        Ok(meta) if meta.is_file() && !is_link(&meta) => { if meta.len() != entry.size { return Ok(false); } Ok(digest(path)?.1 == entry.sha256) },
        _ => Err("Управляемый путь не является обычным файлом".into()),
    }
}
#[cfg(test)]
fn scan_files(root:&Path,entries:&[Entry],progress:&SharedProgress,workers:usize)->Result<Vec<Entry>,String>{cache::scan(root,entries,progress,workers,&cache::Proof::new(root,true))}
fn authenticated(envelope: &Envelope, settings: &Settings) -> Result<Manifest, String> {
    let canonical = serde_json::to_vec(&envelope.manifest).map_err(|_| "Некорректный manifest")?;
    let key = VerifyingKey::from_bytes(&settings.key).map_err(|_| "Некорректный release key")?;
    key.verify_strict(&canonical, &Signature::from_bytes(&unhex(&envelope.signature)?)).map_err(|_| "Подпись manifest недействительна")?;
    let manifest: Manifest = serde_json::from_value(envelope.manifest.clone()).map_err(|_| "Некорректная схема manifest")?;
    if manifest.schema_version != 1 || manifest.platform != "windows-x86_64" || manifest.minecraft_version != "1.21.1" || manifest.neo_forge_version != "21.1.251" ||
        !manifest.version.starts_with("alpha-") || manifest.version.len() > 64 || manifest.sequence == 0 || manifest.files.is_empty() || manifest.files.len() > 30000 { return Err("Несовместимый manifest".into()); }
    let mut paths = HashSet::new(); let mut total = 0u64;
    for entry in &manifest.files {
        if !path_policy(&entry.path, &entry.mode) || !paths.insert(entry.path.to_ascii_lowercase()) || entry.size > 2 * 1024 * 1024 * 1024 || unhex::<32>(&entry.sha256).is_err() { return Err("Недопустимый allowlist manifest".into()); }
        total = total.checked_add(entry.size).ok_or("Слишком большой release")?;
        let url = Url::parse(&entry.url).map_err(|_| "Некорректный download URL")?;
        if !secure_url(&url) || url.origin() != settings.manifest_url.origin() || url.path() != format!("/releases/objects/{}", entry.sha256) { return Err("Недопустимый download URL".into()); }
    }
    if total > 20 * 1024 * 1024 * 1024 { return Err("Слишком большой release".into()); }
    Ok(manifest)
}
fn atomic_bytes(path: &Path, bytes: &[u8]) -> Result<(), String> {
    let temp = path.with_extension("next");
    if fs::symlink_metadata(&temp).is_ok_and(|m| is_link(&m) || !m.is_file()) { return Err("Небезопасный state temporary file".into()); }
    let mut file = OpenOptions::new().write(true).create(true).truncate(true).open(&temp).map_err(|_| "Не удалось сохранить состояние обновления")?;
    file.write_all(bytes).and_then(|_| file.sync_all()).map_err(|_| "Не удалось сохранить состояние обновления")?;
    drop(file);
    fs::rename(temp, path).map_err(|_| "Не удалось атомарно сохранить состояние обновления".to_string())
}
#[derive(Serialize, Deserialize)]
struct Change { path: String, backup: Option<String> }
#[derive(Serialize, Deserialize)]
struct Journal { previous: Option<Envelope>, target: Envelope, changes: Vec<Change>, committed: bool }
fn recover(root: &Path, settings: &Settings) -> Result<(), String> {
    let journal_file = safe(root, ".recreated/journal.json")?;
    if !journal_file.exists() { return Ok(()); }
    let journal: Journal = serde_json::from_slice(&fs::read(&journal_file).map_err(|_| "Не удалось прочитать recovery journal")?).map_err(|_| "Некорректный recovery journal")?;
    let target = authenticated(&journal.target, settings)?;
    let previous = journal.previous.as_ref().map(|e| authenticated(e, settings)).transpose()?;
    let allowed: HashSet<_> = target.files.iter().chain(previous.iter().flat_map(|m| m.files.iter())).filter(|e| e.mode == "managed").map(|e| e.path.as_str()).collect();
    let receipt = safe(root, ".recreated/receipt.json")?;
    let installed = fs::read(&receipt).ok().and_then(|b| serde_json::from_slice::<Envelope>(&b).ok());
    // Receipt is the commit marker. If it matches target, commit completed.
    if journal.committed && installed.as_ref().is_some_and(|e| e.signature == journal.target.signature && e.manifest == journal.target.manifest) {
        fs::remove_file(journal_file).map_err(|_| "Не удалось завершить recovery")?;
        return Ok(());
    }
    for change in journal.changes.iter().rev() {
        if !allowed.contains(change.path.as_str()) || !path_policy(&change.path, "managed") { return Err("Недопустимый recovery allowlist".into()); }
        let destination = safe(root, &change.path)?;
        if let Some(backup) = &change.backup {
            if !backup.starts_with(".recreated/staging/backup/") { return Err("Недопустимый recovery backup".into()); }
            let backup = safe(root, backup)?;
            if backup.exists() {
                let restore = safe(root, ".recreated/staging/restore.part")?;
                fs::copy(&backup, &restore).map_err(|_| "Recovery copy failed")?;
                fs::rename(restore, &destination).map_err(|_| "Recovery rename failed")?;
            }
        } else if destination.exists() { fs::remove_file(destination).map_err(|_| "Recovery removal failed")?; }
    }
    match journal.previous {
        Some(envelope) => atomic_bytes(&receipt, &serde_json::to_vec(&envelope).unwrap())?,
        None if receipt.exists() => fs::remove_file(&receipt).map_err(|_| "Recovery receipt failed")?,
        None => {},
    }
    fs::remove_file(journal_file).map_err(|_| "Не удалось завершить recovery")?;
    Ok(())
}
fn phase(progress: &SharedProgress, value: &str, message: &str) { let mut p = progress.lock().unwrap(); p.phase = value.into(); p.message = message.into(); }
pub fn reconcile(root:&Path,settings:&Settings,progress:&SharedProgress)->Result<String,String>{Ok(prepare(root,settings,progress,true)?.version)}
pub(crate) fn prepare(root:&Path,settings:&Settings,progress:&SharedProgress,full:bool)->Result<cache::Proof,String>{
    let mut proof=cache::Proof::new(root,full);
    match reconcile_inner(root,settings,progress,&proof){Ok(version)=>{proof.version=version;proof.save(root);Ok(proof)},Err(error)=>{phase(progress,"FAILED",&error);Err(error)}}
}
fn reconcile_inner(root: &Path, settings: &Settings, progress: &SharedProgress, proof:&cache::Proof) -> Result<String, String> {
    *progress.lock().unwrap() = Progress { phase: "CHECKING".into(), message: "Проверка обновлений".into(), ..Progress::default() };
    if !secure_url(&settings.manifest_url) { return Err("Release URL должен использовать HTTPS".into()); }
    let state = safe(root, ".recreated")?;
    fs::create_dir_all(&state).map_err(|_| "Не удалось подготовить updater state")?;
    recover(root, settings)?;
    let client = Client::builder().pool_max_idle_per_host(8).tcp_keepalive(Duration::from_secs(30)).redirect(Policy::none()).connect_timeout(Duration::from_secs(12)).timeout(Duration::from_secs(90)).build().map_err(|_| "Updater HTTP недоступен")?;
    let mut bytes=Vec::new();
    for attempt in 0..4 {
        let result=(||->Result<Vec<u8>,String>{
            let response=crate::network::blocking_send(client.get(settings.manifest_url.clone()).header("Cache-Control","no-cache")).map_err(|_|"Нет связи с сервером обновлений")?;
            if !response.status().is_success(){return Err(format!("Manifest: HTTP {}",response.status().as_u16()))}
            if response.content_length().is_some_and(|n|n>16*1024*1024){return Err("Manifest слишком большой".into())}
            let mut data=Vec::new();response.take(16*1024*1024+1).read_to_end(&mut data).map_err(|_|"Загрузка manifest прервана")?;
            if data.len()>16*1024*1024{return Err("Manifest слишком большой".into())}Ok(data)
        })();
        match result {Ok(data)=>{bytes=data;break},Err(error)=>{if attempt==3{return Err(error)}std::thread::sleep(Duration::from_millis(500*(attempt+1)));}}
    }
    let envelope: Envelope = serde_json::from_slice(&bytes).map_err(|_| "Некорректный signed manifest")?;
    let manifest = authenticated(&envelope, settings)?;
    let receipt = safe(root, ".recreated/receipt.json")?;
    let previous: Option<Envelope> = match fs::read(&receipt) {
        Ok(bytes) => Some(serde_json::from_slice(&bytes).map_err(|_| "Некорректный installed receipt")?),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => None,
        Err(_) => return Err("Installed receipt недоступен".into()),
    };
    let previous_manifest = previous.as_ref().map(|e| authenticated(e, settings)).transpose()?;
    if let Some(old) = &previous_manifest {
        if manifest.sequence < old.sequence || (manifest.sequence == old.sequence && (manifest.version != old.version || previous.as_ref().unwrap().manifest != envelope.manifest)) { return Err("Отклонён rollback/изменение immutable release".into()); }
    }
    { let mut p = progress.lock().unwrap(); p.version = Some(manifest.version.clone()); p.total_files = manifest.files.len() as u64; }
    let workers = std::thread::available_parallelism().map(|n| n.get()).unwrap_or(1).min(4);
    let pending = cache::scan(root, &manifest.files, progress, workers, proof)?;
    let current: HashSet<_> = manifest.files.iter().map(|e| e.path.to_ascii_lowercase()).collect();
    let stale: Vec<_> = previous_manifest.iter().flat_map(|m| m.files.iter()).filter(|e| e.mode == "managed" && !current.contains(&e.path.to_ascii_lowercase())).cloned().collect();
    let staging = safe(root, ".recreated/staging")?;
    if staging.exists() { fs::remove_dir_all(&staging).map_err(|_| "Не удалось очистить updater staging")?; }
    fs::create_dir_all(staging.join("backup")).map_err(|_| "Не удалось подготовить staging")?;
    { let mut p = progress.lock().unwrap(); p.total_bytes = pending.iter().map(|e| e.size).sum(); p.changed_files = pending.len() as u64; }
    phase(progress, "DOWNLOADING", "Загрузка файлов");
    let downloads=safe(root,".recreated/downloads")?;
    fs::create_dir_all(&downloads).map_err(|_|"Не удалось создать кэш докачки")?;
    download::batch(root,&client,&pending,progress)?;
    for (index,entry) in pending.iter().enumerate(){
        let cached=safe(root,&format!(".recreated/downloads/{}.part",entry.sha256))?;
        let staged=staging.join(format!("{index}.part"));
        fs::hard_link(&cached,&staged).or_else(|_|fs::copy(&cached,&staged).map(|_|())).map_err(|_|"Не удалось подготовить скачанный файл")?;
    }
    phase(progress, "VERIFYING", "Проверка SHA-256");
    for(i,entry)in pending.iter().enumerate(){let p=staging.join(format!("{i}.part"));let before=cache::stamp(&p)?;if !matches(&p,entry)?||before!=cache::stamp(&p)?{return Err("Staged hash mismatch".into());}proof.remember(entry,before);}
    let mut changes = Vec::new();
    for entry in pending.iter().filter(|e| e.mode == "managed").chain(stale.iter()) {
        let destination = safe(root, &entry.path)?;
        let backup = if destination.exists() {
            if !destination.is_file() { return Err("Управляемый путь не файл".into()); }
            let relative = format!(".recreated/staging/backup/{}", changes.len());
            fs::copy(&destination, safe(root, &relative)?).map_err(|_| "Не удалось сохранить rollback copy")?;
            Some(relative)
        } else { None };
        changes.push(Change { path: entry.path.clone(), backup });
    }
    let mut journal = Journal { previous, target: envelope.clone(), changes, committed: false };
    let journal_file = safe(root, ".recreated/journal.json")?;
    atomic_bytes(&journal_file, &serde_json::to_vec(&journal).unwrap())?;
    phase(progress, "APPLYING", "Применение обновления");
    let apply = (|| -> Result<(), String> {
        for (i, entry) in pending.iter().enumerate() {
            let destination = safe(root, &entry.path)?;
            fs::create_dir_all(destination.parent().unwrap()).map_err(|_| "Не удалось подготовить release directory")?;
            if entry.mode == "seed" {
                // Atomic create-if-absent, never replace; no seed deletion in rollback.
                match fs::hard_link(staging.join(format!("{i}.part")), &destination) {
                    Ok(_) => {}, Err(e) if e.kind() == std::io::ErrorKind::AlreadyExists => {}, Err(_) => return Err("Не удалось создать config seed".into()),
                }
            } else { fs::rename(staging.join(format!("{i}.part")), &destination).map_err(|_| "Не удалось атомарно заменить release-файл")?; proof.relocated(entry,&destination)?; }
        }
        for entry in &stale { let target = safe(root, &entry.path)?; if target.exists() { fs::remove_file(target).map_err(|_| "Не удалось удалить owned obsolete файл")?; } }
        atomic_bytes(&receipt, &serde_json::to_vec(&envelope).unwrap())?;
        Ok(())
    })();
    if let Err(error) = apply { recover(root, settings).map_err(|_| "Ошибка применения и rollback; запуск заблокирован до recovery")?; return Err(error); }
    journal.committed = true;
    if atomic_bytes(&journal_file, &serde_json::to_vec(&journal).unwrap()).is_err() {
        recover(root, settings)?;
        return Err("Не удалось зафиксировать update commit".into());
    }
    fs::remove_file(journal_file).map_err(|_| "Не удалось завершить update commit")?;
    fs::remove_dir_all(staging).map_err(|_| "Не удалось убрать updater staging")?;
    for entry in &pending { let p=safe(root,&format!(".recreated/downloads/{}.part",entry.sha256))?; if p.exists(){let _=fs::remove_file(p);} }
    phase(progress, "READY", "Сборка готова");
    Ok(manifest.version)
}

#[cfg(test)]
mod tests {
    use super::*;
    use ed25519_dalek::{Signer, SigningKey};
    use std::{net::TcpListener, sync::atomic::{AtomicBool, Ordering}, thread, time::{SystemTime, UNIX_EPOCH}};
    #[test]
    fn parallel_scan_preserves_hash_checks_seeds_and_order() {
        let f = Fixture::new();
        f.release(1, &[("libraries/a.jar", "managed", b"aaaa"), ("libraries/b.jar", "managed", b"bbbb"), ("instances/alpha/config/test.toml", "seed", b"seed")]);
        let manifest = authenticated(&f.data.lock().unwrap().0, &f.settings).unwrap();
        fs::create_dir_all(f.root.join("libraries")).unwrap();
        fs::write(f.root.join("libraries/a.jar"), b"xxxx").unwrap(); // Same-size tampering.
        fs::create_dir_all(f.root.join("instances/alpha/config")).unwrap();
        fs::write(f.root.join("instances/alpha/config/test.toml"), b"user settings").unwrap();
        let p = SharedProgress::default();
        let serial = scan_files(&f.root, &manifest.files, &p, 1).unwrap();
        let p = SharedProgress::default();
        let parallel = scan_files(&f.root, &manifest.files, &p, 4).unwrap();
        assert_eq!(serial.iter().map(|e| &e.path).collect::<Vec<_>>(), parallel.iter().map(|e| &e.path).collect::<Vec<_>>());
        assert_eq!(parallel.len(), 2);
        assert_eq!(p.lock().unwrap().checked_files, 3);
        fs::create_dir(f.root.join("libraries/b.jar")).unwrap();
        assert!(scan_files(&f.root, &manifest.files, &SharedProgress::default(), 4).is_err());
    }
    #[test]
    #[ignore = "Read-only timing of the real prepared Windows instance; no mutations or network"]
    fn prepared_scan_benchmark() {
        let root = crate::game::root().unwrap();
        let settings = production_settings().unwrap();
        let envelope: Envelope = serde_json::from_slice(&fs::read(root.join(".recreated/receipt.json")).unwrap()).unwrap();
        let manifest = authenticated(&envelope, &settings).unwrap();
        for workers in [1, 4, 1, 4] {
            let start = std::time::Instant::now();
            let pending = scan_files(&root, &manifest.files, &SharedProgress::default(), workers).unwrap();
            println!("SCAN workers={workers} files={} bytes={} seconds={:.3} pending={}", manifest.files.len(), manifest.files.iter().map(|f| f.size).sum::<u64>(), start.elapsed().as_secs_f64(), pending.len());
        }
    }
    struct Fixture {
        root: PathBuf, settings: Settings, data: Arc<Mutex<(Envelope, Vec<(String, Vec<u8>)>, usize)>>,
        stop: Arc<AtomicBool>, worker: Option<thread::JoinHandle<()>>,
    }
    impl Fixture {
        fn new() -> Self {
            let root = std::env::temp_dir().join(format!("recreated-updater-{}", SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_nanos()));
            fs::create_dir_all(&root).unwrap();
            let listener = TcpListener::bind("127.0.0.1:0").unwrap(); listener.set_nonblocking(true).unwrap();
            let origin = format!("http://{}", listener.local_addr().unwrap());
            // Test-only signing material, never production key.
            let key = SigningKey::from_bytes(&[7; 32]);
            let settings = Settings { manifest_url: Url::parse(&format!("{origin}/releases/channels/alpha/manifest.json")).unwrap(), key: key.verifying_key().to_bytes() };
            let data: Arc<Mutex<(Envelope, Vec<(String, Vec<u8>)>, usize)>> = Arc::new(Mutex::new((Envelope { manifest: serde_json::Value::Null, signature: "".into() }, vec![], 0)));
            let stop = Arc::new(AtomicBool::new(false)); let shared = data.clone(); let stopping = stop.clone();
            let worker = thread::spawn(move || {
                while !stopping.load(Ordering::Relaxed) {
                    if let Ok((mut stream, _)) = listener.accept() {
                        // Accepted sockets inherit nonblocking mode on Windows.
                        stream.set_nonblocking(false).unwrap();
                        stream.set_read_timeout(Some(Duration::from_secs(2))).unwrap();
                        // TCP reads are not message boundaries. Closing with unread request headers
                        // can reset the socket on Windows and intermittently truncate the response.
                        let mut request = Vec::new(); let mut chunk = [0; 1024];
                        while !request.windows(4).any(|bytes| bytes == b"\r\n\r\n") && request.len() < 8192 {
                            let n = stream.read(&mut chunk).unwrap_or(0);
                            if n == 0 { break; }
                            request.extend_from_slice(&chunk[..n]);
                        }
                        let path = String::from_utf8_lossy(&request).split_whitespace().nth(1).unwrap_or("").to_string();
                        let mut state = shared.lock().unwrap();
                        let body = if path.ends_with("manifest.json") { serde_json::to_vec(&state.0).unwrap() }
                        else { state.2 += 1; state.1.iter().find(|(url, _)| *url == path).map(|(_, bytes)| bytes.clone()).unwrap_or_default() };
                        let mut response = format!("HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n", body.len()).into_bytes();
                        response.extend_from_slice(&body);
                        let _ = stream.write_all(&response);
                    } else { thread::sleep(Duration::from_millis(5)); }
                }
            });
            Self { root, settings, data, stop, worker: Some(worker) }
        }
        fn release(&self, sequence: u64, entries: &[(&str, &str, &[u8])]) {
            let mut files = Vec::new(); let mut blobs = Vec::new();
            for (path, mode, bytes) in entries {
                let hash = format!("{:x}", Sha256::digest(bytes));
                let relative = format!("/releases/objects/{hash}");
                files.push(serde_json::json!({ "path": path, "mode": mode, "size": bytes.len(), "sha256": hash, "url": self.settings.manifest_url.join(&relative).unwrap().as_str() }));
                blobs.push((relative, bytes.to_vec()));
            }
            let manifest = serde_json::json!({ "schemaVersion": 1, "version": format!("alpha-0.1.{}", sequence - 1), "sequence": sequence,
                "platform": "windows-x86_64", "minecraftVersion": "1.21.1", "neoForgeVersion": "21.1.251", "files": files });
            let signature = SigningKey::from_bytes(&[7; 32]).sign(&serde_json::to_vec(&manifest).unwrap()).to_bytes().iter().map(|b| format!("{b:02x}")).collect();
            let mut state = self.data.lock().unwrap(); state.0 = Envelope { manifest, signature }; state.1 = blobs; state.2 = 0;
        }
        fn apply(&self) -> Result<Progress, String> {
            let _lock = crate::game::instance_lock(&self.root).map_err(|_| "Game/update lock busy")?;
            let progress = SharedProgress::default(); reconcile(&self.root, &self.settings, &progress)?;
            let result = progress.lock().unwrap().clone(); Ok(result)
        }
    }
    impl Drop for Fixture {
        fn drop(&mut self) { self.stop.store(true, Ordering::Relaxed); self.worker.take().unwrap().join().unwrap(); fs::remove_dir_all(&self.root).unwrap(); }
    }
    #[test]
    fn fresh_install_current_noop_and_modified_missing_repair_over_real_http() {
        let fixture = Fixture::new();
        fixture.release(1, &[("instances/alpha/mods/a.jar", "managed", b"jar A"), ("assets/indexes/17.json", "managed", b"{}")]);
        assert_eq!(fixture.apply().unwrap().changed_files, 2);
        let before = fixture.data.lock().unwrap().2;
        let current = fixture.apply().unwrap(); assert_eq!(current.changed_files, 0); assert_eq!(current.downloaded_bytes, 0);
        assert_eq!(fixture.data.lock().unwrap().2, before);
        fs::write(fixture.root.join("instances/alpha/mods/a.jar"), b"wrong").unwrap();
        fs::remove_file(fixture.root.join("assets/indexes/17.json")).unwrap();
        assert_eq!(fixture.apply().unwrap().changed_files, 2);
        assert_eq!(fs::read(fixture.root.join("instances/alpha/mods/a.jar")).unwrap(), b"jar A");
        assert_eq!(fs::read(fixture.root.join("assets/indexes/17.json")).unwrap(), b"{}");
    }
    #[test]
    fn bad_download_hash_rejects_entire_batch_before_live_file_changes() {
        let fixture = Fixture::new();
        fixture.release(1, &[("instances/alpha/mods/a.jar", "managed", b"jar A")]); fixture.apply().unwrap();
        fixture.release(2, &[("instances/alpha/mods/a.jar", "managed", b"jar B"), ("instances/alpha/mods/b.jar", "managed", b"jar C")]);
        fixture.data.lock().unwrap().1[1].1 = b"jar X".to_vec();
        assert!(fixture.apply().unwrap_err().contains("SHA-256"));
        assert_eq!(fs::read(fixture.root.join("instances/alpha/mods/a.jar")).unwrap(), b"jar A");
        assert!(!fixture.root.join("instances/alpha/mods/b.jar").exists());
    }
    #[test]
    fn deletes_only_previously_owned_files_and_never_overwrites_user_or_seed_data() {
        let fixture = Fixture::new();
        fixture.release(1, &[("instances/alpha/mods/old.jar", "managed", b"old"), ("instances/alpha/config/create.toml", "seed", b"default")]); fixture.apply().unwrap();
        let protected = ["instances/alpha/options.txt", "instances/alpha/saves/my-world/level.dat", "instances/alpha/screenshots/image.png", "instances/alpha/logs/latest.log", "instances/alpha/cache/local", "instances/alpha/mods/user-added.jar", "instances/alpha/config/create.toml"];
        for relative in protected { let file = fixture.root.join(relative); fs::create_dir_all(file.parent().unwrap()).unwrap(); fs::write(file, b"personal").unwrap(); }
        fixture.release(2, &[("instances/alpha/mods/new.jar", "managed", b"new"), ("instances/alpha/config/create.toml", "seed", b"new defaults")]); fixture.apply().unwrap();
        assert!(!fixture.root.join("instances/alpha/mods/old.jar").exists());
        for relative in protected { assert_eq!(fs::read(fixture.root.join(relative)).unwrap(), b"personal"); }
    }
    #[test]
    fn signature_path_url_case_and_running_game_guards() {
        let fixture = Fixture::new(); fixture.release(1, &[("instances/alpha/mods/a.jar", "managed", b"jar")]);
        let lock = crate::game::instance_lock(&fixture.root).unwrap(); assert!(fixture.apply().unwrap_err().contains("lock")); drop(lock);
        fixture.data.lock().unwrap().0.manifest["version"] = "tampered".into(); assert!(fixture.apply().unwrap_err().contains("Подпись"));
        for (relative, mode) in [("../outside", "managed"), ("instances/alpha/options.txt", "managed"), ("instances/alpha/config/personal.toml", "managed"), ("runtime/java-21/NUL.txt", "managed"), ("assets/a:stream", "managed"), ("assets/a.", "managed")] { assert!(!path_policy(relative, mode)); }
        fixture.release(1, &[("assets/a", "managed", b"x"), ("assets/A", "managed", b"x")]); assert!(fixture.apply().is_err());
        fixture.release(1, &[("assets/a", "managed", b"x")]); fixture.apply().unwrap();
        fixture.release(2, &[("assets/a", "managed", b"x")]); fixture.apply().unwrap();
        fixture.release(1, &[("assets/a", "managed", b"x")]); assert!(fixture.apply().unwrap_err().contains("rollback"));
    }
    #[test]
    fn interrupted_apply_recovers_before_any_new_update() {
        let fixture = Fixture::new(); fixture.release(1, &[("instances/alpha/mods/a.jar", "managed", b"old")]); fixture.apply().unwrap();
        let previous: Envelope = serde_json::from_slice(&fs::read(fixture.root.join(".recreated/receipt.json")).unwrap()).unwrap();
        fixture.release(2, &[("instances/alpha/mods/a.jar", "managed", b"new")]);
        let backup = fixture.root.join(".recreated/staging/backup/0"); fs::create_dir_all(backup.parent().unwrap()).unwrap(); fs::write(&backup, b"old").unwrap();
        fs::write(fixture.root.join("instances/alpha/mods/a.jar"), b"partial").unwrap();
        let journal = Journal { previous: Some(previous), target: fixture.data.lock().unwrap().0.clone(), changes: vec![Change { path: "instances/alpha/mods/a.jar".into(), backup: Some(".recreated/staging/backup/0".into()) }], committed: false };
        fs::write(fixture.root.join(".recreated/journal.json"), serde_json::to_vec(&journal).unwrap()).unwrap();
        recover(&fixture.root, &fixture.settings).unwrap();
        assert_eq!(fs::read(fixture.root.join("instances/alpha/mods/a.jar")).unwrap(), b"old");
        assert!(!fixture.root.join(".recreated/journal.json").exists());
    }
    #[test]
    #[ignore = "Live HTTPS baseline no-op plus repair in an isolated copy; never corrupts real instance"]
    fn production_baseline_probe() {
        let settings = production_settings().unwrap(); let real = crate::game::root().unwrap();
        let progress = SharedProgress::default();
        { let _lock = crate::game::instance_lock(&real).expect("Close Minecraft first");
          assert_eq!(reconcile(&real, &settings, &progress).unwrap(), "alpha-0.1.0");
          assert_eq!(progress.lock().unwrap().changed_files, 0); }
        let bytes = Client::new().get(settings.manifest_url.clone()).send().unwrap().bytes().unwrap();
        let envelope: Envelope = serde_json::from_slice(&bytes).unwrap(); let manifest = authenticated(&envelope, &settings).unwrap();
        let probe = std::env::temp_dir().join(format!("recreated-live-probe-{}", SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_nanos())); fs::create_dir(&probe).unwrap();
        for entry in &manifest.files { let source = safe(&real, &entry.path).unwrap(); if source.is_file() { let dest = probe.join(&entry.path); fs::create_dir_all(dest.parent().unwrap()).unwrap(); fs::copy(source, dest).unwrap(); } }
        let _lock = crate::game::instance_lock(&probe).unwrap();
        let altered = "instances/alpha/mods/createcityexpansioncore-1.0.1.jar";
        fs::write(probe.join(altered), b"deliberately changed test copy").unwrap();
        fs::remove_file(probe.join("assets/indexes/17.json")).unwrap();
        fs::write(probe.join("instances/alpha/options.txt"), b"user options preserved").unwrap();
        let p = SharedProgress::default(); reconcile(&probe, &settings, &p).unwrap();
        assert_eq!(p.lock().unwrap().changed_files, 2);
        assert_eq!(digest(&probe.join(altered)).unwrap(), digest(&real.join(altered)).unwrap());
        assert_eq!(fs::read(probe.join("instances/alpha/options.txt")).unwrap(), b"user options preserved");
        println!("Live alpha-0.1.0: current installation 0 downloads; isolated modified/deleted files restored (2); user options unchanged");
        drop(_lock); fs::remove_dir_all(probe).unwrap();
    }
}
