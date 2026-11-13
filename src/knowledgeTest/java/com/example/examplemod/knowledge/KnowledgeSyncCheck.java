package com.example.examplemod.knowledge;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;

public final class KnowledgeSyncCheck {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    @FunctionalInterface interface Action {void run() throws Exception;}
    private static void rejects(Action action,String message) throws Exception {
        try{action.run();}catch(IOException expected){checks++;return;}throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("ccec-knowledge-check");
        try {
            Path source=root.resolve("source"),local=root.resolve("local"),managed=root.resolve("cache");
            Files.createDirectories(source.resolve("folder"));Files.createDirectories(local);
            byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aXfoAAAAASUVORK5CYII=");
            Files.write(source.resolve("image.png"),png);
            Files.write(source.resolve("same.png"),png);
            Files.writeString(source.resolve("folder/intro.md"),"---\ntitle: Введение\ndefault: visible\n---\n# Статья\n![Схема](../image.png)\n[Следующая](../next.md)");
            Files.writeString(source.resolve("next.md"),"# Next");
            UUID library=UUID.randomUUID();var first=KnowledgeBundle.build(source,library);
            check(first.catalog().files().size()==4,"Articles and images discovered");
            check(first.catalog().blobs().size()==3,"Identical image transferred once");
            check(first.articles().getFirst().visibleByDefault(),"Server metadata retained");
            check(KnowledgeBundle.decode(first.manifest()).equals(first.catalog()),"Manifest roundtrip");
            check(Arrays.equals(first.manifest(),KnowledgeBundle.build(source,library).manifest()),"Stable manifest");
            var frozen=KnowledgeBundle.freeze(first,root.resolve("published"));
            var cache=new KnowledgeBundle.Cache(managed);
            Files.copy(source.resolve("next.md"),local.resolve("next.md"));
            var missing=cache.missing(first.catalog(),local);
            check(missing.size()==2,"Matching local article seeds cache");
            for(var e:missing)cache.store(e,Files.readAllBytes(first.sources().get(e.hash())));
            Path installed=cache.activate(first.catalog());
            check(Files.readString(installed.resolve("folder/intro.md")).contains("Статья"),"Unicode article installed");
            check(Arrays.equals(Files.readAllBytes(installed.resolve("image.png")),png),"Image bytes retained");
            check(cache.missing(first.catalog(),local).isEmpty(),"Second login needs zero files");
            check(new KnowledgeBundle.Cache(managed).active().equals(installed),"Cache survives restart");
            check(Files.readString(local.resolve("next.md")).equals("# Next"),"User files untouched");

            Files.writeString(source.resolve("next.md"),"# Updated");
            var oldArticle=first.catalog().files().stream().filter(e->e.path().equals("next.md")).findFirst().orElseThrow();
            check(Files.readString(frozen.sources().get(oldArticle.hash())).equals("# Next"),"Published bytes survive source edits and failed reloads");
            var updated=KnowledgeBundle.build(source,library);missing=cache.missing(updated.catalog(),local);
            check(missing.size()==1 && missing.getFirst().path().equals("next.md"),"Only changed article requested");
            rejects(()->cache.activate(updated.catalog()),"Incomplete revision must not activate");
            check(cache.active().equals(installed),"Interrupted update keeps old revision");
            var changed=missing.getFirst();
            rejects(()->cache.store(changed,"tampered".getBytes(StandardCharsets.UTF_8)),"Checksum verified");
            cache.store(changed,Files.readAllBytes(updated.sources().get(changed.hash())));
            Path next=cache.activate(updated.catalog());
            check(!next.equals(installed) && Files.readString(next.resolve("next.md")).equals("# Updated"),"Revision switches as a whole");
            check(!Files.exists(installed),"Previous view removed after activation");
            check(cache.missing(updated.catalog(),local).isEmpty(),"Completed transfers reused after reconnect");

            var entry=updated.catalog().files().stream().filter(e->e.path().equals("next.md")).findFirst().orElseThrow();
            Files.write(cache.blob(entry.hash()),new byte[entry.size()]);
            check(cache.missing(updated.catalog(),local).size()==1,"Same-size corruption detected by hash");
            cache.store(entry,Files.readAllBytes(updated.sources().get(entry.hash())));cache.activate(updated.catalog());
            check(Files.readString(cache.active().resolve("next.md")).equals("# Updated"),"Damaged view repaired");
            Files.delete(source.resolve("same.png"));
            var removed=KnowledgeBundle.build(source,library);cache.activate(removed.catalog());
            check(!Files.exists(cache.active().resolve("same.png")),"Deleted files disappear");
            Files.writeString(managed.resolve("active.json"),"broken");
            check(cache.missing(removed.catalog(),local).isEmpty(),"Damaged manifest rebuilt from verified blobs");
            cache.activate(removed.catalog());
            Files.writeString(source.resolve("broken.md"),"![missing](missing.png)");
            rejects(()->KnowledgeBundle.build(source,library),"Broken links reject publication");Files.delete(source.resolve("broken.md"));
            for(String bad:List.of("../outside.md","/absolute.md","C:/outside.md","a\\b.md","a/../../b.md","con.md"))
                rejects(()->KnowledgeBundle.resolve(managed,bad),"Reject unsafe path "+bad);
            var evil=new KnowledgeBundle.Catalog(library,List.of(new KnowledgeBundle.Entry("../escape.md","a".repeat(64),1)));
            rejects(()->KnowledgeBundle.decode(KnowledgeBundle.encode(evil)),"Untrusted manifest validated");
            var duplicate=new KnowledgeBundle.Catalog(library,List.of(entry,new KnowledgeBundle.Entry("NEXT.md",entry.hash(),entry.size())));
            rejects(()->KnowledgeBundle.decode(KnowledgeBundle.encode(duplicate)),"Case-insensitive duplicate rejected");
            byte[] bomb=png.clone();java.nio.ByteBuffer.wrap(bomb,16,4).putInt(100000);
            rejects(()->KnowledgeBundle.validateContent("bomb.png",bomb),"PNG dimensions bounded before texture decode");
            rejects(()->KnowledgeBundle.validateContent("bad.md",new byte[]{(byte)0xff}),"UTF-8 validated");

            var bucket=new KnowledgeBundle.Limiter(64*1024,0);
            check(!bucket.canSend(8192),"No initial unbounded burst");bucket.refill(250_000_000L);
            check(bucket.canSend(16384),"Configured rate refills");bucket.take(16384);
            check(!bucket.canSend(1),"Used budget deducted");bucket.refill(100_000_000_000L);
            check(!bucket.canSend(64*1024),"Long pause cannot accumulate huge burst");
            var global=new KnowledgeBundle.Limiter(64*1024,0);global.refill(1_000_000_000L);
            int sent=0;for(int i=0;i<100;i++)if(global.canSend(8448)){global.take(8448);sent+=8448;}
            check(sent<=40960,"Shared budget bounds concurrent players");
            System.out.println("PASS: "+checks+" knowledge checks: incremental transfer, images, restart, interruption, corruption, paths, limits");
        } finally {
            try(var walk=Files.walk(root)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
        }
    }
}
