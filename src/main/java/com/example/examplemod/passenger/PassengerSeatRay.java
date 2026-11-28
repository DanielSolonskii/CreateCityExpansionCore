package com.example.examplemod.passenger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.function.Function;

/** A server-side outline ray in carriage-local coordinates, including Create's horizontally protruding shapes. */
final class PassengerSeatRay {
    enum Reach { OPEN, BLOCKED, UNKNOWN }
    private static final Direction[] NEIGHBORS={Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
    static Reach first(Vec3 eye,Vec3 aim,BlockPos seat,Function<BlockPos,VoxelShape> outlines) {
        return BlockGetter.traverseBlocks(eye,aim,outlines,(shapes,cell)->{
            for(var side:NEIGHBORS){
                var pos=side==Direction.DOWN?cell:cell.relative(side);
                var shape=shapes.apply(pos);
                if(shape==null){
                    if(Shapes.block().clip(eye,aim,pos)!=null)return Reach.UNKNOWN;
                }else if(!shape.isEmpty() && shape.clip(eye,aim,pos)!=null)
                    return pos.equals(seat)?Reach.OPEN:Reach.BLOCKED;
            }
            return null;
        },ignored->Reach.BLOCKED);
    }
    private PassengerSeatRay(){}
}
