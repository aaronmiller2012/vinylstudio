package com.halftonestudio;
import java.util.*;
/** Host-only tests for photo edge adherence, masking, bounded regions and vinyl geometry. */
public final class StainedGlassPortraitRC79Test {
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    static int[] image(int w,int h){
        int[] im=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int c=0xffe8d9c4;
            float dx=(x-w*.52f)/(w*.35f),dy=(y-h*.52f)/(h*.43f);
            if(dx*dx+dy*dy<1f)c=0xffb98160;
            if(dx*dx+dy*dy<1f && y<h*.29f)c=0xff29202d;
            if(Math.abs(y-h*.43f)<h*.045f && (Math.abs(x-w*.37f)<w*.085f||Math.abs(x-w*.64f)<w*.085f))c=0xff1f232b;
            if(y>h*.60f&&y<h*.64f&&x>w*.41f&&x<w*.64f)c=0xff54333a;
            if(y>h*.78f&&x>w*.20f&&x<w*.81f)c=0xff315670;
            im[y*w+x]=c;
        }return im;
    }
    static int panes(StyleKit.Result r){int n=0;for(StyleKit.Layer l:r.layers)if(!l.name.equals("lead"))n+=l.loops.size();return n;}
    static String digest(StyleKit.Result r){
        long a=1469598103934665603L;
        for(StyleKit.Layer l:r.layers){a=(a^l.color)*1099511628211L;
            for(float[] loop:l.loops){a=(a^loop.length)*1099511628211L;
                for(float x:loop)a=(a^Float.floatToIntBits(x))*1099511628211L;
            }
        }return Long.toHexString(a);
    }
    public static void main(String[] args){
        int w=220,h=255;int[] pixels=image(w,h);
        boolean[] mask=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float dx=(x-w*.52f)/(w*.40f),dy=(y-h*.51f)/(h*.47f);
            boolean hole=(x>w*.48f&&x<w*.53f&&y>h*.75f&&y<h*.79f);
            mask[y*w+x]=dx*dx+dy*dy<1f&&!hole;
        }
        StainedGlassEngine.Options low=new StainedGlassEngine.Options();
        low.widthMm=152.4f;low.allowed=mask;low.minSpacingMm=4.5f;low.maxSpacingMm=20f;
        low.maxPieces=180;low.leadMode=StainedGlassEngine.LeadMode.UNDERLAY;
        low.palette=new int[]{0xff1f232b,0xff315670,0xffb98160,0xffe8d9c4};
        StainedGlassEngine.Options high=low.copy();high.minSpacingMm=2.95f;high.maxPieces=1160;
        StyleKit.Result a=StainedGlassEngine.generate(pixels,w,h,low);
        StyleKit.Result b=StainedGlassEngine.generate(pixels,w,h,high);
        check(panes(a)>35,"low detail lost panes");
        check(panes(b)>panes(a)*2,"detail control must add useful physical regions");
        check(b.layers.size()>=4&&b.layers.size()<=8,"palette collapsed or exceeded limit");
        check("lead".equals(b.layers.get(0).name),"missing first lead underlay");
        check(b.layers.get(0).loops.size()==1,"standalone underlay changed");
        int colored=0;for(int k=1;k<b.layers.size();k++){
            StyleKit.Layer l=b.layers.get(k);
            check(l.loops.size()>0,"empty palette slot");
            check(Arrays.stream(low.palette).anyMatch(c->c==l.color),"swatch changed during quantization");
            for(float[] poly:l.loops){check(poly.length>=6&&poly.length%2==0,"invalid polygon");
                check(Math.abs(StyleKit.signedArea(poly))>0.01,"degenerate polygon");
                for(int p=0;p<poly.length;p+=2){
                    check(Float.isFinite(poly[p])&&Float.isFinite(poly[p+1]),"NaN/infinite geometry");
                    check(poly[p]>-.001&&poly[p]<b.widthMm+.001,"x out of artboard");
                    check(poly[p+1]>-.001&&poly[p+1]<b.heightMm+.001,"y out of artboard");
                }
            }
            colored++;
        }
        check(colored>=3,"face lost tone/color palette diversity");
        check(digest(b).equals(digest(StainedGlassEngine.generate(pixels,w,h,high))),"not deterministic");
        VinylStyleSuite.Config config=new VinylStyleSuite.Config();
        config.style=VinylStyleSuite.GLASS;config.detail=100;config.colorCount=5;
        config.palette=low.palette.clone();config.allowed=mask;
        StyleKit.Result r=VinylStyleSuite.generate(pixels,w,h,config);
        check(r.layers.get(0).loops.size()>=2,"Snip exterior or interior opening not retained");
        check(r.layers.size()<=8,"style layer limit broken");
        for(int k=0;k<r.layers.size();k++){
            String svg=NewStyleExport.layerSvg(r,k,true,RegistrationMarks.CROSS,2.5f,2);
            check(svg.contains("<path")&&svg.contains("fill-rule=\"evenodd\""),"invalid SVG or evenodd fill");
        }
        // Check actual working-grid exclusion and the physical lead clearance.
        StyleKit.Source src=new StyleKit.Source(pixels,w,h,mask,152.4f);
        StainedGlassEngine.Pixels grid=new StainedGlassEngine.Pixels(src);
        int[] owner=StainedGlassEngine.segment(grid,StainedGlassEngine.refineSites(grid,high));
        boolean[] kept=StainedGlassEngine.insetMask(grid,owner,high.leadMm);
        for(int k=0;k<kept.length;k++)if(kept[k])check(grid.valid[k]&&owner[k]>=0,"pane leaked into Snip hole");
        System.out.println("PASS RC79 photo-aware panes: low="+panes(a)+" high="+panes(b)+" colors="+colored+" silhouette hole, SVG, gaps, deterministic");
    }
}
