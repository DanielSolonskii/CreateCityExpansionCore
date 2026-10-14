use serde::{Deserialize,Serialize};
use std::fs;
#[derive(Clone,Deserialize,Serialize)]
#[serde(rename_all="camelCase",deny_unknown_fields)]
pub struct Settings { pub memory_gb:u8,pub width:u16,pub height:u16,pub hide_after_launch:bool }
impl Default for Settings {fn default()->Self{Self{memory_gb:4,width:1280,height:720,hide_after_launch:false}}}
impl Settings { fn validate(&self)->Result<(),String>{if !(4..=16).contains(&self.memory_gb) || ![(1280,720),(1600,900),(1920,1080)].contains(&(self.width,self.height)){return Err("Недопустимые параметры игры".into());}Ok(())} }
pub fn load()->Result<Settings,String>{
    match fs::read(crate::game::root()?.join("launcher-settings.json")){Ok(bytes)=>{let value:Settings=serde_json::from_slice(&bytes).map_err(|_|"Не удалось прочитать настройки")?;value.validate()?;Ok(value)},Err(e) if e.kind()==std::io::ErrorKind::NotFound=>Ok(Settings::default()),Err(_)=>Err("Не удалось открыть настройки".into())}
}
#[tauri::command]pub fn launcher_settings()->Result<Settings,String>{load()}
#[tauri::command]pub fn save_launcher_settings(settings:Settings)->Result<(),String>{
    settings.validate()?;let root=crate::game::root()?;fs::create_dir_all(&root).map_err(|_|"Не удалось создать папку настроек")?;let temp=root.join("launcher-settings.json.tmp");
    fs::write(&temp,serde_json::to_vec(&settings).map_err(|_|"Некорректные настройки")?).map_err(|_|"Не удалось сохранить настройки")?;fs::rename(temp,root.join("launcher-settings.json")).map_err(|_|"Не удалось применить настройки".to_string())
}
#[tauri::command]pub fn open_game_folder()->Result<(),String>{let dir=crate::game::root()?.join("instances/alpha");fs::create_dir_all(&dir).map_err(|_|"Не удалось открыть папку игры")?;std::process::Command::new("explorer.exe").arg(dir).spawn().map_err(|_|"Не удалось открыть проводник")?;Ok(())}
#[cfg(test)]mod tests {use super::*;#[test]fn validates_settings(){let s=Settings::default();assert!(s.validate().is_ok());let s:Settings=serde_json::from_slice(&serde_json::to_vec(&s).unwrap()).unwrap();assert_eq!(s.memory_gb,4);for memory_gb in [0,3,17,255]{assert!(Settings{memory_gb,..Settings::default()}.validate().is_err());}assert!(Settings{width:1,..Settings::default()}.validate().is_err());}}
