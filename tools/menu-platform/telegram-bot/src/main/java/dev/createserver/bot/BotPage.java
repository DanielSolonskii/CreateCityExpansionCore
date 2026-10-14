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
        boolean active = account != null && account.active();
        if (Set.of("account", "server", "notifications").contains(action) && !active) action = "home";
        return switch (action) {
            case "account" -> new BotPage("👤 Аккаунт\n\nИмя: " + account.username() +
                "\nСтатус: Активен\nTelegram: привязан к этому аккаунту.",List.of(
                    List.of(button("💻 Устройства", "devices")),List.of(button("← Главное меню", "home"))));
            case "server" -> notice("🎮 Сервер RE:CREATED\n\nДанные временно недоступны. Обновите меню.");
            case "notifications" -> notice("🔔 Уведомления\n\nЗдесь появятся новости о релизах, перезапусках и обслуживании.\n" +
                "Уведомления пока не подключены; подписка не включена.");
            case "code" -> notice("🔑 Подтверждение регистрации\n\nОтправьте код из Launcher: /code XXXXX-XXXXX.\n" +
                "Можно отправить и сам короткий код. Не отправляйте пароль или альфа-ключ.");
            case "help" -> notice("❓ Помощь\n\n1. Установите Launcher и создайте аккаунт с альфа-ключом.\n" +
                "2. Отсканируйте QR телефоном или отправьте боту /code и код из Launcher. Подтвердите свой аккаунт кнопкой.\n" +
                "3. Вернитесь в Launcher: дождитесь подтверждения, войдите и нажмите «Играть».\n\n" +
                "Если QR или код истёк, обновите его в Launcher. Пароль боту не нужен.");
            case "launcher" -> new BotPage("📥 Launcher RE:CREATED\n\n" +
                (launcherUrl == null ? "Ссылка на скачивание пока не настроена. Уточните её у организатора альфы."
                    : "Скачайте Windows Launcher, установите и войдите в аккаунт."),
                launcherUrl == null ? List.of(List.of(button("← Главное меню", "home"))) : List.of(
                    List.of(Map.of("text", "📥 Скачать Launcher", "url", launcherUrl)),
                    List.of(button("← Главное меню", "home"))));
            default -> active ? new BotPage("RE:CREATED\n\nДобро пожаловать, " + account.username() + ".\nВыберите раздел:", List.of(
                List.of(button("👤 Аккаунт", "account"), button("🎮 Сервер", "server")),
                List.of(button("📥 Launcher", "launcher"), button("🔔 Уведомления", "notifications")),
                List.of(button("❓ Помощь", "help")))) : new BotPage("RE:CREATED\n\n" +
                (account == null ? "Добро пожаловать! Создайте аккаунт в Launcher с альфа-ключом, затем подтвердите регистрацию здесь с телефона."
                    : "Telegram привязан, но аккаунт пока не активен. Проверьте Launcher или обратитесь к организатору альфы.") +
                "\n\nОтсканируйте QR из Launcher или введите короткий код.", List.of(
                List.of(button("📥 Скачать Launcher", "launcher")),
                List.of(button("🔑 Ввести код", "code"), button("❓ Помощь", "help"))));
        };
    }
}
