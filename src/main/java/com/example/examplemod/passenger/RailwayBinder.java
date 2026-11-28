package com.example.examplemod.passenger;

import com.example.examplemod.block.TransportProtection;
import com.simibubi.create.content.trains.RailwaySavedData;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/** The operator's notebook is kept in server player data, including when the item is replaced. */
public final class RailwayBinder extends Item {
    static final String STATIONS="ccec_binder_stations", TRAINS="ccec_binder_trains";
    public RailwayBinder(Properties properties) { super(properties); }

    static List<UUID> selected(ServerPlayer actor,String key) {
        return actor.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getList(key,Tag.TAG_INT_ARRAY)
                .stream().limit(PassengerData.MAX_PLATFORMS).map(NbtUtils::loadUUID).toList();
    }
    private static void remember(ServerPlayer actor,String key,UUID id) {
        var data=actor.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        var ids=data.getList(key,Tag.TAG_INT_ARRAY);
        var value=NbtUtils.createUUID(id);
        if (!ids.contains(value)) {
            if (ids.size()>=PassengerData.MAX_PLATFORMS) throw new IllegalArgumentException("limit");
            ids.add(value);
        }
        data.put(key,ids);
        actor.getPersistentData().put(Player.PERSISTED_NBT_TAG,data);
    }
    private static void operator(ServerPlayer actor) {
        if (!actor.hasPermissions(2) || actor.isSpectator()) throw new IllegalArgumentException("op_required");
    }
    private static void rememberTrain(ServerPlayer actor,Train train) {
        if (train==null || train.invalid || train.derailed) throw new IllegalArgumentException("no_docked_train");
        if (!TransportProtection.canModifyTrain(actor,train)) throw new IllegalArgumentException("permission");
        remember(actor,TRAINS,train.id);
        actor.displayClientMessage(Component.translatable("ccec.passenger.binder_train",train.name),true);
    }
    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof StationBlockEntity station)) return InteractionResult.PASS;
        if (context.getPlayer() instanceof ServerPlayer actor) {
            try {
                operator(actor);
                var stop=station.getStation();
                if (stop==null || stop.assembling) throw new IllegalArgumentException("look");
                if (actor.isShiftKeyDown()) rememberTrain(actor,stop.getPresentTrain());
                else {
                    if (PassengerService.stop(actor,stop.id)==null) PassengerService.platform(actor,false);
                    if (PassengerService.stop(actor,stop.id)==null) throw new IllegalArgumentException("platform");
                    remember(actor,STATIONS,stop.id);
                    actor.displayClientMessage(Component.translatable("ccec.passenger.binder_station",stop.name),true);
                }
            } catch (IllegalArgumentException e) { error(actor,e); }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }
    /** Create carriages cannot be picked as ordinary entities. Intersect their actual block outlines up to the first world block. */
    public static CarriageContraptionEntity aimedCarriage(Player player) {
        var eye=player.getEyePosition();
        var end=blockHit(player).getLocation();
        var bounds=new AABB(eye,end).inflate(8);
        double closest=eye.distanceToSqr(end)+1e-6;
        CarriageContraptionEntity nearest=null;
        for(var entity:player.level().getEntitiesOfClass(CarriageContraptionEntity.class,bounds)){
            var body=entity.getContraption();
            if(body==null) continue;
            var a=entity.toLocalVector(eye,1F);
            var b=entity.toLocalVector(end,1F);
            BlockHitResult hit=BlockGetter.traverseBlocks(a,b,body,(c,pos)->{
                var block=c.getBlocks().get(pos);
                return block==null?null:block.state().getShape(c.getContraptionWorld(),pos).clip(a,b,pos);
            },c->null);
            if(hit==null)continue;
            double distance=a.distanceToSqr(hit.getLocation());
            if(distance<closest){closest=distance;nearest=entity;}
        }
        return nearest;
    }
    private static BlockHitResult blockHit(Player player) {
        var eye=player.getEyePosition();
        var end=eye.add(player.getLookAngle().scale(player.blockInteractionRange()));
        return player.level().clip(new ClipContext(eye,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        if (player instanceof ServerPlayer actor) {
            try {
                operator(actor);
                var carriage=aimedCarriage(actor);
                if(carriage!=null) rememberTrain(actor,RailwaySavedData.load(actor.getServer()).getTrains().get(carriage.trainId));
                else if (actor.isShiftKeyDown() && blockHit(actor).getType()
                        == net.minecraft.world.phys.HitResult.Type.MISS) {
                    var data=actor.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
                    data.remove(STATIONS);data.remove(TRAINS);
                    actor.getPersistentData().put(Player.PERSISTED_NBT_TAG,data);
                    actor.displayClientMessage(Component.translatable("ccec.passenger.binder_cleared"),true);
                } else actor.displayClientMessage(Component.translatable("ccec.passenger.binder_hint"),true);
            } catch (IllegalArgumentException denied) { error(actor,denied); }
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide());
    }
    private static void error(ServerPlayer actor,IllegalArgumentException e) {
        actor.displayClientMessage(Component.translatable("ccec.passenger."+e.getMessage()),true);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag) {
        lines.add(Component.translatable("ccec.passenger.binder_hint"));
        lines.add(Component.translatable("ccec.passenger.binder_clear_hint"));
    }
}
