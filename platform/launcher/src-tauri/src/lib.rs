use tauri::Manager;
#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    if self_update::helper(){return;}
    if game_lock::helper(){return;}
    telemetry::start();
    tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app,_,_| {
            if windows::show_main(app).is_err() {eprintln!("Launcher window could not be shown");}
        }))
        .plugin(tauri_plugin_opener::init())
        .setup(|app| {windows::setup(app)?;presence::start(app.handle().clone());Ok(())})
        .on_window_event(|window,event| {
            if window.label()=="main" {
                if let tauri::WindowEvent::CloseRequested{api,..}=event {
                    let state=game::game_status(window.app_handle().state::<game::GameRuntime>());
                    if state.state==game::Phase::STARTING {api.prevent_close();let _=window.hide();}
                }
            }
            if window.label()=="main" && matches!(event,tauri::WindowEvent::Destroyed) {
                window.app_handle().exit(0);
            }
        })
        .manage(game::GameRuntime::default())
        .manage(device::DeviceRuntime::default())
        .manage(self_update::Runtime::default())
        .invoke_handler(tauri::generate_handler![account::account_request, account::open_telegram, device::device_request, profile::launcher_profile,
            social_links::open_community,
            presence::discord_presence_enabled,presence::save_discord_presence,
            telemetry::telemetry_survey,telemetry::telemetry_feedback,game::game_start, game::game_status, game::game_logout, game::game_prepare, game::update_status, server_status::server_status,
            settings::launcher_settings,settings::launcher_memory,settings::save_launcher_settings,settings::open_game_folder,news::launcher_news,
            self_update::launcher_build_info,self_update::launcher_update_check,self_update::launcher_update_download,
            self_update::launcher_update_install,self_update::launcher_update_ready])
        .run(tauri::generate_context!())
        .expect("error while running RE:CREATED launcher");
}
mod account;
mod social_links;
mod presence;
mod presence_ipc;
mod news;
mod game;
mod game_session;
mod game_lock;
mod update;
mod device;
mod profile;
mod telemetry;
mod credential_store;
mod windows;
mod windows_notice;
mod server_status;
mod network;
mod settings;
mod self_update;
#[cfg(feature="window-smoke")] mod window_smoke;

