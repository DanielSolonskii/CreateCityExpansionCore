package com.example.examplemod.client.screen;

import com.example.examplemod.network.ProjectPlanRequest;
import com.example.examplemod.projects.EngineeringPlan;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import java.nio.file.Files;
import java.util.*;

final class SharedPlanCheck {
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    static void run() throws Exception {
        var dim=ResourceLocation.parse("minecraft:overworld");
        var project=UUID.randomUUID();
        var doc=new EngineeringPlan(UUID.randomUUID(),dim,EngineeringPlan.Kind.LINE,"Original","Заметка",
                List.of(new EngineeringPlan.Node(1,-2),new EngineeringPlan.Node(7,3)));
        var change=new ProjectPlanRequest(UUID.randomUUID(),project,doc.id(),12,doc);
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            ProjectPlanRequest.CODEC.encode(buffer,change);
            check(change.equals(ProjectPlanRequest.CODEC.decode(buffer)),"version and geometry on wire");
            buffer.clear();buffer.writeUUID(change.request());buffer.writeUUID(project);buffer.writeUUID(doc.id());
            buffer.writeVarLong(12);buffer.writeBoolean(true);buffer.writeUtf(dim.toString());buffer.writeEnum(doc.kind());
            buffer.writeUtf(doc.name());buffer.writeUtf(doc.note());buffer.writeVarInt(EngineeringPlan.MAX_NODES+1);
            try{ProjectPlanRequest.CODEC.decode(buffer);throw new AssertionError("oversized document accepted");}
            catch(IllegalArgumentException expected){check(!buffer.isReadable(),"oversized node count rejected before node allocation");}
        } finally {buffer.release();}
        var directory=Files.createTempDirectory("ccec-project-draft-");
        try {
            var drafts=new ProjectPlanDrafts(directory,dim);
            var revised=doc.describe("Concurrent local document","my edits");
            drafts.save(project,12,revised);
            var read=new ProjectPlanDrafts(directory,dim).read(project,doc.id());
            check(read.base()==12 && read.plan().equals(revised),"local edits and expected version survive reopen");
            check(drafts.read(UUID.randomUUID(),doc.id())==null,"separate project with same document UUID");
            drafts.remember(project,12,doc,doc);
            check(drafts.read(project,doc.id())==null,"undo to the original document removes the old recovery copy");
        } finally {
            try(var files=Files.list(directory)){for(var item:files.toList())Files.deleteIfExists(item);}
            Files.deleteIfExists(directory);
        }
        System.out.println("PASS: bounded project-plan wire format and conflict draft recovery");
    }
}
