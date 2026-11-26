package com.halftonestudio;

import java.util.*;
import java.io.*;
import java.util.zip.*;

/** Production-style vector union, clip, export and fail-closed review smoke test.
 * JTS is mandatory for NAS/CI; --geos is an independent local reference, not Android. */
public final class M6ConfettiVectorTest {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    static final int[] IDS={27,30,31,32,33,34};
    static void run()throws Exception{
        int w=160,h=128;int[] p=V3AllStyleExportMatrixTest.portrait(w,h);
        boolean[] allowed=new boolean[p.length];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double dx=(x-w*.5)/(w*.48),dy=(y-h*.5)/(h*.47);
            allowed[y*w+x]=dx*dx+dy*dy<1&&!(x>72&&x<88&&y>55&&y<69);
        }
        int[] colors={0xffb08769,0xff241d19,0xff70523b,0xffdbb38d,0xfff5e5cd,0xff9f694d};
        int cases=0,sheets=0,reviews=0;
        for(int id:IDS)for(float width:new float[]{101.6f,279.4f}){
            VinylStyleSuite.Config c=new VinylStyleSuite.Config();c.style=id;c.widthMm=width;c.detail=82;c.colorCount=colors.length;c.palette=colors.clone();c.minimumMm=width<150?.8f:.254f;c.gapMm=.5f;c.allowed=allowed;
            StyleKit.Result r=VinylStyleSuite.generate(p,w,h,c);
            check(r.layers.size()==colors.length,"missing physical slot "+id+" @ "+width);
            check(r.layers.get(0).vectorGeometryLocked,"not vector foundation");
            // Intended Snip hole is open; retained interior is filled.
            for(float[] q:new float[][]{{width*.5f,r.heightMm*.5f},{width*.3f,r.heightMm*.4f}}){
                boolean inside=false;for(float[] l:r.layers.get(0).loops)if(MaskedFacetClipper.inside(l,q[0],q[1]))inside=!inside;
                check(inside==(q[0]<width*.4f),"foundation Snip hole lost");
            }
            float[][] marks=RegistrationMarks.aboveBelowPositions(r.widthMm,r.heightMm,2.5f,1f);
            int review=0,loops=0;
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(ZipOutputStream zip=new ZipOutputStream(bytes)){
                CutSheetZipWriter cuts=new CutSheetZipWriter(zip,OutputDeviceProfile.CRICUT,c.minimumMm,c.gapMm,true).selectMat("12x12");
                for(int i=0;i<r.layers.size();i++){
                    StyleKit.Layer l=r.layers.get(i);check(l.vectorGeometryLocked&&!l.loops.isEmpty()&&l.color==colors[i],"invalid slot "+i);
                    String svg=NewStyleExport.layerSvg(r,i,true,RegistrationMarks.CIRCLE,2.5f,1f,marks,c.minimumMm);
                    SvgDxfR13Exporter.SafetyGeometry g=SvgDxfR13Exporter.safetyGeometry(svg);
                    SvgDxfR13Exporter.Result d=SvgDxfR13Exporter.convert(svg);
                    check(g.openPaths==0&&d.openPaths==0&&g.circles.size()==4,"open paths or missing registration marks");
                    check(Math.abs(g.widthMm-d.widthMm)<.05&&Math.abs(g.heightMm-d.heightMm)<.05,"DXF frame mismatch");
                    check(g.closedLoops.size()+g.circles.size()<=5000,"mat import contour limit");
                    cuts.put("layer-"+i+".svg",svg);loops+=g.closedLoops.size();sheets++;
                }
                cuts.finish();review=cuts.reviewSheetCount();
            }
            boolean warning=false;try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))){
                for(ZipEntry e;(e=in.getNextEntry())!=null;)if(e.getName().equals("NOT-CUT-QUALIFIED.txt"))warning=true;
            }
            check(warning==(review>0),"qualification warning not preserved");
            cases++;reviews+=review;
            System.out.println("vector style="+id+" width="+width+" loops="+loops+" review sheets="+review);
        }
        System.out.println("PASS vector export/review contract: "+cases+" cases, "+sheets+" sheets, "+reviews+" sheets require review (NOT physical qualification)");
    }
    public static void main(String[] args)throws Exception{
        if(args.length>0&&args[0].equals("--geos")){
            ParallelGeometry.forceSequential=true;
            try(GeosVectorReferenceBackend ref=new GeosVectorReferenceBackend()){VectorBooleanEngine.install(ref);run();}
        }else{VectorBooleanEngine.install((VectorBooleanEngine.Backend)Class.forName("com.halftonestudio.JtsVectorBooleanBackend").getDeclaredConstructor().newInstance());run();}
    }
}
