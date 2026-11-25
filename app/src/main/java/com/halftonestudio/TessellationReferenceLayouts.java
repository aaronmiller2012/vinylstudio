package com.halftonestudio;

import java.util.ArrayList;
import java.util.List;

/**
 * Seven deterministic, periodic photographic vinyl layouts (variants 22..28).
 * All coordinates are in physical millimetres. Each polygon is a material
 * region, not an SVG stroke or <pattern>. Adjacent pieces are shrunk by the
 * shared tessellation renderer before being assigned a photographic color.
 * No Android dependencies; pure JVM geometry can be qualified on the NAS.
 */
final class TessellationReferenceLayouts {
    private TessellationReferenceLayouts() { }
    private static final double ROOT3 = Math.sqrt(3);
    private static float[] p(double... values) {
        float[] out=new float[values.length];
        for(int i=0;i<values.length;i++)out[i]=(float)values[i];
        return out;
    }
    private static double signed(float[] a) {
        double s=0;int n=a.length/2;
        for(int i=0;i<n;i++){int j=(i+1)%n;
            s+=(double)a[2*i]*a[2*j+1]-(double)a[2*j]*a[2*i+1];}
        return s*.5;
    }
    private static float[] ccw(float[] a){
        if(signed(a)>=0)return a;
        float[] out=new float[a.length];int n=a.length/2;
        for(int i=0;i<n;i++){out[2*i]=a[2*(n-i-1)];out[2*i+1]=a[2*(n-i-1)+1];}
        return out;
    }
    private static void add(List<float[]> list,float[] a){
        if(a.length>=6 && Math.abs(signed(a))>1e-5)list.add(ccw(a));
    }
    private static float[] rect(double x0,double y0,double x1,double y1){
        return p(x0,y0,x1,y0,x1,y1,x0,y1);
    }
    private static int cols(float width,double cell){return (int)Math.ceil(width/cell)+2;}
    private static int rows(float height,double cell){return (int)Math.ceil(height/cell)+2;}

    static List<float[]> tiles(int variant,float width,float height,float unit){
        if(variant<TessellationMotifEngine.INTERLOCKING_BASKET ||
           variant>TessellationMotifEngine.ISOMETRIC_RIBBONS ||
           !(unit>0) || !Float.isFinite(unit))
            throw new IllegalArgumentException("Unsupported reference tessellation");
        switch(variant){
            case TessellationMotifEngine.INTERLOCKING_BASKET:return basket(width,height,unit);
            case TessellationMotifEngine.FRAMED_SQUARE:return framed(width,height,unit);
            case TessellationMotifEngine.INTERLOCKING_DUMBBELL:return dumbbells(width,height,unit);
            case TessellationMotifEngine.TRIANGULAR_RIBBONS:return triangles(width,height,unit);
            case TessellationMotifEngine.STEPPED_CROSS:return crosses(width,height,unit);
            case TessellationMotifEngine.DIAMOND_STAR:return stars(width,height,unit);
            case TessellationMotifEngine.ISOMETRIC_RIBBONS:return isometric(width,height,unit);
            default:throw new IllegalArgumentException("Unrecognized tessellation");
        }
    }
    // 2x2 domino blocks alternating H-H and V-V, with no overlapping bars.
    private static List<float[]> basket(float w,float h,float u){
        ArrayList<float[]> out=new ArrayList<>();double step=2*u;
        for(int row=-2;row<rows(h,step);row++){
            StyleKit.checkCancelled();
            for(int col=-2;col<cols(w,step);col++){
                double x=col*step,y=row*step;
                if(((row+col)&1)==0){
                    add(out,rect(x,y,x+step,y+u));add(out,rect(x,y+u,x+step,y+step));
                }else{
                    add(out,rect(x,y,x+u,y+step));add(out,rect(x+u,y,x+step,y+step));
                }
            }
        }
        return out;
    }
    // Orthogonal frame rails around a truly separate central square.
    private static List<float[]> framed(float w,float h,float u){
        ArrayList<float[]> out=new ArrayList<>();double step=4*u;
        for(int row=-2;row<rows(h,step);row++){
            StyleKit.checkCancelled();
            for(int col=-2;col<cols(w,step);col++){
                double x=col*step,y=row*step;
                add(out,rect(x,y,x+step,y+.8*u));
                add(out,rect(x,y+3.2*u,x+step,y+step));
                add(out,rect(x,y+.8*u,x+.8*u,y+3.2*u));
                add(out,rect(x+3.2*u,y+.8*u,x+step,y+3.2*u));
                add(out,rect(x+.8*u,y+.8*u,x+3.2*u,y+1.55*u));
                add(out,rect(x+.8*u,y+2.45*u,x+3.2*u,y+3.2*u));
                add(out,rect(x+.8*u,y+1.55*u,x+1.55*u,y+2.45*u));
                add(out,rect(x+2.45*u,y+1.55*u,x+3.2*u,y+2.45*u));
                add(out,rect(x+1.55*u,y+1.55*u,x+2.45*u,y+2.45*u));
            }
        }
        return out;
    }
    /** Smooth bone shape with a genuinely concave waist and 48 contour nodes. */
    private static float[] bone(double x,double y,double pitch,boolean vertical){
        final double[][] anchor={
            {-.41,0},{-.36,-.17},{-.27,-.195},{-.17,-.115},{0,-.078},
            {.17,-.115},{.27,-.195},{.36,-.17},{.41,0},
            {.36,.17},{.27,.195},{.17,.115},{0,.078},
            {-.17,.115},{-.27,.195},{-.36,.17}
        };
        int n=anchor.length,sub=3;float[] result=new float[2*n*sub];
        for(int i=0;i<n;i++){
            int a=(i+n-1)%n,b=i,c=(i+1)%n,d=(i+2)%n;
            for(int k=0;k<sub;k++){
                double t=(double)k/sub,t2=t*t,t3=t2*t;
                double xx=.5*((2*anchor[b][0])+(-anchor[a][0]+anchor[c][0])*t+
                    (2*anchor[a][0]-5*anchor[b][0]+4*anchor[c][0]-anchor[d][0])*t2+
                    (-anchor[a][0]+3*anchor[b][0]-3*anchor[c][0]+anchor[d][0])*t3);
                double yy=.5*((2*anchor[b][1])+(-anchor[a][1]+anchor[c][1])*t+
                    (2*anchor[a][1]-5*anchor[b][1]+4*anchor[c][1]-anchor[d][1])*t2+
                    (-anchor[a][1]+3*anchor[b][1]-3*anchor[c][1]+anchor[d][1])*t3);
                int index=2*(i*sub+k);
                result[index]=(float)(x+pitch*(vertical?-yy:xx));
                result[index+1]=(float)(y+pitch*(vertical?xx:yy));
            }
        }
        return ccw(result);
    }
    private static List<float[]> dumbbells(float w,float h,float u){
        ArrayList<float[]> out=new ArrayList<>();double step=2*u;
        for(int row=-2;row<rows(h,step);row++){
            StyleKit.checkCancelled();
            for(int col=-2;col<cols(w,step);col++)
                add(out,bone((col+.5)*step,(row+.5)*step,step,((row+col)&1)!=0));
        }
        return out;
    }
    // Three tapered bands within each equilateral triangle. Band edges agree
    // before the uniform physical weed clearance is applied.
    private static List<float[]> triangles(float w,float h,float u){
        ArrayList<float[]> out=new ArrayList<>();
        List<float[]> base=TessellationMotifEngine.tiles(TessellationMotifEngine.TRIANGLE,w,h,2*u);
        for(float[] t:base){
            StyleKit.checkCancelled();
            if(t.length!=6)continue;
            // Rotate stripe direction over the three triangular lattice axes,
            // instead of accidentally creating horizontal stripes everywhere.
            double mx=(t[0]+t[2]+t[4])/3.0;
            double my=(t[1]+t[3]+t[5])/3.0;
            int q=(int)Math.floor(mx/(u*1.5)+0.00001);
            int r=(int)Math.floor(my/(u*ROOT3)+0.00001);
            int apex=Math.floorMod(q+r,3);
            int v1=(apex+1)%3,v2=(apex+2)%3;
            double ax=t[2*apex],ay=t[2*apex+1];
            double bx=t[2*v1],by=t[2*v1+1],cx=t[2*v2],cy=t[2*v2+1];
            double[] stops={0,.34,.67,1};
            for(int band=0;band<3;band++){
                double a=stops[band],b=stops[band+1];
                add(out,p(ax+(bx-ax)*a,ay+(by-ay)*a,
                    ax+(bx-ax)*b,ay+(by-ay)*b,
                    ax+(cx-ax)*b,ay+(cy-ay)*b,
                    ax+(cx-ax)*a,ay+(cy-ay)*a));
            }
        }
        return out;
    }
    // A genuine twelve-edge cross rather than crossed overlapping rectangles.
    private static List<float[]> crosses(float w,float h,float u){
        ArrayList<float[]> out=new ArrayList<>();double s=3*u,r=1.5*u,a=.5*u;
        for(int row=-2;row<rows(h,s);row++){
            StyleKit.checkCancelled();
            for(int col=-2;col<cols(w,s);col++){
                double x=(col+.5)*s,y=(row+.5)*s;
                // The 12-vertex plus occupies five of nine unit squares.
                // The remaining four are individual corner pieces. Together
                // they cover the macrocell exactly before introducing gaps.
                add(out,p(x-a,y-r,x+a,y-r,x+a,y-a,x+r,y-a,
                    x+r,y+a,x+a,y+a,x+a,y+r,x-a,y+r,x-a,y+a,
                    x-r,y+a,x-r,y-a,x-a,y-a));
                add(out,rect(x-r,y-r,x-a,y-a));
                add(out,rect(x+a,y-r,x+r,y-a));
                add(out,rect(x-r,y+a,x-a,y+r));
                add(out,rect(x+a,y+a,x+r,y+r));
            }
        }
        return out;
    }
    // Concave four-point stars made from four disjoint material petals, a
    // separate small square center, and intermediary lozenge tiles.
    private static List<float[]> stars(float w,float h,float u){
        ArrayList<float[]> out=new ArrayList<>();double s=3*u,r=.325*s,d=.175*s,a=.095*s;
        for(int row=-2;row<rows(h,s);row++){
            StyleKit.checkCancelled();
            for(int col=-2;col<cols(w,s);col++){
                double x=(col+.5)*s,y=(row+.5)*s;
                add(out,p(x-r,y-r,x,y-d,x+r,y-r,x+a,y-a,x-a,y-a));
                add(out,p(x+r,y-r,x+d,y,x+r,y+r,x+a,y+a,x+a,y-a));
                add(out,p(x+r,y+r,x,y+d,x-r,y+r,x-a,y+a,x+a,y+a));
                add(out,p(x-r,y+r,x-d,y,x-r,y-r,x-a,y-a,x-a,y+a));
                add(out,rect(x-a,y-a,x+a,y+a));
                // The mid-edge diamonds never touch the adjacent star petals.
                add(out,p(x+.5*s-.12*s,y,x+.5*s,y-.095*s,
                          x+.5*s+.12*s,y,x+.5*s,y+.095*s));
                add(out,p(x,y+.5*s-.12*s,x+.095*s,y+.5*s,
                          x,y+.5*s+.12*s,x-.095*s,y+.5*s));
            }
        }
        return out;
    }
    // Hexagonal cube illusion: three rhombi meet at each hex center. Each
    // rhombus is split into three parallel quadrilateral slats.
    private static List<float[]> isometric(float w,float h,float u){
        ArrayList<float[]> out=new ArrayList<>();double r=2*u;
        int nx=cols(w,1.5*r)+2,ny=rows(h,ROOT3*r)+2;
        double[] ts={0,.335,.67,1};
        for(int q=-3;q<nx;q++){
            StyleKit.checkCancelled();
            for(int row=-3;row<ny;row++){
                double x=1.5*r*q,y=ROOT3*r*(row+((q&1)!=0?.5:0));
                for(int k=0;k<6;k+=2){
                    double angle=k*Math.PI/3;
                    double ax=r*Math.cos(angle),ay=r*Math.sin(angle);
                    double cx=r*Math.cos(angle+2*Math.PI/3),cy=r*Math.sin(angle+2*Math.PI/3);
                    for(int band=0;band<3;band++){
                        double a=ts[band],b=ts[band+1];
                        add(out,p(x+a*cx,y+a*cy,x+ax+a*cx,y+ay+a*cy,
                            x+ax+b*cx,y+ay+b*cy,x+b*cx,y+b*cy));
                    }
                }
            }
        }
        return out;
    }

    /**
     * Mitered inward offset of a simple CCW polygon, including mild concavities.
     * Used only on the new concave motifs. Fail closed on degeneration or a
     * self-intersection; never emit an accidentally expanded cutting shape.
     */
    static float[] insetConcave(float[] polygon,float distance){
        if(polygon==null||polygon.length<6||!(distance>=0)||!Float.isFinite(distance))
            return new float[0];
        float[] q=ccw(polygon);int n=q.length/2;
        if(distance==0)return q.clone();
        float[] out=new float[q.length];
        for(int i=0;i<n;i++){
            int before=(i+n-1)%n,after=(i+1)%n;
            double px=q[2*before],py=q[2*before+1];
            double x=q[2*i],y=q[2*i+1];
            double nx=q[2*after],ny=q[2*after+1];
            double e0x=x-px,e0y=y-py,e1x=nx-x,e1y=ny-y;
            double l0=Math.hypot(e0x,e0y),l1=Math.hypot(e1x,e1y);
            if(l0<1e-5||l1<1e-5)return new float[0];
            e0x/=l0;e0y/=l0;e1x/=l1;e1y/=l1;
            double n0x=-e0y,n0y=e0x,n1x=-e1y,n1y=e1x;
            double denom=e0x*e1y-e0y*e1x;
            double ix,iy;
            if(Math.abs(denom)<1e-6){
                ix=x+distance*n0x;iy=y+distance*n0y;
            }else{
                double dx=distance*(n1x-n0x),dy=distance*(n1y-n0y);
                double t=(dx*e1y-dy*e1x)/denom;
                ix=x+distance*n0x+t*e0x;iy=y+distance*n0y+t*e0y;
            }
            if(!Double.isFinite(ix)||!Double.isFinite(iy)||
                Math.hypot(ix-x,iy-y)>Math.max(10*distance,1.0))return new float[0];
            out[2*i]=(float)ix;out[2*i+1]=(float)iy;
        }
        if(signed(out)<=.05 || !simple(out))return new float[0];
        return out;
    }
    private static boolean simple(float[] a){
        int n=a.length/2;
        for(int i=0;i<n;i++){
            int i2=(i+1)%n;
            for(int j=i+2;j<n;j++){
                if(i==0&&j==n-1)continue;
                int j2=(j+1)%n;
                if(intersects(a[2*i],a[2*i+1],a[2*i2],a[2*i2+1],
                    a[2*j],a[2*j+1],a[2*j2],a[2*j2+1]))return false;
            }
        }
        return true;
    }
    private static double cross(double x,double y,double a,double b,double c,double d){
        return (c-x)*(b-y)-(d-y)*(a-x);
    }
    private static boolean intersects(double ax,double ay,double bx,double by,
                                      double cx,double cy,double dx,double dy){
        double o1=cross(ax,ay,bx,by,cx,cy),o2=cross(ax,ay,bx,by,dx,dy);
        double o3=cross(cx,cy,dx,dy,ax,ay),o4=cross(cx,cy,dx,dy,bx,by);
        return ((o1>1e-7&&o2< -1e-7)||(o1< -1e-7&&o2>1e-7))&&
               ((o3>1e-7&&o4< -1e-7)||(o3< -1e-7&&o4>1e-7));
    }
}
