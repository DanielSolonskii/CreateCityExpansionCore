package com.example.examplemod.client;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.client.screen.PlayerProfileScreen;
import com.example.examplemod.network.OpenProfilePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

@EventBusSubscriber(modid=CreateCityExpansionCore.MODID,value=Dist.CLIENT)
public final class ProfileDiscoveryClient {
    private ProfileDiscoveryClient() {}
    public static void open(OpenProfilePayload payload) {
        var client=Minecraft.getInstance();
        if(client.player==null || client.getConnection()==null) return;
        PlayerProfileScreen.open(client.screen instanceof ChatScreen ? null : client.screen,payload.player());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void chat(ClientChatReceivedEvent.Player event) {
        var bound=event.getBoundChatType();
        if(bound==null || event.isSystem()) return;
        var decoration=bound.chatType().value().chat();
        Component message=event.getMessage();
        if(!(message.getContents() instanceof TranslatableContents text) || !text.getKey().equals(decoration.translationKey())) return;
        Object[] args=text.getArgs().clone();
        if(args.length!=decoration.parameters().size()) return;
        boolean changed=false;
        for(int i=0;i<args.length;i++) {
            if(decoration.parameters().get(i)==ChatTypeDecoration.Parameter.SENDER && args[i] instanceof Component sender) {
                // Only the protocol's sender slot; never scan the message body for matching nicknames.
                args[i]=sender.copy().withStyle(style->style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/ccec profile "+event.getSender()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Component.translatable("gui.createcityexpansioncore.profile.open_hint"))));
                changed=true;
            }
        }
        if(!changed) return;
        MutableComponent result=MutableComponent.create(new TranslatableContents(text.getKey(),text.getFallback(),args)).setStyle(message.getStyle());
        message.getSiblings().forEach(sibling->result.append(sibling.copy()));
        event.setMessage(result);
    }
}
