package com.example.examplemod.client.screen;

import com.example.examplemod.client.map.CityMapPanel;
import com.example.examplemod.client.map.AtlasPlaceIcon;
import com.example.examplemod.places.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

final class AtlasPlaceEditor extends Screen {
    private final PolishedWorldMapScreen atlas;
    private final CityMapPanel map;
    private final UUID id;
    private final long version;
    private final AtlasPlace.Source source;
    private final boolean entranceOptional;
    private final boolean previouslyPublic;
    private String name,description,notice="";
    private AtlasPlace.Category category;
    private BlockPos icon;
    private final ArrayList<BlockPos> entrances;
    private boolean published,points,confirmDelete;
    private int selected;
    private UUID pending;
    private PlaceNetwork.Action action;
    private EditBox nameBox,noteBox;

    AtlasPlaceEditor(PolishedWorldMapScreen atlas,CityMapPanel map,AtlasPlace existing,CityMapPanel.Selection linked){
        this(atlas,map,existing,linked,null);
    }
    static AtlasPlaceEditor sharedAt(PolishedWorldMapScreen atlas,CityMapPanel map,BlockPos point){
        return new AtlasPlaceEditor(atlas,map,null,null,point);
    }
    private AtlasPlaceEditor(PolishedWorldMapScreen atlas,CityMapPanel map,AtlasPlace existing,CityMapPanel.Selection linked,BlockPos picked){
        super(AtlasPlaces.text(existing!=null?"edit":picked==null?"add":"shared_marker"));this.atlas=atlas;this.map=map;
        var player=net.minecraft.client.Minecraft.getInstance().player;
        this.id=existing==null?UUID.randomUUID():existing.id();this.version=existing==null?0:existing.version();
        this.source=existing==null?AtlasPlaces.source(linked,map.dimensionId()):existing.source();
        name=existing!=null?existing.name():linked==null?"":linked.title().getString();
        description=existing==null?"":existing.description();
        category=existing==null?AtlasPlace.Category.OTHER:existing.category();
        icon=existing!=null?existing.icon():linked!=null?linked.pos():picked!=null?picked:player.blockPosition();
        previouslyPublic=existing!=null && existing.published();
        published=picked!=null || previouslyPublic;
        entrances=new ArrayList<>(existing==null?List.of():existing.entrances());
        entranceOptional=picked!=null || existing!=null && source==null && entrances.isEmpty();
    }
    private Component tr(String key,Object... args){return AtlasPlaces.text(key,args);}
    private Button button(int x,int y,int w,Component label,Runnable run,boolean enabled){
        return button(x,y,w,label,run,enabled,false,false);
    }
    private Button button(int x,int y,int w,Component label,Runnable run,boolean enabled,boolean selected,boolean primary){
        var b=addRenderableWidget(AtlasFormStyle.button(x,y,w,18,label,run,selected,primary));
        b.active=enabled;return b;
    }
    private EditBox field(int x,int y,int w,String label,int max,String value,java.util.function.Consumer<String> change){
        var box=AtlasFormStyle.field(font,x,y,w,17,tr(label));box.setMaxLength(max);box.setValue(value);box.setResponder(change);
        box.setEditable(pending==null);return addRenderableWidget(box);
    }
    @Override protected void init(){
        int w=Math.min(320,width-34),x=(width-w)/2,h=(w-4)/2,y=57,foot=height-26;
        boolean enabled=pending==null;
        button(x,33,h,tr("details"),()->{points=false;rebuildWidgets();},enabled&&points,!points,false);
        var entryTab=button(x+h+4,33,w-h-4,tr(entranceOptional?"optional_entry_tab":"entry_tab",entrances.size()),
                ()->{points=true;rebuildWidgets();},enabled&&!points,points,false);
        if(entranceOptional)entryTab.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("optional_entry_help")));
        x+=6;w-=12;h=(w-4)/2;
        if(points){
            String pos=entrances.isEmpty()?"—":coords(entrances.get(selected));
            button(x,y+12,w,tr("selected_entry",pos),()->{selected=(selected+1)%entrances.size();rebuildWidgets();},enabled&&entrances.size()>1);
            button(x,y+34,h,tr("capture"),this::capture,enabled&&entrances.size()<AtlasPlace.MAX_ENTRANCES);
            button(x+h+4,y+34,w-h-4,tr("remove_entry"),()->{entrances.remove(selected);selected=0;rebuildWidgets();},enabled&&!entrances.isEmpty());
        } else {
            nameBox=field(x,y+11,w,"name",AtlasPlace.NAME,name,v->name=v);
            noteBox=field(x,y+40,w,"description",AtlasPlace.DESCRIPTION,description,v->description=v);
            button(x+20,y+61,h-20,tr("category."+AtlasPlaces.key(category)),()->{
                var kinds=AtlasPlace.Category.values();
                category=kinds[(category.ordinal()+1)%kinds.length];
                if(category==AtlasPlace.Category.STATION && (minecraft.getConnection()==null ||
                        !minecraft.getConnection().hasChannel(PlaceNetwork.CategorizedReply.TYPE))) category=kinds[0];
                rebuildWidgets();
            },enabled);
            boolean canPublish=previouslyPublic || minecraft.player!=null && minecraft.player.hasPermissions(2);
            var visibility=button(x+h+4,y+61,w-h-4,tr(published?"public":"personal"),()->{published=!published;rebuildWidgets();},enabled&&canPublish);
            if(!canPublish)visibility.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("status.publish_admin")));
            button(x,y+83,w,tr("icon_here",icon.getX(),icon.getZ()),()->{icon=minecraft.player.blockPosition();rebuildWidgets();},enabled&&source==null);
        }
        x-=6;w+=12;h=(w-4)/2;
        if(version>0)button(x,foot-23,w,tr(confirmDelete?"confirm_delete":"delete"),()->{
            if(!confirmDelete){confirmDelete=true;rebuildWidgets();}else submit(PlaceNetwork.Action.DELETE);
        },enabled);
        button(x,foot,h,tr(published && version==0?"publish":"save"),()->submit(PlaceNetwork.Action.SAVE),enabled,false,true);
        button(x+h+4,foot,w-h-4,tr("back"),this::onClose,true);
    }
    private static String coords(BlockPos p){return p.getX()+", "+p.getY()+", "+p.getZ();}
    private void capture(){
        var player=minecraft.player;
        if(player==null || !player.level().dimension().location().equals(map.dimensionId())){notice="dimension";return;}
        var feet=player.blockPosition();
        if(!entrances.contains(feet)){entrances.add(feet);selected=entrances.size()-1;}
        notice="";rebuildWidgets();
    }
    private void submit(PlaceNetwork.Action op){
        if(pending!=null||minecraft.player==null)return;
        try {
            if(op==PlaceNetwork.Action.SAVE && !entranceOptional && entrances.isEmpty()){notice="entrance";points=true;rebuildWidgets();return;}
            AtlasPlace value=op==PlaceNetwork.Action.DELETE?null:new AtlasPlace(id,minecraft.player.getUUID(),version,name,category,
                    description,map.dimensionId(),icon,entrances,published,source);
            action=op;pending=AtlasPlaces.mutate(map.dimensionId(),op,id,version,value);
            notice=pending==null?"unavailable":"waiting";
        }catch(IllegalArgumentException invalid){notice="invalid";}
        rebuildWidgets();
    }
    @Override public void tick(){
        if(version>0 && pending==null && AtlasPlaces.ready(map.dimensionId()) && AtlasPlaces.get(id,map.dimensionId())==null){
            atlas.closeAtlasTool();atlas.showCardNotice("unavailable");return;
        }
        String receipt=AtlasPlaces.receipt(pending);
        if(receipt==null)return;
        pending=null;
        if(receipt.equals("saved")){
            atlas.closeAtlasTool();
            if(action==PlaceNetwork.Action.SAVE)atlas.pinMapObject(AtlasPlaces.selection(id,map.dimensionId()));
            else map.clearSelection();
            return;
        }
        notice=receipt;rebuildWidgets();
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        int w=Math.min(320,width-34),x=(width-w)/2,y=57,foot=height-26;
        AtlasFormStyle.workspace(g,font,width,height,x,w,getTitle(),false);
        AtlasFormStyle.inset(g,x,y-4,w,Math.max(1,foot-y-(version>0?25:3)));
        x+=6;w-=12;
        if(points){
            g.drawString(font,tr("entry_help"),x,y,0xffc4d3d6,false);
            g.drawWordWrap(font,tr(entranceOptional?"optional_entry_help":"capture_help"),x,y+58,w,0xffa8bbbf);
        }else{
            AtlasPlaceIcon.forCategory(category).draw(g,x+1,y+62,16);
            g.drawString(font,tr("name"),x,y,0xffc4d3d6,false);g.drawString(font,tr("description"),x,y+29,0xffc4d3d6,false);
        }
        if(!notice.isEmpty()){int noticeY=foot-(version>0?45:20);g.drawWordWrap(font,tr("status."+notice),x,noticeY,w,0xffefd19e);}
        super.render(g,mx,my,delta);
    }
    @Override public void onClose(){atlas.closeAtlasTool();}
    @Override public boolean isPauseScreen(){return false;}
}
