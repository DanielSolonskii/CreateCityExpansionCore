package com.example.examplemod.community;
import com.example.examplemod.city.*;
import com.example.examplemod.client.screen.CommunityLayout;
import com.example.examplemod.network.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
import java.util.*;
public final class CommunityCheck {
    private static int checks;private static void require(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        UUID owner=UUID.randomUUID(),admin=UUID.randomUUID(),builder=UUID.randomUUID(),engineer=UUID.randomUUID(),treasurer=UUID.randomUUID(),resident=UUID.randomUUID(),other=UUID.randomUUID();
        require(CityMember.Role.OWNER.ordinal()==0&&CityMember.Role.ADMIN.ordinal()==1&&CityMember.Role.BUILDER.ordinal()==2&&CityMember.Role.RESIDENT.ordinal()==3,"legacy role IDs preserved");
        City c=new City(UUID.randomUUID(),"Town",owner,BlockPos.ZERO);c.addMember(admin,CityMember.Role.ADMIN);c.addMember(builder,CityMember.Role.BUILDER);c.addMember(engineer,CityMember.Role.ENGINEER);c.addMember(treasurer,CityMember.Role.TREASURER);c.addMember(resident,CityMember.Role.RESIDENT);
        require(c.canBuild(engineer)&&!c.canBuild(treasurer),"engineer builds; treasury role has no building grant");require(!CityAuthority.finance(c,engineer)&&CityAuthority.review(c,engineer),"engineering cannot approve treasury");require(CityAuthority.finance(c,treasurer)&&!CityAuthority.review(c,treasurer),"treasurer approves funding only");require(!CityAuthority.finance(c,builder)&&!CityAuthority.review(c,builder)&&c.canBuild(builder),"legacy builders not silently elevated");
        require(!CityAuthority.assign(c,admin,owner,CityMember.Role.RESIDENT),"owner immutable");require(!CityAuthority.assign(c,admin,admin,CityMember.Role.TREASURER),"self assignment forbidden");require(!CityAuthority.assign(c,resident,builder,CityMember.Role.ADMIN),"resident cannot assign");require(!CityAuthority.assign(c,admin,resident,CityMember.Role.ADMIN),"deputy cannot promote deputy");require(CityAuthority.assign(c,owner,resident,CityMember.Role.ADMIN),"owner can appoint deputy");require(!CityAuthority.assign(c,owner,resident,CityMember.Role.OWNER),"ownership transfer never disguised as role change");
        c.addInvite(other,admin);UUID token=c.getInvite(other).token();c.removeInvite(other);c.addInvite(other,admin);require(!token.equals(c.getInvite(other).token()),"revoked invitation token never reused");
        var manager=new CityManager();var saved=manager.createCity("Saved",owner,net.minecraft.resources.ResourceLocation.parse("minecraft:overworld"),BlockPos.ZERO);saved.addMember(treasurer,CityMember.Role.TREASURER);saved.addInvite(other,owner);UUID invite=saved.getInvite(other).token();
        var restored=CityManager.load(manager.save(new CompoundTag(),null),null).getCity(saved.getId());require(restored.getInvite(other).token().equals(invite),"invite token survives save/restart");require(restored.getMember(treasurer).getRole()==CityMember.Role.TREASURER,"new role persists");
        var legacy=manager.save(new CompoundTag(),null);((CompoundTag)((CompoundTag)legacy.getList("Cities",10).getFirst()).getList("Invites",10).getFirst()).remove("Token");require(CityManager.load(legacy,null).getCity(saved.getId()).getInvite(other).token()!=null,"old invites migrate additively");
        var data=new CommunityData();data.enqueue(owner,other,"MESSAGE","private",false);require(data.outbox.isEmpty(),"Telegram opt-in required");data.preferences.put(owner,new CommunityData.Preferences(true,false,true,false,180));data.enqueue(owner,other,"MESSAGE","secret",true);require(data.outbox.isEmpty(),"offline-only excludes online owner");data.enqueue(owner,other,"MESSAGE","secret",false);require(data.outbox.size()==1&&data.outbox.getFirst().text().isEmpty(),"default notification hides contents");data.enqueue(owner,other,"MESSAGE","second secret",false);require(data.outbox.size()==1,"notification group coalesces");
        var reloaded=CommunityData.load(data.save(new CompoundTag(),null),null);require(reloaded.outbox.size()==1&&reloaded.preferences(owner).telegram(),"outbox and opt-in persist");reloaded.muted.put(owner,new HashSet<>(Set.of(other)));reloaded.enqueue(owner,other,"INVITE","Town",false);require(reloaded.outbox.size()==1,"muted peers do not enqueue");
        for(int w:new int[]{240,320,432,700,1000})for(int h:new int[]{180,240,360,700}){var l=CommunityLayout.calculate(w,h);require(l.x()>=0&&l.y()>=0&&l.width()*l.scale()<=w&&l.height()*l.scale()<=h,"community window fits GUI");var offscreen=new CommunityLayout.Rect(l.body().x(),40,120,40);require(!l.hit(offscreen,true,offscreen.x()+2,50),"scrolled content cannot click navigation");require(l.hit(l.nav(0),false,l.nav(0).x()+2,l.nav(0).y()+2),"navigation hitbox matches paint");var clip=l.screen(l.body());require(clip.width()>0&&clip.height()>0,"absolute transformed scissor");}
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);try{var r=new CommunityRequestPayload(UUID.randomUUID(),CommunityRequestPayload.Action.ROLE,c.getId(),resident,null,5,0,"Town");CommunityRequestPayload.STREAM_CODEC.encode(b,r);require(CommunityRequestPayload.STREAM_CODEC.decode(b).equals(r),"bounded community codec");}finally{b.release();}
        System.out.println("PASS: "+checks+" community roles, invite migration, notification privacy and layout checks");
    }
}
