package com.example.examplemod.client.map;

import java.util.Arrays;
import java.util.BitSet;

/** Visual regression properties: hue, material boundaries, LOD, and bounded regional zoom. */
public final class TerrainVisualCheck {
    private static int checks;
    private static void require(boolean condition,String message) {
        checks++;
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        TerrainPipelineCheck.run();
        int size=32;
        int[] noise=new int[size*size];
        for(int z=0;z<size;z++)for(int x=0;x<size;x++)noise[z*size+x]=((x+z)&1)==0?0xFF629132:0xFF70A040;
        int[] original=noise.clone(),smooth=TerrainSurfaceFilter.smooth(noise,size);
        require(Arrays.equals(noise,original),"filter never modifies persisted/raw samples");
        require(variation(smooth)<variation(noise)/2,"fine-grain tonal noise is substantially reduced");
        for(int z=0;z<size;z++)for(int x=0;x<size;x++) {
            if(x==0||z==0||x==31||z==31)require(noise[z*size+x]==smooth[z*size+x],"independent tile edges remain exact");
            require((smooth[z*size+x]>>>24)==255,"filter retains opacity");
        }
        int[] coast=new int[1024];
        for(int z=0;z<32;z++)for(int x=0;x<32;x++)coast[z*32+x]=x<16?0xFF373A97:0xFF689739;
        require(Arrays.equals(coast,TerrainSurfaceFilter.smooth(coast,32)),"water/land boundary never bleeds across materials");
        coast[17*32+17]=0x00689739;
        require(TerrainSurfaceFilter.smooth(coast,32)[17*32+17]==coast[17*32+17],"unknown alpha is preserved");
        var allContours=new BitSet(1024);allContours.set(0,1024);
        int[] plain=new int[1024];Arrays.fill(plain,0xFF689739);
        int[] close=TerrainTileTexture.raster(plain,allContours,1);
        int base=TerrainTileTexture.raster(plain,new BitSet(),1)[0];
        require(MapPalette.luminance(close[0])<MapPalette.luminance(base),"close relief remains visible");
        require(MapPalette.luminance(base)-MapPalette.luminance(close[0])<32,"dense contours cannot turn terrain into black fill");
        for(int stride:new int[]{8,16,32})require(Arrays.equals(TerrainTileTexture.raster(plain,allContours,stride),TerrainTileTexture.raster(plain,new BitSet(),stride)),"far LOD suppresses contour mosaics");
        int grass=MapSurveyTransform.survey(0xFF689739),water=MapSurveyTransform.survey(0xFF373A97);
        require(MapPalette.green(grass)>MapPalette.red(grass)&&MapPalette.green(grass)>MapPalette.blue(grass),"plains stay green");
        require(MapPalette.blue(water)>MapPalette.red(water)&&MapPalette.blue(water)>MapPalette.green(water),"water stays blue");
        int sand=MapSurveyTransform.survey(0xFFDAD29A);
        require(MapPalette.red(sand)-MapPalette.blue(sand)>40,"sand retains a warm distinct hue");
        require(AtlasMapScale.MIN_ZOOM==0.25/4,"fourfold linear zoom-out extension");
        require(AtlasMapScale.clamp(.00001)==AtlasMapScale.MIN_ZOOM&&AtlasMapScale.clamp(99)==4,"zoom bounds enforced");
        for(int span:new int[]{8,64,80,120,600,2000})for(int cx:new int[]{-12000,-30,0,20000}) {
            var window=AtlasMapScale.requestWindow(cx-span,cx+span,-span,span,cx,0);
            require(window.count()<=(long)AtlasMapScale.REQUEST_SIDE*AtlasMapScale.REQUEST_SIDE,"regional request scan bounded");
            require(window.contains(cx,0),"negative/positive coordinates keep view centre in request window");
            require(window.minX()>=cx-span&&window.maxX()<=cx+span&&window.minZ()>=-span&&window.maxZ()<=span,"loading never leaves visible rectangle");
        }
        var panoramic=AtlasMapScale.requestWindow(-100,100,-5,5,0,0);
        require(panoramic.count()==201*11,"ordinary wide view loads its full width");
        if(args.length>0)preview(java.nio.file.Path.of(args[0]));
        System.out.println("PASS: "+checks+" terrain and regional zoom visual checks");
    }
    private static long variation(int[] colors) {
        long sum=0;
        for(int z=1;z<31;z++)for(int x=1;x<31;x++)sum+=Math.abs(MapPalette.luminance(colors[z*32+x])-MapPalette.luminance(colors[z*32+x+1]));
        return sum;
    }
    /** Optional fixture output uses the production raster; it is not an in-game screenshot. */
    private static void preview(java.nio.file.Path path) throws Exception {
        var image=new java.awt.image.BufferedImage(768,256,java.awt.image.BufferedImage.TYPE_INT_RGB);
        int[] swatches={0xFF373A97,0xFF42622F,0xFF689739,0xFFDAD29A,0xFF7C7C7C,0xFFE6E8E9};
        var random=new java.util.Random(49);
        for(int band=0;band<6;band++) {
            int[] input=new int[1024];var mask=new BitSet(1024);
            for(int z=0;z<32;z++)for(int x=0;x<32;x++) {
                int c=swatches[band],delta=random.nextInt(21)-10;
                input[z*32+x]=0xFF000000|MapPalette.clamp(MapPalette.red(c)+delta)<<16|MapPalette.clamp(MapPalette.green(c)+delta)<<8|MapPalette.clamp(MapPalette.blue(c)+delta);
                if((x+z*2)%9<2)mask.set(z*32+x);
            }
            int[] close=TerrainTileTexture.raster(input,mask,1),far=TerrainTileTexture.raster(input,mask,8);
            for(int y=0;y<256;y++)for(int x=0;x<128;x++)image.setRGB(band*128+x,y,(y<128?close:far)[(y%128/4)*32+x/4]);
        }
        java.nio.file.Files.createDirectories(path.toAbsolutePath().getParent());
        javax.imageio.ImageIO.write(image,"png",path.toFile());
    }
}
