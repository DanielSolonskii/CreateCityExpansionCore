package com.example.examplemod.client.screen;
import com.example.examplemod.client.*;
import com.example.examplemod.onboarding.OnboardingProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
/** A small optional reference book and the non-modal contextual card share existing atlas colours. */
public final class OnboardingHelpScreen extends Screen {
 private final Screen parent;private int page;
 public OnboardingHelpScreen(Screen parent){super(Component.translatable("gui.createcityexpansioncore.onboarding.title"));this.parent=parent;}
 private static Component tr(String key){return Component.translatable("gui.createcityexpansioncore.onboarding."+key);}
 @Override protected void init(){
  var r=OnboardingLayout.guide(width,height);int half=(r.width()-28)/2;
  addRenderableWidget(Button.builder(Component.literal("‹"),b->{page=Math.floorMod(page-1,OnboardingProgress.COUNT);}).bounds(r.x()+12,r.y()+r.height()-79,36,20).build());
  addRenderableWidget(Button.builder(Component.literal("›"),b->{page=(page+1)%OnboardingProgress.COUNT;}).bounds(r.x()+r.width()-48,r.y()+r.height()-79,36,20).build());
  addRenderableWidget(Button.builder(tr("later"),b->{OnboardingClient.later();onClose();}).bounds(r.x()+12,r.y()+r.height()-54,half,20).build());
  var skip=addRenderableWidget(Button.builder(tr("skip"),b->{OnboardingClient.skip();onClose();}).bounds(r.x()+16+half,r.y()+r.height()-54,half,20).build());skip.active=!OnboardingClient.skipped();
  addRenderableWidget(Button.builder(tr("close"),b->onClose()).bounds(r.x()+12,r.y()+r.height()-29,r.width()-24,20).build());
 }
 @Override public void render(GuiGraphics g,int mx,int my,float partial){var r=OnboardingLayout.guide(width,height);WorldMapChrome.panel(g,r.x(),r.y(),r.width(),r.height());g.drawString(font,title,r.x()+14,r.y()+12,WorldMapChrome.ACCENT,false);
  g.drawString(font,OnboardingClient.title(page),r.x()+14,r.y()+36,WorldMapChrome.TEXT,false);int y=r.y()+55;for(var line:font.split(OnboardingClient.body(page),r.width()-28)){if(y>r.y()+r.height()-95)break;g.drawString(font,line,r.x()+14,y,WorldMapChrome.MUTED,false);y+=11;}
  g.drawCenteredString(font,Component.translatable("gui.createcityexpansioncore.onboarding.page",page+1,OnboardingProgress.COUNT),width/2,r.y()+r.height()-73,WorldMapChrome.ACCENT);super.render(g,mx,my,partial);
 }
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
 private static OnboardingLayout.Rect bounds(int id,boolean screen){var mc=Minecraft.getInstance();return OnboardingLayout.card(mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),mc.font.split(OnboardingClient.body(id),Math.min(278,mc.getWindow().getGuiScaledWidth()-24)-24).size(),screen);}
 private static int entranceShift(long elapsed){return Math.round((1-(float)Math.clamp(elapsed/250d,0,1))*5);}
 public static int cardAction(int id,long elapsed,double x,double y){var r=bounds(id,true);y-=entranceShift(elapsed);for(int i=0;i<3;i++)if(OnboardingLayout.action(r,i).contains(x,y))return i;return -1;}
 public static void renderCard(GuiGraphics g,int id,long elapsed,boolean screen){
  var mc=Minecraft.getInstance();var r=bounds(id,screen);int shift=entranceShift(elapsed);
  g.pose().pushPose();g.pose().translate(0,shift,400);WorldMapChrome.rounded(g,r.x()+2,r.y()+3,r.width(),r.height(),5,0x40000000);WorldMapChrome.rounded(g,r.x(),r.y(),r.width(),r.height(),5,0xF21D2529);
  g.fill(r.x()+1,r.y()+9,r.x()+3,r.y()+r.height()-9,WorldMapChrome.ACCENT);
  Item icon=switch(id){case 0,1->Items.COMPASS;case 2->Items.PLAYER_HEAD;case 3->Items.BEACON;case 4->Items.NAME_TAG;case 5->Items.IRON_PICKAXE;default->Items.MAP;};g.renderItem(new ItemStack(icon),r.x()+11,r.y()+8);
  g.drawString(mc.font,WorldMapChrome.fit(mc.font,OnboardingClient.title(id),r.width()-45),r.x()+34,r.y()+11,WorldMapChrome.ACCENT,false);
  int y=r.y()+31;for(var line:mc.font.split(OnboardingClient.body(id),r.width()-24)){if(y>r.y()+r.height()-31)break;g.drawString(mc.font,line,r.x()+12,y,WorldMapChrome.TEXT,false);y+=10;}
  g.fill(r.x()+10,r.y()+r.height()-29,r.x()+r.width()-10,r.y()+r.height()-28,0xFF3D484B);
  if(screen){String[] names={"understood","later","skip"};for(int i=0;i<3;i++){var a=OnboardingLayout.action(r,i);g.fill(a.x(),a.y(),a.x()+a.width(),a.y()+a.height(),i==0?0xFF594326:0xFF263137);g.drawCenteredString(mc.font,WorldMapChrome.fit(mc.font,tr(names[i]),a.width()-4),a.x()+a.width()/2,a.y()+5,i==0?WorldMapChrome.ACCENT:WorldMapChrome.MUTED);}}
  else g.drawString(mc.font,Component.translatable("gui.createcityexpansioncore.onboarding.help_key",OnboardingClient.key("key.createcityexpansioncore.first_steps")),r.x()+12,r.y()+r.height()-19,WorldMapChrome.MUTED,false);
  g.pose().popPose();
 }
}
