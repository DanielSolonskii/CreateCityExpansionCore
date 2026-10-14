//! Read-only Minecraft STATUS over the same verified TLS gateway used by the game.
//! No login, credentials, service secrets or player names are queried.
use std::{io::{Read, Write}, net::{TcpStream, ToSocketAddrs}, sync::Arc, time::{Duration, Instant}};
use serde::Serialize;

#[derive(Debug, Serialize)]
#[serde(rename_all="camelCase")]
pub struct ServerStatus {
    state: &'static str, label: &'static str, players_online: Option<u32>,
    players_max: Option<u32>, ping: Option<u64>, message: &'static str,
}
fn unavailable() -> ServerStatus {
    ServerStatus {state:"unknown",label:"Нет связи с сервером",players_online:None,players_max:None,ping:None,message:"Альфа тестирование"}
}
fn varint(mut value: u32) -> Vec<u8> {
    let mut bytes=Vec::new();
    loop {let byte=(value & 127) as u8;value >>= 7;bytes.push(byte | if value>0 {128} else {0});if value==0 {return bytes}}
}
fn read_varint(reader: &mut impl Read) -> Result<u32, String> {
    let mut value=0;
    for shift in (0..35).step_by(7) {let mut byte=[0];reader.read_exact(&mut byte).map_err(|_|"Invalid status packet")?;
        if shift==28 && byte[0]&0xf0!=0 {return Err("Invalid VarInt".into())}
        value |= ((byte[0]&127) as u32)<<shift;if byte[0]&128==0 {return Ok(value)}}
    Err("Invalid VarInt".into())
}
fn send(writer:&mut impl Write,body:&[u8])->Result<(),String>{
    writer.write_all(&varint(body.len() as u32)).and_then(|_|writer.write_all(body)).and_then(|_|writer.flush()).map_err(|_|"Status send failed".into())
}
fn packet(reader:&mut impl Read)->Result<Vec<u8>,String>{
    let length=read_varint(reader)? as usize;
    if length==0 || length>65536 {return Err("Status packet too large".into())}
    let mut data=vec![0;length];reader.read_exact(&mut data).map_err(|_|"Status read failed")?;Ok(data)
}
fn parse(data:&[u8])->Result<(u32,u32),String>{
    let mut reader=data;
    if read_varint(&mut reader)?!=0 {return Err("Invalid status response".into())}
    let length=read_varint(&mut reader)? as usize;
    if length!=reader.len() {return Err("Invalid status JSON length".into())}
    let response:serde_json::Value=serde_json::from_slice(reader).map_err(|_|"Invalid status JSON")?;
    let online=response["players"]["online"].as_u64().filter(|v|*v<=1_000_000).ok_or("Invalid player count")?;
    let maximum=response["players"]["max"].as_u64().filter(|v|*v>0 && *v<=1_000_000).ok_or("Invalid player limit")?;
    Ok((online as u32,maximum as u32))
}
fn probe(host:&str,port:u16)->Result<ServerStatus,String>{
    if host.is_empty() || host.len()>253 || port==0 {return Err("Endpoint not configured".into())}
    let deadline=Instant::now()+Duration::from_secs(6);
    let mut connected=None;
    for address in (host,port).to_socket_addrs().map_err(|_|"DNS failed")?.take(4) {
        let remaining=deadline.saturating_duration_since(Instant::now());if remaining.is_zero(){break}
        if let Ok(tcp)=TcpStream::connect_timeout(&address,remaining.min(Duration::from_secs(2))){connected=Some(tcp);break}
    }
    let tcp=connected.ok_or("Connect failed")?;
    let timeout=deadline.saturating_duration_since(Instant::now());if timeout.is_zero(){return Err("Timeout".into())}
    tcp.set_read_timeout(Some(timeout)).map_err(|_|"Timeout setup failed")?;
    tcp.set_write_timeout(Some(timeout)).map_err(|_|"Timeout setup failed")?;
    let roots=rustls::RootCertStore::from_iter(webpki_roots::TLS_SERVER_ROOTS.iter().cloned());
    let config=rustls::ClientConfig::builder().with_root_certificates(roots).with_no_client_auth();
    let name=rustls::pki_types::ServerName::try_from(host.to_owned()).map_err(|_|"Invalid TLS name")?;
    let connection=rustls::ClientConnection::new(Arc::new(config),name).map_err(|_|"TLS failed")?;
    let mut stream=rustls::StreamOwned::new(connection,tcp);
    let mut handshake=vec![0];handshake.extend(varint(767));handshake.extend(varint(host.len() as u32));
    handshake.extend(host.as_bytes());handshake.extend(port.to_be_bytes());handshake.push(1);
    send(&mut stream,&handshake)?;send(&mut stream,&[0])?;
    let (online,maximum)=parse(&packet(&mut stream)?)?;
    let payload=42_i64.to_be_bytes();let mut ping=vec![1];ping.extend(payload);
    let start=Instant::now();send(&mut stream,&ping)?;
    let pong=packet(&mut stream)?;if pong!=ping{return Err("Invalid pong".into())}
    Ok(ServerStatus {state:"online",label:"Сервер работает",players_online:Some(online),players_max:Some(maximum),ping:Some(start.elapsed().as_millis() as u64),message:"Альфа тестирование"})
}
#[tauri::command]
pub async fn server_status()->ServerStatus {
    tauri::async_runtime::spawn_blocking(|| {
        let host=option_env!("RECREATED_GAME_TLS_HOST").unwrap_or("");
        let port=option_env!("RECREATED_GAME_TLS_PORT").unwrap_or("25565").parse().unwrap_or(0);
        probe(host,port).unwrap_or_else(|_|unavailable())
    }).await.unwrap_or_else(|_|unavailable())
}
#[cfg(test)] mod tests {
    use super::*;
    fn response(value:&str)->Vec<u8>{let mut data=vec![0];data.extend(varint(value.len() as u32));data.extend(value.as_bytes());data}
    #[test] fn real_counts(){assert_eq!(parse(&response(r#"{"players":{"online":3,"max":20}}"#)).unwrap(),(3,20));}
    #[test] fn reject_invalid_counts(){for text in [r#"{"players":{"online":-1,"max":20}}"#,r#"{"players":{"online":1,"max":0}}"#,r#"{}"#]{assert!(parse(&response(text)).is_err());}}
    #[test] fn bounded_frames(){assert!(packet(&mut &varint(65537)[..]).is_err());assert!(read_varint(&mut &[255,255,255,255,255][..]).is_err());assert!(parse(&[0,5,123]).is_err());}
    #[test] fn missing_data_is_not_zero_players(){let s=unavailable();assert_eq!(s.state,"unknown");assert!(s.players_online.is_none());assert!(s.ping.is_none());}
    #[test] fn live_production_status(){if std::env::var("RECREATED_STATUS_SMOKE").as_deref()==Ok("1"){println!("{}",serde_json::to_string(&probe("api.recreated.ru",25565).unwrap()).unwrap());}}
}
