//! Native Windows tray balloon. Text is fixed, never credentials/server content.
#[cfg(windows)]
pub fn show(app:&tauri::AppHandle,phase:&crate::game::Phase){
    use tauri::Manager;
    use windows_sys::Win32::UI::{Shell::*,WindowsAndMessaging::*};
    let Some(window)=app.get_webview_window("main")else{return;};
    let Ok(hwnd)=window.hwnd()else{return;};
    let mut data:NOTIFYICONDATAW=unsafe{std::mem::zeroed()};data.cbSize=std::mem::size_of::<NOTIFYICONDATAW>()as u32;
    data.hWnd=hwnd.0 as _;data.uID=0x5243;data.uFlags=NIF_INFO|NIF_ICON;data.dwInfoFlags=if *phase==crate::game::Phase::FAILED{NIIF_ERROR}else{NIIF_INFO};
    data.hIcon=unsafe{LoadIconW(std::ptr::null_mut(),IDI_APPLICATION)};
    let title="RE:CREATED".encode_utf16().collect::<Vec<_>>();data.szInfoTitle[..title.len()].copy_from_slice(&title);
    let body=if *phase==crate::game::Phase::FAILED{"Не удалось подготовить игру. Откройте Launcher."}else if *phase==crate::game::Phase::RUNNING{"Minecraft запущен."}else{"Сборка готова к запуску."}.encode_utf16().collect::<Vec<_>>();data.szInfo[..body.len()].copy_from_slice(&body);
    unsafe{Shell_NotifyIconW(NIM_ADD,&data);}
    let handle=hwnd.0 as usize;
    std::thread::spawn(move||{std::thread::sleep(std::time::Duration::from_secs(12));let mut d:NOTIFYICONDATAW=unsafe{std::mem::zeroed()};d.cbSize=std::mem::size_of::<NOTIFYICONDATAW>()as u32;d.hWnd=handle as _;d.uID=0x5243;unsafe{Shell_NotifyIconW(NIM_DELETE,&d);}});
}
#[cfg(not(windows))]pub fn show(_: &tauri::AppHandle,_:&crate::game::Phase){}
