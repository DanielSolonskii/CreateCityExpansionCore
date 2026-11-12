package com.example.examplemod.client.logistics;

import com.google.gson.*;
import net.minecraft.nbt.CompoundTag;
import java.nio.file.*;
import java.util.*;

/** Scale/accessibility and form validation contracts. Real OpenGL/Create movement is tested separately. */
public final class LogisticsNavigatorCheck {
    private static int assertions;
    private static void require(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        for(int width:new int[]{320,400,580,699,700,960,1280,1600})for(int height:new int[]{180,240,360,720,900}){
            var layout=CargoNavigatorLayout.of(width,height);var boxes=List.of(layout.map(),layout.summary(),layout.back(),layout.worldMap(),layout.order());
            for(var r:boxes){require(r.width()>0&&r.height()>0,"positive native hit target");require(r.x()>=0&&r.y()>=0&&r.right()<=width&&r.bottom()<=height,"all controls and content inside window "+width+"x"+height);}
            for(int i=0;i<boxes.size();i++)for(int j=i+1;j<boxes.size();j++)require(!boxes.get(i).overlaps(boxes.get(j)),"map, quote, and actions never overlap");
            require(layout.order().width()>=60,"order remains reachable at smallest supported scale");
            require(layout.summary().height()-43>=12,"summary text remains readable below route controls");
        }
        var salary=new CompoundTag();salary.putString("Key","Salary");salary.putString("Source","0");
        require(CenterPresentation.validNumber(salary,"0"),"zero salary can be agreed");require(!CenterPresentation.validNumber(salary,"-1"),"negative salary forbidden");
        var gift=new CompoundTag();gift.putString("Key","Price");gift.putString("Source","0");require(CenterPresentation.validNumber(gift,"0"),"private share gift can have zero price");
        var quantity=new CompoundTag();quantity.putString("Key","Quantity");quantity.putString("Source","1");require(!CenterPresentation.validNumber(quantity,"0"),"zero cargo amount forbidden");
        var tax=new CompoundTag();tax.putString("Key","TaxBps");tax.putLong("Min",0);tax.putLong("Max",10000);
        require(CenterPresentation.validNumber(tax,"10000")&&!CenterPresentation.validNumber(tax,"10001"),"tax respects schema bounds");
        require(!CenterPresentation.validNumber(tax,"9223372036854775808"),"overflowing numeric input rejected");
        require(CenterPresentation.percentBasisPoints("12,5")==1250&&CenterPresentation.percentBasisPoints("100")==10000,"human percentages convert exactly");
        require(!CenterPresentation.validPercent("100.01")&&!CenterPresentation.validPercent("0.001")&&!CenterPresentation.validPercent("-1"),"percentage bounds and precision");
        var row=new CompoundTag();var facts=new net.minecraft.nbt.ListTag();var fact=new CompoundTag();fact.putString("LabelKey","ccec.company.balance");fact.putString("Value","125 CR");facts.add(fact);row.put("Facts",facts);
        require(CenterText.fields(row,key->key.equals("ccec.company.balance")?"Company account":key).stream().anyMatch(f->f.label().equals("Company account")&&f.value().equals("125 CR")),"company facts reach ordinary detail pane");
        for(String prefix:List.of("ccec.center.error.","ccec.rail.error.","ccec.company.error.","ccec.master.error."))
            require(CenterText.value("fleet_phase_start_changed",key->key.equals(prefix+"fleet_phase_start_changed")?"Recalculate the trip":key).equals("Recalculate the trip"),"saved warning uses error dictionary "+prefix);
        require(CenterText.value("READY",key->key.equals("ccec.center.status.READY")?"Ready":key.equals("ccec.center.error.READY")?"Error":key).equals("Ready"),"status labels retain priority over error fallback");
        var root=Path.of("").toAbsolutePath();while(root!=null&&!Files.isRegularFile(root.resolve("build.gradle")))root=root.getParent();if(root==null)throw new AssertionError("repo root");
        for(String locale:List.of("ru_ru","en_us")){
            var language=JsonParser.parseString(Files.readString(root.resolve("src/main/resources/assets/createcityexpansioncore/lang/"+locale+".json"))).getAsJsonObject();
            for(String section:List.of("companies","fleet","roads","debts","trade_contracts","master_city"))for(String prefix:List.of("tab.","nav_hint.","section_hint."))require(language.has("ccec.center."+prefix+section),"new workspace has label/help "+locale+" "+section);
            for(String key:List.of("title","atlas","order","CHEAPEST","NO_TOLLS","FASTEST","goods","carrier","train","cycles","tolls","total","readiness","reserve_hint","road_title","register_road"))require(language.has("ccec.center.navigator."+key),"navigator localization "+locale+" "+key);
            String warning="fleet_phase_start_changed";
            require(CenterText.value(warning,key->language.has(key)?language.get(key).getAsString():key).equals(language.get("ccec.center.error."+warning).getAsString()),"dispatch warning remains actionable in "+locale);
            require(language.has("block.createcityexpansioncore.customs_buffer"),"customs inventory has visible name");
        }
        System.out.println("PASS: "+assertions+" logistics navigator scale, numeric bounds, facts and localization checks");
    }
}
