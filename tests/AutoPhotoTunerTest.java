import com.halftonestudio.AutoPhotoTuner;
import com.halftonestudio.SnipMask;
import com.halftonestudio.VinylPlainLanguage;

public class AutoPhotoTunerTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        int w=128,h=96;
        int[] pixels=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int v=x<w/2?25:225;
            if(y%7==0)v=110;
            pixels[y*w+x]=0xff000000|(v<<16)|(v<<8)|v;
        }
        AutoPhotoTuner.Stats st=AutoPhotoTuner.analyze(pixels,w,h,null,false);
        check(st.valid==w*h,"Whole photo should be analyzed");
        check(st.contrast>.6f,"Strong black/white split should show high contrast");
        AutoPhotoTuner.Stats circ=AutoPhotoTuner.analyze(pixels,w,h,null,true);
        check(circ.valid>0&&circ.valid<st.valid,"Circle exclusion should reduce analyzed area");
        SnipMask half=new SnipMask();
        half.add(new SnipMask.Operation(SnipMask.KEEP_INSIDE,0f,0f,.5f,0f,.5f,1f,0f,1f));
        AutoPhotoTuner.Stats cropped=AutoPhotoTuner.analyze(pixels,w,h,half,false);
        check(cropped.valid>0&&cropped.valid<st.valid,"Snip mask must exclude outside pixels");
        for(int goal=0;goal<=2;goal++)for(int style=0;style<=8;style++){
            AutoPhotoTuner.Suggestion r=AutoPhotoTuner.suggest(st,style,goal,152.4f,.8f);
            check(r.style==style&&r.goal==goal,"Style/goal identity");
            check(r.spacingMm*r.lineWidth>=.8f,"Physical line clearance floor");
            check(r.stippleDensity>=.5f&&r.stippleDensity<=1f,"Density allowed range");
            check(r.stippleSize>=.6f&&r.stippleSize<=1f,"Dot size allowed range");
            check(r.stippleFill>=.25f&&r.stippleFill<=.8f,"Coverage slider bounds");
            check(r.summary()!=null&&!r.summary().isEmpty(),"Explanatory recommendation");
        }
        AutoPhotoTuner.Suggestion detailed=AutoPhotoTuner.suggest(st,AutoPhotoTuner.STIPPLE,AutoPhotoTuner.DETAIL,152.4f,.8f);
        AutoPhotoTuner.Suggestion easy=AutoPhotoTuner.suggest(st,AutoPhotoTuner.STIPPLE,AutoPhotoTuner.EASY,152.4f,.8f);
        check(detailed.stippleDensity>easy.stippleDensity,"Maximum detail must request more dots");
        check(easy.stippleSize>=detailed.stippleSize,"Easy cutting must not create smaller dots");
        check(VinylPlainLanguage.label("Stipple same-sheet clearance · mm").startsWith("Space between cuts"),"Plain clearance label");
        for(String key:new String[]{"Priority knockout","Stacked overprint","Nearest swatch","Adaptive coverage",
            "Minimum feature","Registration marks","Layer density","Trap margin","Edge definition"}){
            check(VinylPlainLanguage.help(key)!=null,"Missing help for "+key);
            check(VinylPlainLanguage.help(key).length()<180,"Tooltip too verbose: "+key);
        }
        check(st.saturation>=0&&st.saturation<=1,"Bounded stats");
        boolean rejected=false;
        try{AutoPhotoTuner.analyze(new int[4],2,2,null,false);}catch(IllegalArgumentException yes){rejected=true;}
        check(rejected,"Reject photos without usable pixels");
        System.out.println("PASS: Auto photo analysis, nine style families, three goals, mask exclusions, physical bounds, tooltips");
    }
}
