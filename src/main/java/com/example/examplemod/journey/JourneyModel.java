package com.example.examplemod.journey;

import java.util.*;

/** Server-owned definitions. Presentation acknowledgements never complete objectives. */
public final class JourneyModel {
    public static final int MAX_MARKERS=128, MAX_QUESTS=128, MAX_STEPS=32, MAX_OBJECTIVES=16, MAX_POINTS=64;
    public enum Kind { GUIDE, QUEST, ACTIVITY, LANDMARK }
    public enum ObjectiveKind { VISIT, INTERACT, ITEM, CITY_MEMBER, SHIPMENT_DELIVERED, PROJECT_COMPLETED, ADVANCEMENT, OPEN_PYLON }
    public record Point(String dimension,int x,int y,int z) {
        public Point { if(dimension==null||!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||Math.abs((long)x)>30_000_000||Math.abs((long)z)>30_000_000||y < -2048||y>2048)throw new IllegalArgumentException("Invalid position"); }
        public double distance(String dim,double px,double py,double pz){return dimension.equals(dim)?Math.sqrt((x+.5-px)*(x+.5-px)+(y-py)*(y-py)+(z+.5-pz)*(z+.5-pz)):Double.POSITIVE_INFINITY;}
    }
    public static final class Marker {
        public final String id;
        public String title,description="";
        public Kind kind=Kind.GUIDE;
        public Point point;
        public int radius=6;
        public boolean enabled=true,newcomers;
        public boolean onMap=true,inWorld=true;
        public int tint,icon,size=10,range=72;
        public String afterQuest="";
        public final List<Point> route=new ArrayList<>();
        public Marker(String id,Point point){this.id=identifier(id);this.title=id;this.point=Objects.requireNonNull(point);}
    }
    public record Objective(String id,ObjectiveKind kind,String target,int count) {
        public Objective { identifier(id);Objects.requireNonNull(kind);if(target==null||target.length()>128||count<1||count>4096)throw new IllegalArgumentException("Invalid objective"); }
    }
    public static final class Quest {
        public final String id;
        public String title,description="",marker="";
        public boolean enabled;
        public final List<Objective> objectives=new ArrayList<>();
        public Quest(String id){this.id=identifier(id);title=id;}
    }
    public static final class Chain {
        public final String id;
        public String title;
        public boolean enabled,automatic;
        public final List<String> steps=new ArrayList<>();
        public Chain(String id){this.id=identifier(id);title=id;}
    }
    public static final class Progress {
        public final Set<String> completed=new LinkedHashSet<>(),met=new LinkedHashSet<>(),accepted=new LinkedHashSet<>();
        public String chain="",activeQuest="",activeMarker="";
        public boolean autoStarted,paused,graduated;
        public boolean accept(String id){if(completed.contains(id))return false;if(!accepted.contains(id)&&accepted.size()>=3)throw new IllegalArgumentException("Only three pinned quests are allowed");accepted.add(id);activeQuest=id;activeMarker="";paused=false;return true;}
        public boolean finish(Quest q){if(completed.contains(q.id)||q.objectives.isEmpty()||q.objectives.stream().anyMatch(o->!met.contains(q.id+"/"+o.id())))return false;completed.add(q.id);accepted.remove(q.id);if(activeQuest.equals(q.id))activeQuest="";return true;}
    }
    public static String identifier(String value){if(value==null||!value.matches("[a-z0-9_./-]{1,64}"))throw new IllegalArgumentException("ID: a-z, 0-9, _, -, /, . (1–64)");return value;}
    public static boolean available(Quest q,Progress p,Collection<Chain> chains){
        if(q==null||!q.enabled||p.completed.contains(q.id))return false;
        for(var chain:chains){if(!chain.enabled)continue;int at=chain.steps.indexOf(q.id);if(at>=0)for(int i=0;i<at;i++)if(!p.completed.contains(chain.steps.get(i)))return false;}
        return true;
    }
    public static String next(Chain c,Progress p){return c.steps.stream().filter(id->!p.completed.contains(id)).findFirst().orElse("");}
    private JourneyModel(){}
}
