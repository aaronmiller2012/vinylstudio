package com.halftonestudio;
import java.util.Arrays;

/** Source-only 11-inch visual-tone and vinyl geometry checks. No private photo. */
public final class OpaqueLinePortraitRC102Test {
    static void yes(boolean x,String why){if(!x)throw new AssertionError(why);}
    static int[] block(int w,int h,int color){int[] p=new int[w*h];Arrays.fill(p,color);return p;}
    static double area(VinylEngine.Result r){
        double total=0;
        for(java.util.List<VinylEngine.Shape> shapes:r.shapes)for(VinylEngine.Shape s:shapes){
            if(s.circle){total+=Math.PI*s.r*s.r;continue;}
            float[] pts=s.xy;double twice=0;
            for(int k=0;k<pts.length;k+=2){int j=(k+2)%pts.length;
                twice+=(double)pts[k]*pts[j+1]-(double)pts[j]*pts[k+1];}
            total+=Math.abs(twice)*.5;
        }
        return total;
    }
    static VinylEngine.Settings selected(){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.marginMm=1.5f;s.registration=false;
        SmartHalftonePresets.apply(s,VinylEngine.LINE,VinylSizing.BALANCED,11f,11f);
        s.portraitBackgroundGuard=0f;
        return s;
    }
    public static void main(String[] args){
        VinylEngine.Settings s=selected();
        yes(s.opaqueLineBalance && OpaqueCmykLineBudget.eligible(s,VinylEngine.LINE),"modern CMYK line selection did not enable compensation");
        yes(!OpaqueCmykLineBudget.eligible(s,VinylEngine.RINGS),"ring families must stay unchanged");
        s.classicCmykLines=true;
        yes(!OpaqueCmykLineBudget.eligible(s,VinylEngine.LINE),"Classic must stay unmodified");
        s.classicCmykLines=false;
        s.paper=0xff151515;
        yes(!OpaqueCmykLineBudget.eligible(s,VinylEngine.LINE),"dark substrate must use existing stack logic");
        s.paper=0xfffaf9f6;
        float[] sample=OpaqueCmykLineBudget.prepare(new int[]{0xffbf9280,0xff352920,0xffffffff,0x00ffffff},4,1,s);
        yes(sample[0]<.93f,"skin-width normalization not active");
        yes(sample[1]>sample[0],"dark detail should keep more width than skin");
        yes(sample[2]==1f && sample[3]==1f,"blank or alpha must not become vinyl");
        // Dark facial feature against lighter skin must increase local ink
        // while adjacent skin gets negative-space contrast.
        int tw=64,th=64;
        int[] eyes=block(tw,th,0xffbf9280);
        for(int y=31;y<=33;y++)for(int x=31;x<=33;x++)eyes[y*tw+x]=0xff30231f;
        float[] detail=OpaqueCmykLineBudget.localDetail(eyes,tw,th,s);
        yes(detail[32*tw+32]>1.10f,"dark eye lost local feature contrast");
        yes(detail[32*tw+34]<1.0f,"skin next to dark eye lost negative space");
        int w=132,h=132;
        int[] mid=block(w,h,0xffbf9280),dark=block(w,h,0xff352920);
        VinylEngine.Result midNew=VinylEngine.generate(mid,w,h,s);
        VinylEngine.Result darkNew=VinylEngine.generate(dark,w,h,s);
        VinylEngine.Settings legacy=s.copy();legacy.opaqueLineBalance=false;
        VinylEngine.Result midOld=VinylEngine.generate(mid,w,h,legacy);
        double before=area(midOld),after=area(midNew),darkArea=area(darkNew);
        yes(after<before*.87,"opaque skin is still overwhelmed: "+after+" vs "+before);
        yes(darkArea>after*1.12,"dark facial structure lost relative to skin: "+darkArea+" vs "+after);
        yes(midNew.opaqueLineScales==null,"tone buffer retained in Result");
        yes(midNew.opaqueLineDetails==null,"detail buffer retained in Result");
        for(int i=0;i<4;i++){
            // A uniform warm field may physically need no cyan; do not invent unsupported sheet cuts.
            yes(!darkNew.shapes.get(i).isEmpty(),"dark CMYK channel "+i+" missing");
        }
        int[] synthetic=LineCoverageRC101Test.portrait(140,160);
        VinylEngine.Settings snip=s.copy();
        snip.snips.add(new SnipMask.Operation(SnipMask.REMOVE_INSIDE,
            .46f,.30f,.54f,.30f,.54f,.60f,.46f,.60f));
        VinylEngine.Result mixed=VinylEngine.generate(synthetic,140,160,snip);
        for(int i=0;i<4;i++)yes(!mixed.shapes.get(i).isEmpty(),"four-color portrait sheet missing "+i);
        LineCutDiagnostics.Report mixedSafety=LineCutDiagnostics.inspect(mixed);
        yes(mixedSafety.complete&&mixedSafety.selfIntersections==0&&mixedSafety.subMinimumWidthSamples==0,
            "11-inch portrait snip geometry is unsafe");
        yes(VinylEngine.validateExport(mixed)==null,"mixed portrait cannot export");
        yes(VinylEngine.validateExport(midNew)==null,"midtones cannot export");
        yes(VinylEngine.validateExport(darkNew)==null,"dark detail cannot export");
        LineCutDiagnostics.Report safe=LineCutDiagnostics.inspect(midNew);
        yes(safe.complete && safe.selfIntersections==0 && safe.subMinimumWidthSamples==0,"unsafe skin screen");
        System.out.println("PASS RC102: 11in opaque skin area "+Math.round(before)+" -> "+Math.round(after)+
            ", dark detail "+Math.round(darkArea)+", four sheets and cut-safe SVG");
    }
}
