package com.halftonestudio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Streaming, bounded manual library backup. No Android dependencies or storage permissions. */
final class StudioProjectBackup {
    private static final int MAX_PROJECTS=2000;
    private static final long MAX_LIBRARY_BYTES=1_500_000_000L;
    private static final long MAX_PROJECT_ZIP=90_000_000L;
    private static final int MAX_CARD_BYTES=12_000, MAX_PREVIEW_BYTES=2_000_000;
    private static final String MAGIC="VinylStudioProjectLibraryBackup";
    private StudioProjectBackup(){}
    private static void copy(InputStream in,OutputStream out,long limit) throws IOException {
        byte[] buf=new byte[32*1024];long total=0;int n;
        while((n=in.read(buf))!=-1){
            if(Thread.currentThread().isInterrupted())throw new IOException("Backup cancelled");
            total+=n;if(total>limit)throw new IOException("Backup entry size limit exceeded");out.write(buf,0,n);}
    }
    private static byte[] readSmall(InputStream in,int max) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();copy(in,out,max);return out.toByteArray();
    }
    private static void entry(ZipOutputStream zip,String name,byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));zip.write(bytes);zip.closeEntry();
    }
    private static byte[] properties(String... pairs) throws IOException {
        Properties p=new Properties();for(int i=0;i<pairs.length;i+=2)p.setProperty(pairs[i],pairs[i+1]);
        ByteArrayOutputStream out=new ByteArrayOutputStream();p.store(out,"VinylStudio library backup");return out.toByteArray();
    }
    /** Validate predictable failures before opening/truncating a SAF destination. */
    static int preflight(StudioProjectLibrary library) throws IOException {
        List<StudioProjectLibrary.Entry> items=library.list();
        if(items.size()>MAX_PROJECTS)throw new IOException("Too many projects for one backup");
        long total=0;
        for(StudioProjectLibrary.Entry item:items){
            if(Thread.currentThread().isInterrupted())throw new IOException("Backup cancelled");
            // Never silently omit damaged entries from a supposedly complete backup.
            if(item.damaged)throw new IOException("Unreadable project: "+item.name+
                ". Export its damaged ZIP for recovery or resolve it before backing up.");
            File archive=library.archive(item.id),preview=library.preview(item.id);
            if(!archive.isFile()||archive.length()<=0||archive.length()>MAX_PROJECT_ZIP)
                throw new IOException("Project unavailable: "+item.name);
            if(preview.exists()&&(!preview.isFile()||preview.length()>MAX_PREVIEW_BYTES))
                throw new IOException("Project preview exceeds backup limit: "+item.name);
            total+=archive.length();
            if(total>MAX_LIBRARY_BYTES)throw new IOException("Library backup exceeds 1.5 GB");
        }
        return items.size();
    }
    private static String sha256(File file) throws IOException {
        try {
            java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
            try(InputStream input=new BufferedInputStream(new FileInputStream(file))){
                byte[] buffer=new byte[32*1024];int n;
                while((n=input.read(buffer))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new IOException("Restore cancelled");
                    digest.update(buffer,0,n);
                }
            }
            return java.util.Base64.getEncoder().encodeToString(digest.digest());
        }catch(java.security.NoSuchAlgorithmException impossible){throw new IOException("Digest unavailable",impossible);}
    }
    static int exportAll(StudioProjectLibrary library,OutputStream destination) throws IOException {
        preflight(library);
        List<StudioProjectLibrary.Entry> items=library.list();
        long total=0;
        // Finish the ZIP but do not close the Android provider's caller-owned stream.
        ZipOutputStream zip=new ZipOutputStream(destination);
        entry(zip,"manifest.properties",properties("format",MAGIC,"version","1"));
        for(StudioProjectLibrary.Entry item:items){
            if(Thread.currentThread().isInterrupted())throw new IOException("Backup cancelled");
            String folder="projects/"+item.id+"/";
            entry(zip,folder+"card.properties",properties("name",item.name,"style",item.style));
            File preview=library.preview(item.id);
            zip.putNextEntry(new ZipEntry(folder+"preview.jpg"));
            if(preview.isFile())try(InputStream in=new FileInputStream(preview)){copy(in,zip,MAX_PREVIEW_BYTES);}
            zip.closeEntry();
            File archive=library.archive(item.id);
            if(!archive.isFile()||archive.length()>MAX_PROJECT_ZIP)throw new IOException("Project unavailable: "+item.name);
            total+=archive.length();if(total>MAX_LIBRARY_BYTES)throw new IOException("Library backup exceeds 1.5 GB");
            zip.putNextEntry(new ZipEntry(folder+"project.zip"));
            try(InputStream in=new FileInputStream(archive)){copy(in,zip,MAX_PROJECT_ZIP);}
            zip.closeEntry();
        }
        zip.finish();zip.flush();return items.size();
    }
    /** Check the entire generated ZIP, including entry CRCs, BEFORE opening the SAF target.
     * This cannot make the provider's final write atomic; callers must still report failures. */
    static void verifyStaged(File file,int expectedProjects) throws IOException {
        // ZipInputStream alone cannot detect a missing or truncated central directory.
        try(ZipFile indexed=new ZipFile(file)){
            if(indexed.size()!=expectedProjects*3+1)
                throw new IOException("Staged backup ZIP directory is incomplete");
        }
        long total=0;int projectArchives=0;int entries=0;
        try(ZipInputStream in=new ZipInputStream(new BufferedInputStream(new FileInputStream(file)))){
            ZipEntry first=in.getNextEntry();
            if(first==null||!"manifest.properties".equals(first.getName()))
                throw new IOException("Staged library backup has no manifest");
            Properties manifest=new Properties();
            manifest.load(new ByteArrayInputStream(readSmall(in,MAX_CARD_BYTES)));
            in.closeEntry();
            if(!MAGIC.equals(manifest.getProperty("format"))||!"1".equals(manifest.getProperty("version")))
                throw new IOException("Staged library backup manifest is invalid");
            ZipEntry entry;
            while((entry=in.getNextEntry())!=null){
                if(Thread.currentThread().isInterrupted())throw new IOException("Backup validation interrupted");
                if(++entries>MAX_PROJECTS*3)throw new IOException("Staged backup has too many entries");
                String path=entry.getName();
                long limit;
                if(path.matches("projects/[a-f0-9-]{36}/card\\.properties"))limit=MAX_CARD_BYTES;
                else if(path.matches("projects/[a-f0-9-]{36}/preview\\.jpg"))limit=MAX_PREVIEW_BYTES;
                else if(path.matches("projects/[a-f0-9-]{36}/project\\.zip")){
                    limit=MAX_PROJECT_ZIP;projectArchives++;
                }else throw new IOException("Unexpected staged backup entry");
                byte[] buf=new byte[32*1024];long bytes=0;int read;
                while((read=in.read(buf))!=-1){
                    bytes+=read;total+=read;
                    if(bytes>limit||total>MAX_LIBRARY_BYTES+(long)MAX_PROJECTS*(MAX_CARD_BYTES+MAX_PREVIEW_BYTES))
                        throw new IOException("Staged backup size limit exceeded");
                }
                in.closeEntry(); // ZipInputStream checks the per-entry CRC.
            }
        }
        if(projectArchives!=expectedProjects||entries!=expectedProjects*3)
            throw new IOException("Staged backup project count mismatch");
    }

    /** Restore new archives without overwriting existing work or duplicating identical content. */
    static int importAll(InputStream source,StudioProjectLibrary library) throws IOException {
        ZipInputStream zip=new ZipInputStream(source);
        ZipEntry first=zip.getNextEntry();
        if(first==null||!"manifest.properties".equals(first.getName()))throw new IOException("Not a VinylStudio library backup");
        Properties manifest=new Properties();manifest.load(new ByteArrayInputStream(readSmall(zip,MAX_CARD_BYTES)));
        zip.closeEntry();
        if(!MAGIC.equals(manifest.getProperty("format"))||!"1".equals(manifest.getProperty("version")))
            throw new IOException("Unsupported library backup version");
        int restored=0;long total=0;ZipEntry item;
        Set<String> known=new HashSet<>();
        for(StudioProjectLibrary.Entry old:library.list()){
            // Cached in the project's card.properties after its first verified read.
            // Unchanged 90 MB archives are not reopened for every repeat restore.
            if(!old.damaged)known.add(library.archiveDigest(old.id));
        }
        int examined=0;
        while((item=zip.getNextEntry())!=null){
            if(Thread.currentThread().isInterrupted())throw new IOException("Restore cancelled");
            if(++examined>MAX_PROJECTS)throw new IOException("Backup has too many projects");
            String path=item.getName();
            if(!path.matches("projects/[a-f0-9-]{36}/card\\.properties"))throw new IOException("Unexpected backup entry: "+path);
            String prefix=path.substring(0,path.length()-"card.properties".length());
            Properties card=new Properties();card.load(new ByteArrayInputStream(readSmall(zip,MAX_CARD_BYTES)));zip.closeEntry();
            String name=card.getProperty("name","Imported project"),style=card.getProperty("style","Vinyl artwork");
            item=zip.getNextEntry();
            if(item==null||!(prefix+"preview.jpg").equals(item.getName()))throw new IOException("Incomplete backup preview");
            byte[] thumb=readSmall(zip,MAX_PREVIEW_BYTES);zip.closeEntry();
            item=zip.getNextEntry();
            if(item==null||!(prefix+"project.zip").equals(item.getName()))throw new IOException("Incomplete backup archive");
            final long budget=MAX_LIBRARY_BYTES-total;
            File staged=library.newRestoreTemp();
            long copied=0;
            try {
                try(OutputStream tempOut=new BufferedOutputStream(new FileOutputStream(staged))){
                    byte[] buffer=new byte[32*1024];int n;
                    while((n=zip.read(buffer))!=-1){
                        if(Thread.currentThread().isInterrupted())throw new IOException("Restore cancelled");
                        copied+=n;
                        if(copied>MAX_PROJECT_ZIP||copied>budget)
                            throw new IOException("Library backup size limit exceeded");
                        tempOut.write(buffer,0,n);
                    }
                }
                total+=copied;
                zip.closeEntry();
                String hash=sha256(staged);
                if(!known.contains(hash)){
                    final File readBack=staged;
                    library.save(null,name,style,thumb.length==0?null:thumb,out->{
                        try(InputStream file=new BufferedInputStream(new FileInputStream(readBack))){
                            copy(file,out,MAX_PROJECT_ZIP);
                        }
                    });
                    known.add(hash);restored++;
                }
            }finally{
                if(staged.exists()&&!staged.delete())staged.deleteOnExit();
            }
        }
        return restored;
    }
}
