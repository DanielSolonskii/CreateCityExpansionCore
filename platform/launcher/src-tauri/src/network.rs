//! Retry only connection establishment, never a timed-out/in-flight mutation.
//! Pinned address is a DNS/proxy fallback, not a bypass of TLS authentication.
use reqwest::{redirect::Policy,Url};
use std::time::Duration;
fn production(url:&Url)->bool {
    url.scheme()=="https"&&url.host_str()==Some("api.recreated.ru")&&url.port_or_known_default()==Some(443)
        &&url.username().is_empty()&&url.password().is_none()
}
pub(crate) async fn send(builder:reqwest::RequestBuilder)->Result<reqwest::Response,reqwest::Error>{
    let retry=builder.try_clone().and_then(|b|b.build().ok()).filter(|r|production(r.url()));
    match builder.send().await {
        Err(error) if error.is_connect()=>{
            let Some(request)=retry else{return Err(error)};
            let client=reqwest::Client::builder().no_proxy().redirect(Policy::none())
                .resolve("api.recreated.ru","186.246.28.214:443".parse().unwrap())
                .connect_timeout(Duration::from_secs(12)).timeout(Duration::from_secs(30)).build()?;
            client.execute(request).await
        },result=>result
    }
}
pub(crate) fn blocking_send(builder:reqwest::blocking::RequestBuilder)->Result<reqwest::blocking::Response,reqwest::Error>{
    let retry=builder.try_clone().and_then(|b|b.build().ok()).filter(|r|production(r.url()));
    match builder.send(){
        Err(error) if error.is_connect()=>{
            let Some(request)=retry else{return Err(error)};
            let client=reqwest::blocking::Client::builder().no_proxy().redirect(Policy::none())
                .resolve("api.recreated.ru","186.246.28.214:443".parse().unwrap())
                .connect_timeout(Duration::from_secs(12)).timeout(Duration::from_secs(90)).build()?;
            client.execute(request)
        },result=>result
    }
}
#[cfg(test)] mod tests {
    use super::*;
    #[test] fn fallback_is_only_for_verified_production_origin(){
        assert!(production(&Url::parse("https://api.recreated.ru/api/auth/devices").unwrap()));
        for value in ["http://api.recreated.ru/","https://evil.test/","https://api.recreated.ru:8080/","https://user:pass@api.recreated.ru/"]{
            assert!(!production(&Url::parse(value).unwrap()));
        }
    }
    #[test]
    fn response_timeout_does_not_replay_post() {
        use std::{io::{ErrorKind, Read}, net::TcpListener, sync::mpsc};

        let server = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = server.local_addr().unwrap();
        let (finished, wait_for_client) = mpsc::channel();
        let task = std::thread::spawn(move || {
            // The listening socket queues the POST without replying until the timeout.
            wait_for_client.recv().unwrap();
            server.set_nonblocking(true).unwrap();
            let (mut stream, _) = server.accept().unwrap();
            let mut request = [0; 1024];
            assert!(stream.read(&mut request).unwrap() > 0);
            assert_eq!(server.accept().unwrap_err().kind(), ErrorKind::WouldBlock);
        });
        let client = reqwest::blocking::Client::builder().timeout(Duration::from_millis(100)).build().unwrap();
        let result = blocking_send(client.post(format!("http://{address}/")).body("data"));
        finished.send(()).unwrap();
        task.join().unwrap();
        assert!(result.unwrap_err().is_timeout());
    }
}
