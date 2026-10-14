//! Opt-in real Windows/WebView integration harness. Never compiled into the installer.
use tauri::{AppHandle,Manager,Listener};
use std::{path::PathBuf,sync::{Arc,Mutex},thread,time::Duration};
use serde_json::{json,Value};

fn wait(mut condition:impl FnMut()->bool)->Result<(),String> {
    for _ in 0..60 {if condition(){return Ok(());} thread::sleep(Duration::from_millis(100));}
    Err("Native window condition timed out".into())
}
fn click(window:&tauri::WebviewWindow,name:&str)->Result<(),String> {
    let selector=serde_json::to_string(&format!("[data-window-controls] button[aria-label='{name}']")).unwrap();
    window.eval(&format!("document.querySelector({selector})?.click();")).map_err(|_|"WebView evaluation failed".into())
}
pub fn start(app:AppHandle) {
    let Some(argument)=std::env::args().find_map(|a|a.strip_prefix("--window-smoke-report=").map(str::to_owned)) else{return;};
    let path=PathBuf::from(argument);
    let quit=std::env::args().any(|a|a=="--window-smoke-tray-exit");
    let payload=Arc::new(Mutex::new(Value::Null));let captured=payload.clone();
    app.listen("window-smoke-ui",move |event|{if let Ok(value)=serde_json::from_str(event.payload()){*captured.lock().unwrap()=value;}});
    thread::spawn(move||{
        thread::sleep(Duration::from_secs(2));
        let mut passed=Vec::new();
        let outcome=(||->Result<(),String>{
            let window=app.get_webview_window("main").ok_or("Main window missing")?;
            if app.tray_by_id("launcher-tray").is_none(){return Err("Native tray missing".into());}
            passed.push("tray-created");
            click(&window,"Развернуть")?;wait(||window.is_maximized().unwrap_or(false))?;passed.push("frontend-maximize-through-IPC");
            thread::sleep(Duration::from_millis(300));
            window.eval("window.__TAURI_INTERNALS__.invoke('plugin:event|emit',{event:'window-smoke-ui',payload:{label:document.querySelector('[data-window-controls] button[aria-pressed]')?.getAttribute('aria-label')}});").map_err(|_|"State probe failed")?;
            wait(||payload.lock().unwrap()["label"]=="Восстановить")?;passed.push("maximize-button-state");
            click(&window,"Восстановить")?;wait(||!window.is_maximized().unwrap_or(true))?;passed.push("frontend-restore-through-IPC");
            thread::sleep(Duration::from_millis(300));click(&window,"Свернуть")?;wait(||window.is_minimized().unwrap_or(false))?;passed.push("frontend-minimize-through-IPC");
            crate::windows::menu_action(&app,"open-launcher");wait(||!window.is_minimized().unwrap_or(true) && window.is_visible().unwrap_or(false))?;passed.push("tray-open-restores-minimized");
            thread::sleep(Duration::from_millis(300));click(&window,"Скрыть в трей")?;wait(||!window.is_visible().unwrap_or(true))?;passed.push("frontend-hide-through-IPC");
            let mut second=std::process::Command::new(std::env::current_exe().map_err(|_|"Executable unavailable")?).arg("--window-smoke-secondary").spawn().map_err(|_|"Second instance launch failed")?;
            wait(||second.try_wait().ok().flatten().is_some())?;
            wait(||window.is_visible().unwrap_or(false) && !window.is_minimized().unwrap_or(true))?;passed.push("second-process-opens-existing-window-and-exits");
            window.hide().map_err(|_|"Hide failed")?;crate::windows::menu_action(&app,"open-launcher");wait(||window.is_visible().unwrap_or(false))?;passed.push("tray-open-restores-hidden");
            Ok(())
        })();
        let report=json!({"ok":outcome.is_ok(),"passed":passed,"error":outcome.err(),"termination":if quit{"tray-exit-handler"}else{"frontend-close-button"}});
        if std::fs::write(&path,serde_json::to_vec_pretty(&report).unwrap()).is_err(){app.exit(2);return;}
        if report["ok"]!=true{app.exit(1);return;}
        if quit{crate::windows::menu_action(&app,"exit-launcher");}
        else if let Some(window)=app.get_webview_window("main"){
            thread::sleep(Duration::from_millis(300));
            if click(&window,"Закрыть").is_err(){app.exit(3);return;}
            thread::sleep(Duration::from_secs(3));app.exit(4); // Nonzero watchdog if normal close didn't terminate.
        }
    });
}
