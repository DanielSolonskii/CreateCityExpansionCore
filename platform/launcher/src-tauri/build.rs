fn main() {
    // Public production configuration is versioned; never depend on a shell's stale env.
    for (key, value) in [
        ("VITE_ACCOUNT_API_BASE_URL", "https://api.recreated.ru"),
        ("RECREATED_GAME_TLS_HOST", "api.recreated.ru"),
        ("RECREATED_GAME_TLS_PORT", "25565"),
        ("RECREATED_GAME_SERVER_ID", "recreated-alpha"),
    ] {
        println!("cargo:rerun-if-env-changed={key}");
        if std::env::var("PROFILE").as_deref() == Ok("release") {
            if let Ok(override_value) = std::env::var(key) {
                assert_eq!(override_value, value, "Production config mismatch: {key}");
            }
            println!("cargo:rustc-env={key}={value}");
        }
    }
    println!("cargo:rerun-if-env-changed=RECREATED_BUILD_ID");
    println!("cargo:rustc-env=RECREATED_BUILD_ID={}", std::env::var("RECREATED_BUILD_ID").unwrap_or_else(|_| "production-selfupdate-1".into()));
    println!("cargo:rerun-if-env-changed=RECREATED_SELF_UPDATE_TEST_URL");
    if std::env::var_os("CARGO_FEATURE_SELF_UPDATE_TEST").is_some() {
        let url = std::env::var("RECREATED_SELF_UPDATE_TEST_URL").expect("Test build needs explicit staging URL");
        assert!(url.starts_with("https://api.recreated.ru/launcher/versions/"));
        println!("cargo:rustc-env=RECREATED_SELF_UPDATE_TEST_URL={url}");
    }
    tauri_build::build()
}
