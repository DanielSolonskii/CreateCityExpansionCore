package com.example.examplemod.client.screen;
public record CommunityV2Layout(int height,float scale,float x,float y) {
    public static final int WIDTH=620;
    public static CommunityV2Layout calculate(int width,int height,SocialHubLayout hub){if(hub!=null)return new CommunityV2Layout(hub.pageHeight(),hub.scale(),hub.pageX(WIDTH),hub.pageY());float scale=Math.min(1,Math.min(Math.max(1,width-24)/(float)WIDTH,Math.max(1,height-24)/360f));int h=Math.clamp((int)((height-24)/scale),360,450);return new CommunityV2Layout(h,scale,(width-WIDTH*scale)/2,(height-h*scale)/2);}
    public SocialHubLayout.Rect body(){return new SocialHubLayout.Rect(12,122,WIDTH-24,height-162);}
    public SocialHubLayout.Rect tab(int index,int count){int width=(WIDTH-24)/count;return new SocialHubLayout.Rect(12+index*width,84,width-4,27);}
    public SocialHubLayout.Rect footer(int index,int count){int width=(WIDTH-24)/count;return new SocialHubLayout.Rect(12+index*width,height-31,width-5,23);}
    public double localX(double value){return(value-x)/scale;}public double localY(double value){return(value-y)/scale;}
    public SocialHubLayout.Rect screen(SocialHubLayout.Rect r){int a=(int)Math.floor(x+r.x()*scale),b=(int)Math.floor(y+r.y()*scale);return new SocialHubLayout.Rect(a,b,(int)Math.ceil(x+r.right()*scale)-a,(int)Math.ceil(y+r.bottom()*scale)-b);}
}
