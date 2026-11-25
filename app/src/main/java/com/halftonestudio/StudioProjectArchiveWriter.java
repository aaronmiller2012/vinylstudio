package com.halftonestudio;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Writes the project format (format 2 adds a final checksum manifest), independent of Android Bitmap.
 * Image encoders stream PNG data directly to the respective ZIP entry.
 */
final class StudioProjectArchiveWriter {
    interface ImageEncoder { boolean encode(OutputStream output); }
    interface SourceSupplier { java.io.InputStream open() throws IOException; }
    private StudioProjectArchiveWriter() {}

    static void write(ZipOutputStream zip, ImageEncoder photo, ImageEncoder original,
                      String settingsJson) throws IOException {
        write(zip,photo,original,settingsJson,null);
    }
    /** Optional encoded original camera file is for project portability, NEVER cut ZIP export. */
    static void write(ZipOutputStream zip, ImageEncoder photo, ImageEncoder original,
                      String settingsJson,SourceSupplier nativeSource) throws IOException {
        Objects.requireNonNull(zip, "zip");
        Objects.requireNonNull(photo, "photo");
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(settingsJson, "settingsJson");
        byte[] settingsBytes=settingsJson.getBytes(StandardCharsets.UTF_8);
        if(settingsBytes.length>ProjectArchiveReader.MAX_SETTINGS_ENTRY)
            throw new IOException("Project settings exceed the supported 1 MB limit");
        ProjectManifest.Builder manifest=new ProjectManifest.Builder();
        writeImage(zip,"photo.png",photo,manifest);
        writeImage(zip,"original.png",original,manifest);
        writeNativeSource(zip,nativeSource,manifest);
        writeBytes(zip,"settings.json",settingsBytes,manifest);
        finish(zip,manifest);
    }
    /** A sampled preview must not replace the full-resolution original image.
     * Reuse the original archive's encoded PNG/JPEG bytes when available.
     */
    static void write(ZipOutputStream zip, ImageEncoder photo, ImageEncoder original,
                      String settingsJson,SourceSupplier nativeSource,java.io.File archiveOriginal) throws IOException {
        if(archiveOriginal==null){
            write(zip,photo,original,settingsJson,nativeSource);return;
        }
        if(!archiveOriginal.isFile())throw new IOException("Full-resolution project original disappeared during save");
        Objects.requireNonNull(zip);Objects.requireNonNull(photo);
        byte[] settingsBytes=Objects.requireNonNull(settingsJson).getBytes(StandardCharsets.UTF_8);
        if(settingsBytes.length>ProjectArchiveReader.MAX_SETTINGS_ENTRY)
            throw new IOException("Project settings exceed the supported limit");
        ProjectManifest.Builder manifest=new ProjectManifest.Builder();
        writeImage(zip,"photo.png",photo,manifest);
        String entry=ProjectEncodedOriginal.zipName(archiveOriginal);
        zip.putNextEntry(new ZipEntry(entry));
        try(java.io.InputStream in=ProjectEncodedOriginal.open(archiveOriginal);OutputStream out=manifest.entry(entry,zip)){
            byte[] buffer=new byte[32768];long copied=0;int n;
            while((n=in.read(buffer))!=-1){
                copied+=n;
                if(copied>ProjectArchiveReader.MAX_IMAGE_ENTRY)throw new IOException("Encoded original exceeds image limit");
                out.write(buffer,0,n);
            }
            if(copied<32)throw new IOException("Encoded original is incomplete");
        }
        zip.closeEntry();
        writeNativeSource(zip,nativeSource,manifest);
        writeBytes(zip,"settings.json",settingsBytes,manifest);
        finish(zip,manifest);
    }
    private static void writeBytes(ZipOutputStream zip,String name,byte[] bytes,ProjectManifest.Builder manifest) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        try(OutputStream out=manifest.entry(name,zip)){out.write(bytes);}
        zip.closeEntry();
    }
    /** Format 2: the checksum manifest is always the final entry. */
    private static void finish(ZipOutputStream zip,ProjectManifest.Builder manifest) throws IOException {
        zip.putNextEntry(new ZipEntry(ProjectManifest.ENTRY));
        zip.write(manifest.toJson().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
        zip.finish();zip.flush();
    }
    private static void writeNativeSource(ZipOutputStream zip,SourceSupplier nativeSource,ProjectManifest.Builder manifest) throws IOException {
        if(nativeSource==null)return;
        try(java.io.InputStream nativeIn=nativeSource.open()) {
            if(nativeIn!=null){
                zip.putNextEntry(new ZipEntry(ProjectArchiveReader.NATIVE_SOURCE_ENTRY));
                byte[] chunk=new byte[32*1024];long total=0;int n;
                try(OutputStream out=manifest.entry(ProjectArchiveReader.NATIVE_SOURCE_ENTRY,zip)){
                    while((n=nativeIn.read(chunk))!=-1){
                        if(n==0)continue;
                        total+=n;if(total>ProjectArchiveReader.MAX_SOURCE_ENTRY)
                            throw new IOException("Original source exceeds portable project limit");
                        out.write(chunk,0,n);
                    }
                }
                if(total==0)throw new IOException("Portable source photo was empty");
                zip.closeEntry();
            }
        }
    }
    private static void writeImage(ZipOutputStream zip,String name,ImageEncoder encoder,ProjectManifest.Builder manifest) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        final OutputStream digesting=manifest.entry(name,zip);
        // Archive-reader image limits also apply to writers. The PNG must be bounded
        // while compressing; allocating the complete PNG in memory is unnecessary.
        final long[] boundedBytes={0};
        OutputStream bounded=new OutputStream(){
            long bytes;
            private void check(int n) throws IOException {
                bytes+=n;boundedBytes[0]=bytes;
                if(bytes>ProjectArchiveReader.MAX_IMAGE_ENTRY)
                    throw new IOException(name+" exceeds the 32 MB image-entry limit");
            }
            @Override public void write(int b) throws IOException {check(1);digesting.write(b);}
            @Override public void write(byte[] b,int off,int len) throws IOException {check(len);digesting.write(b,off,len);}
            @Override public void flush() throws IOException {zip.flush();}
        };
        if (!encoder.encode(bounded)) throw new IOException("Cannot encode " + name);
        if(boundedBytes[0]==0)throw new IOException("Image encoder produced an empty "+name);
        digesting.close();
        zip.closeEntry();
    }
}
