package com.halftonestudio;

/** Build 553 host policy regressions (no Android framework types). */
public final class V3Product553PolicyTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static final long DAY=24L*60*60*1000;
    static void backupReminder(){
        long now=1_800_000_000_000L;
        check(!BackupReminderPolicy.shouldRemind(2,0,0,now),"two projects must not nag");
        check(BackupReminderPolicy.shouldRemind(3,0,0,now),"never backed up with 3 projects must remind");
        check(!BackupReminderPolicy.shouldRemind(9,now-29*DAY,0,now),"recent backup must not remind");
        check(BackupReminderPolicy.shouldRemind(9,now-31*DAY,0,now),"stale backup must remind");
        check(!BackupReminderPolicy.shouldRemind(9,0,now-2*DAY,now),"dismissal snoozes for a week");
        check(BackupReminderPolicy.shouldRemind(9,0,now-8*DAY,now),"snooze expires");
        check(BackupReminderPolicy.shouldRemind(9,now+5*DAY,0,now),"future backup time (clock change) must not suppress forever");
        check(BackupReminderPolicy.message(4,0,34).contains("phone-to-phone transfer"),"API 31+ message");
        check(BackupReminderPolicy.message(4,0,29).contains("cannot move"),"pre-31 message");
    }
    static java.io.InputStream zip(String[][] entries) throws java.io.IOException {
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        try(java.util.zip.ZipOutputStream z=new java.util.zip.ZipOutputStream(bytes)){
            for(String[] e:entries){z.putNextEntry(new java.util.zip.ZipEntry(e[0]));z.write(e[1].getBytes("UTF-8"));z.closeEntry();}
        }
        return new java.io.ByteArrayInputStream(bytes.toByteArray());
    }
    static void sharedExports() throws Exception {
        java.io.File root=java.nio.file.Files.createTempDirectory("vinyl-553-share-").toFile();
        try{
            java.util.List<java.io.File> sheets=SharedExportFiles.extractCutSheets(zip(new String[][]{
                {"sheets/01 Black.svg","<svg/>"},{"other/01 Black.svg","<svg id='b'/>"},{"02 Cyan.dxf","0\nEOF"},
                {"README.txt","x"},{"preview.png","x"},{"../../evil.svg","<svg/>"}}),root);
            check(sheets.size()==4,"only SVG/DXF sheets extracted: "+sheets.size());
            java.util.Set<String> names=new java.util.HashSet<>();for(java.io.File f:sheets){names.add(f.getName());
                check(f.getParentFile().getParentFile().getCanonicalFile().equals(root.getCanonicalFile()),"extracted outside share folder");}
            check(names.contains("01 Black.svg")&&names.contains("01 Black-2.svg"),"duplicate names disambiguated: "+names);
            check(names.contains("evil.svg"),"traversal entry flattened, not followed");
            check(!new java.io.File(root.getParentFile(),"evil.svg").exists(),"zip-slip write");
            String rel=SharedExportFiles.relativePath(root,sheets.get(0));
            check(SharedExportFiles.resolve(root,rel.split("/")).equals(sheets.get(0).getCanonicalFile()),"round trip");
            for(String[] bad:new String[][]{{"..","x.svg"},{"a","../x"},{"a"},{"a","b","c"},{"",""},{"a","x\\y"}}){
                try{SharedExportFiles.resolve(root,bad);throw new AssertionError("accepted unsafe path "+String.join("/",bad));}
                catch(java.io.IOException expected){}
            }
            try{SharedExportFiles.relativePath(root,new java.io.File(root.getParentFile(),"outside.svg"));throw new AssertionError("outside file shared");}
            catch(java.io.IOException expected){}
            int before=root.listFiles().length;
            try{SharedExportFiles.extractCutSheets(zip(new String[][]{{"a.txt","x"}}),root);throw new AssertionError("empty export shared");}
            catch(java.io.IOException expected){}
            check(root.listFiles().length==before,"failed extraction must leave nothing behind");
            check("image/svg+xml".equals(SharedExportFiles.mimeType("A.SVG"))&&"image/vnd.dxf".equals(SharedExportFiles.mimeType("b.dxf")),"mime");
            java.io.File old=sheets.get(0).getParentFile();
            check(old.setLastModified(System.currentTimeMillis()-SharedExportFiles.EXPIRY_MS-60_000),"mtime");
            check(SharedExportFiles.prune(root,System.currentTimeMillis())==1&&!old.exists(),"expired share pruned");
        }finally{
            java.util.function.Consumer<java.io.File>[] del=new java.util.function.Consumer[1];
            del[0]=f->{java.io.File[] c=f.listFiles();if(c!=null)for(java.io.File x:c)del[0].accept(x);f.delete();};del[0].accept(root);
        }
    }
    static void engineBuild() throws Exception {
        check(EngineBuild.regeneratedByNewerEngine(0)&&EngineBuild.regeneratedByNewerEngine(552),"older builds flagged");
        check(!EngineBuild.regeneratedByNewerEngine(EngineBuild.CURRENT)&&!EngineBuild.regeneratedByNewerEngine(EngineBuild.CURRENT+1),"same/newer not flagged");
        check(EngineBuild.notice(0).contains("earlier VinylStudio build")&&EngineBuild.notice(540).contains("build 540"),"notice text");
        check(!EngineBuild.ackToken("a").equals(EngineBuild.ackToken("b"))&&EngineBuild.ackToken(null).endsWith("external"),"ack tokens");
    }
    static void photoPairing() throws Exception {
        java.io.File dir=java.nio.file.Files.createTempDirectory("vinyl-553-pair-").toFile();
        try{
            PhotoRevisionStore.commit(dir,(photo,original,prior)->{
                java.nio.file.Files.write(photo.toPath(),new byte[]{1});java.nio.file.Files.write(original.toPath(),new byte[]{2});
                PhotoPairToken.write(photo,"proc0001-r2");
            });
            java.io.File active=PhotoRevisionStore.photo(dir);
            check("proc0001-r2".equals(PhotoPairToken.read(active)),"token committed with pair");
            check(PhotoPairToken.compare("proc0001-r2",PhotoPairToken.read(active))==PhotoPairToken.Verdict.MATCH,"match");
            // Settings persisted for a newer photo state, photo job killed before commit.
            check(PhotoPairToken.compare("proc0001-r3",PhotoPairToken.read(active))==PhotoPairToken.Verdict.MISMATCH,"mismatch detected");
            // A failed staged commit must not change the active token.
            try{PhotoRevisionStore.commit(dir,(photo,original,prior)->{PhotoPairToken.write(photo,"proc0001-r4");throw new java.io.IOException("killed");});}
            catch(java.io.IOException expected){}
            check("proc0001-r2".equals(PhotoPairToken.read(PhotoRevisionStore.photo(dir))),"aborted commit leaked token");
            check(PhotoPairToken.compare("","proc0001-r2")==PhotoPairToken.Verdict.LEGACY,"pre-553 settings trusted");
            check(PhotoPairToken.compare("proc0001-r2","")==PhotoPairToken.Verdict.LEGACY,"pre-553 photo trusted");
            check(!PhotoPairToken.valid("../x")&&!PhotoPairToken.valid("short"),"token validation");
        }finally{PhotoRevisionStore.clear(dir);dir.delete();}
    }
    public static void main(String[] args) throws Exception {
        photoPairing();
        backupReminder();
        sharedExports();
        engineBuild();
        System.out.println("PASS 553 backup reminder, shared cut-sheet files, engine build and photo/settings pairing");
    }
}
