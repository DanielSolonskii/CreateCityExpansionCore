package com.example.examplemod.client.journey;

import com.example.examplemod.client.map.CityMapPanel;
import com.example.examplemod.journey.*;
import com.example.examplemod.journey.JourneyModel.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.function.Consumer;
import static com.example.examplemod.client.journey.JourneyAtlasStyle.*;

/** A map workspace: isolated drafts, searchable choices, no command strings or progress editing. */
public final class JourneyAtlasScreen extends Screen {
    private final Screen parent;
    public final CityMapPanel map;
    private final Map<String,CompoundTag> originals=new LinkedHashMap<>(), drafts=new LinkedHashMap<>();
    private final Map<String,String> revisions=new HashMap<>();
    private final List<Label> labels=new ArrayList<>();
    private final List<StepHit> stepHits=new ArrayList<>();
    private record Label(String text,int x,int y){}
    private record StepHit(int index,int y){}
    private record Choice(String id,String title){}
    private String group="marker",selected="",filter="",status="",pending="";
    private int tab,scroll,listScroll,formBottom,dragStep=-1;
    private boolean editing,loading,preview,pickPosition,drawRoute,showList,dragging;
    private long pendingAt;
    private boolean returning;
    private boolean routeEditorOpen;
    private final Set<String> invalidFields=new HashSet<>();
    private List<Choice> choices;
    private Consumer<String> choose;
    private String choiceFilter="";
    private int choiceScroll;
    private Runnable confirmation;
    private CompoundTag pointEditor;
    private String confirmationKey;
    private int left,right,rx,mapX,mapW;
    private final List<AbstractWidget> formWidgets=new ArrayList<>();

    public JourneyAtlasScreen(Screen parent,CityMapPanel map,String selected){super(JourneyClient.tr("atlas"));this.parent=parent;this.map=map;this.selected=selected==null?"":selected;}
    private String key(){return group+":"+selected;}
    private CompoundTag draft(){return drafts.get(key());}
    private static String title(CompoundTag t){return t.getString("Title");}
    private boolean dirty(String key){return !Objects.equals(drafts.get(key),originals.get(key));}
    private boolean dirty(){return drafts.keySet().stream().anyMatch(this::dirty);}
    private void send(String action,String group,String id,String base,String value){PacketDistributor.sendToServer(new JourneyEditorPayload(action,group,id,base,value));}
    private void startEditing(){editing=true;loading=true;status="loading";send("list","","","","");rebuildWidgets();}
    public void receive(CompoundTag t){
        String event=t.getString("Event"),k=t.getString("Group")+":"+t.getString("Id");
        switch(event){
            case "begin" -> {originals.clear();revisions.clear();}
            case "entry" -> {var value=t.getCompound("Value").copy();originals.put(k,value);revisions.put(k,t.getString("Revision"));drafts.putIfAbsent(k,value.copy());}
            case "ready" -> {loading=false;status="";if(!selected.isEmpty()&&draft()==null)selected="";rebuildWidgets();}
            case "saved" -> {pointEditor=null;var value=t.getCompound("Value").copy();if(value.isEmpty()){originals.remove(k);drafts.remove(k);revisions.remove(k);selected="";}else{originals.put(k,value);drafts.put(k,value.copy());revisions.put(k,t.getString("Revision"));}pending="";status="saved";rebuildWidgets();}
            case "error" -> {pending="";loading=false;status="error."+t.getString("Error");rebuildWidgets();}
        }
    }
    public boolean previewing(){return preview;}
    public List<Marker> expedition(){var result=new ArrayList<Marker>();if(!group.equals("chain")||selected.isEmpty())return result;var c=editing?draft():JourneyClient.chains.stream().filter(v->v.getString("Id").equals(selected)).findFirst().orElse(null);if(c==null)return result;for(String id:JourneyCodec.strings(c,"Steps")){var q=editing?drafts.get("quest:"+id):JourneyClient.quests.stream().filter(v->v.getString("Id").equals(id)).findFirst().orElse(null);if(q==null)continue;String marker=q.getString("Marker");for(var m:displayMarkers())if(m.id.equals(marker)){result.add(m);break;}}return result;}
    public Collection<Marker> displayMarkers(){
        if(!editing)return JourneyClient.markers.values();var result=new ArrayList<Marker>();
        for(var entry:drafts.entrySet())if(entry.getKey().startsWith("marker:"))try{var m=JourneyCodec.marker(entry.getValue());if(!preview||m.enabled&&m.afterQuest.isEmpty())result.add(m);}catch(RuntimeException ignored){}
        return result;
    }
    public String selectedMarker(){if(group.equals("marker"))return selected;var d=draft();return d==null?"":d.getString("Marker");}
    public void selectMarker(String id){if(!pending.isEmpty())return;group="marker";selected=id;tab=scroll=0;pickPosition=drawRoute=false;pointEditor=null;rebuildWidgets();}
    private void layout(){left=width>=700?170:width>=540?135:0;right=Math.min(246,Math.max(174,width*2/5));rx=width-right;mapX=left;mapW=rx-left;}
    private Button button(int x,int y,int w,String key,Runnable action){var b=JourneyAtlasStyle.button(x,y,w,key,action);addRenderableWidget(b);return b;}
    private void formButton(int y,String label,Runnable action){if(y>=92&&y+20<=height-82){var b=new FlatButton(rx+12,y,right-24,20,Component.literal(label),action);addRenderableWidget(b);formWidgets.add(b);}formBottom=Math.max(formBottom,y+scroll+24);}
    private void field(int y,String label,String value,int max,Consumer<String> change){
        formBottom=Math.max(formBottom,y+scroll+38);if(y<92||y+34>height-82)return;
        labels.add(new Label(JourneyClient.tr(label).getString(),rx+12,y));
        var e=new EditBox(font,rx+16,y+16,right-32,14,JourneyClient.tr(label));e.setBordered(false);e.setTextColor(TEXT);e.setMaxLength(max);e.setValue(value);e.setResponder(change);addRenderableWidget(e);formWidgets.add(e);
    }
    private void descriptionField(int y){var d=draft();int boxHeight=Math.min(50,Math.max(24,height-214));formBottom=Math.max(formBottom,y+scroll+boxHeight+26);if(y<92||y+boxHeight+18>height-82)return;labels.add(new Label(JourneyClient.tr("description").getString(),rx+12,y));var box=new MultiLineEditBox(font,rx+12,y+14,right-24,boxHeight,JourneyClient.tr("description"),JourneyClient.tr("description")){@Override protected void renderBackground(GuiGraphics g){g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),RAISED);g.renderOutline(getX(),getY(),getWidth(),getHeight(),isFocused()?BRASS:EDGE);}};box.setCharacterLimit(512);box.setValue(d.getString("Description"));box.setValueListener(v->d.putString("Description",v));addRenderableWidget(box);formWidgets.add(box);}
    private void stringField(int y,String label,String name,int max){var d=draft();field(y,label,d.getString(name),max,s->d.putString(name,s));}
    private void numberField(int y,String label,String name,int low,int high){var d=draft();field(y,label,Integer.toString(d.getInt(name)),9,s->{try{int n=Integer.parseInt(s);if(n>=low&&n<=high){d.putInt(name,n);invalidFields.remove(key()+name);status="";}else{invalidFields.add(key()+name);status="error.range";}}catch(NumberFormatException e){invalidFields.add(key()+name);status="error.range";}});}
    private void toggle(int y,String label,String name){var d=draft();formButton(y,JourneyClient.tr(label).getString()+": "+JourneyClient.tr(d.getBoolean(name)?"yes":"no").getString(),()->{d.putBoolean(name,!d.getBoolean(name));rebuildWidgets();});}
    private List<Choice> named(String type,boolean empty){var result=new ArrayList<Choice>();if(empty)result.add(new Choice("",JourneyClient.tr("none").getString()));for(var e:drafts.entrySet())if(e.getKey().startsWith(type+":"))result.add(new Choice(e.getValue().getString("Id"),title(e.getValue())));return result;}
    private void picker(List<Choice> options,Consumer<String> action){choices=options;choose=action;choiceFilter="";choiceScroll=0;rebuildWidgets();}
    private String namedTitle(String type,String id){var d=drafts.get(type+":"+id);return id.isEmpty()?JourneyClient.tr("none").getString():d==null?id:title(d);}
    private void reference(int y,String label,String name,String type){var d=draft();formButton(y,JourneyClient.tr(label).getString()+": "+namedTitle(type,d.getString(name)),()->picker(named(type,true),id->{d.putString(name,id);rebuildWidgets();}));}
    @Override protected void init(){
        layout();labels.clear();formWidgets.clear();stepHits.clear();formBottom=0;
        if(confirmation!=null){button(width/2-112,height/2+12,108,"discard",()->{var action=confirmation;confirmation=null;action.run();});button(width/2+4,height/2+12,108,"cancel",()->{confirmation=null;rebuildWidgets();});return;}
        if(choices!=null){var search=new EditBox(font,width/2-130,55,260,18,JourneyClient.tr("search"));search.setMaxLength(96);search.setValue(choiceFilter);search.setResponder(s->{choiceFilter=s;choiceScroll=0;});addRenderableWidget(search);button(width/2+60,height-35,70,"cancel",()->{choices=null;rebuildWidgets();});setInitialFocus(search);return;}
        button(8,8,72,"close",this::onClose);
        if(JourneyClient.canEdit&&!editing){
            button(86,8,100,"route_editor.open",this::openRouteEditor);
            button(192,8,104,"route_editor.advanced",this::startEditing);
        }
        if(editing){button(86,8,100,preview?"preview_exit":"preview",()->{preview=!preview;rebuildWidgets();});button(192,8,78,"new",this::create);}
        int categoryWidth=Math.min(80,(width-(left==0?102:24))/3);button(8,33,categoryWidth,"markers",()->switchGroup("marker"));button(12+categoryWidth,33,categoryWidth,"quests",()->switchGroup("quest"));button(16+2*categoryWidth,33,categoryWidth,"chains",()->switchGroup("chain"));
        if(left==0)button(width-86,33,78,showList?"properties":"list",()->{showList=!showList;rebuildWidgets();});
        if(left>0||showList){int x=left>0?10:rx+12,w=left>0?left-20:right-24;var search=new EditBox(font,x,69,w,16,JourneyClient.tr("search"));search.setBordered(false);search.setMaxLength(96);search.setValue(filter);search.setHint(JourneyClient.tr("search"));search.setResponder(s->{filter=s;listScroll=0;});addRenderableWidget(search);}
        if(showList&&left==0)return;
        if(!editing){initViewer();return;}
        if(draft()==null||loading)return;
        String[] tabs=group.equals("marker")?new String[]{"basic","appearance","actions","availability","path"}:group.equals("quest")?new String[]{"basic","conditions"}:new String[]{"basic","steps"};
        tab=Math.min(tab,tabs.length-1);int tw=(right-16)/tabs.length;
        for(int i=0;i<tabs.length;i++){int index=i;var b=button(rx+8+i*tw,66,tw-2,"tab_"+tabs[i],()->{tab=index;scroll=0;rebuildWidgets();});b.setTooltip(Tooltip.create(JourneyClient.tr("tab_"+tabs[i])));}
        int y=96-scroll;var d=draft();
        if(tab==0){stringField(y,"name","Title",96);y+=40;if(!group.equals("chain")){descriptionField(y);y+=78;}
            if(group.equals("marker")){
                formButton(y,JourneyClient.tr("type").getString()+": "+JourneyClient.tr("kind."+d.getString("Kind")).getString(),()->picker(Arrays.stream(Kind.values()).map(k->new Choice(k.name(),JourneyClient.tr("kind."+k.name()).getString())).toList(),id->{d.putString("Kind",id);d.putInt("Icon",Kind.valueOf(id).ordinal());rebuildWidgets();}));y+=28;
                formButton(y,JourneyClient.tr("my_position").getString(),()->{var p=minecraft.player;if(p!=null){d.merge(JourneyCodec.point(new Point(p.level().dimension().location().toString(),p.blockPosition().getX(),p.blockPosition().getY(),p.blockPosition().getZ())));d.put("Route",new ListTag());rebuildWidgets();}});y+=28;
                formButton(y,JourneyClient.tr("pick_map").getString(),()->{pickPosition=true;drawRoute=false;status="pick_hint";});y+=28;
                formButton(y,JourneyClient.tr("dimension_name").getString()+": "+d.getString("Dimension"),()->{var dims=new TreeSet<String>();dims.add(d.getString("Dimension"));for(var dim:com.example.examplemod.client.CityPublicDirectory.dimensions)dims.add(dim.toString());if(minecraft.level!=null)dims.add(minecraft.level.dimension().location().toString());picker(dims.stream().map(id->new Choice(id,id)).toList(),id->{d.putString("Dimension",id);d.put("Route",new ListTag());rebuildWidgets();});});y+=28;numberField(y,"x","X",-30000000,30000000);y+=40;numberField(y,"y","Y",-2048,2048);y+=40;numberField(y,"z","Z",-30000000,30000000);y+=40;numberField(y,"arrival_radius","Radius",1,64);
            }else if(group.equals("quest")){reference(y,"attached_marker","Marker","marker");y+=28;toggle(y,"published","Enabled");}
            else {toggle(y,"published","Enabled");y+=28;toggle(y,"automatic","Automatic");}
        }else if(group.equals("marker")){
            if(tab==1){formButton(y,JourneyClient.tr("icon").getString()+": "+JourneyClient.tr("icon."+d.getInt("Icon")).getString(),()->picker(java.util.stream.IntStream.range(0,8).mapToObj(i->new Choice(""+i,JourneyClient.tr("icon."+i).getString())).toList(),id->{d.putInt("Icon",Integer.parseInt(id));rebuildWidgets();}));y+=28;
                formButton(y,JourneyClient.tr("color").getString(),()->picker(List.of(new Choice("0",JourneyClient.tr("auto_color").getString()),new Choice("13675891",JourneyClient.tr("brass").getString()),new Choice("9566628",JourneyClient.tr("sage").getString()),new Choice("9422073",JourneyClient.tr("blue").getString()),new Choice("13083086",JourneyClient.tr("lilac").getString())),id->{d.putInt("Tint",Integer.parseInt(id));rebuildWidgets();}));y+=28;
                numberField(y,"size","Size",8,16);y+=40;numberField(y,"range","Range",16,160);y+=40;toggle(y,"on_map","OnMap");y+=28;toggle(y,"in_world","InWorld");
            }else if(tab==2){formButton(y,JourneyClient.tr("create_quest_here").getString(),()->{String marker=selected;switchGroup("quest");create();draft().putString("Marker",marker);rebuildWidgets();});y+=28;
                for(var q:named("quest",false))if(drafts.get("quest:"+q.id).getString("Marker").equals(selected)){formButton(y,q.title,()->{switchGroup("quest");selected=q.id;rebuildWidgets();});y+=28;}
            }else if(tab==3){toggle(y,"published","Enabled");y+=28;toggle(y,"newcomers","Newcomers");y+=28;reference(y,"after_quest","AfterQuest","quest");}
            else if(pointEditor!=null){var point=pointEditor;formButton(y,JourneyClient.tr("back_to_path").getString(),()->{pointEditor=null;scroll=0;rebuildWidgets();});y+=28;for(String axis:List.of("X","Y","Z")){String a=axis;field(y,axis.toLowerCase(Locale.ROOT),""+point.getInt(axis),9,v->{try{int n=Integer.parseInt(v);if((a.equals("Y")&&n>=-2048&&n<=2048)||(!a.equals("Y")&&Math.abs((long)n)<=30000000)){point.putInt(a,n);invalidFields.remove(key()+"point"+a);}else invalidFields.add(key()+"point"+a);}catch(NumberFormatException ex){invalidFields.add(key()+"point"+a);}});y+=40;}formButton(y,JourneyClient.tr("remove").getString(),()->{d.getList("Route",10).remove(point);pointEditor=null;scroll=0;rebuildWidgets();});}
            else {formButton(y,JourneyClient.tr(drawRoute?"finish_path":"draw_path").getString(),()->{drawRoute=!drawRoute;pickPosition=false;status=drawRoute?"path_hint":"";});y+=28;
                formButton(y,JourneyClient.tr("undo_point").getString(),()->{var points=d.getList("Route",10);if(!points.isEmpty())points.removeLast();rebuildWidgets();});y+=28;
                formButton(y,JourneyClient.tr("clear_path").getString(),()->{d.put("Route",new ListTag());rebuildWidgets();});y+=28;
                int index=0;for(var raw:d.getList("Route",10)){int n=index++;var point=(CompoundTag)raw;formButton(y,(n+1)+" · "+point.getInt("X")+" / "+point.getInt("Y")+" / "+point.getInt("Z"),()->editPoint(point));y+=26;}
            }
        }else if(group.equals("quest")){
            var objectives=d.getList("Objectives",10);
            formButton(y,JourneyClient.tr("add_condition").getString(),()->{if(objectives.size()>=16)return;var o=new CompoundTag();o.putString("Id",UUID.randomUUID().toString());o.putString("Kind","VISIT");o.putString("Target",d.getString("Marker"));o.putInt("Count",1);objectives.add(o);d.put("Objectives",objectives);rebuildWidgets();});y+=30;
            for(var raw:objectives){var o=(CompoundTag)raw;
                formButton(y,JourneyClient.tr("condition."+o.getString("Kind")).getString(),()->picker(Arrays.stream(ObjectiveKind.values()).map(k->new Choice(k.name(),JourneyClient.tr("condition."+k.name()).getString())).toList(),id->{o.putString("Kind",id);o.putString("Target","");o.putInt("Count",1);rebuildWidgets();}));y+=26;
                String kind=o.getString("Kind");if(kind.equals("VISIT")||kind.equals("INTERACT")||kind.equals("OPEN_PYLON")){formButton(y,namedTitle("marker",o.getString("Target")),()->picker(named("marker",false),id->{o.putString("Target",id);rebuildWidgets();}));y+=26;}
                else if(kind.equals("ITEM")){var resource=net.minecraft.resources.ResourceLocation.tryParse(o.getString("Target"));String itemName=resource==null?JourneyClient.tr("choose_item").getString():net.minecraft.core.registries.BuiltInRegistries.ITEM.get(resource).getDescription().getString();formButton(y,itemName,()->picker(net.minecraft.core.registries.BuiltInRegistries.ITEM.stream().filter(item->item!=net.minecraft.world.item.Items.AIR).map(item->new Choice(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString(),item.getDescription().getString())).sorted(Comparator.comparing(Choice::title)).toList(),id->{o.putString("Target",id);rebuildWidgets();}));y+=28;}else if(kind.equals("ADVANCEMENT")){field(y,"advancement_id",o.getString("Target"),128,s->o.putString("Target",s));y+=40;}
                if(kind.equals("ITEM")||kind.equals("SHIPMENT_DELIVERED")||kind.equals("PROJECT_COMPLETED")){field(y,"count",""+o.getInt("Count"),4,s->{try{int n=Integer.parseInt(s);if(n>=1&&n<=4096)o.putInt("Count",n);}catch(NumberFormatException ignored){}});y+=40;}
                formButton(y,JourneyClient.tr("remove_condition").getString(),()->{objectives.remove(o);rebuildWidgets();});y+=34;
            }
        }else{
            var steps=JourneyCodec.strings(d,"Steps");formButton(y,JourneyClient.tr("add_step").getString(),()->picker(named("quest",false).stream().filter(c->!steps.contains(c.id)).toList(),id->{if(steps.size()<32){steps.add(id);d.put("Steps",JourneyCodec.strings(steps));rebuildWidgets();}}));y+=30;
            for(int i=0;i<steps.size();i++){int n=i;String id=steps.get(i);if(y>=92&&y+20<=height-82)stepHits.add(new StepHit(i,y));formButton(y,(i+1)+"  ≡  "+namedTitle("quest",id),()->{});y+=24;
                if(y>=92&&y+20<=height-82){int by=y;int bw=(right-28)/3;button(rx+12,by,bw,"up",()->moveStep(n,n-1));button(rx+14+bw,by,bw,"down",()->moveStep(n,n+1));button(rx+16+2*bw,by,bw,"remove",()->{steps.remove(n);d.put("Steps",JourneyCodec.strings(steps));rebuildWidgets();});}y+=28;formBottom=Math.max(formBottom,y+scroll);
            }
        }
        int bw=(right-28)/3;button(rx+10,height-48,bw,"save",this::save).active=pending.isEmpty();button(rx+14+bw,height-48,bw,"revert",this::revert);button(rx+18+2*bw,height-48,bw,"more",()->picker(List.of(new Choice("copy",JourneyClient.tr("duplicate").getString()),new Choice("delete",JourneyClient.tr("delete").getString())),id->{if(id.equals("copy"))duplicate();else confirm("confirm_delete",()->submit("delete"));}));
        if(!pending.isEmpty())for(var widget:children())if(widget instanceof AbstractWidget w)w.active=false;
    }
    private void initViewer(){
        if(group.equals("marker")){var m=JourneyClient.markers.get(selected);if(m!=null){button(rx+12,height-74,right-24,"related",()->picker(JourneyClient.quests.stream().filter(q->q.getString("Marker").equals(m.id)||q.getList("Objectives",10).stream().anyMatch(v->((CompoundTag)v).getString("Target").equals(m.id))).map(q->new Choice(q.getString("Id"),title(q))).toList(),id->{group="quest";selected=id;scroll=0;rebuildWidgets();}));button(rx+12,height-48,right-24,"track_place",()->JourneyClient.request(JourneyRequestPayload.Action.TRACK_MARKER,m.id));}}
        else if(group.equals("quest")){var q=JourneyClient.quests.stream().filter(v->v.getString("Id").equals(selected)).findFirst().orElse(null);if(q!=null)button(rx+12,height-48,right-24,q.getBoolean("Available")?"track":"locked",()->JourneyClient.request(JourneyRequestPayload.Action.TRACK_QUEST,selected)).active=q.getBoolean("Available");}
        else if(!selected.isEmpty())button(rx+12,height-48,right-24,"start",()->JourneyClient.request(JourneyRequestPayload.Action.START,selected));
    }
    private void editPoint(CompoundTag point){pointEditor=point;scroll=0;drawRoute=false;rebuildWidgets();}
    private void moveStep(int from,int to){var d=draft();var steps=JourneyCodec.strings(d,"Steps");if(from<0||from>=steps.size()||to<0||to>=steps.size())return;steps.add(to,steps.remove(from));d.put("Steps",JourneyCodec.strings(steps));rebuildWidgets();}
    private void switchGroup(String next){if(!pending.isEmpty())return;pointEditor=null;group=next;selected="";tab=scroll=listScroll=0;filter="";pickPosition=drawRoute=false;rebuildWidgets();}
    private void create(){if(!editing||loading||!pending.isEmpty())return;String id=group+"_"+UUID.randomUUID().toString().substring(0,8);CompoundTag d;
        if(group.equals("marker")){var p=minecraft.player;if(p==null)return;var m=new Marker(id,new Point(map.dimensionId().toString(),(int)map.centerX(),p.blockPosition().getY(),(int)map.centerZ()));m.enabled=false;d=JourneyCodec.marker(m);}
        else if(group.equals("quest"))d=JourneyCodec.quest(new Quest(id));else d=JourneyAuthoring.chain(new Chain(id));
        d.putString("Title",JourneyClient.tr("new_"+group).getString());pointEditor=null;selected=id;drafts.put(key(),d);tab=scroll=0;showList=false;rebuildWidgets();
    }
    private void duplicate(){pointEditor=null;var value=draft().copy();String id=group+"_"+UUID.randomUUID().toString().substring(0,8);value.putString("Id",id);value.putString("Title",title(value).substring(0,Math.min(86,title(value).length()))+" · "+JourneyClient.tr("copy").getString());value.putBoolean("Enabled",false);if(group.equals("chain")){value.putBoolean("Automatic",false);value.put("Steps",new ListTag());}selected=id;drafts.put(key(),value);rebuildWidgets();}
    private void revert(){pointEditor=null;invalidFields.removeIf(k->k.startsWith(key()));if(originals.containsKey(key()))drafts.put(key(),originals.get(key()).copy());else{drafts.remove(key());selected="";}status="";rebuildWidgets();}
    private void save(){if(invalidFields.stream().anyMatch(k->k.startsWith(key()))){status="error.range";return;}submit("save");}
    private void submit(String action){if(draft()==null||!pending.isEmpty())return;if(action.equals("delete")&&!originals.containsKey(key())){revert();return;}String value=action.equals("save")?draft().toString():"";if(value.length()>24000||value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>30000){status="error.limit";return;}pending=key();pendingAt=net.minecraft.Util.getMillis();status="saving";send(action,group,selected,revisions.getOrDefault(key(),""),value);rebuildWidgets();}
    private void confirm(String key,Runnable action){confirmationKey=key;confirmation=action;rebuildWidgets();}
    @Override public void tick(){if(parent instanceof com.example.examplemod.client.screen.PolishedWorldMapScreen atlas)atlas.tickJourneyWorkspace();if(!pending.isEmpty()&&net.minecraft.Util.getMillis()-pendingAt>10000){pending="";status="error.timeout";rebuildWidgets();}}
    private List<Choice> entries(){
        List<Choice> result;
        if(editing)result=named(group,false);
        else if(group.equals("marker"))result=JourneyClient.markers.values().stream().map(m->new Choice(m.id,m.title)).toList();
        else result=(group.equals("quest")?JourneyClient.quests:JourneyClient.chains).stream().map(t->new Choice(t.getString("Id"),title(t))).toList();
        String query=filter.toLowerCase(Locale.ROOT);return result.stream().filter(c->c.title.toLowerCase(Locale.ROOT).contains(query)||c.id.contains(query)).toList();
    }
    private List<Choice> filteredChoices(){String query=choiceFilter.toLowerCase(Locale.ROOT);return choices.stream().filter(c->c.title.toLowerCase(Locale.ROOT).contains(query)||c.id.toLowerCase(Locale.ROOT).contains(query)).toList();}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xff10171b);map.render(g,mapX,60,mapW,Math.max(20,height-84),mx,my);
        panel(g,rx,60,right,height-84);if(left>0)panel(g,0,60,left,height-84);
        g.fill(0,0,width,59,SURFACE);g.fill(0,58,width,59,BRASS);
        if(width>520)g.drawString(font,JourneyClient.tr(editing?"editor_title":"atlas"),width-250,14,BRASS,false);
        if(left>0||showList){int x=left>0?8:rx+10,w=left>0?left-16:right-20;var list=entries();int rows=Math.max(1,(height-133)/32);listScroll=Math.clamp(listScroll,0,Math.max(0,list.size()-rows));
            for(int i=listScroll;i<Math.min(list.size(),listScroll+rows);i++){var c=list.get(i);int y=94+(i-listScroll)*32;boolean active=c.id.equals(selected);g.fill(x,y,x+w,y+28,active?0xff35413f:RAISED);if(active)g.fill(x,y,x+2,y+28,BRASS);g.drawString(font,font.plainSubstrByWidth(c.title,w-14),x+7,y+5,TEXT,false);String detail=editing&&dirty(group+":"+c.id)?JourneyClient.tr("draft").getString():c.id;g.drawString(font,font.plainSubstrByWidth(detail,w-14),x+7,y+17,MUTED,false);}
            if(list.isEmpty())g.drawString(font,JourneyClient.tr("empty"),x+5,100,MUTED,false);
        }
        if(!(showList&&left==0)){
            if(editing){g.enableScissor(rx+4,90,width-4,height-81);for(var label:labels){g.drawString(font,label.text,label.x,label.y,MUTED,false);g.fill(label.x,label.y+12,width-12,label.y+34,RAISED);g.fill(label.x,label.y+33,width-12,label.y+34,EDGE);}g.disableScissor();}
            else renderDetails(g);
            if(selected.isEmpty())g.drawWordWrap(font,JourneyClient.tr(editing?"editor_hint":"select_hint"),rx+14,104,right-28,MUTED);
        }
        String hint=!status.isEmpty()?JourneyClient.tr(status).getString():preview?JourneyClient.tr("preview_hint").getString():JourneyClient.tr("map_hint").getString();
        g.drawString(font,font.plainSubstrByWidth(hint,width-18),9,height-17,MUTED,false);
        if(editing&&draft()!=null&&!showList&&pending.isEmpty()){int cy=height-65;g.drawString(font,JourneyClient.tr(dirty(key())?"unsaved":"synced"),rx+12,cy,dirty(key())?BRASS:MUTED,false);}
        if(choices!=null){g.fill(0,0,width,height,0xb010171b);panel(g,width/2-142,40,284,height-66);var options=filteredChoices();int rows=Math.max(1,(height-130)/24);choiceScroll=Math.clamp(choiceScroll,0,Math.max(0,options.size()-rows));for(int i=choiceScroll;i<Math.min(options.size(),choiceScroll+rows);i++){int y=82+(i-choiceScroll)*24;g.fill(width/2-132,y,width/2+132,y+22,my>=y&&my<y+22?0xff35413f:RAISED);g.drawString(font,font.plainSubstrByWidth(options.get(i).title,250),width/2-125,y+7,TEXT,false);}}
        if(confirmation!=null){g.fill(0,0,width,height,0xc010171b);panel(g,width/2-128,height/2-48,256,98);g.drawWordWrap(font,JourneyClient.tr(confirmationKey),width/2-116,height/2-35,232,TEXT);}
        super.render(g,mx,my,delta);
    }
    private void renderDetails(GuiGraphics g){
        String name="",description="";var lines=new ArrayList<Component>();
        if(group.equals("marker")){var m=JourneyClient.markers.get(selected);if(m==null)return;name=m.title;description=m.description;lines.add(Component.literal(m.point.x()+" / "+m.point.y()+" / "+m.point.z()));lines.add(JourneyClient.tr(m.route.isEmpty()?"direction":"route"));for(var q:JourneyClient.quests)if(q.getString("Marker").equals(m.id))lines.add(Component.literal(q.getString("Title")));}
        else {var q=(group.equals("quest")?JourneyClient.quests:JourneyClient.chains).stream().filter(t->t.getString("Id").equals(selected)).findFirst().orElse(null);if(q==null)return;name=title(q);description=q.getString("Description");for(var raw:q.getList("Objectives",10)){var o=(CompoundTag)raw;var marker=JourneyClient.markers.get(o.getString("Target"));lines.add(JourneyClient.tr("objective."+o.getString("Kind").toLowerCase(Locale.ROOT),marker==null?o.getString("Target"):marker.title,o.getInt("Count")));}if(group.equals("chain"))lines.add(Component.literal(q.getInt("Done")+" / "+q.getInt("Total")));}
        lines.addFirst(Component.literal(description));lines.addFirst(Component.literal(name));int y=76-scroll;g.enableScissor(rx+10,68,width-10,height-55);for(var text:lines){for(var line:font.split(text,right-28)){g.drawString(font,line,rx+14,y,TEXT,false);y+=12;}y+=12;}g.disableScissor();formBottom=y+scroll;
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(confirmation!=null)return super.mouseClicked(x,y,button);
        if(choices!=null){if(button==0&&x>=width/2-132&&x<=width/2+132&&y>=82&&y<height-48){int i=choiceScroll+((int)y-82)/24;var values=filteredChoices();if(i<values.size()){var id=values.get(i).id;var action=choose;choices=null;action.accept(id);rebuildWidgets();return true;}}return super.mouseClicked(x,y,button);}
        if(!pending.isEmpty())return true;
        if(button==0&&(left>0&&x<left||showList&&x>=rx)&&y>=94&&y<height-38){int i=listScroll+((int)y-94)/32;var entries=entries();if(i<entries.size()){pointEditor=null;selected=entries.get(i).id;scroll=tab=0;showList=false;pickPosition=drawRoute=false;rebuildWidgets();return true;}}
        if(button==0&&group.equals("chain")&&tab==1&&x>=rx)for(var hit:stepHits)if(y>=hit.y&&y<hit.y+20){dragStep=hit.index;return true;}
        if(super.mouseClicked(x,y,button))return true;
        if(x>=mapX&&x<rx&&y>=60&&y<height-24){
            if(button==0&&(pickPosition||drawRoute)&&draft()!=null&&group.equals("marker")){
                var d=draft();if(!d.getString("Dimension").equals(map.dimensionId().toString())){status="error.dimension";return true;}
                var p=new Point(map.dimensionId().toString(),(int)Math.floor(map.screenToWorldX(x)),d.getInt("Y"),(int)Math.floor(map.screenToWorldZ(y)));
                if(pickPosition){d.merge(JourneyCodec.point(p));pickPosition=false;status="";}else{var points=d.getList("Route",10);if(points.size()<64){points.add(JourneyCodec.point(p));d.put("Route",points);}else status="error.limit";}rebuildWidgets();return true;
            }
            if(button==0&&JourneyMapLayer.click(map,x,y))return true;
            if(button==0){dragging=true;return true;}
        }
        return false;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(dragStep>=0)return true;if(dragging&&button==0){map.restoreView(map.centerX()-dx/map.zoom(),map.centerZ()-dy/map.zoom(),map.zoom());return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){dragging=false;if(dragStep>=0){int from=dragStep;dragStep=-1;for(var h:stepHits)if(y>=h.y&&y<h.y+44){moveStep(from,h.index);break;}return true;}return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
        if(confirmation!=null)return true;if(choices!=null){choiceScroll=Math.max(0,choiceScroll-(int)dy);return true;}
        if(x<left||showList&&x>=rx){listScroll=Math.max(0,listScroll-(int)dy);return true;}
        if(x>=rx){for(var widget:formWidgets)if(widget instanceof MultiLineEditBox&&widget.isMouseOver(x,y)&&widget.mouseScrolled(x,y,dx,dy))return true;scroll=Math.clamp(scroll-(int)(dy*36),0,Math.max(0,formBottom-(height-94)));rebuildWidgets();return true;}
        return map.mouseScrolled(x,y,dy);
    }
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==GLFW.GLFW_KEY_ESCAPE){if(confirmation!=null){confirmation=null;rebuildWidgets();return true;}if(choices!=null){choices=null;rebuildWidgets();return true;}if(pickPosition||drawRoute){pickPosition=drawRoute=false;status="";rebuildWidgets();return true;}}if(choices==null&&confirmation==null&&hasControlDown()&&key==GLFW.GLFW_KEY_S&&editing){save();return true;}return super.keyPressed(key,scan,mods);}
    @Override public void onClose(){if(!pending.isEmpty())return;if(dirty()){confirm("confirm_close",this::returnToMap);return;}returnToMap();}
    private void returnToMap(){returning=true;minecraft.setScreen(parent);}
    private void openRouteEditor() {
        var next = new JourneyRouteEditorScreen(this, map);
        routeEditorOpen = true;
        minecraft.setScreen(next);
        if (minecraft.screen != next) routeEditorOpen = false;
    }
    void finishRouteEditor(boolean returningToAtlas) {
        routeEditorOpen = false;
        if (!returningToAtlas && parent instanceof com.example.examplemod.client.screen.PolishedWorldMapScreen atlas) {
            atlas.finishJourneyWorkspace(false);
        }
    }
    @Override public void removed(){if(!routeEditorOpen&&parent instanceof com.example.examplemod.client.screen.PolishedWorldMapScreen atlas)atlas.finishJourneyWorkspace(returning);super.removed();}
    @Override public boolean isPauseScreen(){return false;}
}
