package com.example.examplemod.client.map;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** Region-based, asynchronous derived cache. No disk access from the render thread. */
public final class TerrainTileStore {
    public static final int MAX_TILES=8192;
    public static final long MAX_DISK_BYTES=256L*1024*1024;
    private static final int MAGIC=0x43434133;
    private static final ScheduledThreadPoolExecutor IO=new ScheduledThreadPoolExecutor(1,r->{Thread t=new Thread(r,"CCEC atlas cache");t.setDaemon(true);return t;});
    static {IO.setRemoveOnCancelPolicy(true);}
    public record Key(int x,int z,int level){public Key(int x,int z){this(x,z,0);}public Key {if(level<0||level>7)throw new IllegalArgumentException("terrain level");}}
    private record Region(int x,int z,int level){}
    public record Tile(int[] colors,BitSet contours,boolean complete,long receivedAt,short[] heights,long revision,UUID worldId) {
        public Tile(int[] colors,BitSet contours,boolean complete,long at){this(colors,contours,complete,at,new short[0],0,new UUID(0,0));}
        public Tile {if(colors.length!=1024||contours.length()>1024||heights.length!=0&&heights.length!=1156)throw new IllegalArgumentException("tile size");colors=colors.clone();contours=(BitSet)contours.clone();heights=heights.clone();}
    }
    private final Path directory;
    private final String prefix;
    private final LinkedHashMap<Key,Tile> tiles=new LinkedHashMap<>(64,.75f,true);
    private final Set<Region> loadedRegions=new HashSet<>(),loadingRegions=new HashSet<>(),dirty=new HashSet<>();
    private final Map<Key,Tile> changed=new LinkedHashMap<>();
    private ScheduledFuture<?> pending;
    public TerrainTileStore(Path directory,String scope){
        this.directory=directory;
        try {prefix=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((scope+":terrain-v3").getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(Exception ex){throw new IllegalStateException(ex);}
    }
    public boolean loaded(){return true;}
    private static Region region(Key key){return new Region(Math.floorDiv(key.x,8),Math.floorDiv(key.z,8),key.level);}
    private Path file(Region r){return directory.resolve(prefix+"_"+r.level+"_"+r.x+"_"+r.z+".atlas");}
    public Tile get(int x,int z){return get(x,z,0);}
    public synchronized Tile get(int x,int z,int level){
        Key key=new Key(x,z,level);Region region=region(key);
        if(!loadedRegions.contains(region)&&loadingRegions.add(region))IO.execute(()->load(region));
        return tiles.get(key);
    }
    public synchronized boolean ready(int x,int z,int level){return loadedRegions.contains(region(new Key(x,z,level)));}
    public synchronized int size(){return tiles.size();}
    public void put(int x,int z,Tile tile){put(x,z,0,tile);}
    public synchronized void put(int x,int z,int level,Tile tile){
        Key key=new Key(x,z,level);Region r=region(key);
        if(!loadedRegions.contains(r)&&loadingRegions.add(r))IO.execute(()->load(r));
        tiles.put(key,tile);changed.put(key,tile);trim();dirty.add(r);
        if(changed.size()>=256){if(pending!=null)pending.cancel(false);pending=IO.schedule(this::write,0,TimeUnit.SECONDS);}
        else if(pending==null||pending.isDone())pending=IO.schedule(this::write,10,TimeUnit.SECONDS);
    }
    private void trim(){while(tiles.size()>MAX_TILES){Key oldest=tiles.keySet().iterator().next();tiles.remove(oldest);loadedRegions.remove(region(oldest));}}
    public synchronized Future<?> flush(){if(pending!=null)pending.cancel(false);pending=IO.schedule(this::write,0,TimeUnit.SECONDS);return pending;}
    public static void awaitIo()throws Exception{IO.submit(()->{}).get(20,TimeUnit.SECONDS);}
    private Map<Key,Tile> read(Region region){
        Map<Key,Tile> saved=new LinkedHashMap<>();Path file=file(region);
        if(Files.isRegularFile(file))try(var in=new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))){
            if(in.readInt()!=MAGIC)throw new IOException("cache version");int count=in.readInt();if(count<0||count>64)throw new IOException("region size");
            for(int i=0;i<count;i++){
                Key key=new Key(in.readInt(),in.readInt(),region.level);if(!region(key).equals(region))throw new IOException("region coordinates");
                long at=in.readLong(),revision=in.readLong();UUID world=new UUID(in.readLong(),in.readLong());boolean complete=in.readBoolean();
                int[] colors=new int[1024];for(int j=0;j<1024;j++)colors[j]=in.readInt();
                long[] bits=new long[16];for(int j=0;j<16;j++)bits[j]=in.readLong();
                int length=in.readInt();if(length!=0&&length!=1156)throw new IOException("heights");short[] heights=new short[length];for(int j=0;j<length;j++)heights[j]=in.readShort();
                saved.put(key,new Tile(colors,BitSet.valueOf(bits),complete,at,heights,revision,world));
            }
            if(in.read()!=-1)throw new IOException("trailing cache data");
        }catch(IOException|RuntimeException corrupt){saved.clear();}
        return saved;
    }
    private void load(Region region){Map<Key,Tile> saved=read(region);synchronized(this){saved.forEach(tiles::putIfAbsent);loadingRegions.remove(region);loadedRegions.add(region);trim();}}
    private void write(){
        Map<Region,Map<Key,Tile>> groups=new HashMap<>();
        synchronized(this){pending=null;for(Region r:dirty){Map<Key,Tile> copy=new LinkedHashMap<>();for(var e:changed.entrySet())if(region(e.getKey()).equals(r))copy.put(e.getKey(),e.getValue());groups.put(r,copy);}dirty.clear();changed.clear();}
        for(var group:groups.entrySet()){
            Path file=file(group.getKey()),temporary=file.resolveSibling(file.getFileName()+".tmp");
            Map<Key,Tile> merged=read(group.getKey());merged.putAll(group.getValue());
            try {
                Files.createDirectories(directory);
                try(var out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(temporary)))){
                    out.writeInt(MAGIC);out.writeInt(merged.size());
                    for(var entry:merged.entrySet()){
                        Tile t=entry.getValue();out.writeInt(entry.getKey().x);out.writeInt(entry.getKey().z);out.writeLong(t.receivedAt);out.writeLong(t.revision);out.writeLong(t.worldId.getMostSignificantBits());out.writeLong(t.worldId.getLeastSignificantBits());out.writeBoolean(t.complete);
                        for(int c:t.colors)out.writeInt(c);long[] bits=t.contours.toLongArray();for(int i=0;i<16;i++)out.writeLong(i<bits.length?bits[i]:0);
                        out.writeInt(t.heights.length);for(short h:t.heights)out.writeShort(h);
                    }
                }
                try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
            }catch(IOException unavailable){synchronized(this){dirty.add(group.getKey());group.getValue().forEach(changed::putIfAbsent);}try{Files.deleteIfExists(temporary);}catch(IOException ignored){}}
        }
        try{prune(directory,MAX_DISK_BYTES);}catch(IOException ignored){}
    }
    static void prune(Path directory,long limit)throws IOException {
        if(!Files.isDirectory(directory))return;List<Path> files;
        try(var stream=Files.list(directory)){files=new ArrayList<>(stream.filter(p->p.getFileName().toString().matches("[0-9a-f]{64}(_[0-7]_-?[0-9]+_-?[0-9]+)?\\.atlas")&&Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)).toList());}
        files.sort(Comparator.comparingLong(p->{try{return Files.getLastModifiedTime(p).toMillis();}catch(IOException ex){return 0;}}));long bytes=0;for(Path p:files)bytes+=Files.size(p);
        for(Path p:files){if(bytes<=limit)break;long n=Files.size(p);Files.deleteIfExists(p);bytes-=n;}
    }
}
