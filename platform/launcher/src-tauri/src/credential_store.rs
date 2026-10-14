use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};

// Never derive Debug or return this to the WebView.
#[derive(Clone, Deserialize, Serialize)]
#[serde(rename_all="camelCase")]
pub(crate) struct StoredDevice {
    pub device_id: String,
    pub secret: String,
    pub endpoint: String,
    pub revoke_pending: bool,
}
pub(crate) fn target(endpoint: &str) -> String {
    format!("RECREATED:Launcher:Device:v1:{:x}", Sha256::digest(endpoint.as_bytes()))
}
#[cfg(windows)]
mod native {
    use super::*;
    use windows_sys::Win32::{Foundation::{GetLastError, ERROR_NOT_FOUND}, Security::Credentials::*};
    fn wide(value:&str)->Vec<u16> { value.encode_utf16().chain(Some(0)).collect() }
    pub fn write(target:&str,value:&StoredDevice)->Result<(), &'static str> {
        let mut name=wide(target); let mut user=wide("RECREATED Launcher device");
        let mut blob=serde_json::to_vec(value).map_err(|_|"Secure credential storage failed")?;
        if blob.len()>2560 { return Err("Invalid credential size"); }
        let entry=CREDENTIALW { Type:CRED_TYPE_GENERIC, TargetName:name.as_mut_ptr(),
            CredentialBlobSize:blob.len() as u32, CredentialBlob:blob.as_mut_ptr(),
            Persist:CRED_PERSIST_LOCAL_MACHINE, UserName:user.as_mut_ptr(), ..Default::default() };
        let ok=unsafe { CredWriteW(&entry,0) };
        blob.fill(0);
        if ok==0 { Err("Secure credential storage failed") } else { Ok(()) }
    }
    pub fn read(target:&str)->Result<Option<StoredDevice>, &'static str> {
        let name=wide(target); let mut entry=std::ptr::null_mut();
        if unsafe { CredReadW(name.as_ptr(),CRED_TYPE_GENERIC,0,&mut entry) }==0 {
            return if unsafe { GetLastError() }==ERROR_NOT_FOUND { Ok(None) } else { Err("Secure credential storage unavailable") };
        }
        let result=unsafe {
            if (*entry).CredentialBlobSize==0 || (*entry).CredentialBlobSize>2560 || (*entry).CredentialBlob.is_null() {
                Err("Invalid secure credential")
            } else {
                let blob=std::slice::from_raw_parts((*entry).CredentialBlob,(*entry).CredentialBlobSize as usize);
                serde_json::from_slice(blob).map(Some).map_err(|_|"Invalid secure credential")
            }
        };
        unsafe { CredFree(entry as _) };
        result
    }
    pub fn delete(target:&str)->Result<(), &'static str> {
        let name=wide(target);
        if unsafe { CredDeleteW(name.as_ptr(),CRED_TYPE_GENERIC,0) }==0 && unsafe { GetLastError() }!=ERROR_NOT_FOUND {
            Err("Secure credential removal failed")
        } else { Ok(()) }
    }
}
#[cfg(windows)] pub(crate) use native::{read,write,delete};
#[cfg(not(windows))] pub(crate) fn read(_: &str)->Result<Option<StoredDevice>, &'static str> { Err("Windows credential storage required") }
#[cfg(not(windows))] pub(crate) fn write(_: &str,_:&StoredDevice)->Result<(), &'static str> { Err("Windows credential storage required") }
#[cfg(not(windows))] pub(crate) fn delete(_: &str)->Result<(), &'static str> { Err("Windows credential storage required") }

#[cfg(test)] mod tests {
    use super::*;
    #[test] fn endpoint_scopes_credential_target() {
        assert_ne!(target("http://127.0.0.1:8080/"),target("https://api.recreated.ru/"));
        assert!(!target("https://api.recreated.ru/").contains("https://"));
    }
    #[test] #[cfg(windows)] fn windows_vault_roundtrip_and_logout_tombstone() {
        let name=format!("RECREATED:Launcher:Test:{}",std::process::id());
        let secret="test-only-not-a-real-device-secret";
        let mut entry=StoredDevice{device_id:"test-device".into(),secret:secret.into(),endpoint:"http://127.0.0.1:18080/".into(),revoke_pending:false};
        delete(&name).unwrap(); assert!(read(&name).unwrap().is_none());
        write(&name,&entry).unwrap(); assert_eq!(read(&name).unwrap().unwrap().secret,secret);
        entry.revoke_pending=true; write(&name,&entry).unwrap();
        assert!(read(&name).unwrap().unwrap().revoke_pending);
        delete(&name).unwrap(); assert!(read(&name).unwrap().is_none());
    }
}
