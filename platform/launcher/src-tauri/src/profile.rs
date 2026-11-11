//! Owner-only profile; device credential stays in native credential storage.
use serde_json::{Value,json};
use reqwest::{Url,redirect::Policy};
use std::{fs,io::{Read,Write},time::Duration};
use base64::Engine;
fn skin_url(raw:&str)->Option<Url>{
    let u=Url::parse(raw).ok()?;let h=u.path().strip_prefix("/texture/")?;
    if u.scheme()!="https"||u.host_str()!=Some("textures.minecraft.net")||u.port().is_some()||!u.username().is_empty()||u.password().is_some()||u.query().is_some()||u.fragment().is_some()||!(32..=64).contains(&h.len())||!h.bytes().all(|b|b.is_ascii_hexdigit()&&!b.is_ascii_uppercase()){return None;}Some(u)
}
fn png(bytes:&[u8])->bool{bytes.len()>=24&&bytes.len()<=131072&&bytes[..8]==[137,80,78,71,13,10,26,10]&&bytes[12..16]==*b"IHDR"&&u32::from_be_bytes(bytes[16..20].try_into().unwrap())==64&&matches!(u32::from_be_bytes(bytes[20..24].try_into().unwrap()),32|64)}
async fn texture(url:Url)->Option<String>{
    let root=crate::game::root().ok()?;fs::create_dir_all(&root).ok()?;let hash=url.path().strip_prefix("/texture/")?.to_owned();
    let cache=crate::update::safe(&root,&format!(".recreated/avatars/{hash}.png")).ok()?;
    let existing=fs::File::open(&cache).ok().and_then(|f|{let mut b=Vec::new();f.take(131073).read_to_end(&mut b).ok()?;png(&b).then_some(b)});
    let bytes=if let Some(b)=existing{b}else{
        let response=reqwest::Client::builder().redirect(Policy::none()).timeout(Duration::from_secs(6)).build().ok()?.get(url).send().await.ok()?;
        if !response.status().is_success()||response.content_length().is_some_and(|n|n>131072){return None;}
        let mut stream=response;let mut b=Vec::new();while let Some(chunk)=stream.chunk().await.ok()?{if b.len()+chunk.len()>131072{return None;}b.extend_from_slice(&chunk);}if !png(&b){return None;}
        if let Some(parent)=cache.parent(){if fs::create_dir_all(parent).is_ok(){if let Ok(temp)=crate::update::safe(&root,&format!(".recreated/avatars/{hash}.tmp")){if let Ok(mut f)=fs::OpenOptions::new().create_new(true).write(true).open(&temp){if f.write_all(&b).is_ok(){drop(f);let _=fs::rename(&temp,&cache);}else{drop(f);let _=fs::remove_file(&temp);}}}}}
        b
    };Some(format!("data:image/png;base64,{}",base64::engine::general_purpose::STANDARD.encode(bytes)))
}
#[tauri::command]
pub async fn launcher_profile(runtime:tauri::State<'_,crate::game::GameRuntime>)->Result<Value,String>{
    let username=runtime.account.lock().unwrap().clone().ok_or("Сначала войдите в аккаунт")?;
    let base=crate::account::base_url(option_env!("VITE_ACCOUNT_API_BASE_URL").unwrap_or("")).map_err(str::to_string)?;
    let entry=crate::credential_store::read(&crate::credential_store::target(base.as_str())).map_err(str::to_string)?.ok_or("Устройство не подтверждено")?;
    if entry.endpoint!=base.as_str()||entry.revoke_pending||uuid::Uuid::parse_str(&entry.device_id).is_err(){return Err("Устройство не подтверждено".into());}
    let response=crate::device::wire(&base,&format!("/api/auth/devices/{}/profile",entry.device_id),"GET",Some(&entry.secret),None).await.map_err(str::to_string)?;
    if response.status!=200{return Err("Не удалось обновить игровой профиль".into());}
    let profile=response.body;let skin=if let Some(url)=profile["skinUrl"].as_str().and_then(skin_url){texture(url).await}else{None};
    if runtime.account.lock().unwrap().as_ref()!=Some(&username){return Err("Аккаунт изменился".into());}
    Ok(json!({"profile":profile,"skinData":skin}))
}
#[cfg(test)]mod tests{use super::*;#[test]fn external_skin_sources_are_rejected(){assert!(skin_url(&format!("https://textures.minecraft.net/texture/{}","a".repeat(64))).is_some());for u in ["https://127.0.0.1/texture/aaaa","http://textures.minecraft.net/texture/aaaa","https://textures.minecraft.net.evil/texture/aaaa","https://user@textures.minecraft.net/texture/aaaa"]{assert!(skin_url(u).is_none());}assert!(!png(b"not a skin"));}}
