package com.example.examplemod.city;

import com.example.examplemod.community.CityAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import java.util.*;

/** Actual City/CityManager save and authorization contracts, including deop and restart. */
public final class DualCityMembershipCheck {
    private static int checks;
    private static void require(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void run() {
        var manager=new CityManager();var ops=new LinkedHashMap<UUID,String>();
        UUID op=UUID.randomUUID(),offline=UUID.randomUUID(),owner=UUID.randomUUID();
        var home=manager.createCity("Основной",op,BlockPos.ZERO);
        var morzh=manager.createCity("Моржеград",owner,new BlockPos(1000,64,0));
        var other=manager.createCity("Другой",UUID.randomUUID(),new BlockPos(2000,64,0));
        manager.operatorRoster(()->Map.copyOf(ops));ops.put(op,"Operator");ops.put(offline,"OfflineOp");
        require(manager.getMemberCity(op)==home,"primary city unaffected by supplemental operator membership");
        require(manager.getMemberCities(op).size()==2&&manager.getMemberCities(op).contains(morzh),"operator belongs to both cities");
        require(manager.getMemberCity(offline)==null&&manager.getMemberCities(offline).equals(List.of(morzh)),"offline operator without primary city is included");
        require(morzh.getMember(op).getRole()==CityMember.Role.OPERATOR_MANAGER,"supplemental role is explicit");
        require(morzh.getMemberCount()==3&&morzh.getMembers().stream().map(CityMember::getPlayerId).distinct().count()==3,"member list and count agree without duplicates");
        require(morzh.canManageInfrastructure(op)&&morzh.canBuild(op)&&manager.allowsTerritoryAction(morzh,op,true),"operator has infrastructure and territory access");
        require(!morzh.isAdmin(op)&&!morzh.isOwner(op)&&!CityAuthority.finance(morzh,op),"supplemental role grants neither political administration nor treasury permissions");
        require(!other.hasMember(op)&&!other.canManageInfrastructure(op),"other cities are not granted operator membership");
        require(!CityMembershipService.mayTransfer(morzh,owner,op)&&!morzh.transferOwnership(owner,op),"ephemeral membership cannot become permanent ownership");
        require(!CityMembershipService.mayKick(morzh,owner,op)&&!CityAuthority.assign(morzh,owner,op,CityMember.Role.ADMIN),"ephemeral membership cannot be reassigned or kicked through ordinary member tools");
        morzh.setMemberRole(op,CityMember.Role.ADMIN);morzh.addMember(offline,CityMember.Role.OPERATOR_MANAGER);
        require(!morzh.isAdmin(op)&&!morzh.hasPrimaryMember(offline),"model refuses persistent synthetic roles");
        var saved=manager.save(new CompoundTag(),null);
        require(saved.getUUID("OperatorCity").equals(morzh.getId()),"Morzhеgrad identity is persisted separately from master city");
        for(var raw:saved.getList("Cities",10)) {
            var city=(CompoundTag)raw;if(!city.getUUID("Id").equals(morzh.getId()))continue;
            require(city.getList("Members",10).size()==1,"operator roster is not written as permanent city membership");
            city.putString("Name","Переименованный Моржеград");
        }
        var loaded=CityManager.load(saved,null);loaded.operatorRoster(()->Map.copyOf(ops));
        var restored=loaded.getCity(morzh.getId());
        require(restored.hasMember(op)&&loaded.getMemberCities(op).size()==2,"restart and rename preserve identity binding and both cities");
        ops.remove(op);
        require(!restored.hasMember(op)&&!restored.canManageInfrastructure(op)&&!restored.canBuild(op),"deop immediately removes supplemental access");
        require(loaded.getMemberCity(op).getId().equals(home.getId())&&loaded.getMemberCities(op).size()==1,"deop preserves normal city");
        ops.put(op,"Operator");require(restored.hasMember(op)&&loaded.getMemberCities(op).size()==2,"reop restores supplemental membership without duplicates");
        restored.addMember(offline,CityMember.Role.RESIDENT);
        require(restored.getMembers().stream().filter(m->m.getPlayerId().equals(offline)).count()==1,"regular member who is also OP is listed only once");
        ops.remove(offline);
        require(restored.hasMember(offline)&&restored.getMember(offline).getRole()==CityMember.Role.RESIDENT&&!restored.canManageInfrastructure(offline),"deop retains independently granted regular membership only");
        require(!CityAuthority.assign(restored,owner,offline,CityMember.Role.OPERATOR_MANAGER),"synthetic role cannot be assigned to an ordinary member");
        var removed=saved.copy();var retained=new ListTag();for(var raw:removed.getList("Cities",10))if(!((CompoundTag)raw).getUUID("Id").equals(morzh.getId()))retained.add(raw.copy());removed.put("Cities",retained);
        var replacement=CityManager.load(removed,null);replacement.operatorRoster(()->Map.copyOf(ops));
        var impostor=replacement.createCity("Моржеград",UUID.randomUUID(),new BlockPos(3000,64,0));
        require(!impostor.hasMember(op),"deleted bound city is not silently replaced by a namesake");
        var noCity=new CityManager();noCity.operatorRoster(()->Map.of(op,"Operator"));
        require(noCity.getMemberCities(op).isEmpty()&&noCity.getOperatorCityId()==null,"missing Morzhеgrad grants no membership and creates no city");
        noCity.createCity("Моржеград",UUID.randomUUID(),BlockPos.ZERO);noCity.createCity("МОРЖЕГРАД",UUID.randomUUID(),new BlockPos(1000,64,0));
        require(noCity.getOperatorCityId()==null&&noCity.getMemberCities(op).isEmpty(),"ambiguous names never grant arbitrary city access");
        System.out.println("PASS: "+checks+" dual city membership, deop, permissions and persistence checks");
    }
}
