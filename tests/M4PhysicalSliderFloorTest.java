package com.halftonestudio;

import java.util.Locale;

/**
 * M4 / D8: physical sliders and the render floor agree with the material minimum.
 * Real engine evidence: the continuous-curve vinyl ribbon generated with the 558 default
 * slider values (feature 0.55 mm, gap 0.45 mm) under the default 0.8 mm cut minimum, and
 * with the floored values the app now passes to the engine.
 */
public final class M4PhysicalSliderFloorTest {
    static int failures=0;
    static void ok(boolean b,String m){if(!b){failures++;System.out.println("FAIL "+m);}}
    public static void main(String[] args){
        // Policy properties over every material profile.
        for(int id=1;id<CutMaterialProfile.names().length;id++){
            CutMaterialProfile.Profile p=CutMaterialProfile.byId(id);
            float f=PhysicalSliderFloor.featureMm(p.featureMm),g=PhysicalSliderFloor.gapMm(p.gapMm,p.featureMm);
            ok(f>=p.featureMm,"feature floor honours "+p.name);
            ok(g>=Math.max(.25f,p.gapMm),"gap floor honours "+p.name);
            for(float[] stat:new float[][]{{.40f,1.20f},{.30f,1.50f},{.4f,3f},{.55f,3f}}){
                float[] r=PhysicalSliderFloor.range(stat[0],stat[1],f,.05f);
                ok(r[0]>=f&&r[0]>=stat[0],"range starts at the floor");
                ok(r[1]>r[0],"range never collapses");
                ok(PhysicalSliderFloor.effective(stat[0],f)==r[0],"lowest slider value is the effective value");
            }
        }
        ok(PhysicalSliderFloor.effective(Float.NaN,.8f)==.8f,"non-finite falls back to the floor");
        ok(PhysicalSliderFloor.effective(1.2f,.8f)==1.2f,"values above the floor are kept");
        // Defaults that are already at the floor do not change (stipple 0.8 mm dots, min 0.8 mm).
        ok(PhysicalSliderFloor.effective(new MultiColorStippleEngine.Options().minDotMm[0],PhysicalSliderFloor.featureMm(.8f))
            ==new MultiColorStippleEngine.Options().minDotMm[0],"stipple default dot unchanged");

        // Real engine: continuous-curve vinyl ribbon at 6 in, default cut minimum 0.8 mm.
        SpaceFillingCurves.Options co=new SpaceFillingCurves.Options();
        co.family=SpaceFillingCurves.GOSPER;co.depth=3;co.width=400;co.height=400;co.photoInfluence=0;
        SpaceFillingCurves.Result line=SpaceFillingCurves.generate(co);
        float minimum=.8f,bridge=.5f;
        SpaceFillingRibbon.Result legacy=ribbon(line,.55f,.45f,1.15f);
        float floorF=PhysicalSliderFloor.featureMm(minimum),floorG=PhysicalSliderFloor.gapMm(bridge,minimum);
        SpaceFillingRibbon.Result floored=ribbon(line,PhysicalSliderFloor.effective(.55f,floorF),
            PhysicalSliderFloor.effective(.45f,floorG),PhysicalSliderFloor.effective(1.15f,Math.max(.55f,floorF)));
        System.out.printf(Locale.US,"curve ribbon at min %.2f mm: 558 defaults narrowest %.3f mm, M4 floor narrowest %.3f mm%n",
            minimum,legacy.minimumWidthMm,floored.minimumWidthMm);
        ok(legacy.minimumWidthMm<minimum,"fixture reproduces the 558 sub-minimum ribbon");
        ok(floored.minimumWidthMm>=minimum-1e-3f,"floored ribbon never narrower than the cut minimum");
        if(failures>0)throw new AssertionError(failures+" D8 failures");
        System.out.println("PASS M4 D8 physical slider floor");
    }
    static SpaceFillingRibbon.Result ribbon(SpaceFillingCurves.Result line,float feature,float gap,float width){
        SpaceFillingRibbon.Options o=new SpaceFillingRibbon.Options();
        o.artworkWidthMm=152.4f;o.maximumWidthMm=width;o.minimumFeatureMm=Math.min(feature,width);o.minimumGapMm=gap;
        return SpaceFillingRibbon.generate(line,400,400,o);
    }
}
