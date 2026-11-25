package com.halftonestudio;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

/** Executes a second JVM to force process death between the two photo writes. */
public final class V3PhotoKillBetweenTest {
    private static void save(File dir,String p,String o)throws Exception{
        PhotoRevisionStore.commit(dir,(photo,original,previous)->{
            Files.write(photo.toPath(),p.getBytes(StandardCharsets.UTF_8));
            Files.write(original.toPath(),o.getBytes(StandardCharsets.UTF_8));
        });
    }
    private static String read(File path)throws Exception{return Files.readString(path.toPath());}
    public static void main(String[] args)throws Exception{
        if(args.length==2&&"KILL".equals(args[0])){
            PhotoRevisionStore.commit(new File(args[1]),(photo,original,previous)->{
                Files.write(photo.toPath(),"HALF_NEW".getBytes(StandardCharsets.UTF_8));
                Runtime.getRuntime().halt(37);
            });return;
        }
        File dir=Files.createTempDirectory("v3-photo-crash-").toFile();
        save(dir,"EDITED_A","ORIGINAL_A");
        Process child=new ProcessBuilder(System.getProperty("java.home")+File.separator+"bin"+File.separator+"java",
                "-cp",System.getProperty("java.class.path"),V3PhotoKillBetweenTest.class.getName(),"KILL",dir.toString())
            .inheritIO().start();
        if(child.waitFor()!=37)throw new AssertionError("kill injection did not reach midpoint");
        PhotoRevisionStore.Pair pair=PhotoRevisionStore.active(dir);
        if(pair==null||!"EDITED_A".equals(read(pair.photo))||!"ORIGINAL_A".equals(read(pair.original)))
            throw new AssertionError("restored a mismatched photo/original revision");
        System.out.println("PASS real process death between photo/original writes preserved matching old pair");
    }
}
