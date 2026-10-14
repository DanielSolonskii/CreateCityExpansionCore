//! Credential-free helper keeps the inherited exclusive instance handle while Minecraft lives.
use std::{fs::File,process::{Command,Stdio}};
pub fn retain(lock:&File,pid:u32)->Result<(),String>{
    let mut command=Command::new(std::env::current_exe().map_err(|_|"Не найден Launcher")?);
    command.arg(format!("--recreated-game-lock-owner={pid}")).stdin(Stdio::from(lock.try_clone().map_err(|_|"Не удалось сохранить instance lock")?)).stdout(Stdio::null()).stderr(Stdio::null());
    #[cfg(windows)]{use std::os::windows::process::CommandExt;command.creation_flags(0x08000000);}
    command.spawn().map_err(|_|"Не удалось сохранить блокировку игры")?;Ok(())
}
pub fn helper()->bool{
    let Some(argument)=std::env::args().find_map(|a|a.strip_prefix("--recreated-game-lock-owner=").map(str::to_string))else{return false;};
    #[cfg(windows)] {
        use windows_sys::Win32::{Foundation::CloseHandle,System::Threading::{OpenProcess,WaitForSingleObject,INFINITE,PROCESS_SYNCHRONIZE}};
        if let Ok(pid)=argument.parse::<u32>(){if pid!=0{unsafe{let process=OpenProcess(PROCESS_SYNCHRONIZE,0,pid);if !process.is_null(){WaitForSingleObject(process,INFINITE);CloseHandle(process);}}}}
    }
    #[cfg(not(windows))]let _=argument;
    true
}

#[cfg(test)] mod tests {
    #[test] #[cfg(windows)]
    #[ignore = "Requires an explicitly built local Tauri candidate executable"]
    fn native_helper_retains_exclusive_lock_until_game_exit() {
        use std::{fs::OpenOptions,os::windows::fs::OpenOptionsExt,process::{Command,Stdio},time::{Duration,SystemTime,UNIX_EPOCH}};
        let binary=std::env::var_os("RECREATED_GAME_LOCK_TEST_BINARY").expect("candidate executable required");
        let path=std::env::temp_dir().join(format!("recreated-auth-lock-{}-{}",std::process::id(),SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_nanos()));
        let lock=OpenOptions::new().read(true).write(true).create_new(true).share_mode(0).open(&path).unwrap();
        let mut game=Command::new("powershell.exe").args(["-NoProfile","-NonInteractive","-Command","Start-Sleep -Seconds 8"]).stdin(Stdio::null()).stdout(Stdio::null()).stderr(Stdio::null()).spawn().unwrap();
        let mut helper=Command::new(binary).arg(format!("--recreated-game-lock-owner={}",game.id())).stdin(Stdio::from(lock.try_clone().unwrap())).stdout(Stdio::null()).stderr(Stdio::null()).spawn().unwrap();
        drop(lock);std::thread::sleep(Duration::from_millis(500));
        assert!(helper.try_wait().unwrap().is_none(),"native helper must outlive spawning handle");
        assert!(OpenOptions::new().read(true).write(true).share_mode(0).open(&path).is_err(),"second game cannot acquire live inherited lock");
        game.wait().unwrap();
        let deadline=std::time::Instant::now()+Duration::from_secs(10);
        while helper.try_wait().unwrap().is_none(){assert!(std::time::Instant::now()<deadline);std::thread::sleep(Duration::from_millis(100));}
        let released=OpenOptions::new().read(true).write(true).share_mode(0).open(&path).unwrap();drop(released);std::fs::remove_file(path).unwrap();
    }
}
