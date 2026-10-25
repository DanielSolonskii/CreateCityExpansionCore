package com.example.examplemod.social;

import java.util.*;

/** Server-thread state machine. Actors are supplied by the authenticated connection, never a packet. */
public final class SocialBook {
    public static final int CONTACT_LIMIT=128, HISTORY_LIMIT=200, GLOBAL_MESSAGES=50000, DIALOG_LIMIT=2048;
    public enum Action { LIST, REQUEST, ACCEPT, DECLINE, CANCEL, REMOVE, BLOCK, UNBLOCK, SEND, READ }
    public enum Relation { NONE, OUTGOING, INCOMING, FRIEND, BLOCKED, UNAVAILABLE }
    public record Pair(UUID first,UUID second) {
        public static Pair of(UUID a,UUID b) { return a.compareTo(b)<0?new Pair(a,b):new Pair(b,a); }
        public boolean includes(UUID id) { return first.equals(id)||second.equals(id); }
        public UUID other(UUID id) { if(!includes(id))throw new IllegalArgumentException("Not a participant");return first.equals(id)?second:first; }
    }
    public record Edge(UUID from,UUID to) {}
    public record Message(long id,UUID nonce,UUID sender,long time,String text) {}
    public final Set<Pair> friends=new HashSet<>();
    public final Set<Edge> requests=new HashSet<>(), blocks=new HashSet<>();
    public final Map<Pair,List<Message>> dialogs=new HashMap<>();
    public final Map<UUID,Long> revisions=new HashMap<>();
    public final Map<Edge,Long> read=new HashMap<>();
    public long sequence;
    public Relation relation(UUID actor,UUID target) {
        if(blocks.contains(new Edge(actor,target)))return Relation.BLOCKED;
        if(blocks.contains(new Edge(target,actor)))return Relation.UNAVAILABLE;
        if(friends.contains(Pair.of(actor,target)))return Relation.FRIEND;
        if(requests.contains(new Edge(actor,target)))return Relation.OUTGOING;
        if(requests.contains(new Edge(target,actor)))return Relation.INCOMING;
        return Relation.NONE;
    }
    public long revision(UUID actor) { return revisions.getOrDefault(actor,0L); }
    public Set<UUID> contacts(UUID actor) {
        Set<UUID> ids=new HashSet<>();
        friends.stream().filter(p->p.includes(actor)).forEach(p->ids.add(p.other(actor)));
        requests.forEach(e->{if(e.from.equals(actor))ids.add(e.to);if(e.to.equals(actor))ids.add(e.from);});
        blocks.stream().filter(e->e.from.equals(actor)).forEach(e->ids.add(e.to));
        return ids;
    }
    public List<Message> messages(UUID actor,UUID target,long before) {
        var values=dialogs.getOrDefault(Pair.of(actor,target),List.of());
        return values.stream().filter(m->before==0||m.id<before).sorted(Comparator.comparingLong(Message::id).reversed()).limit(20)
                .sorted(Comparator.comparingLong(Message::id)).toList();
    }
    public int unread(UUID actor,UUID target) {
        long seen=read.getOrDefault(new Edge(actor,target),0L);
        return (int)dialogs.getOrDefault(Pair.of(actor,target),List.of()).stream().filter(m->!m.sender.equals(actor)&&m.id>seen).count();
    }
    public String apply(UUID actor,UUID target,Action action,long expected,String text,long cursor,long now,UUID nonce) {
        if(actor.equals(target))return "self";
        if(action==Action.LIST)return "ok";
        if(action==Action.READ) {
            // Never acknowledge future messages. The UI sends the last visible page cursor.
            long latest=dialogs.getOrDefault(Pair.of(actor,target),List.of()).stream().mapToLong(Message::id).max().orElse(0);
            if(latest==0||cursor<=0||cursor>latest)return "invalid";
            read.merge(new Edge(actor,target),cursor,Math::max);return "ok";
        }
        if(action==Action.SEND && dialogs.getOrDefault(Pair.of(actor,target),List.of()).stream()
                .anyMatch(m->m.sender.equals(actor)&&m.nonce.equals(nonce)))return "duplicate";
        if(expected!=revision(actor))return "stale";
        var state=relation(actor,target);var pair=Pair.of(actor,target);var outgoing=new Edge(actor,target);var incoming=new Edge(target,actor);
        switch(action) {
            case REQUEST -> {
                if(state!=Relation.NONE)return "state";
                if(contacts(actor).size()>=CONTACT_LIMIT||contacts(target).size()>=CONTACT_LIMIT)return "full";
                requests.add(outgoing);
            }
            case ACCEPT -> { if(state!=Relation.INCOMING)return "state"; requests.remove(incoming);friends.add(pair); }
            case DECLINE -> { if(state!=Relation.INCOMING)return "state";requests.remove(incoming); }
            case CANCEL -> { if(state!=Relation.OUTGOING)return "state";requests.remove(outgoing); }
            case REMOVE -> { if(state!=Relation.FRIEND)return "state";friends.remove(pair); }
            case BLOCK -> {
                if(state==Relation.BLOCKED)return "state";
                if(!contacts(actor).contains(target)&&contacts(actor).size()>=CONTACT_LIMIT)return "full";
                friends.remove(pair);requests.remove(outgoing);requests.remove(incoming);blocks.add(outgoing);
            }
            case UNBLOCK -> { if(state!=Relation.BLOCKED)return "state";blocks.remove(outgoing); }
            case SEND -> {
                if(state!=Relation.FRIEND)return "permission";
                if(text.isBlank()||text.length()>500||text.chars().anyMatch(c->Character.isISOControl(c)||c==167))return "invalid";
                if(!dialogs.containsKey(pair)&&dialogs.size()>=DIALOG_LIMIT)return "full";
                var history=dialogs.computeIfAbsent(pair,k->new ArrayList<>());
                history.add(new Message(++sequence,nonce,actor,now,text.strip()));
                while(history.size()>HISTORY_LIMIT)history.removeFirst();
                int total=dialogs.values().stream().mapToInt(List::size).sum();
                if(total>GLOBAL_MESSAGES) dialogs.values().stream().filter(v->!v.isEmpty()).min(Comparator.comparingLong(v->v.getFirst().id)).ifPresent(v->v.removeFirst());
            }
            default -> { return "invalid"; }
        }
        revisions.put(actor,revision(actor)+1);revisions.put(target,revision(target)+1);
        return "ok";
    }
}
