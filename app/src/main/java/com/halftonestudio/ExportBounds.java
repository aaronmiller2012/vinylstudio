package com.halftonestudio;
import java.io.IOException;
/** Guard export loops against unsupported or malicious layer counts. */
public final class ExportBounds {
    private ExportBounds() {}
    public static void requireLayers(int count) throws IOException {
        if(count<1||count>8) throw new IOException("Unsupported export layer count: "+count+" (maximum 8)");
    }
}
