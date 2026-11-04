package com.example.examplemod.client.logistics;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import static com.example.examplemod.client.logistics.LogisticsSkin.*;

/** Server-defined form: choose, review the quote, explicitly confirm. Never retries mutations. */
public final class CenterActionScreen extends Screen implements CenterResponseScreen {
    private final LogisticsCenterScreen parent;
    private final CompoundTag schema,snapshot,row;
    private final Map<String,String> values=new HashMap<>();
    private final Map<String,List<CompoundTag>> options=new HashMap<>();
    private String dropdown,error="",dropQuery="";
    private int offset,dropScroll,dropCursor,left,top,formWidth,quoteContentHeight;
    private UUID pending,stockRequest;
    private CompoundTag quote;
    private long searchChanged,sent;
    private boolean uncertain;
    private ActionButton confirmButton;
    private CompoundTag equipment;

    public CenterActionScreen(LogisticsCenterScreen parent,CompoundTag schema,CompoundTag snapshot){
        super(tr("action."+schema.getString("Action")));this.parent=parent;this.schema=schema.copy();this.snapshot=snapshot.copy();this.row=snapshot.getCompound("SelectedRow").copy();
    }
    private boolean purchase(){return Set.of("BUY","SEND").contains(schema.getString("Action"));}
    private boolean receiver(){return schema.getString("Action").equals("WAREHOUSE_RECEIVER");}
    private List<CompoundTag> visibleOptions(){return options.getOrDefault(dropdown,List.of()).stream().filter(t->name(t).toLowerCase(Locale.ROOT).contains(dropQuery.toLowerCase(Locale.ROOT))).toList();}
    private void invalidateQuote(){quote=null;error="";if(confirmButton!=null&&purchase())confirmButton.setMessage(tr("calculate"));}
    @Override public void tick(){
        super.tick();long now=net.minecraft.Util.getMillis();
        if(pending!=null&&!uncertain&&CenterPresentation.timedOut(sent,now)){uncertain=true;error=text("uncertain");rebuildWidgets();}
        if(pending==null&&searchChanged>0&&now-searchChanged>350){searchChanged=0;if("Stock".equals(dropdown)&&(dropQuery.isBlank()||visibleOptions().isEmpty()))stockRequest=parent.searchStock(dropQuery);}
    }
    @Override public boolean charTyped(char character,int modifiers){
        if(pending!=null)return false;
        if(dropdown!=null&&!Character.isISOControl(character)){if(dropQuery.length()<48){dropQuery+=character;dropCursor=dropScroll=0;searchChanged=net.minecraft.Util.getMillis();}return true;}
        return super.charTyped(character,modifiers);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(dropdown!=null){
            if(key==256){dropdown=null;return true;}
            if(key==259&&!dropQuery.isEmpty()){dropQuery=dropQuery.substring(0,dropQuery.length()-1);dropCursor=dropScroll=0;searchChanged=net.minecraft.Util.getMillis();return true;}
            if(key==264||key==265){dropCursor=Math.max(0,Math.min(visibleOptions().size()-1,dropCursor+(key==264?1:-1)));int count=optionCount();dropScroll=Math.max(0,Math.min(dropCursor,Math.max(dropScroll,dropCursor-count+1)));return true;}
            if(key==257){choose(dropCursor);return true;}
        }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override protected void init(){
        left=Math.max(12,(width-Math.min(480,width-24))/2);formWidth=width-left*2;top=66;options.clear();
        int i=0;
        for(Tag raw:schema.getList("Fields",10)){
            var field=(CompoundTag)raw;String key=field.getString("Key"),kind=field.getString("Kind");int y=top+24+i++*42-offset;
            if(kind.equals("choice")||kind.equals("enum")){
                var list=new ArrayList<CompoundTag>();
                if(kind.equals("enum"))for(String value:field.getString("Source").split("\\|")){var t=new CompoundTag();t.putString("Value",value);t.putString("Name",text("value."+value));list.add(t);}
                else {String source=field.getString("Source");for(Tag value:Set.of("Allowed","BoundRouters","BoundStations").contains(source)?row.getList(source,10):snapshot.getCompound("Choices").getList(source,10))list.add(((CompoundTag)value).copy());}
                options.put(key,list);String initial=key.equals("Owner")?row.getString("OwnerValue"):key.equals("Enabled")&&row.contains("Enabled",1)?Boolean.toString(row.getBoolean("Enabled")):row.getString(key);
                if(!values.containsKey(key))values.put(key,list.stream().filter(v->v.getString("Value").equals(initial)).map(v->v.getString("Value")).findFirst().orElse(list.isEmpty()?"":list.getFirst().getString("Value")));
            }else{
                var edit=new EditBox(font,left+16,y,formWidth-32,20,tr("field."+key));edit.setMaxLength(key.equals("Terms")?256:key.equals("Name")?64:96);
                String initial=key.equals("Tariff")?Long.toString(row.getLong("Tariff")):row.getString(key);
                edit.setValue(values.getOrDefault(key,initial.isEmpty()&&kind.equals("number")?field.getString("Source"):initial));
                edit.setResponder(value->{values.put(key,value);invalidateQuote();});values.putIfAbsent(key,edit.getValue());
                edit.setEditable(pending==null);edit.visible=y>=top+20&&y+20<height-76;addRenderableWidget(edit);
            }
        }
        var cancel=addRenderableWidget(new ActionButton(left,height-38,96,24,tr(pending!=null&&uncertain?"back":"cancel"),this::onClose));cancel.active=pending==null||uncertain;
        Component label=purchase()?(quote==null?tr("calculate"):Component.literal(text("confirm_price")+" "+quote.getLong("Total")+" CR")):tr("confirm");
        confirmButton=addRenderableWidget(new ActionButton(left+108,height-38,formWidth-108,24,label,this::submit));confirmButton.active=pending==null&&!uncertain;
        if(receiver()){
            var pick=addRenderableWidget(new ActionButton(left+16,top+20,formWidth-32,24,tr(equipment==null?"pick.start":"pick.again"),()->{error="";sent=net.minecraft.Util.getMillis();pending=parent.mutate("PICK_RECEIVER",new CompoundTag());rebuildWidgets();},10));pick.active=pending==null;
            confirmButton.active&=equipment!=null;
        }
    }
    private void submit(){
        if(pending!=null||uncertain)return;
        for(Tag raw:schema.getList("Fields",10)){
            var field=(CompoundTag)raw;String key=field.getString("Key"),kind=field.getString("Kind"),value=values.getOrDefault(key,"").trim();
            if((kind.equals("choice")||kind.equals("enum"))&&options.getOrDefault(key,List.of()).stream().noneMatch(option->option.getString("Value").equals(value))){error=text("missing_choice");return;}
            if(kind.equals("number")&&!CenterPresentation.validNumber(key,value)){error=text("invalid_number");return;}
            if(key.equals("WaitSeconds")){long seconds=Long.parseLong(value);if(seconds<10||seconds>300){error=text("error.schedule_conditions");return;}}
            if(key.equals("Name")&&value.isBlank()){error=text("required_name");return;}
        }
        var payload=new CompoundTag();values.forEach((key,value)->payload.putString(key,value.trim()));String action=schema.getString("Action");
        error="";sent=net.minecraft.Util.getMillis();pending=parent.mutate(purchase()&&quote==null?"QUOTE_"+action:action,payload,quote==null?null:quote.getUUID("Token"));rebuildWidgets();
    }
    public void accept(CompoundTag response){
        parent.accept(response);
        if(stockRequest!=null&&response.hasUUID("Request")&&stockRequest.equals(response.getUUID("Request"))){stockRequest=null;if(!response.getBoolean("Unchanged")){snapshot.put("Choices",parent.snapshot().getCompound("Choices").copy());rebuildWidgets();}return;}
        if(pending==null||!response.hasUUID("Request")||!pending.equals(response.getUUID("Request")))return;
        pending=null;uncertain=false;
        if(response.getString("Result").equals("ok")){
            if(response.getBoolean("PickingEquipment")){LogisticsClient.awaitEquipment(this);minecraft.setScreen(null);return;}
            if(response.contains("RoutePreview")){var routeValues=new CompoundTag();values.forEach(routeValues::putString);LogisticsMapOverlay.openRoute(parent,routeValues,response.getCompound("RoutePreview"));return;}
            if(response.contains("Quote")){quote=response.getCompound("Quote").copy();error="";rebuildWidgets();return;}
            minecraft.setScreen(parent);return;
        }
        quote=null;error=result(response.getString("Result"));rebuildWidgets();
    }
    void acceptEquipment(CompoundTag response){
        if(response.hasUUID("EquipmentToken")){equipment=response.copy();values.put("EquipmentToken",response.getUUID("EquipmentToken").toString());error="";}
        else error=text("pick.cancelled");
        pending=null;uncertain=false;net.minecraft.client.Minecraft.getInstance().setScreen(this);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fillGradient(0,0,width,height,BG,0xff17252b);surface(g,left,12,formWidth,44,PANEL);
        icon(g,left+12,24,CenterActionCatalog.icon(schema.getString("Action")),TEAL);g.drawString(font,fit(title.getString(),formWidth-53),left+34,23,TEXT,false);
        g.drawString(font,fit(quote==null?text("step.choose"):text("step.review"),formWidth-53),left+34,40,MUTED,false);
        g.enableScissor(left,top,width-left,height-76);int i=0;
        for(Tag raw:schema.getList("Fields",10)){
            var field=(CompoundTag)raw;String key=field.getString("Key");int y=top+9+i++*42-offset;g.drawString(font,text("field."+key),left+16,y,MUTED,false);
            if(options.containsKey(key)){surface(g,left+16,y+15,formWidth-32,20,PANEL);String name=options.get(key).stream().filter(v->v.getString("Value").equals(values.get(key))).map(LogisticsSkin::name).findFirst().orElse(text("no_options"));g.drawString(font,fit(name,formWidth-64),left+24,y+21,options.get(key).isEmpty()?BRASS:TEXT,false);g.hLine(width-left-30,width-left-24,y+22,TEAL);g.hLine(width-left-29,width-left-25,y+23,TEAL);g.hLine(width-left-28,width-left-26,y+24,TEAL);}
        }
        int y=top+10+i*42-offset;String hintKey="hint."+schema.getString("Action");Component hint=net.minecraft.client.resources.language.I18n.exists("ccec.center."+hintKey)?tr(hintKey):tr("confirm_hint");
        if(receiver()){
            y=top+56;
            if(equipment!=null){g.drawString(font,text("pick.packager_label"),left+16,y,TEAL,false);g.drawString(font,fit(CenterText.location(equipment.getCompound("EquipmentPackager"),key->Component.translatable(key).getString()),formWidth-32),left+16,y+15,TEXT,false);g.drawString(font,text("pick.postbox_label"),left+16,y+40,TEAL,false);g.drawString(font,fit(CenterText.location(equipment.getCompound("EquipmentPostbox"),key->Component.translatable(key).getString()),formWidth-32),left+16,y+55,TEXT,false);y+=82;}
            g.drawWordWrap(font,tr("pick.review"),left+16,y,formWidth-32,MUTED);quoteContentHeight=170;
        }else if(quote==null){g.drawWordWrap(font,hint,left+16,y+4,formWidth-32,MUTED);quoteContentHeight=font.wordWrapHeight(hint,formWidth-32)+20;}else renderQuote(g,y);
        g.disableScissor();super.render(g,mx,my,partial);if(dropdown!=null)renderDropdown(g,mx,my);
        if(!error.isEmpty())g.drawWordWrap(font,Component.literal(error),left,height-72,formWidth,BRASS);
        else if(pending!=null){g.drawString(font,text("sync"),left,height-57,TEAL,false);int progress=(int)((net.minecraft.Util.getMillis()%1100)/1100.0*(formWidth-24));g.fill(left+progress,height-44,left+progress+24,height-43,TEAL);}
    }
    private void renderQuote(GuiGraphics g,int y){
        int start=y;g.drawWordWrap(font,Component.translatable("ccec.center.quote_summary",quote.getLong("Total"),quote.getLong("Goods"),quote.getLong("Transport")),left+16,y,formWidth-32,TEAL);y+=30;g.drawString(font,text("quote_route"),left+16,y,TEXT,false);y+=21;int n=1;
        for(Tag raw:quote.getList("Route",10)){var leg=(CompoundTag)raw;g.fill(left+19,y,left+21,y+23,TEAL);g.drawString(font,fit(n+++". "+leg.getString("Name")+" · "+leg.getLong("Price")+" CR",formWidth-48),left+29,y,TEXT,false);y+=15;g.drawString(font,fit(leg.getString("Carrier"),formWidth-48),left+29,y,MUTED,false);y+=19;if(!leg.getString("Terms").isBlank()){var terms=Component.literal(leg.getString("Terms"));g.drawWordWrap(font,terms,left+29,y,formWidth-48,MUTED);y+=font.wordWrapHeight(terms,formWidth-48)+10;}}
        if(quote.getList("Route",10).isEmpty()){g.drawWordWrap(font,tr("no_route_legs"),left+16,y,formWidth-32,MUTED);y+=28;}
        g.drawWordWrap(font,tr("quote_terms"),left+16,y+8,formWidth-32,MUTED);quoteContentHeight=y-start+font.wordWrapHeight(tr("quote_terms"),formWidth-32)+30;
    }
    private int optionCount(){return Math.max(1,(height-top-132)/23);}
    private void renderDropdown(GuiGraphics g,int mx,int my){
        int y=top+20;var list=visibleOptions();surface(g,left+12,y,formWidth-24,Math.max(52,Math.min(height-top-98,list.size()*23+34)),0xff1b3037);g.enableScissor(left+16,y,width-left-16,height-80);g.drawString(font,fit(dropQuery.isEmpty()?text("search"):dropQuery,formWidth-52),left+24,y+8,MUTED,false);
        if(list.isEmpty())g.drawString(font,text("no_options"),left+24,y+31,BRASS,false);
        for(int n=dropScroll;n<Math.min(list.size(),dropScroll+optionCount());n++){int dy=y+32+(n-dropScroll)*23;g.fill(left+18,dy-3,width-left-18,dy+18,my>=dy-3&&my<dy+18||n==dropCursor?0xff294b45:PANEL);g.drawString(font,fit(name(list.get(n)),formWidth-52),left+24,dy,TEXT,false);}g.disableScissor();
    }
    private void choose(int index){var list=visibleOptions();if(index>=0&&index<list.size()){values.put(dropdown,list.get(index).getString("Value"));invalidateQuote();}dropdown=null;}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button!=0)return super.mouseClicked(x,y,button);
        if(dropdown!=null){int index=(int)((y-top-52)/23)+dropScroll;if(x>=left+16&&x<=width-left-16&&y>=top+52&&y<height-80)choose(index);else dropdown=null;return true;}
        if(pending==null){int i=0;for(Tag raw:schema.getList("Fields",10)){var field=(CompoundTag)raw;int fy=top+24+i++*42-offset;if(options.containsKey(field.getString("Key"))&&y>=fy&&y<fy+20&&y>=top&&y<height-76&&x>=left+16&&x<=width-left-16){dropdown=field.getString("Key");dropQuery="";dropCursor=dropScroll=0;return true;}}}
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(dropdown!=null){dropScroll=Math.max(0,Math.min(Math.max(0,visibleOptions().size()-optionCount()),dropScroll-(int)vertical));return true;}
        offset=Math.max(0,Math.min(CenterPresentation.scrollLimit(schema.getList("Fields",10).size()*42+Math.max(40,quoteContentHeight),height-top-88),offset-(int)(vertical*24)));rebuildWidgets();return true;
    }
    @Override public void onClose(){if(pending==null||uncertain)minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
