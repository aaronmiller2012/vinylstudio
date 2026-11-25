package com.halftonestudio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Host regression of the v3 streaming project archive path, independent of Android. */
public final class V3ProjectStreamingTest {
    private static final byte[] PNG=Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAIAAAASFvFNAAAAFUlEQVR4nGPUSNnCwMDAwMDAxAADABYwAUTo8FNRAAAAAElFTkSuQmCC");
    private static void check(boolean result,String reason){if(!result)throw new AssertionError(reason);}
    private static byte[] zip(String[] names,byte[][] contents) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream archive=new ZipOutputStream(out)){
            for(int i=0;i<names.length;i++){
                ZipEntry entry=new ZipEntry(names[i]);archive.putNextEntry(entry);
                archive.write(contents[i]);archive.closeEntry();
            }
        }
        return out.toByteArray();
    }
    private static byte[] makeProject(byte[] photo,byte[] original,byte[] settings)throws IOException{
        if(original==null)return zip(new String[]{"photo.png","settings.json"},new byte[][]{photo,settings});
        return zip(new String[]{"photo.png","original.png","settings.json"},
            new byte[][]{photo,original,settings});
    }
    private static ProjectArchiveReader.StagedProject read(byte[] bytes,Path dir)throws IOException{
        try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(bytes))){
            return ProjectArchiveReader.readToFiles(in,dir.toFile());
        }
    }
    private static long leftovers(Path dir)throws IOException {
        try(java.util.stream.Stream<Path> stream=Files.list(dir)){
            return stream.filter(p->p.getFileName().toString().startsWith("vinyl-v3-reopen-")).count();
        }
    }
    public static void main(String[] args)throws Exception {
        Path cache=Files.createTempDirectory("vinyl-v3-test-");
        try{
            byte[] options="{\"schema\":3,\"workflowStep\":5}".getBytes(StandardCharsets.UTF_8);
            byte[] standard=makeProject(PNG,PNG,options);
            ProjectArchiveReader.StagedProject staged=read(standard,cache);
            check(staged.photo.isFile()&&staged.original.isFile(),"Project images not staged");
            check(Arrays.equals(PNG,Files.readAllBytes(staged.photo.toPath())),"Photo bytes changed");
            check(Arrays.equals(PNG,Files.readAllBytes(staged.original.toPath())),"Original bytes changed");
            check(ProjectImageFileCompare.equal(staged.photo,staged.original),"Identical image reread incorrectly");
            check(Arrays.equals(options,staged.settings),"Settings changed");
            ProjectEncodedOriginal.Stage originalStage=ProjectEncodedOriginal.stage(
                cache.toFile(),staged.original,staged.originalFormat);
            ProjectEncodedOriginal.commit(cache.toFile(),originalStage);
            check(Arrays.equals(PNG,Files.readAllBytes(ProjectEncodedOriginal.active(cache.toFile()).toPath())),
                "Full resolution encoded original was not preserved");
            staged.close();staged.close();
            check(leftovers(cache)==0,"Project spools survive successful close");
            byte[] simple=makeProject(PNG,null,options);
            try(ProjectArchiveReader.StagedProject single=read(simple,cache)){
                check(single.original==null,"An unsupplied original was invented");
                check(!ProjectImageFileCompare.equal(single.photo,null),"Null image equality is wrong");
            }
            check(leftovers(cache)==0,"Single-image spool leaked");
            byte[] badPng=PNG.clone();badPng[45]^=1;
            try{read(makeProject(PNG,badPng,options),cache);
                throw new AssertionError("Corrupt PNG data was accepted");}
            catch(IOException expected){}
            check(leftovers(cache)==0,"Corrupted original leaked previously staged photo");
            try{read(makeProject(PNG,null,"[1,2]".getBytes(StandardCharsets.UTF_8)),cache);
                throw new AssertionError("Nonobject settings accepted");}
            catch(IOException expected){}
            check(leftovers(cache)==0,"Malformed settings left spool files");
            try{read(zip(new String[]{"photo.png","../escape","settings.json"},
                new byte[][]{PNG,PNG,options}),cache);
                throw new AssertionError("ZIP traversal entry accepted");}
            catch(IOException expected){}
            check(leftovers(cache)==0,"Unsafe archive leaked spool");
            try{read(zip(new String[]{"photo.png","photo.jpg","settings.json"},
                new byte[][]{PNG,PNG,options}),cache);
                throw new AssertionError("Mislabeled image accepted");}
            catch(IOException expected){}
            check(leftovers(cache)==0,"Extension mismatch leaked spool");
            byte[] oversized=new byte[(int)ProjectArchiveReader.MAX_SETTINGS_ENTRY+1];
            try{read(makeProject(PNG,null,oversized),cache);
                throw new AssertionError("Over-limit settings accepted");}
            catch(IOException expected){}
            check(leftovers(cache)==0,"Size rejection leaked spool");
            Thread.currentThread().interrupt();
            try{read(simple,cache);throw new AssertionError("Interrupted project open proceeded");}
            catch(IOException expected){}
            finally{Thread.interrupted();}
            check(leftovers(cache)==0,"Canceled project open leaked spool");
            long now=System.currentTimeMillis();
            File old=cache.resolve("vinyl-v3-reopen-obsolete.png").toFile();
            File fresh=cache.resolve("vinyl-v3-reopen-active.png").toFile();
            File abandonedZip=cache.resolve("vinyl-cut-abandoned.pending.zip").toFile();
            File liveZip=cache.resolve("vinyl-cut-live.pending.zip").toFile();
            File unrelated=cache.resolve("user-reference.png").toFile();
            check(old.createNewFile()&&fresh.createNewFile()&&unrelated.createNewFile()&&
                abandonedZip.createNewFile()&&liveZip.createNewFile(),"Test setup failed");
            check(old.setLastModified(now-48L*3600*1000),"Cannot age temp file");
            check(abandonedZip.setLastModified(now-48L*3600*1000),"Cannot age export stage");
            check(unrelated.setLastModified(now-48L*3600*1000),"Cannot age unrelated file");
            check(ProjectTempJanitor.prune(cache.toFile(),now)==2,"Only old owned scratch may be removed");
            check(!old.exists()&&!abandonedZip.exists()&&fresh.exists()&&liveZip.exists()&&unrelated.exists(),
                "Janitor removed live/user file or retained abandoned export");
            System.out.println("PASS V3 staged archive: fidelity, equal originals, corruption, traversal, sizing, cancellation, cleanup");
        }finally{
            try(java.util.stream.Stream<Path> all=Files.walk(cache)){
                all.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});
            }
        }
    }
}
