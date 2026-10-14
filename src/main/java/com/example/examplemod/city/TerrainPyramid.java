package com.example.examplemod.city;

import java.util.*;

/** Derived terrain only. Never accesses chunks or generates world data. Server-thread owned. */
public final class TerrainPyramid {
    public static final int MAX_LEVEL=7, SIZE=32, COUNT=1024;
    public static final short NO_HEIGHT=Short.MIN_VALUE;
    public record Key(int level,int x,int z) {
        public Key { if(level<0||level>MAX_LEVEL)throw new IllegalArgumentException("terrain level"); }
        public int span(){return SIZE<<level;}
        public Key parent(){return new Key(level+1,Math.floorDiv(x,2),Math.floorDiv(z,2));}
    }
    public record Data(int[] colors,short[] heights) {
        public Data {if(colors.length!=COUNT||heights.length!=COUNT)throw new IllegalArgumentException("terrain data");colors=colors.clone();heights=heights.clone();}
    }
    private final Map<Key,Data> tiles=new HashMap<>();
    private final LinkedHashSet<Key> pending=new LinkedHashSet<>();
    public void put(int x,int z,int[] colors,short[] heights) {
        tiles.put(new Key(0,x,z),new Data(colors,heights));pending.add(new Key(1,Math.floorDiv(x,2),Math.floorDiv(z,2)));
    }
    public Data get(Key key){return tiles.get(key);}
    /** Work is bounded by time and count; overview construction is never done in a packet handler. */
    public void advance(int maximum,long nanos) {
        long until=System.nanoTime()+nanos;
        for(int i=0;i<maximum&&!pending.isEmpty()&&System.nanoTime()<until;i++) {
            Key key=pending.iterator().next();pending.remove(key);
            Data[] children=new Data[4];for(int z=0;z<2;z++)for(int x=0;x<2;x++)children[z*2+x]=tiles.get(new Key(key.level-1,key.x*2+x,key.z*2+z));
            tiles.put(key,reduce(children));if(key.level<MAX_LEVEL)pending.add(key.parent());
        }
    }
    public static Data reduce(Data[] children) {
        int[] colors=new int[COUNT];short[] heights=new short[COUNT];Arrays.fill(heights,NO_HEIGHT);
        for(int z=0;z<SIZE;z++)for(int x=0;x<SIZE;x++) {
            Data d=children[(z/16)*2+x/16];if(d==null)continue;
            int r=0,g=0,b=0,n=0,h=0,hn=0;
            for(int dz=0;dz<2;dz++)for(int dx=0;dx<2;dx++) {
                int at=((z%16)*2+dz)*SIZE+(x%16)*2+dx,c=d.colors[at];
                if((c>>>24)==0)continue;
                int weight=c>>>24;r+=(c>>16&255)*weight;g+=(c>>8&255)*weight;b+=(c&255)*weight;n+=weight;
                if(d.heights[at]!=NO_HEIGHT){h+=d.heights[at]*weight;hn+=weight;}
            }
            if(n>0)colors[z*SIZE+x]=Math.max(1,n/4)<<24|(r/n)<<16|(g/n)<<8|b/n;
            if(hn>0)heights[z*SIZE+x]=(short)Math.round((double)h/hn);
        }
        return new Data(colors,heights);
    }
    /** A one-sample halo provides continuous lighting at tile boundaries. */
    public short[] halo(Key key) {
        short[] result=new short[34*34];Arrays.fill(result,NO_HEIGHT);
        for(int z=-1;z<=32;z++)for(int x=-1;x<=32;x++) {
            Data d=tiles.get(new Key(key.level,key.x+Math.floorDiv(x,32),key.z+Math.floorDiv(z,32)));
            if(d!=null)result[(z+1)*34+x+1]=d.heights[Math.floorMod(z,32)*32+Math.floorMod(x,32)];
        }
        return result;
    }
    public static long revision(int[] colors,short[] heights) {
        long hash=0xcbf29ce484222325L;
        for(int c:colors)hash=(hash^c)*0x100000001b3L;
        for(short h:heights)hash=(hash^h)*0x100000001b3L;
        return hash==0?1:hash;
    }
}
