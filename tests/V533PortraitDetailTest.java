package com.halftonestudio;
import java.util.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
/** Real rendered geometry and compositing checks, not only option presence. */
public final class V533PortraitDetailTest {
    static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
    static final int W=240,H=240;
    static final int[] COLORS={0xff2b1b15,0xff543424,0xff78503e,0xffa57962,0xffd39572,0xffedbc9c,0xff705f70,0xffefdfc9};
    static int[] photo(){int[] out=new int[W*H];for(int y=0;y<H;y++)for(int x=0;x<W;x++){
        float dx=(x-W*.5f)/(W*.37f),dy=(y-H*.46f)/(H*.42f);
        float f=dx*dx+dy*dy;int l=f>1?44:Math.round(203-75*f+14*(float)Math.sin(x*.22+y*.07));
        if((Math.abs(dx-.35f)<.20f||Math.abs(dx+.35f)<.20f)&&Math.abs(dy+.17f)<.075f)l=26;
        if(Math.abs(dx)<.38f&&Math.abs(dy-.42f)<.06f)l=32;
        l=Math.max(10,Math.min(225,l));out[y*W+x]=0xff000000|((l+22)<<16)|(l<<8)|Math.max(0,l-18);
    }return out;}
    static boolean[] mask(){boolean[] m=new boolean[W*H];for(int y=0;y<H;y++)for(int x=0;x<W;x++){float dx=(x-W*.5f)/(W*.49f),dy=(y-H*.5f)/(H*.48f);m[y*W+x]=dx*dx+dy*dy<1;}return m;}
    static LayeredCellPortraitEngine.Options opts(float size,int cols){LayeredCellPortraitEngine.Options o=new LayeredCellPortraitEngine.Options();o.widthMm=size;o.columns=cols;o.layerCount=8;o.colors=COLORS.clone();o.photoDetail=1;o.ornamentation=.95f;o.motifDepth=3;o.gapMm=.35f;o.seamGapMm=.31f;o.minFeatureMm=.304f;o.registration=false;o.solidBase=true;o.allowedPixels=mask();return o;}
    static void fish(int[] px){for(float size:new float[]{101.6f,152.4f,279.4f}){
        LayeredCellPortraitEngine.Options o=opts(size,34);o.columns=LayeredCellReleaseProfile.safeColumns(o.columns,W,H,size,o.minFeatureMm,o.gapMm,true);
        o.detailMotifOverlay=true;LayeredCellPortraitEngine.Result a=FishScaleMosaicEngine.generate(px,W,H,o);
        check(a.pieceCounts[7]>10,"missing accent at "+size);
        Map<Integer,ArrayList<LayeredCellPortraitEngine.Piece>> bodies=new HashMap<>();
        for(LayeredCellPortraitEngine.Piece p:a.pieces)if(p.layer>0&&p.layer<7)bodies.computeIfAbsent(p.cell,k->new ArrayList<>()).add(p);
        for(LayeredCellPortraitEngine.Piece p:a.pieces)if(p.layer==7){boolean inside=false;
            for(LayeredCellPortraitEngine.Piece b:bodies.getOrDefault(p.cell,new ArrayList<>())){boolean all=true;for(int i=0;i<p.outer.length;i+=2)all&=b.contains(p.outer[i],p.outer[i+1]);inside|=all;}
            check(inside,"orphan accent");}
        LayeredCellExportSafety.validate(a,7,a.svg(7));
        o.detailMotifOverlay=false;LayeredCellPortraitEngine.Result b=FishScaleMosaicEngine.generate(px,W,H,o);
        check(b.pieces.size()<a.pieces.size(),"toggle ineffective");
        System.out.println("fish width="+size+" accents="+a.pieceCounts[7]);
    }}
    static int motifs(LayeredCellPortraitEngine.Result r){int n=0;for(LayeredCellPortraitEngine.Piece p:r.pieces)if(p.cell>=0&&p.outer.length>8)n++;return n;}
    static void cells(int[] px){for(boolean offset:new boolean[]{false,true})for(float size:new float[]{101.6f,152.4f,279.4f}){
        LayeredCellPortraitEngine.Options o=opts(size,40);o.family=LayeredCellPortraitEngine.ELLIPSE;o.detailMotifOverlay=false;
        LayeredCellPortraitEngine.Result a=offset?OffsetCellPortraitEngine.generate(px,W,H,o):LayeredCellPortraitEngine.generate(px,W,H,o);
        int n=motifs(a);Map<Integer,Set<Integer>> occupied=new HashMap<>();
        for(LayeredCellPortraitEngine.Piece p:a.pieces)if(p.cell>=0)occupied.computeIfAbsent(p.cell,k->new HashSet<>()).add(p.layer);
        long stacked=occupied.values().stream().filter(v->v.size()>1).count();check(stacked>30,"no physical multi-layer cells");
        check(n>30,"motifs absent at "+size+" offset="+offset);
        for(LayeredCellPortraitEngine.Piece p:a.pieces)if(p.cell>=0&&p.holes.size()>0){ // real Snip holes only; this mask has none
            throw new AssertionError("unexpected nested cutout");}
        for(int i=0;i<8;i++)if(a.pieceCounts[i]>0)LayeredCellExportSafety.validate(a,i,a.svg(i));
        o.motifDepth=1;LayeredCellPortraitEngine.Result single=offset?OffsetCellPortraitEngine.generate(px,W,H,o):LayeredCellPortraitEngine.generate(px,W,H,o);
        check(motifs(single)<n,"motif depth ineffective");o.motifDepth=3;
        o.family=LayeredCellPortraitEngine.DIAMOND;LayeredCellPortraitEngine.Result b=offset?OffsetCellPortraitEngine.generate(px,W,H,o):LayeredCellPortraitEngine.generate(px,W,H,o);
        boolean different=false;for(int i=0;i<8;i++)different|=!a.svg(i).equals(b.svg(i));check(different,"motif control ineffective");
        System.out.println("cells offset="+offset+" width="+size+" motifs="+n);
    }}
    static double light(int c){return .2126*((c>>16)&255)+.7152*((c>>8)&255)+.0722*(c&255);}
    /** Render actual opaque vector layers in physical order for a fidelity score. */
    static double corr(StyleKit.Result r,int[] src,boolean[] mask){BufferedImage im=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);Graphics2D g=im.createGraphics();g.scale(W/r.widthMm,H/r.heightMm);
        for(StyleKit.Layer l:r.layers){Path2D.Float p=new Path2D.Float(Path2D.WIND_EVEN_ODD);for(float[] q:l.loops){p.moveTo(q[0],q[1]);for(int j=2;j<q.length;j+=2)p.lineTo(q[j],q[j+1]);p.closePath();}g.setColor(new Color(l.color));g.fill(p);}g.dispose();
        double sx=0,sy=0,sxx=0,syy=0,sxy=0,n=0;for(int i=0;i<src.length;i++)if(mask[i]){double x=light(src[i]),y=light(im.getRGB(i%W,i/W));sx+=x;sy+=y;sxx+=x*x;syy+=y*y;sxy+=x*y;n++;}return (n*sxy-sx*sy)/Math.sqrt((n*sxx-sx*sx)*(n*syy-sy*sy));}
    static void camo(int[] px,boolean compareOnly){int[] ids={35,40,41,42};for(int id:ids)for(float size:new float[]{152.4f,279.4f}){
        NineLayerCraftPatternEngine.Options o=new NineLayerCraftPatternEngine.Options();o.pattern=id-35;o.widthMm=size;o.detail=94;o.layers=9;o.allowed=mask();o.palette=CamouflagePalette.defaults(id,null);o.minCutMm=.3245f;
        StyleKit.Result r=NineLayerCraftPatternEngine.generate(px,W,H,o);double c=corr(r,px,o.allowed);int loops=0;for(StyleKit.Layer l:r.layers){check(!l.loops.isEmpty(),"empty sheet");loops+=l.loops.size();}
        if(!compareOnly){
            double baseline=id==35?(size<200?.8819:.8691):id==40?(size<200?.8762:.8529):id==41?(size<200?.9338:.9362):(size<200?.8186:.8701);
            if(id==41)check(Math.abs(c-baseline)<.001,"urban output regression: "+c);
            else check(c>baseline+.007,"composite fidelity did not improve: "+c);
            if(size>200){int warnings=StyleCutGeometryPreflight.inspect(r,.254f,.25f,300).size();
                int old=id==35?15:id==40?6:id==41?25:72;
                check(warnings<=old,"new cut clearance regression: "+warnings+" vs "+old);
                System.out.println("cut warning count "+id+": "+warnings+" (baseline "+old+")");
            }
        }System.out.printf(Locale.ROOT,"camo %d %.1f corr=%.4f loops=%d%n",id,size,c,loops);
    }}
    public static void main(String[] args){int[] px=photo();if(args.length>0){camo(px,true);return;}fish(px);cells(px);camo(px,false);System.out.println("PASS V533 visible portrait detail and real accent overlays");}
}
