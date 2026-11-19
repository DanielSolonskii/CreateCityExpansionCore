package com.example.examplemod.places;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** The icon represents a place on the atlas. Only surveyed feet positions may serve as entrances. */
public record AtlasPlace(UUID id, UUID owner, long version, String name, Category category, String description,
                         ResourceLocation dimension, BlockPos icon, List<BlockPos> entrances, boolean published, Source source) {
    public enum Category { HOME, SHOP, FACTORY, WAREHOUSE, MINE, PUBLIC, OTHER }
    public enum SourceKind { CITY, PROJECT, WAREHOUSE }
    public record Source(SourceKind kind, UUID id) {
        public Source { Objects.requireNonNull(kind);Objects.requireNonNull(id); }
    }
    public static final int MAX_ENTRANCES=4, NAME=48, DESCRIPTION=180;
    public AtlasPlace {
        Objects.requireNonNull(id);Objects.requireNonNull(owner);Objects.requireNonNull(category);Objects.requireNonNull(dimension);
        name=name.strip();description=description.strip();
        if(version<0 || name.isEmpty() || !validText(name,NAME) || !validText(description,DESCRIPTION)
                || dimension.toString().length()>160 || entrances.size()>MAX_ENTRANCES) throw new IllegalArgumentException("Place fields");
        icon=check(icon);entrances=entrances.stream().map(AtlasPlace::check).toList();
        if(new HashSet<>(entrances).size()!=entrances.size())throw new IllegalArgumentException("Duplicate entrance");
    }
    private static boolean validText(String value,int limit) {
        return value.length()<=limit && value.codePoints().noneMatch(c->Character.isISOControl(c) || c==0xa7);
    }
    private static BlockPos check(BlockPos p) {
        if(Math.abs((long)p.getX())>29_999_984 || Math.abs((long)p.getZ())>29_999_984 || p.getY() < -2048 || p.getY()>2047)
            throw new IllegalArgumentException("Place position");
        return p.immutable();
    }
    public AtlasPlace at(BlockPos pos) {
        return icon.equals(pos)?this:new AtlasPlace(id,owner,version,name,category,description,dimension,pos,entrances,published,source);
    }
    AtlasPlace next(UUID actor,long nextVersion) {
        return new AtlasPlace(id,actor,nextVersion,name,category,description,dimension,icon,entrances,published,source);
    }
    CompoundTag save() {
        var tag=new CompoundTag();
        tag.putUUID("Id",id);tag.putUUID("Owner",owner);tag.putLong("Version",version);
        tag.putString("Name",name);tag.putString("Category",category.name());tag.putString("Description",description);
        tag.putString("Dimension",dimension.toString());tag.putLong("Icon",icon.asLong());
        tag.putLongArray("Entrances",entrances.stream().mapToLong(BlockPos::asLong).toArray());tag.putBoolean("Published",published);
        if(source!=null){tag.putString("Source",source.kind.name());tag.putUUID("SourceId",source.id);}
        return tag;
    }
    static AtlasPlace load(CompoundTag t) {
        long[] raw=t.getLongArray("Entrances");
        if(raw.length>MAX_ENTRANCES || !t.contains("Icon",Tag.TAG_LONG) || t.getLong("Version")<1)throw new IllegalArgumentException("Place record");
        return new AtlasPlace(t.getUUID("Id"),t.getUUID("Owner"),t.getLong("Version"),t.getString("Name"),
                Category.valueOf(t.getString("Category")),t.getString("Description"),ResourceLocation.parse(t.getString("Dimension")),
                BlockPos.of(t.getLong("Icon")),Arrays.stream(raw).mapToObj(BlockPos::of).toList(),t.getBoolean("Published"),
                t.contains("Source")?new Source(SourceKind.valueOf(t.getString("Source")),t.getUUID("SourceId")):null);
    }
    static void write(RegistryFriendlyByteBuf b,AtlasPlace p) {
        b.writeUUID(p.id);b.writeUUID(p.owner);b.writeVarLong(p.version);b.writeUtf(p.name,NAME);b.writeEnum(p.category);
        b.writeUtf(p.description,DESCRIPTION);b.writeUtf(p.dimension.toString(),160);b.writeBlockPos(p.icon);
        b.writeVarInt(p.entrances.size());p.entrances.forEach(b::writeBlockPos);b.writeBoolean(p.published);
        b.writeBoolean(p.source!=null);if(p.source!=null){b.writeEnum(p.source.kind);b.writeUUID(p.source.id);}
    }
    static AtlasPlace read(RegistryFriendlyByteBuf b) {
        UUID id=b.readUUID(),owner=b.readUUID();long v=b.readVarLong();String name=b.readUtf(NAME);
        var cat=b.readEnum(Category.class);String desc=b.readUtf(DESCRIPTION);var dim=ResourceLocation.parse(b.readUtf(160));
        var icon=b.readBlockPos();int count=b.readVarInt();if(count<0||count>MAX_ENTRANCES)throw new IllegalArgumentException("Entrance count");
        List<BlockPos> points=new ArrayList<>(count);for(int i=0;i<count;i++)points.add(b.readBlockPos());
        return new AtlasPlace(id,owner,v,name,cat,desc,dim,icon,points,b.readBoolean(),
                b.readBoolean()?new Source(b.readEnum(SourceKind.class),b.readUUID()):null);
    }
}
