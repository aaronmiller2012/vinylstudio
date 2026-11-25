package com.halftonestudio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Links the autosaved photo pair (PhotoRevisionStore) with the editor settings kept in
 * SharedPreferences. The two stores commit independently, so a process kill between
 * them could pair new settings (Snip outlines in pixel coordinates, crop, layers) with
 * the previous photo. The token is written inside the staged photo revision before its
 * atomic rename and stored beside the editor JSON; a restore compares the two.
 */
final class PhotoPairToken {
    static final String FILE="pair-token.txt";
    static final String PREF="editorPhotoToken";
    enum Verdict { MATCH, LEGACY, MISMATCH }
    private PhotoPairToken() { }

    static boolean valid(String token){return token!=null&&token.matches("[A-Za-z0-9-]{8,80}");}

    /** Write into the staged revision directory that holds {@code stagedPhoto}. */
    static void write(File stagedPhoto,String token) throws IOException {
        if(!valid(token))return;
        File target=new File(stagedPhoto.getParentFile(),FILE);
        try(FileOutputStream out=new FileOutputStream(target)){
            out.write(token.getBytes(StandardCharsets.US_ASCII));out.getFD().sync();
        }
    }

    /** Token of the committed pair containing {@code photo}; "" for legacy pairs. */
    static String read(File photo){
        if(photo==null||photo.getParentFile()==null)return "";
        File file=new File(photo.getParentFile(),FILE);
        if(!file.isFile()||file.length()>128)return "";
        try{
            String token=new String(Files.readAllBytes(file.toPath()),StandardCharsets.US_ASCII).trim();
            return valid(token)?token:"";
        }catch(IOException unreadable){return "";}
    }

    /** Data written before build 553 carries no token on one side; it is trusted as before. */
    static Verdict compare(String settingsToken,String photoToken){
        if(!valid(settingsToken)||!valid(photoToken))return Verdict.LEGACY;
        return settingsToken.equals(photoToken)?Verdict.MATCH:Verdict.MISMATCH;
    }
}
