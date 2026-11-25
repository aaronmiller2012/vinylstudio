package com.halftonestudio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Encoded archive-original cache; project reopening must never downgrade the
 * original entry to a sampled display preview on the next project save.
 * This is intentionally independent of Android's Bitmap decoder.
 */
final class ProjectEncodedOriginal {
    private static final String PNG="editing-archive-original.png";
    private static final String JPG="editing-archive-original.jpg";
    private static final String ACTIVE="editing-archive-original.active";
    private ProjectEncodedOriginal(){}
    static final class Stage {
        final File pending;final String suffix;
        Stage(File pending,String suffix){this.pending=pending;this.suffix=suffix;}
        void discard(){pending.delete();}
    }
    static Stage stage(File appDir,byte[] encoded) throws IOException {
        String suffix=ProjectImageSignature.format(encoded);
        if(encoded.length>ProjectArchiveReader.MAX_IMAGE_ENTRY)
            throw new IOException("Original image exceeds project limit");
        File staged=File.createTempFile("vinyl-archive-original-",".pending",appDir);
        try(FileOutputStream out=new FileOutputStream(staged)){
            out.write(encoded);out.getFD().sync();
        }catch(IOException failure){staged.delete();throw failure;}
        return new Stage(staged,suffix);
    }
    /** v3 streaming equivalent: no 32 MB intermediate byte[] on project open. */
    static Stage stage(File appDir,File verifiedImage,String suffix) throws IOException {
        if(verifiedImage==null||!verifiedImage.isFile()||
                verifiedImage.length()<32||verifiedImage.length()>ProjectArchiveReader.MAX_IMAGE_ENTRY||
                !("png".equals(suffix)||"jpg".equals(suffix)))
            throw new IOException("Verified encoded project original is unavailable");
        File staged=File.createTempFile("vinyl-archive-original-",".pending",appDir);
        try(java.io.InputStream in=new java.io.BufferedInputStream(new java.io.FileInputStream(verifiedImage));
            FileOutputStream out=new FileOutputStream(staged)){
            byte[] buffer=new byte[32768];int n;long count=0;
            while((n=in.read(buffer))!=-1){
                if(Thread.currentThread().isInterrupted())throw new IOException("Project open canceled");
                count+=n;if(count>ProjectArchiveReader.MAX_IMAGE_ENTRY)
                    throw new IOException("Project original exceeds size limit");
                out.write(buffer,0,n);
            }
            if(count!=verifiedImage.length())throw new IOException("Project original changed during staging");
            out.getFD().sync();
        }catch(IOException|RuntimeException failure){staged.delete();throw failure;}
        return new Stage(staged,suffix);
    }
    static void commit(File appDir,Stage staged) throws IOException {
        if(staged==null||!staged.pending.isFile())throw new IOException("Original source staging missing");
        File target=new File(appDir,"png".equals(staged.suffix)?PNG:JPG);
        try{Files.move(staged.pending.toPath(),target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException unsupported){
            Files.move(staged.pending.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);
        }
        // The committed format is an explicit revision decision. An older PNG
        // must never win over a newer JPEG if the process dies before cleanup.
        File active=new File(appDir,ACTIVE),temp=File.createTempFile("vinyl-source-choice-",".pending",appDir);
        try(FileOutputStream out=new FileOutputStream(temp)){
            out.write(staged.suffix.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            out.getFD().sync();
        }
        try{Files.move(temp.toPath(),active.toPath(),
                StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        finally{temp.delete();}
        File other=new File(appDir,"png".equals(staged.suffix)?JPG:PNG);
        if(other.exists()&&!other.delete())throw new IOException("Stale original image cache cannot be removed");
    }
    enum Provenance { SELECTED, MISSING, AMBIGUOUS, CORRUPT }
    static Provenance provenance(File dir){
        File png=new File(dir,PNG),jpg=new File(dir,JPG),marker=new File(dir,ACTIVE);
        if(marker.exists()){
            if(!marker.isFile())return Provenance.CORRUPT;
            try{
                String choice=new String(Files.readAllBytes(marker.toPath()),java.nio.charset.StandardCharsets.US_ASCII);
                if(!"png".equals(choice)&&!"jpg".equals(choice))return Provenance.CORRUPT;
                File selected="png".equals(choice)?png:jpg;
                return selected.isFile()&&selected.length()>0?Provenance.SELECTED:Provenance.CORRUPT;
            }catch(IOException error){return Provenance.CORRUPT;}
        }
        if(png.isFile()&&jpg.isFile())return Provenance.AMBIGUOUS;
        if(png.isFile()||jpg.isFile())return Provenance.SELECTED;
        return Provenance.MISSING;
    }
    /** Fail closed if a project was opened from an encoded original. A null
     * fallback would re-encode the downsampled display bitmap and silently
     * discard its source fidelity in the next ZIP. Never guess by file mtime.
     */
    static File requireSelected(File dir,boolean expected) throws IOException {
        if(!expected)return null;
        Provenance state=provenance(dir);
        if(state!=Provenance.SELECTED)throw new IOException(
            "Full-resolution project original is "+state.name().toLowerCase(java.util.Locale.US)+
            "; reopen the original project ZIP before saving to avoid image quality loss");
        File chosen=active(dir);
        if(chosen==null||!chosen.isFile())throw new IOException("Original image source cannot be recovered");
        return chosen;
    }
    static File active(File dir){
        File png=new File(dir,PNG),jpg=new File(dir,JPG),marker=new File(dir,ACTIVE);
        if(marker.isFile())try{
            String format=new String(Files.readAllBytes(marker.toPath()),java.nio.charset.StandardCharsets.US_ASCII);
            File selected="png".equals(format)?png:"jpg".equals(format)?jpg:null;
            return selected!=null&&selected.isFile()?selected:null;
        }catch(IOException ignored){return null;}
        // Legacy migration: one file is unambiguous, but choosing between
        // two formats by mtime or extension would silently corrupt provenance.
        if(png.isFile()&&jpg.isFile())return null;
        return png.isFile()?png:jpg.isFile()?jpg:null;
    }
    static void clear(File dir){new File(dir,ACTIVE).delete();new File(dir,PNG).delete();new File(dir,JPG).delete();}
    static String zipName(File file){return file!=null&&file.getName().endsWith(".jpg")?"original.jpg":"original.png";}
    static InputStream open(File file) throws IOException{return Files.newInputStream(file.toPath());}
}
