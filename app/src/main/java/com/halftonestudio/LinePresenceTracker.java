package com.halftonestudio;

import java.util.ArrayList;

/**
 * Width-aware 1-D hysteresis for a single, already existing ribbon centerline.
 * Never invents centerlines: candidates must have original CMYK coverage and
 * acceptable geometry. The two-pass rule never crosses a hard exclusion.
 * Each lane is independent, so no extra neighboring cuts or duplicated paths.
 */
public final class LinePresenceTracker {
    private LinePresenceTracker() {}
    public static final class Metrics {
        public long examined,strong,promoted,rejected,backgroundDownweighted;
    }
    /** x, y, half-width, original CMYK coverage, allowed(1/0). */
    public static final class Sample {
        final float x,y,half,source;
        final boolean allowed,subject;
        Sample(float x,float y,float half,float source,boolean allowed,boolean subject){
            this.x=x;this.y=y;this.half=half;this.source=source;this.allowed=allowed;this.subject=subject;
        }
    }
    private static boolean highlightEligible(Sample p,float half,float threshold){
        // Source evidence is essential: never fill empty/transparent/paper pixels.
        // Warm subject gating avoids turning smooth beige/neutral walls into
        // full-page colored screens. Dark hair already passes strong thresholds.
        return p.allowed && p.subject && p.source>=threshold && p.half>=half*.10f;
    }
    /** Return physically valid groups; caller converts groups to closed ribbon polygons. */
    public static ArrayList<ArrayList<float[]>> extract(ArrayList<Sample> input,
            float minimumMm,float sampleStepMm,float strength,int lane,int channel,Metrics m) {
        return extract(input,minimumMm,sampleStepMm,strength,lane,channel,m,0f);
    }
    /** Additional independent highlight preservation. Opt-in: no geometry changes at 0.
     * A long, supported, warm subject-tone run can be retained even without strong
     * anchors. Never creates a new centerline, crosses a hard exclusion, or extends
     * into unrepresented paper. On a sparse tone, line-frequency modulation
     * retains fewer lanes at the cutter's physical minimum width.
     */
    public static ArrayList<ArrayList<float[]>> extract(ArrayList<Sample> input,
            float minimumMm,float sampleStepMm,float strength,int lane,int channel,Metrics m,
            float highlightRetention) {
        ArrayList<ArrayList<float[]>> result=new ArrayList<>();
        if(input.isEmpty())return result;
        int n=input.size();boolean[] strong=new boolean[n],weak=new boolean[n],keep=new boolean[n];
        float minHalf=minimumMm*.5f;
        // Low threshold is bounded. Even at 100%, we cannot retain arbitrary
        // infinitesimal coverage or 0-width geometry.
        float threshold=minHalf*(1f-.45f*strength);
        for(int i=0;i<n;i++){
            Sample p=input.get(i);m.examined++;
            strong[i]=p.allowed&&p.half>=minHalf;
            weak[i]=p.allowed&&p.source>=.14f&&p.half>=threshold;
            if(strong[i]){keep[i]=true;m.strong++;}
        }
        // Two-ended path hysteresis: connect short weak sections ONLY if
        // supported by strong sections on both sides, never at blank endpoints.
        // No gap can exceed 8 mm or cross forbidden/blank samples.
        int maxGap=Math.max(1,Math.min(18,(int)(8f/Math.max(.1f,sampleStepMm))));
        for(int i=0;i<n;){
            if(strong[i]||!weak[i]){i++;continue;}
            int a=i;
            while(i<n && !strong[i] && weak[i])i++;
            int b=i;
            boolean anchored=a>0&&b<n&&strong[a-1]&&strong[b];
            if(anchored && b-a<=maxGap){for(int k=a;k<b;k++){keep[k]=true;m.promoted++;}}
            // Unsupported weak sections remain uncut.
        }
        // Sustained under-width portrait tone cannot be rescued by ordinary
        // two-ended hysteresis because the lane may have no strong anchor.
        // Instead use spatial-frequency modulation: reserve an occasional
        // *existing* lane at minimum width where several adjacent source
        // samples carry genuine warm portrait color. No blanket carrier in
        // smooth backgrounds, neutral paper, or unsupported cool regions.
        // This keeps physical cut width while using fewer ribbons to model
        // weak tones, rather than filling EVERY faint section (v2.50.1 bug).
        int period=strength>=.80f?2:strength>=.35f?3:4;
        boolean selected=Math.floorMod(lane+Math.max(0,channel)*7,period)==0;
        if(selected){
            int needed=Math.max(5,(int)Math.ceil(5f/Math.max(.1f,sampleStepMm)));
            for(int i=0;i<n;){
                Sample p=input.get(i);
                if(!p.allowed || !p.subject || p.source<.17f || p.half<minHalf*.42f){i++;continue;}
                int a=i;
                while(i<n){
                    Sample q=input.get(i);
                    if(!q.allowed||!q.subject||q.source<.17f||q.half<minHalf*.42f)break;
                    i++;
                }
                if(i-a>=needed){
                    for(int k=a;k<i;k++)if(!keep[k]){keep[k]=true;m.promoted++;}
                }
            }
        }
        if(highlightRetention>0.001f){
            float gain=Math.max(0f,Math.min(1f,highlightRetention));
            int highlightPeriod=gain>=.82f?1:gain>=.52f?2:gain>=.25f?3:4;
            // Offset families so the chromatic sheets do not all use identical
            // faint-tone lanes. No new centerlines or duplicate same-sheet paths.
            boolean selectedHighlight=Math.floorMod(lane+channel*5,highlightPeriod)==0;
            if(selectedHighlight){
                float sourceThreshold=.175f-.085f*gain;
                int needed=Math.max(4,(int)Math.ceil((5f-2f*gain)/Math.max(.1f,sampleStepMm)));
                for(int i=0;i<n;){
                    Sample p=input.get(i);
                    if(!highlightEligible(p,minHalf,sourceThreshold)){i++;continue;}
                    int a=i;
                    while(i<n&&highlightEligible(input.get(i),minHalf,sourceThreshold))i++;
                    // A brief isolated skin-colored patch does not become a cut.
                    if(i-a>=needed){
                        for(int j=a;j<i;j++)if(!keep[j]){keep[j]=true;m.promoted++;}
                    }
                }
            }
        }
        for(int i=0;i<n;i++)if(!keep[i] && input.get(i).allowed && input.get(i).source>=.14f)m.rejected++;
        // Preserve width-threshold behavior at ends and paper gaps. Thin
        // sections that are retained become minimum-width cuttable geometry;
        // skip isolated single-sample shapes, which create blade chatter.
        ArrayList<float[]> run=new ArrayList<>();
        for(int i=0;i<n;i++){
            Sample p=input.get(i);
            if(keep[i]){
                run.add(new float[]{p.x,p.y,Math.max(p.half,minHalf+.012f)});
            }else if(!run.isEmpty()){
                if(run.size()>=2)result.add(run);
                run=new ArrayList<>();
            }
        }
        if(run.size()>=2)result.add(run);
        return result;
    }
}
