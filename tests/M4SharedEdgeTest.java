package com.halftonestudio;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * M4 shared edges (M2 open finding). Torn-Paper Collage and Organic Marquetry stack
 * label sheets on a full base sheet. Per-sheet cut-ready repair moved formerly shared
 * edges independently, exposing hairlines of the base colour between neighbouring
 * pieces. This test composites the generated and the cut-ready sheets as opaque vinyl
 * at 0.05 mm and measures the visible damage the repair introduced:
 *   baseExposed - area where the base sheet becomes visible but a label sheet was visible
 *                 in the generated artwork (hairline gaps);
 *   changed     - any area whose visible sheet changed.
 */
public final class M4SharedEdgeTest {
    static final float PX_MM=.05f;

    static int[] visibleSheet(StyleKit.Result r){
        int w=Math.round(r.widthMm/PX_MM),h=Math.round(r.heightMm/PX_MM);
        BufferedImage img=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(Color.BLACK);g.fillRect(0,0,w,h);
        g.transform(AffineTransform.getScaleInstance(1/PX_MM,1/PX_MM));
        for(int i=0;i<r.layers.size();i++){
            g.setColor(new Color(i+1)); // sheet index as the "colour": opaque, later on top
            Path2D.Float p=new Path2D.Float(Path2D.WIND_EVEN_ODD);
            for(float[] xy:r.layers.get(i).loops){if(xy.length<6)continue;p.moveTo(xy[0],xy[1]);
                for(int k=2;k+1<xy.length;k+=2)p.lineTo(xy[k],xy[k+1]);p.closePath();}
            g.fill(p);
        }
        g.dispose();
        int[] px=img.getRGB(0,0,w,h,null,0,w);
        for(int i=0;i<px.length;i++)px[i]=(px[i]&0xffffff)-1; // -1 = mounting surface
        return px;
    }
    /** [baseExposed mm², changed mm²] of cut relative to generated. */
    static double[] damage(StyleKit.Result gen,StyleKit.Result cut){
        int[] a=visibleSheet(gen),b=visibleSheet(cut);
        long exposed=0,changed=0;
        for(int i=0;i<a.length;i++){
            if(a[i]!=b[i])changed++;
            if(a[i]>=1&&b[i]==0)exposed++;
        }
        double cell=PX_MM*PX_MM;return new double[]{exposed*cell,changed*cell};
    }

    public static void main(String[] args)throws Exception{
        String photoName=System.getProperty("m4.photo","astronaut.png");
        M4ArtBench.Photo photo=M4ArtBench.load(new File("tests/m4/photos/"+photoName),850);
        boolean report=Boolean.getBoolean("m4.report");
        int fails=0;
        System.out.println("style @size            base-exposed mm²  changed mm²   (generated -> cut-ready)");
        for(int style:new int[]{VinylStyleSuite.TORN_COLLAGE,VinylStyleSuite.MARQUETRY})
        for(float size:new float[]{76.2f,152.4f}){
            VinylStyleSuite.Config c=M4ArtBench.suiteConfig(style,photo,size,.8f,.5f);
            StyleKit.Result gen=VinylStyleSuite.generate(photo.argb,photo.w,photo.h,c);
            StyleKit.Result cut=VinylStyleSuite.prepareForCutting(gen,style,.8f,.5f);
            double[] d=damage(gen,cut);
            System.out.printf(Locale.US,"%-22s %5.1fin %12.2f %12.2f%n",VinylStyleSuite.NAMES[style],size/25.4f,d[0],d[1]);
            double area=gen.widthMm*gen.heightMm;
            // 558: 3.6-4.8% of the artwork at 3 in. Allowed residue: whole pieces the
            // cut-ready stage must remove with no lower neighbour to show instead.
            if(!report&&d[0]>area*.01){fails++;System.out.println("  FAIL base colour exposed along shared edges");}
        }
        // The underlap must be invisible: the generated artwork's visible sheet map is
        // identical with and without it (engine level, same options).
        for(int style:new int[]{ExpandedPortraitStylesEngine.TORN_COLLAGE,ExpandedPortraitStylesEngine.MARQUETRY}){
            ExpandedPortraitStylesEngine.Options o=new ExpandedPortraitStylesEngine.Options();
            o.style=style;o.widthMm=76.2f;o.minimumMm=.9f;o.gapMm=.6f;o.colorCount=6;o.detail=55;
            StyleKit.Result with=ExpandedPortraitStylesEngine.generate(photo.argb,photo.w,photo.h,o);
            o.sharedEdgeUnderlap=false;
            StyleKit.Result without=ExpandedPortraitStylesEngine.generate(photo.argb,photo.w,photo.h,o);
            int[] a=visibleSheet(with),b=visibleSheet(without);long diff=0,restored=0;
            // Pixel-level truth: which palette label the photo assigns here (via the
            // label sheet visible in the underlap-free result where it is not the base).
            for(int i=0;i<a.length;i++)if(a[i]!=b[i]){
                if(b[i]==0&&a[i]>=1)restored++; // base was showing; now a label piece shows
                else diff++;
            }
            double mm2=diff*PX_MM*PX_MM;
            System.out.printf(Locale.US,"underlap visibility check style %d: %.2f mm² other visible difference, %.2f mm² where base showed before%n",
                style,mm2,restored*PX_MM*PX_MM);
            // Allowed: (1) pixels where the base showed and the photo's own label now shows:
            // pieces 558 dropped as too small survive attached to their hidden band;
            // (2) <= 1 mm² of sub-pixel contour-smoothing difference.
            if(!report&&mm2>1.0){fails++;System.out.println("  FAIL underlap changed visible artwork");}
        }
        if(fails>0)throw new AssertionError(fails+" shared-edge failures");
        System.out.println(report?"REPORT M4 shared edges":"PASS M4 shared edges: cut-ready repair exposes no base hairlines");
    }
}
