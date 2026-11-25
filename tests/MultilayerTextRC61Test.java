package com.halftonestudio;

/** RC61: perceptual swatches, readable whole words, Snip cut safety, 2..8 layers. */
public final class MultilayerTextRC61Test {
    private static void ok(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static int[] colorful(int w,int h) {
        int[] p=new int[w*h];
        int[] swatches={0xffe9d3b6,0xff204870,0xffb45539,0xff4f7044,0xff3b2029,0xffb89c35,0xff6a4388,0xff269ba3};
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int i=Math.min(7, (x*4/w)+(y*2/h)*4);
            // Keep each region large enough to contain numerous glyphs.
            p[y*w+x]=swatches[i];
        }
        return p;
    }
    private static final int[] PALETTE={0xfff4dbc0,0xff204870,0xffb45539,0xff4f7044,0xff3b2029,0xffb89c35,0xff6a4388,0xff269ba3};
    private static TextHalftoneEngine.Options opt(int count,TextHalftoneEngine.Mode mode){
        TextHalftoneEngine.Options o=new TextHalftoneEngine.Options();
        o.widthMm=160;o.cellMm=3.5f;o.minStrokeMm=.4f;o.autoLevels=false;
        o.silhouetteUnderlay=true;o.colorLayers=count;
        o.palette=new int[count];System.arraycopy(PALETTE,0,o.palette,0,count);
        o.mode=mode;o.text="JANE SMITH VINYL STUDIO";
        return o;
    }
    private static int contours(StyleKit.Result result){int n=0;for(StyleKit.Layer l:result.layers)n+=l.loops.size();return n;}
    public static void main(String[] args) {
        int w=240,h=240;int[] p=colorful(w,h);
        for(int count=2;count<=8;count++){
            for(TextHalftoneEngine.Mode mode:TextHalftoneEngine.Mode.values()){
                TextHalftoneEngine.Options o=opt(count,mode);
                StyleKit.Result r=TextHalftoneEngine.generate(p,w,h,o);
                ok(r.layers.size()>=2&&r.layers.size()<=count,"Must include solid base and text; <= requested total layers");
                ok(r.layers.get(0).name.equals("snipped-background-1"),"Layer 1 remains solid underlay");
                for(StyleKit.Layer l:r.layers)ok(l.color==o.palette[NewStyleColorSlots.slot(VinylStyleSuite.TEXT,l.name,0)],"custom swatch identity lost");
                ok(contours(r)>3,"Must contain cuttable vector artwork");
                ok(!r.summary.contains("no full words fit"),"Words failed to pack");
                if(mode==TextHalftoneEngine.Mode.WORDS)ok(r.summary.contains("whole words"),"Only complete words allowed");
                if(count==8&&mode==TextHalftoneEngine.Mode.RAMP){
                    System.out.println("COLOR SEPARATION: "+r.summary);
                    ok(r.layers.size()>=5,"Chromatic source should use at least four of seven detail colors");
                }
            }
        }
        // Layer 1 retains an interior Snip hole; upper details may not enter it.
        boolean[] mask=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double dx=(x-w/2.0)/(w*.43),dy=(y-h/2.0)/(h*.43);
            double hole=Math.hypot(x-w/2.0,y-h/2.0);
            mask[y*w+x]=dx*dx+dy*dy<1&&hole>25;
        }
        TextHalftoneEngine.Options o=opt(6,TextHalftoneEngine.Mode.WORDS);o.allowed=mask;
        StyleKit.Result clipped=TextHalftoneEngine.generate(p,w,h,o);
        ok(clipped.layers.get(0).loops.size()>=2,"Snip hole lost");
        for(int k=1;k<clipped.layers.size();k++)for(float[] loop:clipped.layers.get(k).loops)
            for(int i=0;i<loop.length;i+=2){
                float px=loop[i]/(o.widthMm/w),py=loop[i+1]/(o.widthMm/w);
                int x=Math.min(w-1,Math.max(0,(int)px)),y=Math.min(h-1,Math.max(0,(int)py));
                ok(mask[y*w+x],"Glyph crosses removed Snip region");
            }
        System.out.println("PASS RC61 palette-aware 2–8 vinyl sheets, whole words, filled Snip with hole, cut-safe vector paths");
    }
}
