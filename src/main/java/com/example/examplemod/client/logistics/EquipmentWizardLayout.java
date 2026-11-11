package com.example.examplemod.client.logistics;

/** One coordinate system for painting, scaled hit testing and keyboard focus. */
record EquipmentWizardLayout(double scale,double left,double top) {
    static final int WIDTH=760,HEIGHT=442;
    record Rect(int x,int y,int width,int height){int right(){return x+width;}int bottom(){return y+height;}boolean contains(double px,double py){return px>=x&&px<right()&&py>=y&&py<bottom();}}
    static final Rect MAIN=new Rect(24,150,448,218),SIDE=new Rect(488,150,248,218);
    static final Rect CLOSE=new Rect(24,390,110,30),RESELECT=new Rect(146,390,180,30),PRIMARY=new Rect(494,390,242,30);
    static EquipmentWizardLayout of(int width,int height){
        double scale=Math.min(1.25,Math.min((width-16d)/WIDTH,(height-16d)/HEIGHT));
        scale=Math.max(.1,scale);
        return new EquipmentWizardLayout(scale,(width-WIDTH*scale)/2,(height-HEIGHT*scale)/2);
    }
    double x(double screenX){return (screenX-left)/scale;}
    double y(double screenY){return (screenY-top)/scale;}
    double screenX(double x){return left+x*scale;}
    double screenY(double y){return top+y*scale;}
}
