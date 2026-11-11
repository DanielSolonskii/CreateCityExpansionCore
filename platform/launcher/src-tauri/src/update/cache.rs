//! Only content-addressed assets may use persistent metadata acceleration.
//! Code is always hashed. Launch proofs exist only for one locked operation.
use super::*;
use std::{collections::HashMap,time::{SystemTime,UNIX_EPOCH}};
#[derive(Clone,Debug,PartialEq,Serialize,Deserialize)]
pub(super) struct Stamp { size:u64,modified:u128,created:Option<u128> }
pub(super) fn stamp(path:&Path)->Result<Stamp,String>{
    let m=fs::symlink_metadata(path).map_err(|_|"Не удалось проверить файл")?;
    if !m.is_file()||is_link(&m){return Err("Небезопасный управляемый файл".into());}
    Ok(Stamp{size:m.len(),modified:m.modified().map_err(|_|"Время файла недоступно")?.duration_since(UNIX_EPOCH).map_err(|_|"Некорректное время файла")?.as_nanos(),created:m.created().ok().and_then(|v|v.duration_since(UNIX_EPOCH).ok()).map(|v|v.as_nanos())})
}
#[derive(Clone,Serialize,Deserialize)]
struct Record { sha:String,stamp:Stamp,verified:u64 }
#[derive(Default,Serialize,Deserialize)]
struct Index { records:HashMap<String,Record> }
pub(crate) struct Proof { pub version:String, records:Mutex<HashMap<String,Record>>,old:Index,full:bool }
fn now()->u64{SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_secs()}
fn asset(path:&str)->bool{
    let parts=path.split('/').collect::<Vec<_>>();
    parts.len()==4&&parts[0]=="assets"&&parts[1]=="objects"&&parts[2].len()==2&&parts[3].len()==40&&parts[3].starts_with(parts[2])&&parts[3].bytes().all(|b|b.is_ascii_hexdigit()&&!b.is_ascii_uppercase())
}
impl Proof {
    pub(super) fn new(root:&Path,full:bool)->Self{
        let old=if full{Index::default()}else{safe(root,".recreated/asset-index-v1.json").ok().and_then(|p|{let f=File::open(p).ok()?;let mut bytes=Vec::new();f.take(4*1024*1024+1).read_to_end(&mut bytes).ok()?;if bytes.len()>4*1024*1024{return None;}serde_json::from_slice::<Index>(&bytes).ok()}).unwrap_or_default()};
        Self{version:String::new(),records:Mutex::new(HashMap::new()),old,full}
    }
    pub(super) fn remember(&self,e:&Entry,s:Stamp){self.records.lock().unwrap().insert(e.path.clone(),Record{sha:e.sha256.clone(),stamp:s,verified:now()});}
    pub(super) fn relocated(&self,e:&Entry,path:&Path)->Result<(),String>{
        let s=stamp(path)?;let mut records=self.records.lock().unwrap();let record=records.get_mut(&e.path).ok_or("Нет подтверждения установки")?;
        if s.size!=record.stamp.size||s.modified!=record.stamp.modified{return Err("Файл изменился во время установки".into());}record.stamp=s;Ok(())
    }
    pub(crate) fn file(&self,root:&Path,path:&str,expected:Option<&str>)->Result<PathBuf,String>{
        let p=safe(root,path)?;let s=stamp(&p)?;let records=self.records.lock().unwrap();
        let record=records.get(path).ok_or("Файл запуска отсутствует в проверенном release")?;
        if record.stamp!=s||expected.is_some_and(|v|v!=record.sha){return Err(format!("Файл изменился после проверки: {path}"));}Ok(p)
    }
    pub(super) fn save(&self,root:&Path){
        let records=self.records.lock().unwrap().iter().filter(|(p,_)|asset(p)).map(|(p,v)|(p.clone(),v.clone())).collect();
        if let Ok(p)=safe(root,".recreated/asset-index-v1.json"){if let Ok(bytes)=serde_json::to_vec(&Index{records}){let _=atomic_bytes(&p,&bytes);}}
    }
}
pub(super) fn scan(root:&Path,entries:&[Entry],progress:&SharedProgress,workers:usize,proof:&Proof)->Result<Vec<Entry>,String>{
    use std::sync::atomic::{AtomicUsize,Ordering};
    let next=AtomicUsize::new(0);let results=Mutex::new(Vec::new());
    std::thread::scope(|scope|{for _ in 0..workers.clamp(1,2).min(entries.len().max(1)){
        let next=&next;let results=&results;
        scope.spawn(move||loop{
            let i=next.fetch_add(1,Ordering::Relaxed);let Some(e)=entries.get(i)else{break;};
            let result:Result<bool,String>=(||{
                let p=safe(root,&e.path)?;if e.mode=="seed"&&p.exists(){return Ok(false);}
                if !p.exists(){return Ok(true);}
                let before=stamp(&p)?;
                if !proof.full&&asset(&e.path){if let Some(old)=proof.old.records.get(&e.path){if before.size==e.size&&old.sha==e.sha256&&old.stamp==before&&old.verified<=now()&&now()-old.verified<7*86400{
                    proof.records.lock().unwrap().insert(e.path.clone(),old.clone());progress.lock().unwrap().cached_files+=1;return Ok(false);
                }}}
                let found=matches(&p,e)?;if before.size==e.size{progress.lock().unwrap().verified_bytes+=before.size;}if !found{return Ok(true);}
                if before!=stamp(&p)?{return Err("Файл изменился во время проверки".into());}
                proof.remember(e,before);Ok(false)
            })();results.lock().unwrap().push((i,result));progress.lock().unwrap().checked_files+=1;
        });
    }});
    let mut results=results.into_inner().unwrap();results.sort_by_key(|(i,_)|*i);let mut pending=Vec::new();
    for(i,result)in results{if result?{pending.push(entries[i].clone());}}Ok(pending)
}
#[cfg(test)]mod tests{
    use super::*;
    #[test]fn warm_assets_skip_bytes_but_code_is_always_hashed(){
        let root=std::env::temp_dir().join(format!("launcher-cache-{}",uuid::Uuid::new_v4()));fs::create_dir_all(root.join("assets/objects/ab")).unwrap();fs::create_dir_all(root.join("libraries")).unwrap();
        let a="assets/objects/ab/abababababababababababababababababababab";fs::write(root.join(a),b"data").unwrap();fs::write(root.join("libraries/a.jar"),b"code").unwrap();
        let entries=vec![Entry{path:a.into(),size:4,sha256:format!("{:x}",Sha256::digest(b"data")),url:String::new(),mode:"managed".into()},Entry{path:"libraries/a.jar".into(),size:4,sha256:format!("{:x}",Sha256::digest(b"code")),url:String::new(),mode:"managed".into()}];
        let first=Proof::new(&root,true);let p=SharedProgress::default();assert!(scan(&root,&entries,&p,2,&first).unwrap().is_empty());assert_eq!(p.lock().unwrap().verified_bytes,8);
        fs::create_dir(root.join(".recreated")).unwrap();first.save(&root);
        let warm=Proof::new(&root,false);assert_eq!(warm.old.records.len(),1);
        let p=SharedProgress::default();assert!(scan(&root,&entries,&p,2,&warm).unwrap().is_empty());assert_eq!(p.lock().unwrap().cached_files,1);assert_eq!(p.lock().unwrap().verified_bytes,4);
        fs::write(root.join("libraries/a.jar"),b"evil").unwrap();let p=SharedProgress::default();assert_eq!(scan(&root,&entries,&p,2,&warm).unwrap()[0].path,"libraries/a.jar");
        fs::write(root.join(a),b"change").unwrap();let p=SharedProgress::default();assert_eq!(scan(&root,&entries,&p,2,&warm).unwrap().len(),2);
        fs::remove_dir_all(root).unwrap();
    }
    #[test]fn expired_cache_and_full_repair_do_not_skip_assets(){
        let root=std::env::temp_dir().join(format!("launcher-cache-{}",uuid::Uuid::new_v4()));fs::create_dir_all(root.join("assets/objects/ab")).unwrap();
        let path="assets/objects/ab/abababababababababababababababababababab";fs::write(root.join(path),b"good").unwrap();
        let entry=Entry{path:path.into(),size:4,sha256:format!("{:x}",Sha256::digest(b"good")),url:String::new(),mode:"managed".into()};
        let record=Record{sha:entry.sha256.clone(),stamp:stamp(&root.join(path)).unwrap(),verified:now()-8*86400};
        let proof=Proof{version:String::new(),records:Mutex::default(),old:Index{records:HashMap::from([(path.into(),record)])},full:false};let p=SharedProgress::default();scan(&root,&[entry.clone()],&p,1,&proof).unwrap();assert_eq!(p.lock().unwrap().cached_files,0);assert_eq!(p.lock().unwrap().verified_bytes,4);
        proof.file(&root,path,Some(&entry.sha256)).unwrap();fs::write(root.join(path),b"changed").unwrap();assert!(proof.file(&root,path,Some(&entry.sha256)).is_err());
        fs::remove_dir_all(root).unwrap();
    }
    #[test]#[ignore="Explicit read-only timing against a supplied signed manifest and prepared root"]
    fn prepared_read_only_timing(){
        let root=PathBuf::from(std::env::var_os("RECREATED_BENCH_ROOT").expect("Explicit prepared root"));
        let envelope:Envelope=serde_json::from_slice(&fs::read(std::env::var_os("RECREATED_BENCH_MANIFEST").expect("Explicit signed manifest")).unwrap()).unwrap();
        let manifest=authenticated(&envelope,&production_settings().unwrap()).unwrap();
        let cold=Proof::new(&root,true);let p=SharedProgress::default();let at=std::time::Instant::now();let pending=scan(&root,&manifest.files,&p,2,&cold).unwrap();let cold_ms=at.elapsed().as_millis();assert!(pending.is_empty());let cold_bytes=p.lock().unwrap().verified_bytes;
        let warm=Proof{version:String::new(),records:Mutex::default(),old:Index{records:cold.records.into_inner().unwrap()},full:false};let p=SharedProgress::default();let at=std::time::Instant::now();assert!(scan(&root,&manifest.files,&p,2,&warm).unwrap().is_empty());
        let p=p.lock().unwrap();println!("BENCHMARK strict_ms={} warm_ms={} strict_hashed_bytes={} warm_hashed_bytes={} cached_assets={} total_files={}",cold_ms,at.elapsed().as_millis(),cold_bytes,p.verified_bytes,p.cached_files,manifest.files.len());
    }
    #[test]fn cache_is_assets_only(){assert!(asset("assets/objects/ab/abababababababababababababababababababab"));for p in ["libraries/a.jar","runtime/java-21/bin/java.exe","assets/indexes/17.json","assets/objects/ab/evil.jar"]{assert!(!asset(p));}}
}
