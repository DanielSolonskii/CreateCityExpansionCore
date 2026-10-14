//! Native-only short capabilities and filtered IPC. Never serialize credentials to the WebView.
use crate::{account,credential_store};
use reqwest::{blocking::Client,Url,redirect::Policy};
use serde::{Deserialize,Serialize};
use std::{io::{Read,Write},time::{Duration,Instant}};

const PREFIX:&str="RECREATED_AUTH_V1 ";
#[derive(Deserialize)] #[serde(rename_all="camelCase")]
pub struct Profile { pub minecraft_uuid:String,pub join_name:String,pub current_name:String }
#[derive(Deserialize)] #[serde(rename_all="camelCase")]
struct Capability { bearer:String,username:String,profile:Profile }
#[derive(Deserialize,Serialize)] #[serde(rename_all="camelCase",deny_unknown_fields)]
struct Challenge { server_id:String,boot_id:String,connection_id:String,nonce:String }
pub struct Context { base:Url,capability:Capability,issued:Instant,pub host:String,pub port:u16,server_id:String }
fn token(value:&str)->bool{value.len()==43 && value.bytes().all(|b|b.is_ascii_alphanumeric() || b==b'_' || b==b'-')}
fn profile_valid(profile:&Profile)->bool {
    profile.join_name.len()==16 && profile.join_name.starts_with("cs") && profile.join_name.bytes().all(|b|b.is_ascii_lowercase() || b.is_ascii_digit())
        && profile.minecraft_uuid==crate::game::offline_uuid(&profile.join_name) && !profile.current_name.is_empty() && profile.current_name.chars().count()<=16
}
fn post<T:serde::de::DeserializeOwned>(base:&Url,path:&str,bearer:&str,body:Option<&impl Serialize>)->Result<T,String>{
    let client=Client::builder().timeout(Duration::from_secs(8)).connect_timeout(Duration::from_secs(2)).redirect(Policy::none()).build().map_err(|_|"Сервис игрового входа недоступен")?;
    let mut request=client.post(base.join(path).map_err(|_|"Некорректный адрес сервиса")?).bearer_auth(bearer).header("Cache-Control","no-store");
    if let Some(body)=body{request=request.json(body);}
    let response=request.send().map_err(|_|"Сервис игрового входа недоступен")?;
    if !response.status().is_success(){return Err("Игровой вход не разрешён. Проверьте авторизацию Launcher и повторите подключение.".into());}
    let mut bytes=Vec::new();response.take(32769).read_to_end(&mut bytes).map_err(|_|"Сервис игрового входа недоступен")?;
    if bytes.len()>32768{return Err("Некорректный ответ сервиса входа".into());}
    serde_json::from_slice(&bytes).map_err(|_|"Некорректный ответ сервиса входа".into())
}
fn device(base:&Url)->Result<credential_store::StoredDevice,String>{
    let entry=credential_store::read(&credential_store::target(base.as_str())).map_err(str::to_string)?.ok_or("Сначала подтвердите устройство Launcher")?;
    if entry.revoke_pending || entry.endpoint!=base.as_str() || uuid::Uuid::parse_str(&entry.device_id).is_err() || !token(&entry.secret){return Err("Устройство Launcher не авторизовано".into());}
    Ok(entry)
}
fn capability(base:&Url)->Result<Capability,String>{
    let entry=device(base)?;
    let result:Capability=post(base,&format!("api/auth/devices/{}/game-session-capability",entry.device_id),&entry.secret,None::<&serde_json::Value>)?;
    let parts:Vec<_>=result.bearer.split('.').collect();
    if parts.len()!=2 || uuid::Uuid::parse_str(parts[0]).is_err() || !token(parts[1]) || !profile_valid(&result.profile){return Err("Некорректный игровой профиль".into());}
    Ok(result)
}
impl Context {
    pub fn authorize(expected_username:&str)->Result<Self,String>{
        let host=option_env!("RECREATED_GAME_TLS_HOST").unwrap_or("").to_owned();
        let port=option_env!("RECREATED_GAME_TLS_PORT").unwrap_or("25565").parse::<u16>().map_err(|_|"Некорректный TLS порт")?;
        let server_id=option_env!("RECREATED_GAME_SERVER_ID").unwrap_or("").to_owned();
        if host.is_empty() || host.len()>253 || !host.bytes().all(|b|b.is_ascii_alphanumeric() || b==b'.' || b==b'-') || port==0
            || server_id.is_empty() || server_id.len()>64 || !server_id.bytes().all(|b|b.is_ascii_lowercase() || b.is_ascii_digit() || b==b'_' || b==b'-'){
            return Err("Game Session: защищённый игровой endpoint ещё не настроен в этой сборке Launcher".into());
        }
        let base=account::base_url(option_env!("VITE_ACCOUNT_API_BASE_URL").unwrap_or("")).map_err(str::to_string)?;
        let capability=capability(&base)?;
        if !capability.username.eq_ignore_ascii_case(expected_username){return Err("Подтверждённое устройство принадлежит другому аккаунту. Войдите заново.".into());}
        Ok(Self{base,capability,issued:Instant::now(),host,port,server_id})
    }
    pub fn join_name(&self)->&str{&self.capability.profile.join_name}
    pub fn is_protocol_line(line:&str)->bool{line.contains(PREFIX)}
    pub fn respond(&mut self,line:&str,input:&mut impl Write)->Result<(),String>{
        let offset=line.find(PREFIX).ok_or("Отсутствует запрос входа")?;
        let text=line[offset+PREFIX.len()..].trim();
        if text.len()>1024{return Err("Превышен размер запроса входа".into());}
        let challenge:Challenge=serde_json::from_str(text).map_err(|_|"Некорректный запрос входа")?;
        if challenge.server_id!=self.server_id || uuid::Uuid::parse_str(&challenge.boot_id).is_err() || uuid::Uuid::parse_str(&challenge.connection_id).is_err() || !token(&challenge.nonce){return Err("Неверный игровой сервер или запрос входа".into());}
        device(&self.base)?; // offline logout tombstone also blocks cached capabilities
        if self.issued.elapsed()>=Duration::from_secs(570){
            let renewed=capability(&self.base)?;
            if renewed.username!=self.capability.username || renewed.profile.join_name!=self.capability.profile.join_name || renewed.profile.minecraft_uuid!=self.capability.profile.minecraft_uuid{return Err("Игровой профиль изменился; перезапустите игру".into());}
            self.capability=renewed;self.issued=Instant::now();
        }
        #[derive(Deserialize)] #[serde(rename_all="camelCase")] struct Ticket{game_session_id:String,secret:String}
        let ticket:Ticket=post(&self.base,"api/game-sessions",&self.capability.bearer,Some(&challenge))?;
        if uuid::Uuid::parse_str(&ticket.game_session_id).is_err() || !token(&ticket.secret){return Err("Некорректное разрешение входа".into());}
        let response=serde_json::json!({"connectionId":challenge.connection_id,"gameSessionId":ticket.game_session_id,"secret":ticket.secret});
        serde_json::to_writer(&mut *input,&response).map_err(|_|"Канал игрового входа закрыт")?;
        input.write_all(b"\n").and_then(|_|input.flush()).map_err(|_|"Канал игрового входа закрыт".into())
    }
}
#[cfg(test)] mod tests {
    use super::*;
    #[test] #[cfg(windows)] fn native_broker_vault_http_private_pipe_and_logout() {
        use std::{net::TcpListener,thread,io::{BufRead,BufReader}};
        let listener=TcpListener::bind("127.0.0.1:0").unwrap();
        let base=Url::parse(&format!("http://{}/",listener.local_addr().unwrap())).unwrap();
        let target=credential_store::target(base.as_str());
        let secret="d".repeat(43);let ticket_secret="t".repeat(43);
        let device_id=uuid::Uuid::from_u128(1).to_string();
        let entry=credential_store::StoredDevice{device_id:device_id.clone(),secret:secret.clone(),endpoint:base.as_str().into(),revoke_pending:false};
        credential_store::write(&target,&entry).unwrap();
        struct Cleanup(String);impl Drop for Cleanup{fn drop(&mut self){let _=credential_store::delete(&self.0);}}
        let _cleanup=Cleanup(target.clone());
        let cap=format!("{}.{}",uuid::Uuid::from_u128(2),"c".repeat(43));
        let expected_cap=cap.clone();let expected_secret=secret.clone();let expected_ticket=ticket_secret.clone();
        let server=thread::spawn(move||{
            for step in 0..2 {
                let (mut socket,_)=listener.accept().unwrap();socket.set_read_timeout(Some(Duration::from_secs(10))).unwrap();
                let mut input=BufReader::new(socket.try_clone().unwrap());let mut headers=String::new();let mut length=0;
                loop{let mut line=String::new();input.read_line(&mut line).unwrap();if line=="\r\n"{break;}
                    if let Some(value)=line.to_ascii_lowercase().strip_prefix("content-length:"){length=value.trim().parse::<usize>().unwrap();}headers.push_str(&line);}
                let mut body=vec![0;length];input.read_exact(&mut body).unwrap();
                let response=if step==0{
                    assert!(headers.contains(&format!("/api/auth/devices/{device_id}/game-session-capability")));
                    assert!(headers.contains(&format!("Bearer {expected_secret}")));
                    serde_json::json!({"bearer":expected_cap,"username":"Engineer","profile":{"joinName":"cs12345678901234","minecraftUuid":crate::game::offline_uuid("cs12345678901234"),"currentName":"Engineer"}})
                }else{
                    assert!(headers.contains("/api/game-sessions"));assert!(headers.contains(&format!("Bearer {expected_cap}")));
                    assert!(!headers.contains(&expected_secret));
                    let challenge:Challenge=serde_json::from_slice(&body).unwrap();assert_eq!(challenge.server_id,"local-test");
                    serde_json::json!({"gameSessionId":uuid::Uuid::from_u128(3).to_string(),"secret":expected_ticket})
                }.to_string();
                write!(socket,"HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",response.len(),response).unwrap();
            }
        });
        let capability=capability(&base).unwrap();
        let mut context=Context{base,capability,issued:Instant::now(),host:"localhost".into(),port:25567,server_id:"local-test".into()};
        let challenge=Challenge{server_id:"local-test".into(),boot_id:uuid::Uuid::from_u128(4).to_string(),connection_id:uuid::Uuid::from_u128(5).to_string(),nonce:"n".repeat(43)};
        let line=format!("{PREFIX}{}",serde_json::to_string(&challenge).unwrap());let mut pipe=Vec::new();
        context.respond(&line,&mut pipe).unwrap();let proof:serde_json::Value=serde_json::from_slice(&pipe).unwrap();
        assert_eq!(proof["secret"],ticket_secret);assert_eq!(proof["connectionId"],challenge.connection_id);
        assert!(!String::from_utf8_lossy(&pipe).contains(&secret));assert!(!String::from_utf8_lossy(&pipe).contains(&cap));
        server.join().unwrap();
        let mut revoked=entry;revoked.revoke_pending=true;credential_store::write(&target,&revoked).unwrap();
        let mut blocked=Vec::new();assert!(context.respond(&line,&mut blocked).is_err());assert!(blocked.is_empty());
        let wrong=line.replace("local-test","other-world");assert!(context.respond(&wrong,&mut blocked).is_err());
    }
    #[test] fn rejects_spoofed_profile_and_malformed_challenge(){
        let name="cs12345678901234";
        let mut p=Profile{join_name:name.into(),minecraft_uuid:crate::game::offline_uuid(name),current_name:"Engineer".into()};
        assert!(profile_valid(&p));p.minecraft_uuid=uuid::Uuid::nil().to_string();assert!(!profile_valid(&p));
        assert!(serde_json::from_str::<Challenge>(r#"{"serverId":"world","bootId":"x","connectionId":"x","nonce":"x","secret":"bad"}"#).is_err());
        assert!(Context::is_protocol_line("[STDOUT]: RECREATED_AUTH_V1 {}"));
        assert!(!token("password"));
    }
}
