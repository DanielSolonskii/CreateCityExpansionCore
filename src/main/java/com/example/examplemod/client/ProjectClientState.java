package com.example.examplemod.client;

import com.example.examplemod.network.*;
import com.example.examplemod.projects.*;
import net.minecraft.nbt.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Scoped map session. Older replies cannot overwrite a newer command or another connection. */
public final class ProjectClientState {
    public record Marker(UUID id,String title,Project.Scope scope,ProjectZone zone,Project.Status status,Project.Category category) {}
    public static List<Project> records=List.of();
    public static List<Marker> markers=List.of();
    public static UUID city,created;
    public static boolean admin,op,cityRepresentative;
    public static int page,filter,total,serial;
    public static long wallet,treasury,maximum,serverLimit,serverAvailable,serverIssued,serverReserved;
    public static CompoundTag conflicts=new CompoundTag();
    public static Set<UUID> cityMembers=Set.of();
    public static String result="loading";
    public static CompoundTag names=new CompoundTag();
    private static ProjectCommandPayload pending;
    private static int ticks;
    private ProjectClientState(){}
    public static void begin(){clear();request(null);}
    public static void clear(){records=List.of();markers=List.of();city=null;created=null;admin=op=cityRepresentative=false;serverLimit=serverAvailable=serverIssued=serverReserved=0;conflicts=new CompoundTag();cityMembers=Set.of();page=filter=total=0;wallet=treasury=maximum=0;names=new CompoundTag();result="loading";pending=null;ticks=0;serial++;}
    public static boolean pending(){return pending!=null;}
    public static int typeFilter(){return filter/4;}
    public static int statusFilter(){return filter%4;}
    public static void setTypeFilter(int type){filter=type*4+statusFilter();page=0;}
    public static void send(ProjectManager.Command c){if(pending!=null)return;pending=new ProjectCommandPayload(c,page,filter);ticks=0;result="loading";PacketDistributor.sendToServer(pending);}
    public static void request(UUID selected){send(new ProjectManager.Command(UUID.randomUUID(),selected==null?new UUID(0,0):selected,0,ProjectManager.Action.REQUEST,new UUID(0,0),0,false,"",null));}
    public static void tick(){if(++ticks<100)return;ticks=0;if(pending!=null)PacketDistributor.sendToServer(pending);else request(null);}
    public static void accept(ProjectStatePayload payload) {
        var t=payload.data();if(pending==null||!t.hasUUID("Request")||!pending.command().request().equals(t.getUUID("Request")))return;
        pending=null;result=t.getString("Result");if(t.getBoolean("KeepState")){if(t.contains("Op"))op=t.getBoolean("Op");serial++;return;}
        city=t.hasUUID("City")?t.getUUID("City"):null;admin=t.getBoolean("Admin");created=t.hasUUID("Created")?t.getUUID("Created"):null;
        op=t.getBoolean("Op");cityRepresentative=t.getBoolean("CityRepresentative");conflicts=t.getCompound("Conflicts");
        Set<UUID> members=new HashSet<>();for(Tag raw:t.getList("CityMembers",Tag.TAG_STRING))members.add(UUID.fromString(raw.getAsString()));cityMembers=Set.copyOf(members);
        serverLimit=t.getLong("ServerLimit");serverAvailable=t.getLong("ServerAvailable");serverIssued=t.getLong("ServerIssued");serverReserved=t.getLong("ServerReserved");
        page=t.getInt("Page");filter=t.getInt("Filter");total=t.getInt("Total");wallet=t.getLong("Wallet");treasury=t.getLong("Treasury");maximum=t.getLong("Maximum");names=t.getCompound("Names");
        records=ProjectCodec.load(t.getCompound("Records"));List<Marker> list=new ArrayList<>();
        for(Tag raw:t.getList("Markers",Tag.TAG_COMPOUND)){CompoundTag m=(CompoundTag)raw,z=m.getCompound("Zone");
            list.add(new Marker(m.getUUID("Id"),m.getString("Title"),Project.Scope.valueOf(m.getString("Scope")),new ProjectZone(net.minecraft.resources.ResourceLocation.parse(z.getString("Dimension")),ProjectZone.Shape.valueOf(z.getString("Shape")),z.getInt("X1"),z.getInt("Z1"),z.getInt("X2"),z.getInt("Z2")),Project.Status.valueOf(m.getString("Status")),Project.Category.valueOf(m.getString("Category"))));}
        markers=List.copyOf(list);serial++;
    }
}
