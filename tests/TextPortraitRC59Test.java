package com.halftonestudio;

public final class TextPortraitRC59Test {
    private static void ok(boolean yes,String msg){if(!yes)throw new AssertionError(msg);}
    private static int[] face(int w,int h){
        int[] image=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float u=(x-w*.5f)/(w*.32f),v=(y-h*.44f)/(h*.43f);
            float radial=u*u+v*v;
            int gray=radial<1?215:250;
            if(radial<1 && Math.abs(v+.22f)<.06f && (Math.abs(u-.33f)<.16f||Math.abs(u+.33f)<.16f))gray=25;
            if(radial<1 && Math.abs(v-.35f)<.045f && Math.abs(u)<.37f)gray=38;
            if(radial<1 && Math.abs(u)<.08f && v>-.1f && v<.3f)gray=125;
            image[y*w+x]=0xff000000|(gray<<16)|(gray<<8)|gray;
        }
        return image;
    }
    private static int glyphs(StyleKit.Result r){
        int total=0;
        for(StyleKit.Layer layer:r.layers)total+=layer.loops.size();
        return total;
    }
    private static float stepInSummary(StyleKit.Result r){
        String s=r.summary;
        int a=s.indexOf("cell ")+5,b=s.indexOf(" mm",a);
        return Float.parseFloat(s.substring(a,b));
    }
    public static void main(String[] args){
        // Edge matching must be based on the actual cut glyph, not uniform luminance.
        float[] patch=new float[35];long m=PixelFont5x7.mask('/');
        for(int i=0;i<35;i++)patch[i]=((m>>>i)&1)!=0?1:0;
        ok(TextHalftoneEngine.glyphForPatch("/\\#",patch,Long.bitCount(m)/35f)=='/',"directional ASCII match");
        int[] photo=face(240,300);
        VinylStyleSuite.Config c=new VinylStyleSuite.Config();
        c.style=VinylStyleSuite.TEXT;c.widthMm=152.4f;c.detail=20;
        StyleKit.Result low=VinylStyleSuite.generate(photo,240,300,c);
        c.detail=95;
        StyleKit.Result high=VinylStyleSuite.generate(photo,240,300,c);
        ok(stepInSummary(high)<stepInSummary(low),"Detail decreases cell size");
        ok(glyphs(high)>glyphs(low),"Detail actually creates more cut geometry");
        ok(!high.layers.isEmpty()&&high.layers.size()<=8,"valid physical layers");
        c.variant=1;c.words="HAPPY DAYS";
        StyleKit.Result words=VinylStyleSuite.generate(photo,240,300,c);
        ok(!words.layers.isEmpty(),"word portrait not empty");
        ok(words.summary.startsWith("Words multilayer"),"word summary");
        float requiredWidth=c.minimumMm;
        ok(stepInSummary(words)*.92f/7f>=requiredWidth-.015f,"physical minimum stroke enforced");
        c.words="WORLD 🌎";
        ok(!VinylStyleSuite.generate(photo,240,300,c).layers.isEmpty(),"unsupported Unicode handled");
        // Cut masks must stop outside Snip even if a character overlaps the boundary.
        boolean[] mask=new boolean[photo.length];
        for(int y=40;y<260;y++)for(int x=40;x<200;x++)mask[y*240+x]=true;
        c.allowed=mask;
        StyleKit.Result clipped=VinylStyleSuite.generate(photo,240,300,c);
        for(StyleKit.Layer layer:clipped.layers)for(float[] loop:layer.loops)
            for(int i=0;i<loop.length;i+=2) {
                ok(loop[i]>=24f && loop[i]<=130f,"Snip x bounds");
                ok(loop[i+1]>=20f && loop[i+1]<=174f,"Snip y bounds");
            }
        System.out.println("PASS RC59 cut-safe ASCII/words detail, edge-aware glyphs, Unicode fallback, Snip safety");
        System.out.println("Low: "+low.summary+"; high: "+high.summary+"; words: "+words.summary);
    }
}
