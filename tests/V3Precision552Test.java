package com.halftonestudio;
import java.util.*;
import java.util.concurrent.CancellationException;

public final class V3Precision552Test {
    static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
    static double distance(double x,double y,float[] p){
        double best=Double.POSITIVE_INFINITY;
        for(int i=2;i<p.length;i+=2){double ax=p[i-2],ay=p[i-1],dx=p[i]-ax,dy=p[i+1]-ay;
            double den=dx*dx+dy*dy,t=den==0?0:Math.max(0,Math.min(1,((x-ax)*dx+(y-ay)*dy)/den));
            best=Math.min(best,Math.hypot(x-ax-t*dx,y-ay-t*dy));}
        return best;
    }
    static double[] evaluate(double[] a,double t){double u=1-t;return new double[]{u*u*u*a[0]+3*u*u*t*a[2]+3*u*t*t*a[4]+t*t*t*a[6],u*u*u*a[1]+3*u*u*t*a[3]+3*u*t*t*a[5]+t*t*t*a[7]};}
    static void curve(double[] a){
        ArrayList<Float> pts=new ArrayList<>();pts.add((float)a[0]);pts.add((float)a[1]);
        PrecisionCurveSampler.cubic(a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],(x,y)->{pts.add(x);pts.add(y);},()->{});
        float[] p=new float[pts.size()];for(int i=0;i<p.length;i++)p[i]=pts.get(i);
        double approx=0;for(int i=2;i<p.length;i+=2)approx+=Math.hypot(p[i]-p[i-2],p[i+1]-p[i-1]);
        double reference=0;double[] last=evaluate(a,0);
        for(int i=1;i<=20000;i++){double[] q=evaluate(a,i/20000.0);reference+=Math.hypot(q[0]-last[0],q[1]-last[1]);last=q;if(i%50==0)require(distance(q[0],q[1],p)<=.0201,"Cubic chord deviation");}
        require(Math.abs(reference-approx)<.0022,"Cubic length error: "+Math.abs(reference-approx));
    }
    static void transformedArcs() throws Exception {
        // Rotated long axis becomes the stretched physical axis, unlike the old radius estimate.
        String arc="<path fill='none' transform='scale(1 3)' d='M10 20 A40 3 90 0 1 10 100'/>";
        String svg="<svg width='400mm' height='400mm' viewBox='0 0 400 400'>"+arc+"</svg>";
        float[] p=SvgDxfR13Exporter.safetyGeometry(svg).openStrokes.get(0);
        double max=0;
        // Arc starts at y=20, ends at y=100, center=(10,60), half ellipse right side.
        for(int i=0;i<=5000;i++){double t=-Math.PI/2+Math.PI*i/5000.0;double x=10+3*Math.cos(t),y=400-3*(60+40*Math.sin(t));max=Math.max(max,distance(x,y,p));}
        require(max<.0201,"Affine arc deviation "+max);
        require(Math.abs(p[p.length-2]-10)<1e-6&&Math.abs(p[p.length-1]-100)<1e-6,"Arc endpoint drift");
        // A shear increases the longest axis beyond either transformed column norm.
        svg="<svg width='300mm' height='300mm' viewBox='0 0 300 300'><ellipse cx='50' cy='50' rx='30' ry='30' transform='matrix(1 0 1 1 0 0)'/></svg>";
        p=SvgDxfR13Exporter.safetyGeometry(svg).closedLoops.get(0);
        float[] closed=Arrays.copyOf(p,p.length+2);closed[p.length]=p[0];closed[p.length+1]=p[1];max=0;
        for(int i=0;i<=5000;i++){double t=2*Math.PI*i/5000;double x=50+30*Math.cos(t),y=50+30*Math.sin(t);max=Math.max(max,distance(x+y,300-y,closed));}
        require(max<.0201,"Sheared ellipse deviation "+max);
        require(SvgDxfR13Exporter.safetyGeometry("<svg width='100mm' height='100mm' viewBox='0 0 100 100' fill='none'><path d='M1 1 L3 4 L5 6'/></svg>").openPaths==1,"Root fill inheritance");
        System.out.println("PASS transformed arc/ellipse tolerance, exact endpoint and root fill inheritance");
    }
    static void mixedClearance(){
        String rect="<rect x='40' y='40' width='20' height='20'/>";
        String src=V3MilestoneA1GeometryTest.svg(100,100,rect+"<circle cx='37.6' cy='50' r='2'/>");
        FinalCutSheetSafety.Report finalReport=FinalCutSheetSafety.inspect("mixed.svg",src,.25f,.6f);
        require(finalReport.narrow>0&&finalReport.critical==0,"Final mixed weed gap ignored");
        require(FinalCutSheetSafety.inspect("mixed.svg",src,.25f,.3f).narrow==0,"Clear mixed pair warned");
        int[] img=new int[32*32];Arrays.fill(img,0xff555555);
        VinylEngine.Settings opt=VinylEngine.Settings.defaults();opt.registration=false;opt.widthMm=100;
        VinylEngine.Result result=VinylEngine.generate(img,32,32,opt);
        result.shapes.get(0).clear();result.shapes.get(0).add(VinylEngine.Shape.polygon(40,40,60,40,60,60,40,60));result.shapes.get(0).add(VinylEngine.Shape.circle(37.6f,50,2));
        require(StandardCutSafety.inspect(result,.25f,.6f,60).stream().anyMatch(w->w.reason.contains("Circle/polygon")),"Source mixed weed gap ignored");
        System.out.println("PASS source/final mixed width and weed-gap parity");
    }
    static void tonalCounts(){
        int w=64,h=48;int[] photo=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++)photo[y*w+x]=0xff000000|((x*255/(w-1))*0x010101);
        for(float size:new float[]{76.2f,101.6f,279.4f}){
            MultilayerStencilEngine.Options o=new MultilayerStencilEngine.Options();o.widthMm=size;o.heightMm=size*h/w;o.layerCount=4;o.registration=false;o.autoWhiteBackground=false;
            MultilayerStencilEngine.Result r=MultilayerStencilEngine.generate(photo,w,h,o);int[] counts=LayerCutCounts.tonal(r);
            for(int i=0;i<counts.length;i++){
                int expected=0;String data=r.cutPathData(i);for(int k=0;k<data.length();k++)if(data.charAt(k)=='M')expected++;
                require(expected==counts[i],"Tonal physical blade count mismatch");
                if(!r.isEmpty(i)){VinylCutPreflight.Report p=VinylCutPreflight.inspect(r,i,.254f,.60f);require(p.requestedMinimumMm==.254f&&p.requestedGapMm==.60f,"Preflight threshold mismatch");}
            }
        }
        System.out.println("PASS final tonal contour counts at 3, 4 and 11 inches");
    }
    public static void main(String[] args)throws Exception{
        for(double scale:new double[]{.1,1,10})for(double[] c:new double[][]{{0,0,0,100,100,100,100,0},{0,0,100,0,-100,0,1,0},{0,0,0,100,0,-100,0,0},{0,0,10,0,20,0,30,0}}){double[] a=c.clone();for(int i=0;i<a.length;i++)a[i]*=scale;curve(a);}
        try{PrecisionCurveSampler.cubic(0,0,0,20,20,20,20,0,(x,y)->{},()->{throw new CancellationException();});throw new AssertionError("Cancellation swallowed");}catch(CancellationException expected){}
        System.out.println("PASS physical adaptive cubic chord/length bounds (12 curves), cancellation");
        transformedArcs();mixedClearance();tonalCounts();
    }
}
