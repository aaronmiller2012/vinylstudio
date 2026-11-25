package com.halftonestudio;

public final class OutputDeviceProfileTest {
    public static void main(String[] args){
        String[] labels=OutputDeviceProfile.names();
        if(labels.length!=8)throw new AssertionError("Unexpected target list");
        for(int id=0;id<labels.length;id++){
            OutputDeviceProfile.Profile p=OutputDeviceProfile.byId(id);
            if(p.id!=id||!labels[id].equals(p.name))throw new AssertionError("Unstable target IDs");
            if(id==OutputDeviceProfile.DESKTOP_PRINTER){
                if(p.svgCutZip)throw new AssertionError("Printer cannot emit cut files");
                try{OutputDeviceProfile.handoffText(id);throw new AssertionError("PDF target accepted as cutter");}
                catch(IllegalArgumentException expected){}
            }else{
                if(!p.svgCutZip)throw new AssertionError("Missing cut handoff");
                String report=OutputDeviceProfile.handoffText(id);
                if(!report.contains(id==OutputDeviceProfile.SILHOUETTE_BASIC?"DXF R13":"SVG")||!report.contains("no device is connected")||
                   !report.contains(p.name))throw new AssertionError("Misleading cutter handoff");
                if(report.toLowerCase().contains("certified compatible"))throw new AssertionError();
            }
        }
        if(OutputDeviceProfile.byId(-1).id!=OutputDeviceProfile.CRICUT ||
           OutputDeviceProfile.byId(500).id!=OutputDeviceProfile.CRICUT)
            throw new AssertionError("Invalid output preference fallback");
        System.out.println("PASS output target format and safe handoff tests");
    }
}
