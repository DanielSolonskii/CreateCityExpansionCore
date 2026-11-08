package com.example.examplemod.client.screen;
/** Shared shell, embedded page origins, drawer and input bounds. Pure, usable headlessly. */
public record SocialHubLayout(int height,float scale,float x,float y) {
    public static final int WIDTH=800,SIDEBAR=160,CONTENT_X=166,CONTENT_Y=46;
    public record Rect(int x,int y,int width,int height){public int right(){return x+width;}public int bottom(){return y+height;}public boolean contains(double a,double b){return a>=x&&b>=y&&a<right()&&b<bottom();}}
    public static SocialHubLayout calculate(int w,int h){float s=Math.min(1,Math.min(Math.max(1,w-24)/800f,Math.max(1,h-24)/470f));int height=Math.clamp((int)((h-24)/s),470,500);return new SocialHubLayout(height,s,(w-WIDTH*s)/2,(h-height*s)/2);}
    public Rect frame(){return new Rect(0,0,WIDTH,height);}public Rect content(){return new Rect(CONTENT_X,CONTENT_Y,WIDTH-CONTENT_X-10,height-CONTENT_Y-10);}
    public Rect nav(int i){return new Rect(10,112+i*35,SIDEBAR-20,30);}public Rect settings(){return new Rect(10,height-40,SIDEBAR-20,26);}public Rect search(){return new Rect(WIDTH-247,10,207,26);}public Rect close(){return new Rect(WIDTH-34,10,24,26);}public Rect back(){return new Rect(CONTENT_X,10,25,26);}
    public Rect balance(){return new Rect(10,height-70,SIDEBAR-20,23);}
    public Rect dailyGift(){return new Rect(WIDTH-285,10,28,26);}
    public Rect drawer(double progress){var c=content();return new Rect(c.right()-252+(int)Math.round((1-progress)*252),c.y(),252,c.height());}
    public Rect drawerAction(double progress,int index){var r=drawer(progress);return new Rect(r.x()+12,r.bottom()-210+index*32,r.width()-24,26);}
    public Rect inboxPage(boolean next){var c=content();return new Rect(c.right()-(next?41:75),c.y()+7,29,25);}
    public Rect inboxRow(int index,int scroll){var c=content();return new Rect(c.x()+12,c.y()+45+index*61-scroll,c.width()-24,55);}
    public float pageX(int width){return x+(content().x()+(content().width()-width)/2f)*scale;}public float pageY(){return y+content().y()*scale;}
    public int pageHeight(){return content().height();}
    public double localX(double v){return(v-x)/scale;}public double localY(double v){return(v-y)/scale;}
    public Rect screen(Rect r){int left=(int)Math.floor(x+r.x*scale),top=(int)Math.floor(y+r.y*scale);return new Rect(left,top,(int)Math.ceil(x+r.right()*scale)-left,(int)Math.ceil(y+r.bottom()*scale)-top);}
}
