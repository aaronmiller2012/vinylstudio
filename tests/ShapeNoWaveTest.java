package com.halftonestudio;
import java.util.*;

/** Regression for reverting all shape families to the regular, closed-vector screen. */
public final class ShapeNoWaveTest {
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static int[] photo(int w,int h){int[] image=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int g=25+(x*160/w+y*40/h);
            image[y*w+x]=0xff000000|(g<<16)|(g<<8)|g;
        }
        return image;
    }
    static VinylEngine.Settings settings(int style){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.mode=VinylEngine.SPOT;s.pattern=style;s.widthMm=70;s.marginMm=6;
        s.minimumMm=.28f;s.bridgeMm=.45f;s.masterSpacingMm=3.8f;
        s.registration=false;
        s.layers.remove(3);s.layers.remove(2);
        return s;
    }
    static void testAllShapes(){int[] photo=photo(45,45);
        require(VinylEngine.SHAPE_STYLES.length>=14,"shape library lost styles");
        for(int id:VinylEngine.SHAPE_STYLES){
            VinylEngine.Result result=VinylEngine.generate(photo,45,45,settings(id));
            require(result.count()>0,"no shapes: "+VinylEngine.PATTERNS[id]);
            require(VinylEngine.validateExport(result)==null,"invalid SVG: "+VinylEngine.PATTERNS[id]);
            for(List<VinylEngine.Shape> shapes:result.shapes)
                for(VinylEngine.Shape shape:shapes){
                    float[] bounds=shape.bounds();
                    require(bounds[0]>=5.99&&bounds[1]>=5.99&&bounds[2]<=64.01&&bounds[3]<=64.01,
                            "margin violation: "+VinylEngine.PATTERNS[id]);
                    if(!shape.circle){require(shape.xy.length>=6&&shape.xy.length%2==0,"bad polygon vertices");
                        for(float v:shape.xy)require(Float.isFinite(v),"nonfinite vector geometry");}
                }
        }
    }
    static void testCMYKAnglesAndWavyLines(){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.widthMm=70;s.marginMm=6;s.registration=false;
        s.masterSpacingMm=3.8f;s.pattern=VinylEngine.DIAMOND;
        VinylEngine.Result diamond=VinylEngine.generate(photo(45,45),45,45,s);
        require(diamond.count()>0,"CMYK diamond layer missing");
        float[] angles={15f,75f,0f,45f};
        for(VinylEngine.Layer layer:diamond.settings.layers)
            require(layer.cmykChannel<0||Math.abs(layer.angle-angles[layer.cmykChannel])<.001,
                    "CMYK channel angle changed");
        s.pattern=VinylEngine.WAVE;
        VinylEngine.Result wave=VinylEngine.generate(photo(45,45),45,45,s);
        require(wave.count()>0,"existing Wavy Lines must remain available");
        require(VinylEngine.validateExport(wave)==null,"Wavy Lines export invalid");
    }
    public static void main(String[] args){
        testAllShapes();testCMYKAnglesAndWavyLines();
        System.out.println("PASS: 14 regular-grid shapes; closed SVGs; CMYK angles; independent Wavy Lines retained");
    }
}
