package com.halftonestudio;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Remembers the cut ZIP most recently verified and saved by the user, keyed by the exact
 * editor state (in-process edit revision + autosave photo token) it was exported from.
 * A library save attaches it only when that key still matches, so a project never
 * carries cut sheets from a different edit. Pure Java; called from disk workers only.
 */
final class LastVerifiedExport {
    static final String DIR="last-verified-export";
    private static final String ZIP="cuts.zip", META="key.properties";
    private LastVerifiedExport() { }

    static void store(File filesDir,InputStream savedZip,long editRevision,String photoToken) throws IOException {
        File dir=new File(filesDir,DIR);
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cannot create export snapshot folder");
        clear(filesDir);
        File part=new File(dir,ZIP+".part");
        long total=0;
        try(InputStream in=savedZip;FileOutputStream out=new FileOutputStream(part)){
            if(in==null)throw new IOException("Saved ZIP unavailable");
            byte[] buf=new byte[32768];int n;
            while((n=in.read(buf))!=-1){
                total+=n;
                if(total>StudioProjectLibrary.MAX_EXPORTED_CUTS_BYTES)throw new IOException("Cut ZIP too large to keep with the project");
                out.write(buf,0,n);
            }
            out.getFD().sync();
        }catch(IOException failure){part.delete();throw failure;}
        if(!StudioProjectLibrary.validExportedCuts(part)){part.delete();throw new IOException("Saved ZIP holds no cut sheets");}
        Files.move(part.toPath(),new File(dir,ZIP).toPath(),StandardCopyOption.REPLACE_EXISTING);
        Properties key=new Properties();
        key.setProperty("editRevision",Long.toString(editRevision));
        key.setProperty("photoToken",photoToken==null?"":photoToken);
        try(FileOutputStream out=new FileOutputStream(new File(dir,META))){key.store(out,"VinylStudio verified export key");out.getFD().sync();}
    }

    /** The stored cut ZIP if it was exported from exactly this editor state, else null. */
    static File matching(File filesDir,long editRevision,String photoToken){
        File dir=new File(filesDir,DIR),zip=new File(dir,ZIP),meta=new File(dir,META);
        if(!zip.isFile()||!meta.isFile()||photoToken==null||photoToken.isEmpty())return null;
        Properties key=new Properties();
        try(FileInputStream in=new FileInputStream(meta)){key.load(in);}catch(IOException unreadable){return null;}
        boolean same=Long.toString(editRevision).equals(key.getProperty("editRevision"))&&
            photoToken.equals(key.getProperty("photoToken"));
        return same?zip:null;
    }

    static void clear(File filesDir){
        File dir=new File(filesDir,DIR);
        new File(dir,META).delete();new File(dir,ZIP).delete();new File(dir,ZIP+".part").delete();
    }
}
