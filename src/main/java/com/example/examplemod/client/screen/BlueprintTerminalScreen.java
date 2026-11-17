package com.example.examplemod.client.screen;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.client.BlueprintPlacement;
import com.example.examplemod.multiblock.BlueprintCatalog;
import com.example.examplemod.multiblock.BlueprintCatalog.Plan;
import com.example.examplemod.multiblock.BlueprintCatalog.Structure;
import com.example.examplemod.multiblock.BlueprintCatalog.Tier;
import com.example.examplemod.network.BlueprintAccessNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static com.example.examplemod.client.screen.WorldMapChrome.*;

public final class BlueprintTerminalScreen extends CommunityPage {
    private static final String PREFIX = "gui.createcityexpansioncore.blueprint.";
    private static final Structure[] STRUCTURES = Structure.values();
    private static final Tier[] TIERS = Tier.values();
    private final Map<Structure, BlueprintPreview> thumbnails = new EnumMap<>(Structure.class);
    private BlueprintAccessNetwork.State access;
    private UUID accessRequest;
    private Structure selected;
    private Plan plan;
    private BlueprintPreview preview;
    private Map<Item, Integer> required;
    private Map<Item, Integer> additions;
    private int filter;

    public BlueprintTerminalScreen() {
        super(null, "blueprint_terminal");
        for (Structure structure : STRUCTURES) {
            thumbnails.put(structure, new BlueprintPreview(BlueprintCatalog.plan(structure, Tier.I)));
        }
    }

    @Override
    protected void init() {
        super.init();
        accessRequest = UUID.randomUUID();
        PacketDistributor.sendToServer(new BlueprintAccessNetwork.Request(accessRequest));
    }

    public static void acceptAccess(BlueprintAccessNetwork.State state) {
        if (state.isCityUpdate()) {
            BlueprintPlacement.updateBlueprintAccess(state.tiers());
        }
        if (Minecraft.getInstance().screen instanceof BlueprintTerminalScreen screen
                && (state.isCityUpdate() || state.requestId().equals(screen.accessRequest))) {
            screen.access = state;
            screen.clearHits();
            if (screen.selected != null && !screen.canSelect(screen.selected, screen.plan.tier())) {
                int level = state.unlockedLevel(screen.selected);
                if (level == 0) {
                    screen.selected = null;
                    screen.transition();
                } else {
                    screen.select(screen.selected, screen.selected.tierFromLevel(level));
                }
            }
        }
    }

    @Override
    public Component getTitle() {
        return label("title");
    }

    @Override
    protected void draw(GuiGraphics graphics, float partial) {
        rounded(graphics, 12, 12, 596, 60, 6, RAISED);
        graphics.pose().pushPose();
        graphics.pose().translate(25, 26, 0);
        graphics.pose().scale(2, 2, 1);
        graphics.renderItem(CreateCityExpansionCore.BLUEPRINT_TERMINAL.get().getDefaultInstance(), 0, 0);
        graphics.pose().popPose();
        text(graphics, label("title"), 72, 26, 490, TEXT);
        text(graphics, label("subtitle"), 72, 47, 490, MUTED);

        if (access == null) {
            text(graphics, label("access_loading"), 20, 94, 570, MUTED);
        } else if (selected == null) {
            drawCatalog(graphics);
        } else {
            drawDetails(graphics);
        }
    }

    private void drawCatalog(GuiGraphics graphics) {
        String[] labels = {"all", "extraction", "storage"};
        SocialHubScreen.Section[] icons = {SocialHubScreen.Section.HOME,
                SocialHubScreen.Section.CITY, SocialHubScreen.Section.FINANCES};
        for (int index = 0; index < labels.length; index++) {
            int next = index;
            tabButton(graphics, index, labels.length, label(labels[index]), icons[index], () -> {
                filter = next;
                transition();
            }, true, index == filter);
        }
        clip(graphics);
        int y = 0;
        for (Structure structure : STRUCTURES) {
            if (!canSelect(structure, Tier.I) || (filter != 0 && (filter == 1) != (structure == Structure.MINE))) {
                continue;
            }
            var rect = row(y, 57);
            card(graphics, rect, accent(structure));
            thumbnails.get(structure).draw(graphics, rect.x() + 8, rect.y() + 4, 94, 49);
            text(graphics, name(structure), rect.x() + 112, rect.y() + 14, rect.width() - 155, TEXT);
            text(graphics, label(structure.key() + ".summary"), rect.x() + 112, rect.y() + 33,
                    rect.width() - 155, MUTED);
            button(graphics, new SocialHubLayout.Rect(rect.right() - 35, rect.y() + 15, 25, 27),
                    Component.literal("›"), () -> select(structure, Tier.I), true, false);
            // The thumbnail and name are also clickable, as in the catalog mockup.
            buttonArea(rect, () -> select(structure, Tier.I));
            y += 64;
        }
        if (y == 0) {
            var empty = row(0, 24);
            text(graphics, label("empty_catalog"), empty.x() + 8, empty.y() + 8, empty.width() - 16, MUTED);
        }
        unclip(graphics);
    }

    private void drawDetails(GuiGraphics graphics) {
        button(graphics, new SocialHubLayout.Rect(12, 84, 109, 27), label("back"), () -> {
            selected = null;
            transition();
        }, true, false);
        text(graphics, name(selected), 136, 93, 458, TEXT);
        clip(graphics);
        var details = row(0, Math.max(190, materialHeight()));
        int x = details.x();
        int y = details.y();
        rounded(graphics, x, y, 333, 116, 5, INSET);
        for (int line = 12; line < 333; line += 12) {
            graphics.fill(x + line, y + 1, x + line + 1, y + 115, 0xff25343a);
        }
        for (int line = 12; line < 116; line += 12) {
            graphics.fill(x + 1, y + line, x + 332, y + line + 1, 0xff25343a);
        }
        preview.draw(graphics, x + 5, y + 4, 323, 108);
        int tierCount = selected.maxTier().level();
        int tierWidth = (333 - 3 * (tierCount - 1)) / tierCount;
        for (int index = 0; index < tierCount; index++) {
            Tier tier = TIERS[index];
            boolean available = canSelect(selected, tier);
            var tierButton = new SocialHubLayout.Rect(x + (tierWidth + 3) * index, y + 123, tierWidth, 25);
            button(graphics, tierButton, label("tier", tier.name()), () -> select(selected, tier),
                    available, plan.tier() == tier);
            if (!available) {
                hint(tierButton, label("tier_locked"));
            }
        }
        int lineY = y + 157;
        for (var line : font.split(label(selected.key() + ".description"), 324)) {
            graphics.drawString(font, line, x + 3, lineY, MUTED, false);
            lineY += 11;
        }

        int right = x + 343;
        int width = details.right() - right;
        section(graphics, right, y, width, 51, label("stats"), 0xff8cc7aa);
        if (selected == Structure.MINE) {
            text(graphics, label("mine.output", plan.tier().outputPercent()), right + 10, y + 22, width - 18, TEXT);
            text(graphics, label("mine.power", plan.tier().powerPercent()), right + 10, y + 36, width - 18, TEXT);
        } else {
            int length = 2 * (plan.tier().ordinal() + 1);
            text(graphics, label("dimensions", selected == Structure.TANK ? "2 × " + length + " × 2"
                    : "2 × 2 × " + length), right + 10, y + 22, width - 18, TEXT);
            text(graphics, label("count", 4 * length), right + 10, y + 36, width - 18, TEXT);
        }
        int listTop = y + 59;
        int end = materials(graphics, right, listTop, width, label("materials"), required);
        if (plan.tier() != Tier.I) {
            materials(graphics, right, end + 6, width, label("additions"), additions);
        }
        unclip(graphics);
        button(graphics, new SocialHubLayout.Rect(12, layout.height() - 32, 214, 25),
                label("place"), () -> BlueprintPlacement.begin(plan), canSelect(selected, plan.tier()), true);
    }

    private int materialHeight() {
        return 59 + 21 + required.size() * 19
                + (plan.tier() == Tier.I ? 0 : 27 + additions.size() * 19);
    }

    private int materials(GuiGraphics graphics, int x, int y, int width,
                          Component title, Map<Item, Integer> items) {
        int height = 21 + items.size() * 19;
        section(graphics, x, y, width, height, title, 0xff77bdd2);
        int rowY = y + 19;
        for (var entry : items.entrySet()) {
            ItemStack stack = entry.getKey().getDefaultInstance();
            graphics.renderItem(stack, x + 8, rowY);
            text(graphics, stack.getHoverName(), x + 28, rowY + 5, width - 68, TEXT);
            String count = "×" + entry.getValue();
            graphics.drawString(font, count, x + width - 9 - font.width(count), rowY + 5, MUTED, false);
            hint(new SocialHubLayout.Rect(x + 6, rowY, width - 12, 18), stack.getHoverName());
            rowY += 19;
        }
        return y + height;
    }

    private void section(GuiGraphics graphics, int x, int y, int width, int height,
                         Component title, int color) {
        card(graphics, new SocialHubLayout.Rect(x, y, width, height), color);
        text(graphics, title, x + 10, y + 7, width - 18, TEXT);
    }

    private void select(Structure structure, Tier tier) {
        if (!canSelect(structure, tier)) {
            return;
        }
        boolean differentStructure = selected != structure;
        selected = structure;
        plan = BlueprintCatalog.plan(structure, tier);
        preview = new BlueprintPreview(plan);
        required = plan.materials(false);
        additions = plan.materials(true);
        if (differentStructure) {
            transition();
        }
    }

    private boolean canSelect(Structure structure, Tier tier) {
        return access != null && tier.level() <= access.unlockedLevel(structure);
    }

    private static int accent(Structure structure) {
        return switch (structure) {
            case MINE -> ACCENT;
            case VAULT -> 0xff8cc7aa;
            case TANK -> 0xff77bdd2;
        };
    }

    private static Component name(Structure structure) {
        return label(structure.key() + ".name");
    }

    private static Component label(String suffix, Object... args) {
        return Component.translatable(PREFIX + suffix, args);
    }
}
