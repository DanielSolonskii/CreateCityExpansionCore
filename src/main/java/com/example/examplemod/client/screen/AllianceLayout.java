package com.example.examplemod.client.screen;
/** Shared local geometry for painting, fields, scrolling and hit testing. */
public record AllianceLayout(int width,int height,float scale,float x,float y) {
    public record Rect(int x,int y,int width,int height){public int right(){return x+width;}public int bottom(){return y+height;}public boolean contains(double px,double py){return px>=x&&py>=y&&px<right()&&py<bottom();}}
    public static AllianceLayout calculate(int w,int h){int width=560;float s=Math.min(1,Math.min(Math.max(1,w-24)/560f,Math.max(1,h-24)/390f));int height=Math.clamp((int)((h-24)/s),390,440);return new AllianceLayout(width,height,s,(w-width*s)/2,(h-height*s)/2);}
    public Rect close(){return new Rect(width-37,13,24,23);}public Rect sidebar(){return new Rect(12,57,151,height-98);}public Rect body(){return new Rect(174,91,width-186,height-132);}
    public Rect tab(int index){return new Rect(174+index*126,57,118,26);}
    public Rect sideRow(int index,int scroll){return new Rect(sidebar().x()+6,sidebar().y()+7+index*43-scroll,sidebar().width()-12,38);}
    public Rect row(int y,int h){return new Rect(body().x()+10,y,body().width()-20,h);}
    public Rect split(int y,int index,int count){var r=row(y,25);int w=(r.width()-(count-1)*6)/count;return new Rect(r.x()+index*(w+6),y,w,25);}
    public Rect footer(){return new Rect(174,height-32,width-186,22);}public Rect sideFooter(int index){return new Rect(12+index*78,height-32,73,22);}
    public Rect field(int index){return row(body().y()+27+index*45,22);}
    public Rect compose(){return new Rect(body().x()+10,body().bottom()-32,body().width()-106,23);}public Rect send(){return new Rect(compose().right()+6,compose().y(),80,23);}
    public Rect chat(){return new Rect(body().x(),body().y(),body().width(),body().height()-41);}
    public Rect modal(){return new Rect(110,height/2-65,340,130);}public Rect modalButton(int index){var r=modal();return new Rect(r.x()+12+index*160,r.bottom()-36,156,24);}
    public Rect scrollTrack(boolean side,boolean chat){var r=side?sidebar():chat?chat():body();return new Rect(r.right()-4,r.y()+3,2,r.height()-6);}
    public Rect scrollThumb(boolean side,boolean chat,int scroll,int contentHeight){var track=scrollTrack(side,chat);int h=Math.min(track.height(),Math.max(16,track.height()*track.height()/Math.max(1,contentHeight)));int max=Math.max(1,contentHeight-track.height());int y=track.y()+(int)((long)(track.height()-h)*Math.clamp(scroll,0,max)/max);return new Rect(track.x(),y,track.width(),h);}
    public double localX(double px){return(px-x)/scale;}public double localY(double py){return(py-y)/scale;}
    public Rect screen(Rect r,float offset){int a=(int)Math.floor(x+r.x*scale),b=(int)Math.floor(y+(r.y+offset)*scale);return new Rect(a,b,(int)Math.ceil(x+r.right()*scale)-a,(int)Math.ceil(y+(r.bottom()+offset)*scale)-b);}
}
