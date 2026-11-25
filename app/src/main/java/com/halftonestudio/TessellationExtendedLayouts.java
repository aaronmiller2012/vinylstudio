package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Periodic tessellation constructions in millimetres. Independent of Android.
 * Tile edges are shared before the presentation renderer adds a weed gap.
 * This class does NOT rename or reinterpret the original eight presets.
 */
final class TessellationExtendedLayouts {
    private TessellationExtendedLayouts() { }
    private static final double ROOT3=Math.sqrt(3.0);
    private static final double TRUNC=1.0/(2.0+ROOT3);

    private static float[] p(double... a){float[] f=new float[a.length];
        for(int i=0;i<a.length;i++)f[i]=(float)a[i];return f;}
    private static double area(float[] p){double a=0;int n=p.length/2;
        for(int i=0;i<n;i++){int j=(i+1)%n;
            a+=(double)p[i*2]*p[j*2+1]-(double)p[j*2]*p[i*2+1];}
        return .5*a;
    }
    private static float[] ccw(float[] p){if(area(p)>=0)return p;
        int n=p.length/2;float[] r=new float[p.length];
        for(int i=0;i<n;i++){r[i*2]=p[2*(n-1-i)];r[i*2+1]=p[2*(n-1-i)+1];}
        return r;
    }
    private static String key(float x,float y){return Math.round(x*1000)+":"+Math.round(y*1000);}
    private static float[] polygon(double x,double y,double r,int n){float[] f=new float[n*2];
        for(int k=0;k<n;k++){double t=2*Math.PI*k/n;f[2*k]=(float)(x+r*Math.cos(t));f[2*k+1]=(float)(y+r*Math.sin(t));}
        return f;
    }
    private static void add(List<float[]> out,float[] polygon){if(polygon.length>=6 && Math.abs(area(polygon))>1e-4)out.add(ccw(polygon));}
    private static float[] rect(double a,double b,double c,double d){return p(a,b,c,b,c,d,a,d);}

    private static final class Vertex {
        final float x,y; final HashMap<String,float[]> incident=new HashMap<>();
        Vertex(float x,float y){this.x=x;this.y=y;}
    }
    private static HashMap<String,Vertex> vertices(List<float[]> faces,boolean useCenters){
        HashMap<String,Vertex> index=new HashMap<>();
        for(float[] face:faces){
            StyleKit.checkCancelled();
            int n=face.length/2;float[] ctr=centroid(face);
            for(int i=0;i<n;i++){
                float x=face[2*i],y=face[2*i+1];String id=key(x,y);
                Vertex v=index.get(id);if(v==null){v=new Vertex(x,y);index.put(id,v);}
                if(useCenters)v.incident.put(key(ctr[0],ctr[1]),ctr);
                else {
                    int before=(i+n-1)%n,after=(i+1)%n;
                    float[] prev=p(face[2*before],face[2*before+1]),next=p(face[2*after],face[2*after+1]);
                    v.incident.put(key(prev[0],prev[1]),prev);
                    v.incident.put(key(next[0],next[1]),next);
                }
            }
        }
        return index;
    }
    private static float[] centroid(float[] f){double a=0,x=0,y=0;int n=f.length/2;
        for(int i=0;i<n;i++){
            int j=(i+1)%n;double cross=(double)f[2*i]*f[2*j+1]-(double)f[2*j]*f[2*i+1];
            a+=cross;x+=(f[2*i]+f[2*j])*cross;y+=(f[2*i+1]+f[2*j+1])*cross;
        }
        if(Math.abs(a)>1e-7)return p(x/(3*a),y/(3*a));
        x=y=0;for(int i=0;i<n;i++){x+=f[2*i];y+=f[2*i+1];}
        return p(x/n,y/n);
    }
    private static float[] sorted(Vertex v){ArrayList<float[]> s=new ArrayList<>(v.incident.values());
        Collections.sort(s,(a,b)->Double.compare(Math.atan2(a[1]-v.y,a[0]-v.x),Math.atan2(b[1]-v.y,b[0]-v.x)));
        float[] out=new float[2*s.size()];for(int i=0;i<s.size();i++){out[2*i]=s.get(i)[0];out[2*i+1]=s.get(i)[1];}
        return out;
    }
    /** Dual of an edge-to-edge tiling: each source vertex becomes a face. */
    private static List<float[]> dual(List<float[]> source){ArrayList<float[]> out=new ArrayList<>();
        for(Vertex v:vertices(source,true).values()){
            if(v.incident.size()<3)continue;
            float[] a=sorted(v);add(out,a);
        }
        return out;
    }
    /** Proper edge truncation for regular uniform primal polygons; vertex holes close automatically. */
    private static List<float[]> truncate(List<float[]> source){ArrayList<float[]> out=new ArrayList<>();
        for(float[] f:source){StyleKit.checkCancelled();int n=f.length/2;
            float[] t=new float[4*n];
            for(int i=0;i<n;i++){
                int j=(i+1)%n;double ax=f[2*i],ay=f[2*i+1],bx=f[2*j],by=f[2*j+1];
                t[4*i]=(float)(ax+(bx-ax)*TRUNC);t[4*i+1]=(float)(ay+(by-ay)*TRUNC);
                t[4*i+2]=(float)(bx+(ax-bx)*TRUNC);t[4*i+3]=(float)(by+(ay-by)*TRUNC);
            }
            add(out,t);
        }
        for(Vertex v:vertices(source,false).values()){
            if(v.incident.size()<3)continue;
            ArrayList<float[]> s=new ArrayList<>(v.incident.values());
            s.sort((a,b)->Double.compare(Math.atan2(a[1]-v.y,a[0]-v.x),Math.atan2(b[1]-v.y,b[0]-v.x)));
            float[] t=new float[2*s.size()];
            for(int i=0;i<s.size();i++){
                t[i*2]=(float)(v.x+TRUNC*(s.get(i)[0]-v.x));
                t[i*2+1]=(float)(v.y+TRUNC*(s.get(i)[1]-v.y));
            }
            add(out,t);
        }
        return out;
    }
    private static List<float[]> snubTrihex(float w,float h,float unit){
        ArrayList<float[]> out=new ArrayList<>();
        double rise=unit*ROOT3*.5;
        int loRow=-5, hiRow=(int)Math.ceil(h/rise)+5;
        for(int row=loRow;row<=hiRow;row++){
            StyleKit.checkCancelled();
            int start=(int)Math.floor(-row*.5-6),end=(int)Math.ceil(w/unit-row*.5+6);
            for(int col=start;col<=end;col++){
                int[][] a={{col,row},{col+1,row},{col,row+1}},
                        b={{col+1,row+1},{col,row+1},{col+1,row}};
                for(int[][] tri:new int[][][]{a,b}){
                    boolean hex=false;
                    for(int[] v:tri)if(Math.floorMod(3*v[0]+v[1],7)==0){hex=true;break;}
                    if(!hex)add(out,p(unit*(tri[0][0]+tri[0][1]*.5),rise*tri[0][1],
                        unit*(tri[1][0]+tri[1][1]*.5),rise*tri[1][1],
                        unit*(tri[2][0]+tri[2][1]*.5),rise*tri[2][1]));
                }
                if(Math.floorMod(3*col+row,7)==0){
                    add(out,polygon(unit*(col+row*.5),rise*row,unit,6));
                }
            }
        }
        return out;
    }
    private static List<float[]> elongated(float w,float h,float unit){
        ArrayList<float[]> out=new ArrayList<>();double rise=unit*ROOT3*.5,band=unit+rise;
        int rows=(int)Math.ceil(h/band)+5,cols=(int)Math.ceil(w/unit)+7;
        for(int row=-4;row<rows;row++){
            StyleKit.checkCancelled();double y=row*band,shift=(row&1)*.5*unit;
            for(int col=-5;col<cols;col++){
                double x=col*unit+shift;
                add(out,rect(x,y,x+unit,y+unit));
                // Equilateral triangular strip shares exact upper/lower square edges.
                add(out,p(x,y+unit,x+unit,y+unit,x+.5*unit,y+unit+rise));
                add(out,p(x+unit,y+unit,x+1.5*unit,y+unit+rise,x+.5*unit,y+unit+rise));
            }
        }
        return out;
    }
    private static List<float[]> kisrhombille(float w,float h,float unit){
        ArrayList<float[]> out=new ArrayList<>();
        for(float[] hex:TessellationMotifEngine.tiles(TessellationMotifEngine.HEXAGON,w,h,unit)){
            float[] c=centroid(hex);int n=hex.length/2;
            for(int i=0;i<n;i++){
                int j=(i+1)%n;double mx=(hex[2*i]+hex[2*j])*.5,my=(hex[2*i+1]+hex[2*j+1])*.5;
                add(out,p(c[0],c[1],hex[2*i],hex[2*i+1],mx,my));
                add(out,p(c[0],c[1],mx,my,hex[2*j],hex[2*j+1]));
            }
        }
        return out;
    }
    private static List<float[]> tetrakis(float w,float h,float unit){
        ArrayList<float[]> out=new ArrayList<>();
        for(float[] sq:TessellationMotifEngine.tiles(TessellationMotifEngine.SQUARE,w,h,unit)){
            float[] c=centroid(sq);for(int k=0;k<4;k++){int next=(k+1)%4;
                add(out,p(c[0],c[1],sq[2*k],sq[2*k+1],sq[2*next],sq[2*next+1]));}
        }
        return out;
    }
    private static List<float[]> brick(float w,float h,float unit){
        ArrayList<float[]> out=new ArrayList<>();int rows=(int)Math.ceil(h/unit)+4;
        int cols=(int)Math.ceil(w/(2*unit))+5;
        for(int row=-3;row<rows;row++){
            StyleKit.checkCancelled();double yy=row*unit,shift=(row&1)*unit;
            for(int col=-4;col<cols;col++){
                double xx=col*2*unit+shift;add(out,rect(xx,yy,xx+2*unit,yy+unit));
            }
        }return out;
    }
    private static List<float[]> basket(float w,float h,float unit){
        ArrayList<float[]> out=new ArrayList<>();int rows=(int)Math.ceil(h/(2*unit))+4,cols=(int)Math.ceil(w/(2*unit))+4;
        for(int row=-3;row<rows;row++){
            StyleKit.checkCancelled();for(int col=-3;col<cols;col++){
                double x=col*2*unit,y=row*2*unit;
                if(((row+col)&1)==0){
                    add(out,rect(x,y,x+2*unit,y+unit));add(out,rect(x,y+unit,x+2*unit,y+2*unit));
                }else{
                    add(out,rect(x,y,x+unit,y+2*unit));add(out,rect(x+unit,y,x+2*unit,y+2*unit));
                }
            }
        }return out;
    }
    private static List<float[]> herringbone(float w,float h,float unit){
        ArrayList<float[]> out=new ArrayList<>();
        int rows=(int)Math.ceil(h/(4*unit))+4,cols=(int)Math.ceil(w/(4*unit))+4;
        // One 4x4 domino macro with alternating vertical/horizontal stair steps.
        int[][] domino={{0,0,2,1},{2,0,1,2},{3,0,1,2},{0,1,1,2},
                        {1,1,1,2},{2,2,2,1},{0,3,2,1},{2,3,2,1}};
        for(int row=-3;row<rows;row++){
            StyleKit.checkCancelled();for(int col=-3;col<cols;col++){
                double x=col*4*unit,y=row*4*unit;
                for(int[] d:domino)add(out,rect(x+d[0]*unit,y+d[1]*unit,x+(d[0]+d[2])*unit,y+(d[1]+d[3])*unit));
            }
        }return out;
    }
    private static List<float[]> pythagorean(float w,float h,float unit){
        ArrayList<float[]> out=new ArrayList<>();int rows=(int)Math.ceil(h/(4*unit))+4,cols=(int)Math.ceil(w/(4*unit))+4;
        for(int row=-3;row<rows;row++){
            StyleKit.checkCancelled();for(int col=-3;col<cols;col++){
                double x=col*4*unit,y=row*4*unit;
                if(((row+col)&1)==0)add(out,rect(x,y,x+4*unit,y+4*unit));
                else for(int yy=0;yy<2;yy++)for(int xx=0;xx<2;xx++)
                    add(out,rect(x+xx*2*unit,y+yy*2*unit,x+(xx+1)*2*unit,y+(yy+1)*2*unit));
            }
        }return out;
    }
    static List<float[]> tiles(int pattern,float w,float h,float unit){
        switch(pattern){
            case TessellationMotifEngine.TRUNCATED_HEX:
                return truncate(TessellationMotifEngine.tiles(TessellationMotifEngine.HEXAGON,w,h,unit));
            case TessellationMotifEngine.TRUNCATED_TRIHEX:
                return truncate(TessellationMotifEngine.tiles(TessellationMotifEngine.TRIHEX,w,h,unit));
            case TessellationMotifEngine.SNUB_TRIHEX: return snubTrihex(w,h,unit);
            case TessellationMotifEngine.KISRHOMBILLE: return kisrhombille(w,h,unit);
            case TessellationMotifEngine.TETRAKIS: return tetrakis(w,h,unit);
            case TessellationMotifEngine.ELONGATED_TRIANGLE: return elongated(w,h,unit);
            case TessellationMotifEngine.DELTOIDAL_TRIHEX:
                return dual(TessellationMotifEngine.tiles(TessellationMotifEngine.RHOMBITRIHEX,w,h,unit));
            case TessellationMotifEngine.TRIAKIS:
                return dual(truncate(TessellationMotifEngine.tiles(TessellationMotifEngine.HEXAGON,w,h,unit)));
            case TessellationMotifEngine.FLORET_PENTAGON: return dual(snubTrihex(w,h,unit));
            case TessellationMotifEngine.PRISMATIC_PENTAGON: return dual(elongated(w,h,unit));
            case TessellationMotifEngine.RUNNING_BOND: return brick(w,h,unit);
            case TessellationMotifEngine.BASKET_WEAVE: return basket(w,h,unit);
            case TessellationMotifEngine.HERRINGBONE: return herringbone(w,h,unit);
            case TessellationMotifEngine.PYTHAGOREAN: return pythagorean(w,h,unit);
            default: throw new IllegalArgumentException("Unknown extended tiling pattern "+pattern);
        }
    }
}
