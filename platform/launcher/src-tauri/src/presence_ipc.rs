//! Bounded Windows Discord IPC. Each operation is cancellable; no blocking pipe flush.
#![cfg(windows)]
use std::{fs::File,os::windows::io::{AsRawHandle,FromRawHandle},time::{Duration,Instant}};
use serde_json::{json,Value};
use windows_sys::Win32::{Foundation::*,Storage::FileSystem::*,System::{IO::*,Threading::CreateEventW}};
const LIMIT:usize=65536;
pub struct Client {id:String,socket:Option<File>}
impl Client {
    pub fn new(id:impl Into<String>)->Self{Self{id:id.into(),socket:None}}
    pub fn connect(&mut self)->Result<(),String>{
        for index in 0..10 {
            let name=format!(r"\\?\pipe\discord-ipc-{index}").encode_utf16().chain(Some(0)).collect::<Vec<_>>();
            let handle=unsafe{CreateFileW(name.as_ptr(),GENERIC_READ|GENERIC_WRITE,0,std::ptr::null(),OPEN_EXISTING,FILE_FLAG_OVERLAPPED,std::ptr::null_mut())};
            if handle!=INVALID_HANDLE_VALUE {self.socket=Some(unsafe{File::from_raw_handle(handle)});break;}
        }
        if self.socket.is_none(){return Err("Discord недоступен".into());}
        self.send(0,json!({"v":1,"client_id":self.id}))?;
        let(op,body)=self.recv()?;
        if op!=1 || body["evt"]!="READY" {self.close()?;return Err("Discord handshake не подтверждён".into());}Ok(())
    }
    fn transfer(&mut self,buffer:&mut[u8],write:bool)->Result<(),String>{
        let handle=self.socket.as_ref().ok_or("Discord не подключён")?.as_raw_handle();
        let deadline=Instant::now()+Duration::from_secs(3);let mut offset=0;
        while offset<buffer.len(){
            let event=unsafe{CreateEventW(std::ptr::null(),1,0,std::ptr::null())};
            if event.is_null(){return Err("Discord IPC event недоступен".into());}
            let mut operation:OVERLAPPED=unsafe{std::mem::zeroed()};operation.hEvent=event;
            let mut count=0;
            let started=unsafe{if write{WriteFile(handle,buffer[offset..].as_ptr(),(buffer.len()-offset)as u32,&mut count,&mut operation)}else{ReadFile(handle,buffer[offset..].as_mut_ptr(),(buffer.len()-offset)as u32,&mut count,&mut operation)}};
            let pending=started==0&&unsafe{GetLastError()}==ERROR_IO_PENDING;
            let ok=if pending {(unsafe{GetOverlappedResultEx(handle,&operation,&mut count,deadline.saturating_duration_since(Instant::now()).as_millis().min(3000)as u32,0)})!=0}else{started!=0};
            if !ok&&pending {
                // Drain cancellation before dropping OVERLAPPED or its buffer.
                unsafe{CancelIoEx(handle,&operation);GetOverlappedResult(handle,&operation,&mut count,1);}
            }
            unsafe{CloseHandle(event);}
            if !ok || count==0{return Err("Discord IPC timeout/disconnect".into());}
            offset+=count as usize;
            if offset<buffer.len()&&Instant::now()>=deadline{return Err("Discord IPC timeout".into());}
        }Ok(())
    }
    fn send(&mut self,op:u32,body:Value)->Result<(),String>{
        let data=serde_json::to_vec(&body).map_err(|_|"Discord payload")?;
        if data.len()>LIMIT{return Err("Discord frame слишком большой".into());}
        let mut frame=Vec::with_capacity(data.len()+8);frame.extend(op.to_le_bytes());frame.extend((data.len()as u32).to_le_bytes());frame.extend(data);
        self.transfer(&mut frame,true)
    }
    pub fn recv(&mut self)->Result<(u32,Value),String>{
        let mut header=[0;8];self.transfer(&mut header,false)?;
        let op=u32::from_le_bytes(header[..4].try_into().unwrap());let length=u32::from_le_bytes(header[4..].try_into().unwrap())as usize;
        if length>LIMIT{return Err("Discord frame слишком большой".into());}
        let mut body=vec![0;length];self.transfer(&mut body,false)?;
        Ok((op,serde_json::from_slice(&body).map_err(|_|"Discord payload")?))
    }
    pub fn set_activity(&mut self,value:discord_rich_presence::activity::Activity<'_>)->Result<(),String>{
        self.send(1,json!({"cmd":"SET_ACTIVITY","args":{"pid":std::process::id(),"activity":value},"nonce":uuid::Uuid::new_v4().to_string()}))
    }
    pub fn clear_activity(&mut self)->Result<(),String>{self.send(1,json!({"cmd":"SET_ACTIVITY","args":{"pid":std::process::id(),"activity":null},"nonce":uuid::Uuid::new_v4().to_string()}))}
    pub fn close(&mut self)->Result<(),String>{self.socket.take();Ok(())}
}
#[cfg(test)]mod tests{
    use super::*;
    fn pair()->(File,Client){
        use windows_sys::Win32::System::Pipes::*;
        let name=format!(r"\\?\pipe\recreated-ipc-test-{}",uuid::Uuid::new_v4()).encode_utf16().chain(Some(0)).collect::<Vec<_>>();
        let server=unsafe{CreateNamedPipeW(name.as_ptr(),PIPE_ACCESS_DUPLEX,PIPE_TYPE_BYTE|PIPE_READMODE_BYTE|PIPE_WAIT,1,65536,65536,0,std::ptr::null())};
        assert_ne!(server,INVALID_HANDLE_VALUE);
        let server=unsafe{File::from_raw_handle(server)};
        let socket=unsafe{CreateFileW(name.as_ptr(),GENERIC_READ|GENERIC_WRITE,0,std::ptr::null(),OPEN_EXISTING,FILE_FLAG_OVERLAPPED,std::ptr::null_mut())};
        assert_ne!(socket,INVALID_HANDLE_VALUE);
        (server,Client{id:"test".into(),socket:Some(unsafe{File::from_raw_handle(socket)})})
    }
    #[test]fn silent_peer_times_out_without_hanging_worker(){
        let (_server,mut client)=pair();let at=Instant::now();
        assert!(client.recv().is_err());assert!(at.elapsed()<Duration::from_secs(5));
        assert!(client.close().is_ok());
    }
    #[test]fn oversized_incoming_frame_is_rejected_before_allocation(){
        use std::io::Write;
        let(mut server,mut client)=pair();server.write_all(&[1u32.to_le_bytes(),(LIMIT as u32+1).to_le_bytes()].concat()).unwrap();
        assert!(client.recv().unwrap_err().contains("слишком большой"));
    }
    #[test]fn disconnected_peer_does_not_hang(){let(server,mut client)=pair();drop(server);assert!(client.recv().is_err());}
}
