use reqwest::{redirect::Policy, Client};
use std::time::Duration;

/// Fixed read-only endpoint, no service/device credentials or WebView-controlled URL.
#[tauri::command]
pub async fn launcher_news() -> Result<serde_json::Value, &'static str> {
    let base = crate::account::base_url(option_env!("VITE_ACCOUNT_API_BASE_URL").unwrap_or(""))?;
    let client = Client::builder().timeout(Duration::from_secs(10))
        .redirect(Policy::none()).build().map_err(|_| "Новости временно недоступны")?;
    let mut response = client.get(base.join("/api/launcher/news").map_err(|_| "Invalid endpoint")?)
        .send().await.map_err(|_| "Новости временно недоступны")?;
    if !response.status().is_success() { return Err("Новости временно недоступны"); }
    let mut bytes = Vec::new();
    while let Some(chunk) = response.chunk().await.map_err(|_| "Новости временно недоступны")? {
        if bytes.len() + chunk.len() > 1024 * 1024 { return Err("Некорректная лента новостей"); }
        bytes.extend_from_slice(&chunk);
    }
    serde_json::from_slice(&bytes).map_err(|_| "Некорректная лента новостей")
}
