package com.example.examplemod.knowledge;

import com.example.examplemod.client.screen.KnowledgeBaseScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.minecraft.Util;

/**
 * Строка в тултипе предмета с привязанным файлом ({@code parent_item}) и
 * «логика открытия Ponder»: удержание клавиши K над предметом открывает базу
 * знаний на этом файле.
 *
 * <p>Как у Ponder: строка подсказки появляется в тултипе, а удержание измеряется
 * по игровой монотонной хронометре; открытие — один раз на предмет, повторное
 * открытие требует заново навести штатный предмет. Доступ виден из
 * синхронизированного списка: недоступный файл не открывается вовсе, а подпись на
 * его месте честно сообщает, что игрок знает предмет не весь.
 */
public final class KnowledgeTooltipHandler {
    /** Порог удержания, мс — медленное, но не бесконечное ожидание. */
    private static final long HOLD_MILLIS = 350;

    private static String hoveredItem;
    private static long holdSince;
    private static boolean holdOpen;

    private KnowledgeTooltipHandler() {
    }

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || minecraft.player == null) return;
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        KnowledgeFileMeta file = KnowledgeBaseClient.byParentItem(itemId);
        if (file == null) return;
        boolean allowed = KnowledgeBaseClient.hasAccess(file);
        if (allowed) {
            event.getToolTip().add(1, Component.translatable(
                    "tooltip.createcityexpansioncore.knowledge.hold",
                    KnowledgeKey.hold().getTranslatedKeyMessage()));
        } else {
            event.getToolTip().add(1, Component.translatable(
                    "tooltip.createcityexpansioncore.knowledge.locked"));
        }
        if (!allowed) {
            reset();
            return;
        }
        advanceHold(minecraft, itemId, file);
    }

    /**
     * Замер удержания: смена наведённого предмета перезапускает отсчёт, а
     * после фактического открытия таймер на этом предмете готов к новому циклу,
     * когда игрок снова наведёт курсор.
     */
    private static void advanceHold(Minecraft minecraft, String itemId, KnowledgeFileMeta file) {
        long now = Util.getMillis();
        if (!itemId.equals(hoveredItem)) {
            hoveredItem = itemId;
            holdSince = now;
            holdOpen = false;
        }
        if (holdOpen) return;
        if (!KnowledgeKey.hold().isDown()) {
            holdSince = now;
            return;
        }
        if (minecraft.screen != null || minecraft.level == null) return;
        if (now - holdSince < HOLD_MILLIS) return;
        holdOpen = true;
        KnowledgeBaseClient.ensureFolder();
        KnowledgeBaseScreen.open(null, file.path());
    }

    private static void reset() {
        hoveredItem = null;
        holdSince = 0;
        holdOpen = false;
    }
}
