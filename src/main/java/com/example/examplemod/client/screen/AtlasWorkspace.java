package com.example.examplemod.client.screen;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Locally saved views and opaque result keys. Object labels and private snapshots stay in memory. */
public final class AtlasWorkspace {
    public static final int MAX_VIEWS = 24, MAX_FAVORITES = 64, MAX_RECENT = 16;
    public record Area(String dimension, UUID world) {
        public Area { if (dimension == null || dimension.length() > 160 || world == null) throw new IllegalArgumentException(); }
    }
    public record Camera(double x, double z, double zoom, int layers, int type, int owner, boolean problems, String query,
                         int territoryMode, boolean plots, boolean grid, boolean overview) {
        public Camera {
            if (!Double.isFinite(x) || !Double.isFinite(z) || !Double.isFinite(zoom) || zoom <= 0
                    || Math.abs(x) > 30_000_001 || Math.abs(z) > 30_000_001 || query == null || query.length() > 64
                    || territoryMode<0 || territoryMode>2 || owner<0 || owner>2 || type<0)
                throw new IllegalArgumentException("Invalid atlas view");
        }
    }
    public record View(UUID id, String name, Camera camera) {}
    private final ArrayList<View> views = new ArrayList<>();
    private final LinkedHashSet<String> favorites = new LinkedHashSet<>();
    private final ArrayList<String> recent = new ArrayList<>();
    private final LinkedHashSet<Area> neighbors = new LinkedHashSet<>();
    private final Path directory;
    public final Area area;

    public AtlasWorkspace(Path directory, Area area) { this.directory = directory; this.area = area; }
    public List<View> views() { return List.copyOf(views); }
    public Set<Area> linkedAreas() { return Set.copyOf(neighbors); }
    public boolean link(Area other) { return !area.equals(other) && neighbors.size() < 32 && neighbors.add(other); }
    public boolean favorite(String key) { return favorites.contains(key); }
    List<String> resultKeys(boolean starred) { return starred ? List.copyOf(favorites) : List.copyOf(recent); }
    AtlasWorkspace reload() { return load(directory,area); }
    PlanDraftStore engineeringDrafts() { return new PlanDraftStore(directory,area); }
    public int recency(String key) { return recent.indexOf(key); }
    public boolean toggle(String key) {
        if (favorites.remove(key)) return true;
        return validKey(key) && favorites.size() < MAX_FAVORITES && favorites.add(key);
    }
    public boolean visit(String key) {
        if (!validKey(key) || !recent.isEmpty() && recent.getFirst().equals(key)) return false;
        recent.remove(key);
        recent.addFirst(key);
        if (recent.size() > MAX_RECENT) recent.removeLast();
        return true;
    }
    private static boolean validKey(String key) { return key != null && !key.isBlank() && key.length() <= 320; }
    public UUID create(String name, Camera camera) {
        name = name.strip();
        if (name.isEmpty() || name.length() > 48 || views.size() >= MAX_VIEWS) return null;
        UUID id = UUID.randomUUID();
        views.add(new View(id,name,camera));
        return id;
    }
    public boolean edit(UUID id, String name, Camera camera) {
        for (int i=0;i<views.size();i++) {
            View old=views.get(i);
            if (!old.id.equals(id)) continue;
            String label=name==null?old.name:name.strip();
            if (label.isEmpty() || label.length()>48) return false;
            views.set(i,new View(id,label,camera==null?old.camera:camera));
            return true;
        }
        return false;
    }
    public boolean delete(UUID id) { return views.removeIf(v -> v.id.equals(id)); }
    public static String digest(String input) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    private Path file() { return directory.resolve(digest(area.dimension + "/" + area.world) + ".bin"); }
    public void save() throws IOException {
        Files.createDirectories(directory);
        Path temp=Files.createTempFile(directory,"atlas-", ".tmp");
        try {
            try (var out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
                out.writeInt(0x43415732); out.writeUTF(area.dimension); out.writeUTF(area.world.toString());
                out.writeInt(views.size());
                for (View v:views) {
                    out.writeUTF(v.id.toString()); out.writeUTF(v.name);
                    var c=v.camera; out.writeDouble(c.x);out.writeDouble(c.z);out.writeDouble(c.zoom);
                    out.writeInt(c.layers);out.writeInt(c.type);out.writeInt(c.owner);out.writeBoolean(c.problems);out.writeUTF(c.query);
                    out.writeInt(c.territoryMode);out.writeBoolean(c.plots);out.writeBoolean(c.grid);out.writeBoolean(c.overview);
                }
                writeKeys(out,favorites); writeKeys(out,recent); out.writeInt(neighbors.size());
                for (Area neighbor:neighbors) {out.writeUTF(neighbor.dimension);out.writeUTF(neighbor.world.toString());}
            }
            try {Files.move(temp,file(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch (AtomicMoveNotSupportedException e) {Files.move(temp,file(),StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temp);}
    }
    private static void writeKeys(DataOutputStream out, Collection<String> keys) throws IOException {
        out.writeInt(keys.size()); for(String key:keys) out.writeUTF(key);
    }
    private static int count(DataInputStream in,int max) throws IOException {
        int count=in.readInt(); if(count<0||count>max) throw new IOException("Invalid atlas count"); return count;
    }
    private static void readKeys(DataInputStream in,int max,Collection<String> keys) throws IOException {
        for(int i=count(in,max);i>0;i--) {
            String key=in.readUTF(); if(!validKey(key)) throw new IOException("Invalid key"); keys.add(key);
        }
    }
    public static AtlasWorkspace load(Path directory, Area area) {
        var result=new AtlasWorkspace(directory,area);
        if (!Files.isRegularFile(result.file())) return result;
        try (var in=new DataInputStream(new BufferedInputStream(Files.newInputStream(result.file())))) {
            if (Files.size(result.file())>131072 || in.readInt()!=0x43415732 || !in.readUTF().equals(area.dimension)
                    || !in.readUTF().equals(area.world.toString())) return result;
            for(int i=count(in,MAX_VIEWS);i>0;i--) {
                UUID id=UUID.fromString(in.readUTF());String name=in.readUTF();
                if(name.isBlank()||name.length()>48) throw new IOException("Invalid name");
                result.views.add(new View(id,name,new Camera(in.readDouble(),in.readDouble(),in.readDouble(),
                        in.readInt(),in.readInt(),in.readInt(),in.readBoolean(),in.readUTF(),
                        in.readInt(),in.readBoolean(),in.readBoolean(),in.readBoolean())));
            }
            readKeys(in,MAX_FAVORITES,result.favorites); readKeys(in,MAX_RECENT,result.recent);
            for(int i=count(in,32);i>0;i--) result.link(new Area(in.readUTF(),UUID.fromString(in.readUTF())));
        } catch(IOException|IllegalArgumentException ignored) {return new AtlasWorkspace(directory,area);}
        return result;
    }
}
