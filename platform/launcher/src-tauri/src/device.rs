use crate::credential_store::{self, StoredDevice};
use reqwest::{Client,Url,redirect::Policy};
use serde::{Deserialize,Serialize};
use serde_json::{json,Value};
use std::{sync::{Mutex,atomic::{AtomicU64,Ordering}},time::Duration};

#[derive(Default)]
pub struct DeviceRuntime { pending:Mutex<Option<Pending>>, generation:AtomicU64, serial:tokio::sync::Mutex<()> }
#[derive(Clone)]
struct Pending { credential:StoredDevice, polling:String }
#[derive(Deserialize,Clone,Copy)] #[serde(rename_all="lowercase")]
pub enum DeviceOperation { Begin,Poll,Restore,Logout,Cancel }
#[derive(Serialize)]
pub struct DeviceResponse { pub(crate) status:u16,pub(crate) body:Value }
fn token(value:&str)->bool { value.len()==43 && value.bytes().all(|b|b.is_ascii_alphanumeric() || b==b'_' || b==b'-') }
fn validate_account(value:&Value)->bool {
    value["status"]=="AUTHENTICATED" && value["platformUserId"].as_str().is_some_and(|id|uuid::Uuid::parse_str(id).is_ok()) &&
        value["username"].as_str().is_some_and(|name| (3..=32).contains(&name.len()) && name.bytes().all(|b|b.is_ascii_lowercase() || b.is_ascii_digit() || b==b'_'))
}
pub(crate) async fn wire(base:&Url,path:&str,method:&str,bearer:Option<&str>,body:Option<Value>)->Result<DeviceResponse,&'static str> {
    let client=Client::builder().timeout(Duration::from_secs(15)).connect_timeout(Duration::from_secs(5)).redirect(Policy::none())
        .build().map_err(|_|"Backend unavailable")?;
    let url=base.join(path).map_err(|_|"Invalid Backend endpoint")?;
    let mut request=if method=="GET" { client.get(url) } else { client.post(url) };
    if let Some(token)=bearer { request=request.bearer_auth(token); }
    if let Some(body)=body { request=request.json(&body); }
    let response=crate::network::send(request.header("Cache-Control","no-store")).await.map_err(|_|"Backend unavailable")?;
    let status=response.status().as_u16();
    if response.content_length().is_some_and(|n|n>32768) { return Err("Invalid Backend response"); }
    let bytes=response.bytes().await.map_err(|_|"Backend unavailable")?;
    if bytes.len()>32768 { return Err("Invalid Backend response"); }
    Ok(DeviceResponse{status,body:serde_json::from_slice(&bytes).unwrap_or(Value::Null)})
}
async fn revoke(base:&Url,entry:&StoredDevice)->Result<bool,&'static str> {
    let result=wire(base,&format!("/api/auth/devices/{}/revoke",entry.device_id),"POST",Some(&entry.secret),None).await?;
    Ok(matches!(result.status,204|401|404))
}
fn valid_stored(entry:&StoredDevice,base:&Url)->bool {
    entry.endpoint==base.as_str() && uuid::Uuid::parse_str(&entry.device_id).is_ok() && token(&entry.secret)
}
fn label()->String {
    let name=std::env::var("COMPUTERNAME").unwrap_or_else(|_|"Windows PC".into());
    let safe:String=name.chars().filter(|c|c.is_alphanumeric() || " ._-".contains(*c)).take(64).collect();
    if safe.is_empty() { "Windows PC".into() } else { safe }
}

#[tauri::command]
pub async fn device_request(operation:DeviceOperation,username:Option<String>,password:Option<String>,
    devices:tauri::State<'_,DeviceRuntime>,runtime:tauri::State<'_,crate::game::GameRuntime>)->Result<DeviceResponse,&'static str> {
    let base=crate::account::base_url(option_env!("VITE_ACCOUNT_API_BASE_URL").unwrap_or(""))?;
    transition(operation,username,password,&devices,&runtime,&base).await
}

async fn transition(operation:DeviceOperation,username:Option<String>,password:Option<String>,
    devices:&DeviceRuntime,runtime:&crate::game::GameRuntime,base:&Url)->Result<DeviceResponse,&'static str> {
    // Serialize IPC auth transitions: restore/poll cannot resurrect a credential after logout.
    let _serial=devices.serial.lock().await;
    let target=credential_store::target(base.as_str());
    let epoch=devices.generation.load(Ordering::SeqCst);
    match operation {
        DeviceOperation::Logout | DeviceOperation::Cancel => {
            let pending=devices.pending.lock().unwrap().take();
            if matches!(operation,DeviceOperation::Cancel) && pending.is_none() {
                return Ok(DeviceResponse{status:200,body:json!({"cancelled":true})});
            }
            devices.generation.fetch_add(1,Ordering::SeqCst);
            *runtime.account.lock().unwrap()=None;
            let entry=if let Some(pending)=pending { Some(pending.credential) } else { credential_store::read(&target)? };
            if let Some(mut entry)=entry {
                if !valid_stored(&entry,&base) { return Err("Invalid secure credential"); }
                // Persist a no-auto-login tombstone BEFORE networking, including offline logout.
                entry.revoke_pending=true; credential_store::write(&target,&entry)?;
                if revoke(&base,&entry).await.unwrap_or(false) { credential_store::delete(&target)?; }
            }
            Ok(DeviceResponse{status:200,body:json!({"loggedOut":true})})
        }
        DeviceOperation::Restore => {
            *runtime.account.lock().unwrap()=None;
            let Some(entry)=credential_store::read(&target)? else { return Ok(DeviceResponse{status:200,body:Value::Null}); };
            if !valid_stored(&entry,&base) { return Err("Invalid secure credential"); }
            if entry.revoke_pending {
                if revoke(&base,&entry).await.unwrap_or(false) { credential_store::delete(&target)?; }
                return Ok(DeviceResponse{status:200,body:Value::Null});
            }
            let result=wire(&base,&format!("/api/auth/devices/{}/authenticate",entry.device_id),"POST",Some(&entry.secret),None).await?;
            if devices.generation.load(Ordering::SeqCst)!=epoch { return Err("Login cancelled"); }
            if matches!(result.status,401|404) { credential_store::delete(&target)?; return Ok(DeviceResponse{status:200,body:Value::Null}); }
            if result.status==200 && validate_account(&result.body) { *runtime.account.lock().unwrap()=result.body["username"].as_str().map(str::to_owned); }
            else if result.status==200 { return Err("Invalid account response"); }
            Ok(result)
        }
        DeviceOperation::Begin => {
            let epoch=devices.generation.fetch_add(1,Ordering::SeqCst)+1;
            *runtime.account.lock().unwrap()=None;
            *devices.pending.lock().unwrap()=None;
            if let Some(mut old)=credential_store::read(&target)? {
                if !valid_stored(&old,&base) { return Err("Invalid secure credential"); }
                old.revoke_pending=true; credential_store::write(&target,&old)?;
                if !revoke(&base,&old).await? { return Err("Previous device revocation pending"); }
                credential_store::delete(&target)?;
            }
            let result=wire(&base,"/api/auth/devices","POST",None,Some(json!({"username":username.ok_or("Missing username")?,
                "password":password.ok_or("Missing password")?,"label":label()}))).await?;
            if result.status!=201 { return Ok(result); }
            let id=result.body["deviceId"].as_str().ok_or("Invalid device response")?;
            let secret=result.body["deviceCredential"].as_str().ok_or("Invalid device response")?;
            let polling=result.body["pollingToken"].as_str().ok_or("Invalid device response")?;
            if uuid::Uuid::parse_str(id).is_err() || !token(secret) || !token(polling) { return Err("Invalid device response"); }
            if devices.generation.load(Ordering::SeqCst)!=epoch { return Err("Login cancelled"); }
            *devices.pending.lock().unwrap()=Some(Pending{credential:StoredDevice{device_id:id.into(),secret:secret.into(),endpoint:base.to_string(),revoke_pending:false},polling:polling.into()});
            // Device credential/polling secret NEVER cross Rust -> WebView IPC.
            Ok(DeviceResponse{status:201,body:json!({"telegramDeepLink":result.body["telegramDeepLink"],"expiresAt":result.body["expiresAt"]})})
        }
        DeviceOperation::Poll => {
            let pending=devices.pending.lock().unwrap().clone().ok_or("No pending device request")?;
            let result=wire(&base,&format!("/api/auth/devices/{}",pending.credential.device_id),"GET",Some(&pending.polling),None).await?;
            if result.status!=200 || result.body["state"]!="TRUSTED" { return Ok(result); }
            let account=wire(&base,&format!("/api/auth/devices/{}/authenticate",pending.credential.device_id),"POST",Some(&pending.credential.secret),None).await?;
            if account.status!=200 || !validate_account(&account.body) { return Err("Device no longer trusted"); }
            if devices.generation.load(Ordering::SeqCst)!=epoch { return Err("Login cancelled"); }
            credential_store::write(&target,&pending.credential)?;
            *runtime.account.lock().unwrap()=account.body["username"].as_str().map(str::to_owned);
            *devices.pending.lock().unwrap()=None;
            Ok(account)
        }
    }
}

#[cfg(test)] mod tests {
    use super::*;
    #[test] fn account_and_credential_validation_fail_closed() {
        let base=Url::parse("https://api.example.test/").unwrap();
        let mut entry=StoredDevice{device_id:"12345678-1234-1234-1234-123456789abc".into(),secret:"s".repeat(43),endpoint:base.to_string(),revoke_pending:false};
        assert!(valid_stored(&entry,&base)); entry.endpoint="http://127.0.0.1:8080/".into(); assert!(!valid_stored(&entry,&base));
        assert!(!validate_account(&json!({"status":"PENDING","username":"engineer"})));
        assert!(!token("password"));
    }
    #[cfg(windows)]
    #[test] fn complete_device_flow_with_real_http_and_windows_vault() {
        use std::{io::{Read,Write},net::TcpListener,sync::{Arc,atomic::AtomicBool},thread};
        let listener=TcpListener::bind("127.0.0.1:0").unwrap();
        listener.set_nonblocking(true).unwrap();
        let base=Url::parse(&format!("http://{}/",listener.local_addr().unwrap())).unwrap();
        let target=credential_store::target(base.as_str());
        let id="12345678-1234-1234-1234-123456789abc";
        let secret="s".repeat(43); let polling="p".repeat(43);
        let approved=Arc::new(AtomicBool::new(false)); let revoked=Arc::new(AtomicBool::new(false));
        let stop=Arc::new(AtomicBool::new(false));
        let (a,r,s)=(approved.clone(),revoked.clone(),stop.clone());
        let worker=thread::spawn(move || {
            while !s.load(Ordering::SeqCst) {
                let Ok((mut stream,_))=listener.accept() else { thread::sleep(Duration::from_millis(5)); continue; };
                stream.set_nonblocking(false).unwrap();
                stream.set_read_timeout(Some(Duration::from_secs(3))).unwrap();
                let mut request=Vec::new(); let mut buf=[0u8;1024];
                while !request.windows(4).any(|w|w==b"\r\n\r\n") {
                    let n=stream.read(&mut buf).unwrap(); if n==0 {break;} request.extend_from_slice(&buf[..n]);
                }
                let header_end=request.windows(4).position(|w|w==b"\r\n\r\n").unwrap()+4;
                let headers=String::from_utf8_lossy(&request[..header_end]).to_lowercase();
                let length=headers.lines().find_map(|line|line.strip_prefix("content-length: ").and_then(|n|n.parse::<usize>().ok())).unwrap_or(0);
                while request.len()<header_end+length { let n=stream.read(&mut buf).unwrap(); if n==0 {break;} request.extend_from_slice(&buf[..n]); }
                let path=headers.split_whitespace().nth(1).unwrap();
                let (status,body)=if path=="/api/auth/devices" {
                    (201,json!({"deviceId":id,"deviceCredential":"s".repeat(43),"pollingToken":"p".repeat(43),
                        "telegramDeepLink":format!("https://t.me/create_test_bot?start=d_{}","v".repeat(43)),"expiresAt":"2099-01-01T00:00:00Z"}))
                } else if path.ends_with("/authenticate") {
                    assert!(headers.contains(&format!("authorization: bearer {}","s".repeat(43))));
                    if a.load(Ordering::SeqCst) && !r.load(Ordering::SeqCst) { (200,json!({"status":"AUTHENTICATED","username":"engineer","platformUserId":id})) }
                    else { (401,json!({"code":"INVALID_DEVICE"})) }
                } else if path.ends_with("/revoke") {
                    assert!(headers.contains(&format!("authorization: bearer {}","s".repeat(43))));
                    r.store(true,Ordering::SeqCst); (204,Value::Null)
                } else {
                    assert!(headers.contains(&format!("authorization: bearer {}","p".repeat(43))));
                    (200,json!({"state":if a.load(Ordering::SeqCst){"TRUSTED"}else{"PENDING"}}))
                };
                let bytes=if status==204 {Vec::new()}else{serde_json::to_vec(&body).unwrap()};
                let mut response=format!("HTTP/1.1 {status} OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",bytes.len()).into_bytes();
                response.extend(bytes); stream.write_all(&response).unwrap();
            }
        });
        tauri::async_runtime::block_on(async {
            let devices=DeviceRuntime::default(); let runtime=crate::game::GameRuntime::default();
            let start=transition(DeviceOperation::Begin,Some("engineer".into()),Some("test-password-123".into()),&devices,&runtime,&base).await.unwrap();
            assert_eq!(start.status,201);
            let ipc=serde_json::to_string(&start.body).unwrap(); assert!(!ipc.contains(&secret)); assert!(!ipc.contains(&polling));
            assert!(credential_store::read(&target).unwrap().is_none());
            assert_eq!(transition(DeviceOperation::Poll,None,None,&devices,&runtime,&base).await.unwrap().body["state"],"PENDING");
            assert!(runtime.account.lock().unwrap().is_none());
            approved.store(true,Ordering::SeqCst);
            assert_eq!(transition(DeviceOperation::Poll,None,None,&devices,&runtime,&base).await.unwrap().body["status"],"AUTHENTICATED");
            assert!(credential_store::read(&target).unwrap().is_some());
            let fresh=DeviceRuntime::default(); let fresh_game=crate::game::GameRuntime::default();
            assert_eq!(transition(DeviceOperation::Restore,None,None,&fresh,&fresh_game,&base).await.unwrap().body["username"],"engineer");
            revoked.store(true,Ordering::SeqCst);
            assert!(transition(DeviceOperation::Restore,None,None,&fresh,&fresh_game,&base).await.unwrap().body.is_null());
            assert!(fresh_game.account.lock().unwrap().is_none());
            assert!(credential_store::read(&target).unwrap().is_none());
            // A durable offline-logout tombstone must never authenticate even if server trust remains active.
            revoked.store(false,Ordering::SeqCst);
            let entry=StoredDevice{device_id:id.into(),secret:secret.clone(),endpoint:base.to_string(),revoke_pending:true};
            credential_store::write(&target,&entry).unwrap();
            assert!(transition(DeviceOperation::Restore,None,None,&fresh,&fresh_game,&base).await.unwrap().body.is_null());
            assert!(revoked.load(Ordering::SeqCst));
            credential_store::write(&target,&StoredDevice{revoke_pending:false,..entry}).unwrap();
            transition(DeviceOperation::Logout,None,None,&fresh,&fresh_game,&base).await.unwrap();
            assert!(credential_store::read(&target).unwrap().is_none());
            assert!(fresh_game.account.lock().unwrap().is_none());
        });
        stop.store(true,Ordering::SeqCst); worker.join().unwrap();
        // Real offline logout (listener gone): keep a protected revocation tombstone, never auto-login.
        credential_store::write(&target,&StoredDevice{device_id:id.into(),secret,endpoint:base.to_string(),revoke_pending:false}).unwrap();
        tauri::async_runtime::block_on(async {
            let devices=DeviceRuntime::default(); let runtime=crate::game::GameRuntime::default();
            *runtime.account.lock().unwrap()=Some("engineer".into());
            transition(DeviceOperation::Logout,None,None,&devices,&runtime,&base).await.unwrap();
            assert!(credential_store::read(&target).unwrap().unwrap().revoke_pending);
            assert!(runtime.account.lock().unwrap().is_none());
            assert!(transition(DeviceOperation::Restore,None,None,&devices,&runtime,&base).await.unwrap().body.is_null());
        });
        credential_store::delete(&target).unwrap();
    }
}
