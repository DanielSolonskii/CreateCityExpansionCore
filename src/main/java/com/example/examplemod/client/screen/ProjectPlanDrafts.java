package com.example.examplemod.client.screen;

import com.example.examplemod.projects.EngineeringPlan;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** Open only after a fresh authorized read of this exact project and document. */
final class ProjectPlanDrafts {
    record Draft(long base,EngineeringPlan plan){}
    private final Path directory;
    private final ResourceLocation dimension;
    ProjectPlanDrafts(Path directory,ResourceLocation dimension){this.directory=directory;this.dimension=dimension;}
    private Path path(UUID project,UUID id){return directory.resolve(project+"_"+id+".bin");}
    Draft read(UUID project,UUID id) throws IOException {
        Path file=path(project,id);
        if(!Files.exists(file))return null;
        if(Files.size(file)>8192)throw new IOException("Draft too large");
        try(var in=new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))){
            if(in.readInt()!=0x43504431)throw new IOException("Draft version");
            long version=in.readLong(); var kind=EngineeringPlan.Kind.valueOf(in.readUTF());
            String title=in.readUTF(),note=in.readUTF(); int n=in.readUnsignedByte();
            if(version<=0 || n>EngineeringPlan.MAX_NODES)throw new IOException("Draft size");
            var nodes=new ArrayList<EngineeringPlan.Node>(n);
            for(int i=0;i<n;i++)nodes.add(new EngineeringPlan.Node(in.readInt(),in.readInt()));
            if(in.read()!=-1)throw new IOException("Trailing data");
            return new Draft(version,new EngineeringPlan(id,dimension,kind,title,note,nodes));
        } catch(IllegalArgumentException e){throw new IOException(e);}
    }
    void save(UUID project,long base,EngineeringPlan plan) throws IOException {
        Files.createDirectories(directory);var dest=path(project,plan.id());
        if(!Files.exists(dest))try(var files=Files.list(directory)){
            if(files.filter(p->p.getFileName().toString().endsWith(".bin")).limit(16).count()>=16)
                throw new IOException("Draft limit");
        }
        Path tmp=Files.createTempFile(directory,"edit-",".tmp");
        try {
            try(var out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))){
                out.writeInt(0x43504431);out.writeLong(base);out.writeUTF(plan.kind().name());
                out.writeUTF(plan.name());out.writeUTF(plan.note());out.writeByte(plan.nodes().size());
                for(var n:plan.nodes()){out.writeInt(n.x());out.writeInt(n.z());}
            }
            try {Files.move(tmp,dest,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(tmp,dest,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(tmp);}
    }
    void clear(UUID project,UUID id) throws IOException {Files.deleteIfExists(path(project,id));}
    void remember(UUID project,long base,EngineeringPlan original,EngineeringPlan working) throws IOException {
        if(working.equals(original))clear(project,working.id());
        else save(project,base,working);
    }
}
