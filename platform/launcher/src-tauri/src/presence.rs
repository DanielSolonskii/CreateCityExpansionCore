//! Optional local Discord IPC. Never participates in account/game admission.
use discord_rich_presence::activity;
#[cfg(windows)] use crate::presence_ipc::Client as DiscordIpcClient;
#[cfg(not(windows))] use discord_rich_presence::{DiscordIpc,DiscordIpcClient};
use serde::{Deserialize, Serialize};
use std::{fs, thread, time::{Duration, SystemTime, UNIX_EPOCH}};
use tauri::Manager;
static WORKER:std::sync::OnceLock<thread::Thread>=std::sync::OnceLock::new();

const APPLICATION_ID: &str = "1561115660975865876";
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum Screen { Launcher, Updating, Starting, Running }
fn screen(game: &crate::game::Phase, update: &str) -> Screen {
    if *game == crate::game::Phase::RUNNING { return Screen::Running; }
    if *game == crate::game::Phase::FAILED { return Screen::Launcher; }
    if matches!(update, "CHECKING" | "DOWNLOADING" | "VERIFYING" | "APPLYING") { return Screen::Updating; }
    if *game == crate::game::Phase::STARTING { return Screen::Starting; }
    Screen::Launcher
}
fn label(value: Screen) -> &'static str {
    match value {Screen::Launcher => "В лаунчере", Screen::Updating => "Обновляет сборку", Screen::Starting => "Запускает Minecraft", Screen::Running => "Игра запущена"}
}
fn activity(value: Screen, since: i64) -> activity::Activity<'static> {
    activity::Activity::new().details(label(value)).state("RE:CREATED · Альфа-тестирование")
        .timestamps(activity::Timestamps::new().start(since))
        .buttons(vec![activity::Button::new("Скачать Launcher", "https://api.recreated.ru/launcher/latest/RE-CREATED-Launcher-x64-setup.exe")])
}
fn now() -> i64 { SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_secs() as i64 }
#[derive(Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct Preference { enabled: bool }
fn enabled() -> Result<bool, String> {
    let path=crate::game::root()?.join("discord-presence.json");
    match fs::read(path) {
        Ok(bytes) => serde_json::from_slice::<Preference>(&bytes).map(|p|p.enabled).map_err(|_|"Не удалось прочитать настройки Discord".into()),
        Err(e) if e.kind()==std::io::ErrorKind::NotFound => Ok(true),
        Err(_) => Err("Не удалось прочитать настройки Discord".into()),
    }
}
#[tauri::command]
pub fn discord_presence_enabled() -> Result<bool,String> { enabled() }
#[tauri::command]
pub fn save_discord_presence(enabled: bool) -> Result<(),String> {
    let root=crate::game::root()?;fs::create_dir_all(&root).map_err(|_|"Не удалось сохранить настройки Discord")?;
    let temp=root.join(format!("discord-presence-{}.next",uuid::Uuid::new_v4()));
    let result=(||{fs::write(&temp,serde_json::to_vec(&Preference{enabled}).map_err(|_|"Некорректные настройки")?).map_err(|_|"Не удалось сохранить настройки Discord")?;
        fs::rename(&temp,root.join("discord-presence.json")).map_err(|_|"Не удалось сохранить настройки Discord".to_string())})();
    let _=fs::remove_file(temp);if result.is_ok(){if let Some(worker)=WORKER.get(){worker.unpark();}}result
}
pub fn start(app: tauri::AppHandle) {
    // Blocking IPC is isolated here, without holding game/settings/auth locks.
    let _=thread::Builder::new().name("Discord presence".into()).spawn(move || {
        let _=WORKER.set(thread::current());
        let mut client:Option<DiscordIpcClient>=None;
        let mut previous=None;let mut since=now();
        loop {
            if app.webview_windows().is_empty() {break;}
            if !enabled().unwrap_or(false) {
                if let Some(mut connection)=client.take(){let _=connection.clear_activity();let _=connection.close();}
                previous=None;thread::park_timeout(Duration::from_secs(3));continue;
            }
            let runtime=app.state::<crate::game::GameRuntime>();
            let game=crate::game::game_status(runtime.clone());
            let progress=crate::game::update_status(runtime);
            let current=screen(&game.state,&progress.phase);
            if previous!=Some(current){since=now();previous=Some(current);}
            if client.is_none() {
                let mut connection=DiscordIpcClient::new(APPLICATION_ID);
                if connection.connect().is_ok(){client=Some(connection);}
            }
            if !enabled().unwrap_or(false){continue;}
            if let Some(connection)=client.as_mut() {
                let result=connection.set_activity(activity(current,since)).and_then(|_|connection.recv());
                if !matches!(result,Ok((1,ref body)) if body.get("evt").and_then(|v|v.as_str())!=Some("ERROR")) {
                    if let Some(mut connection)=client.take(){let _=connection.close();}
                }
            }
            thread::park_timeout(Duration::from_secs(5));
        }
        if let Some(mut connection)=client{let _=connection.clear_activity();let _=connection.close();}
    });
}
#[cfg(test)] mod tests {
    use super::*;
    #[test] fn honest_states_and_running_priority(){
        use crate::game::Phase::*;
        assert_eq!(screen(&STOPPED,"READY"),Screen::Launcher);
        assert_eq!(screen(&FAILED,"FAILED"),Screen::Launcher);
        for p in ["CHECKING","DOWNLOADING","VERIFYING","APPLYING"]{assert_eq!(screen(&STARTING,p),Screen::Updating);assert_eq!(screen(&RUNNING,p),Screen::Running);assert_eq!(screen(&FAILED,p),Screen::Launcher);}
        assert_eq!(screen(&STARTING,"READY"),Screen::Starting);
        assert_eq!(label(Screen::Running),"Игра запущена");
    }
    #[test] fn activity_contains_only_public_allowlisted_data(){
        let value=serde_json::to_string(&activity(Screen::Running,123)).unwrap();
        assert!(value.contains("Игра запущена"));assert!(value.contains("123"));
        for forbidden in ["username","password","credential","secret","minecraftUuid","token","Онлайн"]{assert!(!value.contains(forbidden));}
        assert!(serde_json::from_str::<Preference>(r#"{"enabled":false}"#).is_ok());
        assert!(serde_json::from_str::<Preference>(r#"{"enabled":true,"token":"x"}"#).is_err());
    }
    #[test] #[ignore = "requires local Discord Desktop; explicitly invoked"]
    fn live_discord_smoke(){
        let mut client=DiscordIpcClient::new(APPLICATION_ID);client.connect().expect("Discord IPC handshake");
        client.set_activity(activity(Screen::Launcher,now())).unwrap();let (_,ack)=client.recv().unwrap();
        assert_ne!(ack.get("evt").and_then(|v|v.as_str()),Some("ERROR"));
        assert_eq!(ack.get("cmd").and_then(|v|v.as_str()),Some("SET_ACTIVITY"));
        client.clear_activity().unwrap();client.close().unwrap();
    }
}
