package com.example.examplemod.client.logistics;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import static com.example.examplemod.client.logistics.LogisticsSkin.*;

/** Server-defined form: choose, review the quote, explicitly confirm. Never retries mutations. */
public final class CenterActionScreen extends Screen implements CenterResponseScreen,LogisticsClient.EquipmentPicker {
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
    private int wizardStep;
    private ActionButton confirmButton,errorButton;
    private CompoundTag equipment;
    private final long opened=net.minecraft.Util.getMillis();

    public CenterActionScreen(LogisticsCenterScreen parent,CompoundTag schema,CompoundTag snapshot){
        super(tr("action."+schema.getString("Action")));this.parent=parent;this.schema=schema.copy();this.snapshot=snapshot.copy();this.row=snapshot.getCompound("SelectedRow").copy();
    }
    private boolean purchase(){return Set.of("BUY","SEND","CONTRACT_SUPPLY").contains(schema.getString("Action"));}
    private boolean road(){return schema.getString("Action").equals("ROAD_REGISTER");}
    public boolean busy(){return pending!=null||uncertain;}
    public String quoteError(){return error;}
    public boolean canConfirmPrepared(){return (purchase()||road())&&quote!=null&&quote.hasUUID("Token")&&!busy()&&quote.getList("Checks",10).stream().noneMatch(raw->((CompoundTag)raw).getString("Status").equals("BLOCKED"));}
    public void confirmPrepared(){if(canConfirmPrepared())submit();}
    public void requestPreference(String preference){
        if(!purchase()||busy()||!Set.of("CHEAPEST","NO_TOLLS","FASTEST").contains(preference))return;
        values.put("Preference",preference);invalidateQuote();wizardStep=2;rebuildWidgets();submit();
    }
    private List<CompoundTag> fields(){var result=new ArrayList<CompoundTag>();for(Tag raw:schema.getList("Fields",10)){var field=(CompoundTag)raw;String key=field.getString("Key");
        if(schema.getString("Action").equals("COMPANY_PROPOSE")){
            String decision=values.getOrDefault("Decision","DIRECTOR");
            if(key.equals("Player")&&!Set.of("DIRECTOR","ISSUE_SHARES").contains(decision)||key.equals("City")&&!decision.equals("CITY")||key.equals("Amount")&&!Set.of("ISSUE_SHARES","DIVIDEND").contains(decision)||key.equals("Terms")&&!decision.equals("CHARTER"))continue;
        }
        int step=Set.of("Stock","Quantity").contains(key)?0:Set.of("Destination","Owner","Source").contains(key)?1:2;if(!purchase()||step==wizardStep)result.add(field);}return result;}
    private boolean receiver(){return schema.getString("Action").equals("WAREHOUSE_RECEIVER");}
    private List<CompoundTag> visibleOptions(){return options.getOrDefault(dropdown,List.of()).stream().filter(t->name(t).toLowerCase(Locale.ROOT).contains(dropQuery.toLowerCase(Locale.ROOT))).toList();}
    private void invalidateQuote(){quote=null;error="";if(errorButton!=null)errorButton.visible=false;if(confirmButton!=null&&purchase())confirmButton.setMessage(tr(wizardStep<2?"wizard.next":"calculate"));}
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
            if(key==258){dropdown=null;return super.keyPressed(key,scan,modifiers);}
            if(key==259&&!dropQuery.isEmpty()){dropQuery=dropQuery.substring(0,dropQuery.length()-1);dropCursor=dropScroll=0;searchChanged=net.minecraft.Util.getMillis();return true;}
            if(key==264||key==265){dropCursor=Math.max(0,Math.min(visibleOptions().size()-1,dropCursor+(key==264?1:-1)));int count=optionCount();dropScroll=Math.max(0,Math.min(dropCursor,Math.max(dropScroll,dropCursor-count+1)));return true;}
            if(key==257||key==335){if(claimActivationKey(key))choose(dropCursor);return true;}
            return true;
        }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override protected void init(){
        left=Math.max(12,(width-Math.min(560,width-24))/2);formWidth=width-left*2;top=height<240?54:purchase()&&height>=300?112:66;options.clear();
        int i=0;
        for(Tag raw:fields()){
            var field=(CompoundTag)raw;String key=field.getString("Key"),kind=field.getString("Kind");int y=top+24+i++*42-offset;
            if(kind.equals("choice")||kind.equals("enum")){
                var list=new ArrayList<CompoundTag>();
                if(kind.equals("enum"))for(String value:field.getString("Source").split("\\|")){var t=new CompoundTag();t.putString("Value",value);t.putString("Name",text("value."+value));list.add(t);}
                else {String source=field.getString("Source");for(Tag value:Set.of("Allowed","BoundRouters","BoundStations").contains(source)?row.getList(source,10):snapshot.getCompound("Choices").getList(source,10)){
                    var option=((CompoundTag)value).copy();if(source.equals("Assets")){String selectedKind=values.getOrDefault("AssetKind","TRAIN");String expected=Set.of("WAREHOUSE","PRODUCTION","STORE").contains(selectedKind)?"WAREHOUSE":selectedKind;if(!option.getString("Value").startsWith(expected+"/"))continue;}list.add(option);
                }}
                options.put(key,list);String initial=key.equals("Owner")?row.getString("OwnerValue"):key.equals("Enabled")&&row.contains("Enabled",1)?Boolean.toString(row.getBoolean("Enabled")):row.getString(key);
                if(!values.containsKey(key))values.put(key,list.stream().filter(v->v.getString("Value").equals(initial)).map(v->v.getString("Value")).findFirst().orElse(list.isEmpty()?"":list.getFirst().getString("Value")));
            }else{
                var edit=new EditBox(font,left+16,y,formWidth-32,20,fieldLabel(field));edit.setMaxLength(key.equals("Terms")||key.equals("Memo")?256:key.equals("Name")?64:96);
                String initial=key.equals("Tariff")?Long.toString(row.getLong("Tariff")):row.getString(key);
                edit.setValue(values.getOrDefault(key,initial.isEmpty()&&kind.equals("number")?field.getString("Source"):initial));
                edit.setResponder(value->{values.put(key,value);invalidateQuote();});values.putIfAbsent(key,edit.getValue());
                edit.setTooltip(Tooltip.create(fieldHint(field)));
                edit.setEditable(pending==null);edit.visible=y>=top+20&&y+20<height-76;addRenderableWidget(edit);
            }
        }
        errorButton=null;if(!error.isEmpty())errorButton=addRenderableWidget(new ActionButton(left+formWidth-90,height-68,90,20,tr("error.explain"),()->minecraft.setScreen(new CenterErrorScreen(this,error))));
        var cancel=addRenderableWidget(new ActionButton(left,height-38,96,24,tr(pending!=null&&uncertain||purchase()&&wizardStep>0?"back":"cancel"),()->{if(pending==null&&purchase()&&wizardStep>0){wizardStep--;quote=null;error="";offset=0;rebuildWidgets();}else onClose();}));cancel.active=pending==null||uncertain;
        Component label=purchase()?(quote==null?tr(wizardStep<2?"wizard.next":"calculate"):Component.literal(text("confirm_price")+" "+quote.getLong("Total")+" CR")):tr("confirm");
        confirmButton=addRenderableWidget(new ActionButton(left+108,height-38,formWidth-108,24,label,this::submit).primary());confirmButton.active=pending==null&&!uncertain&&(quote==null||canConfirmPrepared());
        if(receiver()){
            var pick=addRenderableWidget(new ActionButton(left+16,top+20,formWidth-32,24,tr(equipment==null?"pick.start":"pick.again"),()->{error="";sent=net.minecraft.Util.getMillis();pending=parent.mutate("PICK_RECEIVER",new CompoundTag());rebuildWidgets();},10));pick.active=pending==null;
            confirmButton.active&=equipment!=null;
        }
    }
    private void submit(){
        if(pending!=null||uncertain)return;
        for(Tag raw:fields()){
            var field=(CompoundTag)raw;String key=field.getString("Key"),kind=field.getString("Kind"),value=values.getOrDefault(key,"").trim();
            if((kind.equals("choice")||kind.equals("enum"))&&options.getOrDefault(key,List.of()).stream().noneMatch(option->option.getString("Value").equals(value))){showError(text("field."+key)+": "+text("missing_choice"));return;}
            if(kind.equals("number")&&!(key.equals("TaxBps")?CenterPresentation.validPercent(value):CenterPresentation.validNumber(field,value))){showError(fieldLabel(field).getString()+": "+text("invalid_number"));return;}
            if(key.equals("WaitSeconds")){long seconds=Long.parseLong(value);if(seconds<10||seconds>300){showError(text("error.schedule_conditions"));return;}}
            if(key.equals("Name")&&value.isBlank()){showError(text("required_name"));return;}
        }
        if(purchase()&&wizardStep<2){wizardStep++;offset=0;error="";rebuildWidgets();return;}
        var payload=new CompoundTag();values.forEach((key,value)->payload.putString(key,key.equals("TaxBps")?Long.toString(CenterPresentation.percentBasisPoints(value.trim())):value.trim()));String action=schema.getString("Action");
        error="";sent=net.minecraft.Util.getMillis();pending=parent.mutate(road()&&quote==null?"ROAD_PREVIEW":purchase()&&quote==null?"QUOTE_"+action:action,payload,quote==null||!quote.hasUUID("Token")?null:quote.getUUID("Token"));rebuildWidgets();
    }
    public void accept(CompoundTag response){
        parent.accept(response);
        if(stockRequest!=null&&response.hasUUID("Request")&&stockRequest.equals(response.getUUID("Request"))){stockRequest=null;if(!response.getBoolean("Unchanged")){snapshot.put("Choices",parent.snapshot().getCompound("Choices").copy());rebuildWidgets();}return;}
        if(pending==null||!response.hasUUID("Request")||!pending.equals(response.getUUID("Request")))return;
        pending=null;uncertain=false;
        if(response.getString("Result").equals("ok")){
            if(response.getBoolean("PickingEquipment")){LogisticsClient.awaitEquipment(this);minecraft.setScreen(null);return;}
            if(response.contains("RoutePreview")){var routeValues=new CompoundTag();values.forEach(routeValues::putString);LogisticsMapOverlay.openRoute(parent,routeValues,response.getCompound("RoutePreview"));return;}
            if(response.contains("Quote")){quote=response.getCompound("Quote").copy();wizardStep=3;offset=0;error="";rebuildWidgets();
                if(minecraft.screen instanceof CargoNavigatorScreen navigator)navigator.updateQuote(quote);else minecraft.setScreen(new CargoNavigatorScreen(parent,this,quote));return;}
            if(purchase()&&response.hasUUID("OrderId"))parent.focusObject("shipment",response.getUUID("OrderId"));
            minecraft.setScreen(parent);return;
        }
        quote=null;if(purchase())wizardStep=2;error=result(response.getString("Result"));rebuildWidgets();
    }
    @Override public void acceptEquipment(CompoundTag response){
        if(response.hasUUID("EquipmentToken")){equipment=response.copy();values.put("EquipmentToken",response.getUUID("EquipmentToken").toString());error="";}
        else error=text("pick.cancelled");
        pending=null;uncertain=false;net.minecraft.client.Minecraft.getInstance().setScreen(this);
    }
    @Override public void returnToWizard(){net.minecraft.client.Minecraft.getInstance().setScreen(this);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        background(g,width,height);
        header(g,left,height<240?8:12,formWidth,title,purchase()?tr("wizard.step."+wizardStep):tr("ui.form_help"),CenterActionCatalog.icon(schema.getString("Action")));
        if(purchase())renderStepper(g);
        surface(g,left,top,formWidth,Math.max(12,height-76-top),PANEL);
        g.enableScissor(left,top,width-left,height-76);int i=0;
        for(Tag raw:fields()){
            var field=(CompoundTag)raw;String key=field.getString("Key");int y=top+9+i++*42-offset;g.drawString(font,fit(fieldLabel(field).getString(),formWidth-60),left+16,y,MUTED,false);icon(g,left+formWidth-29,y-2,13,MUTED);
            if(options.containsKey(key)){boolean hover=mx>=left+16&&mx<width-left-16&&my>=y+15&&my<y+35;surface(g,left+16,y+15,formWidth-32,20,hover&&pending==null?0xff233e49:0xff1a2937);String name=options.get(key).stream().filter(v->v.getString("Value").equals(values.get(key))).map(LogisticsSkin::name).findFirst().orElse(text("no_options"));g.drawString(font,fit(name,formWidth-64),left+24,y+21,options.get(key).isEmpty()?BRASS:TEXT,false);g.hLine(width-left-30,width-left-24,y+22,TEAL);g.hLine(width-left-29,width-left-25,y+23,TEAL);g.hLine(width-left-28,width-left-26,y+24,TEAL);}
        }
        int y=top+10+i*42-offset;String hintKey="hint."+schema.getString("Action");Component hint=net.minecraft.client.resources.language.I18n.exists("ccec.center."+hintKey)?tr(hintKey):tr("confirm_hint");
        if(receiver()){
            y=top+56;
            if(equipment!=null){g.drawString(font,text("pick.packager_label"),left+16,y,TEAL,false);g.drawString(font,fit(CenterText.location(equipment.getCompound("EquipmentPackager"),key->Component.translatable(key).getString()),formWidth-32),left+16,y+15,TEXT,false);g.drawString(font,text("pick.postbox_label"),left+16,y+40,TEAL,false);g.drawString(font,fit(CenterText.location(equipment.getCompound("EquipmentPostbox"),key->Component.translatable(key).getString()),formWidth-32),left+16,y+55,TEXT,false);y+=82;}
            g.drawWordWrap(font,tr("pick.review"),left+16,y,formWidth-32,MUTED);quoteContentHeight=170;
        }else if(quote==null){g.drawWordWrap(font,hint,left+16,y+4,formWidth-32,MUTED);quoteContentHeight=font.wordWrapHeight(hint,formWidth-32)+20;}else renderQuote(g,y);
        g.disableScissor();super.render(g,mx,my,partial);if(dropdown!=null)renderDropdown(g,mx,my);
        else if(my>=top&&my<height-76){int n=0;for(var field:fields()){int fy=top+7+n++*42-offset;if(my>=fy-2&&my<fy+13&&mx>=left+12&&mx<width-left-12){g.renderTooltip(font,fieldHint(field),mx,my);break;}}}
        if(!error.isEmpty())g.drawString(font,fit(error,formWidth-100),left,height-62,BRASS,false);
        else if(pending!=null){g.drawString(font,text("sync"),left,height-57,TEAL,false);int progress=reducedMotion()?0:(int)((net.minecraft.Util.getMillis()%1100)/1100.0*(formWidth-24));g.fill(left+progress,height-44,left+progress+(reducedMotion()?formWidth:24),height-43,TEAL);}
    }
    private Component fieldHint(CompoundTag field){
        if(!field.getString("HintKey").isBlank())return Component.translatable(field.getString("HintKey"));
        String key="field_hint."+field.getString("Key");return I18n.exists("ccec.center."+key)?tr(key):tr("ui.field_help");
    }
    private Component fieldLabel(CompoundTag field){return field.getString("LabelKey").isBlank()?tr("field."+field.getString("Key")):Component.translatable(field.getString("LabelKey"));}
    private void showError(String message){error=message;rebuildWidgets();}
    private void renderStepper(GuiGraphics g){
        int active=Math.min(3,wizardStep);
        if(height<300){if(height>=240){g.fill(left+12,59,left+formWidth-12,61,EDGE);g.fill(left+12,59,left+12+(formWidth-24)*(active+1)/4,61,TEAL);}return;}
        int cell=(formWidth-24)/4;
        for(int n=0;n<4;n++){
            int x=left+12+n*cell;boolean current=n==active,done=n<active;
            surface(g,x,67,cell-6,29,current?0xff244239:0xff172634);
            g.drawString(font,Integer.toString(n+1),x+8,77,current||done?TEAL:MUTED,false);
            g.drawString(font,fit(text("ui.stage"+(n+1)),cell-33),x+23,77,current?TEXT:MUTED,false);
        }
        g.fill(left+12,103,left+formWidth-12,105,EDGE);
        float reveal=reducedMotion()?1:com.example.examplemod.client.CommunityFx.ease((net.minecraft.Util.getMillis()-opened)/260d);
        g.fill(left+12,103,left+12+(int)((formWidth-24)*(active+1)/4f*reveal),105,TEAL);
    }
    private void renderQuote(GuiGraphics g,int y){
        int start=y;g.drawString(font,text("ui.quote_ready"),left+16,y,TEAL,false);y+=18;
        var delivery=Component.literal(quote.getString("Source")+" → "+quote.getString("Destination")+" · ×"+quote.getInt("Quantity"));
        g.drawWordWrap(font,delivery,left+16,y,formWidth-32,TEXT);y+=font.wordWrapHeight(delivery,formWidth-32)+12;
        var costs=Component.translatable("ccec.center.quote_summary",quote.getLong("Total"),quote.getLong("Goods"),quote.getLong("Transport"));
        int costHeight=font.wordWrapHeight(costs,formWidth-56)+44;
        surface(g,left+16,y,formWidth-32,costHeight,0xff203b3b);g.fill(left+16,y+4,left+18,y+costHeight-4,TEAL);
        g.pose().pushPose();g.pose().translate(left+28,y+12,0);g.pose().scale(1.35f,1.35f,1);
        g.drawString(font,fit(quote.getLong("Total")+" CR",(int)((formWidth-56)/1.35f)),0,0,TEXT,false);g.pose().popPose();
        g.drawWordWrap(font,costs,left+28,y+33,formWidth-56,MUTED);y+=costHeight+20;
        g.drawString(font,text("quote_route"),left+16,y,TEXT,false);y+=24;int n=1;
        for(Tag raw:quote.getList("Route",10)){
            var leg=(CompoundTag)raw;int legTop=y;
            surface(g,left+16,y-4,22,23,0xff1b303a);g.drawString(font,Integer.toString(n++),left+24,y+3,TEAL,false);
            var title=Component.literal(leg.getString("Name")+" · "+leg.getLong("Price")+" CR");g.drawWordWrap(font,title,left+47,y,formWidth-64,TEXT);y+=font.wordWrapHeight(title,formWidth-64)+6;
            g.drawString(font,fit(leg.getString("Carrier"),formWidth-64),left+47,y,MUTED,false);y+=18;
            if(!leg.getString("Terms").isBlank()){var terms=Component.literal(leg.getString("Terms"));g.drawWordWrap(font,terms,left+47,y,formWidth-64,MUTED);y+=font.wordWrapHeight(terms,formWidth-64)+10;}
            g.vLine(left+27,legTop+21,y+5,EDGE);
            if(!reducedMotion()){long now=net.minecraft.Util.getMillis();int dot=legTop+22+(int)((now%1800)/1800d*Math.max(1,y-legTop-17));g.fill(left+26,dot,left+29,dot+3,TEAL);}y+=13;
        }
        if(quote.getList("Route",10).isEmpty()){g.drawWordWrap(font,tr("no_route_legs"),left+16,y,formWidth-32,MUTED);y+=28;}
        g.drawWordWrap(font,tr("quote_terms"),left+16,y+8,formWidth-32,MUTED);quoteContentHeight=y-start+font.wordWrapHeight(tr("quote_terms"),formWidth-32)+30;
    }
    private int dropdownTop(){return Math.min(top+20,Math.max(56,height-126));}
    private int optionCount(){return Math.max(1,(height-48-dropdownTop()-32)/23);}
    private void renderDropdown(GuiGraphics g,int mx,int my){
        int y=dropdownTop();var list=visibleOptions();g.fill(left,Math.min(top,y),width-left,height-44,0xee0a1018);surface(g,left+12,y,formWidth-24,Math.min(height-48-y,Math.max(1,Math.min(optionCount(),list.size()))*23+36),0xff1c303d);g.enableScissor(left+16,y,width-left-16,height-51);g.drawString(font,fit(dropQuery.isEmpty()?text("search"):dropQuery,formWidth-52),left+24,y+8,MUTED,false);
        if(list.isEmpty())g.drawString(font,text("no_options"),left+24,y+31,BRASS,false);
        for(int n=dropScroll;n<Math.min(list.size(),dropScroll+optionCount());n++){int dy=y+32+(n-dropScroll)*23;g.fill(left+18,dy-3,width-left-18,dy+18,mx>=left+18&&mx<width-left-18&&my>=dy-3&&my<dy+18||n==dropCursor?0xff294b45:PANEL);g.drawString(font,fit(name(list.get(n)),formWidth-52),left+24,dy,TEXT,false);}g.disableScissor();
    }
    private void choose(int index){var list=visibleOptions();if(index>=0&&index<list.size()){if("AssetKind".equals(dropdown))values.remove("Asset");values.put(dropdown,list.get(index).getString("Value"));invalidateQuote();}dropdown=null;rebuildWidgets();}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button!=0)return super.mouseClicked(x,y,button);
        if(dropdown!=null){
            double localY=y-dropdownTop()-29;int index=(int)Math.floor(localY/23d);
            if(x>=left+18&&x<width-left-18&&localY>=0&&index<optionCount()&&localY-index*23<21&&y<height-51)choose(index+dropScroll);
            else dropdown=null;return true;
        }
        if(pending==null){int i=0;for(Tag raw:fields()){var field=(CompoundTag)raw;int fy=top+24+i++*42-offset;if(options.containsKey(field.getString("Key"))&&y>=fy&&y<fy+20&&y>=top&&y<height-76&&x>=left+16&&x<=width-left-16){dropdown=field.getString("Key");dropQuery="";dropCursor=dropScroll=0;return true;}}}
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(dropdown!=null){dropScroll=Math.max(0,Math.min(Math.max(0,visibleOptions().size()-optionCount()),dropScroll-(int)vertical));return true;}
        offset=Math.max(0,Math.min(CenterPresentation.scrollLimit(fields().size()*42+Math.max(40,quoteContentHeight),height-top-76),offset-(int)(vertical*24)));rebuildWidgets();return true;
    }
    @Override public void onClose(){if(pending==null||uncertain)minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
