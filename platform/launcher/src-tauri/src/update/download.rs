//! Bounded retries and persistent, content-addressed partial downloads.
use super::*;
use reqwest::header::{CONTENT_RANGE, RANGE};

pub(super) fn batch(root:&Path,client:&Client,entries:&[Entry],progress:&SharedProgress)->Result<(),String>{
    use std::sync::atomic::{AtomicUsize,AtomicBool,Ordering};
    let mut hashes=HashSet::new();
    let jobs:Vec<_>=entries.iter().filter(|entry|hashes.insert(entry.sha256.as_str())).collect();
    {let mut p=progress.lock().unwrap();p.total_bytes=jobs.iter().map(|e|e.size).sum();p.message="Параллельная загрузка · до 4 соединений".into();}
    let next=AtomicUsize::new(0);let failed=AtomicBool::new(false);let error=Mutex::new(None);
    std::thread::scope(|scope|{
        for _ in 0..4.min(jobs.len()) {
            scope.spawn(||{
                while !failed.load(Ordering::Acquire){
                    let index=next.fetch_add(1,Ordering::Relaxed);
                    let Some(entry)=jobs.get(index) else{break};
                    let result=safe(root,&format!(".recreated/downloads/{}.part",entry.sha256))
                        .and_then(|path|fetch(client,entry,&path,progress));
                    if let Err(reason)=result{
                        failed.store(true,Ordering::Release);let mut first=error.lock().unwrap();if first.is_none(){*first=Some(reason)}break
                    }
                    progress.lock().unwrap().completed_files+=entries.iter().filter(|e|e.sha256==entry.sha256).count() as u64;
                }
            });
        }
    });
    error.into_inner().unwrap().map_or(Ok(()),Err)
}

pub(super) fn fetch(client:&Client,entry:&Entry,path:&Path,progress:&SharedProgress)->Result<(),String>{
    if entry.size==0 && !path.exists(){File::create(path).map_err(|_|"Не удалось создать пустой файл")?;}
    if matches(path,entry)? {progress.lock().unwrap().downloaded_bytes+=entry.size;return Ok(())}
    if path.exists() && fs::metadata(path).map_err(|_|"Кэш загрузки недоступен")?.len()>=entry.size {
        fs::remove_file(path).map_err(|_|"Не удалось сбросить повреждённую загрузку")?;
    }
    let mut offset=fs::metadata(path).map(|m|m.len()).unwrap_or(0);
    progress.lock().unwrap().downloaded_bytes+=offset;
    let mut failures=0;
    let mut range_bytes=8*1024*1024u64;
    while offset<entry.size || entry.size==0 && !path.exists(){
        let end=(offset+range_bytes-1).min(entry.size.saturating_sub(1));
        let result=(||->Result<(),String>{
            let mut response=crate::network::blocking_send(client.get(&entry.url).header("Accept-Encoding","identity")
                .header(RANGE,format!("bytes={offset}-{end}")))
                .map_err(|_|"Нет связи с сервером загрузки")?;
            let partial=response.status()==reqwest::StatusCode::PARTIAL_CONTENT;
            if !partial && response.status()!=reqwest::StatusCode::OK {
                return Err(format!("Сервер загрузки: HTTP {}",response.status().as_u16()))
            }
            if partial {
                let expected=format!("bytes {offset}-{end}/{}",entry.size);
                if response.headers().get(CONTENT_RANGE).and_then(|h|h.to_str().ok())!=Some(expected.as_str()){
                    return Err("Некорректный диапазон загрузки".into())
                }
            } else if offset>0 {
                // Servers ignoring Range must restart, never append a complete body.
                progress.lock().unwrap().downloaded_bytes-=offset;offset=0;
            }
            let expected=if partial {end-offset+1}else{entry.size};
            if response.content_length().is_some_and(|n|n!=expected){return Err("Неверный размер загрузки".into())}
            let mut output=OpenOptions::new().create(true).write(true).truncate(offset==0).append(offset>0)
                .open(path).map_err(|_|"Не удалось записать кэш загрузки")?;
            let start=offset;let mut buffer=[0;262144];
            loop {
                let n=response.read(&mut buffer).map_err(|_|"Соединение прервано; докачка будет продолжена")?;
                if n==0 {break}
                if offset+n as u64>start+expected{return Err("Превышен размер загрузки".into())}
                output.write_all(&buffer[..n]).map_err(|_|"Ошибка записи загрузки; проверьте место на диске")?;
                offset+=n as u64;progress.lock().unwrap().downloaded_bytes+=n as u64;
            }

            if offset!=start+expected{return Err("Неполная загрузка; повторное соединение".into())} Ok(())
        })();
        match result {
            Ok(())=>{failures=0;range_bytes=(range_bytes*2).min(32*1024*1024)},
            Err(error)=>{
                range_bytes=(range_bytes/2).max(1024*1024);
                failures+=1;if failures>=4{return Err(format!("{error}. Нажмите «Повторить»: скачанное сохранено."))}
                phase(progress,"DOWNLOADING",&format!("Восстановление соединения ({failures}/3), докачка"));
                std::thread::sleep(Duration::from_millis(250*(1<<failures)));
            }
        }
    }
    OpenOptions::new().write(true).open(path).and_then(|f|f.sync_all()).map_err(|_|"Не удалось сохранить загрузку")?;
    if !matches(path,entry)?{
        fs::remove_file(path).map_err(|_|"Не удалось удалить повреждённую загрузку")?;
        return Err("SHA-256 загрузки не совпадает; повреждённый файл удалён. Повторите загрузку.".into())
    }
    Ok(())
}

#[cfg(test)] mod tests {
    use super::*;
    use std::net::TcpListener;
    #[test] fn parallel_batch_is_bounded_and_deduplicates_content(){
        use std::sync::atomic::{AtomicUsize,Ordering};
        let listener=TcpListener::bind("127.0.0.1:0").unwrap();let address=listener.local_addr().unwrap();
        let active=Arc::new(AtomicUsize::new(0));let peak=Arc::new(AtomicUsize::new(0));
        let current=active.clone();let maximum=peak.clone();
        let server=std::thread::spawn(move||{
            let mut workers=Vec::new();
            for _ in 0..16 {
                let(mut stream,_)=listener.accept().unwrap();let current=current.clone();let maximum=maximum.clone();
                workers.push(std::thread::spawn(move||{
                    stream.set_read_timeout(Some(Duration::from_secs(5))).unwrap();
                    let mut request=Vec::new();let mut byte=[0];while !request.ends_with(b"\r\n\r\n"){stream.read_exact(&mut byte).unwrap();request.push(byte[0]);}
                    let text=String::from_utf8(request).unwrap();let index=text.split_whitespace().nth(1).unwrap().trim_start_matches('/').parse::<u8>().unwrap();
                    let count=current.fetch_add(1,Ordering::SeqCst)+1;maximum.fetch_max(count,Ordering::SeqCst);
                    std::thread::sleep(Duration::from_millis(60));let body=vec![index;4096];
                    write!(stream,"HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",body.len()).unwrap();stream.write_all(&body).unwrap();current.fetch_sub(1,Ordering::SeqCst);
                }));
            }
            for worker in workers{worker.join().unwrap();}
        });
        let root=std::env::temp_dir().join(format!("parallel-download-{}",uuid::Uuid::new_v4()));
        fs::create_dir_all(root.join(".recreated/downloads")).unwrap();
        let mut entries:Vec<_>=(0..8u8).map(|i|Entry{path:format!("libraries/{i}.jar"),mode:"managed".into(),size:4096,sha256:format!("{:x}",Sha256::digest(vec![i;4096])),url:format!("http://{address}/{i}")}).collect();
        let client=Client::builder().timeout(Duration::from_secs(5)).build().unwrap();let start=std::time::Instant::now();
        for (i,entry)in entries.iter().enumerate(){fetch(&client,entry,&root.join(format!("serial-{i}")),&SharedProgress::default()).unwrap();}
        let serial=start.elapsed();entries.push(entries[0].clone());let progress=SharedProgress::default();let start=std::time::Instant::now();
        batch(&root,&client,&entries,&progress).unwrap();let parallel=start.elapsed();server.join().unwrap();
        assert!(peak.load(Ordering::SeqCst)>1);assert!(peak.load(Ordering::SeqCst)<=4);
        assert_eq!(progress.lock().unwrap().completed_files,9);assert_eq!(progress.lock().unwrap().total_bytes,8*4096);
        for entry in entries{assert!(matches(&root.join(format!(".recreated/downloads/{}.part",entry.sha256)),&entry).unwrap());}
        println!("DOWNLOAD BENCH serial={serial:?} parallel={parallel:?} peak={} unique=8 files=9",peak.load(Ordering::SeqCst));
        fs::remove_dir_all(root).unwrap();
    }
    #[test] fn interrupted_response_resumes_with_verified_range(){
        let body=b"verified payload after network interruption".to_vec();
        let listener=TcpListener::bind("127.0.0.1:0").unwrap();
        let url=format!("http://{}/object",listener.local_addr().unwrap());
        let sent=body.clone();
        let worker=std::thread::spawn(move||{
            for attempt in 0..2 {
                let (mut stream,_)=listener.accept().unwrap();stream.set_read_timeout(Some(Duration::from_secs(3))).unwrap();
                let mut bytes=Vec::new();let mut one=[0;1];
                while !bytes.ends_with(b"\r\n\r\n"){stream.read_exact(&mut one).unwrap();bytes.push(one[0]);}
                let request=String::from_utf8(bytes).unwrap().to_lowercase();
                if attempt==0 {
                    write!(stream,"HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",sent.len()).unwrap();stream.write_all(&sent[..7]).unwrap();
                }else{
                    assert!(request.contains(&format!("range: bytes=7-{}",sent.len()-1)));
                    write!(stream,"HTTP/1.1 206 Partial Content\r\nContent-Length: {}\r\nContent-Range: bytes 7-{}/{}\r\nConnection: close\r\n\r\n",sent.len()-7,sent.len()-1,sent.len()).unwrap();stream.write_all(&sent[7..]).unwrap();
                }
            }
        });
        let path=std::env::temp_dir().join(format!("download-test-{}",uuid::Uuid::new_v4()));
        let entry=Entry{path:"libraries/test.jar".into(),mode:"managed".into(),size:body.len() as u64,sha256:format!("{:x}",Sha256::digest(&body)),url};
        let progress=SharedProgress::default();
        fetch(&Client::builder().timeout(Duration::from_secs(3)).build().unwrap(),&entry,&path,&progress).unwrap();
        worker.join().unwrap();assert_eq!(fs::read(&path).unwrap(),body);assert_eq!(progress.lock().unwrap().downloaded_bytes,entry.size);fs::remove_file(path).unwrap();
    }
}
