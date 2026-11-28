package com.example.examplemod.client.screen;

import com.example.examplemod.projects.EngineeringPlan;
import com.example.examplemod.projects.EngineeringPlan.Node;
import net.minecraft.resources.ResourceLocation;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** One player/server directory; every document is additionally scoped to a confirmed world and dimension. */
final class PlanDraftStore {
    static final int LIMIT = 24;
    private static final int MAGIC = 0x43504c31; // CPL1
    final AtlasWorkspace.Area area;
    private final Path file;
    private final LinkedHashMap<UUID, EngineeringPlan> plans = new LinkedHashMap<>();
    boolean unreadable;
    private UUID selected;

    PlanDraftStore(Path directory, AtlasWorkspace.Area area) {
        this.area=area;
        file=directory.resolve("plans-"+AtlasWorkspace.digest(area.dimension()+"/"+area.world())+".bin");
        if (!Files.exists(file)) return;
        try (var in=new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (Files.size(file)>256_000 || in.readInt()!=MAGIC || !area.dimension().equals(in.readUTF())
                    || !area.world().equals(new UUID(in.readLong(),in.readLong()))) throw new IOException();
            int count=in.readUnsignedByte();
            if (count>LIMIT) throw new IOException();
            selected=new UUID(in.readLong(),in.readLong());
            for(int i=0;i<count;i++) {
                UUID id=new UUID(in.readLong(),in.readLong());
                var kind=EngineeringPlan.Kind.valueOf(in.readUTF());
                String name=in.readUTF(), note=in.readUTF(); int n=in.readUnsignedByte();
                if(n>EngineeringPlan.MAX_NODES) throw new IOException();
                var nodes=new ArrayList<Node>(n);
                for(int j=0;j<n;j++) nodes.add(new Node(in.readInt(),in.readInt()));
                var doc=new EngineeringPlan(id,ResourceLocation.parse(area.dimension()),kind,name,note,nodes);
                if (plans.put(id,doc)!=null) throw new IOException();
            }
            if (in.read()!=-1 || !plans.isEmpty() && !plans.containsKey(selected)) throw new IOException();
        } catch (IOException | IllegalArgumentException e) { plans.clear(); selected=null; unreadable=true; }
    }
    ProjectPlanDrafts sharedDrafts() {
        return new ProjectPlanDrafts(file.resolveSibling(file.getFileName()+".project-edits"),ResourceLocation.parse(area.dimension()));
    }
    EngineeringPlan selected() {return plans.get(selected);}
    List<EngineeringPlan> all() {return List.copyOf(plans.values());}
    void choose(UUID id) {if (plans.containsKey(id)) selected=id;}
    void remove(UUID id) {plans.remove(id); if (id.equals(selected)) selected=plans.isEmpty()?null:plans.firstEntry().getKey();}
    boolean put(EngineeringPlan doc) {
        if (!area.dimension().equals(doc.dimension().toString()) || !plans.containsKey(doc.id()) && plans.size()>=LIMIT) return false;
        plans.put(doc.id(),doc); selected=doc.id(); return true;
    }
    void save() throws IOException {
        if (unreadable) throw new IOException("Unrecognized or damaged plan library");
        Files.createDirectories(file.getParent());
        Path temp=Files.createTempFile(file.getParent(),"plan-", ".tmp");
        try {
            try (var out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
                out.writeInt(MAGIC); out.writeUTF(area.dimension()); uuid(out,area.world());
                out.writeByte(plans.size()); uuid(out,selected==null?new UUID(0,0):selected);
                for(var doc:plans.values()) {
                    uuid(out,doc.id()); out.writeUTF(doc.kind().name()); out.writeUTF(doc.name()); out.writeUTF(doc.note());
                    out.writeByte(doc.nodes().size()); for(var p:doc.nodes()){out.writeInt(p.x());out.writeInt(p.z());}
                }
            }
            try {Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch (AtomicMoveNotSupportedException e) {Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temp);}
    }
    private static void uuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits());
    }
}
