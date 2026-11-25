package com.halftonestudio;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Project format 2 (build 554): {@code manifest.json} is written LAST into every project
 * ZIP and lists the SHA-256 and byte length of every other entry plus the engine build.
 * Readers that find a manifest require the archive to match it exactly (no missing,
 * extra or altered entries). Archives without one (format 1, builds <= 553) remain valid.
 * Older builds read manifest.json as a small unknown entry and ignore it.
 * Pure Java with a fixed, strictly parsed layout so host tests need no org.json.
 */
final class ProjectManifest {
    static final String ENTRY="manifest.json";
    static final int FORMAT=2;
    static final long MAX_BYTES=16*1024;
    private static final Pattern ROW=Pattern.compile("\"([A-Za-z0-9._-]{1,64})\":\\{\"sha256\":\"([0-9a-f]{64})\",\"bytes\":(\\d{1,12})\\}");
    private static final Pattern HEAD=Pattern.compile("^\\{\"format\":(\\d+),\"engineBuild\":(\\d+),\"entries\":\\{(.*)\\}\\}$");

    static final class Digest {
        final String sha256;final long bytes;
        Digest(String sha256,long bytes){this.sha256=sha256;this.bytes=bytes;}
    }
    final int format,engineBuild;
    final Map<String,Digest> entries;
    ProjectManifest(int format,int engineBuild,Map<String,Digest> entries){
        this.format=format;this.engineBuild=engineBuild;this.entries=entries;
    }

    static MessageDigest sha256(){
        try{return MessageDigest.getInstance("SHA-256");}
        catch(NoSuchAlgorithmException absent){throw new IllegalStateException("SHA-256 unavailable",absent);}
    }
    static String hex(byte[] digest){
        StringBuilder b=new StringBuilder(64);
        for(byte x:digest)b.append(Character.forDigit((x>>4)&15,16)).append(Character.forDigit(x&15,16));
        return b.toString();
    }

    /** Writer-side accumulator; wrap each entry's bytes with {@link #entry}. */
    static final class Builder {
        private final LinkedHashMap<String,Digest> entries=new LinkedHashMap<>();
        OutputStream entry(String name,OutputStream target){
            if(ENTRY.equals(name)||entries.containsKey(name))throw new IllegalArgumentException("Duplicate manifest entry "+name);
            final MessageDigest digest=sha256();
            return new OutputStream(){
                long bytes;boolean closed;
                @Override public void write(int b) throws IOException {target.write(b);digest.update((byte)b);bytes++;}
                @Override public void write(byte[] b,int off,int len) throws IOException {target.write(b,off,len);digest.update(b,off,len);bytes+=len;}
                @Override public void flush() throws IOException {target.flush();}
                /** Records the entry; does NOT close the shared ZIP stream. */
                @Override public void close(){if(!closed){closed=true;entries.put(name,new Digest(hex(digest.digest()),bytes));}}
            };
        }
        String toJson(){
            StringBuilder b=new StringBuilder("{\"format\":").append(FORMAT).append(",\"engineBuild\":").append(EngineBuild.CURRENT).append(",\"entries\":{");
            boolean first=true;
            for(Map.Entry<String,Digest> e:entries.entrySet()){
                if(!first)b.append(',');first=false;
                b.append('"').append(e.getKey()).append("\":{\"sha256\":\"").append(e.getValue().sha256).append("\",\"bytes\":").append(e.getValue().bytes).append('}');
            }
            return b.append("}}").toString();
        }
    }

    static ProjectManifest parse(byte[] json) throws IOException {
        if(json.length>MAX_BYTES)throw new IOException("Project manifest too large");
        String s=new String(json,StandardCharsets.UTF_8).trim();
        Matcher head=HEAD.matcher(s);
        if(!head.matches())throw new IOException("Malformed project manifest");
        int format=Integer.parseInt(head.group(1));
        if(format<2)throw new IOException("Unsupported project manifest format");
        LinkedHashMap<String,Digest> rows=new LinkedHashMap<>();
        String body=head.group(3);
        Matcher row=ROW.matcher(body);int at=0;
        while(at<body.length()){
            if(!row.find(at)||row.start()!=at)throw new IOException("Malformed project manifest entry");
            if(rows.put(row.group(1),new Digest(row.group(2),Long.parseLong(row.group(3))))!=null)
                throw new IOException("Duplicate project manifest entry");
            at=row.end();
            if(at<body.length()){if(body.charAt(at)!=',')throw new IOException("Malformed project manifest");at++;}
        }
        if(rows.isEmpty())throw new IOException("Empty project manifest");
        return new ProjectManifest(format,Integer.parseInt(head.group(2)),rows);
    }

    /** Exact match: every listed entry present with identical digest, nothing unlisted. */
    void verify(Map<String,Digest> actual) throws IOException {
        for(Map.Entry<String,Digest> e:entries.entrySet()){
            Digest a=actual.get(e.getKey());
            if(a==null)throw new IOException("Project entry missing: "+e.getKey());
            if(a.bytes!=e.getValue().bytes||!a.sha256.equals(e.getValue().sha256))
                throw new IOException("Project entry altered: "+e.getKey());
        }
        for(String name:actual.keySet())
            if(!entries.containsKey(name))throw new IOException("Unlisted project entry: "+name);
    }
}
