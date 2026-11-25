package com.halftonestudio;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/** Host-only golden signatures. Run on the NAS/JVM, not Android's math runtime.
 * --bless deliberately replaces baselines; ordinary runs are exact comparisons.
 */
public final class GoldenGeometryHostTest {
    private static final String[] PHOTOS={"portrait","highcontrast","dim"};
    private static final File ROOT=new File("tests/golden");
    private static final File GOLD=new File(ROOT,"geometry-signatures.tsv");
    private static final String HEADER="case\tlayers\tloops\tpoints\tarea01mm2\tlength01mm\tsha256";
    private static void need(boolean condition,String detail){if(!condition)throw new AssertionError(detail);}
    private static long q(float x){return Math.round((double)x*100.0);}
    private static String hex(byte[] dig){StringBuilder out=new StringBuilder();for(byte b:dig)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    private static final class Metrics {
        int layers,loops,points;double area,length;
        MessageDigest md;
        Metrics()throws Exception{md=MessageDigest.getInstance("SHA-256");}
        void part(String content){md.update(content.getBytes(StandardCharsets.UTF_8));}
        void addPoly(float[] xy,boolean closed){
            int n=xy.length/2;points+=n;
            part(closed?"C":"O");part(Integer.toString(n));part(":");
            for(int j=0;j<n;j++){
                long x=q(xy[2*j]),y=q(xy[2*j+1]);
                part(x+","+y+";");
                if(j>0){length+=Math.hypot(x-q(xy[2*j-2]),y-q(xy[2*j-1]))/100.;}
            }
            if(closed&&n>2){
                length+=Math.hypot(q(xy[0])-q(xy[xy.length-2]),q(xy[1])-q(xy[xy.length-1]))/100.;
                double twice=0;
                for(int j=0;j<n;j++){
                    int k=(j+1)%n;
                    twice+=(double)q(xy[2*j])*q(xy[2*k+1])-(double)q(xy[2*k])*q(xy[2*j+1]);
                }
                area+=Math.abs(twice)/20000.;
            }
        }
        String line(String key){return key+"\t"+layers+"\t"+loops+"\t"+points+"\t"+
            Math.round(area*100)+"\t"+Math.round(length*100)+"\t"+hex(md.digest());}
    }
    private static String measure(String name,StyleKit.Result result)throws Exception{
        Metrics m=new Metrics();m.layers=result.layers.size();
        for(StyleKit.Layer layer:result.layers){
            m.part("L:"+layer.name+":"+layer.color+":"+q(layer.strokeMm)+"|");
            for(float[] loop:layer.loops){m.loops++;m.addPoly(loop,true);}
            for(float[] line:layer.lines)m.addPoly(line,false);
        }
        return m.line(name);
    }
    private static String measureStandard(String name,VinylEngine.Result result)throws Exception{
        Metrics m=new Metrics();m.layers=result.settings.layers.size();
        for(int i=0;i<result.shapes.size();i++){
            m.part("L:"+i+":"+result.settings.layers.get(i).color+"|");
            for(VinylEngine.Shape shape:result.shapes.get(i)){
                if(shape.circle){
                    long x=q(shape.x),y=q(shape.y),r=q(shape.r);
                    m.loops++;m.points+=12;
                    m.area+=Math.PI*r*r/10000.;m.length+=2*Math.PI*r/100.;
                    m.part("C:"+x+","+y+","+r+";");
                }else{
                    float[][] loops=shape.contours==null?new float[][]{shape.xy}:shape.contours;
                    for(float[] ring:loops){m.loops++;m.addPoly(ring,true);}
                }
            }
        }
        // Exact canonical 0.01mm coordinate hash avoids Android svg-output formats.
        return m.line(name);
    }
    private static int[] pixels(String photo)throws IOException{
        BufferedImage b=ImageIO.read(new File(ROOT,photo+".png"));
        need(b!=null,"Missing image fixture "+photo);
        int[] out=new int[b.getWidth()*b.getHeight()];
        b.getRGB(0,0,b.getWidth(),b.getHeight(),out,0,b.getWidth());return out;
    }
    private static int variants(int style){
        if(style==VinylStyleSuite.BANKNOTE)return BanknoteEngravingEngine.VARIANTS.length;
        if(style==VinylStyleSuite.TESSELLATION_MOTIF)return TessellationMotifEngine.PATTERNS.length;
        if(style==VinylStyleSuite.TRUCHET)return TruchetTilesEngine.Pattern.values().length;
        if(style==VinylStyleSuite.TEXT)return 3;
        if(style==VinylStyleSuite.TSP||style==VinylStyleSuite.LINOCUT||
            style==VinylStyleSuite.TOPOGRAPHIC||style==VinylStyleSuite.RIDGE)return 2;
        return 1;
    }
    private static boolean withinApprovedTolerance(String original,String next){
        String[] a=original.split("\\t",-1),b=next.split("\\t",-1);
        if(a.length!=7||b.length!=7||!a[0].equals(b[0])||!a[1].equals(b[1]))return false;
        try{
            // Metrics are quantized to hundredths of a millimeter / mm squared.
            double loopsA=Double.parseDouble(a[2]),loopsB=Double.parseDouble(b[2]);
            double areaA=Double.parseDouble(a[4]),areaB=Double.parseDouble(b[4]);
            double lengthA=Double.parseDouble(a[5]),lengthB=Double.parseDouble(b[5]);
            return Math.abs(loopsA-loopsB)<=Math.max(1,loopsA*.01)&&
                Math.abs(areaA-areaB)<=Math.max(1,areaA*.005)&&
                Math.abs(lengthA-lengthB)<=Math.max(1,lengthA*.005);
        }catch(NumberFormatException invalid){return false;}
    }
    public static void main(String[] args)throws Exception{
        boolean bless=args.length==1&&"--bless".equals(args[0]);
        boolean tolerance=args.length==1&&"--approved-tolerances".equals(args[0]);
        need(args.length==0||bless||tolerance,
            "Usage: GoldenGeometryHostTest [--bless|--approved-tolerances]");
        List<String> actual=new ArrayList<>();actual.add(HEADER);
        int done=0;
        for(String photo:PHOTOS){
            int[] input=pixels(photo);int w=48,h=60;
            // Historical golden signatures stay byte-for-byte stable. RC141's appended
            // engines have their own deterministic host suite and do not re-bless these baselines.
            for(int style=0;style<=VinylStyleSuite.TESSELLATION_MOTIF;style++){
                if(VinylStyleSuite.restoredStyleId(style)<0)continue;
                for(int variant=0;variant<variants(style);variant++){
                    if(style==VinylStyleSuite.BANKNOTE && variant==BanknoteEngravingEngine.RETIRED_GUILLOCHE)continue;
                    VinylStyleSuite.Config c=new VinylStyleSuite.Config();
                    c.style=style;c.variant=variant;c.detail=35;c.colorCount=3;
                    c.widthMm=78;c.minimumMm=.35f;c.weightMm=.7f;c.leadMm=.9f;
                    c.palette=new int[]{0xff212a30,0xffab5e70,0xffdcbd63};
                    c.words="PORTRAIT";
                    String caseId="style-"+style+"-"+variant+"-"+photo;
                    try{
                        actual.add(measure(caseId,VinylStyleSuite.generate(input,w,h,c)));
                    }catch(IllegalArgumentException invalid){
                        // An uncuttable fixed configuration remains a regression
                        // case. A change from failure to valid geometry requires
                        // deliberate re-blessing rather than silently passing.
                        actual.add(caseId+"\tEXPECTED_REJECTION\t"+invalid.getMessage());
                    }
                    done++;
                }
            }
            // All core pattern families, preserving current physical defaults.
            for(int pattern=0;pattern<VinylEngine.PATTERNS.length;pattern++){
                VinylEngine.Settings settings=VinylEngine.Settings.defaults();
                settings.pattern=pattern;settings.widthMm=78f;settings.marginMm=1.5f;
                settings.minimumMm=.35f;settings.registration=false;
                settings.masterSpacingMm=4f;
                for(VinylEngine.Layer l:settings.layers)l.spacing=4f;
                actual.add(measureStandard("core-"+pattern+"-"+photo,
                    VinylEngine.generate(input,w,h,settings)));
                done++;
            }
        }
        if(bless){Files.write(GOLD.toPath(),actual,StandardCharsets.UTF_8);
            System.out.println("BLESSED "+done+" cases -> "+GOLD);return;}
        need(GOLD.isFile(),"Golden file missing; intentional baselines must be created with --bless");
        List<String> expected=Files.readAllLines(GOLD.toPath(),StandardCharsets.UTF_8);
        need(expected.size()==actual.size(),"Golden case count changed: "+expected.size()+" != "+actual.size());
        for(int i=0;i<actual.size();i++){
            if(actual.get(i).equals(expected.get(i)))continue;
            if(tolerance&&withinApprovedTolerance(expected.get(i),actual.get(i)))continue;
            throw new AssertionError("Golden mismatch at "+i+"\nEXPECTED "+expected.get(i)+
                "\nACTUAL   "+actual.get(i));
        }
        System.out.println("PASS "+done+" host-only deterministic golden images across 3 image types");
    }
}
