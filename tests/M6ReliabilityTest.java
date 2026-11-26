package com.halftonestudio;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class M6ReliabilityTest {
    static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
    static InputStream zip(String[][] rows)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        try(ZipOutputStream z=new ZipOutputStream(b)){
            for(String[] row:rows){z.putNextEntry(new ZipEntry(row[0]));z.write(row[1].getBytes(java.nio.charset.StandardCharsets.UTF_8));z.closeEntry();}
        }
        return new ByteArrayInputStream(b.toByteArray());
    }
    static void grid(){
        for(float min:new float[]{.254f,.4f,.8f,1.2f})for(float gap:new float[]{.25f,.5f,1f}){
            CutRepairGrid g=CutRepairGrid.plan(20,30,min,gap,2_500_000);
            check(g!=null,"small grid refused");
            check(g.pitch<=(min+.02)/7.0&&g.pitch<=(gap+.02)/3.0,"unsafe sampling pitch");
            check((long)g.width*g.height<=2_500_000,"pixel budget");
            check(g.width>=Math.ceil(20/(double)g.pitch)+2,"missing border");
        }
        check(CutRepairGrid.plan(279.4f,279.4f,.254f,.5f,2_500_000)==null,"unsafe coarse fallback");
        check(CutRepairGrid.plan(50,50,.8f,.5f,2_500_000)!=null,"normal repair disabled");
        check(CutRepairGrid.plan(1000,1,.254f,.5f,100)==null,"skinny grid bypassed budget");
        try{CutRepairGrid.plan(Float.NaN,1,.8f,.5f,100);throw new AssertionError("NaN accepted");}catch(IllegalArgumentException expected){}
    }
    static void sharing()throws Exception{
        Path root=Files.createTempDirectory("m6-share");
        try{
            SharedExportFiles.CutShare r=SharedExportFiles.extractCutShare(zip(new String[][]{{"sheet.svg","<svg/>"},{"nested/NOT-CUT-QUALIFIED.txt","unsafe"},{"CRICUT-MAT.txt","12 x 24 required"}}),root.toFile());
            check(r.reviewOnly()&&"unsafe".equals(r.warning)&&"12 x 24 required".equals(r.matNote),"warnings lost");
            check(r.sheets.size()==1&&r.sheets.get(0).getName().equals("sheet.svg"),"report mixed into cutter sheets");
            SharedExportFiles.CutShare q=SharedExportFiles.extractCutShare(zip(new String[][]{{"sheet.dxf","0\nEOF"},{"CRICUT-MAT.txt","mat"}}),root.toFile());
            check(!q.reviewOnly()&&"mat".equals(q.matNote),"clean share blocked");
            SharedExportFiles.CutShare e=SharedExportFiles.extractCutShare(zip(new String[][]{{"sheet.svg","<svg/>"},{"NOT-CUT-QUALIFIED.txt",""}}),root.toFile());
            check(e.reviewOnly(),"empty warning bypassed review");
            for(String[][] rows:new String[][][]{{{"sheet.svg","x"},{"NOT-CUT-QUALIFIED.txt","x"},{"nested/not-cut-qualified.txt","y"}},{{"sheet.svg","x"},{"CRICUT-MAT.txt","x".repeat(SharedExportFiles.MAX_REPORT_BYTES+1)}}}){
                int before=root.toFile().listFiles().length;
                try{SharedExportFiles.extractCutShare(zip(rows),root.toFile());throw new AssertionError("invalid report accepted");}catch(IOException expected){}
                check(root.toFile().listFiles().length==before,"failed share leaked temp files");
            }
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(root)){for(Path p:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(p);}}
    }
    static void memory(){
        ParallelGeometry.forceSequential=false;
        ParallelGeometry.configureMemoryBudget(false,512L*1024*1024);check(!ParallelGeometry.enabled(),"small heap parallel");
        ParallelGeometry.configureMemoryBudget(true,1024L*1024*1024);check(!ParallelGeometry.enabled(),"low RAM parallel");
        ParallelGeometry.configureMemoryBudget(false,1024L*1024*1024);check(ParallelGeometry.enabled()==(ParallelGeometry.PARALLELISM>1),"normal scheduling changed");
        ParallelGeometry.forceSequential=true;check(!ParallelGeometry.enabled(),"test override broken");ParallelGeometry.forceSequential=false;
    }
    public static void main(String[] args)throws Exception{grid();sharing();memory();System.out.println("PASS M6 safe repair grid, share reports and memory scheduling");}
}
