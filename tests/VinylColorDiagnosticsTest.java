package com.halftonestudio;
import java.util.*;
public final class VinylColorDiagnosticsTest {
    private static void check(boolean x,String msg){if(!x)throw new AssertionError(msg);}
    private static float area(VinylEngine.Shape sh){
        if(sh.circle)return (float)Math.PI*sh.r*sh.r;
        double sum=0;for(int i=0;i<sh.xy.length;i+=2){int j=(i+2)%sh.xy.length;
            sum+=sh.xy[i]*sh.xy[j+1]-sh.xy[j]*sh.xy[i+1];}return (float)Math.abs(sum/2);
    }
    private static float layerArea(VinylEngine.Result r,int idx){float a=0;for(VinylEngine.Shape sh:r.shapes.get(idx))a+=area(sh);return a;}
    private static int channel(VinylEngine.Result r,int ch){for(int i=0;i<r.settings.layers.size();i++)if(r.settings.layers.get(i).cmykChannel==ch)return i;throw new AssertionError("channel absent");}
    private static int[] photo(int w,int h){int[] pix=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){
        // Teal-blue, warm skin and grayscale areas; cyan must come from actual source chroma.
        float q=x/(float)w;int red=q<.48?40:q<.75?220:65;
        int green=q<.48?165:q<.75?155:65;
        int blue=q<.48?195:q<.75?130:65;
        int variation=(int)(24*Math.sin(x*.13)+17*Math.cos(y*.16));
        red=Math.max(0,Math.min(255,red+variation));green=Math.max(0,Math.min(255,green+variation));blue=Math.max(0,Math.min(255,blue+variation));
        pix[y*w+x]=0xff000000|(red<<16)|(green<<8)|blue;
    }return pix;}
    private static void testCyanStrength(){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();s.widthMm=100;s.marginMm=1;s.minimumMm=.18f;s.bridgeMm=.15f;
        s.masterSpacingMm=3f;s.pattern=VinylEngine.ROUND;s.layers.get(1).strength=1f;
        int[] pix=photo(112,80);
        VinylEngine.Result base=VinylEngine.generate(pix,112,80,s);
        s.layers.get(1).strength=1.55f;
        VinylEngine.Result boosted=VinylEngine.generate(pix,112,80,s);
        check(layerArea(boosted,channel(boosted,0))>layerArea(base,channel(base,0))*1.08f,"cyan strength should increase cyan cut area where source cyan exists");
        // Cyan strength must not alter black, magenta or yellow cut geometry.
        for(int channel=1;channel<4;channel++){
            int i=channel(base,channel),j=channel(boosted,channel);
            check(VinylEngine.svg(base,i).equals(VinylEngine.svg(boosted,j)),"cyan strength altered unrelated layer "+channel);
        }
        check(boosted.settings.layers.get(1).strength==1.55f,"strength lost in copy");
    }
    private static void testBlackColorProtection(){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();s.widthMm=100;s.marginMm=1;
        s.minimumMm=.18f;s.bridgeMm=.15f;s.masterSpacingMm=3f;s.pattern=VinylEngine.ROUND;
        int[] pix=photo(112,80);
        s.blackColorProtection=0f;VinylEngine.Result full=VinylEngine.generate(pix,112,80,s);
        s.blackColorProtection=.55f;VinylEngine.Result protectedColors=VinylEngine.generate(pix,112,80,s);
        int black=channel(full,3);
        check(layerArea(protectedColors,black)<layerArea(full,black),"black color protection should shrink black in saturated regions");
        // Preserve other separations, including cyan, rather than compensating by forced nesting.
        for(int ch=0;ch<3;ch++){
            int i=channel(full,ch);check(VinylEngine.svg(full,i).equals(VinylEngine.svg(protectedColors,i)),
                "color protection must not alter CMY geometry "+ch);
        }
        // Neutral charcoal should stay unchanged even when color protection increases.
        int[] neutral=new int[80*60];Arrays.fill(neutral,0xff505050);
        VinylEngine.Result a=VinylEngine.generate(neutral,80,60,s);
        s.blackColorProtection=0f;VinylEngine.Result b=VinylEngine.generate(neutral,80,60,s);
        check(VinylEngine.svg(a,channel(a,3)).equals(VinylEngine.svg(b,channel(b,3))),
                "neutral black geometry should remain unchanged");
    }
    private static void testOpaqueOcclusion(){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();s.widthMm=100;
        VinylEngine.Result r=new VinylEngine.Result(s,100,10,VinylEngine.defaultMarks(100,100));
        for(int i=0;i<4;i++)r.shapes.add(new ArrayList<>());
        r.shapes.get(1).add(VinylEngine.Shape.polygon(10,10,60,10,60,60,10,60));
        r.shapes.get(3).add(VinylEngine.Shape.polygon(10,10,60,10,60,60,10,60));
        VinylDiagnostics.Report d=VinylDiagnostics.analyze(r,100);
        check(d.layers[1].coveredPct>20,"cyan should cover test area");
        check(d.layers[1].visiblePct==0,"covered cyan must be hidden by opaque black");
        check(d.layers[1].hiddenFractionPct==100,"all covered cyan should be reported as occluded");
        check(d.layers[3].visiblePct>20,"top black should be visible");
        // Application order affects visible area, not its individual path geometry.
        Collections.swap(r.shapes,1,3);Collections.swap(r.settings.layers,1,3);
        VinylDiagnostics.Report moved=VinylDiagnostics.analyze(r,100);
        check(moved.layers[3].visiblePct>20,"top shifted cyan remains visible");
    }
    private static void testContour(){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();s.widthMm=105;s.marginMm=1;s.minimumMm=.18f;
        s.masterSpacingMm=3f;s.bridgeMm=.2f;s.pattern=VinylEngine.LINE;s.anglePreset=VinylEngine.ANGLES_CLASSIC;
        s.lineGamma=.65f;s.lineSmoothing=.45f;s.lineSampleMm=.35f;
        int[] pix=photo(112,80);
        s.contourStrength=0f;VinylEngine.Result straight=VinylEngine.generate(pix,112,80,s);
        s.contourStrength=.8f;VinylEngine.Result contoured=VinylEngine.generate(pix,112,80,s);
        int black=channel(straight,3);
        check(!VinylEngine.svg(straight,black).equals(VinylEngine.svg(contoured,black)),"contour strength should alter line geometry");
        check(VinylEngine.validateExport(contoured)==null,"contoured ribbon should remain exportable");
        check(contoured.settings.layers.get(black).angle==45,"black angle changed");
        check(contoured.settings.layers.get(channel(contoured,0)).angle==15,"cyan angle changed");
    }
    public static void main(String[] a){testCyanStrength();testBlackColorProtection();testOpaqueOcclusion();testContour();
        System.out.println("PASS: cyan gain isolated; black chroma protection; opaque-stack visibility diagnostics; contour response; CMYK angles and export");}
}
