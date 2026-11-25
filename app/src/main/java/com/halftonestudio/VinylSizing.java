package com.halftonestudio;

/** Physical vinyl cutting recommendations, not print-shop LPI values.
 * These are conservative STARTING POINTS. Final image detail and weedability
 * must be verified in a Cricut test cut.
 */
public final class VinylSizing {
    private VinylSizing() {}
    public static final int DETAIL=0, BALANCED=1, EASY_WEED=2;
    public static final String[] PROFILES={"Detail", "Balanced", "Easy weed"};
    public static final class Size {
        public final String label;public final float widthIn,heightIn;
        public Size(String name,float w,float h){label=name;widthIn=w;heightIn=h;}
    }
    public static final Size[] SIZES={
        new Size("4 × 6",4,6),new Size("5 × 7",5,7),
        new Size("8 × 10",8,10),new Size("8.5 × 11",8.5f,11),
        new Size("11 × 14",11,14),new Size("12 × 16",12,16)
    };
    public static int targetPaths(int profile){return profile==DETAIL?4200:profile==EASY_WEED?1700:3000;}
    public static float spacingMm(float widthIn,float heightIn,int pattern,int profile){
        if(!(widthIn>0&&heightIn>0))throw new IllegalArgumentException("Invalid physical size");
        float area=widthIn*heightIn;
        float inches;
        if(SmartHalftonePresets.ribbons(pattern)){
            inches=(float)(.105*Math.pow(Math.max(4,area)/35f,.20));
            inches*=profile==DETAIL?.84f:profile==EASY_WEED?1.32f:1f;
            inches=Math.max(.075f,inches);
        }else{
            // The estimated dot-grid density is intentionally below the Cricut
            // 5000-element maximum. Actual path counts are checked per layer.
            inches=(float)Math.sqrt(area/(targetPaths(profile)*.83f));
            inches=Math.max(profile==EASY_WEED?.11f:profile==DETAIL?.067f:.082f,inches);
        }
        return VinylEngine.toMm(Math.min(.36f,inches));
    }
    public static void apply(VinylEngine.Settings s,float widthIn,float heightIn,int profile){
        s.widthMm=VinylEngine.toMm(widthIn);
        s.masterSpacingMm=spacingMm(widthIn,heightIn,s.pattern,profile);
        s.linkedControls=true;
        // RC104: size presets control spacing/detail, never silently raise the
        // user-set smallest cut width. New Settings start at 0.010 in.
        // An explicitly selected material profile can still set stricter limits.
        s.bridgeMm=VinylEngine.toMm(profile==DETAIL?.014f:profile==EASY_WEED?.032f:.020f);
        if(SmartHalftonePresets.ribbons(s.pattern)){
            s.lineMaxFraction=profile==DETAIL?.90f:profile==EASY_WEED?.78f:.84f;
            s.lineSmoothing=profile==DETAIL?.30f:profile==EASY_WEED?.58f:.44f;
            s.lineGamma=profile==DETAIL?.80f:profile==EASY_WEED?1.12f:.92f;
        }
        for(VinylEngine.Layer l:s.layers){l.spacing=s.masterSpacingMm;l.density=s.masterDensity;l.contrast=s.masterContrast;}
    }
    /** Increase spacing until the *actual generated paths in each SVG* fit the
     * preferred budget. All layers share identical spacing to maintain continuity.
     * Work happens on a background executor, never the Android UI thread.
     */
    public static final class Fit {
        public final VinylEngine.Settings settings;
        public final VinylEngine.Result result;
        public final int maxLayer;
        public Fit(VinylEngine.Settings s,VinylEngine.Result r,int n){settings=s;result=r;maxLayer=n;}
    }
    public static Fit fit(int[] pixels,int w,int h,VinylEngine.Settings selected,int profile){
        VinylEngine.Settings s=selected.copy();s.linkedControls=true;
        float heightIn=VinylEngine.toInches(s.widthMm)*h/(float)w;
        s.masterSpacingMm=Math.max(s.masterSpacingMm,spacingMm(VinylEngine.toInches(s.widthMm),heightIn,s.pattern,profile));
        final int budget=targetPaths(profile);
        for(int attempt=0;attempt<12;attempt++){
            for(VinylEngine.Layer l:s.layers)l.spacing=s.masterSpacingMm;
            try{
                VinylEngine.Result r=VinylEngine.generate(pixels,w,h,s);
                int max=0;for(int i=0;i<r.shapes.size();i++)if(r.settings.layers.get(i).enabled)
                    max=Math.max(max,VinylEngine.svgElementsForLayer(r,i));
                String error=VinylEngine.validateExport(r);
                if(error!=null&&!error.contains("5,000"))throw new IllegalArgumentException(error);
                if(max<=budget && error==null){
                    // Explore slightly finer detail while preserving a safety margin.
                    Fit best=new Fit(s.copy(),r,max);
                    for(int finer=0;finer<3;finer++){
                        VinylEngine.Settings trial=best.settings.copy();
                        trial.masterSpacingMm*=.93f;
                        for(VinylEngine.Layer l:trial.layers)l.spacing=trial.masterSpacingMm;
                        try{
                            VinylEngine.Result fr=VinylEngine.generate(pixels,w,h,trial);
                            String issue=VinylEngine.validateExport(fr);
                            int peak=0;for(int k=0;k<fr.shapes.size();k++)if(fr.settings.layers.get(k).enabled)
                                peak=Math.max(peak,VinylEngine.svgElementsForLayer(fr,k));
                            if(issue!=null||peak>budget)break;
                            best=new Fit(trial,fr,peak);
                        }catch(IllegalArgumentException overflow){break;}
                    }
                    return best;
                }
            }catch(IllegalArgumentException e){
                if(!e.getMessage().contains("Cricut")&&!e.getMessage().contains("samples")&&!e.getMessage().contains("Too many"))throw e;
            }
            s.masterSpacingMm*=1.16f;
            if(s.masterSpacingMm>VinylEngine.toMm(.38f))break;
        }
        throw new IllegalArgumentException("Cannot fit SVG layers at this size. Try a simpler crop or smaller artwork.");
    }
}
