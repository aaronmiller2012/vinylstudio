package com.halftonestudio;

import java.util.*;

/**
 * Build 555 regression for "Chainmail only renders layers on half the image".
 *
 * Root cause: the shared photo auto palette placed slots at equal sample-count
 * percentiles. On side-lit or dim photos the first overlay landed in the foundation's
 * own tonal cluster (dE ~ 11 or less), so rings over the shadowed half were nearly
 * invisible against L1. This test renders real vector geometry (JTS) and requires every
 * overlay to be visibly distinct from the foundation where it is the top layer, the
 * palette to be ordered and separated, and the cut-piece limit to hold.
 */
public final class V3ChainmailVisibilityTest {
    static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
    static double dE(int a,int b){return Math.sqrt(StyleKit.labDist2(StyleKit.toLab(a),StyleKit.toLab(b)));}
    static boolean inside(List<float[]> loops,float x,float y){
        boolean in=false;
        for(float[] p:loops)for(int i=0,j=p.length-2;i<p.length;j=i,i+=2){
            float ax=p[i],ay=p[i+1],bx=p[j],by=p[j+1];
            if((ay>y)!=(by>y)&&x<(bx-ax)*(y-ay)/(by-ay)+ax)in=!in;
        }
        return in;
    }
    /** Window-lit portrait: left half bright, right half in shadow. */
    static int[] sideLit(int w,int h){
        int[] px=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double dx=(x-w/2.0)/(w*.32),dy=(y-h*.45)/(h*.36);boolean face=dx*dx+dy*dy<1;
            int v=(int)Math.round((x<w/2?225:70)+(face?15:-10)+12*Math.sin(x*.21+y*.13));
            v=Math.max(0,Math.min(255,v));
            px[y*w+x]=0xff000000|(Math.min(255,v+18)<<16)|(v<<8)|Math.max(0,v-14);
        }
        return px;
    }
    /** Under-exposed photo: whole image within a narrow dark range. */
    static int[] dim(int w,int h){
        int[] px=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int v=18+(int)(30.0*x/w)+(int)(8*Math.sin(y*.17));
            px[y*w+x]=0xff000000|(v<<16)|(v<<8)|(v+4);
        }
        return px;
    }
    static void visible(String name,int[] px,int w,int h,int layers){
        VinylStyleSuite.Config c=new VinylStyleSuite.Config();
        c.style=VinylStyleSuite.CHAINMAIL;c.widthMm=203.2f;c.detail=50;c.colorCount=layers;c.minimumMm=.254f;c.draft=false;
        StyleKit.Result r=VinylStyleSuite.generate(px,w,h,c);
        int base=r.layers.get(0).color;
        for(int i=1;i<r.layers.size();i++){
            check(!r.layers.get(i).loops.isEmpty(),name+": empty overlay L"+(i+1));
            check(dE(r.layers.get(i).color,base)>=(layers<=4?15:9),name+": overlay L"+(i+1)+" too close to foundation, dE="+dE(r.layers.get(i).color,base));
            check(PerceptualLayerColorEngine.light(r.layers.get(i).color)>=PerceptualLayerColorEngine.light(r.layers.get(i-1).color)-1e-6,name+": palette not ordered dark to light");
        }
        int G=48;int[] cover=new int[6],faint=new int[6];
        for(int gy=0;gy<G;gy++)for(int gx=0;gx<G;gx++){
            float x=(gx+.5f)*r.widthMm/G,y=(gy+.5f)*r.heightMm/G;
            for(int i=r.layers.size()-1;i>=1;i--)if(inside(r.layers.get(i).loops,x,y)){
                int b=gx*6/G;cover[b]++;if(dE(r.layers.get(i).color,base)<8)faint[b]++;break;
            }
        }
        for(int b=0;b<6;b++){
            check(cover[b]>0,name+": no rings in sixth "+(b+1));
            check(faint[b]*100<=cover[b]*5,name+": "+(faint[b]*100/cover[b])+"% near-invisible rings in sixth "+(b+1));
        }
    }
    public static void main(String[] args){
        VectorBooleanEngine.install(new JtsVectorBooleanBackend());
        for(int layers:new int[]{4,6}){
            visible("side-lit",sideLit(300,400),300,400,layers);
            visible("dim",dim(240,320),240,320,layers);
        }
        // Cut-piece limit at a busy size (the old 1.65x occupancy cap overloaded one sheet).
        int[] portrait=V3AllStyleExportMatrixTest.portrait(120,150);
        for(float size:new float[]{76.2f,203.2f,279.4f}){
            VinylStyleSuite.Config c=new VinylStyleSuite.Config();
            c.style=VinylStyleSuite.CHAINMAIL;c.widthMm=size;c.detail=50;c.colorCount=4;c.minimumMm=.254f;c.draft=false;
            StyleKit.Result r=VinylStyleSuite.generate(portrait,120,150,c); // throws if any sheet exceeds 5000 pieces
            check(r.layers.size()==4,"chainmail layers at "+size);
        }
        // Palette unit checks: separation guaranteed even for a flat gray photo.
        int[] flat=new int[64*64];Arrays.fill(flat,0xff606060);
        StyleKit.Source s=new StyleKit.Source(flat,64,64,null,100f);
        int[] p=FallenConfettiEngine.autoPalette(s,4);
        for(int i=1;i<p.length;i++)check(PerceptualLayerColorEngine.light(p[i])-PerceptualLayerColorEngine.light(p[i-1])>.10,"flat photo palette must still be separated");
        System.out.println("PASS 555 chainmail visibility: foundation contrast, no near-invisible bands, ordered palette, piece limit");
    }
}
