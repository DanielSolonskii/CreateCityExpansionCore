package com.example.examplemod.client.freight;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.infrastructure.ponder.scenes.highLogistics.PonderHilo;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Real saved equipment and native Create package animations. No live economy actions. */
public final class FreightPonderScenes {
    private static final BlockPos STOCK=new BlockPos(1,2,2),PACK=new BlockPos(2,2,2),BOX=new BlockPos(6,2,2),RECEIVER=new BlockPos(10,2,8),DEST=new BlockPos(11,2,8);
    private FreightPonderScenes(){}
    private static CreateSceneBuilder base(SceneBuilder builder,SceneBuildingUtil u,String id){
        var s=new CreateSceneBuilder(builder);s.title(id,"CCEC freight");s.configureBasePlate(0,0,13);s.scaleSceneView(.65f);s.showBasePlate();s.idle(10);return s;
    }
    private static void note(CreateSceneBuilder s,String key,BlockPos pos){s.addKeyframe();s.overlay().showText(70).sharedText("fl_"+key).pointAt(Vec3.atCenterOf(pos)).placeNearTarget();s.idle(80);}
    private static ItemStack parcel(){var box=PackageItem.containing(List.of(new ItemStack(Items.IRON_INGOT,64)));PackageItem.addAddress(box,"CCEC_DESTINATION");return box;}
    private static void sender(CreateSceneBuilder s,SceneBuildingUtil u){s.world().showSection(u.select().fromTo(0,1,2,6,2,3),Direction.DOWN);s.world().setKineticSpeed(u.select().fromTo(3,1,2,5,1,2),-32);s.idle(15);}
    private static void pack(CreateSceneBuilder s,SceneBuildingUtil u){var box=parcel();PonderHilo.packagerCreate(s,PACK,box);s.idle(25);s.world().createItemOnBelt(new BlockPos(3,1,2),Direction.WEST,box);PonderHilo.packagerClear(s,PACK);s.idle(40);}
    public static void terminal(SceneBuilder b,SceneBuildingUtil u){
        var s=base(b,u,"freight_terminal");sender(s,u);note(s,"warehouse",STOCK);
        s.overlay().showBigLine(PonderPalette.INPUT,Vec3.atCenterOf(STOCK),Vec3.atCenterOf(PACK),70);note(s,"facing",PACK);
        pack(s,u);note(s,"physical",new BlockPos(4,1,2));
        s.world().showSection(u.select().fromTo(0,1,4,12,1,6),Direction.DOWN);s.world().animateTrainStation(new BlockPos(6,1,4),true);note(s,"station",BOX);
        note(s,"setup",PACK);s.markAsFinished();
    }
    public static void warehouse(SceneBuilder b,SceneBuildingUtil u){
        var s=base(b,u,"freight_warehouse");sender(s,u);s.world().showSection(u.select().position(2,3,2),Direction.DOWN);s.world().showSection(u.select().position(4,1,0),Direction.DOWN);
        note(s,"network",new BlockPos(2,3,2));s.world().showSection(u.select().fromTo(1,1,0,1,2,1),Direction.DOWN);
        s.world().modifyBlockEntity(new BlockPos(1,2,0),FactoryPanelBlockEntity.class,be->{be.restocker=true;var p=be.panels.get(PanelSlot.TOP_LEFT);p.active=true;p.setFilter(new ItemStack(Items.IRON_INGOT));p.count=128;});
        note(s,"restocker",new BlockPos(1,2,0));PonderHilo.packagerUnpack(s,new BlockPos(1,2,1),parcel());note(s,"replenish",STOCK);pack(s,u);note(s,"stock_scope",PACK);s.markAsFinished();
    }
    public static void delivery(SceneBuilder b,SceneBuildingUtil u){
        var s=base(b,u,"freight_delivery");sender(s,u);s.world().showSection(u.select().fromTo(0,1,4,12,1,6),Direction.DOWN);
        var train=s.world().showIndependentSection(u.select().fromTo(2,2,5,4,3,5),Direction.DOWN);s.idle(20);note(s,"quote",new BlockPos(3,3,5));pack(s,u);
        s.world().createItemOnBeltLike(new BlockPos(3,3,5),Direction.DOWN,parcel());note(s,"load",BOX);
        s.world().animateBogey(new BlockPos(3,2,5),5,55);s.world().moveSection(train,new Vec3(5,0,0),55);s.idle(65);
        s.world().showSection(u.select().fromTo(6,1,7,12,2,9),Direction.DOWN);s.world().setKineticSpeed(u.select().fromTo(7,1,8,9,1,8),-32);
        s.world().createItemOnBelt(new BlockPos(7,1,8),Direction.WEST,parcel());s.idle(60);PonderHilo.packagerUnpack(s,RECEIVER,parcel());note(s,"receive",DEST);note(s,"pay",DEST);s.markAsFinished();
    }
    public static void hub(SceneBuilder b,SceneBuildingUtil u){
        var s=base(b,u,"freight_hub");sender(s,u);s.world().showSection(u.select().fromTo(0,1,4,12,1,6),Direction.DOWN);s.world().showSection(u.select().fromTo(6,1,7,12,2,9),Direction.DOWN);
        var inbound=s.world().showIndependentSection(u.select().fromTo(2,2,5,4,3,5),Direction.DOWN);s.world().moveSection(inbound,new Vec3(4,0,0),45);s.idle(55);
        s.world().createItemOnBelt(new BlockPos(7,1,8),Direction.WEST,parcel());s.world().setKineticSpeed(u.select().fromTo(7,1,8,9,1,8),-32);s.idle(55);s.world().createItemOnBeltLike(DEST,Direction.DOWN,parcel());note(s,"hub_store",new BlockPos(10,1,9));
        note(s,"hub_address",new BlockPos(6,2,8));note(s,"hub_schedule",new BlockPos(6,1,6));
        s.world().animateBogey(new BlockPos(3,2,5),-4,45);s.world().moveSection(inbound,new Vec3(-4,0,0),45);s.idle(55);note(s,"hub_final",DEST);s.markAsFinished();
    }
}
