package com.example.examplemod.mine;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.*;
import com.example.examplemod.machine.MachinePanels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder(CreateCityExpansionCore.MODID)
@PrefixGameTestTemplate(false)
public final class MineControllerResetGameTest {
    @GameTest(template="auger_empty", timeoutTicks=200)
    public static void destructionDropsCleanControllerAndLegacyItemsReset(GameTestHelper h) {
        var level=h.getLevel();
        var pos=h.absolutePos(new BlockPos(4,3,4));
        var block=CreateCityExpansionCore.MINE_CONTROLLER.get();
        var state=block.defaultBlockState();
        level.setBlockAndUpdate(pos,state);
        var controller=(MineControllerBlockEntity)level.getBlockEntity(pos);
        UUID owner=UUID.randomUUID(), projectId=UUID.randomUUID();
        var cities=CityManager.get(level.getServer().overworld());
        var city=cities.createCity("Reset "+owner.toString().substring(0,8),owner,level.dimension().location(),pos.offset(2000,0,0));
        var deposit=ResourceLocation.parse("test:old_deposit");
        var resource=ResourceLocation.parse("test:old_resource");
        controller.setCreator(owner,"Previous owner");
        h.assertTrue(cities.addProject(city,new CityProject(projectId,city.getId(),CityProjectType.INDUSTRIAL_MINE,
                level.dimension().location(),pos,owner,deposit,resource,1)),"Project registered");
        h.assertTrue(controller.commission(city.getId(),projectId,deposit,resource),"Controller commissioned");
        level.setBlockAndUpdate(pos,state.setValue(MachinePanels.COLOR,DyeColor.BLUE));
        h.assertTrue(cities.getProject(projectId)!=null && level.getBlockEntity(pos)==controller,"Painting preserves controller and project");
        level.destroyBlock(pos,true);
        h.assertTrue(cities.getProject(projectId)==null,"Destroyed controller releases city project slot");
        var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2)).stream()
                .filter(e->e.getItem().is(CreateCityExpansionCore.MINE_CONTROLLER_ITEM.get())).toList();
        h.assertTrue(drops.size()==1 && drops.getFirst().getItem().getCount()==1,"Exactly one controller drops");
        var item=drops.getFirst().getItem();
        h.assertTrue(!item.has(DataComponents.CUSTOM_DATA) && !item.has(DataComponents.BLOCK_ENTITY_DATA),"Drop carries no configuration");

        var legacy=new CompoundTag(); var saved=new CompoundTag();
        saved.putUUID("Creator",owner); saved.putString("CreatorName","Previous owner");
        saved.putString("Deposit",deposit.toString()); saved.putString("Resource",resource.toString());
        saved.putBoolean("Commissioned",true); saved.putUUID("CityId",city.getId()); saved.putUUID("ProjectId",projectId);
        legacy.put("createcityexpansioncore:mine_controller",saved);
        item.set(DataComponents.CUSTOM_DATA,CustomData.of(legacy));
        level.setBlockAndUpdate(pos,state);
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        block.setPlacedBy(level,pos,state,player,item);
        var fresh=(MineControllerBlockEntity)level.getBlockEntity(pos);
        h.assertTrue(fresh.getCreatorId().filter(player.getUUID()::equals).isPresent(),"New placer owns controller");
        h.assertTrue(!fresh.isCommissioned() && fresh.getProjectId().isEmpty()
                && fresh.getActiveDepositId().isEmpty() && fresh.getSpecializedResourceId().isEmpty(),"Legacy item does not restore configuration");
        h.succeed();
    }
}
