# Windows window controls — 2026-10-04

## Fix and behavior

Previous JS used Tauri2 window APIs but capability only contained core:default.
Minimize/toggle-maximize/close were therefore not granted; rejected promises were
not handled. Added only main-window permissions: minimize, toggle-maximize,
close, hide, start-dragging. No remote capabilities, shell/general system access,
auth/game/updater permissions were added.

- Свернуть: Windows taskbar minimize, not tray hide.
- Развернуть/Восстановить: toggleMaximize, actual isMaximized state and resize
  listener keep label/icon/aria-pressed synchronized. Async errors are visible.
- Закрыть: normal Tauri window close; main-window destruction exits Launcher.
- Скрыть в трей: separate hide, no logout or process termination.
- Native tray: app icon, left click opens; right menu has Открыть Launcher/Выход.
  Open shows/unminimizes/focuses the existing window, preserves maximization.
  Exit uses app.exit(0), releasing native single-instance resources normally.
- tauri-plugin-single-instance2.5.2 is registered first. A second executable
  restores the existing window and exits, rather than creating another Launcher.

Close/Exit never call device logout/revoke, so protected trusted credential stays
available on next startup. No Java process is killed. Existing game.lock's child
inherited exclusive handle and updater recovery are unchanged. Native auth,
game/updater modules and server components were not edited for this task.

Official API checked against installed Tauri2.12.1/API declarations and docs:
[Window customization](https://v2.tauri.app/learn/window-customization/),
[Tray](https://v2.tauri.app/learn/system-tray/),
[Single instance](https://v2.tauri.app/plugin/single-instance/).

## Verification

- Frontend46 tests passed, including three window regressions: distinct
  minimize/hide/close, maximize/restore/native resize state, safe API failures.
- Rust18 tests passed; two intentionally ignored manual game/live distribution
  probes. Existing trusted-device, game launch boundaries and updater tests pass.
- Real Windows Tauri/WebView integration build, not browser/mock: feature
  window-smoke with a separate app identifier and loopback account endpoint.
  Real frontend buttons were evaluated within own WebView and used normal IPC/
  capabilities; real native window flags were checked. Maximized button label
  was also read back through Tauri event IPC.
- Passed: tray creation, frontend maximize/restore/minimize/hide, tray open
  handlers from minimized/hidden, a real second executable exit + original
  window restoration. Two full smoke processes exited0: frontend close button
  and tray Exit handler respectively. Test mode does not access production
  device credentials/Backend and does not launch Minecraft or apply updates.
- Computer Use helper could not initialize (kernel assets path os error3),
  including one reset/retry. No OS-level manual tray mouse click/visual icon
  inspection is claimed; real menu handlers and tray creation were tested.

## Reproduce isolated native smoke

From launcher in PowerShell:

```powershell
$env:VITE_ACCOUNT_API_BASE_URL='http://127.0.0.1:18080'
npm run tauri -- build --debug --no-bundle --features window-smoke --config src-tauri/tauri.window-smoke.conf.json
$report=Join-Path $env:TEMP 'recreated-window-smoke.json'
$process=Start-Process -FilePath '.\src-tauri\target\debug\create-server-launcher.exe' -ArgumentList "--window-smoke-report=$report" -WindowStyle Hidden -PassThru
$process.WaitForExit(45000)
Get-Content -LiteralPath $report
$process.ExitCode
```

Repeat with additional --window-smoke-tray-exit argument to verify Exit handler.
Report must have ok:true, process exit0. A nonzero watchdog indicates close did
not terminate correctly. Smoke feature/alternate identifier are never enabled
in the distributed installer. Normal production build:

```powershell
$env:VITE_ACCOUNT_API_BASE_URL='https://api.recreated.ru'
npm run tauri -- build --bundles nsis
```

No production/VDS/approved latest publication or installed-app replacement.
Installer remains a local acceptance candidate, version0.1.0; do not publish over
the immutable approved0.1.0 artifact. Assign/approve a new release separately.

Windows release/NSIS build completed successfully. Handoff:
`launcher/artifacts/window-controls-20261004/RE-CREATED-Launcher-window-controls-x64-setup.exe`.
SHA-256: `e736f871333e67930afa8b6b53f378db4cdaabd86d5f2e05b8868d46ec6dff54`.
Production binary does not contain the --window-smoke-report marker; test feature
was not enabled. Existing release workflow tests: 6 passed. Smoke processes ended,
no Launcher test process left running. Native/device/game/updater regression
checks passed; no new game launch or live trusted-device/login acceptance claimed.
