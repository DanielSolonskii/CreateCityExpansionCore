//! Owner-bound, typed diagnostics. No secrets, logs, coordinates or chat in disk events.
use serde::{Serialize,Deserialize};
use serde_json::{json,Value};
use std::{fs,path::PathBuf,sync::{Mutex,atomic::{AtomicBool,Ordering}},time::{Instant,SystemTime,UNIX_EPOCH,Duration}};
static IO:Mutex<()>=Mutex::new(());
static STARTED:AtomicBool=AtomicBool::new(false);
static SURVEY:Mutex<Option<Attempt>>=Mutex::new(None);
#[derive(Clone)]pub struct Attempt{pub id:String,device:String,start:Instant}
#[derive(Serialize,Deserialize)]#[serde(rename_all="camelCase")]
struct Event{id:String,attempt_id:String,r#type:String,code:String,value:i64,elapsed_ms:u64,occurred_at_ms:u64,version:String}
#[derive(Serialize,Deserialize)]#[serde(rename_all="camelCase")]
struct Envelope{device_id:String,event:Event}
fn now()->u64{SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_millis() as u64}
fn directory()->Option<PathBuf>{Some(crate::game::root().ok()?.join(".telemetry-outbox"))}
impl Attempt{
 pub fn begin(enabled:bool)->Option<Self>{
  if !enabled{return None;}let base=crate::account::base_url(option_env!("VITE_ACCOUNT_API_BASE_URL").unwrap_or("")).ok()?;
  let device=crate::credential_store::read(&crate::credential_store::target(base.as_str())).ok()??;
  if device.revoke_pending||device.endpoint!=base.as_str(){return None;}
  let a=Self{id:uuid::Uuid::new_v4().to_string(),device:device.device_id,start:Instant::now()};a.event("LAUNCH_CLICK","OK",0);Some(a)
 }
 pub fn event(&self,kind:&str,code:&str,value:i64){
  let Some(dir)=directory()else{return;};let Ok(_guard)=IO.lock()else{return;};
  if fs::create_dir_all(&dir).is_err(){return;}
  if fs::read_dir(&dir).map(|d|d.count()).unwrap_or(256)>=256{return;}
  let id=uuid::Uuid::new_v4().to_string();let event=Event{id:id.clone(),attempt_id:self.id.clone(),r#type:kind.into(),code:code.into(),value,elapsed_ms:self.start.elapsed().as_millis().min(604800000) as u64,occurred_at_ms:now(),version:env!("CARGO_PKG_VERSION").into()};
  let payload=Envelope{device_id:self.device.clone(),event};if let Ok(bytes)=serde_json::to_vec(&payload){let tmp=dir.join(format!("{id}.tmp"));if fs::write(&tmp,bytes).is_ok(){let _=fs::rename(tmp,dir.join(format!("{:020}-{id}.json",now())));}}
 }
 pub fn ended(&self,code:Option<i32>){self.event("PROCESS_EXIT",if code==Some(0){"OK"}else if code.is_some(){"ABNORMAL"}else{"UNKNOWN"},code.unwrap_or(0) as i64);if self.start.elapsed()>=Duration::from_secs(600){*SURVEY.lock().unwrap()=Some(self.clone());}}
}
pub fn start(){if STARTED.swap(true,Ordering::SeqCst){return;}std::thread::spawn(||loop{let _=tauri::async_runtime::block_on(flush());std::thread::sleep(Duration::from_secs(5));});}
async fn flush()->Option<()>{
 if !crate::settings::load().ok()?.diagnostics_enabled{return None;}
 let base=crate::account::base_url(option_env!("VITE_ACCOUNT_API_BASE_URL").unwrap_or("")).ok()?;
 let credential=crate::credential_store::read(&crate::credential_store::target(base.as_str())).ok()??;
 if credential.revoke_pending||credential.endpoint!=base.as_str(){return None;}
 let dir=directory()?;let mut files=fs::read_dir(dir).ok()?.filter_map(Result::ok).map(|e|e.path()).filter(|p|p.extension().is_some_and(|e|e=="json")).collect::<Vec<_>>();files.sort();
 let mut selected=Vec::new();let mut events=Vec::new();
 for path in files.into_iter().take(256){
  if fs::symlink_metadata(&path).ok()?.file_type().is_symlink(){continue;}
  if fs::metadata(&path).ok()?.len()>4096{let _=fs::remove_file(path);continue;}
  let Ok(data)=fs::read(&path)else{continue;};let Ok(envelope)=serde_json::from_slice::<Envelope>(&data)else{let _=fs::remove_file(path);continue;};
  if now().saturating_sub(envelope.event.occurred_at_ms)>604800000{let _=fs::remove_file(path);continue;}
  if envelope.device_id!=credential.device_id{continue;}
  selected.push(path);events.push(envelope.event);if events.len()>=32{break;}
 }
 if events.is_empty(){return Some(());}
 let reply=crate::device::wire(&base,&format!("/api/auth/devices/{}/telemetry",credential.device_id),"POST",Some(&credential.secret),Some(json!({"events":events}))).await.ok()?;
 if reply.status==200||matches!(reply.status,400|401|403|409){for file in selected{let _=fs::remove_file(file);}}
 Some(())
}
fn current_ticket()->Option<Attempt>{
 if !crate::settings::load().ok()?.diagnostics_enabled{return None;}
 let a=SURVEY.lock().ok()?.clone()?;let base=crate::account::base_url(option_env!("VITE_ACCOUNT_API_BASE_URL").unwrap_or("")).ok()?;
 let device=crate::credential_store::read(&crate::credential_store::target(base.as_str())).ok()??;
 if device.device_id!=a.device||device.revoke_pending{return None;}
 let root=crate::game::root().ok()?;let last=fs::read_to_string(root.join("telemetry-survey-at")).ok().and_then(|s|s.parse::<u64>().ok()).unwrap_or(0);
 if now().saturating_sub(last)<604800000{return None;}Some(a)
}
#[tauri::command]pub fn telemetry_survey()->Value{json!({"available":current_ticket().is_some()})}
#[tauri::command]pub fn telemetry_feedback(reason:String,interest:String)->Result<(),String>{
 if !["SKIP","GOOD","LAGS","CONFUSING","NO_GOAL","OTHER"].contains(&reason.as_str())||!["SKIP","BUILD","CITY","INDUSTRY","TRADE","RAIL","SOCIAL"].contains(&interest.as_str()){return Err("Некорректный ответ".into());}
 let Some(a)=current_ticket()else{return Ok(());};*SURVEY.lock().unwrap()=None;
 if reason!="SKIP"{a.event("SURVEY_REASON",&reason,0);}if interest!="SKIP"{a.event("SURVEY_INTEREST",&interest,0);}
 if let Ok(root)=crate::game::root(){let _=fs::write(root.join("telemetry-survey-at"),now().to_string());}Ok(())
}
