use reqwest::{redirect::Policy, Client, Url};
use serde::{Deserialize, Serialize};
use std::time::Duration;
use tauri_plugin_opener::OpenerExt;

#[derive(Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Operation { Register, Status, Login, Restart }

// Deliberately no Debug: these values can contain passwords and polling secrets.
#[derive(Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Credentials {
    username: String,
    password: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    alpha_key: Option<String>,
}
#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AccountRequest {
    operation: Operation,
    body: Option<Credentials>,
    registration_id: Option<String>,
    polling_token: Option<String>,
}
#[derive(Serialize)]
pub struct AccountResponse { status: u16, body: serde_json::Value }

pub(crate) fn base_url(value: &str) -> Result<Url, &'static str> {
    let url = Url::parse(value).map_err(|_| "Invalid Backend endpoint")?;
    let local = matches!(url.host_str(), Some("localhost" | "127.0.0.1" | "[::1]"));
    if (url.scheme() != "https" && !(local && url.scheme() == "http")) ||
        !url.username().is_empty() || url.password().is_some() ||
        url.query().is_some() || url.fragment().is_some() || url.path() != "/" {
        return Err("Invalid Backend endpoint");
    }
    Ok(url)
}
fn is_token(value: &str) -> bool {
    (32..=64).contains(&value.len()) && value.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'_' || b == b'-')
}
fn request_path(request: &AccountRequest) -> Result<String, &'static str> {
    match request.operation {
        Operation::Register => Ok("/api/registrations".into()),
        Operation::Login => Ok("/api/auth/login".into()),
        Operation::Status | Operation::Restart => {
            let id = request.registration_id.as_deref().ok_or("Invalid registration ID")?;
            let id = uuid::Uuid::parse_str(id).map_err(|_| "Invalid registration ID")?;
            Ok(format!("/api/registrations/{id}{}", if matches!(request.operation, Operation::Restart) { "/restart" } else { "" }))
        }
    }
}

#[tauri::command]
pub async fn account_request(request: AccountRequest, runtime: tauri::State<'_, crate::game::GameRuntime>) -> Result<AccountResponse, &'static str> {
    let login = matches!(request.operation, Operation::Login);
    if login { *runtime.account.lock().unwrap() = None; }
    // Compile-time public configuration; no arbitrary URL/path/headers from the WebView.
    let base = base_url(option_env!("VITE_ACCOUNT_API_BASE_URL").unwrap_or(""))?;
    let url = base.join(&request_path(&request)?).map_err(|_| "Invalid Backend endpoint")?;
    let client = Client::builder().timeout(Duration::from_secs(15))
        .connect_timeout(Duration::from_secs(5)).redirect(Policy::none())
        .build().map_err(|_| "Backend connection failed")?;
    let builder = match request.operation {
        Operation::Status | Operation::Restart => {
            let token = request.polling_token.as_deref().filter(|value| is_token(value)).ok_or("Invalid polling token")?;
            if matches!(request.operation, Operation::Restart) { client.post(url).bearer_auth(token) }
            else { client.get(url).bearer_auth(token) }
        }
        Operation::Register | Operation::Login => {
            client.post(url).json(&request.body.ok_or("Missing credentials")?)
        }
    };
    let response = crate::network::send(builder.header("Cache-Control", "no-store")).await.map_err(|_| "Backend connection failed")?;
    let status = response.status().as_u16();
    if response.content_length().is_some_and(|size| size > 32768) { return Err("Unexpected Backend response"); }
    let bytes = response.bytes().await.map_err(|_| "Backend connection failed")?;
    if bytes.len() > 32768 { return Err("Unexpected Backend response"); }
    let body: serde_json::Value = serde_json::from_slice(&bytes).unwrap_or(serde_json::Value::Null);
    // Legacy API remains callable, but only the trusted-device command may authorize
    // this Launcher runtime. Password login alone must not bypass device confirmation.
    Ok(AccountResponse { status, body })
}

fn telegram_link(link: &str) -> Result<Url, &'static str> {
    let url = Url::parse(link).map_err(|_| "Invalid Telegram link")?;
    let name = url.path().strip_prefix('/').unwrap_or("");
    let params: Vec<_> = url.query_pairs().collect();
    if url.scheme() != "https" || url.host_str() != Some("t.me") || url.port().is_some() ||
        !url.username().is_empty() || url.password().is_some() || url.fragment().is_some() ||
        !(5..=32).contains(&name.len()) || !name.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'_') ||
        params.len() != 1 || params[0].0 != "start" || !is_token(&params[0].1) {
        return Err("Invalid Telegram link");
    }
    Ok(url)
}

#[tauri::command]
pub fn open_telegram(app: tauri::AppHandle, link: String) -> Result<(), &'static str> {
    let url = telegram_link(&link)?;
    app.opener().open_url(url.as_str(), None::<&str>).map_err(|_| "Could not open Telegram")
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn registration_alpha_key_is_forwarded_and_login_stays_unchanged() {
        let body: Credentials = serde_json::from_value(serde_json::json!({
            "username": "engineer", "password": "test-password-only", "alphaKey": "k".repeat(43)
        })).unwrap();
        assert_eq!(serde_json::to_value(body).unwrap()["alphaKey"], "k".repeat(43));
        let login: Credentials = serde_json::from_value(serde_json::json!({
            "username": "engineer", "password": "test-password-only"
        })).unwrap();
        assert!(serde_json::to_value(login).unwrap().get("alphaKey").is_none());
    }
    #[test]
    fn only_secure_or_loopback_endpoints() {
        for value in ["https://api.example.test", "http://127.0.0.1:18080", "http://localhost:8080"] { assert!(base_url(value).is_ok()); }
        for value in ["", "http://186.246.28.214:8080", "https://user:secret@example.test", "https://example.test/internal", "https://example.test?token=x"] { assert!(base_url(value).is_err()); }
    }
    #[test]
    fn only_account_routes_and_uuid_status() {
        let request = AccountRequest { operation: Operation::Status, body: None, registration_id: Some("../../internal/telegram".into()), polling_token: None };
        assert!(request_path(&request).is_err());
        let request = AccountRequest { registration_id: Some("12345678-1234-1234-1234-123456789abc".into()), ..request };
        assert_eq!(request_path(&request).unwrap(), "/api/registrations/12345678-1234-1234-1234-123456789abc");
    }
    #[test]
    fn telegram_opener_is_scoped() {
        assert!(telegram_link(&format!("https://t.me/recreating_bot?start={}", "x".repeat(43))).is_ok());
        for value in ["file:///etc/passwd", "https://evil.test/bot?start=x", "https://t.me/recreating_bot?start=x", "https://t.me/recreating_bot?start=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx&other=1"] { assert!(telegram_link(value).is_err()); }
    }
    #[test]
    fn restart_uses_only_uuid_scoped_route_and_polling_proof() {
        let request: AccountRequest = serde_json::from_value(serde_json::json!({
            "operation": "restart", "registrationId": "12345678-1234-1234-1234-123456789abc",
            "pollingToken": "p".repeat(43)
        })).unwrap();
        assert!(request.body.is_none());
        assert_eq!(request_path(&request).unwrap(), "/api/registrations/12345678-1234-1234-1234-123456789abc/restart");
        assert!(is_token(request.polling_token.as_deref().unwrap()));
    }
}
