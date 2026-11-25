package com.halftonestudio;

/** Check that circle crop uses closed geometry, not an unsupported SVG mask. */
public final class CircleCropGeometryTest {
    private static void ok(boolean v,String msg){if(!v)throw new AssertionError(msg);}
    static int[] pixels(int w,int h){int[] p=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){
        float nx=(x+.5f)/w-.5f,ny=(y+.5f)/h-.5f;
        // Actual circular alpha crop from PhotoCropEditor leaves the four corners transparent.
        p[y*w+x]=(nx*nx+ny*ny<=.25f)?0xff17222e:0x00000000;
    }return p;}
    static void verify(VinylEngine.Result r){float rx=r.settings.widthMm/2,ry=r.heightMm/2;
        for(java.util.List<VinylEngine.Shape> layer:r.shapes)for(VinylEngine.Shape shape:layer){
            if(shape.circle){double k=Math.hypot((shape.x-rx)/rx,(shape.y-ry)/ry)+shape.r/Math.min(rx,ry);ok(k<=1.00001,"Circle exceeds crop");}
            else {float[][] rings=shape.contours==null?new float[][]{shape.xy}:shape.contours;
                for(float[] ring:rings)for(int i=0;i<ring.length;i+=2){float a=(ring[i]-rx)/rx,b=(ring[i+1]-ry)/ry;
                    ok(a*a+b*b<=1.00001f,"Closed vinyl polygon must stay inside round crop");}}
        }
    }
    public static void main(String[] args){
        int[] image=pixels(48,48);
        for(int style:new int[]{VinylEngine.LINE,VinylEngine.ROUND,VinylEngine.STAR,VinylEngine.STRAIGHT}){
            VinylEngine.Settings s=VinylEngine.Settings.defaults();s.circleCrop=true;s.pattern=style;s.masterSpacingMm=3f;
            VinylEngine.Result result=VinylEngine.generate(image,48,48,s);
            verify(result);ok(result.count()>0,"Circle crop must not erase all cuts: "+style);
            for(int li=0;li<4;li++){String svg=VinylEngine.svg(result,li);
                ok(!svg.contains("clipPath")&&!svg.contains("<mask"),"Cricut-safe closed paths only");}
            ok(result.settings.circleCrop,"Circle flag survives render copy");
        }
        VinylEngine.Settings stencil=VinylEngine.Settings.defaults();
        stencil.mode=VinylEngine.STENCIL;stencil.circleCrop=true;
        stencil.stencilPaperBackground=true;stencil.stencilCleanup=0;stencil.stencilSmoothing=0;
        stencil.masterSpacingMm=3f;
        VinylEngine.Result stencilResult=VinylEngine.generate(pixels(48,48),48,48,stencil);
        verify(stencilResult);
        ok(stencilResult.count()>0,"Circular stencil crop must retain interior cut regions");
        System.out.println("PASS: circle crop confines line, dot, star and stencil cut geometry without SVG clips or masks");
    }
}
