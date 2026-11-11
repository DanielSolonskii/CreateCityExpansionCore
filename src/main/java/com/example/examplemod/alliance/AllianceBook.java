package com.example.examplemod.alliance;

import java.util.*;

/** Server-thread aggregate. Cities and authority are always supplied from the live CityManager. */
public final class AllianceBook {
    public static final int MAX_CITIES=32, MAX_ALLIANCES=256, MAX_PROPOSALS=16, MAX_CHAT=200;
    public static final long EXPIRY=7L*24*60*60*1000;
    public enum Action { VIEW, DIRECTORY, PLAYER, CREATE, INVITE, RENAME, EXCLUDE, YES, NO, ACCEPT, DECLINE, LEAVE, CHAT, REPRESENTATIVE }
    public record CityRef(UUID id, String name, UUID owner, Set<UUID> members) {
        public CityRef { members=Set.copyOf(members); }
    }
    public record Command(UUID request,Action action,UUID alliance,UUID target,long revision,String text,String description,int color,int emblem) {}
    public record Message(UUID id,UUID author,UUID city,String name,String text,long time) {}
    public static final class Proposal {
        public final UUID id; public final Action kind; public final UUID target; public final String text;
        public final Set<UUID> electorate; public final Map<UUID,Boolean> votes=new LinkedHashMap<>(); public final long expires;
        Proposal(UUID id,Action kind,UUID target,String text,Set<UUID> electorate,long expires){this.id=id;this.kind=kind;this.target=target;this.text=text;this.electorate=new LinkedHashSet<>(electorate);this.expires=expires;}
        public int yes(){return (int)votes.values().stream().filter(Boolean::booleanValue).count();}
        public int no(){return votes.size()-yes();}
        public int majority(){return electorate.size()/2+1;}
    }
    public static final class Alliance {
        public final UUID id; public String name,description; public final int color,emblem; public long revision;
        public final Set<UUID> cities=new LinkedHashSet<>();
        public final Map<UUID,UUID> representatives=new LinkedHashMap<>();
        public final Map<UUID,Long> invitations=new LinkedHashMap<>();
        public final Map<UUID,Proposal> proposals=new LinkedHashMap<>();
        public final List<Message> chat=new ArrayList<>();
        Alliance(UUID id,String name,String description,int color,int emblem){this.id=id;this.name=name;this.description=description;this.color=color;this.emblem=emblem;}
    }
    final Map<UUID,Alliance> alliances=new LinkedHashMap<>();
    final LinkedHashSet<UUID> requests=new LinkedHashSet<>();
    public Collection<Alliance> all(){return Collections.unmodifiableCollection(alliances.values());}
    public Alliance get(UUID id){return alliances.get(id);}
    public Alliance city(UUID city){return city==null?null:alliances.values().stream().filter(a->a.cities.contains(city)).findFirst().orElse(null);}
    public UUID representative(Alliance a,CityRef city){UUID r=a.representatives.get(city.id);return r!=null&&city.members.contains(r)?r:city.owner;}
    public boolean council(Alliance a,CityRef city,UUID actor){return city!=null&&a.cities.contains(city.id)&&actor.equals(representative(a,city));}
    public boolean reconcile(Map<UUID,CityRef> cities,long now){
        boolean changed=false;
        for(var iterator=alliances.values().iterator();iterator.hasNext();){var a=iterator.next();
            if(a.cities.removeIf(id->!cities.containsKey(id))){a.proposals.clear();a.revision++;changed=true;}
            if(a.cities.isEmpty()){iterator.remove();changed=true;continue;}
            a.representatives.keySet().retainAll(a.cities);
            if(a.invitations.entrySet().removeIf(e->e.getValue()<=now||!cities.containsKey(e.getKey())||city(e.getKey())!=null)){a.revision++;changed=true;}
            if(a.proposals.values().removeIf(p->p.expires<=now||p.target!=null&&!cities.containsKey(p.target))){a.revision++;changed=true;}
        }return changed;
    }
    public static boolean validText(String text,int max,boolean empty){return text!=null&&(empty||!text.isBlank())&&text.length()<=max&&text.codePoints().noneMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT||c==0xA7);}
    private boolean available(String name,UUID except){return alliances.values().stream().noneMatch(a->!a.id.equals(except)&&a.name.equalsIgnoreCase(name));}
    public String apply(Command c,UUID actor,CityRef own,Map<UUID,CityRef> directory,String author,long now){
        if(requests.contains(c.request))return "duplicate";
        if(own==null||!own.members.contains(actor))return "city_required";
        if(switch(c.action){case INVITE,EXCLUDE,YES,NO,REPRESENTATIVE->c.target==null;default->false;})return "invalid";
        if(c.action==Action.CREATE){
            String name=c.text.strip();if(!own.owner.equals(actor))return "owner_only";
            if(city(own.id)!=null)return "already_member";
            if(!validText(name,48,false)||!validText(c.description,180,true)||c.color<0||c.color>0xFFFFFF||c.emblem<0||c.emblem>2)return "invalid";
            if(!available(name,null))return "name_taken";if(alliances.size()>=MAX_ALLIANCES)return "full";
            var a=new Alliance(UUID.randomUUID(),name,c.description,c.color,c.emblem);a.cities.add(own.id);a.revision=1;alliances.put(a.id,a);remember(c.request);return "created";
        }
        var a=get(c.alliance);if(a==null)return "missing";
        if(c.action==Action.ACCEPT||c.action==Action.DECLINE){
            if(!own.owner.equals(actor))return "owner_only";
            if(!a.invitations.containsKey(own.id)||a.invitations.get(own.id)<=now)return "expired";
            if(c.revision!=a.revision)return "stale";
            if(c.action==Action.ACCEPT){if(city(own.id)!=null)return "already_member";if(a.cities.size()>=MAX_CITIES)return "full";a.cities.add(own.id);a.proposals.clear();}
            a.invitations.remove(own.id);a.revision++;remember(c.request);return "ok";
        }
        if(!a.cities.contains(own.id))return "permission";
        if(c.action==Action.CHAT){
            if(!validText(c.text,256,false))return "invalid";
            if(a.chat.stream().anyMatch(m->m.id.equals(c.request)))return "duplicate";
            a.chat.add(new Message(c.request,actor,own.id,author,c.text.strip(),now));while(a.chat.size()>MAX_CHAT)a.chat.removeFirst();remember(c.request);return "sent";
        }
        if(c.revision!=a.revision)return "stale";
        if(c.action==Action.LEAVE){
            if(!own.owner.equals(actor))return "owner_only";
            a.cities.remove(own.id);a.representatives.remove(own.id);a.proposals.clear();a.revision++;if(a.cities.isEmpty())alliances.remove(a.id);remember(c.request);return "left";
        }
        if(c.action==Action.REPRESENTATIVE){
            if(!own.owner.equals(actor))return "owner_only";if(!own.members.contains(c.target))return "invalid";
            a.representatives.put(own.id,c.target);a.revision++;remember(c.request);return "ok";
        }
        if(!council(a,own,actor))return "council_only";
        if(c.action==Action.YES||c.action==Action.NO){
            var p=a.proposals.get(c.target);if(p==null||p.expires<=now)return "expired";
            if(!p.electorate.contains(own.id))return "permission";if(p.votes.containsKey(own.id))return "already_voted";
            p.votes.put(own.id,c.action==Action.YES);resolve(a,p,directory,now);a.revision++;remember(c.request);return "voted";
        }
        if(c.action!=Action.INVITE&&c.action!=Action.RENAME&&c.action!=Action.EXCLUDE)return "invalid";
        if(a.proposals.size()>=MAX_PROPOSALS)return "full";
        if(c.action==Action.INVITE){if(!directory.containsKey(c.target)||city(c.target)!=null||a.invitations.containsKey(c.target))return "invalid";if(a.invitations.size()>=MAX_CITIES||a.cities.size()>=MAX_CITIES||incoming(c.target)>=32)return "full";}
        if(c.action==Action.EXCLUDE&&(!a.cities.contains(c.target)||own.id.equals(c.target)))return "invalid";
        if(c.action==Action.RENAME&&(!validText(c.text.strip(),48,false)||!available(c.text.strip(),a.id)))return "name_taken";
        if(a.proposals.values().stream().anyMatch(p->p.kind==c.action&&Objects.equals(p.target,c.target)))return "pending";
        var electorate=new LinkedHashSet<>(a.cities);if(c.action==Action.EXCLUDE)electorate.remove(c.target);
        var p=new Proposal(UUID.randomUUID(),c.action,c.target,c.text.strip(),electorate,now+EXPIRY);p.votes.put(own.id,true);a.proposals.put(p.id,p);
        resolve(a,p,directory,now);a.revision++;remember(c.request);return "proposed";
    }
    private void resolve(Alliance a,Proposal p,Map<UUID,CityRef> directory,long now){
        if(p.yes()>=p.majority()){
            switch(p.kind){
                case INVITE -> {if(city(p.target)==null&&directory.containsKey(p.target)&&a.invitations.size()<MAX_CITIES&&incoming(p.target)<32)a.invitations.put(p.target,now+EXPIRY);}
                case RENAME -> {if(available(p.text,a.id))a.name=p.text;}
                case EXCLUDE -> {a.cities.remove(p.target);a.representatives.remove(p.target);a.proposals.clear();}
                default -> {}
            }a.proposals.remove(p.id);
        }else if(p.no()>p.electorate.size()-p.majority())a.proposals.remove(p.id);
    }
    private void remember(UUID id){requests.add(id);while(requests.size()>4096)requests.remove(requests.iterator().next());}
    private long incoming(UUID city){return alliances.values().stream().filter(a->a.invitations.containsKey(city)).count();}
}
