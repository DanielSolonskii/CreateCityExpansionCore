package com.example.examplemod.client.map;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
@EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class MinimapKeys {
 private MinimapKeys(){}
 static final KeyMapping TOGGLE=new KeyMapping("key.createcityexpansioncore.minimap_toggle",KeyConflictContext.IN_GAME,InputConstants.Type.KEYSYM,org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN,"key.categories.createcityexpansioncore");
 static final KeyMapping SETTINGS=new KeyMapping("key.createcityexpansioncore.minimap_settings",KeyConflictContext.IN_GAME,InputConstants.Type.KEYSYM,org.lwjgl.glfw.GLFW.GLFW_KEY_N,"key.categories.createcityexpansioncore");
 @SubscribeEvent public static void register(RegisterKeyMappingsEvent event){event.register(TOGGLE);event.register(SETTINGS);}
}
