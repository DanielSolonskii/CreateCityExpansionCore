package com.example.examplemod.economy;

import com.example.examplemod.client.freight.FreightGuideLayout;
import com.example.examplemod.network.FreightGuideRequest;
import com.example.examplemod.network.FreightGuideState;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Shared geometry, bounded protocol and shipped resources; not an in-game acceptance claim. */
public final class FreightGuideCheck {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void rejects(Runnable action,String message){try{action.run();throw new AssertionError(message);}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args)throws Exception {
        var p=new BlockPos(10,64,10);
        require(FreightGuideGeometry.warehouse(p,Direction.SOUTH).equals(new BlockPos(10,64,9)),"south-facing Packager reads north inventory");
        require(FreightGuideGeometry.warehouse(p,Direction.NORTH).equals(new BlockPos(10,64,11)),"north-facing Packager reads south inventory");
        require(FreightGuideGeometry.warehouse(p,Direction.EAST).equals(new BlockPos(9,64,10)),"east-facing Packager reads west inventory");
        require(FreightGuideGeometry.warehouse(p,Direction.WEST).equals(new BlockPos(11,64,10)),"west-facing Packager reads east inventory");
        require(FreightGuideGeometry.warehouse(p,Direction.UP).equals(p.below()),"vertical Packager reads below");
        require(FreightGuideGeometry.warehouse(p,Direction.DOWN).equals(p.above()),"vertical Packager reads above");
        require(FreightGuideGeometry.inRange(p,p.offset(16,0,0)),"16 blocks accepted");
        require(!FreightGuideGeometry.inRange(p,p.offset(16,1,0)),"diagonal over 16 rejected");
        require(!FreightGuideGeometry.inRange(p,p.offset(0,17,0)),"vertical limit respected");
        for(int[] size:new int[][]{{320,180},{320,240},{427,240},{480,270},{640,360},{960,540}}){
            var l=FreightGuideLayout.of(size[0],size[1]);
            require(l.x()>=0&&l.y()>=0&&l.x()+l.width()<=size[0]&&l.y()+l.height()<=size[1],"window in screen");
            require(l.contentTop()+20<l.contentBottom()&&l.contentBottom()<l.footer(),"input / content / controls separate");
            for(int a=0;a<6;a++){
                require(l.buttonX(a)>=l.x()&&l.buttonX(a)+l.buttonWidth()<=l.x()+l.width(),"button horizontal bounds");
                require(l.buttonY(a)>=l.footer()&&l.buttonY(a)+20<=l.y()+l.height(),"button vertical bounds");
                for(int b=a+1;b<6;b++)require(l.buttonX(a)+l.buttonWidth()<=l.buttonX(b)||l.buttonX(b)+l.buttonWidth()<=l.buttonX(a)||l.buttonY(a)+20<=l.buttonY(b)||l.buttonY(b)+20<=l.buttonY(a),"buttons do not overlap");
            }
        }
        var session=UUID.randomUUID();
        for(var action:FreightGuideRequest.Action.values()){
            var original=new FreightGuideRequest(action,session,null,p,2,"Северный склад");
            var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
            try{FreightGuideRequest.STREAM_CODEC.encode(buf,original);require(original.equals(FreightGuideRequest.STREAM_CODEC.decode(buf)),"request round trip "+action);}finally{buf.release();}
        }
        rejects(()->new FreightGuideRequest(FreightGuideRequest.Action.FINISH,session,null,p,3,"x"),"invalid mode");
        rejects(()->new FreightGuideRequest(FreightGuideRequest.Action.FINISH,session,null,p,0,"x".repeat(65)),"oversized name");
        rejects(()->new FreightGuideState(null),"null snapshot");
        var input=new CompoundTag();input.putString("Name","before");var state=new FreightGuideState(input);input.putString("Name","after");require(state.data().getString("Name").equals("before"),"response owns a snapshot");
        var loader=FreightGuideCheck.class.getClassLoader();
        try(var in=loader.getResourceAsStream("assets/createcityexpansioncore/ponder/freight_terminal.nbt")){
            require(in!=null,"Ponder structure shipped");var t=NbtIo.readCompressed(in,NbtAccounter.unlimitedHeap());
            require(t.getList("size",3).getInt(0)==13,"full Ponder base width");
            require(t.getList("blocks",10).size()>180,"real saved equipment, not just a floor");
            var palette=t.getList("palette",10);var blocks=t.getList("blocks",10);var names=new java.util.HashSet<String>();for(Tag raw:palette)names.add(((CompoundTag)raw).getString("Name"));
            for(String block:java.util.List.of("create:packager","create:white_postbox","create:belt","create:track","create:small_bogey","create:track_station","create:item_vault","create:stock_link","create:stock_ticker"))require(names.contains(block),"native equipment exists: "+block);
            for(Tag raw:blocks){var block=(CompoundTag)raw;var pos=block.getList("pos",3);for(int axis=0;axis<3;axis++)require(pos.getInt(axis)>=0&&pos.getInt(axis)<t.getList("size",3).getInt(axis),"equipment within schematic bounds");}
            for(Tag raw:blocks){var block=(CompoundTag)raw;var stateTag=palette.getCompound(block.getInt("state"));if(!stateTag.getString("Name").equals("create:andesite_funnel"))continue;var pos=block.getList("pos",3);int rear=pos.getInt(0)+(stateTag.getCompound("Properties").getString("facing").equals("east")?-1:1);require(blocks.stream().map(v->(CompoundTag)v).anyMatch(v->{var at=v.getList("pos",3);String name=palette.getCompound(v.getInt("state")).getString("Name");return at.getInt(0)==rear&&at.getInt(1)==pos.getInt(1)&&at.getInt(2)==pos.getInt(2)&&(name.equals("create:packager")||name.equals("create:white_postbox"));}),"funnel rear actually faces its package inventory");}
            require(blocks.stream().map(raw->(CompoundTag)raw).anyMatch(v->v.getList("pos",3).getInt(1)==3),"train cargo and stock links above floor are saved");
            require(t.getList("palette",10).getCompound(0).getString("Name").equals("minecraft:polished_andesite"),"registered base material");
        }
        com.google.gson.JsonObject ru,en;
        try(var in=loader.getResourceAsStream("assets/createcityexpansioncore/lang/ru_ru.json")){ru=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}
        try(var in=loader.getResourceAsStream("assets/createcityexpansioncore/lang/en_us.json")){en=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}
        for(var key:ru.keySet())if(key.contains("freight_guide.")||key.contains(".ponder.shared.fg_")||key.contains(".ponder.freight_"))require(en.has(key)&&!en.get(key).getAsString().isBlank(),"English translation "+key);
        for(var key:en.keySet())if(key.contains("freight_guide.")||key.contains(".ponder.shared.fg_")||key.contains(".ponder.freight_"))require(ru.has(key)&&!ru.get(key).getAsString().isBlank(),"Russian translation "+key);
        System.out.println("PASS: "+checks+" freight guide geometry, layout, protocol and Ponder resource checks");
    }
}
