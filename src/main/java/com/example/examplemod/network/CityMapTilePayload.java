package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.BitSet;

/**
 * One tile of the survey atlas.
 *
 * @param colors    flat {@code 0xAARRGGBB} sample colours, {@code SAMPLE_COUNT × SAMPLE_COUNT}
 * @param contours  one bit per sample: true when the ground crosses an elevation isoline here.
 *                  Sent as a bit set rather than as heights because the client never needs the
 *                  height itself — only whether a line belongs here. That is 128 bytes per tile
 *                  instead of 1024, and it cannot go stale against the colour underneath it the
 *                  way a second, separately interpolated channel eventually would.
 */
public record CityMapTilePayload(
        BlockPos pylonPos,
        ResourceLocation dimensionId,
        int tileX,
        int tileZ,
        boolean complete,
        int[] colors,
        BitSet contours
) implements CustomPacketPayload {

    /**
     * One sample per world block. The previous step of 2 meant four blocks were flattened into every
     * drawn pixel, which is what made the terrain read as oversized squares; a vanilla chunk now maps
     * exactly onto a 16×16 block of samples.
     */
    public static final int SAMPLE_COUNT = 32;
    public static final int SAMPLE_STEP = 1;
    public static final int WORLD_SIZE = SAMPLE_COUNT * SAMPLE_STEP;
    public static final int COLOR_COUNT = SAMPLE_COUNT * SAMPLE_COUNT;

    public static final Type<CityMapTilePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    CreateCityExpansionCore.MODID,
                    "city_map_tile"
            )
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CityMapTilePayload>
            STREAM_CODEC = StreamCodec.of(
                    (buffer, payload) -> {
                        BlockPos.STREAM_CODEC.encode(buffer, payload.pylonPos());
                        buffer.writeResourceLocation(payload.dimensionId());
                        buffer.writeInt(payload.tileX());
                        buffer.writeInt(payload.tileZ());
                        buffer.writeBoolean(payload.complete());
                        for (int color : payload.colors()) {
                            buffer.writeInt(color);
                        }
                        buffer.writeBitSet(payload.contours());
                    },
                    buffer -> {
                        BlockPos pylonPos = BlockPos.STREAM_CODEC.decode(buffer);
                        ResourceLocation dimension = buffer.readResourceLocation();
                        int tileX = buffer.readInt();
                        int tileZ = buffer.readInt();
                        boolean complete = buffer.readBoolean();
                        int[] colors = new int[COLOR_COUNT];
                        for (int index = 0; index < colors.length; index++) {
                            colors[index] = buffer.readInt();
                        }
                        /*
                         * readBitSet() has no size argument in this MC version and reads exactly as
                         * many bits as writeBitSet() wrote. The length is therefore taken from the
                         * tile's own colour count, which is fixed, and any surplus bits the decoder
                         * hands back are dropped by the canonical constructor below.
                         */
                        BitSet contours = buffer.readBitSet();
                        return new CityMapTilePayload(
                                pylonPos,
                                dimension,
                                tileX,
                                tileZ,
                                complete,
                                colors,
                                contours
                        );
                    }
            );

    public CityMapTilePayload {
        if (colors.length != COLOR_COUNT) {
            throw new IllegalArgumentException("Invalid city map tile color count");
        }
        colors = colors.clone();
        if (contours == null) {
            contours = new BitSet(COLOR_COUNT);
        } else if (contours.length() > COLOR_COUNT) {
            throw new IllegalArgumentException("Invalid city map tile contour count");
        } else {
            contours = (BitSet) contours.clone();
        }
    }

    @Override
    public int[] colors() {
        return colors.clone();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
