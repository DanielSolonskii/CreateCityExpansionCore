# Происхождение исходного GUI launcher

**Сверка состояния: 2026-11-04.** [Актуальный аудит CCEC/Cargo](../../../docs/audit-2026-11-04/README.md) описывает интеграцию 1.0.2.15. Ранее датированные результаты проверок ниже сохраняют свою дату; новый игровой/production прогон ими не подтверждается.

Актуализировано 02.11.2026. Этот файл сохраняет provenance UI prototype после
интеграции в CCEC. Текущие команды и ограничения:
[launcher README](../README.md), [platform STATE](../../STATE.md).

Первоначальный GUI использовал Tauri 2, React/TypeScript, Vite/CSS и
`LauncherGateway` с mock profile/news/status/launch actions. Старые demo
credentials, plaintext storage и mock alpha keys не являются действующим
account protocol и больше не приводятся как инструкция.

Текущие исходники включают backend registration, Telegram/trusted-device flow,
Windows Credential Manager, native Game Session, signed game/launcher updates,
settings/status/news/profile/telemetry. При этом
[MockLauncherGateway](../src/services/mockLauncherGateway.ts) остаётся
bootstrap display snapshot; наличие mock файла не означает mock всех действий.

Native сборка этой копии блокируется отсутствующими Rust modules/command.
Результаты и runtime acceptance gaps: [аудит](../../../docs/audit-2026-11-02/PLATFORM_AUDIT_RU.md).
История интеграции: [GUI import](../../docs/launcher-gui-import.md).
