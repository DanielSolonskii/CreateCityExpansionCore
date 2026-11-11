package com.example.examplemod.client;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
@EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class BoundaryKeys {
 private BoundaryKeys(){}
 public static final KeyMapping TOGGLE=new KeyMapping("key.createcityexpansioncore.city_boundaries",KeyConflictContext.IN_GAME,InputConstants.Type.KEYSYM,org.lwjgl.glfw.GLFW.GLFW_KEY_G,"key.categories.createcityexpansioncore");
 @SubscribeEvent public static void register(RegisterKeyMappingsEvent event){event.register(TOGGLE);}
}
