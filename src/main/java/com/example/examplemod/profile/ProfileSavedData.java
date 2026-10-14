package com.example.examplemod.profile;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Additive, independent settings save. Vanilla stats and project history remain in their own saves. */
public final class ProfileSavedData extends SavedData {
    public record Skin(String name, String texture, String signature) {
        public static Skin empty() { return new Skin("", "", ""); }
    }
    private final Map<UUID, PlayerProfile.Settings> settings = new HashMap<>();
    private final Map<UUID, Skin> skins = new HashMap<>();
    public static ProfileSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(ProfileSavedData::new, ProfileSavedData::load, null), "ccec_player_profiles");
    }
    public PlayerProfile.Settings settings(UUID id) { return settings.getOrDefault(id, PlayerProfile.Settings.defaults()); }
    public Skin skin(UUID id) { return skins.getOrDefault(id, Skin.empty()); }
    public boolean contains(UUID id) { return settings.containsKey(id) || skins.containsKey(id); }
    public String update(UUID actor, UUID target, PlayerProfile.Settings proposed) {
        if (!PlayerProfile.mayEdit(actor, target)) return "permission";
        var current = settings(target);
        String about = proposed.about().strip();
        if (current.about().equals(about) && current.showcase().equals(proposed.showcase())) return "saved";
        if (current.revision() != proposed.revision()) return "stale";
        settings.put(target, new PlayerProfile.Settings(about, proposed.showcase(), current.revision() + 1));
        setDirty(); return "saved";
    }
    public void rememberSkin(GameProfile profile) {
        var property = profile.getProperties().get("textures").stream().findFirst().orElse(null);
        if (property == null || property.value().length() > 16384 || property.signature() != null && property.signature().length() > 2048) return;
        Skin next = new Skin(profile.getName(), property.value(), Objects.requireNonNullElse(property.signature(), ""));
        if (!next.equals(skins.put(profile.getId(), next))) setDirty();
    }
    public static ProfileSavedData load(CompoundTag root, HolderLookup.Provider lookup) {
        ProfileSavedData data = new ProfileSavedData();
        ListTag list = root.getList("Players", Tag.TAG_COMPOUND);
        for (Tag entry : list) {
            CompoundTag tag = (CompoundTag) entry;
            if (!tag.hasUUID("Id")) continue;
            UUID id = tag.getUUID("Id");
            try {
                List<PlayerProfile.Metric> metrics = new ArrayList<>();
                for (Tag value : tag.getList("Showcase", Tag.TAG_STRING)) metrics.add(PlayerProfile.Metric.valueOf(value.getAsString()));
                data.settings.put(id, new PlayerProfile.Settings(tag.getString("About"), metrics, tag.getLong("Revision")));
            } catch (IllegalArgumentException ignored) { /* One corrupt settings entry must not discard other players. */ }
            String texture = tag.getString("Texture"), signature = tag.getString("Signature"), name = tag.getString("SkinName");
            if (!texture.isEmpty() && texture.length() <= 16384 && signature.length() <= 2048 && name.length() <= 64)
                data.skins.put(id, new Skin(name, texture, signature));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider lookup) {
        root.putInt("Version", 1);
        ListTag list = new ListTag(); Set<UUID> ids = new HashSet<>(settings.keySet()); ids.addAll(skins.keySet());
        for (UUID id : ids) {
            CompoundTag tag = new CompoundTag(); tag.putUUID("Id", id);
            var value = settings(id); tag.putString("About", value.about()); tag.putLong("Revision", value.revision());
            ListTag metrics = new ListTag(); value.showcase().forEach(m -> metrics.add(StringTag.valueOf(m.name()))); tag.put("Showcase", metrics);
            var skin = skin(id); tag.putString("SkinName", skin.name()); tag.putString("Texture", skin.texture()); tag.putString("Signature", skin.signature());
            list.add(tag);
        }
        root.put("Players", list); return root;
    }
}
