package com.example.examplemod.client.screen;
public record CommunityLayout(int width,int height,float scale,float x,float y,boolean embedded) {
    public CommunityLayout(int width,int height,float scale,float x,float y){this(width,height,scale,x,y,false);}
    public record Rect(int x,int y,int width,int height){public int right(){return x+width;}public int bottom(){return y+height;}public boolean contains(double a,double b){return a>=x&&a<right()&&b>=y&&b<bottom();}}
    public static CommunityLayout calculate(int w,int h){int width=w>=700?620:432;float s=Math.min(1,Math.min(Math.max(1,w-24)/(float)width,Math.max(1,h-24)/330f));int height=Math.clamp((int)((h-24)/s),330,430);return new CommunityLayout(width,height,s,(w-width*s)/2,(h-height*s)/2);}
    public Rect close(){return new Rect(width-36,10,24,22);}public Rect settings(){return new Rect(width-126,10,82,22);}
    public Rect nav(int i){int w=(width-28)/5;return new Rect(14+i*w,46,w-4,25);}
    public Rect body(){return new Rect(14,embedded?47:84,width-28,height-(embedded?91:128));}public Rect row(int y,int h){return new Rect(body().x()+10,y,body().width()-20,h);}
    public Rect footer(boolean next){return new Rect(next?width-42:14,height-33,28,22);}
    public Rect membershipModal(){return new Rect(24,height/2-81,width-48,162);}
    public Rect membershipConfirm(boolean accept){var r=membershipModal();int w=(r.width()-30)/2;return new Rect(r.x()+10+(accept?w+10:0),r.bottom()-34,w,24);}
    public boolean hit(Rect r,boolean content,double x,double y){return r.contains(x,y)&&(!content||body().contains(x,y));}
    public double localX(double x){return(x-this.x)/scale;}public double localY(double y){return(y-this.y)/scale;}
    public Rect screen(Rect r){return screen(r,0);}
    public Rect screen(Rect r,float offset){int a=(int)Math.floor(x+r.x*scale),b=(int)Math.floor(y+(r.y+offset)*scale);return new Rect(a,b,(int)Math.ceil(x+r.right()*scale)-a,(int)Math.ceil(y+(r.bottom()+offset)*scale)-b);}
}
