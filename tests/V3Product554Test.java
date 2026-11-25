package com.halftonestudio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** Build 554 host regressions: project format 2, kept cut sheets, weed score, camera policy. */
public final class V3Product554Test {
    private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
    private static final byte[] PNG=Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAIAAAASFvFNAAAAFUlEQVR4nGPUSNnCwMDAwMDAxAADABYwAUTo8FNRAAAAAElFTkSuQmCC");
    private static StudioProjectLibrary.ArchiveWriter archive(String json){
        return out->StudioProjectArchiveWriter.write(new ZipOutputStream(out),
            s->{try{s.write(PNG);return true;}catch(IOException e){return false;}},
            s->{try{s.write(PNG);return true;}catch(IOException e){return false;}},json);
    }
    private static byte[] zipBytes(String[][] entries) throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        try(ZipOutputStream z=new ZipOutputStream(b)){
            for(String[] e:entries){z.putNextEntry(new ZipEntry(e[0]));z.write(e[1].getBytes(StandardCharsets.UTF_8));z.closeEntry();}
        }
        return b.toByteArray();
    }
    /** Rewrite a project ZIP, replacing one entry's bytes (valid CRC, so only the manifest can tell). */
    private static void rewrite(File zip,String name,byte[] replacement,boolean drop,String[] extra) throws IOException {
        Map<String,byte[]> entries=new LinkedHashMap<>();
        try(ZipInputStream in=new ZipInputStream(new FileInputStream(zip))){ZipEntry e;while((e=in.getNextEntry())!=null)entries.put(e.getName(),in.readAllBytes());}
        if(drop)entries.remove(name);else if(replacement!=null)entries.put(name,replacement);
        if(extra!=null)entries.put(extra[0],extra[1].getBytes(StandardCharsets.UTF_8));
        try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(zip))){
            for(Map.Entry<String,byte[]> e:entries.entrySet()){out.putNextEntry(new ZipEntry(e.getKey()));out.write(e.getValue());out.closeEntry();}
        }
    }
    private static boolean verifies(File zip){
        try{StudioProjectLibrary.verifyProjectArchive(zip);return true;}catch(IOException e){return false;}
    }
    private static void deleteTree(File f){File[] c=f.listFiles();if(c!=null)for(File x:c)deleteTree(x);f.delete();}

    static void projectFormat2() throws Exception {
        File dir=Files.createTempDirectory("vinyl-554-format").toFile();
        try{
            File zip=new File(dir,"p.zip");
            try(FileOutputStream out=new FileOutputStream(zip)){archive("{\"schema\":14}").write(out);}
            check(verifies(zip),"fresh format-2 project must verify");
            byte[] tampered="{\"schema\":14,\"x\":1}".getBytes(StandardCharsets.UTF_8);
            File a=new File(dir,"a.zip");Files.copy(zip.toPath(),a.toPath());rewrite(a,"settings.json",tampered,false,null);
            check(!verifies(a),"altered settings with valid CRC must fail the manifest");
            File b=new File(dir,"b.zip");Files.copy(zip.toPath(),b.toPath());rewrite(b,"original.png",null,true,null);
            check(!verifies(b),"missing listed entry must fail");
            File c=new File(dir,"c.zip");Files.copy(zip.toPath(),c.toPath());rewrite(c,null,null,false,new String[]{"notes.txt","hi"});
            check(!verifies(c),"unlisted entry must fail when a manifest exists");
            File legacy=new File(dir,"legacy.zip");Files.copy(zip.toPath(),legacy.toPath());rewrite(legacy,ProjectManifest.ENTRY,null,true,null);
            check(verifies(legacy),"format-1 project without manifest stays valid");
            File legacyExtra=new File(dir,"legacy2.zip");Files.copy(legacy.toPath(),legacyExtra.toPath());rewrite(legacyExtra,null,null,false,new String[]{"notes.txt","hi"});
            check(verifies(legacyExtra),"format-1 tolerance for small unknown entries is unchanged");
            for(String bad:new String[]{"{}","{\"format\":1,\"engineBuild\":1,\"entries\":{}}","{\"format\":2,\"engineBuild\":1,\"entries\":{\"a\":{\"sha256\":\"zz\",\"bytes\":1}}}"}){
                try{ProjectManifest.parse(bad.getBytes(StandardCharsets.UTF_8));throw new AssertionError("accepted "+bad);}catch(IOException expected){}
            }
            // Older builds read manifest.json as an unknown entry limited to 64 KB: stay far below.
            try(ZipFile z=new ZipFile(zip)){check(z.getEntry(ProjectManifest.ENTRY).getSize()<ProjectArchiveReader.MAX_UNKNOWN_ENTRY,"manifest must fit older readers");}
        }finally{deleteTree(dir);}
    }

    static void keptCutSheets() throws Exception {
        File files=Files.createTempDirectory("vinyl-554-cuts").toFile();
        try{
            byte[] cuts=zipBytes(new String[][]{{"01 Black.svg","<svg/>"},{"cut-complexity.csv","x"}});
            LastVerifiedExport.store(files,new ByteArrayInputStream(cuts),7,"proc0001-r3");
            check(LastVerifiedExport.matching(files,7,"proc0001-r3")!=null,"same edit matches");
            check(LastVerifiedExport.matching(files,8,"proc0001-r3")==null,"later edit must not match");
            check(LastVerifiedExport.matching(files,7,"proc0001-r4")==null,"different photo must not match");
            check(LastVerifiedExport.matching(files,7,"")==null,"no photo token never matches");
            try{LastVerifiedExport.store(files,new ByteArrayInputStream(zipBytes(new String[][]{{"a.txt","x"}})),9,"proc0001-r5");throw new AssertionError("non-cut ZIP kept");}
            catch(IOException expected){}
            check(LastVerifiedExport.matching(files,7,"proc0001-r3")==null,"failed store must clear the previous snapshot");
            LastVerifiedExport.store(files,new ByteArrayInputStream(cuts),7,"proc0001-r3");
            StudioProjectLibrary lib=new StudioProjectLibrary(files);
            StudioProjectLibrary.Entry e=lib.save(null,"Cuts","Lines",null,archive("{\"schema\":14}"),LastVerifiedExport.matching(files,7,"proc0001-r3"));
            File kept=new StudioProjectLibrary(files).exportedCuts(e.id);
            check(kept!=null&&Arrays.equals(Files.readAllBytes(kept.toPath()),cuts),"cut sheets kept byte-identical with the revision");
            lib.save(e.id,"Cuts","Lines",null,archive("{\"schema\":14,\"v\":2}"),null);
            check(new StudioProjectLibrary(files).exportedCuts(e.id)==null,"a later save without a matching export must not inherit stale cut sheets");
            check(lib.list().size()==1,"sidecar must not affect listing");
        }finally{deleteTree(files);}
    }

    static void weed(){
        List<float[]> loops=new ArrayList<>();List<double[]> circles=new ArrayList<>();
        loops.add(new float[]{0,0,100,0,100,100,0,100});
        WeedDifficulty.Score easy=WeedDifficulty.score(loops,circles,0,300,300,.254,0);
        check("EASY".equals(easy.rating)&&easy.tiny==0,"single large shape is easy: "+easy.value);
        for(int i=0;i<600;i++){float x=(i%30)*9,y=(i/30)*9;loops.add(new float[]{x,y,x+1.5f,y,x+1.5f,y+1.5f,x,y+1.5f});}
        WeedDifficulty.Score hard=WeedDifficulty.score(loops,circles,0,300,300,.254,4);
        check(hard.value>=75&&"EXPERT".equals(hard.rating)&&hard.tiny==600,"dense tiny pieces are expert: "+hard.value);
        check(WeedDifficulty.score(new ArrayList<>(),circles,5,300,300,.254,0).value==0,"draw-only sheet scores 0");
        String csv="sheet,format,a,b,c,d,e,f,g,status,weed_score,weed_rating\n01.svg,SVG,1,1,1,0,0,1,5000,COUNTED,12,EASY\n02.svg,SVG,1,1,1,0,0,1,5000,COUNTED,81,EXPERT\n";
        check("EXPERT (81/100) on 02.svg".equals(WeedDifficulty.hardest(csv)),"hardest sheet summary");
        check(WeedDifficulty.hardest("sheet,status\n01.svg,COUNTED\n")==null,"pre-554 CSV without weed columns");
    }

    static void cameraPolicy() throws Exception {
        check(SharedExportFiles.isCameraCapture(Arrays.asList("camera-123e4567","capture.jpg")),"capture target writable");
        check(!SharedExportFiles.isCameraCapture(Arrays.asList("camera-","capture.jpg")),"bare prefix rejected");
        check(!SharedExportFiles.isCameraCapture(Arrays.asList("camera-1","01 Black.svg")),"only capture.jpg");
        check(!SharedExportFiles.isCameraCapture(Arrays.asList("123e4567","capture.jpg")),"cut-sheet shares stay read-only");
        File root=Files.createTempDirectory("vinyl-554-cam").toFile();
        try{
            File f=SharedExportFiles.newCameraCapture(root);
            check(f.isFile()&&f.length()==0&&f.getParentFile().getName().startsWith("camera-"),"capture file created empty");
            check(SharedExportFiles.resolve(root,SharedExportFiles.relativePath(root,f).split("/")).equals(f.getCanonicalFile()),"capture resolvable");
            check("image/jpeg".equals(SharedExportFiles.mimeType("capture.jpg")),"jpeg mime");
        }finally{deleteTree(root);}
    }

    public static void main(String[] args) throws Exception {
        projectFormat2();keptCutSheets();weed();cameraPolicy();
        System.out.println("PASS 554 project format 2 manifest, kept cut sheets, weed score and camera write policy");
    }
}
