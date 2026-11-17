package com.example.examplemod.knowledge;

import com.google.gson.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

/** Bounded, content-addressed articles. No Minecraft/client classes or world access. */
public final class KnowledgeBundle {
    public static final int CHUNK = 8192, MAX_FILES = 2048, MAX_FILE = 8*1024*1024;
    public static final int MAX_TOTAL = 64*1024*1024, MAX_MANIFEST = 2*1024*1024;
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Pattern LINK = Pattern.compile("!?\\[[^]]*]\\(([^)]+)\\)");
    public record Entry(String path, String hash, int size) {}
    public record Catalog(UUID library, List<Entry> files) {
        public Catalog { files=List.copyOf(files); }
        public Map<String,Entry> blobs() {
            Map<String,Entry> result=new LinkedHashMap<>();
            files.forEach(e->result.putIfAbsent(e.hash(),e));return result;
        }
    }
    public record Snapshot(Catalog catalog, byte[] manifest, Map<String,Path> sources, List<KnowledgeFileMeta> articles) {}
    private KnowledgeBundle() {}

    public static String hash(byte[] bytes) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public static boolean validHash(String hash) {return hash!=null && hash.matches("[0-9a-f]{64}");}
    public static Path resolve(Path root,String path) throws IOException {
        if(path==null || path.isBlank() || path.length()>240 || path.contains("\\") || path.contains(":")
                || path.startsWith("/") || path.chars().anyMatch(c->c<32)) throw new IOException("Invalid path: "+path);
        for(String part:path.split("/",-1)) if(part.isBlank() || part.equals(".") || part.equals("..")
                || part.endsWith(".") || part.endsWith(" ") || part.matches("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?"))
            throw new IOException("Invalid path: "+path);
        Path base=root.toAbsolutePath().normalize(), result=base.resolve(path).normalize();
        if(!result.startsWith(base)) throw new IOException("Path outside library");
        Path current=base;
        if(Files.isSymbolicLink(current)) throw new IOException("Linked library root");
        for(Path part:base.relativize(result)) {current=current.resolve(part);if(Files.isSymbolicLink(current)) throw new IOException("Linked file: "+path);}
        return result;
    }
    public static byte[] read(Path file,int limit) throws IOException {
        try(var in=Files.newInputStream(file)) {byte[] data=in.readNBytes(limit+1);if(data.length>limit)throw new IOException("File too large: "+file);return data;}
    }
    public static byte[] encode(Catalog catalog) {return JSON.toJson(catalog).getBytes(StandardCharsets.UTF_8);}
    public static Catalog decode(byte[] bytes) throws IOException {
        if(bytes.length>MAX_MANIFEST)throw new IOException("Catalog too large");
        try {
            Catalog c=JSON.fromJson(new String(bytes,StandardCharsets.UTF_8),Catalog.class);
            if(c==null || c.library()==null || c.files().size()>MAX_FILES)throw new IOException("Invalid catalog");
            Set<String> names=new HashSet<>();Map<String,Integer> sizes=new HashMap<>();long total=0;
            for(Entry e:c.files()) {
                resolve(Path.of("."),e.path());
                if(!supported(e.path()) || !validHash(e.hash()) || e.size()<0 || e.size()>MAX_FILE
                        || !names.add(e.path().toLowerCase(Locale.ROOT)))throw new IOException("Invalid entry: "+e.path());
                Integer old=sizes.putIfAbsent(e.hash(),e.size());
                if(old!=null && old!=e.size())throw new IOException("Inconsistent blob size");
                total+=e.size();
            }
            if(total>MAX_TOTAL)throw new IOException("Library exceeds 64 MiB");
            return c;
        } catch(RuntimeException e){throw new IOException("Invalid catalog",e);}
    }
    private static boolean supported(String path) {String s=path.toLowerCase(Locale.ROOT);return s.endsWith(".md") || s.endsWith(".png");}
    public static void validateContent(String path,byte[] data) throws IOException {
        if(path.toLowerCase(Locale.ROOT).endsWith(".md")) {
            if(data.length>1024*1024)throw new IOException("Article exceeds 1 MiB: "+path);
            try {StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data));}
            catch(Exception e){throw new IOException("Article must be UTF-8: "+path,e);}
        } else {
            if(data.length<24 || ByteBuffer.wrap(data).getLong()!=0x89504e470d0a1a0aL
                    || ByteBuffer.wrap(data,12,4).getInt()!=0x49484452)throw new IOException("Invalid PNG: "+path);
            int w=ByteBuffer.wrap(data,16,4).getInt(),h=ByteBuffer.wrap(data,20,4).getInt();
            if(w<1 || h<1 || w>4096 || h>4096 || (long)w*h>16_777_216)throw new IOException("PNG exceeds 4096x4096: "+path);
        }
    }
    public static Snapshot build(Path root,UUID library) throws IOException {
        Files.createDirectories(root);
        List<Entry> entries=new ArrayList<>();Map<String,Path> sources=new HashMap<>();
        Map<String,String> articles=new LinkedHashMap<>();List<KnowledgeFileMeta> metas=new ArrayList<>();
        try(var walk=Files.walk(root)) {
            var files=walk.filter(p->!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))
                    .filter(p->supported(p.toString())).sorted().limit(MAX_FILES+1L).toList();
            if(files.size()>MAX_FILES)throw new IOException("Too many library files");
            long total=0;
            for(Path p:files) {
                String name=root.relativize(p).toString().replace('\\','/');Path safe=resolve(root,name);
                byte[] data=read(safe,MAX_FILE);total+=data.length;
                if(total>MAX_TOTAL)throw new IOException("Library exceeds 64 MiB");
                validateContent(name,data);String hash=hash(data);
                entries.add(new Entry(name,hash,data.length));sources.putIfAbsent(hash,safe);
                if(name.toLowerCase(Locale.ROOT).endsWith(".md")) {
                    String text=new String(data,StandardCharsets.UTF_8);articles.put(name,text);
                    metas.add(KnowledgeFileMeta.parse(name,text.lines().limit(32).toList()));
                }
            }
        }
        Set<Path> available=new HashSet<>();for(Entry e:entries)available.add(resolve(root,e.path()));
        for(var article:articles.entrySet()) {
            var matcher=LINK.matcher(article.getValue());
            while(matcher.find()) {
                String target=matcher.group(1).trim();
                if(target.startsWith("ponder:") || target.startsWith("https://") || target.startsWith("http://") || target.startsWith("#"))continue;
                if(!supported(target))continue;
                Path parent=Path.of(article.getKey()).getParent();
                String relative=(parent==null?Path.of(target):parent.resolve(target)).normalize().toString().replace('\\','/');
                if(!available.contains(resolve(root,relative)) && !available.contains(resolve(root,target)))
                    throw new IOException("Missing link in "+article.getKey()+": "+target);
            }
        }
        Catalog catalog=new Catalog(library,entries);byte[] manifest=encode(catalog);decode(manifest);
        return new Snapshot(catalog,manifest,Map.copyOf(sources),List.copyOf(metas));
    }

    /** Readers must keep serving the published bytes even while authors edit source files. */
    public static Snapshot freeze(Snapshot source,Path store) throws IOException {
        Map<String,Path> frozen=new HashMap<>();Files.createDirectories(store);
        for(var entry:source.catalog().blobs().values()) {
            Path dest=resolve(store,entry.hash());
            if(!Files.isRegularFile(dest) || Files.size(dest)!=entry.size() || !hash(read(dest,MAX_FILE)).equals(entry.hash())) {
                byte[] bytes=read(source.sources().get(entry.hash()),MAX_FILE);
                if(!hash(bytes).equals(entry.hash()))throw new IOException("Source changed during publication: "+entry.path());
                Path temp=resolve(store,entry.hash()+".tmp");Files.write(temp,bytes);move(temp,dest);
            }
            frozen.put(entry.hash(),dest);
        }
        return new Snapshot(source.catalog(),source.manifest(),Map.copyOf(frozen),source.articles());
    }
    public static void pruneSnapshot(Path store,Set<String> keep) throws IOException {
        if(!Files.isDirectory(store))return;
        try(var files=Files.list(store)){
            for(Path path:files.toList())if(!keep.contains(path.getFileName().toString())) {
                try{Files.deleteIfExists(path);}catch(java.nio.file.FileSystemException stillReading){/* Retry on next publication. */}
            }
        }
    }

    /** Wall-clock bucket, with a small bounded burst, independent of server TPS. */
    public static final class Limiter {
        private final double rate,capacity;private double tokens;private long last;
        public Limiter(int bytesPerSecond,long now){rate=Math.max(CHUNK+512,bytesPerSecond);capacity=rate/2+CHUNK;tokens=0;last=now;}
        public void refill(long now){tokens=Math.min(capacity,tokens+Math.max(0,now-last)/1e9*rate);last=now;}
        public boolean canSend(int bytes){return tokens>=bytes;}
        public void take(int bytes){tokens-=bytes;}
    }

    /** Only writes beneath a managed server-specific directory; user articles stay untouched. */
    public static final class Cache {
        private final Path root;
        public Cache(Path root){this.root=root.toAbsolutePath().normalize();}
        public Path root(){return root;}
        public Path blob(String hash) throws IOException {
            if(!validHash(hash))throw new IOException("Invalid blob hash");return resolve(root,"objects/"+hash);
        }
        public List<Entry> missing(Catalog c,Path local) throws IOException {
            // Interrupted revisions must not accumulate unbounded orphan blobs/views.
            Set<String> keep=new HashSet<>(c.blobs().keySet());
            Path activeFile=resolve(root,"active.json");
            String activeRevision=null;
            if(Files.isRegularFile(activeFile)) {
                try {
                    byte[] old=read(activeFile,MAX_MANIFEST);keep.addAll(decode(old).blobs().keySet());activeRevision=hash(old);
                } catch(IOException damaged) { Files.deleteIfExists(activeFile); }
            }
            Path views=resolve(root,"views");
            if(Files.isDirectory(views))try(var dirs=Files.list(views)){
                for(Path p:dirs.toList())if(!p.getFileName().toString().equals(activeRevision))deleteTree(p);
            }
            Path objects=resolve(root,"objects");
            if(Files.isDirectory(objects))try(var paths=Files.list(objects)){
                for(Path p:paths.toList())if(!keep.contains(p.getFileName().toString()))Files.deleteIfExists(p);
            }
            List<Entry> missing=new ArrayList<>();
            for(Entry e:c.blobs().values()) {
                Path blob=blob(e.hash());
                if(valid(blob,e))continue;
                // First installation can reuse a matching manually installed article/image.
                Path seed=resolve(local,e.path());
                if(valid(seed,e)){Files.createDirectories(blob.getParent());Files.copy(seed,blob,StandardCopyOption.REPLACE_EXISTING);}
                else missing.add(e);
            }
            return missing;
        }
        private boolean valid(Path p,Entry e) throws IOException {
            return Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS) && Files.size(p)==e.size() && hash(read(p,MAX_FILE)).equals(e.hash());
        }
        public void store(Entry e,byte[] data) throws IOException {
            if(data.length!=e.size() || !hash(data).equals(e.hash()))throw new IOException("Checksum mismatch: "+e.path());
            validateContent(e.path(),data);Path dest=blob(e.hash());Files.createDirectories(dest.getParent());
            Path temp=resolve(root,"objects/"+e.hash()+".tmp");Files.write(temp,data);move(temp,dest);
        }
        public Path activate(Catalog catalog) throws IOException {
            byte[] manifest=encode(catalog);String revision=hash(manifest);
            Path view=resolve(root,"views/"+revision);Files.createDirectories(view);
            for(Entry e:catalog.files()) {
                Path source=blob(e.hash());if(!valid(source,e))throw new IOException("Incomplete cache: "+e.path());
                validateContent(e.path(),read(source,MAX_FILE));
                Path dest=resolve(view,e.path());Files.createDirectories(dest.getParent());
                if(!valid(dest,e)) {
                    Files.deleteIfExists(dest);
                    try{Files.createLink(dest,source);}catch(IOException|UnsupportedOperationException ex){Files.copy(source,dest);}
                }
            }
            Path temp=resolve(root,"active.tmp");Files.write(temp,manifest);move(temp,resolve(root,"active.json"));
            // Keep the current view and current blobs only, including removed articles cleanup.
            try(var dirs=Files.list(resolve(root,"views"))) {
                for(Path p:dirs.toList())if(!p.equals(view))deleteTree(p);
            }
            Set<String> keep=catalog.blobs().keySet();
            Path objects=resolve(root,"objects");Files.createDirectories(objects);
            try(var files=Files.list(objects)){for(Path p:files.toList())if(!keep.contains(p.getFileName().toString()))Files.deleteIfExists(p);}
            return view;
        }
        public Path active() throws IOException {
            Path file=resolve(root,"active.json");if(!Files.isRegularFile(file))return null;
            byte[] bytes=read(file,MAX_MANIFEST);decode(bytes);Path view=resolve(root,"views/"+hash(bytes));
            return Files.isDirectory(view)?view:null;
        }
    }
    /** Evicts least-recently-used OTHER libraries, never the active connection or user files. */
    public static void trimCaches(Path base,Path current,long budget) throws IOException {
        if(!Files.isDirectory(base))return;
        List<Path> candidates;
        try(var dirs=Files.list(base)){candidates=new ArrayList<>(dirs.filter(p->validHash(p.getFileName().toString())
                && Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)).toList());}
        candidates.sort(Comparator.comparingLong(p->{try{return Files.getLastModifiedTime(p.resolve("active.json")).toMillis();}catch(IOException e){return 0;}}));
        long total=0;Map<Path,Long> sizes=new HashMap<>();
        for(Path p:candidates) {
            long size=0;try(var walk=Files.walk(p)){for(Path f:walk.filter(f->Files.isRegularFile(f,LinkOption.NOFOLLOW_LINKS)).toList())size+=Files.size(f);}
            sizes.put(p,size);total+=size;
        }
        for(Path p:candidates)if(total>budget && !p.toAbsolutePath().normalize().equals(current.toAbsolutePath().normalize())) {
            deleteTree(p);total-=sizes.get(p);
        }
    }
    private static void move(Path from,Path to) throws IOException {
        try{Files.move(from,to,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException e){Files.move(from,to,StandardCopyOption.REPLACE_EXISTING);}
    }
    private static void deleteTree(Path path) throws IOException {
        try(var walk=Files.walk(path)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
    }
}
