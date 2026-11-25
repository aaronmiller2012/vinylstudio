package com.halftonestudio;

import java.lang.reflect.Method;

/** Regression: applying a geometric Snip does not turn OFF local portrait tone
 * recovery in unaffected interior pixels. Physical cut masking remains strict. */
public final class V3SnipPortraitDetailTest {
    private static void ok(boolean b,String msg){if(!b)throw new AssertionError(msg);}
    private static int[] photo(int w,int h){
        int[] a=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float u=x/(float)w,v=y/(float)h;
            float dx=(u-.5f)/.33f,dy=(v-.51f)/.42f;
            int p=0xffeee8de;
            if(dx*dx+dy*dy<1){
                int lum=(int)(191 + 28*Math.sin(u*29)*Math.cos(v*16));
                int rr=Math.max(0,Math.min(255,lum+17));
                int gg=Math.max(0,Math.min(255,lum-18));
                int bb=Math.max(0,Math.min(255,lum-8));
                p=0xff000000|(rr<<16)|(gg<<8)|bb;
                if((v>.37&&v<.40&&(u<.43||u>.57))||(v>.62&&v<.65))p=0xff48392f;
                if(v<.21)p=0xff372c2b;
            }
            a[y*w+x]=p;
        }
        return a;
    }
    private static float tone(String engine,VinylEngine.Result r,int[] pixels,int w,int h,int layer,float x,float y)throws Exception{
        Class<?> c=Class.forName("com.halftonestudio."+engine);
        Method m=c.getDeclaredMethod("tone",VinylEngine.Result.class,int[].class,int.class,int.class,int.class,float.class,float.class);
        m.setAccessible(true);
        return (Float)m.invoke(null,r,pixels,w,h,layer,x,y);
    }
    private static VinylEngine.Settings setup(int style,boolean classic,boolean mask){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.registration=false;s.widthMm=140;s.marginMm=2.0f;s.minimumMm=.65f;s.bridgeMm=.45f;
        s.pattern=style;s.classicCmykLines=classic;s.opaqueLineBalance=false;
        s.lineHighlightRetention=.95f;s.linePresenceRecovery=.65f;
        s.masterSpacingMm=3.1f;
        if(mask){
            s.snips.add(new SnipMask.Operation(SnipMask.KEEP_INSIDE,
                .08f,.08f,.92f,.08f,.92f,.92f,.08f,.92f));
            s.snips.add(new SnipMask.Operation(SnipMask.REMOVE_INSIDE,
                .73f,.35f,.78f,.35f,.78f,.68f,.73f,.68f));
        }
        return s;
    }
    public static void main(String[] args)throws Exception{
        int w=122,h=144;int[] pixels=photo(w,h);
        int checked=0, boost=0;
        for(boolean classic:new boolean[]{false,true})for(int style:new int[]{VinylEngine.HATCH,VinylEngine.LINE}){
            VinylEngine.Settings plain=setup(style,classic,false);
            VinylEngine.Settings masked=setup(style,classic,true);
            String engine=classic?"ClassicLineArtEngine":"LineArtEngine";
            float height=plain.widthMm*h/(float)w;
            VinylEngine.Result r0=new VinylEngine.Result(plain,height,0,null);
            r0.photoSampler=new ScaleAwarePhotoSampler(pixels,w,h,plain.widthMm,height,plain.paper);
            VinylEngine.Result r1=new VinylEngine.Result(masked,height,0,null);
            r1.photoSampler=new ScaleAwarePhotoSampler(pixels,w,h,masked.widthMm,height,masked.paper);
            for(int li=0;li<4;li++)for(int iy=0;iy<7;iy++)for(int ix=0;ix<7;ix++){
                float x=plain.widthMm*(.27f+ix*.065f),y=height*(.26f+iy*.070f);
                float a=tone(engine,r0,pixels,w,h,li,x,y);
                float b=tone(engine,r1,pixels,w,h,li,x,y);
                ok(Math.abs(a-b)<.00001f,"Snip altered interior tone engine="+engine+" style="+style+" layer="+li+" x="+x+" y="+y+" old="+a+" new="+b);
                checked++;
                int p=VinylEngine.at(pixels,w,h,x,y,plain.widthMm,height);
                if(a>VinylEngine.coverage(p,plain,li)+.01f)boost++;
            }
            // Generated cut sheets must still obey the actual Snip, including its hole.
            VinylEngine.Result full=VinylEngine.generate(pixels,w,h,masked);
            for(int li=0;li<4;li++)for(VinylEngine.Shape sh:full.shapes.get(li)){
                if(sh.circle)ok(masked.snips.allowsCircle(sh.x,sh.y,sh.r,masked.widthMm,full.heightMm),"Escaped Snip circle");
                else ok(masked.snips.allowsPolygon(sh.xy,masked.widthMm,full.heightMm),"Escaped Snip polygon");
            }
            LineCutDiagnostics.Report check=LineCutDiagnostics.inspect(full);
            ok(check.complete&&check.selfIntersections==0&&check.subMinimumWidthSamples==0,"Unsafe Snip geometry");
        }
        ok(boost>40,"Fixture did not exercise highlight recovery boost="+boost);
        // A ribbon's two-sided photo-gradient probes must be restored in a Snip
        // interior, yet never borrow a pixel outside a keep boundary or hole.
        VinylEngine.Settings testSettings=setup(VinylEngine.HATCH,false,true);
        float testHeight=testSettings.widthMm*h/(float)w;
        ScaleAwarePhotoSampler probe=new ScaleAwarePhotoSampler(pixels,w,h,testSettings.widthMm,testHeight,testSettings.paper);
        ScaleAwarePhotoSampler.Coverage shade=pixel->VinylEngine.coverage(pixel,testSettings,3);
        int nonzero=0;
        for(int yi=0;yi<11;yi++)for(int xi=0;xi<11;xi++){
            float x=testSettings.widthMm*(.28f+xi*.037f),y=testHeight*(.27f+yi*.041f);
            float direct=probe.ribbonBias(x,y,.6f,.8f,.65f,.45f,shade);
            float masked=probe.ribbonBiasWithinSnip(x,y,.6f,.8f,.65f,.45f,shade,testSettings.snips);
            ok(Math.abs(direct-masked)<.00001f,"Interior masked ribbon-bias differs from original");
            if(Math.abs(direct)>.002f)nonzero++;
        }
        ok(nonzero>=2,"Bias fixture did not exercise facial contrast");
        float blocked=probe.ribbonBiasWithinSnip(testSettings.widthMm*.081f,testHeight*.40f,
            .6f,.8f,.65f,.45f,shade,testSettings.snips);
        ok(blocked==0f,"Image gradient crossed outer Snip boundary");
        float hole=probe.ribbonBiasWithinSnip(testSettings.widthMm*.725f,testHeight*.50f,
            .6f,.8f,.65f,.45f,shade,testSettings.snips);
        ok(hole==0f,"Image gradient crossed excluded Snip hole");
        System.out.println("PASS Snip tonal parity: "+checked+" interior samples, "+boost+" recoverable; "+nonzero+" ribbon biases; 4 geometry cut-safe configurations");
    }
}
