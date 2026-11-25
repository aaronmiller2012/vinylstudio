package com.halftonestudio;

import java.io.*;
import java.util.function.Consumer;

/** Optional data must never prevent writing the editable photo/settings archive. */
final class ProjectSaveSupport {
    private ProjectSaveSupport(){}
    static StudioProjectArchiveWriter.SourceSupplier portableOriginal(File activeSource,byte[] restoredSource){
        if(activeSource!=null&&activeSource.isFile()&&activeSource.length()>0&&
                activeSource.length()<=ProjectArchiveReader.MAX_SOURCE_ENTRY)
            return ()->new BufferedInputStream(new FileInputStream(activeSource));
        if(restoredSource!=null&&restoredSource.length>0&&restoredSource.length<=ProjectArchiveReader.MAX_SOURCE_ENTRY)
            return ()->new ByteArrayInputStream(restoredSource);
        return null;
    }
    interface Thumbnail { byte[] encode() throws IOException; }
    static byte[] optionalThumbnail(Thumbnail artwork,Thumbnail photo,Consumer<Throwable> warn){
        for(Thumbnail candidate:new Thumbnail[]{artwork,photo}){
            if(candidate==null)continue;
            try{
                byte[] data=candidate.encode();
                if(data==null||data.length==0)continue;
                ThumbnailHeader.validate(data);
                return data;
            }catch(IOException|RuntimeException|OutOfMemoryError error){warn.accept(error);}
        }
        return null;
    }
}
