package com.example.examplemod.client.screen;

import com.example.examplemod.projects.EngineeringPlan;
import com.example.examplemod.projects.EngineeringPlan.Node;
import com.example.examplemod.client.map.PlanSurface;
import com.example.examplemod.city.CityPlot;
import com.example.examplemod.network.CityMapPeersPayload.Peer;
import java.util.*;

final class PlanSurvey {
    static final int MAX_SAMPLES=128;
    interface Heights { PlanSurface at(int x,int z); }
    record Sample(double distance, PlanSurface height) {}
    record Profile(double step, List<Sample> samples) {}
    static Profile profile(EngineeringPlan plan, Heights heights) {
        if(plan.nodes().isEmpty())return new Profile(0,List.of());
        double length=plan.length();
        int count=Math.min(MAX_SAMPLES,1+(int)Math.ceil(Math.min(length,MAX_SAMPLES)));
        double step=count<=1?0:length/(count-1);
        int edge=0;
        double before=0;
        var result=new ArrayList<Sample>(count);
        for(int i=0;i<count;i++) {
            double at=i==count-1?length:step*i;
            Node a=plan.nodes().get(edge), b=plan.edges()==0?a:plan.end(edge);
            while(edge+1<plan.edges() && before+a.distance(b)<at) { before+=a.distance(b); a=plan.nodes().get(++edge); b=plan.end(edge); }
            double segment=a.distance(b), t=segment==0?0:Math.clamp((at-before)/segment,0,1);
            result.add(new Sample(at,heights.at((int)Math.round(a.x()+(b.x()-a.x())*t),(int)Math.round(a.z()+(b.z()-a.z())*t))));
        }
        return new Profile(step,Collections.unmodifiableList(result));
    }

    /** Advance through the authorized, immutable claim snapshot on ticks. Never expands a bounding box into world cells. */
    static final class Territories {
        private final EngineeringPlan plan;
        private final Iterator<Peer> peers;
        private Iterator<CityPlot> plots=Collections.emptyIterator();
        private String name="";
        final Set<String> hits=new LinkedHashSet<>();
        boolean done;
        Territories(EngineeringPlan plan,List<Peer> peers) {this.plan=plan;this.peers=peers.iterator();}
        void advance() {
            long deadline=System.nanoTime()+1_000_000;
            for(int budget=0;budget<96 && System.nanoTime()<deadline;budget++){
                if (!plots.hasNext()) {
                    if(!peers.hasNext()) {done=true;return;}
                    Peer p=peers.next();name=p.name();plots=p.plots().iterator();continue;
                }
                var plot=plots.next();
                if (plot.dimension().equals(plan.dimension()) && plan.touches(plot.minX(),plot.minZ(),plot.maxX(),plot.maxZ())) {
                    hits.add(name);plots=Collections.emptyIterator();
                }
            }
        }
    }
}
