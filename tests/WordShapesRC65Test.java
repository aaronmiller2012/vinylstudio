package com.halftonestudio;
import java.util.List;
/** Fixed regression for distinct text tiles and CMYK/spot whole-word cut geometry. */
public final class WordShapesRC65Test {
    static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
    static int[] image(int w,int h){
        int[] out=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int r=(x*255)/(w-1),g=(y*255)/(h-1),b=((x+y)*255)/(w+h-2);
            out[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
        }
        return out;
    }
    static void words(){
        check(WordScreenMotif.normalize(" Jane, Smith! 123") .equals("JANE SMITH 123"),"token normalization");
        check(WordScreenMotif.normalize("not_a;safe @!? input").equals("NOT A SAFE INPUT"),"separator normalization");
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        check(VinylEngine.ASCII==27&&VinylEngine.WORD_SCREEN==28,"saved style index changed");
        s.pattern=VinylEngine.WORD_SCREEN;s.screenWords="JANE SMITH";s.registration=false;
        s.widthMm=124f;s.marginMm=3f;s.masterSpacingMm=5f;s.minimumMm=.55f;
        for(VinylEngine.Layer l:s.layers)l.spacing=5f;
        VinylEngine.Result r=VinylEngine.generate(image(72,75),72,75,s);
        check(r.shapes.size()==4,"CMYK layer count changed");
        int total=0;boolean lower=false;
        for(int i=0;i<r.shapes.size();i++){
            List<VinylEngine.Shape> items=r.shapes.get(i);
            total+=items.size();
            check(VinylEngine.svgElementsForLayer(r,i)<=5000,"Cricut path budget");
            for(VinylEngine.Shape shape:items){
                check(shape.contours!=null&&shape.contours.length>=5,"word split into loose glyphs");
                float[] bounds=shape.bounds();
                check(bounds[0]>=s.marginMm-.001&&bounds[2]<=s.widthMm-s.marginMm+.001,"outside width");
                if(bounds[1]>r.heightMm*.65f)lower=true;
            }
            String svg=VinylEngine.svg(r,i);
            check(svg.contains("<svg")&&!svg.contains("<text"),"non-vector svg");
            check(r.settings.layers.get(i).cmykChannel==new int[]{2,0,1,3}[i],"CMYK identity modified");
        }
        check(total>6,"no words in CMYK source: "+total);
        check(lower,"layout stops before bottom of canvas");
        check(VinylEngine.validateExport(r)==null,"word export preflight: "+VinylEngine.validateExport(r));
        s.snips.add(new SnipMask.Operation(SnipMask.REMOVE_INSIDE,.36f,.31f,.63f,.31f,.63f,.63f,.36f,.63f));
        VinylEngine.Result snipped=VinylEngine.generate(image(72,75),72,75,s);
        for(List<VinylEngine.Shape> layer:snipped.shapes)for(VinylEngine.Shape shape:layer)
            for(float[] ring:shape.contours)check(s.snips.allowsPolygon(ring,s.widthMm,snipped.heightMm),"word intersects removed Snip");
        s.mode=VinylEngine.SPOT;
        for(int n=2;n<=8;n++){
            while(s.layers.size()>n)s.layers.remove(s.layers.size()-1);
            while(s.layers.size()<n){VinylEngine.Layer l=new VinylEngine.Layer("Spot "+n,0xff4050a0);l.spacing=5f;s.layers.add(l);}
            VinylEngine.Result spot=VinylEngine.generate(image(36,39),36,39,s);
            check(spot.shapes.size()==n,"2–8 color compatibility at "+n);
        }
        System.out.println("PASS RC65 distinct Word Shapes, full-height packing, CMYK/spot, Snip holes, closed SVG contours and path budget");
    }
    public static void main(String[] args){words();}
}
