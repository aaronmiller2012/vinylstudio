package com.halftonestudio;

import java.util.Arrays;

/** v2.8 regression tests for region tracing, openings, paper, snips and SVGs. */
public final class StencilVectorTest {
    private static void ok(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static VinylEngine.Settings preset(){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.layers.clear();s.layers.add(new VinylEngine.Layer("Black",0xff101010));
        s.layers.add(new VinylEngine.Layer("Red",0xffeb2222));
        s.mode=VinylEngine.STENCIL;s.stencilCleanup=0;s.stencilSmoothing=0;
        s.stencilPaperBackground=true;s.widthMm=100;s.marginMm=0;s.masterSpacingMm=2.5f;
        s.minimumMm=.4f;return s;
    }
    private static int[] picture(int w,int h,int color){int[] p=new int[w*h];Arrays.fill(p,color);return p;}
    private static VinylEngine.Result generate(VinylEngine.Settings s,int[] data,int w,int h){
        return VinylEngine.generate(data,w,h,s);
    }
    private static void testPaperOpening(){
        VinylEngine.Settings s=preset();int w=40,h=40;
        int[] photo=picture(w,h,0xfffaf9f6);
        for(int y=8;y<32;y++)for(int x=8;x<32;x++)photo[y*w+x]=0xff101010;
        for(int y=15;y<25;y++)for(int x=15;x<25;x++)photo[y*w+x]=0xfffaf9f6;
        VinylEngine.Result r=generate(s,photo,w,h);
        ok(r.shapes.get(0).size()==1,"ring should be ONE connected region");
        VinylEngine.Shape donut=r.shapes.get(0).get(0);
        ok(donut.contours!=null&&donut.contours.length==2,"paper opening must be a compound path, not a filled square");
        String svg=VinylEngine.svg(r,0);
        ok(svg.contains("fill-rule=\"evenodd\""),"opening must remain empty in SVG");
        ok(!svg.contains("<clipPath")&&!svg.contains("<mask")&&!svg.contains("<image"),"Cricut unsupported elements forbidden");
        ok(VinylEngine.svgElementsForLayer(r,0)==VinylEngine.registrationElements(r)+2,"count both boundary loops");
        VinylDiagnostics.Report report=VinylDiagnostics.analyze(r,160);
        ok(report.layers[0].coveredPct>20&&report.layers[0].coveredPct<38,"diagnostic should subtract opening, actual="+report.layers[0].coveredPct);
        ok(r.shapes.get(1).size()==0,"paper is not converted into other vinyl layers");
        ok(VinylEngine.validateExport(r)==null,"donut path and registration marks should pass");
    }
    private static void testSnipAndTransparency(){
        VinylEngine.Settings s=preset();int[] img=picture(40,40,0xff101010);
        // A large removed square must become a genuine hole rather than being
        // exported as a filled exterior polygon.
        s.snips.add(new SnipMask.Operation(SnipMask.REMOVE_INSIDE,new float[]{.25f,.25f,.75f,.25f,.75f,.75f,.25f,.75f}));
        VinylEngine.Result r=generate(s,img,40,40);
        ok(r.shapes.get(0).size()==1,"snipped exterior is still one physical region");
        ok(r.shapes.get(0).get(0).contours.length==2,"removed square must be an SVG hole");
        ok(VinylDiagnostics.analyze(r,120).layers[0].coveredPct<80,"snip must be missing from coverage estimate");
        ok(VinylEngine.validateExport(r)==null,"snip region should remain exportable");
        int[] transparent=picture(40,40,0xff101010);
        for(int y=0;y<40;y++)for(int x=0;x<20;x++)transparent[y*40+x]=0x00101010;
        VinylEngine.Settings alpha=preset();r=generate(alpha,transparent,40,40);
        ok(VinylDiagnostics.analyze(r,160).layers[0].coveredPct<60,"transparent pixels must not generate cut vinyl");
    }
    private static void testSeparateColorRegions(){
        VinylEngine.Settings s=preset();int[] px=picture(40,40,0xff101010);
        for(int y=0;y<40;y++)for(int x=20;x<40;x++)px[y*40+x]=0xffeb2222;
        VinylEngine.Result r=generate(s,px,40,40);
        ok(r.shapes.get(0).size()==1&&r.shapes.get(1).size()==1,"each contiguous flat color should have one path");
        VinylDiagnostics.Report report=VinylDiagnostics.analyze(r,160);
        float sum=report.layers[0].coveredPct+report.layers[1].coveredPct;
        ok(sum>95&&sum<=100.01,"exclusive palette assignment must not duplicate geometry: "+sum);
        ok(r.settings.layers.get(0).color==0xff101010,"vinyl color identity preserved");
        ok(VinylEngine.svg(r,0).equals(VinylEngine.svg(generate(s,px,40,40),0)),"vector result must be deterministic");
    }
    private static void testDiagonalCornersAndSmoothing(){
        VinylEngine.Settings s=preset();s.masterSpacingMm=10;s.stencilCleanup=0;
        int[] px=picture(10,10,0xfffaf9f6);
        px[2*10+2]=0xff101010;px[3*10+3]=0xff101010;
        VinylEngine.Result r=generate(s,px,10,10);
        ok(r.shapes.get(0).size()==2,"diagonal islands must remain distinct instead of self-intersecting");
        s.stencilSmoothing=.2f;
        r=generate(s,px,10,10);
        ok(r.shapes.get(0).size()==2,"corner bevel must preserve two islands");
        for(VinylEngine.Shape sh:r.shapes.get(0))ok(sh.xy.length==16,"bevelled square should have eight vertices");
    }
    private static void testLayerCap(){
        VinylEngine.Settings s=preset();s.widthMm=200;s.masterSpacingMm=2f;
        int size=100;int[] px=picture(size,size,0xfffaf9f6);
        for(int y=0;y<size;y++)for(int x=0;x<size;x++)if((x+y)%2==0)px[y*size+x]=0xff101010;
        boolean guarded=false;
        try{generate(s,px,size,size);}catch(IllegalArgumentException ex){guarded=ex.getMessage().contains("5,000");}
        ok(guarded,"every individual island counts toward the SVG 5000-path safety limit");
    }
    private static void testEightColors(){
        for(int count:new int[]{2,4,8}){
            VinylEngine.Settings s=preset();s.layers.clear();int[] palette=VinylPalettes.get(0,count);
            for(int i=0;i<count;i++)s.layers.add(new VinylEngine.Layer("Vinyl "+i,palette[i]));
            s.stencilPaperBackground=false;
            int[] px=picture(80,40,palette[0]);
            for(int y=0;y<40;y++)for(int x=0;x<80;x++)px[y*80+x]=palette[Math.min(count-1,x*count/80)];
            VinylEngine.Result r=generate(s,px,80,40);
            for(int i=0;i<count;i++)ok(!r.shapes.get(i).isEmpty(),count+"-color palette missing "+i);
            ok(VinylEngine.validateExport(r)==null,"8-color SVG must validate");
        }
    }
    private static boolean pointIn(float[] p,float x,float y){
        boolean inside=false;for(int i=0,j=p.length-2;i<p.length;j=i,i+=2){
            float yi=p[i+1],yj=p[j+1],xi=p[i],xj=p[j];
            if((yi>y)!=(yj>y) && x < (xj-xi)*(y-yi)/(yj-yi)+xi)inside=!inside;
        }return inside;
    }
    private static boolean contains(VinylEngine.Shape sh,float x,float y){
        if(sh.circle)return Math.hypot(x-sh.x,y-sh.y)<sh.r;
        boolean yes=false;
        float[][] rings=sh.contours==null?new float[][]{sh.xy}:sh.contours;
        for(float[] ring:rings)if(pointIn(ring,x,y))yes=!yes;
        return yes;
    }
    private static void testRandomizedGeometry(){
        java.util.Random rng=new java.util.Random(2852026L);
        VinylEngine.Settings s=preset();s.widthMm=64;s.masterSpacingMm=4;
        s.stencilCleanup=0;s.stencilSmoothing=0;
        int white=0xfffaf9f6,black=0xff101010,red=0xffeb2222;
        for(int trial=0;trial<75;trial++){
            int[] pixels=new int[16*16],owners=new int[pixels.length];
            for(int k=0;k<pixels.length;k++){
                int c=rng.nextInt(3);pixels[k]=c==0?white:c==1?black:red;owners[k]=c-1;
            }
            VinylEngine.Result result=generate(s,pixels,16,16);
            for(int y=0;y<16;y++)for(int x=0;x<16;x++){
                float xx=x*4+2,yy=y*4+2;
                int actual=-1;
                for(int li=0;li<2;li++)for(VinylEngine.Shape sh:result.shapes.get(li))if(contains(sh,xx,yy)){
                    ok(actual==-1,"overlapping colors at sample point");actual=li;
                }
                ok(actual==owners[y*16+x],"stencil geometry must represent exact owner at trial="+trial+" x="+x+" y="+y+" expected="+owners[y*16+x]+" actual="+actual);
            }
        }
    }
    public static void main(String[] args){testPaperOpening();testSnipAndTransparency();testSeparateColorRegions();testDiagonalCornersAndSmoothing();testLayerCap();testEightColors();testRandomizedGeometry();
        System.out.println("PASS: Lab palette stencils; holes; paper; snips; alpha; exclusive colors; corner tracing; path cap; 2-8 colors");
    }
}
