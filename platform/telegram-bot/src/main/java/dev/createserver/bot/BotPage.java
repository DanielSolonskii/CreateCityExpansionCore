package dev.createserver.bot;

import java.util.*;

/** Presentation only; no verification credentials or notification preferences live here. */
record BotPage(String text, List<List<Map<String, String>>> keyboard) {
    /** Escape the entire plain-text page before applying a single trusted title tag. */
    String html() {
        String safe=text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
        int end=safe.indexOf('\n');
        return end<0 ? "<b>"+safe+"</b>" : "<b>"+safe.substring(0,end)+"</b>"+safe.substring(end);
    }
    static Map<String, String> button(String text, String action) {
        return Map.of("text", text, "callback_data", "menu:v1:" + action);
    }
    static BotPage notice(String text) {
        return new BotPage(text, List.of(List.of(button("← Главное меню", "home"))));
    }
    static BotPage render(String action, BackendClient.Account account, String launcherUrl) {
        return render(action, account, launcherUrl, null);
    }
    static BotPage render(String action, BackendClient.Account account, String launcherUrl, String macLauncherUrl) {
        boolean active = account != null && account.active();
        if (Set.of("account", "server", "notifications").contains(action) && !active) action = "home";
        return switch (action) {
            case "account" -> new BotPage("👤 Аккаунт\n\nИмя: " + account.username() +
                "\nСтатус: Активен\nTelegram: привязан к этому аккаунту.",List.of(
                    List.of(button("💻 Устройства", "devices")),List.of(button("← Главное меню", "home"))));
            case "server" -> notice("🎮 Сервер RE:CREATED\n\nДанные временно недоступны. Обновите меню.");
            case "notifications" -> notice("🔔 Новости RE:CREATED\n\nОбновления сборки, события и важные объявления приходят прямо в этот чат.\n\n" +
                "Меню потерялось среди сообщений? Отправьте /menu или нажмите «Меню вниз».");
            case "code" -> notice("🔑 Подтверждение регистрации\n\nОтправьте код из Launcher: /code XXXXX-XXXXX.\n" +
                "Можно отправить и сам короткий код. Не отправляйте пароль или альфа-ключ.");
            case "help" -> new BotPage("❓ Помощь\n\n" + (active
                ? "С чем нужна помощь? Выберите ситуацию ниже."
                : "Начинаете играть или возникла проблема? Выберите ситуацию ниже."), List.of(
                List.of(button("🚀 Как начать играть", "help_start")),
                List.of(button("🔑 QR или код не работает", "help_code")),
                List.of(button("🔐 Не получается войти", "help_login")),
                List.of(button("🎮 Игра не запускается", "help_game")),
                List.of(button("🎟 Как получить доступ", "help_access")),
                List.of(button("← Главное меню", "home"))));
            case "help_start" -> help("🚀 Как начать играть\n\n1. Скачайте Launcher для Windows или macOS.\n2. Создайте аккаунт с альфа-ключом.\n3. Отсканируйте QR телефоном или отправьте боту код из Launcher.\n4. Проверьте имя аккаунта и подтвердите регистрацию.\n5. Вернитесь в Launcher, войдите и нажмите «Играть».", true);
            case "help_code" -> help("🔑 QR или код не работает\n\nПолучите свежий QR или код на экране подтверждения в Launcher. Отсканируйте QR телефоном или отправьте сюда /code и короткий код.\n\nКод действует ограниченное время и используется один раз. Если регистрация уже завершилась, вернитесь на экран входа в Launcher.\n\nЕсли Telegram уже привязан к другому аккаунту, обратитесь к организатору альфы.", false);
            case "help_login" -> help("🔐 Не получается войти\n\nПроверьте имя аккаунта и пароль в Launcher. Если запрошено подтверждение устройства, откройте свежий QR и разрешите вход в этом чате.\n\nПодтверждайте только запрос, который открыли сами. Просроченный запрос нужно обновить в Launcher.\n\nЕсли доступ восстановить не удалось, обратитесь к организатору альфы. Пароль боту не нужен.", false);
            case "help_game" -> help("🎮 Игра не запускается\n\nОткройте Launcher и дождитесь завершения обновления сборки. Проверьте статус сервера в главном меню.\n\nЕсли ошибка повторяется, сохраните её текст, укажите Windows или macOS и версию Launcher и передайте организатору альфы. Перед отправкой скриншота скройте пароли, коды и ключи доступа.", true);
            case "help_access" -> help("🎟 Доступ к альфе\n\nДля создания аккаунта нужен альфа-ключ от организатора RE:CREATED. Бот не выдаёт ключи.\n\nЕсли ключа ещё нет или он не принимается, уточните доступ у организатора. Сам ключ отправлять боту не нужно.", false);
            case "launcher" -> new BotPage("📥 Скачать игру\n\nLauncher установит игровую сборку и будет обновлять её перед запуском.\n\nВыберите систему вашего компьютера.", List.of(
                List.of(button("🪟 Windows", "launcher_windows"), button("🍎 macOS", "launcher_macos")),
                List.of(button("← Главное меню", "home"))));
            case "launcher_windows" -> download("Windows", launcherUrl);
            case "launcher_macos" -> download("macOS", macLauncherUrl);
            default -> active ? new BotPage("RE:CREATED · Главное меню\n\nДобро пожаловать, " + account.username() + ".\nВыберите, что хотите открыть.", List.of(
                List.of(button("👤 Мой профиль", "account"), button("🎮 Сервер", "server")),
                List.of(button("🏙 Мои города", "cities"), button("🔔 Уведомления", "preferences")),
                List.of(button("📥 Скачать игру", "launcher")),
                List.of(button("❓ Помощь", "help"), button("↻ Обновить", "home")))) : new BotPage("RE:CREATED · Начать играть\n\n" +
                (account == null ? "Добро пожаловать! Создайте аккаунт в Launcher с альфа-ключом, затем подтвердите регистрацию здесь с телефона."
                    : "Telegram привязан, но аккаунт пока не активен. Проверьте Launcher или обратитесь к организатору альфы.") +
                "\n\nУже видите QR или код в Launcher? Подтвердите регистрацию здесь.", List.of(
                List.of(button("📥 Скачать Launcher", "launcher")),
                List.of(button("🔑 У меня есть код", "code")), List.of(button("❓ Помощь", "help"))));
        };
    }
    private static BotPage help(String text, boolean download) {
        var keys = new ArrayList<List<Map<String, String>>>();
        if (download) keys.add(List.of(button("📥 Скачать Launcher", "launcher")));
        keys.add(List.of(button("← Помощь", "help"), button("Главное меню", "home")));
        return new BotPage(text, keys);
    }
    private static BotPage download(String platform, String url) {
        var keys = new ArrayList<List<Map<String, String>>>();
        if (url != null) keys.add(List.of(Map.of("text", "📥 Скачать для " + platform, "url", url)));
        keys.add(List.of(button("← Выбрать систему", "launcher")));
        return new BotPage("📥 Launcher · " + platform + "\n\n" + (url == null
            ? "Ссылка на скачивание пока не настроена для " + platform + ". Уточните её у организатора альфы."
            : "Скачайте Launcher на компьютер с " + platform + ", установите его и войдите в аккаунт.\n\nВпервые здесь? Создайте аккаунт с альфа-ключом — Launcher подскажет следующие шаги."), keys);
    }
}
