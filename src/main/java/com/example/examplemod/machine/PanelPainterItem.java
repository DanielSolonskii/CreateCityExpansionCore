package com.example.examplemod.machine;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.CityProtectionManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.List;
import java.util.function.Consumer;

@EventBusSubscriber(modid = CreateCityExpansionCore.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class PanelPainterItem extends Item {
    private static final String COLOR_KEY = "PanelColor";
    public static Consumer<InteractionHand> openPalette = hand -> {};

    public PanelPainterItem(Properties properties) { super(properties); }

    public static DyeColor color(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return DyeColor.byName(tag.getString(COLOR_KEY), MachinePanels.DEFAULT_COLOR);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) openPalette.accept(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return useOn(context);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) return use(context.getLevel(), player, context.getHand()).getResult();
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        if (!MachinePanels.isPanel(state)) return InteractionResult.PASS;
        if (!player.mayBuild()) return InteractionResult.FAIL;
        if (player instanceof ServerPlayer serverPlayer) {
            if (!CityProtectionManager.canModify(serverPlayer.serverLevel(), serverPlayer, pos)) {
                player.displayClientMessage(Component.translatable("message.createcityexpansioncore.panel_painter.denied"), true);
                return InteractionResult.FAIL;
            }
            var next = state.setValue(MachinePanels.COLOR, color(context.getItemInHand()));
            if (state != next && !level.setBlock(pos, next, Block.UPDATE_CLIENTS)) return InteractionResult.FAIL;
            com.example.examplemod.network.PaintSprayFeedback.send(serverPlayer, context.getHand(),
                    context.getClickLocation().add(net.minecraft.world.phys.Vec3.atLowerCornerOf(
                            context.getClickedFace().getNormal()).scale(.025)), color(context.getItemInHand()));
        }
        return InteractionResult.CONSUME;
    }

    public static Component colorName(DyeColor color) {
        return Component.translatable("item.minecraft." + color.getName() + "_dye");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.createcityexpansioncore.panel_painter.selected", colorName(color(stack))));
        tooltip.add(Component.translatable("item.createcityexpansioncore.panel_painter.controls"));
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(SelectColor.TYPE, SelectColor.CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (payload.colorId < 0 || payload.colorId >= DyeColor.values().length) return;
                    var stack = context.player().getItemInHand(payload.hand);
                    if (!(stack.getItem() instanceof PanelPainterItem)) return;
                    CustomData.update(DataComponents.CUSTOM_DATA, stack,
                            tag -> tag.putString(COLOR_KEY, DyeColor.byId(payload.colorId).getName()));
                }));
    }

    public record SelectColor(InteractionHand hand, int colorId) implements CustomPacketPayload {
        public static final Type<SelectColor> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID, "panel_paint_color"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SelectColor> CODEC = StreamCodec.of(
                (buf, payload) -> { buf.writeEnum(payload.hand); buf.writeVarInt(payload.colorId); },
                buf -> new SelectColor(buf.readEnum(InteractionHand.class), buf.readVarInt()));

        @Override public Type<SelectColor> type() { return TYPE; }
    }
}
