package com.halftonestudio;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.Locale;

/**
 * M5 profiling harness (host, not a gate): final production renders of the Prismatic family
 * with the JTS backend at 4, 6, 8 and 11 in. Reports wall time, bytes allocated by all threads
 * (including the geometry pool) and peak heap after a forced GC.
 *
 * Usage: java -Xmx512m ... PrismaticProfile <photo.png> [repeats] [styleIds,comma]
 * Numbers are host numbers; the phone must be measured separately.
 */
public final class PrismaticProfile {
    static final com.sun.management.ThreadMXBean THREADS=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    static final com.sun.management.OperatingSystemMXBean CPU=(com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
    static long allocated(){
        long[] ids=THREADS.getAllThreadIds();long sum=0;
        for(long b:THREADS.getThreadAllocatedBytes(ids))if(b>0)sum+=b;
        return sum;
    }
    static long peakHeap(){
        long p=0;for(MemoryPoolMXBean m:ManagementFactory.getMemoryPoolMXBeans())
            if(m.getType()==MemoryType.HEAP&&m.getPeakUsage()!=null)p+=m.getPeakUsage().getUsed();
        return p;
    }
    static void resetPeak(){for(MemoryPoolMXBean m:ManagementFactory.getMemoryPoolMXBeans())m.resetPeakUsage();}

    public static void main(String[] a)throws Exception{
        VectorBooleanEngine.install(new JtsVectorBooleanBackend());
        ParallelGeometry.forceSequential=Boolean.getBoolean("profile.sequential");
        M4ArtBench.Photo p=M4ArtBench.load(new java.io.File(a[0]),850);
        int repeats=a.length>1?Integer.parseInt(a[1]):2;
        int[] styles={VinylStyleSuite.PRISMATIC_DIAGONAL,VinylStyleSuite.PRISMATIC_CONTOUR_FLOW,VinylStyleSuite.PRISMATIC_TWILL,
            VinylStyleSuite.PRISMATIC_SILK,VinylStyleSuite.PRISMATIC_BRAIDED_FLOW,VinylStyleSuite.PRISMATIC_CHEVRON};
        if(a.length>2){String[] s=a[2].split(",");styles=new int[s.length];for(int i=0;i<s.length;i++)styles[i]=Integer.parseInt(s[i]);}
        System.out.println("style\tsize_in\tms\talloc_mb\tpeak_heap_mb\tloops\tcpu_ms");
        float[] sizes={4,6,8,11};
        if(System.getProperty("profile.sizes")!=null){String[] z=System.getProperty("profile.sizes").split(",");sizes=new float[z.length];for(int i=0;i<z.length;i++)sizes[i]=Float.parseFloat(z[i]);}
        for(int id:styles)for(float in:sizes){
            long bestMs=Long.MAX_VALUE,alloc=0,peak=0,bestCpu=0;int loops=0;
            for(int r=0;r<repeats;r++){
                System.gc();Thread.sleep(50);resetPeak();
                long a0=allocated(),t0=System.nanoTime(),c0=CPU.getProcessCpuTime();
                StyleKit.Result res=VinylStyleSuite.generate(p.argb,p.w,p.h,M4ArtBench.suiteConfig(id,p,in*25.4f,.8f,.5f));
                long ms=(System.nanoTime()-t0)/1_000_000L;
                long cpu=(CPU.getProcessCpuTime()-c0)/1_000_000L;
                if(ms<bestMs){bestMs=ms;bestCpu=cpu;alloc=allocated()-a0;peak=peakHeap();}
                loops=0;for(StyleKit.Layer l:res.layers)loops+=l.loops.size();
            }
            System.out.printf(Locale.US,"%s\t%.0f\t%d\t%.0f\t%.0f\t%d\t%d%n",VinylStyleSuite.NAMES[id],in,bestMs,alloc/1048576.0,peak/1048576.0,loops,bestCpu);
        }
    }
}
