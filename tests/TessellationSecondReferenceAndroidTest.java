package com.halftonestudio;

import java.util.List;

/** Beta 7: saved ID stability, independent new geometry, and real cut output. */
public final class TessellationSecondReferenceAndroidTest {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static double area(float[] v){double sum=0;for(int i=0,n=v.length/2;i<n;i++){
        int j=(i+1)%n;sum+=(double)v[2*i]*v[2*j+1]-(double)v[2*j]*v[2*i+1];}return sum*.5;}
    static int overlays(StyleKit.Result res){int n=0;for(int k=1;k<res.layers.size();k++){
        StyleKit.Layer layer=res.layers.get(k);
        require(layer.loops.size()<4801,"Cut-sheet path count exceeded");
        require(layer.lines.isEmpty(),"Unexpected open path in vinyl cut layer");
        for(float[] shape:layer.loops){
            require(shape.length>=6&&shape.length%2==0,"Malformed loop");
            for(float v:shape)require(Float.isFinite(v),"Nonfinite vertex");
            require(Math.abs(area(shape))>.01,"Degenerate contour");
            n++;
        }
    }return n;}
    static int[] pixels(int w,int h){int[] rgba=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){
        int rr=(x*255)/w,gg=(y*255)/h,bb=(x+y)*127/(w+h);
        rgba[y*w+x]=0xff000000|(rr<<16)|(gg<<8)|bb;
    }return rgba;}
    static boolean[] mask(int w,int h){boolean[] allowed=new boolean[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){
        double a=(x-w*.5)/(w*.37),b=(y-h*.5)/(h*.43);
        allowed[y*w+x]=a*a+b*b<1;
    }return allowed;}
    public static void main(String[] args){
        require(TessellationMotifEngine.PATTERN_SLOT_COUNT==37,"Saved IDs changed");
        require(TessellationMotifEngine.ACTIVE_PATTERN_COUNT==28,"Active count incorrect");
        require(TessellationMotifEngine.PATTERNS.length==37,"Variant names missing");
        require(StudioThumbnailRegistry.allKeys().size()==StudioThumbnailRegistry.TOP_LEVEL_COUNT+StudioThumbnailRegistry.TESSELLATION_COUNT,"Picker thumbnail registry incomplete");
        for(int id:new int[]{18,19,20,21,24,27,32,33,36})require(!TessellationMotifEngine.selectablePattern(id),"Retired ID reused");
        for(int id=0;id<=17;id++)require(NewStyleVariantPolicy.restore(10,id)==id,"Legacy variant changed");
        for(int id=22;id<=36;id++)if(TessellationMotifEngine.selectablePattern(id))
            require(NewStyleVariantPolicy.restore(10,id)==id,"Reference variant changed");
        require(NewStyleVariantPolicy.next(10,28)==29,"Beta 5 -> Beta 7 selection broken");
        require(NewStyleVariantPolicy.next(10,35)==0,"Picker wrap broken");
        int w=112,h=144;int[] rgba=pixels(w,h);boolean[] oval=mask(w,h);
        for(int id=29;id<=36;id++){
            if(!TessellationMotifEngine.selectablePattern(id))continue;
            require(StudioThumbnailRegistry.isKnown(StudioThumbnailRegistry.tessellationKey(id)),"Missing picker identity " +id);
            String name=TessellationMotifEngine.PATTERNS[id];
            List<float[]> raw=TessellationMotifEngine.tiles(id,120,110,5.5f);
            require(raw.size()>20,"Raw geometry missing for "+name);
            for(float[] loop:raw){
                require(loop.length>=6&&loop.length%2==0,"Raw malformed: "+name);
                for(float v:loop)require(Float.isFinite(v),"Raw nonfinite: "+name);
                require(area(loop)>.005,"Wrong winding / area: "+name);
            }
            TessellationMotifEngine.Options opt=new TessellationMotifEngine.Options();
            opt.pattern=id;opt.widthMm=152.4f;opt.allowed=oval;opt.detail=100;
            StyleKit.Result high=TessellationMotifEngine.generate(rgba,w,h,opt);
            int dense=overlays(high);
            require(dense>12,"Empty/highly coarse generator: "+name+" ("+dense+")");
            require(dense<=2300,"Over-budget output: "+name);
            require(high.layers.size()==8,"Wrong sheet count: "+name);
            opt.detail=35;
            StyleKit.Result low=TessellationMotifEngine.generate(rgba,w,h,opt);
            int sparse=overlays(low);
            require(dense>sparse,"Detail slider no longer increases density: "+name);
            opt.detail=100;
            String xml=StyleKit.toSvg(high,false);
            require(xml.contains("<svg")&&xml.equals(StyleKit.toSvg(
                TessellationMotifEngine.generate(rgba,w,h,opt),false)),"Unstable SVG: "+name);
            System.out.println(name+": low="+sparse+" high="+dense+" loops, "+high.totalPoints()+" points");
        }
        // Negative-space and physical-scale extremes. Only two physical colors.
        for(int id:new int[]{29,30,31,34,35}){
            for(float mm:new float[]{20f,279.4f,2000f}){
                TessellationMotifEngine.Options opt=new TessellationMotifEngine.Options();
                opt.pattern=id;opt.widthMm=mm;opt.layerCount=2;opt.palette=new int[]{0xff19191a,0xffefcdb8};
                opt.allowed=oval;opt.detail=55;
                StyleKit.Result two=TessellationMotifEngine.generate(rgba,w,h,opt);
                require(two.layers.size()==2,"Two-color mode broke: "+id);
                require(overlays(two)>2,"Extreme dimensions empty: "+id+" @"+mm);
                require(Math.abs(two.widthMm-mm)<.01,"Artwork size changed: "+id);
            }
        }
        System.out.println("PASS Beta 7 eight new tessellations, 2/8 layers, extreme dimensions, SVG determinism and legacy IDs");
    }
}
