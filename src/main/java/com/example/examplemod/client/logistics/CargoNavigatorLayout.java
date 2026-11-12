package com.example.examplemod.client.logistics;

/** Native-size map and quote geometry; shared by drawing, clicks and headless checks. */
public record CargoNavigatorLayout(Rect map, Rect summary, Rect back, Rect worldMap, Rect order,
                                   boolean stacked) {
    public record Rect(int x,int y,int width,int height) {
        public int right(){return x+width;}
        public int bottom(){return y+height;}
        public boolean contains(double px,double py){return px>=x&&py>=y&&px<right()&&py<bottom();}
        public boolean overlaps(Rect r){return x<r.right()&&right()>r.x&&y<r.bottom()&&bottom()>r.y;}
    }
    public static CargoNavigatorLayout of(int width,int height){
        int margin=width<480?8:14,inner=Math.max(1,width-margin*2),top=height<240?44:58,bottom=height-43;
        boolean stacked=width<700;
        Rect map,summary;
        if(stacked){
            int available=Math.max(40,bottom-top),mapHeight=Math.max(24,Math.min(190,Math.min(available*2/5,available-8-55)));
            map=new Rect(margin,top,inner,mapHeight);
            summary=new Rect(margin,map.bottom()+8,inner,Math.max(20,bottom-map.bottom()-8));
        }else{
            int summaryWidth=Math.min(330,inner*2/5);
            map=new Rect(margin,top,inner-summaryWidth-12,Math.max(40,bottom-top));
            summary=new Rect(map.right()+12,top,summaryWidth,Math.max(40,bottom-top));
        }
        int backWidth=Math.min(86,inner/4),worldWidth=Math.min(120,inner/3);
        var back=new Rect(margin,height-32,backWidth,24);
        var world=new Rect(back.right()+6,height-32,worldWidth,24);
        var order=new Rect(world.right()+6,height-32,Math.max(1,width-margin-world.right()-6),24);
        return new CargoNavigatorLayout(map,summary,back,world,order,stacked);
    }
}
