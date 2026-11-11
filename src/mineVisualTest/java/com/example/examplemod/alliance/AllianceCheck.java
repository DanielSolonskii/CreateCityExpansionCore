package com.example.examplemod.alliance;

import com.example.examplemod.client.screen.AllianceLayout;
import com.example.examplemod.network.*;
import net.minecraft.nbt.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import java.util.*;
import static com.example.examplemod.alliance.AllianceBook.Action;

/** Real aggregate and NBT codec, including adversarial authority and replay cases. No game client. */
public final class AllianceCheck {
    private static int count;private static final long NOW=1000000;
    private static void require(boolean value,String label){count++;if(!value)throw new AssertionError(label);}
    private static void eq(Object actual,Object expected,String label){require(Objects.equals(actual,expected),label+": "+actual+" != "+expected);}
    private static AllianceBook.CityRef city(String name){UUID owner=UUID.randomUUID();return new AllianceBook.CityRef(UUID.randomUUID(),name,owner,Set.of(owner,UUID.randomUUID()));}
    private static AllianceBook.Command command(Action action,AllianceBook.Alliance a,UUID target,String text){return new AllianceBook.Command(UUID.randomUUID(),action,a==null?null:a.id,target,a==null?0:a.revision,text,"Shared purpose",0xD3A363,1);}
    private static String apply(AllianceBook b,Action action,AllianceBook.Alliance a,UUID target,String text,AllianceBook.CityRef city,Map<UUID,AllianceBook.CityRef> cities){return b.apply(command(action,a,target,text),city.owner(),city,cities,"Owner",NOW);}
    public static void main(String[] args){
        var aCity=city("Alpha");var bCity=city("Beta");var cCity=city("Gamma");var outsider=city("Outside");
        var cities=new LinkedHashMap<UUID,AllianceBook.CityRef>();for(var c:List.of(aCity,bCity,cCity,outsider))cities.put(c.id(),c);
        var data=new AllianceSavedData();var b=data.book;
        UUID resident=aCity.members().stream().filter(id->!id.equals(aCity.owner())).findFirst().orElseThrow();
        eq(b.apply(command(Action.CREATE,null,null,"Union"),resident,aCity,cities,"Resident",NOW),"owner_only","residents cannot found alliances");
        eq(b.apply(command(Action.CREATE,null,null,"Union"),UUID.randomUUID(),null,cities,"None",NOW),"city_required","cityless caller");
        var create=command(Action.CREATE,null,null,"Union");eq(b.apply(create,aCity.owner(),aCity,cities,"Owner",NOW),"created","create");eq(b.apply(create,aCity.owner(),aCity,cities,"Owner",NOW),"duplicate","create replay");
        var a=b.city(aCity.id());require(a!=null,"city indexed");eq(b.representative(a,aCity),aCity.owner(),"default representative");
        eq(apply(b,Action.CREATE,null,null,"Other",aCity,cities),"already_member","one alliance per city");
        eq(apply(b,Action.CREATE,null,null,"union",bCity,cities),"name_taken","case insensitive name uniqueness");
        eq(apply(b,Action.INVITE,a,bCity.id(),"",aCity,cities),"proposed","single city majority issues invitation");require(a.invitations.containsKey(bCity.id())&&a.proposals.isEmpty(),"invitation is not membership");
        eq(apply(b,Action.ACCEPT,a,null,"",outsider,cities),"expired","uninvited city cannot join");
        UUID bResident=bCity.members().stream().filter(id->!id.equals(bCity.owner())).findFirst().orElseThrow();eq(b.apply(command(Action.ACCEPT,a,null,""),bResident,bCity,cities,"Resident",NOW),"owner_only","only target owner accepts");
        eq(apply(b,Action.ACCEPT,a,null,"",bCity,cities),"ok","target accepts");require(a.cities.size()==2,"two member cities");
        eq(apply(b,Action.INVITE,a,cCity.id(),"",aCity,cities),"proposed","invite proposal");var invitation=a.proposals.values().iterator().next();require(invitation.yes()==1&&!a.invitations.containsKey(cCity.id()),"two cities need two yes votes");
        eq(apply(b,Action.YES,a,invitation.id,"",aCity,cities),"already_voted","one vote per city");
        eq(apply(b,Action.YES,a,invitation.id,"",outsider,cities),"permission","outsider cannot vote");
        eq(b.apply(command(Action.YES,a,invitation.id,""),bResident,bCity,cities,"Resident",NOW),"council_only","resident cannot vote");
        var pendingReload=AllianceSavedData.load(data.save(new CompoundTag(),null),null);var pendingAlliance=pendingReload.book.get(a.id);require(pendingAlliance.proposals.get(invitation.id).votes.equals(invitation.votes),"pending electorate and votes persist across restart");
        eq(apply(b,Action.YES,a,invitation.id,"",bCity,cities),"voted","second city votes");require(a.invitations.containsKey(cCity.id()),"approved invitation");require(AllianceSavedData.load(data.save(new CompoundTag(),null),null).book.get(a.id).invitations.equals(a.invitations),"approved invitations survive restart");
        eq(apply(b,Action.RENAME,a,null,"New union",aCity,cities),"proposed","pending rename");require(!a.proposals.isEmpty(),"rename pending");
        eq(apply(b,Action.ACCEPT,a,null,"",cCity,cities),"ok","third city joins");require(a.proposals.isEmpty(),"membership change cancels pending ballots");
        eq(apply(b,Action.RENAME,a,null,"New union",aCity,cities),"proposed","three city rename");var rename=a.proposals.values().iterator().next();
        eq(apply(b,Action.NO,a,rename.id,"",bCity,cities),"voted","no vote retained");require(a.proposals.containsKey(rename.id),"one no cannot block majority");
        eq(apply(b,Action.YES,a,rename.id,"",cCity,cities),"voted","two thirds majority");eq(a.name,"New union","rename applied once");
        eq(apply(b,Action.RENAME,a,null,"Rejected",aCity,cities),"proposed","rejection proposal");rename=a.proposals.values().iterator().next();apply(b,Action.NO,a,rename.id,"",bCity,cities);apply(b,Action.NO,a,rename.id,"",cCity,cities);require(a.proposals.isEmpty()&&a.name.equals("New union"),"majority no closes ballot without rename");
        eq(apply(b,Action.REPRESENTATIVE,a,resident,"",aCity,cities),"ok","owner appoints live city member");require(!b.council(a,aCity,aCity.owner())&&b.council(a,aCity,resident),"one live representative");
        var changed=new AllianceBook.CityRef(aCity.id(),aCity.name(),aCity.owner(),Set.of(aCity.owner()));eq(b.representative(a,changed),aCity.owner(),"departed representative falls back to owner");
        apply(b,Action.REPRESENTATIVE,a,aCity.owner(),"",aCity,cities);
        eq(apply(b,Action.EXCLUDE,a,cCity.id(),"",aCity,cities),"proposed","exclusion vote");var exclusion=a.proposals.values().iterator().next();require(!exclusion.electorate.contains(cCity.id()),"target cannot vote on exclusion");eq(apply(b,Action.YES,a,exclusion.id,"",cCity,cities),"permission","excluded city denied");apply(b,Action.YES,a,exclusion.id,"",bCity,cities);require(!a.cities.contains(cCity.id())&&b.city(cCity.id())==null,"majority exclusion applied");
        var stale=command(Action.LEAVE,a,null,"");apply(b,Action.REPRESENTATIVE,a,bResident,"",bCity,cities);eq(b.apply(stale,aCity.owner(),aCity,cities,"Owner",NOW),"stale","stale mutation rejected");
        require(AllianceService.mayReadPrivate(a,aCity,resident),"city resident may read alliance chat");require(!AllianceService.mayReadPrivate(a,outsider,outsider.owner()),"outsider cannot read private projection");require(!AllianceService.mayReadPrivate(a,aCity,outsider.owner()),"actor UUID not a member");
        eq(apply(b,Action.CHAT,a,null,"Secret",outsider,cities),"permission","outsider cannot send");var chat=command(Action.CHAT,a,null,"Hello");eq(b.apply(chat,resident,aCity,cities,"Resident",NOW),"sent","resident can chat");eq(b.apply(chat,resident,aCity,cities,"Resident",NOW),"duplicate","chat duplicate rejected");eq(a.chat.size(),1,"one message for replay");
        for(int i=0;i<205;i++)apply(b,Action.CHAT,a,null,"Line "+i,aCity,cities);eq(a.chat.size(),200,"bounded retained chat");
        var publicView=AllianceService.summary(a,cities);require(!publicView.contains("Chat")&&!publicView.contains("Proposals")&&!publicView.contains("Representatives"),"public summary excludes private data");
        var retained=a.chat.getLast();var remembered=new LinkedHashSet<>(b.requests);b.requests.clear();var retainedReplay=new AllianceBook.Command(retained.id(),Action.CHAT,a.id,null,a.revision,retained.text(),"",0,0);eq(b.apply(retainedReplay,retained.author(),aCity,cities,retained.name(),NOW),"duplicate","retained message remains idempotent after request cache eviction");b.requests.addAll(remembered);
        var loaded=AllianceSavedData.load(data.save(new CompoundTag(),null),null);var restored=loaded.book.get(a.id);eq(restored.name,a.name,"name persists");eq(restored.cities,a.cities,"cities persist");eq(restored.representatives,a.representatives,"representatives persist");eq(restored.chat,a.chat,"bounded chat survives restart");eq(loaded.book.apply(chat,resident,aCity,cities,"Resident",NOW),"duplicate","request replay survives restart");
        apply(b,Action.REPRESENTATIVE,a,bCity.owner(),"",bCity,cities);apply(b,Action.INVITE,a,cCity.id(),"",aCity,cities);var pending=a.proposals.values().iterator().next();apply(b,Action.YES,a,pending.id,"",bCity,cities);require(a.invitations.containsKey(cCity.id()),"second invitation");b.reconcile(cities,NOW+AllianceBook.EXPIRY+1);require(!a.invitations.containsKey(cCity.id()),"invitation expires in real time");
        var noB=new LinkedHashMap<>(cities);noB.remove(bCity.id());require(b.reconcile(noB,NOW),"deleted city reconciles");require(!a.cities.contains(bCity.id())&&a.proposals.isEmpty(),"deleted city loses alliance access");
        eq(apply(b,Action.LEAVE,a,null,"",aCity,noB),"left","last city leaves");require(b.all().isEmpty(),"empty alliance dissolves");
        require(!AllianceBook.validText("Bad\u00a7name",48,false)&&!AllianceBook.validText("\u202Ebad",48,false)&&!AllianceBook.validText("\n",256,false),"formatting and control characters blocked");
        var empty=new AllianceSavedData();require(AllianceSavedData.load(empty.save(new CompoundTag(),null),null).book.all().isEmpty(),"existing worlds start without alliances");
        var buf=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),RegistryAccess.EMPTY);try{var request=new AllianceRequestPayload(command(Action.CHAT,a,null,"bounded"),3,4);AllianceRequestPayload.STREAM_CODEC.encode(buf,request);eq(AllianceRequestPayload.STREAM_CODEC.decode(buf),request,"bounded command codec");buf.clear();var state=new AllianceStatePayload(publicView);AllianceStatePayload.STREAM_CODEC.encode(buf,state);eq(AllianceStatePayload.STREAM_CODEC.decode(buf).data(),publicView,"public response codec");}finally{buf.release();}
        for(int[] size:new int[][]{{320,180},{426,240},{640,360},{960,540},{1920,1080}}){var l=AllianceLayout.calculate(size[0],size[1]);require(l.x()>=0&&l.y()>=0,"centered frame fits viewport");require(l.width()*l.scale()<=size[0]&&l.height()*l.scale()<=size[1],"small GUI scale fit");for(var r:List.of(l.close(),l.sidebar(),l.body(),l.field(0),l.field(1),l.compose(),l.send(),l.footer(),l.modal())){require(r.x()>=0&&r.y()>=0&&r.right()<=l.width()&&r.bottom()<=l.height(),"geometry within frame");var screen=l.screen(r,0);require(r.contains(l.localX(screen.x()+screen.width()/2d),l.localY(screen.y()+screen.height()/2d)),"render and hit transform agree");}require(l.field(1).bottom()+111<l.body().bottom()-35,"creation guidance clear of primary action");}
        System.out.println("PASS: "+count+" alliance authority, council, replay, privacy, persistence, codec and adaptive layout assertions");
    }
}
