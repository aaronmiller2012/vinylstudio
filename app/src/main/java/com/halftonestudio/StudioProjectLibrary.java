package com.halftonestudio;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Private project library with crash-consistent, immutable revisions.
 * RC54 flat id.zip/id.jpg/id.properties projects remain readable without migration.
 * A new revision is staged in one directory, synchronized, and made visible by
 * renaming that directory. The prior revision remains valid until a later save.
 * This is a single-process repository; methods are synchronized but are called
 * from the editor I/O lane, NOT the main/UI thread.
 */
final class StudioProjectLibrary {
    interface ArchiveWriter { void write(OutputStream out) throws IOException; }
    static final class Entry {
        final String id, name, style;
        final long modified;
        /** Unreadable project files remain visible for explicit recovery or deletion. */
        final boolean damaged;
        Entry(String id,String name,String style,long modified){
            this(id,name,style,modified,false);
        }
        Entry(String id,String name,String style,long modified,boolean damaged){
            this.id=id;this.name=name;this.style=style;this.modified=modified;this.damaged=damaged;
        }
    }
    private static final String REV=".rev-", SUFFIX=".bundle";
    private static final long MAX_ARCHIVE_BYTES=90_000_000L;
    private static final int KEEP_REVISIONS=2;
    private final File folder;
    // Gallery reads cache verified revision fingerprints, never archive-open calls.
    private final LinkedHashMap<String,String> verified = new LinkedHashMap<String,String>(64,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,String> oldest){return size()>96;}
    };
    private static String fingerprint(File file){return file.getAbsolutePath()+":"+file.length()+":"+file.lastModified();}
    // Archives whose full verification failed at this exact fingerprint. The gallery's
    // recorded-fingerprint fast path must not keep listing them as healthy: same-size,
    // same-mtime bit-rot otherwise leaves a project that lists, cannot open, and is
    // refused by recovery export.
    private final LinkedHashMap<String,String> failed = new LinkedHashMap<String,String>(16,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,String> oldest){return size()>96;}
    };
    private boolean knownBad(File file){return fingerprint(file).equals(failed.get(file.getAbsolutePath()));}
    private boolean usable(File file,boolean fresh){
        String key=file.getAbsolutePath(),stamp=fingerprint(file);
        if(!fresh && stamp.equals(verified.get(key)))return true;
        try{verifyArchive(file);verifyArchivePayload(file);verified.put(key,stamp);failed.remove(key);return true;}
        // ZipInputStream reports malformed entry names as IllegalArgumentException; any
        // parser failure on one damaged archive must not escape and break the whole library.
        catch(IOException|RuntimeException problem){verified.remove(key);failed.put(key,stamp);return false;}
    }
    private static final Map<String,StudioProjectLibrary> SHARED=new HashMap<>();
    static synchronized StudioProjectLibrary shared(File appFiles){
        String key=appFiles.getAbsoluteFile().toPath().normalize().toString();
        StudioProjectLibrary result=SHARED.get(key);
        if(result==null){result=new StudioProjectLibrary(new File(key));SHARED.put(key,result);}
        return result;
    }
    StudioProjectLibrary(File appFiles){folder=new File(appFiles,"project-library");}
    private static boolean valid(String id){return id!=null&&id.matches("[a-f0-9]{8}-[a-f0-9-]{27,36}");}
    /** Bounded private staging is never exposed in the project gallery. */
    synchronized File newRestoreTemp() throws IOException {
        ensureFolder();
        return File.createTempFile("vinyl-restore-", ".part", folder);
    }
    /** Staging on private storage finishes before any user-selected document is opened. */
    synchronized File newBackupTemp() throws IOException {
        ensureFolder();
        return File.createTempFile("vinyl-backup-", ".part", folder);
    }
    private void ensureFolder() throws IOException {
        if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Cannot create project library");
    }
    private File asset(String id,String ext){
        if(!valid(id))throw new IllegalArgumentException("Invalid library project ID");
        return new File(folder,id+ext);
    }
    private static String safeLabel(String name,String fallback){
        String result=name==null?"":name.replace('\n',' ').replace('\r',' ').trim();
        return result.isEmpty()?fallback:result.length()>70?result.substring(0,70):result;
    }
    private static void move(File from,File to) throws IOException {
        if(!from.renameTo(to))throw new IOException("Could not commit "+to.getName());
    }
    private static void writeProperties(File target,Entry entry,String rev) throws IOException {
        Properties p=new Properties();p.setProperty("name",entry.name);p.setProperty("style",entry.style);
        p.setProperty("modified",Long.toString(entry.modified));p.setProperty("revision",rev);
        try(FileOutputStream out=new FileOutputStream(target)){p.store(out,"VinylStudio project");out.getFD().sync();}
    }
    private static String hashFile(File file) throws IOException {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream in=new BufferedInputStream(new FileInputStream(file))){
                byte[] buf=new byte[32768];int n;
                while((n=in.read(buf))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new IOException("Archive hashing cancelled");
                    digest.update(buf,0,n);
                }
            }
            return Base64.getEncoder().encodeToString(digest.digest());
        }catch(NoSuchAlgorithmException absent){throw new IOException("SHA-256 unavailable",absent);}
    }
    private static Properties readPropertiesStrict(File file) throws IOException {
        Properties p=new Properties();
        try(FileInputStream in=new FileInputStream(file)){p.load(in);}
        return p;
    }
    private static void persistArchiveHash(File metadata,File archive,String hash) throws IOException {
        // Strict read: a damaged card must never be replaced by one holding only the
        // fingerprint, which would silently erase the project name and style.
        // A missing card (RC54 flat legacy layout) is created; nothing can be lost.
        Properties props=metadata.exists()?readPropertiesStrict(metadata):new Properties();
        if(metadata.exists()&&props.getProperty("name")==null&&props.getProperty("revision")==null&&
                props.getProperty("archiveSha256")==null)
            throw new IOException("Project card metadata unreadable; fingerprint not cached");
        props.setProperty("archiveSha256",hash);
        props.setProperty("archiveBytes",Long.toString(archive.length()));
        props.setProperty("archiveModified",Long.toString(archive.lastModified()));
        File staging=File.createTempFile("vinyl-hash-",".tmp",metadata.getParentFile());
        try {
            try(FileOutputStream out=new FileOutputStream(staging)){
                props.store(out,"VinylStudio project archive fingerprint");out.getFD().sync();
            }
            try {Files.move(staging.toPath(),metadata.toPath(),
                StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(java.nio.file.AtomicMoveNotSupportedException fallback){
                Files.move(staging.toPath(),metadata.toPath(),StandardCopyOption.REPLACE_EXISTING);
            }
        }finally{if(staging.exists())staging.delete();}
    }
    /** Read a verified revision's saved SHA-256 without rehashing 90 MB on every restore.
     * Legacy or changed archives are hashed once and cached with file size + mtime.
     * The existing ZIP format is not modified. */
    synchronized String archiveDigest(String id) throws IOException {
        if(!valid(id))throw new IOException("Invalid project ID");
        File bundle=currentBundle(id); // reuses the verified-revision cache
        File archive=bundle==null?asset(id,".zip"):new File(bundle,"project.zip");
        if(!archive.isFile())throw new IOException("Project archive unavailable");
        File metadata=bundle==null?asset(id,".properties"):new File(bundle,"card.properties");
        Properties props=readProperties(metadata);
        String digest=props.getProperty("archiveSha256","");
        if(digest.matches("[A-Za-z0-9+/]{43}=")&&
           Long.toString(archive.length()).equals(props.getProperty("archiveBytes"))&&
           Long.toString(archive.lastModified()).equals(props.getProperty("archiveModified")))
            return digest;
        digest=hashFile(archive);
        // Caching is an optimization only. Legacy cards without a name, or cards that
        // cannot be read, keep their original bytes and are simply hashed again later.
        try{persistArchiveHash(metadata,archive,digest);}
        catch(IOException cacheOnly){/* digest is still correct for this call */}
        return digest;
    }
    private static Properties readProperties(File file){
        Properties p=new Properties();
        if(file.isFile())try(FileInputStream in=new FileInputStream(file)){p.load(in);}catch(IOException ignored){}
        return p;
    }
    private static boolean removeTree(File f){
        if(f.isDirectory()){
            File[] nested=f.listFiles();if(nested!=null)for(File child:nested)if(!removeTree(child))return false;
        }
        return !f.exists()||f.delete();
    }
    private List<File> versions(String id){
        File[] found=folder.listFiles((dir,name)->name.startsWith(id+REV)&&name.endsWith(SUFFIX));
        List<File> list=new ArrayList<>();if(found!=null)for(File f:found)if(f.isDirectory())list.add(f);
        list.sort((a,b)->b.getName().compareTo(a.getName()));return list;
    }
    private static String revisionName(String id,long modified){
        return id+REV+String.format(Locale.US,"%019d",modified)+"-"+UUID.randomUUID()+SUFFIX;
    }
    static void verifyProjectArchive(File file) throws IOException {
        verifyArchive(file);verifyArchivePayload(file);
    }
    private static void verifyArchive(File file) throws IOException {
        if(!file.isFile()||file.length()==0||file.length()>MAX_ARCHIVE_BYTES)throw new IOException("Invalid project ZIP size");
        long declared=0;
        ProjectArchiveReader.EntryPolicy policy=new ProjectArchiveReader.EntryPolicy();
        try(ZipFile zip=new ZipFile(file)){
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()){
                ZipEntry e=entries.nextElement();
                ProjectArchiveReader.EntryPolicy.EntryInfo info=policy.accept(e.getName());
                long size=e.getSize();if(size<0)throw new IOException("Unknown ZIP entry size");
                if(size>info.limit||(declared+=size)>ProjectArchiveReader.MAX_UNCOMPRESSED)
                    throw new IOException("Project ZIP entry too large");
            }
        }
        policy.requireComplete();
    }
    private static void verifyArchivePayload(File file) throws IOException {
        byte[] buf=new byte[32*1024];long sum=0;boolean hasSettings=false;
        ProjectArchiveReader.EntryPolicy policy=new ProjectArchiveReader.EntryPolicy();
        Map<String,ProjectManifest.Digest> digests=new LinkedHashMap<>();
        ProjectManifest manifest=null;
        try(java.util.zip.ZipInputStream zip=new java.util.zip.ZipInputStream(new FileInputStream(file))){
            ZipEntry entry;while((entry=zip.getNextEntry())!=null){
                ProjectArchiveReader.EntryPolicy.EntryInfo info=policy.accept(entry.getName());
                String n=entry.getName();long max=info.limit;
                ByteArrayOutputStream settings=n.equals("settings.json")?new ByteArrayOutputStream():null;
                boolean isManifest=n.equals(ProjectManifest.ENTRY);
                ByteArrayOutputStream manifestBytes=isManifest?new ByteArrayOutputStream():null;
                java.security.MessageDigest entryDigest=ProjectManifest.sha256();
                boolean image=info.image();
                ByteArrayOutputStream header=image?new ByteArrayOutputStream(96*1024):null;
                ProjectImageSignature.PngChunks png=n.endsWith(".png")&&image?
                    new ProjectImageSignature.PngChunks():null;
                byte[] tail=image?new byte[12]:null;int tailCount=0;
                long size=0;int read;while((read=zip.read(buf))!=-1){
                    size+=read;sum+=read;
                    if(size>max||sum>ProjectArchiveReader.MAX_UNCOMPRESSED)
                        throw new IOException("Project ZIP decompressed data too large");
                    if(settings!=null)settings.write(buf,0,read);
                    if(manifestBytes!=null)manifestBytes.write(buf,0,read);else entryDigest.update(buf,0,read);
                    if(image){
                        if(png!=null)png.accept(buf,0,read);
                        int taken=Math.min(read,96*1024-header.size());
                        if(taken>0)header.write(buf,0,taken);
                        for(int k=0;k<read;k++){tail[tailCount%12]=buf[k];tailCount++;}
                    }
                }
                zip.closeEntry();
                if(image){
                    if(png!=null)png.finish();
                    byte[] orderedTail=new byte[Math.min(12,tailCount)];
                    for(int k=0;k<orderedTail.length;k++)
                        orderedTail[k]=tail[(tailCount-orderedTail.length+k)%12];
                    ProjectImageSignature.requireValidSuffix(n,header.toByteArray(),size,orderedTail);
                }
                if(settings!=null){JsonSyntaxVerifier.requireObject(settings.toByteArray());hasSettings=true;}
                if(isManifest)manifest=ProjectManifest.parse(manifestBytes.toByteArray());
                else digests.put(n,new ProjectManifest.Digest(ProjectManifest.hex(entryDigest.digest()),size));
            }
        }
        policy.requireComplete();
        if(!hasSettings)throw new IOException("No valid settings JSON");
        // Format 2: when a manifest exists, the archive must match it exactly.
        if(manifest!=null)manifest.verify(digests);
    }
    /** Gallery fast path: a committed revision whose archive still matches the SHA-256,
     * byte length and mtime recorded at save time was fully verified before publication.
     * Listing trusts that record instead of decompressing every project; opening a
     * project ({@link #archive}) always re-verifies the complete payload. */
    static boolean matchesRecordedFingerprint(File zip,File metadata){
        if(!zip.isFile()||!metadata.isFile())return false;
        Properties p=readProperties(metadata);
        return p.getProperty("archiveSha256","").matches("[A-Za-z0-9+/]{43}=")&&
            Long.toString(zip.length()).equals(p.getProperty("archiveBytes"))&&
            Long.toString(zip.lastModified()).equals(p.getProperty("archiveModified"));
    }
    private File listedBundle(String id){
        for(File dir:versions(id)){
            File candidate=new File(dir,"project.zip");
            if(knownBad(candidate))continue;
            if(matchesRecordedFingerprint(candidate,new File(dir,"card.properties"))||usable(candidate,false))return dir;
        }
        return null;
    }
    /** Choose newest usable revision, automatically falling back if latest is corrupt. */
    private File currentBundle(String id){return currentBundle(id,false);}
    private File currentBundle(String id,boolean fresh){
        for(File dir:versions(id)){
            File candidate=new File(dir,"project.zip");
            if(usable(candidate,fresh))return dir;
        }
        return null;
    }
    synchronized File archive(String id){
        File bundle=currentBundle(id,true);
        if(bundle!=null)return new File(bundle,"project.zip");
        File legacy=asset(id,".zip");
        return usable(legacy,true)?legacy:new File(folder,id+".unavailable");
    }
    /** Open inside the monitor so a concurrent prune/delete cannot race the open.
     * On Android/Linux an open descriptor remains readable after unlink. */
    synchronized InputStream openArchive(String id) throws IOException {
        return new BufferedInputStream(new FileInputStream(archive(id)));
    }
    /** Recovery export is intentionally NOT used by project open or backup.
     * Copies untrusted original bytes without treating them as a working project. */
    synchronized InputStream openDamagedArchiveForRecovery(String id) throws IOException {
        // Decided by a fresh full verification, not the gallery fingerprint: a project that
        // lists as healthy but cannot be opened must still be recoverable.
        if(!valid(id) || describe(id)==null || archive(id).isFile())throw new IOException("Not an unreadable project");
        for(File dir:versions(id)){
            File raw=new File(dir,"project.zip");
            if(raw.isFile()&&raw.length()>0){
                if(raw.length()>MAX_ARCHIVE_BYTES)throw new IOException("Damaged archive exceeds recovery size limit");
                return new BufferedInputStream(new FileInputStream(raw));
            }
        }
        File legacy=asset(id,".zip");
        if(legacy.isFile()&&legacy.length()>0){
            if(legacy.length()>MAX_ARCHIVE_BYTES)throw new IOException("Damaged archive exceeds recovery size limit");
            return new BufferedInputStream(new FileInputStream(legacy));
        }
        throw new FileNotFoundException("Unreadable project has no archive available for recovery");
    }
    synchronized File preview(String id){
        File bundle=listedBundle(id);return bundle!=null?new File(bundle,"preview.jpg"):asset(id,".jpg");
    }
    private Entry describe(String id){
        File bundle=listedBundle(id);
        File zip=bundle!=null?new File(bundle,"project.zip"):asset(id,".zip");
        // Do not hide the only remaining archive if every revision is damaged.
        // Keep it separate from usable projects; normal project opens still fail closed.
        boolean damaged=false;
        if(!zip.isFile() || (bundle==null && (knownBad(zip) ||
                (!matchesRecordedFingerprint(zip,asset(id,".properties")) && !usable(zip,false))))){
            damaged=true;
            List<File> revisions=versions(id);
            File newest=revisions.isEmpty()?null:revisions.get(0);
            if(newest!=null){
                zip=new File(newest,"project.zip");
                bundle=newest;
            }else{
                zip=asset(id,".zip");
            }
            if(!zip.isFile()){
                // A committed revision may have lost its archive entirely. Keep its
                // surviving card available for explicit recovery/deletion.
                File card=bundle==null?asset(id,".properties"):new File(bundle,"card.properties");
                if(!card.isFile())return null;
            }
        }
        String rev=bundle==null?"legacy":bundle.getName();
        Properties p=readProperties(bundle==null?asset(id,".properties"):new File(bundle,"card.properties"));
        Properties override=readProperties(asset(id,".label.properties"));
        if(rev.equals(override.getProperty("revision")))p=override;
        long modified=zip.lastModified();
        if(bundle!=null)try{modified=Long.parseLong(readProperties(new File(bundle,"card.properties")).getProperty("modified"));}catch(Exception ignored){}
        return new Entry(id,safeLabel(p.getProperty("name"),damaged?"Unreadable project":"Untitled project"),
            safeLabel(p.getProperty("style"),"Photo"),modified,damaged);
    }
    synchronized Entry get(String id){
        Entry entry=valid(id)?describe(id):null;
        return entry==null||entry.damaged?null:entry;
    }
    private void cleanOldPending(){
        File[] pending=folder.listFiles((dir,name)->name.contains(".pending-"));
        File[] staleImports=folder.listFiles((dir,name)->(name.startsWith("vinyl-restore-")||
            name.startsWith("vinyl-backup-"))&&name.endsWith(".part"));
        if(staleImports!=null)for(File file:staleImports)
            if(file.isFile()&&System.currentTimeMillis()-file.lastModified()>3_600_000L)
                file.delete();
        if(pending!=null)for(File f:pending){
            if(f.isDirectory()&&System.currentTimeMillis()-f.lastModified()>3_600_000L){
                String id=f.getName().substring(0,f.getName().indexOf(".pending-"));
                if(valid(id))removeTree(f);
            }
        }
    }
    synchronized List<Entry> list(){
        cleanOldPending();
        Set<String> ids=new HashSet<>();File[] files=folder.listFiles();
        if(files!=null)for(File f:files){String n=f.getName();
            if(f.isFile()&&n.endsWith(".zip")){String id=n.substring(0,n.length()-4);if(valid(id))ids.add(id);}
            else if(f.isDirectory()&&n.contains(REV)&&n.endsWith(SUFFIX)){
                String id=n.substring(0,n.indexOf(REV));if(valid(id))ids.add(id);
            }
        }
        List<Entry> result=new ArrayList<>();for(String id:ids){Entry e=describe(id);if(e!=null)result.add(e);}
        result.sort((a,b)->Long.compare(b.modified,a.modified));return result;
    }
    /** Commit only after complete project archive, preview and metadata exist in a staged directory. */
    synchronized Entry save(String oldId,String name,String style,byte[] thumbnail,ArchiveWriter writer) throws IOException {
        return save(oldId,name,style,thumbnail,writer,null);
    }
    static final String EXPORTED_CUTS="exported-cuts.zip";
    static final long MAX_EXPORTED_CUTS_BYTES=32L*1024*1024;
    /** A verified cut ZIP is kept only if it is a readable ZIP holding SVG/DXF sheets. */
    static boolean validExportedCuts(File zip){
        if(zip==null||!zip.isFile()||zip.length()==0||zip.length()>MAX_EXPORTED_CUTS_BYTES)return false;
        try(ZipFile z=new ZipFile(zip)){
            Enumeration<? extends ZipEntry> e=z.entries();
            while(e.hasMoreElements())if(SharedExportFiles.isCutSheet(e.nextElement().getName()))return true;
        }catch(IOException|RuntimeException unreadable){return false;}
        return false;
    }
    /** Cut sheets exactly as last verified and exported for this saved revision, or null. */
    synchronized File exportedCuts(String id){
        if(!valid(id))return null;
        File bundle=listedBundle(id);
        File cuts=bundle==null?null:new File(bundle,EXPORTED_CUTS);
        return cuts!=null&&cuts.isFile()?cuts:null;
    }
    /** @param exportedCuts the verified cut ZIP exported from exactly this edit, or null.
     *  Stored beside (not inside) project.zip so portable project ZIPs stay readable by
     *  older builds; a later save without a matching export drops it with the revision. */
    synchronized Entry save(String oldId,String name,String style,byte[] thumbnail,ArchiveWriter writer,File exportedCuts) throws IOException {
        ensureFolder();
        String id=oldId!=null&&valid(oldId)&&get(oldId)!=null?oldId:UUID.randomUUID().toString();
        Entry before=get(id);long modified=Math.max(System.currentTimeMillis(),before==null?0:before.modified+1);
        Entry next=new Entry(id,safeLabel(name,"Untitled project"),safeLabel(style,"Photo"),modified);
        File pending=new File(folder,id+".pending-"+UUID.randomUUID());
        if(!pending.mkdir())throw new IOException("Could not stage project");
        File committed=new File(folder,revisionName(id,modified));
        boolean success=false;
        try {
            File zip=new File(pending,"project.zip");
            try(FileOutputStream out=new FileOutputStream(zip)){writer.write(out);out.getFD().sync();}
            verifyArchive(zip);
            // Read all ZIP members once before publication. ZipInputStream checks CRC
            // and verifies compressed data; catalog reads use the faster ZipFile index.
            verifyArchivePayload(zip);
            if(thumbnail!=null&&thumbnail.length>0){
                ThumbnailHeader.validate(thumbnail);
                try(FileOutputStream out=new FileOutputStream(new File(pending,"preview.jpg"))){out.write(thumbnail);out.getFD().sync();}
            }
            if(validExportedCuts(exportedCuts)){
                File copy=new File(pending,EXPORTED_CUTS);
                Files.copy(exportedCuts.toPath(),copy.toPath());
                try(FileOutputStream sync=new FileOutputStream(copy,true)){sync.getFD().sync();}
            }
            writeProperties(new File(pending,"card.properties"),next,committed.getName());
            // Cache the immutable revision fingerprint once at save time. All
            // later restores read this card metadata instead of its full ZIP.
            persistArchiveHash(new File(pending,"card.properties"),zip,hashFile(zip));
            // Atomic single-directory publication: no reader can see mixed revisions.
            move(pending,committed);success=true;
            verified.put(new File(committed,"project.zip").getAbsolutePath(),
                fingerprint(new File(committed,"project.zip")));
            // Preserve the old RC54 flat archive as the rollback for the first
            // revision; retire it only after at least two revision bundles exist.
            List<File> revisions=versions(id);
            if(revisions.size()>=KEEP_REVISIONS){
                for(File legacy:new File[]{asset(id,".zip"),asset(id,".jpg"),asset(id,".properties")})legacy.delete();
            }
            asset(id,".label.properties").delete();
            // Do not erase the only valid rollback merely because a newer
            // revision directory exists but its payload is corrupt.
            int kept=0;
            for(File revision:revisions){
                File candidate=new File(revision,"project.zip");
                if(usable(candidate,false)){
                    if(++kept>KEEP_REVISIONS)removeTree(revision);
                }
            }
            return next;
        }finally{if(!success)removeTree(pending);}
    }
    synchronized Entry rename(String id,String name) throws IOException {
        Entry old=get(id);if(old==null)throw new IOException("Project no longer exists");ensureFolder();
        File bundle=listedBundle(id);String rev=bundle==null?"legacy":bundle.getName();
        Entry renamed=new Entry(id,safeLabel(name,old.name),old.style,old.modified);
        File pending=File.createTempFile(id+"-",".label.pending",folder);
        try{writeProperties(pending,renamed,rev);move(pending,asset(id,".label.properties"));return renamed;}
        finally{pending.delete();}
    }
    synchronized void delete(String id) throws IOException {
        if(!valid(id)||describe(id)==null)throw new IOException("Project no longer exists");
        for(File f:versions(id))if(!removeTree(f))throw new IOException("Could not delete project revision");
        for(String suffix:new String[]{".zip",".jpg",".properties",".label.properties"}){
            File f=asset(id,suffix);if(f.exists()&&!f.delete())throw new IOException("Could not delete project file");
        }
    }
}
