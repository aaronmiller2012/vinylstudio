package com.halftonestudio;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

/**
 * Read-only, non-exported provider that lets the user hand finished cut sheets to
 * Cricut Design Space, Silhouette Studio or any other app through the Android share
 * sheet. It serves only regular files inside cache/shared-exports, and receiving
 * apps gain access solely through the per-share URI grant. Writes are refused except
 * for the single pre-created file in a camera-* capture folder.
 */
public final class SharedExportProvider extends ContentProvider {
    static final String AUTHORITY="com.halftonestudio.sharedexports";
    static final String DIRECTORY="shared-exports";

    static File root(Context context){return new File(context.getCacheDir(),DIRECTORY);}

    static Uri uriFor(Context context,File file) throws IOException {
        String relative=SharedExportFiles.relativePath(root(context),file);
        Uri.Builder builder=new Uri.Builder().scheme("content").authority(AUTHORITY);
        for(String part:relative.split("/"))builder.appendPath(part);
        return builder.build();
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        Context context=getContext();
        if(context==null||uri==null||!AUTHORITY.equals(uri.getAuthority()))throw new FileNotFoundException("Unknown share");
        List<String> segments=uri.getPathSegments();
        try{
            File file=SharedExportFiles.resolve(root(context),segments.toArray(new String[0]));
            if(!file.isFile())throw new FileNotFoundException("Shared file expired");
            return file;
        }catch(IOException invalid){throw new FileNotFoundException("Invalid shared file");}
    }

    @Override public boolean onCreate(){return true;}

    @Override public String getType(Uri uri){
        List<String> segments=uri.getPathSegments();
        return segments.isEmpty()?null:SharedExportFiles.mimeType(segments.get(segments.size()-1));
    }

    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        // Camera capture (build 554): a camera app may write ONLY into a camera-* folder that
        // VinylStudio created for one capture, and only through the per-intent write grant.
        if(mode!=null&&!mode.equals("r")&&SharedExportFiles.isCameraCapture(uri.getPathSegments())&&
                (mode.equals("w")||mode.equals("wt")||mode.equals("rw")||mode.equals("rwt")))
            return ParcelFileDescriptor.open(resolve(uri),ParcelFileDescriptor.parseMode(mode));
        if(mode==null||!mode.equals("r"))throw new SecurityException("Shared cut files are read-only");
        return ParcelFileDescriptor.open(resolve(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        File file;
        try{file=resolve(uri);}catch(FileNotFoundException missing){return null;}
        String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
        MatrixCursor cursor=new MatrixCursor(columns,1);
        Object[] row=new Object[columns.length];
        for(int i=0;i<columns.length;i++){
            if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))row[i]=file.getName();
            else if(OpenableColumns.SIZE.equals(columns[i]))row[i]=file.length();
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("read-only");}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException("read-only");}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException("read-only");}
}
