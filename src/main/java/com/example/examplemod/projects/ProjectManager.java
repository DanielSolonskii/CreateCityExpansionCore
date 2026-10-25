package com.example.examplemod.projects;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.BiPredicate;
import static com.example.examplemod.projects.Project.*;

/** One lifecycle, with scope-specific authority/funding. All calls run on the server thread. */
public final class ProjectManager {
    public enum Action { REQUEST, CREATE, UPDATE, PUBLISH, APPROVE, REJECT_FUNDING, APPLY, ASSIGN, STAGE, SUBMIT, ACCEPT, RETURN, CANCEL, APPLY_CITY, ASSIGN_CITY, WITHDRAW }
    public record Actor(UUID player, UUID city, boolean admin, Predicate<UUID> member, Predicate<ProjectZone> owns,
                        boolean op, BiPredicate<UUID,UUID> representsCity, Predicate<ProjectZone> conflicts, boolean finance, boolean reviewer) {
        public Actor(UUID player,UUID city,boolean admin,Predicate<UUID> member,Predicate<ProjectZone> owns,
                boolean op,BiPredicate<UUID,UUID> representsCity,Predicate<ProjectZone> conflicts) {
            this(player,city,admin,member,owns,op,representsCity,conflicts,admin,admin);
        }
        public Actor(UUID player,UUID city,boolean admin,Predicate<UUID> member,Predicate<ProjectZone> owns) {
            this(player,city,admin,member,owns,false,(id,representative)->false,zone->false);
        }
    }
    public record Draft(Scope scope,String title,String description,Category category,ProjectZone zone,List<Stage> stages,Funding funding,long reward) {}
    public record Command(UUID request,UUID project,long revision,Action action,UUID target,int stage,boolean done,String note,Draft draft) {}
    public interface Accounts {
        long balance(boolean city,UUID id);
        boolean debit(boolean city,UUID id,long amount);
        boolean canCredit(boolean city,UUID id,long amount);
        void credit(boolean city,UUID id,long amount);
        long maximum();
        default long serverBudget(){return 0;}
        void dirty();
    }
    private final Accounts accounts;
    private final Map<UUID,Project> projects=new LinkedHashMap<>();
    private long serverIssued;
    private final ProjectJournal journal=new ProjectJournal();
    public ProjectJournal journal(){return journal;}
    public ProjectManager(Accounts accounts){this.accounts=Objects.requireNonNull(accounts);}
    public List<Project> all(){return List.copyOf(projects.values());}
    public Project get(UUID id){return projects.get(id);}
    public long serverIssued(){return serverIssued;}
    public long serverReserved(){return projects.values().stream().filter(p->p.scope()==Scope.SERVER).mapToLong(Project::reserved).reduce(0,Math::addExact);}
    public long serverLimit(){return Math.max(0,accounts.serverBudget());}
    public long serverAvailable(){return Math.max(0,serverLimit()-serverIssued-Math.min(serverReserved(),Math.max(0,serverLimit()-serverIssued)));}
    public boolean hasActive(UUID city){return projects.values().stream().anyMatch(p->Objects.equals(p.city(),city)&&!p.status().terminal());}
    public List<Project> visible(Actor actor,int filter) {
        return projects.values().stream().filter(p->p.scope()==Scope.SERVER||actor.city()!=null&&Objects.equals(p.city(),actor.city()))
                .filter(p->p.status()!=Status.DRAFT||(p.scope()==Scope.SERVER?actor.op():p.author().equals(actor.player())||actor.admin()))
                .filter(p->switch(filter){case 1->p.author().equals(actor.player())||p.executor()!=null&&p.executor().representative().equals(actor.player());
                    case 2->p.status()==Status.REVIEW||p.status()==Status.PENDING_FUNDING;case 3->p.status().terminal();default->!p.status().terminal();})
                .sorted(Comparator.comparing((Project p)->p.status().ordinal()).thenComparing(Project::title).thenComparing(Project::id)).toList();
    }
    /** Errors are stable localization keys; no partial updates on a rejected command. */
    public synchronized String execute(Actor a,Command c) {
        if(a==null||a.player()==null)return "membership";
        if(c==null||c.action()==null||c.request()==null||c.note()==null||c.note().length()>512)return "invalid";
        if(c.action()==Action.REQUEST)return "ok";
        if(c.action()==Action.CREATE) {
            boolean server=c.draft()!=null&&c.draft().scope()==Scope.SERVER;
            if(server?!a.op():a.city()==null||!a.member().test(a.player()))return server?"permission":"membership";
            if(projects.values().stream().anyMatch(p->p.author().equals(a.player())&&p.creationRequest().equals(c.request())))return "ok";
            if(projects.values().stream().filter(p->p.scope()==(server?Scope.SERVER:Scope.CITY)&&(server||Objects.equals(a.city(),p.city()))&&!p.status().terminal()).count()>=64)return "limit";
            String error=validate(a,c.draft());if(!error.equals("ok"))return error;
            Draft d=c.draft();Project p=new Project(UUID.randomUUID(),c.request(),d.scope(),server?null:a.city(),a.player(),d.title().strip(),d.description().strip(),d.category(),d.zone(),
                    d.stages().stream().map(s->new Stage(s.title().strip(),false)).toList(),d.funding(),d.reward(),Status.DRAFT,List.of(),null,0,false,0,"");
            projects.put(p.id(),p);journal.record(p.id(),c.request(),a.player(),c.action().name(),p.revision(),System.currentTimeMillis());accounts.dirty();return "ok";
        }
        Project p=projects.get(c.project());
        if(p==null)return "missing";
        boolean server=p.scope()==Scope.SERVER;
        if(!server&&!Objects.equals(p.city(),a.city()))return "missing";
        if(!server&&(a.city()==null||!a.member().test(a.player())))return "membership";
        if(server&&p.status()==Status.DRAFT&&!a.op())return "permission";
        if(server&&EnumSet.of(Action.UPDATE,Action.PUBLISH,Action.APPROVE,Action.REJECT_FUNDING,Action.ASSIGN,Action.ASSIGN_CITY,Action.ACCEPT,Action.RETURN,Action.CANCEL,Action.WITHDRAW).contains(c.action())&&!a.op())return "permission";
        if(p.revision()!=c.revision())return "stale";
        if(p.revision()==Long.MAX_VALUE)return "invalid";
        if(p.status().terminal())return "terminal";
        boolean author=p.author().equals(a.player()),manage=server?a.op():author||a.admin()||a.reviewer(),executor=p.executor()!=null&&p.executor().representative().equals(a.player())&&validApplicant(a,p.executor(),server);
        Status state=p.status();List<Stage> stages=p.stages();List<Applicant> applications=p.applicants();Applicant assigned=p.executor();
        long reserve=p.reserved();boolean paid=p.paid();String note=p.reviewNote();
        boolean payerCity=p.funding()==Funding.TREASURY;UUID payer=payerCity?p.city():p.author();
        switch(c.action()) {
            case WITHDRAW -> {
                if(!server||!a.op()||state!=Status.OPEN)return "permission";
                state=Status.DRAFT;reserve=0;applications=List.of();note="";
            }
            case UPDATE -> {
                if(!manage||state!=Status.DRAFT)return "permission";
                if(c.draft()==null||c.draft().scope()!=p.scope())return "invalid";
                String error=validate(a,c.draft());if(!error.equals("ok"))return error;
                Draft d=c.draft();p=new Project(p.id(),p.creationRequest(),p.scope(),p.city(),p.author(),d.title().strip(),d.description().strip(),d.category(),d.zone(),
                        d.stages().stream().map(s->new Stage(s.title().strip(),false)).toList(),d.funding(),d.reward(),state,applications,assigned,0,false,p.revision(),note);
            }
            case PUBLISH -> {
                if(!manage||state!=Status.DRAFT)return "permission";
                if(server) {
                    if(a.conflicts().test(p.zone())&&!c.done())return "conflict";
                    if(p.reward()>serverAvailable())return "budget";
                    reserve=p.reward();state=Status.OPEN;
                }
                else if(!a.owns().test(p.zone()))return "territory";
                else if(p.funding()==Funding.TREASURY)state=Status.PENDING_FUNDING;
                else { if(p.reward()>0&&!accounts.debit(false,p.author(),p.reward()))return "funds";reserve=p.reward();state=Status.OPEN; }
            }
            case APPROVE -> {
                if(!a.finance()||state!=Status.PENDING_FUNDING)return "permission";
                if(!a.owns().test(p.zone()))return "territory";
                if(!accounts.debit(true,p.city(),p.reward()))return "funds";
                reserve=p.reward();state=Status.OPEN;
            }
            case REJECT_FUNDING -> {
                if(!a.finance()||state!=Status.PENDING_FUNDING)return "permission";
                if(c.note().isBlank())return "reason";
                state=Status.DRAFT;note=c.note();
            }
            case APPLY, APPLY_CITY -> {
                if(state!=Status.OPEN)return "state";
                if(c.action()==Action.APPLY_CITY&&(!server||a.city()==null||!a.representsCity().test(a.city(),a.player())))return "permission";
                Applicant applicant=c.action()==Action.APPLY_CITY?new Applicant(ApplicantKind.CITY,a.city(),a.player()):Applicant.player(a.player());
                Applicant existing=applications.stream().filter(app->app.kind()==applicant.kind()&&app.id().equals(applicant.id())).findFirst().orElse(null);
                if(existing!=null&&validApplicant(a,existing,server))return "ok";
                if(existing==null&&applications.size()>=128)return "limit";
                applications=new ArrayList<>(applications);applications.remove(existing);applications.add(applicant);
            }
            case ASSIGN, ASSIGN_CITY -> {
                boolean abandoned=assigned!=null&&!validApplicant(a,assigned,server);
                if(!manage||state!=Status.OPEN&&!((server||abandoned)&&(state==Status.BUILDING||state==Status.REWORK||state==Status.REVIEW)))return "permission";
                if(c.target()==null)return "applicant";
                ApplicantKind kind=c.action()==Action.ASSIGN_CITY?ApplicantKind.CITY:ApplicantKind.PLAYER;
                Applicant target=applications.stream().filter(app->app.kind()==kind&&app.id().equals(c.target())).findFirst().orElse(null);
                if(target==null||!validApplicant(a,target,server))return "applicant";
                assigned=target;state=Status.BUILDING;
                stages=stages.stream().map(s->new Stage(s.title(),false)).toList();
            }
            case STAGE -> {
                if(!executor||state!=Status.BUILDING&&state!=Status.REWORK)return "permission";
                if(c.stage()<0||c.stage()>=stages.size())return "invalid";
                stages=new ArrayList<>(stages);stages.set(c.stage(),new Stage(stages.get(c.stage()).title(),c.done()));
            }
            case SUBMIT -> {
                if(!executor||state!=Status.BUILDING&&state!=Status.REWORK)return "permission";
                if(stages.stream().anyMatch(s->!s.done()))return "stages";
                state=Status.REVIEW;
            }
            case RETURN -> {
                if(!manage||executor||state!=Status.REVIEW)return "permission";
                if(c.note().isBlank())return "reason";
                state=Status.REWORK;note=c.note();
            }
            case ACCEPT -> {
                if(!manage||executor||state!=Status.REVIEW)return "permission";
                if(assigned==null||!validApplicant(a,assigned,server))return "applicant";
                if(!server&&!a.owns().test(p.zone()))return "territory";
                if(server&&a.conflicts().test(p.zone())&&!c.done())return "conflict";
                if(stages.stream().anyMatch(s->!s.done())||reserve!=p.reward()||p.paid())return "state";
                boolean cityAccount=assigned.kind()==ApplicantKind.CITY;
                if(!accounts.canCredit(cityAccount,assigned.id(),reserve))return "overflow";
                long issued=server?Math.addExact(serverIssued,reserve):serverIssued;
                accounts.credit(cityAccount,assigned.id(),reserve);serverIssued=issued;reserve=0;paid=true;state=Status.COMPLETED;
            }
            case CANCEL -> {
                if(server?!a.op():assigned==null?!manage:!(a.admin()||a.reviewer()))return "permission";
                if(assigned!=null&&c.note().isBlank())return "reason";
                if(!server) {
                    if(!accounts.canCredit(payerCity,payer,reserve))return "overflow";
                    accounts.credit(payerCity,payer,reserve);
                }
                reserve=0;state=Status.CANCELLED;note=c.note();
            }
            default -> {return "invalid";}
        }
        Project updated=new Project(p.id(),p.creationRequest(),p.scope(),p.city(),p.author(),p.title(),p.description(),p.category(),p.zone(),stages,p.funding(),p.reward(),state,
                applications,assigned,reserve,paid,Math.addExact(p.revision(),1),note);
        projects.put(p.id(),updated);journal.record(p.id(),c.request(),a.player(),c.action().name(),updated.revision(),System.currentTimeMillis());accounts.dirty();return "ok";
    }
    private String validate(Actor a,Draft d) {
        if(d==null)return "invalid";
        boolean server=d.scope()==Scope.SERVER;
        if(server&&!a.op())return "permission";
        if(!server&&d.funding()==Funding.SERVER_ISSUANCE||server&&d.funding()!=Funding.SERVER_ISSUANCE)return "scope_disabled";
        if(d.reward()>accounts.maximum())return "invalid";
        try {new Project(UUID.randomUUID(),UUID.randomUUID(),d.scope(),server?null:a.city(),a.player(),d.title(),d.description(),d.category(),d.zone(),d.stages(),d.funding(),d.reward(),Status.DRAFT,List.of(),null,0,false,0,"");}
        catch(RuntimeException ex){return "invalid";}
        return server||a.owns().test(d.zone())?"ok":"territory";
    }
    private boolean validApplicant(Actor actor,Applicant applicant,boolean server) {
        return applicant.kind()==ApplicantKind.CITY?server&&actor.representsCity().test(applicant.id(),applicant.representative())
                :server||actor.member().test(applicant.representative());
    }
    public net.minecraft.nbt.CompoundTag save(){var tag=ProjectCodec.save(all());tag.putLong("ServerIssued",serverIssued);tag.put("CommunityJournal",journal.save());return tag;}
    public void load(net.minecraft.nbt.CompoundTag tag) {
        journal.load(tag.getCompound("CommunityJournal"));projects.clear();for(Project p:ProjectCodec.load(tag)) {
            if(projects.putIfAbsent(p.id(),p)!=null)throw new IllegalStateException("Duplicate project in save");
        }
        long paid=projects.values().stream().filter(p->p.scope()==Scope.SERVER&&p.paid()).mapToLong(Project::reward).reduce(0,Math::addExact);
        serverIssued=tag.contains("ServerIssued")?tag.getLong("ServerIssued"):paid;
        if(serverIssued<0||serverIssued!=paid)throw new IllegalStateException("Server projects issuance ledger mismatch");
        Math.addExact(serverIssued,serverReserved());
    }
}
