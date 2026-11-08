use tauri_plugin_opener::OpenerExt;
fn destination(kind:&str)->Result<&'static str,&'static str>{match kind{
    "discord"=>Ok("https://discord.gg/Qcks6wsBUX"),
    "telegram"=>Ok("https://t.me/recreating_bot"),
    _=>Err("Неизвестная ссылка сообщества"),
}}
#[tauri::command]
pub fn open_community(app:tauri::AppHandle,kind:String)->Result<(),&'static str>{
    app.opener().open_url(destination(&kind)?,None::<&str>).map_err(|_|"Не удалось открыть браузер")
}
#[cfg(test)]mod tests{use super::*;#[test]fn exact_public_destinations_only(){assert_eq!(destination("discord").unwrap(),"https://discord.gg/Qcks6wsBUX");assert_eq!(destination("telegram").unwrap(),"https://t.me/recreating_bot");for s in ["https://evil.test","file:///secret","discord?token=x",""]{assert!(destination(s).is_err());}}}
