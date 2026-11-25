package com.halftonestudio;
import java.util.ArrayList;

/** Conversion of actual production SVG emitters, not only hand-authored XML. */
public final class RC172ProductionSvgInteropTest {
    private static void ok(boolean c,String msg){if(!c)throw new AssertionError(msg);}
    public static void main(String[] args)throws Exception {
        StyleKit.Result styles=new StyleKit.Result(140f,190f);
        StyleKit.Layer layer=new StyleKit.Layer("Filled with a hole",0xff991122);
        layer.loops.add(new float[]{2,2,72,2,72,72,2,72});
        layer.loops.add(new float[]{22,22,22,42,42,42,42,22});
        layer.lines.add(new float[]{10,90,25,105,40,90,65,115});
        styles.layers.add(layer);
        String original=StyleKit.toSvg(styles,false);
        SvgDxfR13Exporter.Result dx=SvgDxfR13Exporter.convert(original);
        ok(dx.closedContours==2&&dx.openPaths==1&&dx.entities==3,"StyleKit compound path / pen paths lost");
        ok(Math.abs(dx.widthMm-140)<.001&&Math.abs(dx.heightMm-190)<.001,"StyleKit physical artboard lost");
        String marked=NewStyleExport.layerSvg(styles,0,false,0,6f,1f);
        ok(SvgDxfR13Exporter.convert(marked).entities==3,"New style export failed");
        VinylEngine.Settings settings=VinylEngine.Settings.defaults();
        settings.widthMm=100f;settings.registration=false;settings.layers.clear();
        settings.layers.add(new VinylEngine.Layer("One",0xff121212));
        VinylEngine.Result result=new VinylEngine.Result(settings,150f,8f,new float[0][]);
        result.shapes.add(new ArrayList<>());
        result.shapes.get(0).add(VinylEngine.Shape.circle(23,25,4));
        result.shapes.get(0).add(VinylEngine.Shape.compound(new float[][]{
            new float[]{1,1,60,1,60,65,1,65},
            new float[]{20,20,20,40,40,40,40,20}
        }));
        String generated=VinylEngine.svg(result,0);
        SvgDxfR13Exporter.Result rd=SvgDxfR13Exporter.convert(generated);
        ok(rd.entities==3&&rd.closedContours==3,"VinylEngine filled shapes/compound hole lost");
        ok(Math.abs(rd.widthMm-116)<.001&&Math.abs(rd.heightMm-166)<.001,"VinylEngine padded board lost");
        System.out.println("PASS RC172 real StyleKit + NewStyleExport + VinylEngine SVG interoperability");
    }
}
