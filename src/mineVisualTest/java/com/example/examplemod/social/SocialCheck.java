package com.example.examplemod.social;
import com.example.examplemod.network.*;
import com.example.examplemod.client.screen.SocialLayout;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
import java.util.*;
import static com.example.examplemod.social.SocialBook.*;
public final class SocialCheck {
    private static int checks;
    private static void require(boolean b,String reason){checks++;if(!b)throw new AssertionError(reason);}
    private static String apply(SocialBook b,UUID a,UUID c,Action action,String text){return b.apply(a,c,action,b.revision(a),text,0,1000,UUID.randomUUID());}
    public static void main(String[] args){
        var data=new SocialSavedData();var b=data.book;UUID a=UUID.randomUUID(),c=UUID.randomUUID(),outsider=UUID.randomUUID();
        require(apply(b,a,c,Action.SEND,"no").equals("permission"),"stranger cannot send");
        require(apply(b,a,a,Action.REQUEST,"").equals("self"),"self request rejected");
        require(apply(b,a,c,Action.REQUEST,"").equals("ok"),"friend request");require(b.relation(c,a)==Relation.INCOMING,"recipient sees incoming");
        require(apply(b,outsider,c,Action.ACCEPT,"").equals("state"),"outsider cannot accept another request");
        require(apply(b,a,c,Action.ACCEPT,"").equals("state"),"sender cannot accept own request");
        require(apply(b,c,a,Action.ACCEPT,"").equals("ok"),"receiver accepts");require(b.relation(a,c)==Relation.FRIEND&&b.relation(c,a)==Relation.FRIEND,"mutual friendship");
        long revision=b.revision(a);UUID nonce=UUID.randomUUID();require(b.apply(a,c,Action.SEND,revision,"Привет 👋",0,1234,nonce).equals("ok"),"send unicode");
        require(b.apply(a,c,Action.SEND,revision,"Привет 👋",0,1234,nonce).equals("duplicate"),"replay acknowledged without second message");
        require(b.apply(a,c,Action.SEND,revision,"different",0,1234,UUID.randomUUID()).equals("stale"),"old revision cannot send new message");
        require(b.messages(outsider,c,0).isEmpty()&&b.messages(outsider,a,0).isEmpty(),"outsider has no private history");require(b.unread(c,a)==1&&b.unread(a,c)==0,"recipient-only unread");
        long last=b.messages(c,a,0).getLast().id();require(b.apply(c,a,Action.READ,0,"",last+1,0,UUID.randomUUID()).equals("invalid"),"cannot mark unseen future read");
        require(b.apply(c,a,Action.READ,0,"",last,0,UUID.randomUUID()).equals("ok")&&b.unread(c,a)==0,"mark actual returned cursor read");
        require(apply(b,a,c,Action.SEND,"\n").equals("invalid"),"control text rejected");require(apply(b,a,c,Action.SEND,"x".repeat(501)).equals("invalid"),"oversized text rejected");
        var restored=SocialSavedData.load(data.save(new CompoundTag(),null),null);var rb=restored.book;
        require(rb.relation(a,c)==Relation.FRIEND&&rb.messages(c,a,0).getFirst().text().equals("Привет 👋"),"restart keeps friends and message");
        require(rb.unread(c,a)==0,"restart keeps read cursor");require(rb.apply(a,c,Action.SEND,revision,"retry",0,9999,nonce).equals("duplicate"),"restart preserves replay protection");
        require(apply(rb,c,a,Action.REMOVE,"").equals("ok"),"remove friend");require(apply(rb,a,c,Action.SEND,"blocked").equals("permission"),"remove disables messaging");require(!rb.messages(a,c,0).isEmpty(),"remove preserves history");
        require(apply(rb,a,c,Action.REQUEST,"").equals("ok"),"can request again");require(apply(rb,c,a,Action.BLOCK,"").equals("ok"),"block cancels request");require(rb.requests.isEmpty()&&rb.relation(a,c)==Relation.UNAVAILABLE,"blocking cancels and hides reason from sender");
        require(apply(rb,a,c,Action.REQUEST,"").equals("state"),"blocked cannot re-request");require(apply(rb,c,a,Action.UNBLOCK,"").equals("ok")&&rb.relation(a,c)==Relation.NONE,"unblock does not restore friendship");
        apply(rb,a,c,Action.REQUEST,"");apply(rb,c,a,Action.ACCEPT,"");
        for(int i=0;i<225;i++)require(apply(rb,a,c,Action.SEND,"message "+i).equals("ok"),"history append");
        require(rb.dialogs.get(Pair.of(a,c)).size()==HISTORY_LIMIT,"bounded history");var latest=rb.messages(a,c,0);var earlier=rb.messages(a,c,latest.getFirst().id());require(latest.size()==20&&earlier.size()==20&&earlier.getLast().id()<latest.getFirst().id(),"history paging has no overlaps");
        var capped=new SocialBook();for(int i=0;i<CONTACT_LIMIT;i++)apply(capped,a,UUID.randomUUID(),Action.REQUEST,"");require(apply(capped,a,outsider,Action.REQUEST,"").equals("full"),"request spam cannot exceed cap");
        var global=new SocialBook();for(int group=0;group<250;group++){
            var pair=Pair.of(UUID.randomUUID(),UUID.randomUUID());var history=new ArrayList<Message>();
            for(int i=0;i<200;i++)history.add(new Message(++global.sequence,UUID.randomUUID(),pair.first(),0,"old"));global.dialogs.put(pair,history);
        }
        global.friends.add(Pair.of(a,c));require(apply(global,a,c,Action.SEND,"new").equals("ok"),"global retention accepts new messages");
        require(global.dialogs.values().stream().mapToInt(List::size).sum()==GLOBAL_MESSAGES,"global history budget stays bounded");
        packets(a,c,latest);
        for(int width:new int[]{240,320,480,700,960})for(int height:new int[]{180,240,360,600}){
            var l=SocialLayout.calculate(width,height);require(l.x()>=0&&l.y()>=0&&l.width()*l.scale()<=width&&l.height()*l.scale()<=height,"window fits GUI scale");
            require(l.messages().height()>0&&l.input().right()<l.send().x(),"message and composer geometry");
            var r=l.send();require(r.contains(l.localX(l.x()+(r.x()+2)*l.scale()),l.localY(l.y()+(r.y()+2)*l.scale())),"hitbox uses render transform");
            require(l.screen(l.messages()).height()>0,"absolute scissor exists");
        }
        System.out.println("PASS: "+checks+" social lifecycle, privacy, replay, persistence, packet and layout checks");
    }
    private static void packets(UUID a,UUID c,List<Message> messages){var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);try {
        var request=new SocialRequestPayload(UUID.randomUUID(),c,Action.SEND,3,"hello","name",0,0,0);SocialRequestPayload.STREAM_CODEC.encode(buffer,request);require(SocialRequestPayload.STREAM_CODEC.decode(buffer).equals(request),"request codec");buffer.clear();
        var state=new SocialStatePayload(request.request(),"ok",4,c,"Friend",Relation.FRIEND,0,0,1,List.of(new SocialStatePayload.Contact(c,"Friend",false,Relation.FRIEND,3)),messages,true,0,3);SocialStatePayload.STREAM_CODEC.encode(buffer,state);require(SocialStatePayload.STREAM_CODEC.decode(buffer).equals(state),"bounded response codec");
    }finally{buffer.release();}}
}
