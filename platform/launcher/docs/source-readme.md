# RE:CREATED Launcher

UI-only desktop launcher for the Create-based Minecraft server. All server state,
profile data, file checks and launch actions are mocks for now.

## Stack

- Tauri 2 — compact desktop shell and future secure access to the file system/processes.
- React + TypeScript — component UI and typed service contracts.
- Vite — fast local development and production frontend build.
- Plain CSS — custom visual system without a large UI framework.

The UI depends only on `LauncherGateway` from `src/types/launcher.ts`. Replace
`MockLauncherGateway` with an HTTP/Tauri implementation to add a backend without
rewriting components.

## Run the UI

```powershell
npm install
npm run dev
```

Open `http://localhost:1420`. This path needs Node.js only.

## Run as a desktop app

Install the current stable Rust toolchain and Microsoft C++ Build Tools, then:

```powershell
npm install
npm run tauri dev
```

## Current mock behavior

- The server status and news are local fixtures.
- Login: `drannch1k`, password: `123`.
- Registration accepts alpha key `RC-ALPHA-2026` and a Telegram handle in `@username` format.
- Mock accounts and sessions are stored only in browser storage and are not suitable for production authentication.
- The first click on **Играть** simulates a file check.
- The second click simulates launching Minecraft.
- Settings are UI-only and remain in memory until the app closes.

## Recommended backend boundary

Implement a `TauriLauncherGateway` with commands for authentication, manifest
retrieval, checksum verification, incremental downloads and Minecraft process
launch. Keep credentials in the OS credential store and restrict Tauri process
permissions to the exact executable and arguments required by the launcher.
