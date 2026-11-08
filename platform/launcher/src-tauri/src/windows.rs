use tauri::{AppHandle,Manager,menu::{Menu,MenuItem},tray::{TrayIconBuilder,TrayIconEvent,MouseButton,MouseButtonState}};

pub fn show_main(app:&AppHandle)->tauri::Result<()> {
    let window=app.get_webview_window("main").ok_or(tauri::Error::WindowNotFound)?;
    window.show()?;
    if window.is_minimized()? {window.unminimize()?;}
    window.set_focus()?;
    Ok(())
}
pub fn menu_action(app:&AppHandle,id:&str) {
    match id {
        "open-launcher"=>{if show_main(app).is_err(){eprintln!("Launcher window could not be shown");}},
        "exit-launcher"=>{let state=crate::game::game_status(app.state::<crate::game::GameRuntime>());if state.state==crate::game::Phase::STARTING{let _=show_main(app);}else{app.exit(0);}},
        _=>{},
    }
}
pub fn job_notice(app:&AppHandle,status:&crate::game::GameStatus){
    if let Some(w)=app.get_webview_window("main"){if w.is_focused().unwrap_or(false){return;}}
    crate::windows_notice::show(app,&status.state);
}
pub fn setup(app:&mut tauri::App)->Result<(),Box<dyn std::error::Error>> {
    let open=MenuItem::with_id(app,"open-launcher","Открыть Launcher",true,None::<&str>)?;
    let exit=MenuItem::with_id(app,"exit-launcher","Выход",true,None::<&str>)?;
    let menu=Menu::with_items(app,&[&open,&exit])?;
    let icon=app.default_window_icon().ok_or("Missing Launcher tray icon")?.clone();
    TrayIconBuilder::with_id("launcher-tray").icon(icon).tooltip("RE:CREATED Launcher").menu(&menu)
        .show_menu_on_left_click(false)
        .on_menu_event(|app,event|menu_action(app,event.id.as_ref()))
        .on_tray_icon_event(|tray,event|{
            if matches!(event,TrayIconEvent::Click{button:MouseButton::Left,button_state:MouseButtonState::Up,..}) {
                menu_action(tray.app_handle(),"open-launcher");
            }
        }).build(app)?;
    #[cfg(feature="window-smoke")] crate::window_smoke::start(app.handle().clone());
    Ok(())
}
