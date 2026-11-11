package com.example.examplemod.client.screen;
import net.minecraft.client.gui.GuiGraphics;
/** Native 16px glyphs, matching the established GUI pixel grid; no font-symbol dependency. */
public final class BankIcons {
    public enum Icon { CARD, TRANSFER, REQUEST, RECEIPT, SAVINGS, LOCK }
    private BankIcons(){}
    public static void draw(GuiGraphics g,Icon icon,int x,int y){int brass=0xffd3a363,teal=0xff83c8bb,ivory=0xffe8e9e1;switch(icon){
        case CARD->{g.fill(x+1,y+3,x+15,y+13,brass);g.fill(x+2,y+4,x+14,y+12,0xff29565b);g.fill(x+2,y+5,x+14,y+7,teal);g.fill(x+3,y+9,x+6,y+11,brass);g.fill(x+10,y+10,x+13,y+11,ivory);}
        case TRANSFER->{g.fill(x+1,y+4,x+13,y+6,teal);g.fill(x+10,y+2,x+12,y+8,teal);g.fill(x+12,y+3,x+14,y+7,teal);g.fill(x+3,y+10,x+15,y+12,brass);g.fill(x+4,y+8,x+6,y+14,brass);g.fill(x+2,y+9,x+4,y+13,brass);}
        case REQUEST,RECEIPT->{g.fill(x+3,y+1,x+13,y+15,brass);g.fill(x+4,y+2,x+12,y+14,0xff28363b);for(int i=4;i<12;i+=3)g.fill(x+6,y+i,x+10,y+i+1,ivory);if(icon==Icon.REQUEST){g.fill(x+11,y+9,x+15,y+15,teal);g.fill(x+10,y+11,x+16,y+13,teal);}}
        case SAVINGS->{g.fill(x+2,y+6,x+14,y+14,brass);g.fill(x+3,y+7,x+13,y+13,0xff46675b);g.fill(x+6,y+3,x+10,y+5,brass);g.fill(x+5,y+9,x+11,y+11,teal);}
        case LOCK->{g.renderOutline(x+5,y+2,6,8,brass);g.fill(x+3,y+7,x+13,y+14,0xff4b6368);g.fill(x+7,y+9,x+9,y+12,brass);}
    }}
}
