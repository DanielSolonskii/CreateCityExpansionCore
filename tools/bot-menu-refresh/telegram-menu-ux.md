# Telegram menu navigation

- `/start` and `/menu` open the main menu next to the latest user message.
- Incoming private messages (including registration codes and admin post drafts) create a fresh menu anchor. Inline navigation edits that anchor rather than creating a chain of messages.
- Every screen has **↓ Меню вниз**, which opens a fresh main menu at the bottom when announcements have pushed navigation into history.
- After a new anchor is successfully sent, only the previous menu's inline keyboard is cleared. No messages or user history are deleted. Cleanup failures do not break the new menu.
- Registration/device confirmations still require explicit confirmation and the existing numeric Telegram sender checks. Old read-only navigation returns to the current main menu.
- Presentation caches are bounded and contain no account credentials; a restart recovers via `/start`, `/menu` or an existing callback.
- No API/schema changes, notification preference settings or broadcast sends are introduced by this UI update.

## Acceptance

Run `gradlew.bat test build installDist` in `telegram-bot`.
In Telegram: `/start` → navigate all sections → send text → menu should appear below it → navigate without more messages → **↓ Меню вниз** → `/menu`.
Also check a phone registration, trusted-device confirmation and admin draft preview. Tests use a fake Telegram API; real phone/desktop rendering requires manual acceptance after deployment.
