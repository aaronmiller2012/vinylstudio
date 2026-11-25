package com.halftonestudio;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Round trips the production save helpers and immutable library publication. */
public final class V3ProjectSaveRepairTest {
    private static final byte[] PNG=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAIAAAASFvFNAAAAFUlEQVR4nGPUSNnCwMDAwMDAxAADABYwAUTo8FNRAAAAAElFTkSuQmCC");
    private static void yes(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static StudioProjectArchiveWriter.ImageEncoder image(){return out->{try{out.write(PNG);return true;}catch(IOException e){return false;}};}
    private static StudioProjectLibrary.ArchiveWriter writer(File source, byte[] restored,File original){
        return out->{try(ZipOutputStream zip=new ZipOutputStream(new FilterOutputStream(out){@Override public void close()throws IOException{flush();}})){
            StudioProjectArchiveWriter.write(zip,image(),image(),"{\"name\":\"saved\"}",ProjectSaveSupport.portableOriginal(source,restored),original);
        }};
    }
    private static ProjectArchiveReader.ProjectEntries read(File file)throws IOException{
        StudioProjectLibrary.verifyProjectArchive(file);
        try(ZipInputStream in=new ZipInputStream(Files.newInputStream(file.toPath()))){return ProjectArchiveReader.read(in);}
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("vinyl-save-repair-");
        try{
            StudioProjectLibrary library=new StudioProjectLibrary(root.toFile());
            File nativeFile=root.resolve("native.bin").toFile();byte[] nativeData={1,2,3,4,5};byte[] restored={7,8,9};
            // Null is a normal outcome from ProjectSourceSnapshot, not an exceptional case.
            yes(ProjectSaveSupport.portableOriginal(null,null)==null,"No native source is valid");
            StudioProjectLibrary.Entry e;
            try(ProjectSourceSnapshot none=ProjectSourceSnapshot.capture(root.toFile(),null,null)){
                e=library.save(null,"No native original","CMYK",null,writer(none.nativeOriginal,null,none.archiveOriginal));
            }
            File published=library.archive(e.id);
            yes(Arrays.equals(read(published).photo,PNG)&&read(published).nativeSource==null,"Normal project did not round trip");
            e=library.save(e.id,"Restored native bytes","Stencil",null,writer(null,restored,null));
            yes(Arrays.equals(restored,read(library.archive(e.id)).nativeSource),"Restored source omitted");
            Files.write(nativeFile.toPath(),nativeData);
            e=library.save(e.id,"Native file","Line",null,writer(nativeFile,restored,null));
            yes(Arrays.equals(nativeData,read(library.archive(e.id)).nativeSource),"File must take precedence");
            File missing=root.resolve("missing.png").toFile();
            yes(ProjectSaveSupport.portableOriginal(missing,null)==null,"Missing optional file is not fatal");
            try{ProjectSourceSnapshot.capture(root.toFile(),missing,null);throw new AssertionError("Missing required original accepted");}catch(IOException expected){}
            try{library.save(e.id,"Must fail","Line",null,writer(null,null,missing));throw new AssertionError("Required original silently downgraded");}catch(IOException expected){}
            yes("Native file".equals(library.get(e.id).name),"Failed revision replaced saved project");
            File original=root.resolve("original.png").toFile();Files.write(original.toPath(),PNG);
            try(ProjectSourceSnapshot sources=ProjectSourceSnapshot.capture(root.toFile(),original,null)){
                e=library.save(e.id,"Confirmed original","Line",null,writer(null,null,sources.archiveOriginal));
                yes(Arrays.equals(PNG,read(library.archive(e.id)).original),"Encoded original lost");
            }
            List<Throwable> failures=new ArrayList<>();
            byte[] thumb=ProjectSaveSupport.optionalThumbnail(()->{throw new IllegalStateException("render failure");},()->PNG,failures::add);
            yes(Arrays.equals(thumb,PNG)&&failures.size()==1,"Fallback thumbnail not used");
            thumb=ProjectSaveSupport.optionalThumbnail(()->{throw new OutOfMemoryError("fixture");},()->{throw new IOException("fixture");},failures::add);
            yes(thumb==null&&failures.size()==3,"Failed previews must not prevent archive saving");
            e=library.save(e.id,"Without thumbnail","Line",thumb,writer(null,null,null));
            yes(read(library.archive(e.id)).photo!=null&&library.list().size()==1,"Optional preview broke save");
            File external=root.resolve("external.zip").toFile();
            try(OutputStream out=Files.newOutputStream(external.toPath())){writer(null,null,null).write(out);}
            yes(Arrays.equals(read(external).original,PNG),"External ZIP without native original failed");
            System.out.println("PASS project saves: no native source, recovered bytes, native file, optional thumbnails, external ZIP, required-original failure and retained revision");
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(root)){for(Path f:paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new))Files.deleteIfExists(f);}}
    }
}
