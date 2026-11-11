package com.example.examplemod.client.logistics;
import com.example.examplemod.cargo.center.CenterNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
@EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class LogisticsClient {
    interface EquipmentPicker {
        void acceptEquipment(net.minecraft.nbt.CompoundTag data);
        default boolean matchesEquipment(net.minecraft.nbt.CompoundTag data){return true;}
        default void equipmentProgress(net.minecraft.nbt.CompoundTag data){}
        default void returnToWizard(){}
        default void renderEquipmentHud(net.minecraft.client.gui.GuiGraphics graphics,net.minecraft.network.chat.Component returnKey){}
    }
    private static EquipmentPicker equipmentScreen;
    static void awaitEquipment(EquipmentPicker screen){equipmentScreen=screen;}
    static void clearEquipment(EquipmentPicker screen){if(equipmentScreen==screen)equipmentScreen=null;}
    private static final KeyMapping OPEN=new KeyMapping("key.createcityexpansioncore.logistics",net.neoforged.neoforge.client.settings.KeyConflictContext.IN_GAME,InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_K,"key.categories.createcityexpansioncore");
    @SubscribeEvent public static void register(RegisterKeyMappingsEvent event){event.register(OPEN);CenterNetwork.CLIENT=data->{
        if(data.getBoolean("EquipmentProgress")||data.getBoolean("EquipmentSelection")){
            var selected=equipmentScreen;
            if(selected!=null&&selected.matchesEquipment(data)){
                if(data.getBoolean("EquipmentProgress"))selected.equipmentProgress(data);
                else {equipmentScreen=null;selected.acceptEquipment(data);}
            }
            return;
        }
        LogisticsMapOverlay.accept(data);var mc=Minecraft.getInstance();if(mc.screen instanceof CenterResponseScreen screen)screen.accept(data);else if(data.getBoolean("Open")){var screen=new LogisticsCenterScreen();mc.setScreen(screen);screen.accept(data);}};
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e)->{equipmentScreen=null;LogisticsMapOverlay.reset();});
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderGuiEvent.Post e)->{
            var mc=Minecraft.getInstance();if(equipmentScreen!=null&&mc.player!=null&&mc.screen==null&&!mc.options.hideGui)equipmentScreen.renderEquipmentHud(e.getGuiGraphics(),OPEN.getTranslatedKeyMessage());
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e)->{
            var mc=Minecraft.getInstance();if(OPEN.consumeClick()&&mc.player!=null&&mc.screen==null){if(equipmentScreen!=null)equipmentScreen.returnToWizard();else mc.setScreen(new LogisticsCenterScreen());}
        });}
}
