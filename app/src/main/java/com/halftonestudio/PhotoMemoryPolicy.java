package com.halftonestudio;

/** Conservative, allocation-aware decoded preview cap for low-memory Android devices. */
final class PhotoMemoryPolicy {
    private PhotoMemoryPolicy() { }
    static int previewLimit(int requested,long heapBytes,int memoryClassMb,boolean lowRam){
        if(requested<128)throw new IllegalArgumentException("preview cap too small");
        long heapMb=Math.max(1,heapBytes/(1024L*1024L));
        long available=Math.min(heapMb,Math.max(1,memoryClassMb));
        // Peak photo editing keeps multiple bitmaps, an int[] and a preview render.
        // Limit decoded dimensions on low-memory devices instead of waiting for OOM.
        int maximum=(lowRam||available<144)?960:available<224?1280:available<320?1600:2400;
        return Math.min(requested,maximum);
    }
}
