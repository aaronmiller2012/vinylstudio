package com.halftonestudio;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Build 557: every adjustment slider in the app has its own detailed ⓘ explanation. */
public final class V3SliderGuideTest {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args) throws Exception {
        Path src=Paths.get("app/src/main/java/com/halftonestudio");
        Pattern call=Pattern.compile("(?:inchSlider|slider)\\([A-Za-z]+,\"([^\"]+)\"");
        Set<String> labels=new TreeSet<>();
        try(DirectoryStream<Path> files=Files.newDirectoryStream(src,"*.java")){
            for(Path f:files){Matcher m=call.matcher(new String(Files.readAllBytes(f),"UTF-8"));while(m.find())labels.add(m.group(1));}
        }
        check(labels.size()>=100,"slider scan found too few labels: "+labels.size());
        List<String> missing=new ArrayList<>();
        Set<SliderGuide.Entry> used=new HashSet<>();
        for(String label:labels){
            // Dynamic labels such as "Level of detail · "+count+" layers" are matched by prefix.
            String probe=label.endsWith(" · ")?label+"4 layers":label;
            SliderGuide.Entry e=SliderGuide.lookup(probe);
            if(e==null){missing.add(label);continue;}
            used.add(e);
            check(e.what.length()>=20&&e.right.length()>=8&&e.left.length()>=8,"thin entry: "+label);
            String body=ContextualLearning.sliderHelp(probe,"LOW","HIGH");
            check(body.contains("WHAT IT DOES")&&body.contains("SLIDE RIGHT (toward HIGH)")&&body.contains("SLIDE LEFT (toward LOW)"),"bad layout: "+label);
            check(body.contains("LOW to HIGH"),"range missing: "+label);
        }
        check(missing.isEmpty(),"sliders without a dedicated explanation: "+missing);
        // Unit-specific entries must not collide.
        check(SliderGuide.lookup("Line width · mm")!=SliderGuide.lookup("Line width · %"),"Line width mm/% share an entry");
        check(SliderGuide.lookup("2 · Shadow emphasis · %")==SliderGuide.lookup("3 · Shadow emphasis · %"),"step prefix not ignored");
        // Unknown sliders still fall back to the generic help, never null.
        check(SliderGuide.explain("Unknown slider","0","1")==null,"unknown slider matched");
        check(ContextualLearning.sliderHelp("Unknown slider","1","5").contains("1 to 5"),"fallback range missing");
        // Tooltips stay short (existing contract), detail lives only in the dialog.
        check(VinylPlainLanguage.help("Spacing").length()<180,"tooltip grew");
        System.out.println("PASS slider guide: "+labels.size()+" slider labels, "+used.size()+" distinct explanations, unit keys, fallback");
    }
}
