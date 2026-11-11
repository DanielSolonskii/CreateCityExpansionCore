package com.example.examplemod.client.screen;

import java.util.List;
/** Shared paint/hit-test/scissor geometry, with a sequential layout at small GUI resolutions. */
public record SocialLayout(int width,int height,float scale,float x,float y,boolean wide) {
    public record Rect(int x,int y,int width,int height){public int right(){return x+width;}public int bottom(){return y+height;}public boolean contains(double a,double b){return a>=x&&a<right()&&b>=y&&b<bottom();}}
    public static SocialLayout calculate(int w,int h){boolean wide=w>=700;int width=wide?620:432;float s=Math.min(1,Math.min(Math.max(1,w-24)/(float)width,Math.max(1,h-24)/320f));int height=Math.clamp((int)((h-24)/s),320,410);return new SocialLayout(width,height,s,(w-width*s)/2,(h-height*s)/2,wide);}
    public Rect close(){return new Rect(width-36,10,24,22);}public Rect search(){return new Rect(width-150,10,106,22);}
    public Rect tab(int i){return tab(i,4);}
    public Rect tab(int i,int count){int w=(width-28)/count;return new Rect(14+i*w,46,w-4,24);}
    public Rect list(){return new Rect(14,82,wide?190:width-28,height-126);}
    public Rect detail(){return new Rect(wide?214:14,82,wide?width-228:width-28,height-126);}
    public Rect row(int i,int scroll){var r=list();return new Rect(r.x(),r.y()+i*42-scroll,r.width(),38);}
    public Rect action(int i,int count){var r=detail();int w=(r.width()-12)/Math.max(1,count);return new Rect(r.x()+i*w,r.y()+29,w-4,22);}
    public Rect mute(){var r=detail();return new Rect(r.right()-180,r.y(),74,22);}
    public Rect chatHeader(){var r=detail();return new Rect(r.x(),r.y(),r.width(),40);}
    public Rect chatBack(){var r=chatHeader();return new Rect(r.x()+6,r.y()+7,26,26);}
    public Rect chatMenu(){var r=chatHeader();return new Rect(r.right()-32,r.y()+7,26,26);}
    public Rect chatMenuItem(int index){var r=chatMenu();return new Rect(r.right()-164,r.bottom()+6+index*22,164,22);}
    public Rect messages(){var r=detail();return new Rect(r.x(),r.y()+44,r.width(),r.height()-44);}
    public Rect input(){var r=detail();return new Rect(r.x(),height-34,r.width()-64,24);}public Rect send(){var r=detail();return new Rect(r.right()-58,height-34,58,24);}
    public Rect previous(){return new Rect(14,height-34,26,24);}public Rect next(){return new Rect(46,height-34,26,24);}
    public Rect back(){return new Rect(14,10,30,22);}public Rect older(){var r=detail();return new Rect(r.right()-100,r.y(),100,22);}

    public record MessageSpan(long id, int top, int height) {}

    /** Preserve the first visible message that still exists after the server window moves. */
    public static int anchorMessages(int scroll, List<MessageSpan> previous, List<MessageSpan> current) {
        for (var old : previous) {
            if (old.top() + old.height() <= scroll) continue;
            for (var updated : current) {
                if (updated.id() == old.id()) {
                    return updated.top() + scroll - old.top();
                }
            }
        }
        return scroll;
    }

    public double localX(double v){return(v-x)/scale;}public double localY(double v){return(v-y)/scale;}
    public Rect screen(Rect r){int l=(int)Math.floor(x+r.x*scale),t=(int)Math.floor(y+r.y*scale);return new Rect(l,t,(int)Math.ceil(x+r.right()*scale)-l,(int)Math.ceil(y+r.bottom()*scale)-t);}
}
