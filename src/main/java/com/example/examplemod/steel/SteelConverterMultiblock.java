package com.example.examplemod.steel;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.ArrayList;
import java.util.List;

/** Canonical 9x9x11 basic-oxygen-converter hall; controller is centered on the front face. */
public final class SteelConverterMultiblock {
    public static final int WIDTH=9, HEIGHT=9, DEPTH=11;
    public static final LocalPos CONTROLLER=new LocalPos(4,2,0), FLUID_INPUT=new LocalPos(1,1,0), ITEM_INPUT=new LocalPos(4,7,0),
            FLUID_OUTPUT=new LocalPos(7,1,0), POWER_PORT=new LocalPos(4,1,10), BLOWER=new LocalPos(0,4,5);
    private SteelConverterMultiblock(){}
    public static ExpectedBlock expectedAt(int x,int y,int z){
        if(x<0||x>=WIDTH||y<0||y>=HEIGHT||z<0||z>=DEPTH)return ExpectedBlock.AIR;
        LocalPos p=new LocalPos(x,y,z);
        if(p.equals(CONTROLLER))return ExpectedBlock.CONTROLLER;if(p.equals(FLUID_INPUT))return ExpectedBlock.FLUID_INPUT;
        if(p.equals(ITEM_INPUT))return ExpectedBlock.ITEM_INPUT;if(p.equals(FLUID_OUTPUT))return ExpectedBlock.FLUID_OUTPUT;
        if(p.equals(POWER_PORT))return ExpectedBlock.POWER_PORT;if(p.equals(BLOWER))return ExpectedBlock.BLOWER;
        if(y==0)return ExpectedBlock.REINFORCED_CONCRETE;
        boolean perimeter=x==0||x==WIDTH-1||z==0||z==DEPTH-1;
        if(perimeter)return ExpectedBlock.FRAME;
        double dx=x-4,dz=z-5; double r2=dx*dx+dz*dz;
        if(y>=1&&y<=7&&r2<=9&&r2>=2)return ExpectedBlock.REFRACTORY;
        if(y==8&&r2<=9)return ExpectedBlock.REFRACTORY;
        return ExpectedBlock.AIR;
    }
    public static ValidationResult validate(ServerLevel level,BlockPos controller){
        BlockState state=level.getBlockState(controller);if(!state.is(CreateCityExpansionCore.STEEL_CONVERTER_CONTROLLER.get()))return failed(controller);
        Direction facing=state.getValue(BlockStateProperties.HORIZONTAL_FACING);BlockPos origin=getOrigin(controller,facing);List<Mismatch> bad=new ArrayList<>();int correct=0,required=0;
        for(int y=0;y<HEIGHT;y++)for(int z=0;z<DEPTH;z++)for(int x=0;x<WIDTH;x++){ExpectedBlock e=expectedAt(x,y,z);BlockPos w=getWorldPosition(origin,facing,x,y,z);BlockState a=level.getBlockState(w);if(e!=ExpectedBlock.AIR)required++;if(matches(a,e)){if(e!=ExpectedBlock.AIR)correct++;}else bad.add(new Mismatch(w,new LocalPos(x,y,z),e));}
        return new ValidationResult(bad.isEmpty(),correct,required,List.copyOf(bad),world(origin,facing,FLUID_INPUT),world(origin,facing,ITEM_INPUT),world(origin,facing,FLUID_OUTPUT),world(origin,facing,POWER_PORT),world(origin,facing,BLOWER));
    }
    public static boolean matches(BlockState s,ExpectedBlock e){return e==ExpectedBlock.AIR?s.isAir():s.is(requiredBlock(e));}
    public static Block requiredBlock(ExpectedBlock e){return switch(e){
        case REINFORCED_CONCRETE->CreateCityExpansionCore.REINFORCED_CONCRETE.get();case REFRACTORY->CreateCityExpansionCore.REFRACTORY_BLOCK.get();case FRAME->CreateCityExpansionCore.MINE_FRAME.get();
        case CONTROLLER->CreateCityExpansionCore.STEEL_CONVERTER_CONTROLLER.get();case FLUID_INPUT->CreateCityExpansionCore.STEEL_CONVERTER_FLUID_INPUT.get();case ITEM_INPUT->CreateCityExpansionCore.STEEL_CONVERTER_ITEM_INPUT.get();case FLUID_OUTPUT->CreateCityExpansionCore.STEEL_CONVERTER_OUTPUT.get();case POWER_PORT->CreateCityExpansionCore.MINE_POWER_PORT.get();case BLOWER->CreateCityExpansionCore.STEELMAKING_BLOWER_PORT.get();case AIR->throw new IllegalArgumentException();};}
    public static BlockPos getOrigin(BlockPos c,Direction f){return c.below(CONTROLLER.y()).relative(f.getClockWise().getOpposite(),CONTROLLER.x());}
    public static BlockPos getWorldPosition(BlockPos o,Direction f,int x,int y,int z){return o.above(y).relative(f.getClockWise(),x).relative(f.getOpposite(),z);}
    public static BlockPos getWorldPosition(BlockPos o,Direction f,LocalPos p){return getWorldPosition(o,f,p.x,p.y,p.z);}private static BlockPos world(BlockPos o,Direction f,LocalPos p){return getWorldPosition(o,f,p);}
    private static ValidationResult failed(BlockPos c){return new ValidationResult(false,0,0,List.of(new Mismatch(c,CONTROLLER,ExpectedBlock.CONTROLLER)),null,null,null,null,null);}
    public enum ExpectedBlock{AIR,REINFORCED_CONCRETE,REFRACTORY,FRAME,CONTROLLER,FLUID_INPUT,ITEM_INPUT,FLUID_OUTPUT,POWER_PORT,BLOWER}
    public record LocalPos(int x,int y,int z){} public record Mismatch(BlockPos worldPos,LocalPos localPos,ExpectedBlock expected){}
    public record ValidationResult(boolean assembled,int correctBlocks,int requiredBlocks,List<Mismatch> mismatches,BlockPos fluidInput,BlockPos itemInput,BlockPos fluidOutput,BlockPos powerPort,BlockPos blower){}
}
