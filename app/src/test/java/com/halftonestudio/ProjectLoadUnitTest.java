package com.halftonestudio;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.*;

/** A real disk-backed project save/load corruption test, not a source-regex test. */
public final class ProjectLoadUnitTest {
    private static void sampleArchive(OutputStream stream) throws IOException {
        // The library owns and fsyncs `stream` after this writer returns.
        // finish() writes the central directory without prematurely closing it.
        ZipOutputStream zip=new ZipOutputStream(stream);
        {
            zip.putNextEntry(new ZipEntry("settings.json"));
            zip.write("{\"schema\":1,\"minimumMm\":0.254}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("photo.png"));
            zip.write(java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAIAAAASFvFNAAAAFUlEQVR4nGPUSNnCwMDAwMDAxAADABYwAUTo8FNRAAAAAElFTkSuQmCC"));
            zip.closeEntry();
            zip.finish();zip.flush();
        }
    }
    @Test public void projectReopensFromVerifiedArchiveAndDoesNotLoseIdentity() throws Exception {
        File root=Files.createTempDirectory("vinyl-project-unit-").toFile();
        try {
            StudioProjectLibrary original=new StudioProjectLibrary(root);
            StudioProjectLibrary.Entry entry=original.save(null,"My portrait","Shape Halftone",null,ProjectLoadUnitTest::sampleArchive);
            assertNotNull(entry);
            StudioProjectLibrary reopened=new StudioProjectLibrary(root);
            StudioProjectLibrary.Entry found=reopened.get(entry.id);
            assertNotNull(found);
            assertEquals(entry.id,found.id);
            assertEquals("My portrait",found.name);
            assertTrue(reopened.archive(entry.id).isFile());
        } finally {
            deleteRecursive(root);
        }
    }
    @Test public void fakePngIsRejectedBeforeLibraryPublication() throws Exception {
        File root=Files.createTempDirectory("vinyl-project-corrupt-").toFile();
        try {
            StudioProjectLibrary store=new StudioProjectLibrary(root);
            try {
                store.save(null,"Corrupt","Photo",null,out->{
                    ZipOutputStream zip=new ZipOutputStream(out);
                    zip.putNextEntry(new ZipEntry("photo.png"));
                    zip.write(new byte[]{(byte)137,80,78,71,13,10,26,10,0,0,0,0});
                    zip.closeEntry();
                    zip.putNextEntry(new ZipEntry("settings.json"));
                    zip.write("{}".getBytes(StandardCharsets.UTF_8));zip.closeEntry();zip.finish();
                });
                fail("Truncated PNG was published as a verified library item");
            } catch(IOException expected) { /* failed closed */ }
            assertEquals(0,store.list().size());
        } finally {deleteRecursive(root);}
    }
    private static String settingsOf(File archive) throws IOException {
        try(ZipInputStream in=new ZipInputStream(new FileInputStream(archive))){
            for(ZipEntry e;(e=in.getNextEntry())!=null;)
                if(e.getName().equals("settings.json"))return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }
        return null;
    }
    /** M5: "Save as new project" (save with no previous id) duplicates a Prismatic project with
     *  its selected mat; both copies reopen independently and saving one never changes the other. */
    @Test public void saveAsNewDuplicatesPrismaticProjectWithSelectedMat() throws Exception {
        File root=Files.createTempDirectory("vinyl-project-duplicate-").toFile();
        try {
            final String doc="{\"schema\":29,\"newStyleId\":52,\"prismaticSpacingMm\":0.5,\"cricutMat\":\"12x24\",\"widthMm\":279.4}";
            StudioProjectLibrary.ArchiveWriter writer=out->{
                ZipOutputStream zip=new ZipOutputStream(out);
                zip.putNextEntry(new ZipEntry("settings.json"));zip.write(doc.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
                zip.putNextEntry(new ZipEntry("photo.png"));
                zip.write(java.util.Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAIAAAASFvFNAAAAFUlEQVR4nGPUSNnCwMDAwMDAxAADABYwAUTo8FNRAAAAAElFTkSuQmCC"));
                zip.closeEntry();zip.finish();zip.flush();
            };
            StudioProjectLibrary store=new StudioProjectLibrary(root);
            StudioProjectLibrary.Entry original=store.save(null,"Chevron portrait","Prismatic Chevron",null,writer);
            StudioProjectLibrary.Entry copy=store.save(null,"Chevron portrait copy","Prismatic Chevron",null,writer);
            assertNotEquals(original.id,copy.id);
            // Saving the original again (same id) leaves the copy untouched.
            StudioProjectLibrary.Entry resaved=store.save(original.id,"Chevron portrait","Prismatic Chevron",null,writer);
            assertEquals(original.id,resaved.id);
            StudioProjectLibrary reopened=new StudioProjectLibrary(root);
            assertEquals(2,reopened.list().size());
            for(String id:new String[]{original.id,copy.id}){
                String settings=settingsOf(reopened.archive(id));
                assertNotNull(settings);
                // The archive keeps the editor document verbatim (org.json is not available
                // in plain JVM unit tests): style, mat and physical size all survive.
                assertEquals(doc,settings);
            }
        } finally {deleteRecursive(root);}
    }
    private static void deleteRecursive(File file) {
        if(file.isDirectory()) {File[] children=file.listFiles();if(children!=null)for(File c:children)deleteRecursive(c);}
        file.delete();
    }
}
