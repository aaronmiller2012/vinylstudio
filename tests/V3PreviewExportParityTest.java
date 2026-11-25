package com.halftonestudio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * D7: the cut-ready preview IS the exported geometry for suite (art) styles.
 *
 * Build 556 drew the generated geometry and ran prepareForCutting only inside the export, which
 * changed about half of style/size cases unseen. The app now runs SuiteCutStage after the final
 * render, shows stage.cut, and exports SuiteCutStage.reusable(...) without regenerating.
 *
 * For representative styles at 3 in and 10.5 in this test compares, contour by contour, the
 * preview geometry (stage.cut, what NewStyleVectorCache draws) with the PARSED exported sheets
 * (NewStyleExport through the real SVG parser), and checks the reuse/invalidation contract and
 * cancellation.
 */
public final class V3PreviewExportParityTest {
    static final List<String> failures=new ArrayList<>();
    static int checks,changedByConditioning,cases;
    static void check(boolean ok,String m){checks++;if(!ok)failures.add(m);}

    static final int[] STYLES={VinylStyleSuite.LINOCUT,VinylStyleSuite.TRUCHET,VinylStyleSuite.PRISMATIC_DIAGONAL,
        VinylStyleSuite.CONFETTI,VinylStyleSuite.FISHNET,VinylStyleSuite.WOVEN_RIBBON,VinylStyleSuite.LOW_POLY,
        VinylStyleSuite.GLASS};

    static double area(float[] p){double a=0;int n=p.length/2;for(int i=0;i<n;i++){int j=(i+1)%n;a+=p[2*i]*(double)p[2*j+1]-p[2*j]*(double)p[2*i+1];}return Math.abs(a)/2;}

    static boolean sameLoops(List<float[]> a,List<float[]> b){
        if(a.size()!=b.size())return false;
        for(int i=0;i<a.size();i++)if(!java.util.Arrays.equals(a.get(i),b.get(i)))return false;
        return true;
    }

    /** Preview contours of one layer vs the closed loops parsed from its exported SVG. */
    static void compareSheet(String tag,StyleKit.Result shown,int layer,boolean marks) throws Exception {
        StyleKit.Layer l=shown.layers.get(layer);
        String svg=NewStyleExport.layerSvg(shown,layer,marks,RegistrationMarks.CROSS,2.5f,1f,null,.8f,false);
        SvgDxfR13Exporter.SafetyGeometry g=SvgDxfR13Exporter.safetyGeometry(svg);
        float pad=(float)((g.widthMm-shown.widthMm)/2);
        List<float[]> exported=new ArrayList<>(g.closedLoops);
        int markLoops=marks?4:0;
        check(exported.size()==l.loops.size()+markLoops,
            String.format(Locale.US,"%s L%d: preview has %d contours, export has %d (+%d marks)",tag,layer,l.loops.size(),exported.size()-markLoops,markLoops));
        if(exported.size()!=l.loops.size()+markLoops)return;
        double previewArea=0,exportArea=0,maxErr=0,perimeter=0;
        for(int i=0;i<l.loops.size();i++){
            float[] p=l.loops.get(i),q=exported.get(i);
            if(p.length!=q.length){maxErr=Double.POSITIVE_INFINITY;break;}
            // The shared parser returns cutter coordinates: origin bottom-left, Y up.
            for(int k=0;k<p.length;k+=2){
                maxErr=Math.max(maxErr,Math.abs((q[k]-pad)-p[k]));
                maxErr=Math.max(maxErr,Math.abs(((float)g.heightMm-q[k+1]-pad)-p[k+1]));
            }
            previewArea+=area(p);exportArea+=area(q);
            for(int k=0,n=p.length/2;k<n;k++){int j=(k+1)%n;perimeter+=Math.hypot(p[2*j]-p[2*k],p[2*j+1]-p[2*k+1]);}
        }
        // SVG coordinates are written to 0.001 mm.
        check(maxErr<=.0011,String.format(Locale.US,"%s L%d: exported contour differs from preview by %.4f mm",tag,layer,maxErr));
        // Moving every vertex by at most d changes area by at most perimeter*d (d = 0.0005*sqrt(2) rounding).
        check(Math.abs(previewArea-exportArea)<=perimeter*.00071+1e-6,
            String.format(Locale.US,"%s L%d: area preview %.3f vs export %.3f mm²",tag,layer,previewArea,exportArea));
    }

    public static void main(String[] args) throws Exception {
        int w=96,h=116;int[] px=V3AllStyleExportMatrixTest.portrait(w,h);
        for(float size:new float[]{76.2f,266.7f})for(int style:STYLES){
            String tag=String.format(Locale.US,"%s @%.1fmm",VinylStyleSuite.NAMES[style],size);
            VinylStyleSuite.Config c=new VinylStyleSuite.Config();
            c.style=style;c.widthMm=size;c.detail=50;c.colorCount=4;c.minimumMm=.8f;c.draft=false;c.weightMm=.7f;
            StyleKit.Result generated=VinylStyleSuite.generate(px,w,h,c);
            SuiteCutStage stage=SuiteCutStage.prepare(generated,style,.8f,.5f);
            cases++;
            boolean changed=false;
            for(int i=0;i<generated.layers.size();i++)if(!sameLoops(generated.layers.get(i).loops,stage.cut.layers.get(i).loops))changed=true;
            if(changed)changedByConditioning++;
            // The preview shows stage.cut; export must reuse it exactly, with no regeneration.
            StyleKit.Result exported=SuiteCutStage.reusable(stage,stage.cut,style,.8f,.5f);
            check(exported==stage.cut,tag+": export did not reuse the cut-ready preview geometry");
            // Any different cut parameter, style, or shown geometry requires preparing again.
            check(SuiteCutStage.reusable(stage,stage.cut,style,1.0f,.5f)==null,tag+": minimum-cut change must invalidate");
            check(SuiteCutStage.reusable(stage,stage.cut,style,.8f,.6f)==null,tag+": bridge change must invalidate");
            check(SuiteCutStage.reusable(stage,stage.cut,style==0?1:0,.8f,.5f)==null,tag+": style change must invalidate");
            check(SuiteCutStage.reusable(stage,generated,style,.8f,.5f)==null||!changed,tag+": un-conditioned geometry must not be exported as cut-ready");
            check(SuiteCutStage.reusable(null,stage.cut,style,.8f,.5f)==null,tag+": missing stage must not be reusable");
            // After a cut-parameter change the export prepares again: from the ORIGINAL geometry,
            // never from the already-conditioned preview (no compounding repair).
            check(SuiteCutStage.sourceFor(stage,stage.cut)==generated,tag+": re-preparation must start from generated geometry");
            check(SuiteCutStage.sourceFor(stage,generated)==generated&&SuiteCutStage.sourceFor(null,stage.cut)==stage.cut,
                tag+": sourceFor must pass through unrelated geometry");
            for(int i=0;i<stage.cut.layers.size();i++){
                StyleKit.Layer l=stage.cut.layers.get(i);
                if(l.loops.isEmpty()||!l.lines.isEmpty())continue;
                compareSheet(tag,stage.cut,i,false);
                compareSheet(tag+" marked",stage.cut,i,true);
            }
        }
        // Cancellation: an interrupted preparation never yields a result.
        VinylStyleSuite.Config c=new VinylStyleSuite.Config();
        c.style=VinylStyleSuite.CONFETTI;c.widthMm=266.7f;c.detail=60;c.colorCount=4;c.minimumMm=.8f;c.draft=false;
        StyleKit.Result big=VinylStyleSuite.generate(px,w,h,c);
        AtomicReference<Object> outcome=new AtomicReference<>();
        Thread worker=new Thread(()->{
            Thread.currentThread().interrupt();
            try{outcome.set(SuiteCutStage.prepare(big,VinylStyleSuite.CONFETTI,.8f,.5f));}
            catch(CancellationException expected){outcome.set("cancelled");}
        });
        worker.start();worker.join(60_000);
        check("cancelled".equals(outcome.get()),"interrupted cut preparation produced "+outcome.get());

        System.out.printf(Locale.US,"conditioning changed geometry in %d of %d cases (exported unseen in build 556)%n",changedByConditioning,cases);
        if(!failures.isEmpty()){
            System.out.println("FAIL preview/export parity: "+failures.size()+" of "+checks);
            for(String f:failures)System.out.println("  - "+f);
            throw new AssertionError(failures.size()+" preview/export parity failures");
        }
        System.out.println("PASS preview/export parity: "+checks+" checks; cut-ready preview contours equal exported contours");
    }
}
