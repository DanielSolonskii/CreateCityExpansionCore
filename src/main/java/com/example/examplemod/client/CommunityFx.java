package com.example.examplemod.client;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
/** Quiet local presentation settings; no gameplay state or tokens are stored here. */
public final class CommunityFx {
    public static boolean reduced, messageSound=true;public static float volume=.35f;public static final CommunityFeedbackSettings feedback = new CommunityFeedbackSettings();private static boolean loaded;private static String loadedScope="";private static final Set<UUID> muted=new HashSet<>();
    public static boolean muted(UUID id){load();return muted.contains(id);}
    public static void setMute(UUID id,boolean value){load();boolean changed=value?muted.add(id):muted.remove(id);if(changed)save();}
    public static void toggleMute(UUID id){load();if(!muted.remove(id))muted.add(id);save();}
    private CommunityFx(){}
    private static Path path(){return Minecraft.getInstance().gameDirectory.toPath().resolve("config/ccec-community.properties");}
    private static String scope(){var c=Minecraft.getInstance();return UUID.nameUUIDFromBytes(((c.getCurrentServer()==null?"local":c.getCurrentServer().ip)+":"+(c.player==null?"":c.player.getUUID())).getBytes(java.nio.charset.StandardCharsets.UTF_8))+".";}
    public static void load(){String scope=scope();if(loaded&&loadedScope.equals(scope))return;loaded=true;loadedScope=scope;muted.clear();var p=new Properties();try(var r=Files.newBufferedReader(path())){p.load(r);for(String key:p.stringPropertyNames())if(key.startsWith(loadedScope+"mute."))try{muted.add(UUID.fromString(key.substring((loadedScope+"mute.").length())));}catch(IllegalArgumentException ignored){}reduced=Boolean.parseBoolean(p.getProperty("reduced","false"));messageSound=Boolean.parseBoolean(p.getProperty("messageSound","true"));volume=Math.clamp(Float.parseFloat(p.getProperty("volume",".35")),0,1);if(!Float.isFinite(volume))volume=.35f;}catch(Exception ignored){}feedback.load(p);}
    public static void save(){try{Files.createDirectories(path().getParent());var p=new Properties();if(Files.isRegularFile(path()))try(var reader=Files.newBufferedReader(path())){p.load(reader);}p.keySet().removeIf(k->k.toString().startsWith(loadedScope+"mute."));feedback.save(p);p.setProperty("reduced",Boolean.toString(reduced));p.setProperty("messageSound",Boolean.toString(messageSound));p.setProperty("volume",Float.toString(volume));muted.stream().limit(128).forEach(id->p.setProperty(loadedScope+"mute."+id,"true"));var temp=path().resolveSibling("ccec-community.properties.tmp");try(var w=Files.newBufferedWriter(temp)){p.store(w,"CCEC community presentation");}Files.move(temp,path(),StandardCopyOption.REPLACE_EXISTING);}catch(Exception ignored){}}
    public static float ease(double time){if(reduced)return 1;double t=Math.clamp(time,0,1);return (float)(1-Math.pow(1-t,3));}
    public static void sound(String kind) {
        load();
        if (volume == 0 || kind.equals("message") && !messageSound
                || !feedback.sound(kind, net.minecraft.Util.getMillis(), System.currentTimeMillis())) return;
        var sound = switch (kind) {
            case "success", "map_ok" -> SoundEvents.NOTE_BLOCK_CHIME.value();
            case "error", "map_denied" -> SoundEvents.NOTE_BLOCK_BASS.value();
            case "message", "map_select" -> SoundEvents.NOTE_BLOCK_HAT.value();
            case "invite", "map_warning" -> SoundEvents.NOTE_BLOCK_BELL.value();
            case "map_save" -> SoundEvents.NOTE_BLOCK_PLING.value();
            case "map_navigation" -> SoundEvents.NOTE_BLOCK_FLUTE.value();
            case "map_event" -> SoundEvents.NOTE_BLOCK_HARP.value();
            default -> SoundEvents.UI_BUTTON_CLICK.value();
        };
        float pitch = switch (kind) {
            case "open", "map_denied" -> .85f;
            case "map_warning" -> .7f;
            case "map_navigation" -> 1.35f;
            default -> 1.15f;
        };
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch,
                volume * (kind.startsWith("map_") || kind.equals("click") ? .35f : .5f)));
    }
}
