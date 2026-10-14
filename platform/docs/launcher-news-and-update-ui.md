# Launcher: новости, changelog и экран обновления

## Публикация постов

Используется существующая админская рассылка Telegram Bot. Черновик не появляется
в Launcher: пост становится публичной новостью только после явного подтверждения
публикации. Отменённые посты не выдаются. Обычный текст становится новостью.

Для changelog первая строка должна содержать только маркер и версию:

```text
#update 1.0.18
Добавлено: новый мод.
Исправлено: ошибки городов.
Перед входом закройте Minecraft и проверьте сборку.
```

Также поддерживается существующий формат `Обновление 1.0.18`. Версия внутри
обычного предложения не превращает пост в changelog. Публикация поста НЕ
публикует игровые файлы и НЕ переключает game channel.

Самый свежий changelog показывается при открытии Launcher до первого закрытия
кнопкой или Escape. В localStorage хранится только UUID просмотренного поста.
История остаётся в разделе «Новости», фильтр «Обновления».

## API и безопасность

`GET /api/launcher/news` — публичная read-only проекция последних 50
QUEUED/COMPLETED постов с confirmed_at, по времени публикации от новых к старым.
Использует существующее ограничение запросов `/api/launcher/*`.

Поля: `id`, `title`, `body`, `releaseVersion`, `publishedAt`, `hasPhoto`.
Никаких Telegram file_id, admin ID, credentials или delivery records.
Текст отображается без HTML-интерпретации. Native transport использует фиксированный
Backend URL, таймаут, запрет redirect и лимит ответа 1 MiB. Ошибка новостей
не блокирует вход и запуск игры. Схема PostgreSQL не меняется.

Фото пока остаётся в Telegram: Launcher показывает текст и соответствующее
уведомление. Bot token не используется для публичных ссылок на изображения.
Для картинок нужен отдельный безопасный media publishing mechanism.

## Update UI и оформление

Экран показывает реальные этапы существующего updater: проверка → загрузка →
SHA-256 → установка → готово; файлы, байты и оценку скорости загрузки.
Progress bar относится к текущему этапу, а не ко всей операции. «Скрыть»
скрывает панель, но не отменяет операцию. При ошибке доступна повторная проверка.
Во время игры обновление запрещено существующим native guard.
Signed manifest, atomic apply, allowlist и self-update не перепроектированы.

Индустриальный intro длится около 1.2 секунды: RE:CREATED, световая полоса,
появление интерфейса. `prefers-reduced-motion` отключает эффект.
Press Start 2P используется для заголовков, меню и кнопок; увеличенный Manrope —
для длинного текста и форм. Это Minecraft-подобный, не оригинальный Minecraft
шрифт. Лицензия OFL находится в `launcher/public/licenses/press-start-2p.txt`
и включается в frontend assets. Внешняя загрузка шрифтов не нужна.

## Controlled deployment (пока НЕ выполнен)

1. Backup текущих Backend/Bot artifacts и production configuration.
2. Tests/build Backend и Bot, обновление Backend с read API, затем Bot prompt.
   Flyway migration не требуется. Существующие secrets/routing сохраняются.
3. Проверка публичного feed, приватности DTO, draft/published поведения.
4. Отдельная новая версия Windows Launcher с production endpoints; GUI E2E:
   news/changelog, закрытие/повторный запуск, недоступный feed, update/Play,
   trusted-device login и reduced-motion.
5. Только после приёмки — явная публикация approved Launcher существующим workflow.

Minecraft/CCEC, game manifests/channel и production в рамках реализации не меняются.
