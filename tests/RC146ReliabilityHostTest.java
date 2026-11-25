package com.halftonestudio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** One consolidated release: CRC fallbacks, invalid settings, staged export,
 * interrupted delivery, and bounded disk work all run in a plain JVM.
 */
public final class RC146ReliabilityHostTest {
    private static void yes(boolean result,String cause){if(!result)throw new AssertionError(cause);}
    private static final byte[] PNG=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAIAAAASFvFNAAAAFUlEQVR4nGPUSNnCwMDAwMDAxAADABYwAUTo8FNRAAAAAElFTkSuQmCC");
    private static byte[] image(String label)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write(PNG,0,PNG.length-12);
        byte[] data=("VinylLabel\0image-"+label).getBytes(StandardCharsets.ISO_8859_1);
        java.io.DataOutputStream b=new java.io.DataOutputStream(out);
        b.writeInt(data.length);byte[] name={'t','E','X','t'};b.write(name);b.write(data);
        CRC32 crc=new CRC32();crc.update(name);crc.update(data);b.writeInt((int)crc.getValue());
        b.write(PNG,PNG.length-12,12);return out.toByteArray();
    }
    private static void writeProject(OutputStream out,String label,String settings)throws IOException {
        ZipOutputStream zip=new ZipOutputStream(out);
        zip.putNextEntry(new ZipEntry("photo.png"));zip.write(image(label));zip.closeEntry();
        zip.putNextEntry(new ZipEntry("settings.json"));zip.write(settings.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        zip.finish();
    }
    private static void flipCompressed(File file,String entry) throws Exception {
        byte[] content=Files.readAllBytes(file.toPath());boolean found=false;
        for(int i=0;i<content.length-35;i++)if(content[i]==0x50&&content[i+1]==0x4b&&content[i+2]==3&&content[i+3]==4){
            int nameSize=(content[i+26]&255)|((content[i+27]&255)<<8);
            int extraSize=(content[i+28]&255)|((content[i+29]&255)<<8);
            if(i+30+nameSize+extraSize+6>=content.length)continue;
            String name=new String(content,i+30,nameSize,StandardCharsets.UTF_8);
            if(name.equals(entry)){
                content[i+30+nameSize+extraSize+5]^=0x20;found=true;break;
            }
        }
        yes(found,"Failed to find compressed ZIP member");Files.write(file.toPath(),content);
    }
    private static void projects(Path home)throws Exception {
        StudioProjectLibrary library=new StudioProjectLibrary(home.toFile());
        StudioProjectLibrary.Entry old=library.save(null,"A","Shapes",null,
            out->writeProject(out,"old","{\"schema\":29,\"marks\":[[1,2],[3,4]]}"));
        StudioProjectLibrary.Entry current=library.save(old.id,"B","Shapes",null,
            out->writeProject(out,"new","{\"schema\":29,\"marks\":[[2,3]]}"));
        File newest=library.archive(current.id);flipCompressed(newest,"settings.json");
        File rollback=library.archive(current.id);
        yes(!newest.equals(rollback),"Latest corrupt CRC must not be selected");
        try(ZipInputStream in=new ZipInputStream(new FileInputStream(rollback))){
            byte[] encoded=ProjectArchiveReader.read(in).photo;
            String text=new String(encoded,StandardCharsets.ISO_8859_1);
            yes("png".equals(ProjectImageSignature.format(encoded))&&text.contains("VinylLabel\0image-old"),
                "Valid older archive not restored");
        }
        yes("A".equals(library.get(current.id).name),"Recovered metadata must match recovered archive");
        try{library.save(old.id,"Invalid","Lines",null,
            out->writeProject(out,"invalid","{\"schema\":29,\"broken\": ]}"));
            throw new AssertionError("Invalid JSON accepted");
        }catch(IOException expected){}
        yes(rollback.equals(library.archive(old.id)),"Rejected update overwrote prior revision");
        System.out.println("PASS saved-project payload CRC, valid rollback and malformed JSON rejection");
    }
    private static void stagedZip(Path home)throws Exception {
        byte[] published;
        try(StagedCutZip stage=new StagedCutZip(home.toFile())){
            try(ZipOutputStream zip=new ZipOutputStream(stage.openStream())){
                zip.putNextEntry(new ZipEntry("01-cut.svg"));
                zip.write("<svg xmlns=\"http://www.w3.org/2000/svg\"><path d=\"M0 0L1 1\"/></svg>".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
                zip.putNextEntry(new ZipEntry("layer-manifest.csv"));
                zip.write("order,file,color_hex\n1,01-cut.svg,#000000\n".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            }
            stage.verify();ByteArrayOutputStream destination=new ByteArrayOutputStream();
            stage.copyTo(destination);published=destination.toByteArray();
        }
        try(ZipInputStream zin=new ZipInputStream(new ByteArrayInputStream(published))){
            yes(zin.getNextEntry().getName().endsWith(".svg"),"Export is not a usable ZIP");
        }
        boolean unsafe=false;
        try(StagedCutZip stage=new StagedCutZip(home.toFile())){
            try(ZipOutputStream zip=new ZipOutputStream(stage.openStream())){
                zip.putNextEntry(new ZipEntry("layer-manifest.csv"));
                zip.write("order,file\n1,missing.svg\n".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            }
            try{stage.verify();}catch(IOException ex){unsafe=true;}
        }
        yes(unsafe,"Empty export should never be published");
        try(StagedCutZip stage=new StagedCutZip(home.toFile())){
            try(ZipOutputStream zip=new ZipOutputStream(stage.openStream())){
                zip.putNextEntry(new ZipEntry("01-cut.svg"));zip.write("<svg><path/></svg>".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            }
            Field f=StagedCutZip.class.getDeclaredField("file");f.setAccessible(true);
            flipCompressed((File)f.get(stage),"01-cut.svg");
            boolean caught=false;try{stage.verify();}catch(IOException ex){caught=true;}
            yes(caught,"Export CRC corruption passed prepublication gate");
        }
        System.out.println("PASS staged cut ZIP, manifest verification, damaged CRC and cleanup");
    }
    private static void stagedDocuments(Path home)throws Exception {
        try(StagedDocumentFile doc=new StagedDocumentFile(home.toFile())){
            try(OutputStream out=doc.openStream()){
                out.write("%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII));
            }
            doc.verify(StagedDocumentFile.Kind.PDF);
            ByteArrayOutputStream publish=new ByteArrayOutputStream();doc.copyTo(publish);
            yes(publish.toString("UTF-8").contains("%%EOF"),"PDF staging failed");
        }
        try(StagedDocumentFile doc=new StagedDocumentFile(home.toFile())){
            try(OutputStream out=doc.openStream()){
                out.write(java.util.Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGOI0qgAAAHaAPt+G3/NAAAAAElFTkSuQmCC"));
            }
            doc.verify(StagedDocumentFile.Kind.PNG);
            Field p=StagedDocumentFile.class.getDeclaredField("file");p.setAccessible(true);
            File file=(File)p.get(doc);
            try(RandomAccessFile raf=new RandomAccessFile(file,"rw")){
                raf.seek(50);int bit=raf.read();raf.seek(50);raf.write(bit^0x10);
            }
            boolean corrupt=false;
            try{doc.verify(StagedDocumentFile.Kind.PNG);}catch(IOException expected){corrupt=true;}
            yes(corrupt,"Invalid PNG chunk CRC must reject publication");
        }
        try(StagedDocumentFile doc=new StagedDocumentFile(home.toFile())){
            try(OutputStream out=doc.openStream()){out.write("%PDF-1.4\nmissing trailer".getBytes(StandardCharsets.US_ASCII));}
            boolean failed=false;try{doc.verify(StagedDocumentFile.Kind.PDF);}catch(IOException expected){failed=true;}
            yes(failed,"Truncated PDF must not be published");
        }
        System.out.println("PASS PDF/PNG staging, signature/trailer checks and incomplete document rejection");
    }
    private static void boundedQueue()throws Exception {
        try(StudioRenderController c=new StudioRenderController()){
            ExecutorService io=c.io();CountDownLatch began=new CountDownLatch(1),finish=new CountDownLatch(1);
            io.execute(()->{began.countDown();try{finish.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
            yes(began.await(2,TimeUnit.SECONDS),"Disk lane did not start");
            for(int i=0;i<32;i++)io.execute(()->{});
            boolean rejected=false;try{io.execute(()->{});}catch(RejectedExecutionException expected){rejected=true;}
            yes(rejected,"Disk queue exceeded its bound");
            finish.countDown();
            Future<?> recovered=io.submit(()->{});recovered.get(5,TimeUnit.SECONDS);
        }
        System.out.println("PASS bounded disk queue and clean recovery");
    }
    public static void main(String[] args)throws Exception {
        Path work=Files.createTempDirectory("vinyl-rc146-");
        try{projects(work);stagedZip(work);stagedDocuments(work);boundedQueue();
            JsonSyntaxVerifier.requireObject("{\"foo\":true,\"bar\":[null,3.5,{\"x\":\"a\\u0041\"}]}".getBytes(StandardCharsets.UTF_8));
            System.out.println("PASS RC146 consolidation host tests");
        }finally{try(java.util.stream.Stream<Path> walk=Files.walk(work)){
            walk.sorted(Comparator.reverseOrder()).forEach(path->{try{Files.deleteIfExists(path);}catch(IOException ignored){}});
        }}
    }
}
