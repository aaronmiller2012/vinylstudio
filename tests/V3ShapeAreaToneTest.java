package com.halftonestudio;

import java.util.Arrays;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Host-only geometric/photometric regression. No claim of actual cutter or portrait qualification. */
public final class V3ShapeAreaToneTest {
    private static void require(boolean good,String message){if(!good)throw new AssertionError(message);}
    private static void near(double a,double b,double tol,String message){require(Math.abs(a-b)<=tol,message+" ("+a+" vs "+b+")");}
    private static float r0(float spacing,float bridge,float scale,float weight){
        return Math.min(spacing*(float)Math.sqrt(weight),spacing-bridge)*scale*.5f;
    }
    private static void areaCalibration(){
        // Normalizing a polygon's FILLED AREA is not the same as normalizing
        // its circumscribed diameter. Sharp points are not assumed cut-safe.
        int[] shapes={VinylEngine.SQUARE,VinylEngine.ELLIPSE,VinylEngine.DIAMOND,
            VinylEngine.DASH,VinylEngine.HEX,VinylEngine.CROSS,VinylEngine.STAR,
            VinylEngine.TRIANGLE,VinylEngine.OCTAGON,VinylEngine.ROUNDED_SQUARE,
            VinylEngine.TEARDROP,VinylEngine.HEART,VinylEngine.CAPSULE};
        for(float inches:new float[]{6,11,15})for(float weight:new float[]{.08f,.20f,.42f,.68f,.90f,.99f}){
            float spacing=2.79f*(inches/11f),bridge=.25f,scale=.82f;
            float base=r0(spacing,bridge,scale,weight);
            float ceiling=(spacing-bridge)*scale*.5f;
            float roundArea=(float)Math.PI*base*base;
            float inner=VinylEngine.ShapeAreaResponse.starInner(weight);
            for(int shape:shapes){
                float coeff=VinylEngine.ShapeAreaResponse.unitArea(shape,inner);
                require(coeff>0&&Float.isFinite(coeff),"No unit area: "+shape);
                float corrected=VinylEngine.ShapeAreaResponse.radius(shape,base,ceiling,inner);
                require(corrected<=ceiling+.00001f && corrected>=0,"Area mapping escaped physical cell: "+shape);
                float actual=coeff*corrected*corrected;
                float oldArea=coeff*base*base;
                require(Math.abs(actual-roundArea)<=Math.abs(oldArea-roundArea)+.0005f,
                    "Area mapping increased error: "+shape+" / "+inches+" / "+weight);
                require(actual<=roundArea+.0005f,"Mapped motif exceeds target coverage: "+shape);
            }
        }
        float innerLight=VinylEngine.ShapeAreaResponse.starInner(.15f);
        float innerDark=VinylEngine.ShapeAreaResponse.starInner(.95f);
        require(innerLight>=.48f&&innerDark<=.721f&&innerDark>innerLight+.20f,
            "Dark star core did not fill as intended");
        float sp=2.79f,weight=.42f,scale=.82f,bridge=.25f;
        float base=r0(sp,bridge,scale,weight),cap=(sp-bridge)*scale*.5f;
        float inner=VinylEngine.ShapeAreaResponse.starInner(weight);
        float coeff=VinylEngine.ShapeAreaResponse.unitArea(VinylEngine.STAR,inner);
        float revised=coeff*(float)Math.pow(VinylEngine.ShapeAreaResponse.radius(VinylEngine.STAR,base,cap,inner),2);
        float old=VinylEngine.ShapeAreaResponse.unitArea(VinylEngine.STAR,.45f)*base*base;
        require(revised>old*1.3f,"Star midtone coverage did not materially improve");
        near(revised,(float)Math.PI*base*base,.0005,"Unclamped star midtone did not match round area");
        System.out.printf(java.util.Locale.ROOT,"Star midtone: %.3f -> %.3f mm² (Round target %.3f mm²)%n",
            old,revised,Math.PI*base*base);
    }
    private static int[] photograph(int w,int h){
        int[] data=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            // Dark hair, lighter skin, dark eyes and beard; occasional transparent cutouts.
            double nx=(x+.5-w*.5)/w,ny=(y+.5-h*.5)/h;
            double eye=Math.exp(-((nx+.11)*(nx+.11)/.0015+(ny+.05)*(ny+.05)/.0008));
            double beard=Math.max(0,(ny-.10)*2.0);
            int v=(int)Math.max(4,Math.min(245,195-118*eye-118*beard-80*(ny<-.27?1:0)));
            data[y*w+x]=0xff000000|(v<<16)|(v<<8)|v;
            if(x<5&&y<6)data[y*w+x]=0x00ffffff;
        }
        return data;
    }
    private static VinylEngine.Settings settings(float inches,int pattern,boolean snip){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.pattern=pattern;s.mode=VinylEngine.CMYK;s.registration=false;
        s.widthMm=25.4f*inches;s.marginMm=3f;s.bridgeMm=.30f;
        s.minimumMm=.254f;s.shapeScale=.82f;
        float spacing=s.widthMm/63f;
        s.masterSpacingMm=spacing;
        for(VinylEngine.Layer layer:s.layers)layer.spacing=spacing;
        if(snip)s.snips.add(new SnipMask.Operation(SnipMask.REMOVE_INSIDE,
            .02f,.02f,.13f,.02f,.13f,.15f,.02f,.15f));
        return s;
    }
    private static void endToEnd(){
        int w=112,h=124;int[] pixels=photograph(w,h);
        for(float inches:new float[]{6,11,15})for(int style:new int[]{VinylEngine.ROUND,
                VinylEngine.STAR,VinylEngine.SQUARE,VinylEngine.HEX,VinylEngine.HEART}){
            VinylEngine.Settings s=settings(inches,style,true);
            int[] paletteBefore=new int[s.layers.size()];for(int i=0;i<paletteBefore.length;i++)paletteBefore[i]=s.layers.get(i).color;
            VinylEngine.Result result=VinylEngine.generate(pixels,w,h,s);
            VinylEngine.Result again=VinylEngine.generate(pixels,w,h,s);
            int total=0;
            for(int i=0;i<s.layers.size();i++){
                require(s.layers.get(i).color==paletteBefore[i],"Physical swatch modified");
                List<VinylEngine.Shape> cut=result.shapes.get(i);
                for(VinylEngine.Shape sh:cut){
                    total++;
                    require(VinylEngine.insideCutBoundary(sh,result.settings.widthMm,result.heightMm,
                        s.marginMm,s.circleCrop),"Motif beyond physical artboard");
                    if(sh.circle)require(s.snips.allowsCircle(sh.x,sh.y,sh.r,result.settings.widthMm,result.heightMm),
                        "Circle intrudes into Snip");
                    else require(s.snips.allowsPolygon(sh.xy,result.settings.widthMm,result.heightMm),
                        "Polygon intrudes into Snip");
                    if(style==VinylEngine.STAR){
                        require(sh.xy!=null&&sh.xy.length==20,"Star's ten vertices changed");
                        double cx=0,cy=0;for(int k=0;k<20;k+=2){cx+=sh.xy[k];cy+=sh.xy[k+1];}
                        cx/=10;cy/=10;
                        double smallest=Double.POSITIVE_INFINITY;
                        for(int k=1;k<10;k+=2)
                            smallest=Math.min(smallest,Math.hypot(sh.xy[2*k]-cx,sh.xy[2*k+1]-cy));
                        require(2*smallest>=s.minimumMm-.001,
                            "Star's concave core below selected minimum requested width");
                    }
                }
                require(VinylEngine.svg(result,i).equals(VinylEngine.svg(again,i)),
                    "Nondeterministic SVG for "+style+" at "+inches+" inches, sheet "+i);
            }
            require(total>20,"Unreasonably sparse Shapes output: "+style+" at "+inches);
            require(VinylEngine.validateExport(result)==null,"Current cut validation failed "+style+" at "+inches);
            System.out.println("Shapes "+VinylEngine.PATTERNS[style]+" @ "+(int)inches+"in: "+total+" contours");
        }
        // Disabled-layer ownership must never be revived by area adjustment.
        VinylEngine.Settings s=settings(11,VinylEngine.STAR,false);
        s.layers.get(1).enabled=false;
        VinylEngine.Result r=VinylEngine.generate(pixels,w,h,s);
        require(r.shapes.get(1).isEmpty(),"Disabled magenta sheet gained star cuts");
        int[] transparent=new int[w*h];Arrays.fill(transparent,0x00ffffff);
        VinylEngine.Result empty=VinylEngine.generate(transparent,w,h,s);
        for(List<VinylEngine.Shape> layer:empty.shapes)require(layer.isEmpty(),"Transparent pixels acquired cuts");
    }
    private static void unchangedRouteHashes() throws Exception {
        // Digests frozen from the untouched v3 Beta20 source, for the same
        // deterministic four-layer photograph and physical settings.
        String[][] frozen={
            {"0","54b89f2b31bb784b13a84b6437eb58aa9def06b159f037d4ccfaf20f391228cf"},
            {"5","6700d65009d5febd92dd5e84f940d6ebf38ed885ea1b4099c8c17491f2e0da47"},
            // ASCII re-frozen in Fix8: '\\' was a zero-width sliver (wrong bar
            // normal) and '/' '\\' collapsed at minimum size. Round and Line are unchanged.
            {"27","4c72b96775b6bfa6494912ebb89aa2c999d59e91483ff80064145e1ddd500f0d"}
        };
        int w=80,h=90;int[] p=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int a=(x*7+y*11)%256,b=(x*9+y*3)%256,c=(x*5+y*17)%256;
            p[y*w+x]=0xff000000|(a<<16)|(b<<8)|c;
        }
        for(String[] entry:frozen){
            int style=Integer.parseInt(entry[0]);
            VinylEngine.Settings settings=VinylEngine.Settings.defaults();
            settings.pattern=style;settings.widthMm=130f;settings.marginMm=4f;
            settings.registration=false;settings.minimumMm=.254f;
            settings.masterSpacingMm=4.2f;settings.bridgeMm=.35f;settings.shapeScale=.82f;
            for(VinylEngine.Layer l:settings.layers)l.spacing=settings.masterSpacingMm;
            VinylEngine.Result result=VinylEngine.generate(p,w,h,settings);
            StringBuilder svg=new StringBuilder();
            // Build-556 black fill keeps the frozen geometry digests valid (colors: V3ExportSheetContractTest).
            for(int k=0;k<settings.layers.size();k++)svg.append(VinylEngine.svg(result,k,0xff000000));
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(svg.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder();
            for(byte b:digest)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            require(hex.toString().equals(entry[1]),"Unrelated Round, Line or ASCII output changed for style "+style);
        }
    }
    public static void main(String[] args) throws Exception {areaCalibration();endToEnd();unchangedRouteHashes();
        System.out.println("PASS area-normalized Shapes: dark star coverage, 6/11/15in, Snip, CMYK, min core, deterministic SVG; Round/Line/ASCII frozen");}
}
