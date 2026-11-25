package com.halftonestudio;
import java.util.Arrays;
import java.util.List;

/** B2 photometric host gate: synthetic contours are NOT human portrait recognition tests. */
public final class V3MilestoneB2ToneTest {
    private static final ScaleAwarePhotoSampler.Coverage TONE=p->1f-((p>>>16)&255)/255f;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void near(float a,float b,float eps,String message){check(Math.abs(a-b)<=eps,message+" "+a+" vs "+b);}
    private static int[] bars(int n){
        int[] img=new int[n*n];Arrays.fill(img,0xffffffff);
        for(int y=16;y<n-16;y++)for(int x=n/2-7;x<n/2+7;x++)img[y*n+x]=0xff000000;
        return img;
    }
    private static void photographicEdgesByArtworkSize(){
        int[] src=bars(256);
        for(float inches:new float[]{6f,11f,15f}){
            float mm=inches*25.4f, pxMm=mm/256f;
            ScaleAwarePhotoSampler s=new ScaleAwarePhotoSampler(src,256,256,mm,mm,0xffffffff);
            float centerX=(128-6+.5f)*pxMm,centerY=128.5f*pxMm;
            float radius=5.5f*pxMm;
            float old=s.motifCoverage(centerX,centerY,radius,TONE);
            float newer=s.detailPreservingMotifCoverage(centerX,centerY,radius,TONE);
            check(old>.60f&&old<1f,"Fixture must sample both sides of edge "+inches+" in");
            check(newer>=old+.025f,"B2 must preserve photographed dark edge at "+inches+" in: "+newer+" / "+old);
            check(newer<=1f,"B2 overshot [0,1]");
            System.out.printf(java.util.Locale.ROOT,"B2 %d-inch synthetic edge coverage: baseline=%.4f, revised=%.4f%n",(int)inches,old,newer);
            near(s.detailPreservingMotifCoverage(18*pxMm,128*pxMm,radius,TONE),
                 s.motifCoverage(18*pxMm,128*pxMm,radius,TONE),0f,"Uniform field changed");
            check(s.detailPreservingMotifCoverage(180*pxMm,128*pxMm,radius,TONE)==0f,
                  "White center created phantom motif");
            check(s.detailPreservingMotifCoverage(-1f,0f,radius,TONE)==0f,
                  "Out-of-board sample produced coverage");
        }
    }
    private static void masksAndIsolation(){
        int n=64;int[] src=new int[n*n];Arrays.fill(src,0xff000000);
        for(int y=25;y<=39;y++)for(int x=30;x<=34;x++)src[y*n+x]=0x00000000;
        ScaleAwarePhotoSampler s=new ScaleAwarePhotoSampler(src,n,n,152.4f,152.4f,0xffffffff);
        float mm=152.4f/n;
        check(s.detailPreservingMotifCoverage(32.5f*mm,32.5f*mm,6f,TONE)==0f,
              "Transparent center must never borrow black from surroundings");
        near(s.detailPreservingMotifCoverage(29.5f*mm,32.5f*mm,6f,TONE),1f,0f,
             "Transparent neighbors must not erase real subject");
        // A valid spot color may have tiny but nonzero layer ownership; no
        // cross-layer interpolation or opacity change is allowed by this helper.
        ScaleAwarePhotoSampler.Coverage zero=p->0f;
        check(s.detailPreservingMotifCoverage(29.5f*mm,32.5f*mm,6f,zero)==0f,
              "Zero-coverage physical layer acquired another layer's material");
    }
    private static void generatedPaths(){
        int w=96,h=96;int[] p=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int g=(x/12+y/8)%2==0?35:222;
            p[y*w+x]=0xff000000|(g<<16)|(g<<8)|g;
        }
        for(float inches:new float[]{6f,11f,15f}){
            VinylEngine.Settings setting=VinylEngine.Settings.defaults();
            setting.mode=VinylEngine.SPOT;setting.widthMm=25.4f*inches;
            setting.pattern=VinylEngine.ROUND;setting.marginMm=3f;
            setting.minimumMm=.254f;setting.bridgeMm=.4f;
            setting.masterSpacingMm=Math.max(2.4f,setting.widthMm/44f);
            setting.registration=false;
            for(VinylEngine.Layer l:setting.layers)l.spacing=setting.masterSpacingMm;
            VinylEngine.Result a=VinylEngine.generate(p,w,h,setting);
            VinylEngine.Result b=VinylEngine.generate(p,w,h,setting);
            int total=0;
            for(int k=0;k<a.shapes.size();k++){
                List<VinylEngine.Shape> shapes=a.shapes.get(k);
                total+=shapes.size();
                String svg=VinylEngine.svg(a,k);
                check(svg.equals(VinylEngine.svg(b,k)),"Nondeterministic SVG at "+inches+" inches layer "+k);
                check(svg.contains("viewBox="),"Physical SVG viewBox missing");
                for(VinylEngine.Shape shape:shapes)
                    check(VinylEngine.insideCutBoundary(shape,a.settings.widthMm,a.heightMm,setting.marginMm,false),
                        "Unexpected out-of-bounds motif at "+inches+" inches");
            }
            check(total>50,"Generated zero/too few cut shapes at "+inches+" inches");
            check(VinylEngine.validateExport(a)==null,"Export limits or validity failed at "+inches+" inches");
        }
    }
    public static void main(String[] args){
        photographicEdgesByArtworkSize();masksAndIsolation();generatedPaths();
        System.out.println("PASS B2 edge-preserving coverage (6/11/15 in), alpha, blank regions, layer zero ownership, deterministic SVG, physical bounds");
    }
}
