package com.example.examplemod.client.logistics;

/** Pure native GUI geometry shared by rendering/widgets and responsive headless checks. */
public record CenterLayout(int sidebar,int navigationStep,Rect map,Rect previous,Rect next,Rect actions) {
    public record Rect(int x,int y,int width,int height){public int right(){return x+width;}public int bottom(){return y+height;}public boolean overlaps(Rect other){return x<other.right()&&right()>other.x&&y<other.bottom()&&bottom()>other.y;}}
    public static CenterLayout of(int width,int height){int sidebar=width<500?88:150,left=sidebar+18,y=height-54;return new CenterLayout(sidebar,Math.min(29,Math.max(18,(height-80)/9)),new Rect(left,y,58,22),new Rect(left+66,y,22,22),new Rect(left+90,y,22,22),new Rect(width-96,y,78,22));}
}
