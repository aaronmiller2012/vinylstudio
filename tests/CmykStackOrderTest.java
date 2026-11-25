package com.halftonestudio;

/** Host JVM test for legacy CMYK stacking and palette/channel fidelity. */
public final class CmykStackOrderTest {
    private static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}
    public static void main(String[] args){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.mode=VinylEngine.CMYK;
        s.layers.clear();
        final int[] beforeChannels={0,1,2,3};
        for(int id:beforeChannels){VinylEngine.Layer layer=new VinylEngine.Layer("Channel "+id,0xff220000+(id<<8));
            layer.cmykChannel=id;layer.angle=13+id;layer.lineStyleOverride=id;
            s.layers.add(layer);
        }
        check(CmykStackOrder.needsCorrection(s),"Legacy CMYK order must be detected");
        VinylEngine.Layer yellow=s.layers.get(2),cyan=s.layers.get(0),magenta=s.layers.get(1),black=s.layers.get(3);
        check(CmykStackOrder.normalize(s),"Legacy order must be corrected");
        check(s.layers.get(0)==yellow&&s.layers.get(1)==cyan&&s.layers.get(2)==magenta&&s.layers.get(3)==black,"Must move entire layer objects");
        check(s.layers.get(0).angle==15&&s.layers.get(0).lineStyleOverride==2,"Do not remap screen/shape channels");
        check(!CmykStackOrder.needsCorrection(s),"Corrected order must be accepted");
        check(!CmykStackOrder.normalize(s),"A second conversion must be no-op");
        s.layers.get(0).cmykChannel=0;
        check(!CmykStackOrder.needsCorrection(s),"Missing/duplicate channel IDs must not be silently rewritten");
        s.mode=VinylEngine.SPOT;
        check(!CmykStackOrder.needsCorrection(s),"Custom vinyl stack must remain untouched");
        System.out.println("PASS v2.48.1: channel identity, opt-in reordering, idempotence, malformed imports, custom preservation");
    }
}
