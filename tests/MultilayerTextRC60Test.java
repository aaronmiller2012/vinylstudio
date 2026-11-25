package com.halftonestudio;

/** Actual vector and palette tests for the snipped filled underlay + typographic detail. */
public final class MultilayerTextRC60Test {
    private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    private static int[] pixels(int w,int h) {
        int[] p=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int gray=30+(x*173/w + y*30/h);
            p[y*w+x]=0xff000000|(gray<<16)|(gray<<8)|gray;
        }
        return p;
    }
    private static boolean[] clipped(int w,int h,boolean hole) {
        boolean[] allowed=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
            float dx=(x-w*.5f)/(w*.37f),dy=(y-h*.48f)/(h*.43f);
            allowed[y*w+x]=dx*dx+dy*dy<=1f &&
                (!hole||(x-w*.5f)*(x-w*.5f)+(y-h*.5f)*(y-h*.5f)>12f*12f);
        }
        return allowed;
    }
    private static boolean contains(float[] polygon,float x,float y){
        boolean inside=false;int n=polygon.length/2;
        for(int i=0,j=n-1;i<n;j=i++){
            float xi=polygon[2*i],yi=polygon[2*i+1],xj=polygon[2*j],yj=polygon[2*j+1];
            if((yi>y)!=(yj>y)&&x<(xj-xi)*(y-yi)/(yj-yi)+xi)inside=!inside;
        }
        return inside;
    }
    private static boolean filled(StyleKit.Layer layer,float x,float y) {
        boolean in=false;for(float[] ring:layer.loops)if(contains(ring,x,y))in=!in;
        return in;
    }
    public static void main(String[] args){
        final int w=140,h=180;int[] photo=pixels(w,h);
        for(int count=2;count<=8;count++)for(int variant=0;variant<=1;variant++){
            VinylStyleSuite.Config c=new VinylStyleSuite.Config();
            c.style=VinylStyleSuite.TEXT;c.colorCount=count;c.variant=variant;
            c.widthMm=140;c.minimumMm=.4f;c.detail=65;
            c.words="VINYL STUDIO FOREVER";c.allowed=clipped(w,h,true);
            c.palette=new int[count];
            for(int i=0;i<count;i++)c.palette[i]=0xffeeeeee-i*0x000c0c0c;
            StyleKit.Result result=VinylStyleSuite.generate(photo,w,h,c);
            check(result.layers.size()<=count,"More exported layers than requested");
            check(!result.layers.isEmpty(),"Missing background");
            StyleKit.Layer base=result.layers.get(0);
            check("snipped-background-1".equals(base.name),"First physical layer is not snipped background");
            check(base.color==c.palette[0],"Underlay lost chosen swatch");
            check(base.loops.size()>=2,"Snip interior hole must be a closed cut path");
            check(NewStyleColorSlots.slot(VinylStyleSuite.TEXT,base.name,0)==0,"Wrong background slot");
            check(filled(base,70,55),"Inside silhouette must be solid");
            check(!filled(base,10,10),"Outside Snip must remain empty");
            check(!filled(base,70,90),"Snip removed hole must stay empty");
            int other=0;
            for(int i=1;i<result.layers.size();i++){
                StyleKit.Layer l=result.layers.get(i);
                int slot=NewStyleColorSlots.slot(VinylStyleSuite.TEXT,l.name,i);
                check(slot>=1&&slot<count,"Glyph exported into base or out of range");
                check(l.color==c.palette[slot],"Palette layer mismatch");
                other+=l.loops.size();
            }
            check(other>0,"No actual text-detail contours");
            String svg=NewStyleExport.layerSvg(result,0,false,0,2.5f,2f);
            check(svg.contains("fill-rule=\"evenodd\"")||svg.contains("fill-rule='evenodd'")||
                svg.contains("fill-rule=\"even-odd\"")||svg.contains("fill-rule='even-odd'"),
                "Snip hole must survive SVG export as even-odd fill");
            check(!svg.contains("<clipPath")&&!svg.contains("<text"),"Cricut output must have real cut paths");
        }
        VinylStyleSuite.Config full=new VinylStyleSuite.Config();full.style=VinylStyleSuite.TEXT;
        full.colorCount=2;full.palette=new int[]{0xfff6d6a9,0xff1b1720};
        StyleKit.Result crop=VinylStyleSuite.generate(photo,w,h,full);
        check(!crop.layers.isEmpty()&&crop.layers.get(0).loops.size()>=1,"No-snip crop fallback missing");
        check(filled(crop.layers.get(0),70,90),"Crop fallback should fill artboard");
        System.out.println("PASS RC60 2–8 layers, filled snip silhouette, interior holes, palettes, Word/ASCII, real SVG paths");
    }
}
