package com.halftonestudio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** RC55 host regression: real project archives, rollback, legacy, backups, and corruption. */
public final class StudioProjectLibraryTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static final byte[] PNG=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAIAAAASFvFNAAAAFUlEQVR4nGPUSNnCwMDAwMDAxAADABYwAUTo8FNRAAAAAElFTkSuQmCC");
    // A valid PNG with distinct tEXt metadata per revision (CRC checked), not fake image bytes.
    private static byte[] labeledPng(String label) throws IOException {
        byte[] data=("VinylLabel\0"+label).getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write(PNG,0,PNG.length-12);
        java.io.DataOutputStream binary=new java.io.DataOutputStream(out);
        binary.writeInt(data.length);byte[] type={'t','E','X','t'};out.write(type);out.write(data);
        java.util.zip.CRC32 crc=new java.util.zip.CRC32();crc.update(type);crc.update(data);
        binary.writeInt((int)crc.getValue());out.write(PNG,PNG.length-12,12);
        return out.toByteArray();
    }
    private static StudioProjectLibrary.ArchiveWriter archive(String label){
        return out->{
            ZipOutputStream zip=new ZipOutputStream(out);
            byte[] photo=labeledPng("photo "+label),original=labeledPng("original "+label);
            StudioProjectArchiveWriter.write(zip,
                stream->{try{stream.write(photo);return true;}catch(IOException e){return false;}},
                stream->{try{stream.write(original);return true;}catch(IOException e){return false;}},
                "{\"schema\":14,\"name\":\""+label+"\"}");
        };
    }
    private static String photo(File zipFile) throws Exception {
        try(ZipInputStream in=new ZipInputStream(new FileInputStream(zipFile))){
            byte[] encoded=ProjectArchiveReader.read(in).photo;
            if(!"png".equals(ProjectImageSignature.format(encoded)))throw new AssertionError("PNG fixture corrupt");
            String bytes=new String(encoded,StandardCharsets.ISO_8859_1);
            int start=bytes.indexOf("VinylLabel\0");
            if(start<0)throw new AssertionError("Revision image label missing");
            int len=((encoded[start-8]&255)<<24)|((encoded[start-7]&255)<<16)|
                ((encoded[start-6]&255)<<8)|(encoded[start-5]&255);
            return bytes.substring(start+11,start+len);
        }
    }
    private static void deleteTree(File f){
        if(f.isDirectory())for(File c:Objects.requireNonNull(f.listFiles()))deleteTree(c);
        if(f.exists())check(f.delete(),"failed to delete test data");
    }
    public static void main(String[] args) throws Exception {
        File root=Files.createTempDirectory("vinyl-project-library-").toFile();
        try{
            StudioProjectLibrary library=new StudioProjectLibrary(root);
            check(library.list().isEmpty(),"new library should be empty");
            // Header-valid 1x1 PNG fixture (library intentionally validates the header).
            byte[] preview=new byte[]{(byte)137,80,78,71,13,10,26,10,
                0,0,0,13,73,72,68,82,0,0,0,1,0,0,0,1};
            StudioProjectLibrary.Entry first=library.save(null,"  Portrait A  ","CMYK",preview,archive("A"));
            check("Portrait A".equals(first.name),"name normalization");
            check(library.list().size()==1,"first project missing");
            check("photo A".equals(photo(library.archive(first.id))),"first archive roundtrip");
            String firstDigest=library.archiveDigest(first.id);
            check(firstDigest.length()==44,"SHA-256 should be cached on initial save");
            File card=library.archive(first.id).getParentFile();
            File cardProperties=new File(card,"card.properties");
            Properties hashCard=new Properties();
            try(FileInputStream in=new FileInputStream(cardProperties)){hashCard.load(in);}
            check(firstDigest.equals(hashCard.getProperty("archiveSha256")),"saved metadata missing digest");
            long before=cardProperties.lastModified();
            check(firstDigest.equals(library.archiveDigest(first.id)),"repeat digest changed");
            check(before==cardProperties.lastModified(),"unchanged ZIP should not rewrite cached metadata");
            check(library.preview(first.id).isFile(),"missing gallery preview");
            String oldArchivePath=library.archive(first.id).getAbsolutePath();
            StudioProjectLibrary.Entry second=library.save(null,"Portrait B","Stencil",preview,archive("B"));
            StudioProjectLibrary.Entry updated=library.save(first.id,"Portrait A v2","Line",preview,archive("A2"));
            check(updated.id.equals(first.id)&&library.list().size()==2,"project update duplicated");
            check("photo A2".equals(photo(library.archive(first.id))),"updated archive incorrect");
            check(new File(oldArchivePath).exists(),"previous valid revision was discarded");
            try{library.save(first.id,"Failed","Line",preview,out->{archive("FAIL").write(out);throw new IOException("disk full");});
                throw new AssertionError("expected write failure");
            }catch(IOException expected){check("disk full".equals(expected.getMessage()),"incorrect failure");}
            check("photo A2".equals(photo(library.archive(first.id))),"failed save broke prior ZIP");
            check("Portrait A v2".equals(library.get(first.id).name),"failed save altered metadata");
            // Simulate power loss with an uncommitted directory: readers must ignore it.
            File pending=new File(new File(root,"project-library"),first.id+".pending-interrupted");
            check(pending.mkdir(),"setup pending folder");
            Files.write(new File(pending,"project.zip").toPath(),new byte[]{1,2,3});
            check(library.list().size()==2,"pending save exposed in gallery");
            // Simulate corrupted latest revision: open previous known-good revision.
            Files.write(library.archive(first.id).toPath(),new byte[]{1,2,3});
            check("photo A".equals(photo(library.archive(first.id))),"corruption did not fall back");
            library.rename(second.id,"New B");
            check("New B".equals(library.get(second.id).name),"rename unsuccessful");
            check("New B".equals(new StudioProjectLibrary(root).get(second.id).name),"rename did not persist");
            check(library.get("../escape")==null,"invalid project ID accepted");
            // Backup and restore must create new IDs and preserve existing projects.
            ByteArrayOutputStream backup=new ByteArrayOutputStream();
            check(StudioProjectBackup.exportAll(library,backup)==2,"backup count");
            File restoreRoot=Files.createTempDirectory("vinyl-library-restore-").toFile();
            try{
                StudioProjectLibrary restored=new StudioProjectLibrary(restoreRoot);
                check(StudioProjectBackup.importAll(new ByteArrayInputStream(backup.toByteArray()),restored)==2,"restore count");
                check(restored.list().size()==2,"restore project count");
                check(restored.list().stream().anyMatch(e->"New B".equals(e.name)),"restore lost labels");
                check(StudioProjectBackup.importAll(new ByteArrayInputStream(backup.toByteArray()),restored)==0,"second restore should skip duplicate archives");
                check(restored.list().size()==2,"second restore duplicated existing projects");
                try{StudioProjectBackup.importAll(new ByteArrayInputStream("garbage".getBytes(StandardCharsets.UTF_8)),restored);
                    throw new AssertionError("unrecognized backup accepted");
                }catch(IOException expected){}
            }finally{deleteTree(restoreRoot);}
            // Real RC54 flat storage layout remains visible and editable after an upgrade.
            String legacy=UUID.randomUUID().toString();
            File legacyZip=new File(new File(root,"project-library"),legacy+".zip");
            try(FileOutputStream out=new FileOutputStream(legacyZip)){archive("legacy").write(out);}
            check("photo legacy".equals(photo(library.archive(legacy))),"legacy ZIP invisible");
            String oldHash=library.archiveDigest(legacy);
            Properties legacyCard=new Properties();
            try(FileInputStream in=new FileInputStream(new File(new File(root,"project-library"),legacy+".properties"))){legacyCard.load(in);}
            check(oldHash.equals(legacyCard.getProperty("archiveSha256")),"legacy cache was not persisted");
            library.save(legacy,"Legacy updated","CMYK",preview,archive("new"));
            check(legacyZip.exists(),"legacy rollback deleted during first new save");
            check("photo new".equals(photo(library.archive(legacy))),"legacy not upgraded");
            library.save(legacy,"Legacy updated again","CMYK",preview,archive("new2"));
            check(!legacyZip.exists(),"legacy cleanup missing after two valid saves");
            library.delete(first.id);
            check(library.get(first.id)==null,"deleted project still visible");
            check(library.get(second.id)!=null,"deletion affected unrelated project");
            System.out.println("PASS RC55 project safety: immutable revisions, recovery, legacy, rename, backup, restore and ZIP validation");
        }finally{deleteTree(root);}
    }
}
