package com.halftonestudio;
import java.util.Locale;

/** Physical frame/parity matrix over live specialty registry; diagnostics can
 * still report unsafe detail and are not physical cutter qualification. */
public final class V3PrecisionSizeMatrixTest {
    public static void main(String[] args)throws Exception{
        int[] photo=V3AllStyleExportMatrixTest.portrait(60,60);
        int styles=0,sheets=0;
        for(float size:new float[]{76.2f,279.4f}){
            for(int id=0;id<VinylStyleSuite.NAMES.length;id++){
                if(VinylStyleSuite.isRetiredStyleId(id)||!NewStyleVariantPolicy.selectable(id))continue;
                VinylStyleSuite.Config c=new VinylStyleSuite.Config();c.style=id;c.widthMm=size;c.detail=25;c.colorCount=4;c.minimumMm=.254f;c.draft=false;c.weightMm=.7f;
                StyleKit.Result r=VinylStyleSuite.generate(photo,60,60,c);
                if(r==null||r.layers.isEmpty()||Math.abs(r.widthMm-size)>.01||Math.abs(r.heightMm-size)>.01)throw new AssertionError("Physical dimensions: "+id+" at "+size);
                int count=0;
                for(int i=0;i<r.layers.size();i++){
                    StyleKit.Layer l=r.layers.get(i);if(l.loops.isEmpty()&&l.lines.isEmpty())continue;
                    String svg=NewStyleExport.layerSvg(r,i,false,0,3f,2f,null,.254f);
                    SvgDxfR13Exporter.SafetyGeometry g=SvgDxfR13Exporter.safetyGeometry(svg);
                    SvgDxfR13Exporter.Result d=SvgDxfR13Exporter.convert(svg);
                    if(Math.abs(g.widthMm-size)>.002||Math.abs(g.heightMm-size)>.002||Math.abs(d.widthMm-g.widthMm)>.002||Math.abs(d.heightMm-g.heightMm)>.002||d.closedContours!=g.closedLoops.size()+g.circles.size()||d.openPaths!=g.openPaths)throw new AssertionError("SVG/DXF physical parity: "+id+" at "+size+" mm; artboard "+g.widthMm+" x "+g.heightMm);
                    count++;sheets++;
                }
                if(count==0)throw new AssertionError("Empty style "+id+" at "+size);
                styles++;
                System.out.printf(Locale.US,"PASS %.1f mm style %d %s sheets=%d%n",size,id,VinylStyleSuite.NAMES[id],count);
            }
        }
        System.out.printf(Locale.US,"PASS 552 physical size matrix: %d style-size cases, %d sheets%n",styles,sheets);
    }
}
