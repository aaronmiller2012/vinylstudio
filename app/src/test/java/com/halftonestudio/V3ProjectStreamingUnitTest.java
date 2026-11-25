package com.halftonestudio;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** JVM unit tests run by Gradle :app:testDebugUnitTest and :app:testReleaseUnitTest. */
public final class V3ProjectStreamingUnitTest {
    private static final byte[] PNG=Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAIAAAASFvFNAAAAFUlEQVR4nGPUSNnCwMDAwMDAxAADABYwAUTo8FNRAAAAAElFTkSuQmCC");
    private static byte[] project(byte[] image) throws IOException {
        ByteArrayOutputStream target=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(target)){
            zip.putNextEntry(new ZipEntry("photo.png"));zip.write(image);zip.closeEntry();
            zip.putNextEntry(new ZipEntry("original.png"));zip.write(image);zip.closeEntry();
            zip.putNextEntry(new ZipEntry("settings.json"));
            zip.write("{\"schema\":3}".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        }
        return target.toByteArray();
    }
    @Test public void losslessSpoolDeletesImagesOnClose() throws Exception {
        Path dir=Files.createTempDirectory("v3-junit-");
        try {
            ProjectArchiveReader.StagedProject snap;
            try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(project(PNG)))){
                snap=ProjectArchiveReader.readToFiles(zip,dir.toFile());
            }
            assertArrayEquals(PNG,Files.readAllBytes(snap.photo.toPath()));
            assertArrayEquals(PNG,Files.readAllBytes(snap.original.toPath()));
            assertTrue(ProjectImageFileCompare.equal(snap.photo,snap.original));
            snap.close();snap.close();
            assertFalse(snap.photo.exists());assertFalse(snap.original.exists());
        }finally{try(java.util.stream.Stream<Path> all=Files.walk(dir)){
            all.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});
        }}
    }
    @Test public void corruptedPngCannotCreateAnImport() throws Exception {
        Path dir=Files.createTempDirectory("v3-badpng-");
        try{
            byte[] broken=PNG.clone();broken[45]^=1;
            try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(project(broken)))){
                try{ProjectArchiveReader.readToFiles(zip,dir.toFile());fail("corrupt PNG accepted");}
                catch(IOException expected){}
            }
            try(java.util.stream.Stream<Path> entries=Files.list(dir)){
                assertEquals(0,entries.count());
            }
        }finally{try(java.util.stream.Stream<Path> all=Files.walk(dir)){
            all.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});
        }}
    }
    @Test public void orphanCleanerLeavesFreshAndUnrelatedFiles() throws Exception {
        Path dir=Files.createTempDirectory("v3-janitor-");
        try{
            long now=System.currentTimeMillis();
            File stale=dir.resolve("vinyl-v3-reopen-old.png").toFile();
            File live=dir.resolve("vinyl-v3-reopen-new.png").toFile();
            File project=dir.resolve("real-user-photo.png").toFile();
            assertTrue(stale.createNewFile());assertTrue(live.createNewFile());
            assertTrue(project.createNewFile());
            assertTrue(stale.setLastModified(now-48L*3600*1000));
            assertTrue(project.setLastModified(now-48L*3600*1000));
            assertEquals(1,ProjectTempJanitor.prune(dir.toFile(),now));
            assertFalse(stale.exists());assertTrue(live.exists());assertTrue(project.exists());
        }finally{try(java.util.stream.Stream<Path> all=Files.walk(dir)){
            all.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});
        }}
    }
}
