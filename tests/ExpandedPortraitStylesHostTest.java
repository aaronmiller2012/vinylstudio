package com.halftonestudio;

/** Deterministic, Android-free contract for six approved engines and nine RC142 tombstones. */
public final class ExpandedPortraitStylesHostTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static int slots(int style){
        switch(style){
            case ExpandedPortraitStylesEngine.MARQUETRY:return 6;
            case ExpandedPortraitStylesEngine.FINGERPRINT:return 1;
            case ExpandedPortraitStylesEngine.REDUCTION_WOODCUT:return 6;
            case ExpandedPortraitStylesEngine.WOVEN_RIBBON:return 4;
            case ExpandedPortraitStylesEngine.WOODGRAIN:return 1;
            case ExpandedPortraitStylesEngine.TORN_COLLAGE:return 6;
            default:throw new IllegalArgumentException();
        }
    }
    private static long signature(StyleKit.Result result){
        long h=0xcbf29ce484222325L;
        for(StyleKit.Layer layer:result.layers){
            h=(h^layer.name.hashCode())*0x100000001b3L;h=(h^layer.color)*0x100000001b3L;
            for(float[] loop:layer.loops)for(float v:loop)h=(h^Float.floatToIntBits(v))*0x100000001b3L;
            for(float[] line:layer.lines)for(float v:line)h=(h^Float.floatToIntBits(v))*0x100000001b3L;
        }
        return h;
    }
    private static StyleKit.Layer named(StyleKit.Result result,String name){
        for(StyleKit.Layer layer:result.layers)if(name.equals(layer.name))return layer;
        throw new AssertionError("Missing layer "+name);
    }
    public static void main(String[] args){
        final int w=144,h=112;int[] image=new int[w*h];boolean[] allowed=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float u=(x-w*.5f)/(w*.46f),v=(y-h*.48f)/(h*.46f);float radial=u*u+v*v;
            int light=Math.max(15,Math.min(245,Math.round(222-150*Math.max(0,1-radial))));
            if(Math.abs(v+.20f)<.055f&&(Math.abs(u-.31f)<.16f||Math.abs(u+.31f)<.16f))light=25;
            if(Math.abs(v-.33f)<.05f&&Math.abs(u)<.35f)light=38;
            int r=Math.min(255,light+Math.round(30*Math.max(0,u)));
            int g=Math.min(255,light+Math.round(20*Math.max(0,-v)));
            int b=Math.max(0,light-Math.round(18*Math.max(0,-u)));
            image[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;allowed[y*w+x]=radial<1f;
        }
        int[] palette={0xff16171b,0xfff0dfc5,0xffc89068,0xff86495f,0xff46647a,0xffd9b736,0xff6c7c4b,0xffece8df};
        int activeCount=0,retiredCount=0;
        for(int style=0;style<ExpandedPortraitStylesEngine.NAMES.length;style++){
            ExpandedPortraitStylesEngine.Options o=new ExpandedPortraitStylesEngine.Options();
            o.style=style;o.detail=42;o.widthMm=152.4f;o.minimumMm=.254f;o.gapMm=.24f;o.weightMm=.70f;o.allowed=allowed;
            if(!ExpandedPortraitStylesEngine.isActive(style)){
                retiredCount++;check(ExpandedPortraitStylesEngine.variantCount(style)==0,"Retired style exposes variants: "+style);
                boolean rejected=false;
                try{ExpandedPortraitStylesEngine.generate(image,w,h,o);}catch(IllegalArgumentException expected){rejected=true;}
                check(rejected,"Retired expanded style still renders: "+style);continue;
            }
            activeCount++;
            o.colorCount=slots(style);o.palette=java.util.Arrays.copyOf(palette,o.colorCount);
            StyleKit.Result a=ExpandedPortraitStylesEngine.generate(image,w,h,o);
            StyleKit.Result bResult=ExpandedPortraitStylesEngine.generate(image,w,h,o);
            check(!a.layers.isEmpty(),ExpandedPortraitStylesEngine.NAMES[style]+" empty");
            check(a.layers.size()<=8,ExpandedPortraitStylesEngine.NAMES[style]+" too many layers");
            check(a.totalPoints()>5,ExpandedPortraitStylesEngine.NAMES[style]+" insufficient geometry");
            int physical=slots(style);
            for(int i=0;i<a.layers.size();i++){
                StyleKit.Layer layer=a.layers.get(i);int end=layer.name.length(),start=end;
                while(start>0&&Character.isDigit(layer.name.charAt(start-1)))start--;
                int slot=start<end?Integer.parseInt(layer.name.substring(start))-1:0;
                check(slot>=0&&slot<physical,ExpandedPortraitStylesEngine.NAMES[style]+" invalid physical slot");
                check(layer.color==palette[slot],ExpandedPortraitStylesEngine.NAMES[style]+" wrong swatch for "+layer.name);
            }
            check(signature(a)==signature(bResult),ExpandedPortraitStylesEngine.NAMES[style]+" not deterministic");
            System.out.println("PASS "+ExpandedPortraitStylesEngine.NAMES[style]+" · "+a.summary);
        }
        check(activeCount==6&&retiredCount==9,"RC142 active/retired style count changed");
        ExpandedPortraitStylesEngine.Options automatic=new ExpandedPortraitStylesEngine.Options();
        automatic.detail=42;automatic.colorCount=6;automatic.widthMm=152.4f;
        automatic.minimumMm=.254f;automatic.gapMm=.24f;automatic.weightMm=.70f;automatic.allowed=allowed;
        automatic.style=ExpandedPortraitStylesEngine.REDUCTION_WOODCUT;
        StyleKit.Result autoReduction=ExpandedPortraitStylesEngine.generate(image,w,h,automatic);
        int base=StyleKit.luma255(named(autoReduction,"reduction-woodcut-1").color);
        for(StyleKit.Layer layer:autoReduction.layers)check(base>=StyleKit.luma255(layer.color),
            "Reduction woodcut base must be lightest automatic swatch");
        boolean cancelled=false;Thread.currentThread().interrupt();
        try{ExpandedPortraitStylesEngine.generate(image,w,h,automatic);}
        catch(java.util.concurrent.CancellationException expected){cancelled=true;}
        finally{Thread.interrupted();}
        check(cancelled,"Interrupted render did not cancel cooperatively");
        System.out.println("PASS automatic palette semantics, cancellation, and retired-style rejection");
        System.out.println("PASS RC142 six approved portrait styles; nine stable tombstones");
    }
}
