package com.example.examplemod.client.screen;

/** Responsive visual blocks and their input geometry, without a running Minecraft client. */
public final class WorldMapVisualCheck {
    private static int checks;
    private static void require(boolean condition,String message) {
        checks++;
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        int[] modes={38,89,56,58},utilities={40,44,52,50,64,72,30,50};
        int[] tools={76,64,91,49,77};
        for(int width:new int[]{320,426,560,640,854,960,1280,1920}) {
            for(int height:new int[]{240,320,380,480,720,1080}) {
                var card=WorldMapUiLayout.contextPanel(width,height,modes,utilities,4000);
                var legend=WorldMapUiLayout.legend(width,height);
                var status=WorldMapUiLayout.statusStrip(width,height);
                require(WorldMapUiLayout.withinScreen(width,height,card),"card stays inside "+width+"x"+height);
                require(WorldMapUiLayout.withinScreen(width,height,legend),"legend stays inside viewport");
                require(!card.overlaps(legend)&&!card.overlaps(status)&&!legend.overlaps(status),"three right-column surfaces never overlap");
                require(card.x()==legend.x()&&card.width()==legend.width(),"context and legend share column edges");
                require(card.bottom()+WorldMapUiLayout.NORMAL<=legend.y(),"card clears legend");
                require(card.y()>=WorldMapUiLayout.EDGE+WorldMapUiLayout.TAB_HEIGHT+WorldMapUiLayout.NORMAL,"right column clears taller mode tabs");
                require(WorldMapUiLayout.popoverY(width,modes,utilities)>=WorldMapUiLayout.EDGE+WorldMapUiLayout.TAB_HEIGHT+WorldMapUiLayout.MICRO,"popovers clear all header controls");
                for(int mouseX:new int[]{0,width-1})for(int mouseY:new int[]{0,height-1}) {
                    var tooltip=WorldMapUiLayout.tooltip(width,height,mouseX,mouseY,4000,90);
                    require(WorldMapUiLayout.withinScreen(width,height,tooltip),"long tooltip stays within every viewport corner");
                }
                int mapWidth=WorldMapUiLayout.contextPanelLeft(width)-WorldMapUiLayout.EDGE-WorldMapUiLayout.NORMAL;
                require(mapWidth>=144,"narrow column still leaves a usable map");
                int top=WorldMapUiLayout.popoverY(width,modes,utilities)+WorldMapUiLayout.NORMAL;
                var buttons=WorldMapUiLayout.viewButtons(mapWidth,top,tools);
                for(int i=0;i<buttons.size();i++) {
                    var button=buttons.get(i);
                    require(button.right()<=card.x()-WorldMapUiLayout.NORMAL,"view controls stop before context column");
                    require(WorldMapUiLayout.withinScreen(width,height,button),"tool stays on screen");
                    for(int j=i+1;j<buttons.size();j++)require(!button.overlaps(buttons.get(j)),"tool hitboxes never overlap");
                }
                require(buttons.getLast().bottom()+WorldMapUiLayout.NORMAL<status.y()-WorldMapUiLayout.MICRO,"toolbar leaves positive map height");
                var track=WorldMapUiLayout.cardScrollTrack(card);
                var body=WorldMapUiLayout.cardBody(card);
                require(body.height()>=0&&body.y()>=card.y()&&body.bottom()<=card.bottom(),"body scissor cannot invert on a short card");
                require(body.height()>=WorldMapUiLayout.ACTION_HEIGHT,"short GUI retains a complete context action");
                var first=WorldMapUiLayout.cardScrollThumb(card,0,2000);
                var last=WorldMapUiLayout.cardScrollThumb(card,2000,2000);
                require(track.y()>=card.y()&&track.bottom()<=card.bottom(),"scroll track stays inside even a short card");
                require(first.y()==track.y()&&last.bottom()==track.bottom(),"thumb reaches both ends of long content");
                require(first.height()==last.height(),"scrolling never changes thumb height");
                var action=WorldMapUiLayout.cardAction(card,card.y()+WorldMapUiLayout.CARD_HEADER,0);
                var second=WorldMapUiLayout.cardAction(card,action.y(),1);
                require(action.x()==card.x()+WorldMapUiLayout.CARD_PADDING,"actions use shared card padding");
                require(!action.overlaps(second)&&action.contains(action.right()-1,action.bottom()-1),"painted actions and clicks share bounds");
                require(!action.contains(action.right(),action.bottom()),"action excludes neighbouring boundary");
                var row=WorldMapUiLayout.legendRow(legend,0,0);
                require(row.bottom()<=legend.bottom(),"short legend retains one complete scrollable row");
                require(row.x()==action.x()&&row.width()==action.width(),"legend and card share internal grid");
                require(WorldMapUiLayout.legendRow(legend,1,18).equals(row),"legend scroll uses same row geometry as hover");
            }
        }
        require(WorldMapUiLayout.modeTabsLabelled(640),"normal Minecraft GUI retains mode names");
        require(WorldMapUiLayout.viewButtonsCompact(160),"narrow map tools collapse to meaningful icons");
        System.out.println("PASS: "+checks+" WorldMap visual composition checks");
    }
}
