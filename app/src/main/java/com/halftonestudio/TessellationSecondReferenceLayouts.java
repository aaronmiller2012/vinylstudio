package com.halftonestudio;

import java.util.ArrayList;
import java.util.List;

/**
 * Eight additional geometric reference layouts (persisted variants 29..36).
 * Regions are closed, positive-area, CCW material pieces in millimetres.
 * No duplicated cut strokes, SVG patterns, bitmap traces or Android APIs.
 *
 * Reference outlines are represented as disconnected material faces so that
 * a photo-colored tessellation remains practical to weed and layer. This is
 * intentionally not a byte-for-byte tracing of the reference photography.
 */
final class TessellationSecondReferenceLayouts {
    private TessellationSecondReferenceLayouts() {}
    private static final double SQRT3=Math.sqrt(3);
    private static float[] poly(double... a){float[] p=new float[a.length];for(int i=0;i<a.length;i++)p[i]=(float)a[i];return p;}
    private static double signed(float[] p){double a=0;int n=p.length/2;
        for(int i=0;i<n;i++){int j=(i+1)%n;a+=(double)p[2*i]*p[2*j+1]-(double)p[2*j]*p[2*i+1];}return a*.5;}
    private static float[] ccw(float[] p){if(signed(p)>=0)return p;
        float[] o=new float[p.length];int n=p.length/2;
        for(int i=0;i<n;i++){int j=n-1-i;o[2*i]=p[2*j];o[2*i+1]=p[2*j+1];}return o;}
    private static void add(List<float[]> out,float[] p){if(p.length>=6&&signed(p)!=0)out.add(ccw(p));}
    private static float[] rect(double x0,double y0,double x1,double y1){return poly(x0,y0,x1,y0,x1,y1,x0,y1);}
    private static float[] ngon(double x,double y,double r,int n,double phase){float[] p=new float[n*2];
        for(int k=0;k<n;k++){double a=phase+2*Math.PI*k/n;p[2*k]=(float)(x+r*Math.cos(a));p[2*k+1]=(float)(y+r*Math.sin(a));}return p;}
    private static float[] scaled(float[] polygon,double scale){float cx=0,cy=0;int n=polygon.length/2;
        for(int i=0;i<n;i++){cx+=polygon[2*i];cy+=polygon[2*i+1];}cx/=n;cy/=n;
        float[] p=new float[polygon.length];for(int i=0;i<n;i++){
            p[2*i]=(float)(cx+(polygon[2*i]-cx)*scale);
            p[2*i+1]=(float)(cy+(polygon[2*i+1]-cy)*scale);
        }return p;
    }
    /** Divides a convex annulus into nonoverlapping, independently cuttable trapezoids. */
    private static void annular(List<float[]> out,float[] outer,float[] inner){int n=outer.length/2;
        for(int i=0;i<n;i++){int j=(i+1)%n;
            add(out,poly(outer[2*i],outer[2*i+1],outer[2*j],outer[2*j+1],
                inner[2*j],inner[2*j+1],inner[2*i],inner[2*i+1]));
        }
    }
    private static void ringPlusCore(List<float[]> out,float[] shape,double outer,double inside,double core){
        annular(out,scaled(shape,outer),scaled(shape,inside));
        add(out,scaled(shape,core));
    }
    private static int cols(float width,double pitch){return (int)Math.ceil(width/pitch)+2;}
    private static int rows(float height,double pitch){return (int)Math.ceil(height/pitch)+2;}

    static List<float[]> tiles(int style,float width,float height,float unit){
        if(style<TessellationMotifEngine.CROSSHAIR_GRID||style>TessellationMotifEngine.OCTAGON_DIAMOND_LINKS||
            !(unit>0)||!Float.isFinite(unit)||!(width>0)||!(height>0))
            throw new IllegalArgumentException("Unsupported new reference tessellation");
        switch(style){
            case TessellationMotifEngine.CROSSHAIR_GRID:return crosshair(width,height,unit);
            case TessellationMotifEngine.ROUNDED_HEX_NETWORK:return roundedHex(width,height,unit);
            case TessellationMotifEngine.DIAGONAL_FRAMED_SQUARE:return diagonalFrames(width,height,unit);
            case TessellationMotifEngine.CIRCLE_CHAIN_LATTICE:return circleLinks(width,height,unit);
            case TessellationMotifEngine.DIAMOND_ARC_ROSETTE:return rosettes(width,height,unit);
            case TessellationMotifEngine.NESTED_HEXAGONS:return nestedHex(width,height,unit);
            case TessellationMotifEngine.INSET_RHOMBUS_CUBES:return rhombusCubes(width,height,unit);
            case TessellationMotifEngine.OCTAGON_DIAMOND_LINKS:return octagonLinks(width,height,unit);
            default:throw new IllegalArgumentException("Unsupported tessellation");
        }
    }

    // Image 1: central outlined plus, small corner squares, underlying square grid.
    // Corners are deliberately separated from the cross material: no shared blades.
    private static List<float[]> crosshair(float w,float h,float u){
        ArrayList<float[]> out=new ArrayList<>();double s=3*u, arm=.365*s,half=.11*s;
        for(int row=-2;row<rows(h,s);row++){
            StyleKit.checkCancelled();for(int col=-2;col<cols(w,s);col++){
                double x=(col+.5)*s,y=(row+.5)*s;
                add(out,poly(x-half,y-arm,x+half,y-arm,x+half,y-half,
                    x+arm,y-half,x+arm,y+half,x+half,y+half,x+half,y+arm,
                    x-half,y+arm,x-half,y+half,x-arm,y+half,x-arm,y-half,x-half,y-half));
                double c=.245*s;
                for(int dx=-1;dx<=1;dx+=2)for(int dy=-1;dy<=1;dy+=2)
                    add(out,rect(x+dx*c-.075*s,y+dy*c-.075*s,
                        x+dx*c+.075*s,y+dy*c+.075*s));
                // Assign each horizontal/vertical grid rail to exactly one
                // macrocell. Rails are separate physical regions and never
                // duplicate an adjacent cell's edges.
                double g=.035*s;
                add(out,rect(x-s*.5,y-s*.5-g,x+s*.5,y-s*.5+g));
                add(out,rect(x-s*.5-g,y-s*.5+g,x-s*.5+g,y+s*.5-g));
            }
        }return out;
    }

    // Image 2: smooth, alternating honeycomb cells. Each rounded hexagon stays
    // INSIDE its regular hexagon, even at cell corners and Snip boundaries.
    private static List<float[]> roundedHex(float w,float h,float u){
        ArrayList<float[]> out=new ArrayList<>();
        for(float[] h6:TessellationMotifEngine.tiles(TessellationMotifEngine.HEXAGON,w,h,u)){
            StyleKit.checkCancelled();float[] shape=new float[6*5*2];int k=0;
            for(int i=0;i<6;i++){
                int prev=(i+5)%6,next=(i+1)%6;
                double x=h6[2*i],y=h6[2*i+1];
                double ax=x*.75+h6[2*prev]*.25,ay=y*.75+h6[2*prev+1]*.25;
                double bx=x*.75+h6[2*next]*.25,by=y*.75+h6[2*next+1]*.25;
                for(int step=0;step<5;step++){
                    double t=step/4.0;
                    shape[k++]=(float)((1-t)*(1-t)*ax+2*(1-t)*t*x+t*t*bx);
                    shape[k++]=(float)((1-t)*(1-t)*ay+2*(1-t)*t*y+t*t*by);
                }
            }
            add(out,shape);
        }return out;
    }

    // Image 3: the existing nine-piece square-and-rail motif, rotated as a
    // whole lattice by 45 degrees. Iterate only near the visible crop.
    private static List<float[]> diagonalFrames(float w,float h,float u){
        ArrayList<float[]> out=new ArrayList<>();double s=4*u,c=Math.sqrt(.5),extent=(w+h)*c*.5;
        int min=(int)Math.floor((-extent-2*s)/s),max=(int)Math.ceil((extent+2*s)/s);
        for(int row=min;row<=max;row++){
            StyleKit.checkCancelled();for(int col=min;col<=max;col++){
                double x=col*s,y=row*s;
                double cx=w*.5+c*(x-y),cy=h*.5+c*(x+y);
                if(cx<-s||cy<-s||cx>w+s||cy>h+s)continue;
                // Center cell mirrors framed-square's exact proportions.
                float[][] pieces={
                    rect(x,y,x+s,y+.8*u),rect(x,y+3.2*u,x+s,y+s),
                    rect(x,y+.8*u,x+.8*u,y+3.2*u),rect(x+3.2*u,y+.8*u,x+s,y+3.2*u),
                    rect(x+.8*u,y+.8*u,x+3.2*u,y+1.55*u),
                    rect(x+.8*u,y+2.45*u,x+3.2*u,y+3.2*u),
                    rect(x+.8*u,y+1.55*u,x+1.55*u,y+2.45*u),
                    rect(x+2.45*u,y+1.55*u,x+3.2*u,y+2.45*u),
                    rect(x+1.55*u,y+1.55*u,x+2.45*u,y+2.45*u)
                };
                for(float[] p:pieces){
                    // A 0.018 mm pre-separation prevents floating-point
                    // seam inversions on the diagonal axes. This is in
                    // addition to, not instead of, the renderer weed gap.
                    final float e=.018f;
                    p[0]+=e;p[1]+=e;p[2]-=e;p[3]+=e;
                    p[4]-=e;p[5]-=e;p[6]+=e;p[7]-=e;
                    for(int i=0;i<p.length;i+=2){
                        double a=p[i],b=p[i+1];
                        p[i]=(float)(Math.rint((w*.5+c*(a-b))*1000.0)/1000.0);
                        p[i+1]=(float)(Math.rint((h*.5+c*(a+b))*1000.0)/1000.0);
                    }
                    add(out,p);
                }
            }
        }return out;
    }

    // Image 4: bead-like circles linked horizontally and vertically by
    // disjoint, cuttable rectangular connectors.
    private static List<float[]> circleLinks(float w,float h,float u){
        ArrayList<float[]> out=new ArrayList<>();double s=2.7*u,r=.315*s,b=.058*s;
        for(int row=-2;row<rows(h,s);row++){
            StyleKit.checkCancelled();for(int col=-2;col<cols(w,s);col++){
                double x=(col+.5)*s,y=(row+.5)*s;
                add(out,ngon(x,y,r,24,0));
                add(out,rect(x+r+.025*s,y-b,x+s-r-.025*s,y+b));
                add(out,rect(x-b,y+r+.025*s,x+b,y+s-r-.025*s));
            }
        }return out;
    }

    // Image 5: center diamond, 4 radial kites, short quarter-circle arc dashes,
    // and two lattice-node circles. No pieces intersect before Snip clipping.
    private static List<float[]> rosettes(float w,float h,float u){
        ArrayList<float[]> out=new ArrayList<>();double s=3.7*u;
        for(int row=-2;row<rows(h,s);row++){
            StyleKit.checkCancelled();for(int col=-2;col<cols(w,s);col++){
                double x=(col+.5)*s,y=(row+.5)*s;
                add(out,poly(x,y-.145*s,x+.145*s,y,x,y+.145*s,x-.145*s,y));
                for(int k=0;k<4;k++){
                    double a=k*Math.PI*.5,ca=Math.cos(a),sa=Math.sin(a);
                    add(out,poly(x+ca*.195*s-sa*.055*s,y+sa*.195*s+ca*.055*s,
                        x+ca*.32*s-sa*.035*s,y+sa*.32*s+ca*.035*s,
                        x+ca*.32*s+sa*.035*s,y+sa*.32*s-ca*.035*s,
                        x+ca*.195*s+sa*.055*s,y+sa*.195*s-ca*.055*s));
                    // A short curved dash in each quadrant; 6 corners per dash.
                    double at=a+Math.PI*.25,lo=.31*s,hi=.365*s;
                    add(out,poly(x+lo*Math.cos(at-.19),y+lo*Math.sin(at-.19),
                        x+hi*Math.cos(at-.19),y+hi*Math.sin(at-.19),
                        x+hi*Math.cos(at),y+hi*Math.sin(at),
                        x+hi*Math.cos(at+.19),y+hi*Math.sin(at+.19),
                        x+lo*Math.cos(at+.19),y+lo*Math.sin(at+.19),
                        x+lo*Math.cos(at),y+lo*Math.sin(at)));
                }
                add(out,ngon(x+.5*s,y, .085*s,16,0));
                add(out,ngon(x,y+.5*s, .085*s,16,0));
                add(out,poly(x+.5*s,y+.5*s-.08*s,x+.5*s+.08*s,y+.5*s,
                    x+.5*s,y+.5*s+.08*s,x+.5*s-.08*s,y+.5*s));
            }
        }return out;
    }

    // Image 6: genuine hexagon grid with a second inset hexagonal border.
    private static List<float[]> nestedHex(float w,float h,float u){
        ArrayList<float[]> out=new ArrayList<>();
        for(float[] hex:TessellationMotifEngine.tiles(TessellationMotifEngine.HEXAGON,w,h,u)){
            StyleKit.checkCancelled();ringPlusCore(out,hex,.89,.63,.49);
        }return out;
    }

    // Image 7: every isometric rhombus is divided into its four border bands
    // and a separate inset diamond. Three rhombi meet per hexagon.
    private static List<float[]> rhombusCubes(float w,float h,float u){
        ArrayList<float[]> out=new ArrayList<>();double r=2*u;
        int nx=cols(w,1.5*r)+2,ny=rows(h,SQRT3*r)+2;
        for(int q=-3;q<nx;q++){
            StyleKit.checkCancelled();for(int row=-3;row<ny;row++){
                double x=1.5*r*q,y=SQRT3*r*(row+((q&1)!=0?.5:0));
                if(x< -3*r||x>w+3*r||y< -3*r||y>h+3*r)continue;
                for(int k=0;k<6;k+=2){
                    double angle=k*Math.PI/3;
                    double ax=r*Math.cos(angle),ay=r*Math.sin(angle);
                    double bx=r*Math.cos(angle+2*Math.PI/3),by=r*Math.sin(angle+2*Math.PI/3);
                    float[] diamond=poly(x,y,x+ax,y+ay,x+ax+bx,y+ay+by,x+bx,y+by);
                    ringPlusCore(out,diamond,.94,.67,.52);
                }
            }
        }return out;
    }

    // Image 8: octave rings and central medallions with little corner
    // diamonds and pairs of bridge bars. All coordinates are cell-local.
    private static List<float[]> octagonLinks(float w,float h,float u){
        ArrayList<float[]> out=new ArrayList<>();double s=3.6*u;
        for(int row=-2;row<rows(h,s);row++){
            StyleKit.checkCancelled();for(int col=-2;col<cols(w,s);col++){
                double x=(col+.5)*s,y=(row+.5)*s;
                ringPlusCore(out,ngon(x,y,.40*s,8,Math.PI/8),.95,.69,.36);
                add(out,poly(x+.5*s,y+.5*s-.10*s,x+.5*s+.10*s,y+.5*s,
                    x+.5*s,y+.5*s+.10*s,x+.5*s-.10*s,y+.5*s));
                for(int side=-1;side<=1;side+=2){
                    // Two short links to each right and lower neighboring ring.
                    add(out,rect(x+.382*s,y+side*.085*s-.032*s,
                        x+.618*s,y+side*.085*s+.032*s));
                    add(out,rect(x+side*.085*s-.032*s,y+.382*s,
                        x+side*.085*s+.032*s,y+.618*s));
                }
            }
        }return out;
    }
}
