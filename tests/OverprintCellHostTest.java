package com.halftonestudio;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Host-only proof: independent overprinted vinyl paths, not nested stencil cuts. */
public final class OverprintCellHostTest {
    private static void ok(boolean good,String problem){if(!good)throw new AssertionError(problem);}
    private static long fingerprint(LayeredCellPortraitEngine.Result r){
        long sig=0xcbf29ce484222325L;
        for(LayeredCellPortraitEngine.Piece p:r.pieces){
            sig=(sig^p.layer)*0x100000001b3L;
            sig=(sig^p.cell)*0x100000001b3L;
            for(float f:p.outer)sig=(sig^Float.floatToIntBits(f))*0x100000001b3L;
            for(float[] hole:p.holes)for(float f:hole)sig=(sig^Float.floatToIntBits(f))*0x100000001b3L;
        }
        return sig;
    }
    private static float[] bounds(float[] xy){
        float[] a={Float.MAX_VALUE,Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE};
        for(int i=0;i<xy.length;i+=2){a[0]=Math.min(a[0],xy[i]);a[1]=Math.min(a[1],xy[i+1]);
            a[2]=Math.max(a[2],xy[i]);a[3]=Math.max(a[3],xy[i+1]);}
        return a;
    }
    private static float overlap(float[] a,float[] b){
        float[] x=bounds(a),y=bounds(b);
        return Math.max(0,Math.min(x[2],y[2])-Math.max(x[0],y[0]))*
               Math.max(0,Math.min(x[3],y[3])-Math.max(x[1],y[1]));
    }
    private static void run(int type,boolean snip,int count){
        int w=156,h=130;int[] photo=new int[w*h];boolean[] allowed=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double dx=(x-w/2.)/(w*.47),dy=(y-h/2.)/(h*.48);
            boolean subject=dx*dx+dy*dy<1;
            allowed[y*w+x]=!snip||subject;
            int shade=Math.max(20,Math.min(235,(int)(100+75*dx+50*dy+(Math.abs(dx)<.3&&dy<-.2?-65:0))));
            int r=shade,g=Math.max(0,Math.min(255,shade+(x%27)*3)),b=Math.max(0,Math.min(255,shade+(y%19)*4));
            photo[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
        }
        int[] orig=photo.clone();
        LayeredCellPortraitEngine.Options o=new LayeredCellPortraitEngine.Options();
        o.widthMm=152.4f;o.columns=17;o.gapMm=.34f;o.seamGapMm=.34f;
        o.minFeatureMm=.30f;o.layerCount=count;o.solidBase=false;
        o.motifDepth=3;o.ornamentation=.74f;o.photoDetail=1f;
        o.overprintKind=type;o.registration=false;
        o.allowedPixels=snip?allowed:null;
        o.colors=new int[]{0xff202431,0xfff4dfca,0xffd48b78,0xff703d65,
                0xff5e778b,0xffc8b44f,0xff40a3a7,0xfff6a4c0};
        LayeredCellPortraitEngine.Result r=LayeredCellPortraitEngine.generate(photo,w,h,o);
        ok(Arrays.equals(photo,orig),"Source photo changed");
        ok(r.options.solidBase,"Overprint lost its solid base");
        ok(r.options.overprintKind==type,"Style ID failed to survive snapshot");
        ok(r.pieceCounts[0]>0,"Empty silhouette");
        ok(r.pieces.size()>(count==2?15:80),"Too few photo-supported marks");
        ok(r.maxContours()<4800,"Contour budget violated");
        for(int layer=0;layer<count;layer++){
            if(r.pieceCounts[layer]==0)continue;
            String svg=r.svg(layer);
            ok(svg.contains("mm\" height=\""),"SVG not in millimeters");
            ok(!svg.contains("<clipPath"),"SVG has clip paths");
            LayeredCellExportSafety.validate(r,layer,svg);
        }
        LayeredCellPortraitEngine.Result second=LayeredCellPortraitEngine.generate(photo,w,h,o);
        ok(fingerprint(r)==fingerprint(second),"Non-deterministic overprint geometry");
        int layered=0,overlapped=0,noHoles=0;
        Map<Integer,LayeredCellPortraitEngine.Piece> first=new HashMap<>();
        for(LayeredCellPortraitEngine.Piece p:r.pieces){
            if(p.layer==0)continue;
            if(p.holes.isEmpty())noHoles++;
            LayeredCellPortraitEngine.Piece prev=first.putIfAbsent(p.cell,p);
            if(prev!=null&&prev.layer!=p.layer){layered++;
                if(overlap(prev.outer,p.outer)>1)overlapped++;
            }
            for(float v:p.outer)ok(Float.isFinite(v),"NaN/Infinity coordinate");
        }
        if(count>=3){ok(layered>10,"Missing independently colored cell pairs");
            ok(overlapped>10,"Overprint marks fail to overlap across sheets");}
        if(!snip)ok(noHoles==r.pieces.size()-r.pieceCounts[0],"Unexpected upper-sheet knockout holes");
        System.out.println("PASS "+LayeredCellPortraitEngine.overprintName(type)+
            (snip?" snipped":" full")+" count="+count+" pieces="+r.pieces.size()+
            " overlapping cells="+overlapped);
    }
    public static void main(String[] args){
        for(int k=1;k<=4;k++){
            run(k,false,8);
            run(k,true,8);
        }
        run(1,false,3);run(4,true,2);
    }
}
