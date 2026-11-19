package com.example.examplemod.client.logistics;

/** A round-ended screen-space stroke. The alpha fringe is one framebuffer pixel wide.
 * Shared endpoints naturally form a round join without implying connectivity between samples. */
final class RailStroke {
    private static final double DIAGONAL=Math.sqrt(.5);
    // Counterclockwise perimeter in the segment's (tangent, normal) basis.
    private static final double[] ALONG={0,-DIAGONAL,-1,-DIAGONAL,0,0,DIAGONAL,1,DIAGONAL,0};
    private static final double[] ACROSS={1,DIAGONAL,0,-DIAGONAL,-1,-1,-DIAGONAL,0,DIAGONAL,1};
    static final int FLOATS_PER_STROKE = 40;
    private final float[] scratch = new float[FLOATS_PER_STROKE];

    interface Sink { void vertex(float x,float y,int argb); }

    record Line(double x,double y,double endX,double endY,double skipped) {}
    static Line clip(double x,double y,double ex,double ey,double left,double top,double right,double bottom) {
        double dx=ex-x,dy=ey-y;
        double enter=0,leave=1;
        for(int side=0;side<4;side++) {
            double p=switch(side){case 0->-dx; case 1->dx; case 2->-dy; default->dy;};
            double q=switch(side){case 0->x-left; case 1->right-x; case 2->y-top; default->bottom-y;};
            if(p==0) { if(q<0)return null; continue; }
            double ratio=q/p;
            if(p<0)enter=Math.max(enter,ratio); else leave=Math.min(leave,ratio);
            if(enter>leave)return null;
        }
        return new Line(x+enter*dx,y+enter*dy,x+leave*dx,y+leave*dy,enter*Math.hypot(dx,dy));
    }

    void emit(double x,double y,double ex,double ey,double dx,double dy,double width,double feather,int color,Sink out) {
        prepare(scratch,0,x,y,ex,ey,dx,dy,width,feather);
        submit(scratch,0,color,out);
    }

    static void prepare(float[] vertices,int offset,double x,double y,double ex,double ey,
                        double dx,double dy,double width,double feather) {
        double inner=Math.max(.06,(width-feather)/2),outer=(width+feather)/2;
        for(int i=0;i<10;i++) {
            double cx=i<5?x:ex,cy=i<5?y:ey;
            double nx=ALONG[i]*dx-ACROSS[i]*dy, ny=ALONG[i]*dy+ACROSS[i]*dx;
            int p=offset+4*i;
            vertices[p]=(float)(cx+nx*inner); vertices[p+1]=(float)(cy+ny*inner);
            vertices[p+2]=(float)(cx+nx*outer); vertices[p+3]=(float)(cy+ny*outer);
        }
    }

    static void submit(float[] vertices,int offset,int color,Sink out) {
        int clear=color&0x00FFFFFF;
        for(int i=1;i<9;i++) {
            int p=offset+4*i;
            out.vertex(vertices[offset],vertices[offset+1],color);
            out.vertex(vertices[p],vertices[p+1],color);
            out.vertex(vertices[p+4],vertices[p+5],color);
        }
        for(int i=0;i<10;i++) {
            int p=offset+4*i,q=offset+4*((i+1)%10);
            out.vertex(vertices[p],vertices[p+1],color);
            out.vertex(vertices[p+2],vertices[p+3],clear);
            out.vertex(vertices[q+2],vertices[q+3],clear);
            out.vertex(vertices[p],vertices[p+1],color);
            out.vertex(vertices[q+2],vertices[q+3],clear);
            out.vertex(vertices[q],vertices[q+1],color);
        }
    }
}
