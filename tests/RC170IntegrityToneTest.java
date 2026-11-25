package com.halftonestudio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Executable RC170 regression: masked fishnet tone, SAF replacement, lifecycle, restore cache. */
public final class RC170IntegrityToneTest {
    static void ok(boolean value,String description){if(!value)throw new AssertionError(description);}
    public static void main(String[] args) throws Exception {
        final int w=94,h=98;
        int[] argb=new int[w*h];boolean[] allowed=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float dx=(x-47)/31f,dy=(y-49)/28f;
            boolean valid=dx*dx+dy*dy<1f;
            allowed[y*w+x]=valid;
            // Very bright excluded background, dark photographic subject with detail.
            argb[y*w+x]=valid?(0xff000000|((35+(x*5+y*3)%145)*0x010101)):0xffffffff;
        }
        StyleKit.Source src=new StyleKit.Source(argb,w,h,allowed,125f);
        NineLayerCraftPatternEngine.Options options=new NineLayerCraftPatternEngine.Options();
        options.pattern=NineLayerCraftPatternEngine.FISHNET;
        options.widthMm=125f;options.allowed=allowed;options.layers=9;
        float pitch=NineLayerCraftPatternEngine.textilePitch(src,options);
        ArrayList<NineLayerCraftPatternEngine.Cell> all=NineLayerCraftPatternEngine.createCells(src,options,pitch);
        int parents=0,valid=0;
        for(NineLayerCraftPatternEngine.Cell c:all){
            if(c.perimeter){
                parents++;
                ok(!src.validAt(c.x,c.y),"perimeter helper is not outside mask");
                float x=NineLayerCraftPatternEngine.fishnetNodeX(c.toneRow,c.toneCol,pitch);
                float y=NineLayerCraftPatternEngine.fishnetNodeY(c.toneRow,c.toneCol,pitch);
                ok(src.validAt(x,y),"perimeter parent must have a valid tone child");
                float expected=NineLayerCraftPatternEngine.textileTone(src,x,y,pitch);
                ok(Math.abs(expected-c.lum)<0.00001f,"outside background changed fishnet tone");
            }else{valid++;ok(src.validAt(c.x,c.y),"inside node marked as helper");}
        }
        ok(parents>0,"missing perimeter helper parents");
        int[] palette={0xff191919,0xff303030,0xff454545,0xff606060,0xff757575,
            0xff909090,0xffa5a5a5,0xffbdbdbd,0xffd4d4d4};
        NineLayerCraftPatternEngine.assignQuantiles(all,palette,9);
        int[] n=new int[9];
        Map<Long,NineLayerCraftPatternEngine.Cell> indexed=new HashMap<>();
        for(NineLayerCraftPatternEngine.Cell c:all)if(!c.perimeter){
            n[c.layer]++;
            indexed.put((((long)c.row)<<32)^(c.col&0xffffffffL),c);
        }
        int min=valid,max=0;
        for(int layer=1;layer<=8;layer++){
            min=Math.min(min,n[layer]);max=Math.max(max,n[layer]);
        }
        ok(max-min<=1,"perimeter cells skewed subject quantile population");
        for(NineLayerCraftPatternEngine.Cell c:all)if(c.perimeter){
            NineLayerCraftPatternEngine.Cell child=indexed.get((((long)c.toneRow)<<32)^(c.toneCol&0xffffffffL));
            ok(child!=null&&child.layer==c.layer,"perimeter color layer differs from subject anchor");
        }
        System.out.println("PASS fishnet masks outside color; "+parents+" margin parents excluded from "+valid+" quantile cells");
        // Simulate SAF document replacement with an actual file; code chooses wt, never wa.
        Path target=Files.createTempFile("rc170-document-",".zip");
        try {
            Files.write(target,"very long stale document data".getBytes(StandardCharsets.UTF_8));
            String[] chosen={null};
            try(OutputStream out=LibraryDocumentStreams.truncate(mode->{
                chosen[0]=mode;
                return new FileOutputStream(target.toFile(),"wa".equals(mode));
            })){
                out.write("zip".getBytes(StandardCharsets.UTF_8));
            }
            ok("wt".equals(chosen[0]),"SAF write must explicitly truncate");
            ok("zip".equals(Files.readString(target)),"replacement left bytes from the old document");
            try{LibraryDocumentStreams.truncate(mode->null);throw new AssertionError("null SAF stream accepted");}
            catch(FileNotFoundException expected){}
        }finally{Files.deleteIfExists(target);}
        PendingLibraryExport waiting=new PendingLibraryExport();
        waiting.set("example-uuid-id");
        String saved=waiting.save();
        PendingLibraryExport recreated=new PendingLibraryExport();
        recreated.restore(saved);
        ok("example-uuid-id".equals(recreated.consume()),"picker result lost across recreation");
        ok(recreated.consume()==null,"picker result consumed twice");
        LifecycleUiGate gate=new LifecycleUiGate();
        ArrayList<Runnable> delayed=new ArrayList<>();
        AtomicBoolean ran=new AtomicBoolean(false);
        ok(gate.dispatch(task->{delayed.add(task);return true;},()->ran.set(true)),"post unexpectedly rejected");
        gate.close();
        delayed.forEach(Runnable::run);
        ok(!ran.get(),"queued UI post ran after destroyed activity");
        ok(!gate.dispatch(task->{delayed.add(task);return true;},()->ran.set(true)),"late UI callback accepted");
        ok("Project failed".equals(FailureText.describe(new IOException(),"Project failed")),"missing error leaked null");
        ok("Project failed".equals(FailureText.describe(new IOException("java.lang.IllegalStateException"),"Project failed")),"technical error exposed");
        ok(FailureText.describe(new IOException("Cannot save: "+"project conflict ".repeat(15)),"fallback").endsWith("…"),
            "useful long error should truncate, not disappear");
        ok(LoadValueConstraints.finiteRange(Double.NaN,0,1,.65f)==.65f,"NaN not repaired");
        ok(LoadValueConstraints.finiteRange(-100,25,90,60)==25,"minimum clamp missing");
        ok(LoadValueConstraints.finiteRange(100,25,90,60)==90,"maximum clamp missing");
        System.out.println("PASS SAF truncation, picker state, dropped queued UI posts, friendly errors and numeric ranges");
    }
}
