package com.halftonestudio;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
/** Documents are replaced, never appended to. Works with SAF mode-aware output APIs. */
final class LibraryDocumentStreams {
    interface Opener { OutputStream open(String mode) throws IOException; }
    static final String TRUNCATE_MODE="wt";
    private LibraryDocumentStreams(){}
    static OutputStream truncate(Opener opener) throws IOException {
        OutputStream result=opener.open(TRUNCATE_MODE);
        if(result==null)throw new FileNotFoundException("Destination document could not be opened");
        return result;
    }
    /** The only way library jobs (project export, damaged-project recovery export,
     * library backup) may reach a user-selected document. The opener is invoked
     * lazily, so a job that fails before writing never truncates the destination. */
    static StudioLibraryJobs.OutputTarget document(Opener opener){
        if(opener==null)throw new IllegalArgumentException("opener");
        return ()->truncate(opener);
    }
}
