package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * Independent subpixel isocontour tracer for vinyl-mask silhouettes.
 * Mask values live at pixel centres, not pixel corners. Threshold intersections
 * are located between centres; saddle cases keep diagonal islands disconnected.
 * All coordinates are in source raster pixels, with the origin at the cut board.
 */
final class SubpixelStencilContours {
    private SubpixelStencilContours() {}
    private static final class Node {
        final double x,y;
        int a=-1,b=-1;
        Node(double xx,double yy){x=xx;y=yy;}
        void link(int n){if(a<0)a=n;else if(b<0 && a!=n)b=n;else throw new IllegalStateException("Non-manifold contour");}
    }
    private static void cancel(){if(Thread.currentThread().isInterrupted())throw new CancellationException("Subpixel trace cancelled");}
    private static double crossing(double a,double b){
        double denom=b-a;
        return Math.abs(denom)<1e-9?.5:Math.max(.01,Math.min(.99,(.5-a)/denom));
    }
    /** Separable Gaussian of masked pixel occupancy, with zero field outside the image. */
    private static float[] field(boolean[] mask,int w,int h,double sigma){
        int sw=w+2,sh=h+2;
        float[] raw=new float[sw*sh];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(mask[y*w+x])raw[(y+1)*sw+x+1]=1f;
        if(sigma<=.001)return raw;
        int reach=Math.max(1,(int)Math.ceil(2.5*sigma));
        double[] weights=new double[2*reach+1];double sum=0;
        for(int k=-reach;k<=reach;k++){double v=Math.exp(-.5*k*k/(sigma*sigma));weights[k+reach]=v;sum+=v;}
        for(int i=0;i<weights.length;i++)weights[i]/=sum;
        float[] tmp=new float[raw.length],out=new float[raw.length];
        for(int y=0;y<sh;y++){
            if((y&31)==0)cancel();
            for(int x=0;x<sw;x++){
                double v=0;for(int d=-reach;d<=reach;d++){
                    int xx=x+d;if(xx>=0&&xx<sw)v+=raw[y*sw+xx]*weights[d+reach];
                }tmp[y*sw+x]=(float)v;
            }
        }
        for(int y=0;y<sh;y++){
            if((y&31)==0)cancel();
            for(int x=0;x<sw;x++){
                double v=0;for(int d=-reach;d<=reach;d++){
                    int yy=y+d;if(yy>=0&&yy<sh)v+=tmp[yy*sw+x]*weights[d+reach];
                }out[y*sw+x]=(float)v;
            }
        }
        return out;
    }
    private static int vertex(int x,int y,int edge,int sw,int hEdges,float tl,float tr,float br,float bl,
                              int[] lookup,ArrayList<Node> nodes){
        int id;double px,py,t;
        switch(edge){
            case 0: id=y*(sw-1)+x;t=crossing(tl,tr);px=x-.5+t;py=y-.5;break;
            case 1: id=hEdges+y*sw+x+1;t=crossing(tr,br);px=x+.5;py=y-.5+t;break;
            case 2: id=(y+1)*(sw-1)+x;t=crossing(bl,br);px=x-.5+t;py=y+.5;break;
            case 3: id=hEdges+y*sw+x;t=crossing(tl,bl);px=x-.5;py=y-.5+t;break;
            default: throw new IllegalArgumentException("Invalid edge");
        }
        int n=lookup[id];if(n!=0)return n-1;
        int next=nodes.size();nodes.add(new Node(px,py));lookup[id]=next+1;return next;
    }
    private static void segment(int x,int y,int e1,int e2,int sw,int hEdges,float tl,float tr,float br,float bl,
                                int[] lookup,ArrayList<Node> nodes){
        int a=vertex(x,y,e1,sw,hEdges,tl,tr,br,bl,lookup,nodes);
        int b=vertex(x,y,e2,sw,hEdges,tl,tr,br,bl,lookup,nodes);
        nodes.get(a).link(b);nodes.get(b).link(a);
    }
    static List<ArrayList<double[]>> trace(boolean[] mask,int w,int h,double sigma){
        if(w<1||h<1||mask==null||mask.length!=(long)w*h)throw new IllegalArgumentException("Invalid mask");
        final int sw=w+2,sh=h+2;
        final int hEdges=(sw-1)*sh;
        float[] values=field(mask,w,h,sigma);
        int[] lookup=new int[hEdges+sw*(sh-1)];
        ArrayList<Node> nodes=new ArrayList<>();
        for(int y=0;y<sh-1;y++){
            if((y&31)==0)cancel();
            for(int x=0;x<sw-1;x++){
                float tl=values[y*sw+x],tr=values[y*sw+x+1],br=values[(y+1)*sw+x+1],bl=values[(y+1)*sw+x];
                int c=(tl>=.5f?1:0)|(tr>=.5f?2:0)|(br>=.5f?4:0)|(bl>=.5f?8:0);
                switch(c){
                    case 0:case 15:break;
                    case 1:case 14:segment(x,y,3,0,sw,hEdges,tl,tr,br,bl,lookup,nodes);break;
                    case 2:case 13:segment(x,y,0,1,sw,hEdges,tl,tr,br,bl,lookup,nodes);break;
                    case 3:case 12:segment(x,y,3,1,sw,hEdges,tl,tr,br,bl,lookup,nodes);break;
                    case 4:case 11:segment(x,y,1,2,sw,hEdges,tl,tr,br,bl,lookup,nodes);break;
                    case 5:
                        segment(x,y,3,0,sw,hEdges,tl,tr,br,bl,lookup,nodes);
                        segment(x,y,1,2,sw,hEdges,tl,tr,br,bl,lookup,nodes);break;
                    case 6:case 9:segment(x,y,0,2,sw,hEdges,tl,tr,br,bl,lookup,nodes);break;
                    case 7:case 8:segment(x,y,3,2,sw,hEdges,tl,tr,br,bl,lookup,nodes);break;
                    case 10:
                        segment(x,y,0,1,sw,hEdges,tl,tr,br,bl,lookup,nodes);
                        segment(x,y,2,3,sw,hEdges,tl,tr,br,bl,lookup,nodes);break;
                    default:throw new IllegalStateException("Unknown marching-squares case");
                }
            }
        }
        ArrayList<ArrayList<double[]>> rings=new ArrayList<>();
        boolean[] visited=new boolean[nodes.size()];
        for(int start=0;start<nodes.size();start++){
            if(visited[start])continue;
            cancel();
            ArrayList<double[]> loop=new ArrayList<>();
            int current=start,previous=-1;
            for(int steps=0;steps<=nodes.size();steps++){
                Node p=nodes.get(current);
                if(p.a<0||p.b<0)throw new IllegalStateException("Open subpixel contour");
                if(visited[current]&&current!=start)throw new IllegalStateException("Crossed subpixel contour");
                if(current==start&&steps>0)break;
                visited[current]=true;
                loop.add(new double[]{p.x,p.y});
                int next=p.a==previous?p.b:p.a;
                previous=current;current=next;
                if(steps==nodes.size())throw new IllegalStateException("Nonclosing subpixel contour");
            }
            if(loop.size()>=3)rings.add(loop);
        }
        return rings;
    }
}
