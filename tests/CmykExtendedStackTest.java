package com.halftonestudio;
import java.util.*;
public final class CmykExtendedStackTest {
    static void ok(boolean yes,String msg){if(!yes)throw new AssertionError(msg);}
    static int[] photo(){int[] p=new int[27*23];for(int y=0;y<23;y++)for(int x=0;x<27;x++){int r=55+x*6,g=45+y*7,b=80+(x*9+y*4)%120;p[y*27+x]=0xff000000|(Math.min(r,255)<<16)|(Math.min(g,255)<<8)|Math.min(b,255);}return p;}
    static int index(List<VinylEngine.Layer> ls,int channel){for(int i=0;i<ls.size();i++)if(ls.get(i).cmykChannel==channel)return i;return -1;}
    public static void main(String[] args){
        VinylEngine.Settings settings=VinylEngine.Settings.defaults();
        settings.pattern=VinylEngine.ROUND;settings.minimumMm=.3f;
        VinylEngine.Result base=VinylEngine.generate(photo(),27,23,settings);
        String[] svg=new String[4];
        for(int i=0;i<4;i++)svg[i]=VinylEngine.svg(base,i);
        VinylEngine.Layer[] anchors={settings.layers.get(0),settings.layers.get(1),settings.layers.get(2),settings.layers.get(3)};
        for(int count=5;count<=8;count++){
            VinylEngine.CmykExtendedStack.resize(settings.layers,count,settings.masterSpacingMm,settings.masterDensity,settings.masterContrast);
            ok(settings.layers.size()==count,"Expanded count");
            ok(settings.layers.get(0)==anchors[0]&&settings.layers.get(1)==anchors[1]&&settings.layers.get(2)==anchors[2],"Preserve Y/C/M objects");
            ok(settings.layers.get(count-1)==anchors[3],"Black always physically last");
            ok(settings.layers.get(0).cmykChannel==2&&settings.layers.get(count-1).cmykChannel==3,"Anchors retain original channels");
            for(int i=3;i<count-1;i++)ok(settings.layers.get(i).cmykChannel==-1,"Only extra spot layers between M and K");
            VinylEngine.Result result=VinylEngine.generate(photo(),27,23,settings);
            for(int channel: new int[]{2,0,1,3}){
                int orig=channel==2?0:channel==0?1:channel==1?2:3;
                int now=index(result.settings.layers,channel);
                ok(now>=0,"CMYK identity lost");
                ok(VinylEngine.svg(result,now).contains("<svg"),"SVG must render");
                // Compare shape contours and center coordinates, not the layer's XML metadata/order.
                List<VinylEngine.Shape> before=base.shapes.get(orig),after=result.shapes.get(now);
                ok(before.size()==after.size(),"CMYK separation geometry count changed: "+channel);
                for(int j=0;j<before.size();j++){
                    VinylEngine.Shape b=before.get(j),a=after.get(j);
                    ok(b.circle==a.circle&&b.r==a.r&&b.x==a.x&&b.y==a.y&&Arrays.equals(b.xy,a.xy),"CMYK geometry changed for channel "+channel);
                }
            }
        }
        VinylEngine.CmykExtendedStack.resize(settings.layers,4,settings.masterSpacingMm,settings.masterDensity,settings.masterContrast);
        ok(settings.layers.get(3)==anchors[3],"Black last after shrinking");
        ok(settings.layers.size()==4,"Return to 4 original layers");
        VinylEngine.CmykExtendedStack.addSpot(settings.layers,3,1,1);
        ok(!VinylEngine.CmykExtendedStack.removeSpot(settings.layers,0),"Do not remove Y");
        ok(!VinylEngine.CmykExtendedStack.removeSpot(settings.layers,4),"Do not remove K");
        ok(VinylEngine.CmykExtendedStack.removeSpot(settings.layers,3),"Remove added accent");
        ok(settings.layers.size()==4,"Only 4 anchors remain");
        System.out.println("PASS: CMYK 4→5→6→7→8→4, physical anchors, unchanged CMYK geometric cuts, protected channels");
    }
}
