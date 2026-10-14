package com.example.examplemod.client.screen;

import com.example.examplemod.projects.Project;


/** Shared visual semantics and marker hit geometry. Glyphs use native pixels, not font symbols. */
public final class ProjectMapStyle {
    private ProjectMapStyle() {}
    public static final int SERVER_COLOR=0xFFF0BB55;
    public static int color(Project.Scope scope,Project.Status status){return scope==Project.Scope.SERVER?status.terminal()?0xFFC49C51:SERVER_COLOR:color(status);}
    public static int[] glyph(Project.Scope scope,Project.Category category,Project.Status status){
        return scope==Project.Scope.SERVER?new int[]{24,24,126,60,60,126,66,126}:glyph(category,status);
    }
    public static int color(Project.Status status) {return switch(status) {
        case DRAFT,PENDING_FUNDING->0xFF9EADB5;case OPEN->0xFF71BFE2;case BUILDING->0xFFE4B65B;
        case REVIEW->0xFFE8D9AE;case REWORK->0xFFE79772;case COMPLETED->0xFF86CFA0;case CANCELLED->0xFF78868A;
    };}
    public static boolean dashed(Project.Status status){return status==Project.Status.DRAFT||status==Project.Status.PENDING_FUNDING||status==Project.Status.OPEN||status==Project.Status.CANCELLED;}
    public static boolean zones(double zoom,boolean selected){return selected||zoom>=.38;}
    public static boolean markers(double zoom,boolean selected){return selected||zoom>=.16;}
    public static WorldMapUiLayout.Rect marker(int x,int y){return new WorldMapUiLayout.Rect(x-9,y-9,18,18);}
    public static WorldMapUiLayout.Rect label(int x,int y,int textWidth,WorldMapUiLayout.Rect viewport) {
        int w=Math.max(1,Math.min(viewport.width()-4,textWidth+12));
        int left=Math.clamp(x-w/2,viewport.x()+2,Math.max(viewport.x()+2,viewport.right()-w-2));
        int top=y+13;if(top+18>viewport.bottom())top=y-31;
        top=Math.clamp(top,viewport.y(),Math.max(viewport.y(),viewport.bottom()-18));
        return new WorldMapUiLayout.Rect(left,top,w,18);
    }
    public static int[] glyph(Project.Category category,Project.Status status) {
        if(status==Project.Status.COMPLETED)return new int[]{0,1,3,6,76,120,48,0};
        return switch(category) {
            case BUILDING->new int[]{24,60,126,66,90,90,126,0};
            case ROAD->new int[]{66,90,66,90,66,90,66,0};
            case RAILWAY->new int[]{66,126,66,126,66,126,66,0};
            case LANDSCAPE->new int[]{24,60,126,24,60,126,24,0};
            case INFRASTRUCTURE->new int[]{98,116,56,28,56,116,98,0};
            case OTHER->new int[]{60,66,90,90,90,66,60,0};
        };
    }
}
