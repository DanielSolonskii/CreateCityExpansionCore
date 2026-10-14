package com.example.examplemod.client;

import com.example.examplemod.client.screen.PolishedWorldMapScreen;
import com.example.examplemod.network.RequestCityWorldMapPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Открытие карты города по клавише — только просмотр.
 *
 * <p><b>Почему проверка «есть ли открытый экран» жёсткая.</b> Карта может
 * открыться поверх чата, инвентаря или любого другого экрана. Взведённый город —
 * не то место, где вдруг появляется полноэкранная карта: она закрывает то, что
 * игрок делал, и её закрытие возвращает не туда, откуда её открыли. Поэтому
 * открывается она только из игры, из любого другого экрана игрок выходит сам.
 *
 * <p><b>Почему при закрытии карты сессия очищается.</b> Данные города не должны
 * жить дольше, чем открыта карта: иначе к городу можно вернуться без запроса и
 * увидеть устаревшую карту, а это выглядело бы как «сервер не обновил».
 *
 * <p><b>Почему запрос отправляется сразу, а не после ответа.</b> Экран рисует
 * состояние ожидания, поэтому игрок сразу видит, что нажатие учтено. Запрос же
 * остаётся единственным источником данных: если сервер откажет, карта не
 * появится вовсе.
 */
public final class CityWorldMapKeyHandler {

    private CityWorldMapKeyHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!CityWorldMapKey.consumeOpen()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }
        // Повторное нажатие закрывает открытую карту, чтобы не приходилось искать
        // кнопку закрытия.
        if (minecraft.screen instanceof PolishedWorldMapScreen) {
            minecraft.setScreen(null);
            return;
        }
        // Из другого экрана карта не открывается: возврат должен вести туда,
        // откуда игрок её открыл.
        if (minecraft.screen != null) {
            return;
        }
        CityWorldMapSession.INSTANCE.awaitAnchor();
        PacketDistributor.sendToServer(new RequestCityWorldMapPayload());
        minecraft.setScreen(new PolishedWorldMapScreen(CityWorldMapSession.INSTANCE));
    }

    /** Ключ категории для настроек управления. */
    public static String categoryKey() {
        return Component.translatable("key.categories.createcityexpansioncore").getString();
    }
}