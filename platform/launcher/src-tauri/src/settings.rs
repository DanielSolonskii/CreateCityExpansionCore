use serde::{Deserialize,Serialize};
use std::fs;
#[derive(Clone,Deserialize,Serialize)]
#[serde(rename_all="camelCase",deny_unknown_fields)]
pub struct Settings { pub memory_gb:u8,pub width:u16,pub height:u16,pub hide_after_launch:bool,#[serde(default="yes")]pub auto_start:bool,#[serde(default="yes")]pub diagnostics_enabled:bool }
fn yes()->bool{true}
impl Default for Settings {fn default()->Self{Self{memory_gb:4,width:1280,height:720,hide_after_launch:false,auto_start:true,diagnostics_enabled:true}}}
impl Settings { fn validate(&self)->Result<(),String>{if !(4..=16).contains(&self.memory_gb) || ![(1280,720),(1600,900),(1920,1080)].contains(&(self.width,self.height)){return Err("Недопустимые параметры игры".into());}Ok(())} }
pub fn load()->Result<Settings,String>{
    match fs::read(crate::game::root()?.join("launcher-settings.json")){Ok(bytes)=>{let value:Settings=serde_json::from_slice(&bytes).map_err(|_|"Не удалось прочитать настройки")?;value.validate()?;Ok(value)},Err(e) if e.kind()==std::io::ErrorKind::NotFound=>Ok(Settings::default()),Err(_)=>Err("Не удалось открыть настройки".into())}
}
#[derive(Serialize)]
#[serde(rename_all="camelCase")]
pub struct MemoryInfo { total_gb:u64, pub max_gb:u8, recommended_gb:u8 }
fn memory_budget(total_gb:u64)->MemoryInfo {
    let reserve=if total_gb>=12 {4}else{2};
    let max=total_gb.saturating_sub(reserve).min(16) as u8;
    MemoryInfo {total_gb,max_gb:max,recommended_gb:if max>=8 {6}else{4.min(max)}}
}
#[tauri::command]
pub fn launcher_memory()->Result<MemoryInfo,String>{
    #[cfg(windows)] unsafe {
        use windows_sys::Win32::System::SystemInformation::{GlobalMemoryStatusEx,MEMORYSTATUSEX};
        let mut status:MEMORYSTATUSEX=std::mem::zeroed();status.dwLength=std::mem::size_of::<MEMORYSTATUSEX>() as u32;
        if GlobalMemoryStatusEx(&mut status)==0{return Err("Не удалось определить RAM компьютера".into());}
        Ok(memory_budget(status.ullTotalPhys.div_ceil(1024*1024*1024)))
    }
    #[cfg(not(windows))] {Err("Определение RAM доступно в Windows Launcher".into())}
}
pub fn validate_memory(memory_gb:u8)->Result<(),String>{
    let info=launcher_memory()?;
    if memory_gb<4 || memory_gb>info.max_gb{return Err(format!("Недостаточно памяти: выберите 4–{} ГБ в настройках; оставьте память для Windows",info.max_gb));}
    Ok(())
}
#[tauri::command]pub fn launcher_settings()->Result<Settings,String>{load()}
#[tauri::command]pub fn save_launcher_settings(settings:Settings)->Result<(),String>{
    settings.validate()?;validate_memory(settings.memory_gb)?;let root=crate::game::root()?;fs::create_dir_all(&root).map_err(|_|"Не удалось создать папку настроек")?;let temp=root.join(format!("launcher-settings-{}.tmp",uuid::Uuid::new_v4()));
    fs::write(&temp,serde_json::to_vec(&settings).map_err(|_|"Некорректные настройки")?).map_err(|_|"Не удалось сохранить настройки")?;fs::rename(temp,root.join("launcher-settings.json")).map_err(|_|"Не удалось применить настройки".to_string())
}
#[tauri::command]pub fn open_game_folder()->Result<(),String>{let dir=crate::game::root()?.join("instances/alpha");fs::create_dir_all(&dir).map_err(|_|"Не удалось открыть папку игры")?;std::process::Command::new("explorer.exe").arg(dir).spawn().map_err(|_|"Не удалось открыть проводник")?;Ok(())}
#[cfg(test)]mod tests {use super::*;#[test]fn validates_settings(){let s=Settings::default();assert!(s.validate().is_ok());let s:Settings=serde_json::from_slice(&serde_json::to_vec(&s).unwrap()).unwrap();assert_eq!(s.memory_gb,4);for memory_gb in [0,3,17,255]{assert!(Settings{memory_gb,..Settings::default()}.validate().is_err());}assert!(Settings{width:1,..Settings::default()}.validate().is_err());}}

#[cfg(test)]mod compatibility {use super::*;#[test]fn old_settings_enable_auto_start(){let s:Settings=serde_json::from_str(r#"{"memoryGb":4,"width":1280,"height":720,"hideAfterLaunch":false}"#).unwrap();assert!(s.auto_start);}}

#[cfg(test)]mod memory_tests{use super::*;
    #[test]fn reserves_memory_for_windows_and_caps_heap(){
        for(total,max,recommended)in[(4,2,2),(8,6,4),(16,12,6),(32,16,6)]{let info=memory_budget(total);assert_eq!(info.max_gb,max);assert_eq!(info.recommended_gb,recommended);}
    }
    #[test]#[cfg(windows)]fn reads_real_windows_memory(){assert!(launcher_memory().unwrap().total_gb>0);}
}
