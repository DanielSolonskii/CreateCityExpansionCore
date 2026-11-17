package com.example.examplemod.knowledge;

import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.*;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import java.util.*;

@GameTestHolder("createcityexpansioncore")
@PrefixGameTestTemplate(false)
public final class KnowledgeServerGameTest {
    @GameTest(template="auger_empty",timeoutTicks=300)
    public static void reloadIsAtomicAndAdminOnly(GameTestHelper h) {
        var server=h.getLevel().getServer();
        Path root=KnowledgeStorage.folder().resolve("sync-test-"+UUID.randomUUID());
        KnowledgeBundle.Snapshot[] published=new KnowledgeBundle.Snapshot[1];
        h.startSequence()
            .thenWaitUntil(()->h.assertTrue(field("building")==null,"Initial snapshot finished"))
            .thenExecute(()->{
                try {
                    Files.createDirectories(root);Files.writeString(root.resolve("test.md"),"---\ndefault: visible\n---\n# First revision");
                    var dispatcher=server.getCommands().getDispatcher();
                    try {dispatcher.execute("ccec knowledge reload",server.createCommandSourceStack().withPermission(0));
                        h.fail("Non-admin must not reload the catalog");}
                    catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){}
                    h.assertTrue(dispatcher.execute("ccec knowledge reload",server.createCommandSourceStack())==1,"Admin can reload");
                } catch(Exception e){throw new RuntimeException(e);}
            })
            .thenWaitUntil(()->{
                h.assertTrue(field("building")==null,"Background build completed");
                published[0]=(KnowledgeBundle.Snapshot)field("snapshot");
                h.assertTrue(published[0]!=null,"Published snapshot available: "+field("failure"));
                h.assertTrue(published[0].articles().stream().anyMatch(a->a.path().contains(root.getFileName().toString())),"New article published");
            })
            .thenExecute(()->{
                try {
                    Files.writeString(root.resolve("test.md"),"![broken](missing.png)");
                    server.getCommands().getDispatcher().execute("ccec knowledge reload",server.createCommandSourceStack());
                } catch(Exception e){throw new RuntimeException(e);}
            })
            .thenWaitUntil(()->h.assertTrue(field("building")==null,"Invalid reload completed"))
            .thenExecute(()->{
                h.assertTrue(field("snapshot")==published[0],"Invalid reload retains the complete previous snapshot");
                try {Files.delete(root.resolve("test.md"));Files.delete(root);}catch(Exception e){throw new RuntimeException(e);}
            }).thenSucceed();
    }
    private static Object field(String name) {
        try{var f=KnowledgeSyncServer.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
        catch(Exception e){throw new RuntimeException(e);}
    }
    @GameTest(template="auger_empty")
    public static void boundedPayloadRoundtrip(GameTestHelper h) {
        byte[] data=new byte[KnowledgeBundle.CHUNK];new Random(42).nextBytes(data);
        var original=new KnowledgeSyncNetwork.Data(UUID.randomUUID(),KnowledgeSyncNetwork.FILE,"a".repeat(64),8192,16384,data);
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            KnowledgeSyncNetwork.Data.CODEC.encode(buffer,original);
            h.assertTrue(buffer.readableBytes()<32*1024,"Transfer chunks remain small");
            var decoded=KnowledgeSyncNetwork.Data.CODEC.decode(buffer);
            h.assertTrue(decoded.session().equals(original.session()) && decoded.offset()==8192
                    && Arrays.equals(decoded.bytes(),data),"Binary chunk codec roundtrip");
            buffer.clear();buffer.writeUUID(UUID.randomUUID());buffer.writeByte(KnowledgeSyncNetwork.FILE);
            buffer.writeUtf("a");buffer.writeVarInt(0);buffer.writeVarInt(9000);buffer.writeByteArray(new byte[9000]);
            boolean rejected=false;try{KnowledgeSyncNetwork.Data.CODEC.decode(buffer);}catch(RuntimeException e){rejected=true;}
            h.assertTrue(rejected,"Oversized chunk rejected at decode");
        } finally {buffer.release();}
        h.succeed();
    }
}
