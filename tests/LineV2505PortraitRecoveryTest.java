package com.halftonestudio;
import java.util.*;

/** Host-only safety and usefulness gate. Does NOT include the user's private photo. */
public final class LineV2505PortraitRecoveryTest {
    static void ok(boolean q,String m){if(!q)throw new AssertionError(m);}
    static int[] photo(int w,int h){
        int[] a=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float u=x/(float)w,v=y/(float)h;
            // Two warm walls and smooth light skin, plus detailed dark eyes/hair.
            int c;
            if((u-.5f)*(u-.5f)/(.34f*.34f)+(v-.52f)*(v-.52f)/(.42f*.42f)<1){
                c=0xffbf95a0;
                if(v<.23f || (v>.36f&&v<.40f&&(u<.46f||u>.57f)))c=0xff44332c;
                if(v>.63f)c=0xff69453a;
            }else c=u>.75f?0xffa7985d:0xffd8ddce;
            a[y*w+x]=c;
        }
        return a;
    }
    static VinylEngine.Settings settings(int style){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        ClassicCmykLines.useModern(s);s.widthMm=82f;s.marginMm=2;s.minimumMm=.76f;s.bridgeMm=.5f;
        s.registration=false;s.pattern=style;s.masterSpacingMm=2.69f;
        for(VinylEngine.Layer l:s.layers)l.spacing=s.masterSpacingMm;
        return s;
    }
    public static void main(String[] args){
        int w=144,h=180;int[] original=photo(w,h);
        int[] white=new int[w*h],transparent=new int[w*h],paper=new int[w*h];
        Arrays.fill(white,0xffffffff);Arrays.fill(paper,0xfffaf9f6);
        int[] styles={VinylEngine.LINE,VinylEngine.STRAIGHT,VinylEngine.CONTOUR,
                     VinylEngine.WAVE,VinylEngine.ZIGZAG};
        long totalPromoted=0,totalBackground=0;
        for(int style:styles){
            VinylEngine.Settings s=settings(style);
            s.linePresenceRecovery=0;s.portraitBackgroundGuard=0;
            VinylEngine.Result frozen=VinylEngine.generate(original,w,h,s);
            s.linePresenceRecovery=.55f;s.portraitBackgroundGuard=.55f;
            VinylEngine.Result fresh=VinylEngine.generate(original,w,h,s);
            for(int i=0;i<4;i++){
                var stats=fresh.lineMetrics.get(i);
                totalPromoted+=stats.promoted;totalBackground+=stats.backgroundDownweighted;
                ok(stats.examined>0,"Tracker not active style="+style+" layer="+i);
                ok(!VinylEngine.svg(fresh,i).isBlank(),"SVG missing");
                ok(VinylEngine.svgElementsForLayer(fresh,i)<=5000,"too many paths");
            }
            LineCutDiagnostics.Report d=LineCutDiagnostics.inspect(fresh);
            ok(d.complete&&d.selfIntersections==0&&d.subMinimumWidthSamples==0,
               "Cut geometry failure "+style+" self="+d.selfIntersections+" width="+d.subMinimumWidthSamples);
            for(int[] empty:new int[][]{white,transparent,paper})
                ok(VinylEngine.generate(empty,w,h,s).count()==0,"Cut generated on blank "+style);
            ok(!VinylEngine.svg(frozen,0).isBlank(),"Legacy mode failed");
            s.linePresenceRecovery=0;s.portraitBackgroundGuard=0f;
            for(int i=0;i<4;i++)ok(VinylEngine.svg(frozen,i).equals(VinylEngine.svg(VinylEngine.generate(original,w,h,s),i)),
              "Background guard modified disabled legacy geometry");
        }
        ok(totalPromoted>300,"No meaningful portrait-tone recovery " +totalPromoted);
        ok(totalBackground>100,"No outer-background downweighting " +totalBackground);
        // RC101: Snip-supported recovery is active, but may not cross a removed
        // polygon or generate subminimum-width / self-intersecting ribbons.
        int snipChanged=0;
        for(int style:styles){
            VinylEngine.Settings s=settings(style);s.snips.add(new SnipMask.Operation(
                SnipMask.REMOVE_INSIDE,.45f,.20f,.55f,.20f,.55f,.82f,.45f,.82f));
            s.linePresenceRecovery=0;VinylEngine.Result baseline=VinylEngine.generate(original,w,h,s);
            s.linePresenceRecovery=1;VinylEngine.Result enabled=VinylEngine.generate(original,w,h,s);
            for(int i=0;i<4;i++){
                if(!VinylEngine.svg(baseline,i).equals(VinylEngine.svg(enabled,i)))snipChanged++;
                for(VinylEngine.Shape sh:enabled.shapes.get(i))if(!sh.circle)
                    ok(s.snips.allowsPolygon(sh.xy,s.widthMm,enabled.heightMm),
                        "Snip recovery escaped boundary style="+style+" layer="+i);
            }
            LineCutDiagnostics.Report report=LineCutDiagnostics.inspect(enabled);
            ok(report.complete&&report.selfIntersections==0&&report.subMinimumWidthSamples==0,
                "Unsafe Snipped recovery style="+style);
        }
        ok(snipChanged>0,"Snip-aware recovery had no effect");
        System.out.println("PASS v2.50.5: 5 families / 4 CMYK channels; recovered="+totalPromoted+
          " downweighted="+totalBackground+"; blank/alpha/paper and snips protected; minimum cut and nonintersection");
    }
}
