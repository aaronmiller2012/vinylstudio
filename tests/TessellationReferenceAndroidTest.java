package com.halftonestudio;

import java.util.List;

/** Seven new layouts: JVM cut geometry and Android picker/restore integration. */
public final class TessellationReferenceAndroidTest {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static double signed(float[] p){double sum=0;for(int i=0,n=p.length/2;i<n;i++){
        int j=(i+1)%n;sum+=(double)p[2*i]*p[2*j+1]-(double)p[2*j]*p[2*i+1];}
        return sum*.5;}
    private static boolean finite(float[] p){for(float v:p)if(!Float.isFinite(v))return false;return true;}
    private static int count(StyleKit.Result r){int n=0;for(StyleKit.Layer l:r.layers)n+=l.loops.size();return n;}
    public static void main(String[] args) {
        require(TessellationMotifEngine.PATTERN_SLOT_COUNT==37,"Variant IDs were renumbered");
        require(TessellationMotifEngine.ACTIVE_PATTERN_COUNT==28,"Active layout count wrong");
        require(StudioThumbnailRegistry.allKeys().size()==StudioThumbnailRegistry.TOP_LEVEL_COUNT+StudioThumbnailRegistry.TESSELLATION_COUNT,"Missing thumbnail identities");
        for(int i:new int[]{18,19,20,21,24,27,32,33,36}){
            require(!TessellationMotifEngine.selectablePattern(i),"Retired ID resurrected");
            require(NewStyleVariantPolicy.restore(10,i)==TessellationMotifEngine.TRIHEX,"Legacy migration broken");
            require(StudioThumbnailRegistry.tessellationVariant("tess.variant."+i)==-1,"Retired thumbnail accepted");
        }
        for(int i=0;i<17;i++){
            require(TessellationMotifEngine.selectablePattern(i),"Preexisting variant retired");
            require(NewStyleVariantPolicy.restore(10,i)==i,"Original variant lost");
        }
        int variant=17;
        for(int expected:new int[]{22,23,25,26,28}){
            variant=NewStyleVariantPolicy.next(10,variant);
            require(variant==expected,"Picker next entered retired ID or skipped new variant");
        }
        require(NewStyleVariantPolicy.next(10,28)==29,"Picker must reach Beta7 variants");
        int w=108,h=92;int[] rgba=new int[w*h];boolean[] allowed=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int red=(x*255)/(w-1),green=(y*255)/(h-1);
            rgba[y*w+x]=0xff000000|(red<<16)|(green<<8)|((x+y)%32+90);
            allowed[y*w+x]=x>5&&x<w-6&&y>5&&y<h-6;
        }
        for(int variantId=22;variantId<=28;variantId++){
            if(!TessellationMotifEngine.selectablePattern(variantId))continue;
            String name=TessellationMotifEngine.PATTERNS[variantId];
            require(TessellationMotifEngine.selectablePattern(variantId),"Missing active style: "+name);
            require(StudioThumbnailRegistry.isKnown(StudioThumbnailRegistry.tessellationKey(variantId)),"No thumbnail: "+name);
            List<float[]> base=TessellationMotifEngine.tiles(variantId,120,96,6.4f);
            require(base.size()>10,"Too little raw geometry "+name);
            for(float[] poly:base){require(poly.length>=6&&poly.length%2==0&&finite(poly),"Invalid raw "+name);require(signed(poly)>0,"Non CCW geometry "+name);}
            TessellationMotifEngine.Options opt=new TessellationMotifEngine.Options();
            opt.pattern=variantId;opt.widthMm=152.4f;opt.detail=55;opt.allowed=allowed;
            StyleKit.Result result=TessellationMotifEngine.generate(rgba,w,h,opt);
            require(result.layers.size()==8,"Physical sheets "+name);
            require(count(result)>20,"New style rendered empty: "+name);
            for(StyleKit.Layer l:result.layers){
                require(l.loops.size()<=4800&&l.lines.isEmpty(),"Invalid cutting layer "+name);
                for(float[] poly:l.loops)require(poly.length>=6&&finite(poly)&&Math.abs(signed(poly))>.01,"Invalid output contour "+name);
            }
            String svg=StyleKit.toSvg(result,false);
            require(svg.contains("<svg")&&svg.equals(StyleKit.toSvg(TessellationMotifEngine.generate(rgba,w,h,opt),false)),"Nondeterministic export: "+name);
            System.out.println(name+": "+count(result)+" closed contours, "+result.totalPoints()+" points");
        }
        System.out.println("PASS seven reference tessellation generators, renderer, stable IDs, SVG parity");
    }
}
