package com.halftonestudio;

/** Edge-following line definition: more black structure at features, none on paper-white, still export-safe. */
public final class ShapeDefinitionTest {
    private static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);}
    static double area(VinylEngine.Result r,int layer){
        double a=0;
        for(VinylEngine.Shape s:r.shapes.get(layer)){
            float[] b=s.bounds();a+=(b[2]-b[0])*(double)(b[3]-b[1]);
        }
        return a;
    }
    public static void main(String[] args){
        int w=116,h=150;
        int[] pix=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float nx=(x+.5f-w*.5f)/w,ny=(y+.5f-h*.5f)/h;
            float face=(float)Math.exp(-(nx*nx/.045f+ny*ny/.10f));
            float eye=(float)Math.exp(-((nx+.09f)*(nx+.09f)/.0012f+(ny+.045f)*(ny+.045f)/.00055f));
            float mouth=(float)Math.exp(-(nx*nx/.009f+(ny-.12f)*(ny-.12f)/.0007f));
            float dark=Math.max(.03f,.35f*(1-face)+.9f*eye+.55f*mouth);
            int v=PortraitCMYKRegressionTest.channel(235-190*dark);
            pix[y*w+x]=0xff000000|(v<<16)|(v<<8)|v;
        }
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.widthMm=115f;s.marginMm=2f;s.masterSpacingMm=3.2f;s.minimumMm=.65f;s.bridgeMm=.30f;s.pattern=VinylEngine.LINE;s.lineSampleMm=.38f;
        for(VinylEngine.Layer l:s.layers)l.spacing=s.masterSpacingMm;
        s.shapeDefinition=0f;
        VinylEngine.Result off=VinylEngine.generate(pix,w,h,s);
        s.shapeDefinition=.8f;
        VinylEngine.Result on=VinylEngine.generate(pix,w,h,s);
        int k=PortraitCMYKRegressionTest.layer(on,3);
        double a0=area(off,k),a1=area(on,k);
        System.out.println("Black ribbon extent: shapeDefinition 0 -> "+Math.round(a0)+", 0.8 -> "+Math.round(a1));
        check(a1>a0,"shape definition must add black structure at feature edges");
        check(VinylEngine.validateExport(on)==null,"edge-following output must stay export-safe");
        int[] white=new int[64*64];java.util.Arrays.fill(white,0xffffffff);
        VinylEngine.Result paper=VinylEngine.generate(white,64,64,s);
        for(int i=0;i<paper.shapes.size();i++)check(paper.shapes.get(i).isEmpty(),"paper-white must stay empty");
        System.out.println("PASS: edge-following lines add black structure, stay export-safe, never cut paper-white");
    }
}
