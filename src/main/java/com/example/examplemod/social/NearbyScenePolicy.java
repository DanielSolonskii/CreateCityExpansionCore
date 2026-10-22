package com.example.examplemod.social;
import com.example.examplemod.projects.ProjectZone;
/** Shared distance and privacy rules; never trust a client-supplied player location. */
public final class NearbyScenePolicy {
    public static final int FRIEND_RANGE=256,PROJECT_RANGE=80;
    private NearbyScenePolicy(){}
    public static boolean friend(boolean mutual,boolean shared,boolean sameDimension,boolean spectator,double distanceSquared){return mutual&&shared&&sameDimension&&!spectator&&Double.isFinite(distanceSquared)&&distanceSquared>=0&&distanceSquared<=FRIEND_RANGE*FRIEND_RANGE;}
    public static boolean project(ProjectZone zone,double x,double z){return zone.distance(x,z)<=PROJECT_RANGE;}
    public static boolean preferenceReady(long previous,long now){return previous<=0||now<previous||now-previous>=400;}
}
