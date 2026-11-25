package com.halftonestudio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.*;

/** Checks the exact exported project archive contract, without any Android runtime. */
public final class RC86StabilityTest {
    private static void ok(boolean test,String msg){if(!test)throw new AssertionError(msg);}
    private static final byte[] IMAGE={1,2,3,4,5};
    private static byte[] write(byte[] original) throws Exception {
        ByteArrayOutputStream result=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(result)) {
            StudioProjectArchiveWriter.write(zip,
                out->{try{out.write(IMAGE);return true;}catch(IOException e){throw new UncheckedIOException(e);}},
                out->{try{out.write(IMAGE);return true;}catch(IOException e){throw new UncheckedIOException(e);}},
                "{\"schema\":18}", original==null?null:()->new ByteArrayInputStream(original));
        }
        return result.toByteArray();
    }
    private static ProjectArchiveReader.ProjectEntries read(byte[] z) throws Exception {
        try(ZipInputStream stream=new ZipInputStream(new ByteArrayInputStream(z))){return ProjectArchiveReader.read(stream);}
    }
    private static void bad(byte[] zip,String error) throws Exception {
        try{read(zip);throw new AssertionError(error);}catch(IOException expected){/* good */}
    }
    public static void main(String[]args) throws Exception {
        byte[] source=new byte[512_003];
        for(int i=0;i<source.length;i++)source[i]=(byte)((i*131+17)>>>4);
        ProjectArchiveReader.ProjectEntries a=read(write(source));
        ok(Arrays.equals(a.photo,IMAGE),"Preview bytes changed");
        ok(Arrays.equals(a.original,IMAGE),"Legacy project original bytes changed");
        ok(Arrays.equals(a.nativeSource,source),"Portable original lost or modified");
        ProjectArchiveReader.ProjectEntries b=read(write(a.nativeSource));
        ok(Arrays.equals(b.nativeSource,source),"Re-save lost the source photograph");
        ok(read(write(null)).nativeSource==null,"Original should be optional for old archives");
        ok(read(write(new byte[]{7})).nativeSource.length==1,"Single-byte original lost");
        byte[] over=new byte[(int)ProjectArchiveReader.MAX_SOURCE_ENTRY+1];
        boolean refused=false;
        try{write(over);}catch(IOException expected){refused=true;}
        ok(refused,"Oversize camera source incorrectly accepted");
        ByteArrayOutputStream duplicate=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(duplicate)){
            zip.putNextEntry(new ZipEntry("photo.png"));zip.write(IMAGE);zip.closeEntry();
            zip.putNextEntry(new ZipEntry("settings.json"));zip.write("{}".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            zip.putNextEntry(new ZipEntry(ProjectArchiveReader.NATIVE_SOURCE_ENTRY));
            zip.write(new byte[(int)ProjectArchiveReader.MAX_SOURCE_ENTRY+1]);zip.closeEntry();
        }
        bad(duplicate.toByteArray(),"Oversize ZIP source accepted by reader");
        System.out.println("PASS RC86: portable source round-trip, legacy ZIP, re-save, source-size boundaries");
    }
}
