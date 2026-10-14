package com.example.examplemod.client.status;

import com.example.examplemod.economy.FreightPointRules;
import com.example.examplemod.status.CcecStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Shared Railway/Logistics status card and transition-only toast presenter. */
public final class CcecStatusUi {
    private static final Map<UUID,CcecStatus.Code> LAST = new HashMap<>();
    private static final Map<UUID,String> LAST_POINT = new HashMap<>();
    /** Per-subject cooldown so a flapping state cannot toast on every refresh. */
    private static final Map<UUID,Long> LAST_TOAST_AT = new HashMap<>();
    private static final long COOLDOWN_MS = 3000L;
    private static final int MAX_TRACKED = 2048;
    private CcecStatusUi() {}
    public static Component title(CcecStatus s){return Component.translatable("status.createcityexpansioncore.unified."+s.key()+".title");}
    public static Component reason(CcecStatus s){return Component.translatable("status.createcityexpansioncore.unified."+s.key()+".reason");}
    public static Component hint(CcecStatus s){return Component.translatable("status.createcityexpansioncore.unified."+s.key()+".hint");}
    public static Component action(CcecStatus s){return Component.translatable("status.createcityexpansioncore.action."+s.action().name().toLowerCase(java.util.Locale.ROOT));}
    public static int color(CcecStatus s){return switch(s.category()){case SUCCESS->0xFF63B47D;case ACTION_REQUIRED->0xFFD6A342;case BLOCKED->0xFFD06363;case WARNING->0xFFE08045;};}
    public static String icon(CcecStatus s){return switch(s.category()){case SUCCESS->"✔";case ACTION_REQUIRED->"☀";case BLOCKED->"⊘";case WARNING->"▲";};}
    public static void render(GuiGraphics g,Font font,CcecStatus s,int x,int y,int w,boolean hint){
        if(g==null)return;int h=hint?48:36,c=color(s);g.fill(x,y,x+w,y+h,0xFF252C2F);g.fill(x,y,x+3,y+h,c);g.renderOutline(x,y,w,h,0xFF465055);
        int textWidth=Math.max(20,w-38);g.drawString(font,icon(s),x+10,y+8,c,false);
        g.drawString(font,Component.literal(font.plainSubstrByWidth(title(s).getString(),textWidth)),x+28,y+6,0xFFF1F3F4,false);
        g.drawString(font,Component.literal(font.plainSubstrByWidth(reason(s).getString(),textWidth)),x+28,y+18,0xFFB4BEC1,false);
        if(hint)g.drawString(font,Component.literal(font.plainSubstrByWidth(hint(s).getString(),textWidth)),x+28,y+32,0xFF8F9A9E,false);
    }
    /** What a point transition is worth telling the player about. Pure, so headless checks cover it. */
    public enum PointToast { SILENT, DEGRADED, RESTORED }
    /** First observation primes the cache; only later meaningful state changes toast. */
    public static void observe(UUID subject,CcecStatus status){
        CcecStatus.Code previous=LAST.put(subject,status.code());if(previous==null||previous==status.code())return;
        toast(subject,Component.literal(icon(status)+" "),title(status),reason(status));
    }
    /**
     * Decision for {@link #observePoint}. Kept separate from the toast itself so the remove/restore
     * and degrade/recover rules are verifiable without a client.
     */
    public static PointToast decision(String previous,String current){
        if(previous==null||previous.equals(current))return PointToast.SILENT;
        CcecStatus before=FreightPointRules.ui(previous),now=FreightPointRules.ui(current);
        if(!isBroken(before)&&!isBroken(now))return PointToast.SILENT;
        if(before.code()==CcecStatus.Code.POINT_DISABLED||before.code()==CcecStatus.Code.POINT_REMOVED)return PointToast.SILENT;
        return isBroken(now)?PointToast.DEGRADED:PointToast.RESTORED;
    }
    /**
     * Freight Point observation. A point coming back from a genuinely broken state to ONLINE gets an
     * explicit "restored" message instead of a generic card title, so recovery is never silent;
     * chunk unloads and deliberate disables stay quiet because they are not actionable.
     */
    public static void observePoint(UUID subject,String status){
        String previous=LAST_POINT.get(subject);
        PointToast decision=decision(previous,status);
        LAST_POINT.put(subject,status);
        if(decision==PointToast.SILENT)return;
        CcecStatus current=FreightPointRules.ui(status);
        if(decision==PointToast.RESTORED){
            toast(subject,Component.literal("✔ "),Component.translatable("gui.createcityexpansioncore.freight.point_restored"),reason(current));
            return;
        }
        toast(subject,Component.literal(icon(current)+" "),title(current),reason(current));
    }
    /** States a player can actually act on; chunk unloads and deliberate disables are not one. */
    private static boolean isBroken(CcecStatus s){
        if(s.code()==CcecStatus.Code.POINT_UNLOADED||s.code()==CcecStatus.Code.POINT_DISABLED
                ||s.code()==CcecStatus.Code.POINT_REMOVED||s.code()==CcecStatus.Code.POINT_READY)return false;
        return s.category()!=CcecStatus.Category.SUCCESS;
    }
    private static void toast(UUID subject,Component icon,Component heading,Component detail){
        long now=System.currentTimeMillis();Long previous=LAST_TOAST_AT.get(subject);
        if(previous!=null&&now-previous<COOLDOWN_MS)return;
        if(LAST_TOAST_AT.size()>MAX_TRACKED){LAST_TOAST_AT.clear();LAST.clear();LAST_POINT.clear();}
        LAST_TOAST_AT.put(subject,now);
        Minecraft mc=Minecraft.getInstance();
        SystemToast.add(mc.getToasts(),SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.literal(icon.getString()).append(heading),detail);
    }
}
