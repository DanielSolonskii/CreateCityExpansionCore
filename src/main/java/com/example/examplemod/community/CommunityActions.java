package com.example.examplemod.community;
import com.example.examplemod.projects.*;
import java.util.*;
/** A read-only action explanation; the existing manager always validates execution again. */
public final class CommunityActions {
    private CommunityActions(){}
    public static String attention(Project p,ProjectManager.Actor a){
        boolean manager=p.scope()==Project.Scope.SERVER?a.op():p.author().equals(a.player())||a.admin()||a.reviewer();
        boolean executor=p.executor()!=null&&p.executor().representative().equals(a.player());
        if(p.status()==Project.Status.OPEN&&manager&&!p.applicants().isEmpty())return "assign";
        if(p.status()==Project.Status.PENDING_FUNDING&&a.finance())return "fund";
        if(p.status()==Project.Status.REVIEW&&manager&&!executor)return "review";
        if((p.status()==Project.Status.BUILDING||p.status()==Project.Status.REWORK)&&executor)return "work";
        return "";
    }
    public static boolean visible(Project p,ProjectManager.Actor a){return (p.scope()==Project.Scope.SERVER||Objects.equals(p.city(),a.city())&&a.city()!=null)&&(p.status()!=Project.Status.DRAFT||(p.scope()==Project.Scope.SERVER?a.op():p.author().equals(a.player())||a.admin()));}
    public static boolean contributor(Project p,UUID player){return p.author().equals(player)||p.executor()!=null&&p.executor().representative().equals(player);}
}
