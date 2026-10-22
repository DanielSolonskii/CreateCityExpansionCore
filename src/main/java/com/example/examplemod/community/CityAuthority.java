package com.example.examplemod.community;
import com.example.examplemod.city.*;
import java.util.UUID;
/** Existing city roles, explicit capabilities. Ordinals 0..3 retain their original meaning. */
public final class CityAuthority {
    private CityAuthority(){}
    public static boolean finance(City c,UUID id){return c!=null&&(c.isAdmin(id)||role(c,id)==CityMember.Role.TREASURER);}
    public static boolean review(City c,UUID id){return c!=null&&(c.isAdmin(id)||role(c,id)==CityMember.Role.ENGINEER);}
    public static CityMember.Role role(City c,UUID id){return c==null||c.getMember(id)==null?null:c.getMember(id).getRole();}
    public static boolean assign(City c,UUID actor,UUID target,CityMember.Role next){
        return c!=null&&c.isAdmin(actor)&&!actor.equals(target)&&c.hasMember(target)&&!c.isOwner(target)&&next!=CityMember.Role.OWNER
            &&(c.isOwner(actor)||!c.isAdmin(target)&&next!=CityMember.Role.ADMIN);
    }
}
