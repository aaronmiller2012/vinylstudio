package com.halftonestudio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/**
 * Two corruptions found in independent review of 557's damaged-project support:
 *  1. Same-size, same-mtime bit-rot: the gallery fingerprint still matches, so the project
 *     listed as healthy, could not open, and recovery export refused it ("stuck").
 *  2. A malformed ZIP entry name makes ZipInputStream throw IllegalArgumentException,
 *     which escaped verification and broke listing of the ENTIRE library.
 * Each uses real saved projects and the real library code.
 */
public final class V3M3CorruptionResilienceTest {
    private static void yes(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    private static void delete(File file){
        File[] inner=file.listFiles();if(inner!=null)for(File f:inner)delete(f);
        file.delete();
    }
    private static StudioProjectLibrary.Entry find(StudioProjectLibrary library,String id){
        return library.list().stream().filter(e->e.id.equals(id)).findFirst()
            .orElseThrow(()->new AssertionError("project hidden from gallery: "+id));
    }
    /** Flip one byte inside the stored entry data, preserving length and mtime. */
    private static byte[] rot(File zip) throws IOException {
        byte[] bytes=Files.readAllBytes(zip.toPath());
        long mtime=zip.lastModified();
        // Local header (30 bytes) + first entry name, then its compressed data.
        int nameLength=(bytes[26]&0xff)|((bytes[27]&0xff)<<8),extra=(bytes[28]&0xff)|((bytes[29]&0xff)<<8);
        int at=30+nameLength+extra+4;
        yes(bytes[0]=='P'&&bytes[1]=='K'&&at<bytes.length/2,"unexpected fixture layout");
        bytes[at]^=0x40;
        Files.write(zip.toPath(),bytes);
        yes(zip.setLastModified(mtime),"cannot restore mtime");
        return bytes;
    }
    /** A real project ZIP whose LOCAL header name for settings.json is invalid UTF-8.
     * The central directory stays valid, so ZipFile accepts it and only the streaming
     * parser fails, with IllegalArgumentException (the single-byte rot seen in review). */
    private static byte[] malformedName(byte[] valid) throws IOException {
        byte[] bytes=valid.clone();
        byte[] name="settings.json".getBytes(StandardCharsets.US_ASCII);
        int at=-1;
        search:for(int i=30;i+name.length<bytes.length;i++){
            for(int j=0;j<name.length;j++)if(bytes[i+j]!=name[j])continue search;
            at=i;break;
        }
        yes(at>0,"fixture name not found");
        bytes[at]=(byte)0xC3;bytes[at+1]=0x28;
        try(ZipFile indexed=new ZipFile(writeTemp(bytes))){yes(indexed.size()==2,"central directory should stay valid");}
        try(ZipInputStream probe=new ZipInputStream(new ByteArrayInputStream(bytes))){
            while(probe.getNextEntry()!=null){}
            throw new AssertionError("fixture should be unparsable");
        }catch(IllegalArgumentException expected){}
        return bytes;
    }
    private static File writeTemp(byte[] bytes) throws IOException {
        File f=File.createTempFile("vinyl-malformed-",".zip");f.deleteOnExit();Files.write(f.toPath(),bytes);return f;
    }
    public static void main(String[] args) throws Exception {
        File root=Files.createTempDirectory("vinyl-m3-corruption-").toFile();
        try{
            StudioProjectLibrary library=new StudioProjectLibrary(root);
            byte[] valid=StudioLibraryJobsTest.archive();
            StudioProjectLibrary.Entry healthy=library.save(null,"Healthy","Lines",null,out->out.write(valid));
            StudioProjectLibrary.Entry rotted=library.save(null,"Rot","Lines",null,out->out.write(valid));
            StudioProjectLibrary.Entry mangled=library.save(null,"Mangled","Lines",null,out->out.write(valid));

            // 1. Bit-rot with unchanged fingerprint.
            byte[] rotBytes=rot(library.archive(rotted.id));
            yes(!library.archive(rotted.id).isFile(),"rotted archive must fail fresh verification");
            yes(find(library,rotted.id).damaged,"project that failed full verification must list as unreadable");
            yes(library.get(rotted.id)==null,"rotted project must not be returned as usable");
            try(InputStream in=library.openDamagedArchiveForRecovery(rotted.id)){
                yes(Arrays.equals(in.readAllBytes(),rotBytes),"recovery must export exact rotted bytes");
            }
            // A fresh library (new process) trusts the fingerprint again until the first
            // failed open; recovery must still be allowed rather than "Not an unreadable project".
            StudioProjectLibrary restarted=new StudioProjectLibrary(root);
            try(InputStream in=restarted.openDamagedArchiveForRecovery(rotted.id)){
                yes(Arrays.equals(in.readAllBytes(),rotBytes),"recovery refused after restart");
            }
            try{library.openDamagedArchiveForRecovery(healthy.id);throw new AssertionError("healthy project exported as damaged");}
            catch(IOException expected){}

            // 2. Malformed entry name must not escape and break the gallery.
            byte[] bad=malformedName(valid);
            Files.write(library.archive(mangled.id).toPath(),bad);
            StudioProjectLibrary cold=new StudioProjectLibrary(root);
            List<StudioProjectLibrary.Entry> listed=cold.list();
            yes(listed.size()==3,"one malformed project hid others: "+listed.size());
            yes(find(cold,mangled.id).damaged,"malformed project must list as unreadable");
            yes(!find(cold,healthy.id).damaged,"healthy project affected by neighbour");
            yes(cold.get(mangled.id)==null,"malformed project must not be usable");
            try(InputStream in=cold.openDamagedArchiveForRecovery(mangled.id)){
                yes(Arrays.equals(in.readAllBytes(),bad),"recovery must export exact malformed bytes");
            }
            try{StudioProjectBackup.preflight(cold);throw new AssertionError("backup must refuse unreadable projects");}
            catch(IOException expected){yes(expected.getMessage().contains("Unreadable"),"backup should name the damage");}
            cold.delete(mangled.id);cold.delete(rotted.id);
            yes(cold.list().size()==1&&cold.get(healthy.id)!=null,"unreadable projects must be deletable");
            System.out.println("PASS M3 bit-rot and malformed-ZIP projects stay listed, recoverable and deletable without breaking the gallery");
        }finally{delete(root);}
    }
}
