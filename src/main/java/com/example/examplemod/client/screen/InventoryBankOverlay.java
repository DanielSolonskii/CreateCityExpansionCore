package com.example.examplemod.client.screen;
import com.example.examplemod.client.BankClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import static com.example.examplemod.client.screen.WorldMapChrome.*;
@EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT)
public final class InventoryBankOverlay {
    private static InventoryBankLayout.Bounds bounds(InventoryScreen s){return InventoryBankLayout.calculate(s.width,s.height,s.getGuiLeft(),s.getGuiTop(),s.getXSize(),s.getYSize(),s.getRecipeBookComponent().isVisible());}
    private static InventoryBankLayout.Bounds market(InventoryScreen s){return InventoryBankLayout.market(s.width,s.height,s.getGuiLeft(),s.getGuiTop(),s.getXSize(),s.getYSize(),s.getRecipeBookComponent().isVisible());}
    @SubscribeEvent public static void marketRender(ScreenEvent.Render.Post event){
        if(!(event.getScreen() instanceof InventoryScreen s)||!BankClient.ready()||!BankClient.hasCity)return;
        var r=market(s);if(r==null)return;var g=event.getGuiGraphics();var rect=new WorldMapUiLayout.Rect(r.x(),r.y(),r.width(),r.height());
        control(g,rect,false,r.contains(event.getMouseX(),event.getMouseY()),true,false);
        g.renderItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD),r.x()+4,r.y()+3);
        g.drawString(Minecraft.getInstance().font,net.minecraft.network.chat.Component.translatable("gui.createcityexpansioncore.trade.inventory"),r.x()+24,r.y()+8,ACCENT,false);
    }
    @SubscribeEvent public static void render(ScreenEvent.Render.Post event){if(!(event.getScreen() instanceof InventoryScreen screen)||Minecraft.getInstance().player==null)return;var r=bounds(screen);if(r==null)return;BankClient.ensure();var g=event.getGuiGraphics();var font=Minecraft.getInstance().font;rounded(g,r.x(),r.y(),r.width(),r.height(),3,EDGE);rounded(g,r.x()+1,r.y()+1,r.width()-2,r.height()-2,3,SURFACE);g.fill(r.x()+1,r.y()+7,r.x()+3,r.y()+r.height()-7,ACCENT);BankIcons.draw(g,BankIcons.Icon.CARD,r.x()+7,r.y()+6);String amount=BankClient.ready()?BankClient.money(BankClient.wallet())+" CR":"â€” CR";
        if(r.compact()){g.drawString(font,fit(font,net.minecraft.network.chat.Component.literal(amount),r.width()-35),r.x()+28,r.y()+8,TEXT,false);}else{g.drawString(font,BankScreen.tr("available"),r.x()+28,r.y()+8,MUTED,false);g.drawString(font,fit(font,net.minecraft.network.chat.Component.literal(amount),r.width()-14),r.x()+8,r.y()+25,TEXT,false);control(g,new WorldMapUiLayout.Rect(r.x()+7,r.y()+42,r.width()-14,17),false,r.contains(event.getMouseX(),event.getMouseY()),true,false);g.drawString(font,BankScreen.tr("title"),r.x()+15,r.y()+47,ACCENT,false);}
        if(r.contains(event.getMouseX(),event.getMouseY()))g.renderTooltip(font,BankScreen.tr("inventory_hint").copy().append(" Â· ").append(amount),event.getMouseX(),event.getMouseY());
    }
    @SubscribeEvent public static void click(ScreenEvent.MouseButtonPressed.Pre event){if(event.getButton()!=0||!(event.getScreen() instanceof InventoryScreen screen))return;var m=market(screen);if(BankClient.ready()&&BankClient.hasCity&&m!=null&&m.contains(event.getMouseX(),event.getMouseY())){event.setCanceled(true);RemoteMarketEntry.open(screen);return;}var r=bounds(screen);if(r!=null&&r.contains(event.getMouseX(),event.getMouseY())){event.setCanceled(true);BankScreen.open(screen,null);}}
}
