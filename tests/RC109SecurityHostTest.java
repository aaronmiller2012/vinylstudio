package com.halftonestudio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Malicious project-archive probes. Android-free, bounded and deterministic. */
public final class RC109SecurityHostTest {
    private static void check(boolean pass,String label) {
        if(!pass)throw new AssertionError(label);
    }
    private static byte[] archive(String... names)throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(out)) {
            for(String name:names) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(name.equals("settings.json")?"{}".getBytes(StandardCharsets.UTF_8):new byte[]{1});
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
    private static void rejected(String label,byte[] bytes)throws Exception {
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            try {ProjectArchiveReader.read(zip);throw new AssertionError(label+" accepted");}
            catch(IOException expected) { /* expected rejection */ }
        }
    }
    public static void main(String[] args)throws Exception {
        for(String bad:new String[]{"../photo.png","..\\photo.png","/photo.png",
                "dir/photo.png","photo.png/../evil","photo.png\\evil","../settings.json"}) {
            String[] names=bad.equals("../settings.json")?
                    new String[]{"photo.png",bad}:new String[]{bad,"settings.json"};
            rejected("path traversal: "+bad,archive(names));
        }
        rejected("duplicate photo aliases",archive("photo.png","photo.jpg","settings.json"));
        rejected("missing photo",archive("settings.json"));
        rejected("missing settings",archive("photo.png"));
        Random random=new Random(0x109BEEF);
        for(int i=0;i<180;i++) {
            String name="extra"+i+"_"+Integer.toHexString(random.nextInt());
            if(i%3==0)name="../"+name;
            if(i%3==1)name="nested/"+name;
            if(i%3==2)name="..\\"+name;
            rejected("hostile extra filename "+i,archive("photo.png","settings.json",name));
        }
        // No path is ever materialized on disk by this parser.
        byte[] safe=archive("photo.png","settings.json");
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(safe))) {
            check(ProjectArchiveReader.read(zip).settings.length==2,"valid archive rejected");
        }
        System.out.println("PASS RC109: 180 hostile-name cases, traversal/duplicates, legacy ZIP behavior");
    }
}
