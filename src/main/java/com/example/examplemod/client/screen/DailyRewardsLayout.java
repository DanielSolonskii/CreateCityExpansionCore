package com.example.examplemod.client.screen;
/** One geometry for card rendering, wood selection, actions and tooltips. */
public record DailyRewardsLayout(float x,float y,float scale) {
    public static final int WIDTH=512,HEIGHT=344;
    public static DailyRewardsLayout of(int width,int height){float scale=Math.min(1,Math.min(Math.max(1,width-20)/(float)WIDTH,Math.max(1,height-20)/(float)HEIGHT));return new DailyRewardsLayout((width-WIDTH*scale)/2,(height-HEIGHT*scale)/2,scale);}
    public SocialHubLayout.Rect card(int i){return new SocialHubLayout.Rect(20+i*68,92,64,118);}
    public SocialHubLayout.Rect wood(int i){return new SocialHubLayout.Rect(20+i*30,244,26,28);}
    public SocialHubLayout.Rect claim(){return new SocialHubLayout.Rect(308,286,184,28);}
    public SocialHubLayout.Rect later(){return new SocialHubLayout.Rect(20,286,132,28);}
    public SocialHubLayout.Rect close(){return new SocialHubLayout.Rect(WIDTH-34,12,22,24);}
    public double localX(double px){return(px-x)/scale;}public double localY(double py){return(py-y)/scale;}
}
