package com.halftonestudio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

public final class StageOneSecurityTest {
    private static byte[] archive(String...names) throws Exception {
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        try(ZipOutputStream z=new ZipOutputStream(b)) {
            for(String n:names) {
                z.putNextEntry(new ZipEntry(n));
                byte[] data=n.equals("settings.json")?"{}".getBytes(StandardCharsets.UTF_8):new byte[]{1,2,3};
                z.write(data);z.closeEntry();
            }
        }return b.toByteArray();
    }
    private static ProjectArchiveReader.ProjectEntries parse(byte[] zip)throws IOException {
        try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(zip))) {return ProjectArchiveReader.read(z);}
    }
    private static void rejects(byte[] zip,String id)throws Exception {
        try{parse(zip);throw new AssertionError("Accepted "+id);}catch(IOException expected){/* good */}
    }
    public static void main(String[]args)throws Exception {
        ProjectArchiveReader.ProjectEntries p=parse(archive("photo.png","original.png","settings.json"));
        if(p.photo.length!=3||p.settings.length!=2)throw new AssertionError("Legacy project format");
        rejects(archive("photo.png","photo.jpg","settings.json"),"duplicate photo aliases");
        rejects(archive("../photo.png","settings.json"),"traversal");
        String[] tooMany=new String[18];for(int i=0;i<tooMany.length;i++)tooMany[i]="extra"+i;
        rejects(archive(tooMany),"entry cap");
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        try(ZipOutputStream z=new ZipOutputStream(b)) {
            z.putNextEntry(new ZipEntry("photo.png"));z.write(new byte[]{1});z.closeEntry();
            z.putNextEntry(new ZipEntry("settings.json"));byte[] buf=new byte[8192];
            for(int i=0;i<130;i++)z.write(buf);z.closeEntry();
        }rejects(b.toByteArray(),"zip bomb settings");
        ExportBounds.requireLayers(8);
        try{ExportBounds.requireLayers(9);throw new AssertionError("Over-count export");}catch(IOException expected){}
        try{ExportBounds.requireLayers(0);throw new AssertionError("Zero export");}catch(IOException expected){}
        System.out.println("PASS: StageOneSecurityTest (legacy ZIP, limits, paths, export caps)");
    }
}
