package com.halftonestudio;

/** Bounded, deterministic, Android-free photo analysis. It never edits colors, masks,
 * project dimensions, layer count or cut profiles. Recommendations are advisory. */
public final class AutoPhotoTuner {
    private AutoPhotoTuner(){}
    public static final int DETAIL=0,BALANCED=1,EASY=2;
    public static final int STIPPLE=0,TESSELLATION=1,LAYERED=2,TONAL=3,
        LINES=4,SHAPES=5,CONTINUOUS=6,MAZE=7,STENCIL=8;
    public static final class Stats {
        public final int valid;
        public final float mean,contrast,edge,saturation,darkFraction,brightFraction;
        Stats(int n,float m,float c,float e,float sat,float dark,float bright){
            valid=n;mean=m;contrast=c;edge=e;saturation=sat;darkFraction=dark;brightFraction=bright;
        }
        public String summary(){return String.format(java.util.Locale.US,
            "Photo: contrast %.0f%% · edges %.0f%% · colorful areas %.0f%% · usable pixels %,d",
            contrast*100f,edge*100f,saturation*100f,valid);
        }
    }
    public static final class Suggestion {
        public final int style,goal;
        public final float spacingMm,lineWidth,lineDetail,lineContour,shapeScale,screenContrast;
        public final float stippleDensity,stippleSize,stippleFill;
        public final int mosaicDetail,layeredColumns,tonalDetail,continuousDetail,mazeGrid,stencilCleanup;
        public final float stencilSmoothing;
        public final float mosaicEdge,layeredPhotoDetail;
        public final String rationale;
        Suggestion(int style,int goal,float spacing,float width,float lineDetail,float contour,
            float shape,float contrast,float density,float dot,float fill,int mosaic,
            float mosaicEdge,int cell,float cellDetail,int tonal,int continuous,int maze,String rationale){
            this.style=style;this.goal=goal;spacingMm=spacing;lineWidth=width;
            this.lineDetail=lineDetail;lineContour=contour;shapeScale=shape;screenContrast=contrast;
            stippleDensity=density;stippleSize=dot;stippleFill=fill;mosaicDetail=mosaic;
            this.mosaicEdge=mosaicEdge;layeredColumns=cell;layeredPhotoDetail=cellDetail;
            tonalDetail=tonal;continuousDetail=continuous;mazeGrid=maze;this.rationale=rationale;
            stencilCleanup=goal==EASY?2:goal==DETAIL?0:1;
            stencilSmoothing=goal==EASY?.18f:goal==DETAIL?.06f:.10f;
        }
        public String summary(){
            String base;
            if(style==STIPPLE)base=String.format(java.util.Locale.US,
                "Detail %.0f%% · Dot size %.0f%% · Fill %.0f%%",stippleDensity*100,stippleSize*100,stippleFill*100);
            else if(style==TESSELLATION)base="Tile detail "+mosaicDetail+" · Edge detail "+Math.round(mosaicEdge*100)+"%";
            else if(style==LAYERED)base="Cell detail "+layeredColumns+" · Photo detail "+Math.round(layeredPhotoDetail*100)+"%";
            else if(style==TONAL)base="Photo detail "+tonalDetail+"%";
            else if(style==CONTINUOUS)base="Portrait detail "+continuousDetail+"%";
            else if(style==MAZE)base="Maze detail "+mazeGrid;
            else if(style==STENCIL)base=String.format(java.util.Locale.US,
                "Region size %.2f mm · Cleanup %d · Smoothing %.0f%%",spacingMm,stencilCleanup,stencilSmoothing*100);
            else base=String.format(java.util.Locale.US,
                "Spacing %.2f mm · Contrast %.0f%% · %s %.0f%%",spacingMm,screenContrast*100,
                style==LINES?"Line width":"Shape size",(style==LINES?lineWidth:shapeScale)*100);
            return base+"\n"+rationale;
        }
    }
    private static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
    private static float lum(int c){return (.2126f*((c>>>16)&255)+.7152f*((c>>>8)&255)+.0722f*(c&255))/255f;}
    /** Analyzer samples only existing and physically eligible image pixels. */
    public static Stats analyze(int[] argb,int width,int height,SnipMask snips,boolean circular){
        if(argb==null||width<2||height<2||argb.length!=(long)width*height)
            throw new IllegalArgumentException("Invalid photo pixels");
        float[] light=new float[argb.length];boolean[] valid=new boolean[argb.length];
        int n=0,dark=0,bright=0,saturated=0;
        double sum=0,sum2=0;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            int i=y*width+x,c=argb[i];
            if((c>>>24)<32)continue;
            float nx=(x+.5f)/width,ny=(y+.5f)/height;
            if(circular && ((nx-.5f)*(nx-.5f)+(ny-.5f)*(ny-.5f)>.25f))continue;
            if(snips!=null&&!snips.allows(nx,ny))continue;
            float v=lum(c);light[i]=v;valid[i]=true;sum+=v;sum2+=v*v;n++;
            if(v<.23f)dark++;if(v>.82f)bright++;
            int r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;
            if(Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))>45)saturated++;
        }
        if(n<20)throw new IllegalArgumentException("No usable photo pixels inside the crop");
        double edges=0;int pairs=0;
        for(int y=1;y<height-1;y++)for(int x=1;x<width-1;x++){
            int i=y*width+x;
            if(!valid[i]||!valid[i+1]||!valid[i-1]||!valid[i+width]||!valid[i-width])continue;
            float dx=Math.abs(light[i+1]-light[i-1]);float dy=Math.abs(light[i+width]-light[i-width]);
            edges+=Math.min(1f,(dx+dy)*1.65f);pairs++;
        }
        float mean=(float)(sum/n);
        float stdev=(float)Math.sqrt(Math.max(0,sum2/n-mean*mean));
        return new Stats(n,mean,clamp(stdev*2.4f,0,1),
            clamp((float)(edges/Math.max(1,pairs))*2f,0,1),
            saturated/(float)n,dark/(float)n,bright/(float)n);
    }
    /** Physical limits are treated as lower bounds. Easy mode cannot reduce cut sizes. */
    public static Suggestion suggest(Stats s,int style,int goal,float artworkWidthMm,float minFeatureMm){
        if(s==null||s.valid<20)throw new IllegalArgumentException("No analyzable photo");
        if(goal<DETAIL||goal>EASY||style<STIPPLE||style>STENCIL)throw new IllegalArgumentException("Unknown photo tuning goal/style");
        float complexity=clamp(.55f*s.edge+.45f*s.contrast,0,1);
        float g=goal==DETAIL?1f:goal==EASY?-1f:0f;
        float detail=clamp(.56f+.32f*complexity+.12f*g,.32f,1);
        float contrast=clamp(1.02f+(.25f-s.contrast)*.38f+.06f*g,.90f,1.26f);
        float width=clamp(.73f+.08f*s.contrast+.05f*g,.64f,.89f);
        float spacing=clamp(3.6f-1.35f*complexity-.42f*g,Math.max(1.3f,minFeatureMm/width),7.0f);
        float lineDetail=clamp(.12f+.50f*s.edge+.12f*g,.08f,.62f);
        float contour=clamp(.54f+.33f*s.edge+.08f*g,.42f,.88f);
        float shape=clamp(.78f+.09f*complexity+.04f*g,.71f,.91f);
        float density=PercentControlLimits.pointDensity(.75f+.18f*complexity+.07f*g);
        float dots=PercentControlLimits.dotSize(1.08f+.10f*s.darkFraction-.08f*g);
        float fill=clamp(.55f+.21f*complexity+.07f*g,.40f,.80f);
        int mosaic=Math.round(clamp(38f+51f*detail,20,100));
        int cells=Math.round(clamp(16f+21f*detail,10,40));
        int tonal=Math.round(clamp(24f+69f*detail,0,100));
        int cont=Math.round(clamp(30f+67f*detail,1,100));
        int maze=Math.round(clamp(24f+62f*detail,20,100));
        String why=s.edge>.17f?"Fine edges detected; detail is prioritized where physically cuttable.":
            s.contrast<.24f?"Low photo contrast detected; tonal separation is gently strengthened.":
            "Settings balance photographic tone against the minimum safe cut size.";
        if(goal==EASY)why+=" Larger, simpler cuts are preferred.";
        return new Suggestion(style,goal,spacing,width,lineDetail,contour,shape,contrast,density,dots,fill,
            mosaic,clamp(.50f+.42f*s.edge,0,1),cells,detail,tonal,cont,maze,why);
    }
}
