package com.halftonestudio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Host-testable file rules behind {@link SharedExportProvider}: traversal-safe paths,
 * MIME types, bounded extraction of cutter sheets from an exported cut ZIP, and expiry.
 */
final class SharedExportFiles {
    static final int MAX_SHEETS=200;
    static final long MAX_TOTAL_BYTES=96L*1024*1024;
    static final long MAX_SHEET_BYTES=48L*1024*1024;
    static final long EXPIRY_MS=24L*60*60*1000;
    private SharedExportFiles() { }

    static String mimeType(String name){
        String n=name==null?"":name.toLowerCase(Locale.ROOT);
        if(n.endsWith(".svg"))return "image/svg+xml";
        if(n.endsWith(".dxf"))return "image/vnd.dxf";
        if(n.endsWith(".zip"))return "application/zip";
        if(n.endsWith(".png"))return "image/png";
        if(n.endsWith(".jpg")||n.endsWith(".jpeg"))return "image/jpeg";
        if(n.endsWith(".pdf"))return "application/pdf";
        return "application/octet-stream";
    }

    static final String CAMERA_PREFIX="camera-";
    /** Only {@code camera-<uuid>/capture.jpg} may be opened for writing by a camera app. */
    static boolean isCameraCapture(List<String> segments){
        return segments!=null&&segments.size()==2&&segments.get(0).startsWith(CAMERA_PREFIX)&&
            segments.get(0).length()>CAMERA_PREFIX.length()&&"capture.jpg".equals(segments.get(1));
    }
    /** Creates an empty capture target the camera app will fill. */
    static File newCameraCapture(File root) throws IOException {
        prune(root,System.currentTimeMillis());
        File dir=new File(root,CAMERA_PREFIX+UUID.randomUUID());
        if(!dir.mkdirs())throw new IOException("Cannot create camera folder");
        File target=new File(dir,"capture.jpg");
        if(!target.createNewFile())throw new IOException("Cannot create camera file");
        return target;
    }

    static boolean isCutSheet(String name){
        String n=name==null?"":name.toLowerCase(Locale.ROOT);
        return n.endsWith(".svg")||n.endsWith(".dxf");
    }

    /** A single safe path segment; never empty, dot-only, or containing separators. */
    static boolean safeSegment(String part){
        return part!=null&&!part.isEmpty()&&part.length()<=128&&!part.equals(".")&&!part.equals("..")&&
            part.indexOf('/')<0&&part.indexOf('\\')<0&&part.indexOf('\0')<0;
    }

    static File resolve(File root,String... segments) throws IOException {
        if(segments==null||segments.length!=2)throw new IOException("Shared path must be <share>/<file>");
        for(String part:segments)if(!safeSegment(part))throw new IOException("Unsafe shared path");
        File canonicalRoot=root.getCanonicalFile();
        File file=new File(new File(canonicalRoot,segments[0]),segments[1]).getCanonicalFile();
        File parent=file.getParentFile();
        if(parent==null||!canonicalRoot.equals(parent.getParentFile()))throw new IOException("Shared path escapes root");
        return file;
    }

    static String relativePath(File root,File file) throws IOException {
        File canonicalRoot=root.getCanonicalFile(),canonical=file.getCanonicalFile();
        File share=canonical.getParentFile();
        if(share==null||!canonicalRoot.equals(share.getParentFile()))throw new IOException("File is not a shared export");
        return share.getName()+"/"+canonical.getName();
    }

    /** Keeps the sheet's own name (color/layer label), flattened and made filesystem-safe. */
    static String flatName(String entryName,Set<String> used){
        String base=entryName.replace('\\','/');
        base=base.substring(base.lastIndexOf('/')+1).replaceAll("[^A-Za-z0-9 ._()+-]","_").trim();
        if(base.isEmpty()||base.startsWith("."))base="sheet"+base;
        if(base.length()>100){
            int dot=base.lastIndexOf('.');
            String ext=dot>0?base.substring(dot):"";
            base=base.substring(0,100-ext.length())+ext;
        }
        String candidate=base;int n=2;
        while(!used.add(candidate.toLowerCase(Locale.ROOT))){
            int dot=base.lastIndexOf('.');
            candidate=dot>0?base.substring(0,dot)+"-"+n+base.substring(dot):base+"-"+n;n++;
        }
        return candidate;
    }

    /** Extract every SVG/DXF sheet into a fresh share folder. Nothing is kept on failure. */
    static List<File> extractCutSheets(InputStream zipBytes,File root) throws IOException {
        prune(root,System.currentTimeMillis());
        File share=new File(root,UUID.randomUUID().toString());
        if(!share.mkdirs())throw new IOException("Cannot create share folder");
        List<File> sheets=new ArrayList<>();
        Set<String> used=new HashSet<>();
        boolean ok=false;long total=0;byte[] buffer=new byte[32768];
        try(ZipInputStream zip=new ZipInputStream(zipBytes)){
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){
                if(Thread.currentThread().isInterrupted())throw new IOException("Share preparation cancelled");
                if(entry.isDirectory()||!isCutSheet(entry.getName()))continue;
                if(sheets.size()>=MAX_SHEETS)throw new IOException("Too many cut sheets to share");
                File out=new File(share,flatName(entry.getName(),used));
                long size=0;
                try(FileOutputStream stream=new FileOutputStream(out)){
                    int read;
                    while((read=zip.read(buffer))!=-1){
                        size+=read;total+=read;
                        if(size>MAX_SHEET_BYTES||total>MAX_TOTAL_BYTES)throw new IOException("Cut sheets too large to share");
                        stream.write(buffer,0,read);
                    }
                }
                if(size==0)throw new IOException("Empty cut sheet in export");
                sheets.add(out);
            }
            if(sheets.isEmpty())throw new IOException("Export has no SVG or DXF sheets");
            ok=true;
            return sheets;
        }finally{if(!ok)deleteTree(share);}
    }

    /** Small text entry (by base name) from a cut ZIP, bounded to 256 KB; null if absent. */
    static String readTextEntry(InputStream zipBytes,String baseName) throws IOException {
        try(ZipInputStream zip=new ZipInputStream(zipBytes)){
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){
                String n=entry.getName().replace('\\','/');
                if(entry.isDirectory()||!n.substring(n.lastIndexOf('/')+1).equals(baseName))continue;
                java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
                byte[] buf=new byte[8192];int read;
                while((read=zip.read(buf))!=-1){out.write(buf,0,read);if(out.size()>256*1024)throw new IOException("Report too large");}
                return new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    /** Remove share folders older than a day; receiving apps copy files on import. */
    static int prune(File root,long nowMs){
        File[] shares=root.listFiles();
        int removed=0;
        if(shares!=null)for(File share:shares){
            long age=nowMs-share.lastModified();
            if(age>EXPIRY_MS||age<-EXPIRY_MS){deleteTree(share);removed++;}
        }
        return removed;
    }

    private static void deleteTree(File file){
        File[] children=file.listFiles();
        if(children!=null)for(File child:children)deleteTree(child);
        file.delete();
    }
}
