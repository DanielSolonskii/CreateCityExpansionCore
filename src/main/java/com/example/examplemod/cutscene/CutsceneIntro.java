package com.example.examplemod.cutscene;

import com.example.examplemod.CreateCityExpansionCore;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

@EventBusSubscriber(modid = CreateCityExpansionCore.MODID)
public final class CutsceneIntro extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String INTRO_SENT = "ccecCutsceneIntroSent";
    private static final Factory<CutsceneIntro> FACTORY = new Factory<>(CutsceneIntro::new, CutsceneIntro::load);
    private String id = "";

    private CutsceneIntro() {
    }

    static CutsceneIntro get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "ccec_cutscene_intro");
    }

    String id() {
        return id;
    }

    void set(String id) {
        this.id = id;
        setDirty();
    }

    private static CutsceneIntro load(CompoundTag tag, HolderLookup.Provider registries) {
        CutsceneIntro data = new CutsceneIntro();
        data.id = tag.getString("Scene");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("Scene", id);
        return tag;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer
                || player.getPersistentData().getBoolean(INTRO_SENT)
                || player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) != 0) {
            return;
        }
        MinecraftServer server = player.serverLevel().getServer();
        String id = get(server).id();
        if (id.isEmpty() || !CutsceneNetwork.canPlay(player)) {
            return;
        }
        try {
            Cutscene scene = CutsceneStore.load(server, id);
            if (scene == null) {
                LOGGER.warn("Configured intro cutscene {} is missing", id);
                return;
            }
            CutsceneNetwork.requireContent(scene);
            if (player.level().dimension().location().equals(scene.dimension())) {
                CutsceneNetwork.play(player, CutsceneJson.encode(scene), true);
                player.getPersistentData().putBoolean(INTRO_SENT, true);
            }
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warn("Could not read configured intro cutscene {}", id, e);
        }
    }
}
