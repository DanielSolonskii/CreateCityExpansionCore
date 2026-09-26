package com.example.examplemod.sound;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Регистрация звуковых событий мода.
 *
 * <p>Идентификаторы в точности соответствуют ключам в
 * {@code assets/createcityexpansioncore/sounds.json}: id события
 * {@code createcityexpansioncore:ui.button_press} ссылается на сэмплы
 * {@code createcityexpansioncore:ui/button_press_01..03}.
 *
 * <p>Здесь только объявления. Воспроизведение живёт в
 * {@code com.example.examplemod.client.sound} и строго на клиенте.
 *
 * <p>Громкость не задаётся здесь: она уже зашита в sounds.json
 * (тихий микс), а множители живут в местах вызова.
 */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, CreateCityExpansionCore.MODID);

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID, id)
        ));
    }

    /*
     * =========================================
     * UI
     * =========================================
     */

    public static final DeferredHolder<SoundEvent, SoundEvent> UI_BUTTON_PRESS = sound("ui.button_press");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_BUTTON_CONFIRM = sound("ui.button_confirm");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_BUTTON_DENY = sound("ui.button_deny");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_TAB_SWITCH = sound("ui.tab_switch");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_MODAL_OPEN = sound("ui.modal_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_MODAL_CLOSE = sound("ui.modal_close");

    /*
     * =========================================
     * CITY PYLON
     * =========================================
     */

    public static final DeferredHolder<SoundEvent, SoundEvent> PYLON_BOOT_START = sound("pylon.boot_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> PYLON_IDLE_HUM = sound("pylon.idle_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> PYLON_MECHANISM = sound("pylon.mechanism");
    public static final DeferredHolder<SoundEvent, SoundEvent> PYLON_RELAY_TICK = sound("pylon.relay_tick");
    public static final DeferredHolder<SoundEvent, SoundEvent> PYLON_SCREEN_WAKE = sound("pylon.screen_wake");
    public static final DeferredHolder<SoundEvent, SoundEvent> PYLON_SCREEN_SLEEP = sound("pylon.screen_sleep");
    public static final DeferredHolder<SoundEvent, SoundEvent> PYLON_ACTIVITY = sound("pylon.activity");
    public static final DeferredHolder<SoundEvent, SoundEvent> PYLON_WARNING = sound("pylon.warning");
    public static final DeferredHolder<SoundEvent, SoundEvent> PYLON_LEVEL_UP = sound("pylon.level_up");

    /*
     * =========================================
     * MAGNETIC ANALYZER
     * =========================================
     */

    public static final DeferredHolder<SoundEvent, SoundEvent> ANALYZER_OPEN = sound("analyzer.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANALYZER_SCAN_START = sound("analyzer.scan_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANALYZER_SCAN_LOOP = sound("analyzer.scan_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANALYZER_PING = sound("analyzer.ping");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANALYZER_DEPOSIT_FOUND = sound("analyzer.deposit_found");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANALYZER_NO_DEPOSIT = sound("analyzer.no_deposit");

    /*
     * =========================================
     * INDUSTRIAL MINE
     * =========================================
     */

    public static final DeferredHolder<SoundEvent, SoundEvent> MINE_CONTROLLER_BOOT = sound("mine.controller_boot");
    public static final DeferredHolder<SoundEvent, SoundEvent> MINE_DRILL_START = sound("mine.drill_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> MINE_DRILL_LOOP = sound("mine.drill_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> MINE_DRILL_STOP = sound("mine.drill_stop");
    public static final DeferredHolder<SoundEvent, SoundEvent> MINE_EMERGENCY_STOP = sound("mine.emergency_stop");
    public static final DeferredHolder<SoundEvent, SoundEvent> MINE_POWER_CONNECT = sound("mine.power_connect");
    public static final DeferredHolder<SoundEvent, SoundEvent> MINE_POWER_DISCONNECT = sound("mine.power_disconnect");
    public static final DeferredHolder<SoundEvent, SoundEvent> MINE_POWER_UNSTABLE = sound("mine.power_unstable");
    public static final DeferredHolder<SoundEvent, SoundEvent> MINE_GATEWAY_ITEM = sound("mine.gateway_item");

    /*
     * =========================================
     * DISPATCHER KEY
     * =========================================
     */

    public static final DeferredHolder<SoundEvent, SoundEvent> DISPATCHER_USE = sound("dispatcher.use");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISPATCHER_REGISTER_SUCCESS = sound("dispatcher.register_success");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISPATCHER_REGISTER_FAIL = sound("dispatcher.register_fail");

    /*
     * =========================================
     * RAILWAY
     * =========================================
     */

    public static final DeferredHolder<SoundEvent, SoundEvent> RAIL_STATION_BIND = sound("rail.station_bind");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAIL_SHIPMENT_COMPLETE = sound("rail.shipment_complete");

    /*
     * =========================================
     * ECONOMY
     * =========================================
     */

    public static final DeferredHolder<SoundEvent, SoundEvent> ECONOMY_LISTING_CREATE = sound("economy.listing_create");
    public static final DeferredHolder<SoundEvent, SoundEvent> ECONOMY_TRADE_COMPLETE = sound("economy.trade_complete");

    /*
     * =========================================
     * CONTRACTS
     * =========================================
     */

    public static final DeferredHolder<SoundEvent, SoundEvent> CONTRACT_ACCEPT = sound("contract.accept");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONTRACT_DELIVERY_TICK = sound("contract.delivery_tick");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONTRACT_CONFIRMED = sound("contract.confirmed");

    /*
     * =========================================
     * WAREHOUSE
     * =========================================
     */

    public static final DeferredHolder<SoundEvent, SoundEvent> WAREHOUSE_INPUT_TICK = sound("warehouse.input_tick");
    public static final DeferredHolder<SoundEvent, SoundEvent> WAREHOUSE_BATCH = sound("warehouse.batch");

    /*
     * =========================================
     * CITY / PROJECT
     * =========================================
     */

    public static final DeferredHolder<SoundEvent, SoundEvent> CITY_CREATE = sound("city.create");
    public static final DeferredHolder<SoundEvent, SoundEvent> CITY_DEVELOPMENT_CONFIRM = sound("city.development_confirm");
    public static final DeferredHolder<SoundEvent, SoundEvent> PROJECT_COMMISSION = sound("project.commission");

    /*
     * =========================================
     * SYSTEM
     * =========================================
     */

    public static final DeferredHolder<SoundEvent, SoundEvent> SYSTEM_ERROR = sound("system.error");
}
