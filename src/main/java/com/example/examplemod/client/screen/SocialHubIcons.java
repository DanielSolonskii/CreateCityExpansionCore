package com.example.examplemod.client.screen;

import com.example.examplemod.client.CommunityFx;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Approved native 16px artwork. Both palettes keep identical silhouettes and transparent padding. */
public final class SocialHubIcons {
    public static final int SIZE=16,INSET=8,LABEL_OFFSET=INSET+SIZE+8;
    private static final ResourceLocation[] ACTIVE=new ResourceLocation[SocialHubScreen.Section.values().length];
    private static final ResourceLocation[] IDLE=new ResourceLocation[ACTIVE.length];
    static {
        for(var section:SocialHubScreen.Section.values()){
            String name=switch(section){case FINANCES->"bank";case HOME->"home";case MESSAGES->"messages";case FRIENDS->"friends";case CITY->"city";case ALLIANCE->"alliance";case INBOX->"notifications";case PROFILE->"profile";case COMMUNITY->"friends";};
            ACTIVE[section.ordinal()]=texture("active",name);IDLE[section.ordinal()]=texture("idle",name);
        }
    }
    enum Icon {
        PROFILE, STATISTICS, PROJECTS, SEARCH, EDIT, SAVE, REFRESH, SETTINGS, FRIENDS, MESSAGES, NOTIFICATIONS,
        CHAT_MORE, CHAT_SEND, CHAT_BACK, CHAT_BLOCK, CLOSE, NEXT, CHECK, ACTION, CITY, HOME, ALLIANCE, BANK, GIFT,
        REPRESENTATIVE;

        private final ResourceLocation idle;
        private final ResourceLocation active;

        Icon() {
            String name = name().toLowerCase(java.util.Locale.ROOT);
            if (this.name().equals("GIFT")) {
                idle = active = ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", "textures/gui/community/gift.png");
            } else {
                idle = texture("idle", name);
                active = texture("active", name);
            }
        }

        void draw(GuiGraphics graphics, int x, int y, boolean highlighted, float hover) {
            graphics.pose().pushPose();
            try {
                scaleAround(graphics, x + SIZE / 2f, y + SIZE / 2f, 1 + 0.12f * hover);
                graphics.blit(highlighted ? active : idle, x, y, 0, 0, SIZE, SIZE, SIZE, SIZE);
            } finally {
                graphics.pose().popPose();
            }
        }
    }

    static Icon navigationIcon(SocialHubScreen.Section section) {
        return switch (section) {
            case HOME -> Icon.HOME;
            case MESSAGES -> Icon.MESSAGES;
            case FRIENDS, COMMUNITY -> Icon.FRIENDS;
            case CITY -> Icon.CITY;
            case ALLIANCE -> Icon.ALLIANCE;
            case INBOX -> Icon.NOTIFICATIONS;
            case PROFILE -> Icon.PROFILE;
            case FINANCES -> Icon.BANK;
        };
    }

    static Icon buttonIcon(Component label) {
        String value = label.getString();
        switch (value) {
            case "×", "✕" -> { return Icon.CLOSE; }
            case "‹", "←" -> { return Icon.CHAT_BACK; }
            case "›", "→" -> { return Icon.NEXT; }
            case "↻" -> { return Icon.REFRESH; }
            case "⚙" -> { return Icon.SETTINGS; }
        }
        if (!(label.getContents() instanceof TranslatableContents translation)) return Icon.ACTION;
        String key = translation.getKey();
        if (key.equals("gui.createcityexpansioncore.alliance.representative")) return Icon.REPRESENTATIVE;
        String action = key.substring(key.lastIndexOf('.') + 1);
        return switch (action) {
            case "back", "search_back", "members_back" -> Icon.CHAT_BACK;
            case "older", "newer", "history", "no_history", "active", "legacy_record", "verified_record" -> Icon.PROJECTS;
            case "close", "cancel", "cancel_request", "decline", "revoke", "exclude", "vote_no" -> Icon.CLOSE;
            case "refresh", "retry" -> Icon.REFRESH;
            case "settings", "reduced", "volume", "quiet", "offline", "preview", "telegram" -> Icon.SETTINGS;
            case "find", "search", "search_submit", "search_profile", "search_button", "players", "browse_cities" -> Icon.SEARCH;
            case "save", "saving" -> Icon.SAVE;
            case "edit", "rename", "description_label", "change_role", "assign_role", "propose" -> Icon.EDIT;
            case "confirm", "accept", "join", "proceed", "apply", "claim", "submit", "assign", "vote_yes" -> Icon.CHECK;
            case "collect", "daily" -> Icon.GIFT;
            case "invite", "invitation", "invite_city", "invite_player", "friend_requests", "friends",
                    "add", "request", "apply_city", "representative" -> Icon.FRIENDS;
            case "blocked", "block", "unblock", "kick", "deny_land" -> Icon.CHAT_BLOCK;
            case "profile", "open_profile", "full_profile", "identity" -> Icon.PROFILE;
            case "write", "chat", "alliance_chat", "discussion", "messages", "dialogs", "chats", "open_messages",
                    "open_peer", "latest", "new_messages" -> Icon.MESSAGES;
            case "send", "reply", "post" -> Icon.CHAT_SEND;
            case "city", "cities", "open_city", "my_city", "on_map", "allow_land" -> Icon.CITY;
            case "alliance", "city_entry", "your_alliance", "council" -> Icon.ALLIANCE;
            case "tasks", "home", "overview", "center" -> Icon.HOME;
            case "workspace", "find_project", "projects", "plan", "passport", "create", "new", "present",
                    "continue", "view" -> Icon.PROJECTS;
            case "notifications", "requests", "invitations", "mute", "unmute", "later" -> Icon.NOTIFICATIONS;
            case "send_money", "request_money", "balance", "deposit", "withdraw", "transfer", "fund", "return",
                    "account_name" -> Icon.BANK;
            case "all_categories", "category" -> Icon.SEARCH;
            case "next_members" -> Icon.NEXT;
            default -> Icon.ACTION;
        };
    }

    static Component buttonCaption(Component label) {
        return switch (label.getString()) {
            case "×", "✕", "‹", "←", "›", "→", "↻", "⚙" -> Component.empty();
            default -> label;
        };
    }

    static void paintButton(GuiGraphics g, Font font, WorldMapUiLayout.Rect bounds, Component label, Icon icon,
            HoverMotion hoverMotion, boolean hovered, boolean enabled, boolean selected, boolean primary) {
        float motion = hoverMotion.amount(bounds, enabled && hovered);
        g.pose().pushPose();
        try {
            scaleButton(g, bounds, motion);
            WorldMapChrome.control(g, bounds, selected, enabled && hovered, enabled, primary);
            boolean iconOnly = label.getString().isEmpty() || bounds.width() < 32;
            if (icon == Icon.CLOSE && iconOnly && enabled) {
                WorldMapChrome.rounded(g, bounds.x(), bounds.y(), bounds.width(), bounds.height(), 4, 0xFF806747);
                WorldMapChrome.rounded(g, bounds.x()+1, bounds.y()+1, bounds.width()-2, bounds.height()-2, 3,
                        hovered ? 0xFF493924 : 0xFF343027);
            }
            int iconX = iconOnly ? bounds.x() + (bounds.width() - SIZE) / 2 : bounds.x() + 5;
            icon.draw(g, iconX, bounds.y() + (bounds.height() - SIZE) / 2, enabled && (hovered || selected), motion);
            if (!iconOnly) {
                int x = bounds.x() + SIZE + 10;
                int y = bounds.y() + (bounds.height() - font.lineHeight) / 2;
                int color = !enabled ? WorldMapChrome.MUTED : selected ? WorldMapChrome.ACCENT : WorldMapChrome.TEXT;
                g.drawString(font, WorldMapChrome.fit(font, label, Math.max(1, bounds.right()-x-5)), x, y, color, false);
            }
        } finally {
            g.pose().popPose();
        }
    }

    static final class HoverMotion {
        private record Sample(float amount, long lastSeen) {}
        private final Map<WorldMapUiLayout.Rect, Sample> samples = new HashMap<>();
        private long frameTime;
        private float blend;

        void beginFrame() {
            long now = Util.getMillis();
            blend = (float) (1 - Math.exp(-Math.min(50, frameTime == 0 ? 16 : now - frameTime) / 65.0));
            frameTime = now;
            samples.values().removeIf(sample -> now - sample.lastSeen() > 300);
        }

        float amount(WorldMapUiLayout.Rect bounds, boolean hovered) {
            if (CommunityFx.reduced) {
                samples.remove(bounds);
                return 0;
            }
            var previous = samples.get(bounds);
            float current = previous == null ? 0 : previous.amount();
            float next = current + ((hovered ? 1 : 0) - current) * blend;
            if (next < 0.01f && !hovered) {
                samples.remove(bounds);
                return 0;
            }
            samples.put(bounds, new Sample(next, frameTime));
            return next;
        }
    }

    static void scaleButton(GuiGraphics graphics, WorldMapUiLayout.Rect bounds, float hover) {
        float scale = 1 + Math.min(0.06f, 4f / Math.max(1, bounds.width())) * hover;
        scaleAround(graphics, bounds.x() + bounds.width() / 2f, bounds.y() + bounds.height() / 2f, scale);
    }

    private static void scaleAround(GuiGraphics graphics, float x, float y, float scale) {
        if (scale == 1) return;
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.pose().translate(-x, -y, 0);
    }

    private SocialHubIcons(){}
    private static ResourceLocation texture(String state,String name){return ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","textures/gui/community/"+state+"/"+name+".png");}
    public static void navigation(GuiGraphics graphics, SocialHubLayout.Rect row, SocialHubScreen.Section section,
            boolean highlighted) {
        navigation(graphics, row, section, highlighted, 0);
    }

    static void navigation(GuiGraphics graphics, SocialHubLayout.Rect row, SocialHubScreen.Section section,
            boolean highlighted, float hover) {
        int x = row.x() + INSET;
        int y = row.y() + (row.height() - SIZE) / 2;
        graphics.pose().pushPose();
        try {
            scaleAround(graphics, x + SIZE / 2f, y + SIZE / 2f, 1 + 0.12f * hover);
            graphics.blit((highlighted ? ACTIVE : IDLE)[section.ordinal()], x, y, 0, 0, SIZE, SIZE, SIZE, SIZE);
        } finally {
            graphics.pose().popPose();
        }
    }
}
