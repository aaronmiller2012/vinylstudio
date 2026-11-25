package com.halftonestudio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.File;

/** Host-only positive/negative regression for Fix7's added safety behavior. */
public final class V3Fix7HardeningTest {
    private static void ok(boolean b,String s){if(!b)throw new AssertionError(s);}
    private static void rejected(String svg){
        try{CutSvgCompatibility.verify(svg,OutputDeviceProfile.SILHOUETTE_BASIC);
            throw new AssertionError("Accepted unsupported SVG: "+svg);
        }catch(IllegalArgumentException expected){}
    }
    public static void main(String[] args)throws Exception {
        ok(LoadValueConstraints.finiteRange(Double.NaN,0f,10f,2f)==2f,"NaN passed into geometry");
        ok(LoadValueConstraints.finiteRange(Double.POSITIVE_INFINITY,0f,10f,2f)==2f,"Infinity passed into geometry");
        ok(LoadValueConstraints.finiteRange(-2,0f,10f,2f)==0f,"Negative passed physical bounds");
        ok(LoadValueConstraints.finiteRange(3,0f,10f,2f)==3f,"Valid legacy value changed");
        String base="<svg width='100mm' height='100mm'><path d='M0 0 L100 0 L100 100 Z' ";
        rejected(base+"\n href='https://example.org/resource'/></svg>");
        rejected(base+"	 href='https://example.org/resource'/></svg>");
        rejected(base+" xlink:href = 'resource'/></svg>");
        rejected("<svg><style>.x{fill:red}</style><path d='M0 0L10 10'/></svg>");
        rejected("<svg><symbol id='s'><path d='M0 0L1 1'/></symbol></svg>");
        ok(CutSvgCompatibility.verify("<svg><path d='M0 0L10 0L10 10Z'/></svg>",
            OutputDeviceProfile.SILHOUETTE_BASIC)==1,"App cut path not recognized");
        Path cache=Files.createTempDirectory("v3fix7-");
        try {
            long now=System.currentTimeMillis();
            File old=cache.resolve("vinyl-cut-cancelled.pending.zip").toFile();
            File fresh=cache.resolve("vinyl-cut-active.pending.zip").toFile();
            File unrelated=cache.resolve("reference.pending.zip").toFile();
            ok(old.createNewFile()&&fresh.createNewFile()&&unrelated.createNewFile(),"Fixture creation failed");
            ok(old.setLastModified(now-48L*3600*1000),"Cannot age old fixture");
            ok(unrelated.setLastModified(now-48L*3600*1000),"Cannot age unrelated fixture");
            ok(ProjectTempJanitor.prune(cache.toFile(),now)==1,"Failed to clean only stale export");
            ok(!old.exists()&&fresh.exists()&&unrelated.exists(),"Janitor leaked or deleted an active file");
        }finally{for(File f:cache.toFile().listFiles())f.delete();Files.delete(cache);}
        System.out.println("PASS Fix7 finite inputs, strict SVG compatibility, aged-only export cleanup");
    }
}
