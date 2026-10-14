# Launcher GUI import

Дата: 2026-10-03.

## Source и destination

Исходник:
`C:/Users/Administrator/.codex/.chatgpt-projects/g-p-6ab3ce086ddc81919dc5b393f320e19e/create-server-launcher`.
Destination: `E:/CreateCityExpansionCore_MERGED/CreateServerPlatform/launcher`.
Исходная папка остаётся без изменений. Это copy/import, не удаление оригинала.

Переносятся `src`, `src-tauri`, assets, index.html, package.json,
package-lock.json, tsconfig.json, vite.config.ts и .gitignore.
Не переносятся node_modules, dist, target, caches и персональные данные browser.
Имеющиеся launcher boundaries README сохранены в объединённом README;
исходный README GUI отдельно в launcher/docs/source-readme.md.
Runtime source и artwork копируются побайтово, без redesign/backend integration.

## Изученное поведение

React App управляет main/news/about navigation и login/register/profile/settings
panels. MockLauncherGateway возвращает fixed snapshot и имитирует check/launch
через delays. MockAuthGateway использует localStorage/sessionStorage и хранит
mock registration password plaintext. Rust shell содержит только Tauri Builder;
нет invoke commands, Minecraft launch, download/update или backend HTTP.
Tauri config: frameless window 1280x800, minimum 980x700, CSP null,
core:default capability, no custom process/filesystem permissions.

## Architectural compatibility

Перенос сохраняет GUI как isolated mock prototype, не объявляет fixtures
реальными platform/game данными. City/role/verified/playtime и server status
не server-authoritative. Profile username/Telegram handle не platform UUID.
Mock alpha-key/short-password registration противоречит production flow ADR-0003;
поэтому не подключается к backend в этой задаче. Future adapter должен выполнить
registration pending/deep link/polling/numeric Telegram verification и реальные
password validation rules, а не доверять editable @username.

ADR-0004 remains proposed: launcher memory-only capability, per-connection game
ticket и IPC/TLS требуют отдельного implementation milestone. Source README
recommendation хранить credentials в OS store не означает permission хранить
password/refresh token: first game-session proposal не предусматривает refresh.
Existing CCEC/server/backend/bot не изменяются для GUI import.

## Deferred issues

- Никакой настоящей authentication/Telegram verification или game launch.
- Mock plaintext password storage: только вымышленные demo данные.
- CSP/native permissions/icons нужно проверять до production packaging.
- Password reset/change account/game folder/resolution/sound UI placeholders.
- Settings partly in-memory, no durable settings service.
- City/role/playtime snapshots должны прийти из authoritative read projections.
- RU/EN localization, accessibility/focus handling и full resize/native acceptance
  требуют самостоятельной работы; перенос не маскирует их как completed.

## Verification

- 30 imported runtime/config/asset files сравнивались SHA-256 с исходником:
  побайтовое совпадение. package-lock сохранён; dependencies не обновлялись.
- npm ci --offline --ignore-scripts --no-audit --no-fund: SUCCESS, 75 packages
  из локального npm cache. Чужие install scripts не запускались.
- npm run build: SUCCESS, exit 0; TypeScript + Vite 6.4.3, 1606 modules,
  frontend bundle/images/local font assets сгенерированы.
- npm run tauri -- --version: tauri-cli 2.12.1 из исходного lockfile.
- Browser smoke на loopback dev server: main artwork, news navigation/filter/
  article, about page, публичный fixture login, profile popover/editor opening,
  settings opening, mock file-check progress -> ready и mock launch -> ready.
  Это UI mocks, не Minecraft/backend authentication/launch acceptance.
- Registration/profile edits не сохранялись; настоящие credentials не вводились.
- Native Tauri build/dev не запускались: Rust/Cargo не найдены в PATH и
  стандартной пользовательской .cargo/bin. C++ Build Tools/WebView2/native
  titlebar permissions не проверены. Unit-test script в исходнике отсутствует.
- node_modules и dist исключены Git ignore; source dist/cache не копировались.
- Aggregate SHA-256 manifests backend (49 files), bot (14) и CCEC src (697)
  совпадают до/после переноса. Существующие migrations и architecture ADR не менялись.
- GitNexus Exploring inventory: indexed только CreateCityExpansionCore;
  launcher не indexed, поэтому изучен по actual source, без изменения оригинала
  ради индексации. Graph CCEC не использован как заместитель launcher source.
