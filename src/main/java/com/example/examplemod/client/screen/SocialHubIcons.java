package com.example.examplemod.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Approved native 16px artwork. Both palettes keep identical silhouettes and transparent padding. */
public final class SocialHubIcons {
    public static final int SIZE=16,INSET=8,LABEL_OFFSET=INSET+SIZE+8;
    private static final ResourceLocation[] ACTIVE=new ResourceLocation[SocialHubScreen.Section.values().length];
    private static final ResourceLocation[] IDLE=new ResourceLocation[ACTIVE.length];
    static {
        for(var section:SocialHubScreen.Section.values()){
            String name=switch(section){case HOME->"home";case MESSAGES->"messages";case FRIENDS->"friends";case CITY->"city";case ALLIANCE->"alliance";case INBOX->"notifications";case PROFILE->"profile";};
            ACTIVE[section.ordinal()]=texture("active",name);IDLE[section.ordinal()]=texture("idle",name);
        }
    }
    private SocialHubIcons(){}
    private static ResourceLocation texture(String state,String name){return ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","textures/gui/community/"+state+"/"+name+".png");}
    public static void navigation(GuiGraphics graphics,SocialHubLayout.Rect row,SocialHubScreen.Section section,boolean highlighted){
        graphics.blit((highlighted?ACTIVE:IDLE)[section.ordinal()],row.x()+INSET,row.y()+(row.height()-SIZE)/2,0,0,SIZE,SIZE,SIZE,SIZE);
    }
}
