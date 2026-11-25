package com.halftonestudio;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/**
 * Build 553: the gallery lists committed revisions from their save-time fingerprint
 * (SHA-256 + length + mtime) instead of decompressing every archive, while opening a
 * project still verifies the complete payload. Damaged card metadata is never replaced
 * by a fingerprint-only card.
 */
public final class V3Product553LibraryTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static final byte[] PNG=Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAIAAAASFvFNAAAAFUlEQVR4nGPUSNnCwMDAwMDAxAADABYwAUTo8FNRAAAAAElFTkSuQmCC");
    private static final byte[] PREVIEW={(byte)137,80,78,71,13,10,26,10,0,0,0,13,73,72,68,82,0,0,0,1,0,0,0,1};
    private static StudioProjectLibrary.ArchiveWriter archive(String label){
        return out->{
            ZipOutputStream zip=new ZipOutputStream(out);
            StudioProjectArchiveWriter.write(zip,
                stream->{try{stream.write(PNG);return true;}catch(IOException e){return false;}},
                stream->{try{stream.write(PNG);return true;}catch(IOException e){return false;}},
                "{\"schema\":14,\"name\":\""+label+"\"}");
        };
    }
    private static File onlyBundle(File root,String id){
        File[] dirs=root.listFiles((d,n)->n.startsWith(id+".rev-")&&n.endsWith(".bundle"));
        check(dirs!=null&&dirs.length==1,"expected exactly one revision bundle");
        return dirs[0];
    }
    /** Same length, same mtime, corrupt bytes: only a full decompression can notice. */
    private static void corruptPreservingFingerprint(File zip) throws IOException {
        long mtime=zip.lastModified();long len=zip.length();
        byte[] junk=new byte[(int)len];Arrays.fill(junk,(byte)0x5a);
        Files.write(zip.toPath(),junk);
        check(zip.setLastModified(mtime),"cannot restore mtime on this filesystem");
        check(zip.length()==len,"length changed");
    }
    private static void deleteTree(File f){
        if(f.isDirectory())for(File c:Objects.requireNonNull(f.listFiles()))deleteTree(c);
        if(f.exists())f.delete();
    }
    public static void main(String[] args) throws Exception {
        File root=Files.createTempDirectory("vinyl-553-library-").toFile();
        try{
            StudioProjectLibrary writer=new StudioProjectLibrary(root);
            StudioProjectLibrary.Entry a=writer.save(null,"Fast A","Shapes",PREVIEW,archive("A"));
            File libraryDir=new File(root,"project-library");
            File bundle=onlyBundle(libraryDir,a.id);
            File zip=new File(bundle,"project.zip"),card=new File(bundle,"card.properties");
            check(StudioProjectLibrary.matchesRecordedFingerprint(zip,card),"save must record a matching fingerprint");

            // 1. Cold gallery listing trusts the recorded fingerprint: a fresh repository
            //    (empty in-memory verification cache) lists the project without inflating it.
            corruptPreservingFingerprint(zip);
            StudioProjectLibrary cold=new StudioProjectLibrary(root);
            List<StudioProjectLibrary.Entry> listed=cold.list();
            check(listed.size()==1&&"Fast A".equals(listed.get(0).name),"fingerprint fast path must list without decompression");
            check(cold.preview(a.id).getName().equals("preview.jpg"),"thumbnail lookup must use listed revision");
            // 2. Opening still performs full verification and refuses the corrupt payload.
            File opened=cold.archive(a.id);
            check(!opened.getName().equals("project.zip"),"open must fully verify and reject a corrupt archive");

            // 3. A changed fingerprint (different mtime) falls back to full verification for listing.
            check(zip.setLastModified(zip.lastModified()-5_000L),"cannot change mtime");
            check(!StudioProjectLibrary.matchesRecordedFingerprint(zip,card),"mtime change must invalidate fingerprint");
            StudioProjectLibrary damagedListing=new StudioProjectLibrary(root);
            List<StudioProjectLibrary.Entry> recoveryEntries=damagedListing.list();
            check(recoveryEntries.size()==1&&recoveryEntries.get(0).damaged,
                "mismatched fingerprint must fail full verification and expose recovery-only entry");
            check(damagedListing.get(a.id)==null,
                "corrupt ZIP must never be returned as a usable project");

            // 4. A damaged card is never overwritten by a fingerprint-only card.
            StudioProjectLibrary.Entry b=writer.save(null,"Card B","Lines",PREVIEW,archive("B"));
            File bBundle=onlyBundle(libraryDir,b.id);
            File bCard=new File(bBundle,"card.properties"),bZip=new File(bBundle,"project.zip");
            check(bZip.setLastModified(bZip.lastModified()-7_000L),"cannot change mtime");
            byte[] damaged="\u0000\u0000garbage-without-keys".getBytes("ISO-8859-1");
            Files.write(bCard.toPath(),damaged);
            String digest=new StudioProjectLibrary(root).archiveDigest(b.id);
            check(digest.matches("[A-Za-z0-9+/]{43}="),"digest must still be computed");
            check(Arrays.equals(damaged,Files.readAllBytes(bCard.toPath())),"damaged card must not be rewritten");
            System.out.println("PASS 553 library: fingerprint listing, full open verification, fallback, damaged card preserved");
        }finally{deleteTree(root);}
    }
}
