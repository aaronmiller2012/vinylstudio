package com.halftonestudio;

import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Path;

/** Independent final-vector composite checks, including actual cross-sheet overlap. */
public final class PrismaticWeaveV3Test {
    static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    static boolean in(float[] p,float x,float y){
        boolean hit=false;
        for(int i=0,j=p.length-2;i<p.length;j=i,i+=2){
            if((p[i+1]>y)!=(p[j+1]>y)&&x<(p[j]-p[i])*(y-p[i+1])/(p[j+1]-p[i+1])+p[i])hit=!hit;
        }
        return hit;
    }
    static boolean contains(StyleKit.Layer l,float x,float y){
        boolean filled=false;
        for(float[] p:l.loops)if(in(p,x,y))filled=!filled;
        return filled;
    }
    static int[] ramp(int w,int h){
        int[] p=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float u=x/(float)(w-1),v=y/(float)(h-1);
            int t=Math.round(22+220*u);
            int r=Math.min(255,t+Math.round(19*(1-v))),g=Math.min(255,t),b=Math.min(255,t+Math.round(25*v));
            p[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
        }
        return p;
    }
    static void verify(String label,int[] palette,float mm,boolean draft){
        int w=104,h=96;int[] photo=ramp(w,h);boolean[] valid=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float a=(x+.5f-w*.5f)/(w*.48f),b=(y+.5f-h*.5f)/(h*.47f);
            valid[y*w+x]=a*a+b*b<1f&&!(a*a+b*b<.035f);
        }
        PrismaticDiagonalPortraitEngine.Options o=new PrismaticDiagonalPortraitEngine.Options();
        o.widthMm=mm;o.allowed=valid;o.layers=palette.length;o.palette=palette;o.toneBalance=50;
        o.detail=100;o.gapMm=.25f;o.draft=draft;
        StyleKit.Result r=PrismaticDiagonalPortraitEngine.generate(photo,w,h,o);
        check(r.layers.size()==palette.length,label+": stable physical slots");
        int[] visible=new int[palette.length];float[] labs=new float[palette.length];
        for(int i=0;i<palette.length;i++){
            StyleKit.Layer l=r.layers.get(i);check(l.color==palette[i],label+": exact swatch RGB/order");
            labs[i]=StyleKit.toLab(l.color)[0];check(l.lines.isEmpty(),label+": only closed cut geometry");
            for(float[] loop:l.loops)check(loop.length>=6&&StyleKit.selfIntersections(loop,1)==0,label+": simple contours");
        }
        int overlaps=0,inside=0,lowN=0,highN=0;float low=0,high=0;
        for(int y=1;y<61;y++)for(int x=1;x<61;x++){
            float u=x/61f,v=y/61f,px=u*r.widthMm,py=v*r.heightMm;
            boolean base=contains(r.layers.get(0),px,py);
            int top=0,upper=0;
            for(int k=1;k<palette.length;k++)if(contains(r.layers.get(k),px,py)){top=k;upper++;}
            if(!base){ // Robustly outside the Snip, including its explicit central hole.
                float a=(u-.5f)/.48f,b=(v-.5f)/.47f,q=a*a+b*b;
                if(q>1.10f||q<.018f)check(upper==0,label+": no overlay well outside Snip");
                continue;
            }
            inside++;visible[top]++;if(upper>1)overlaps++;
            if(u<.31f){low+=labs[top];lowN++;}if(u>.69f){high+=labs[top];highN++;}
        }
        float dark=low/lowN,light=high/highN;
        int used=0;for(int n:visible)if(n>0)used++;
        System.out.printf("%s %.1fmm %s: L*=%.1f -> %.1f, visible swatches=%d/%d, actual upper overlaps=%d/%d%n",label,mm,draft?"draft":"final",dark,light,used,palette.length,overlaps,inside);
        check(used>=2,label+": photo uses more than one opaque sheet");
        check(light-dark>15f,label+": preserved dark-to-light contrast");
        if(palette.length>=4&&!draft)check(overlaps>4,label+": real cross-sheet overlaps survive vectorization");
        check(Arrays.equals(o.palette,palette),label+": no palette mutation");
        if(!draft&&label.equals("cream/mauve/gold")){
            for(int k=0;k<r.layers.size();k++){
                if(r.layers.get(k).loops.isEmpty())continue;
                try {
                    String svg=NewStyleExport.layerSvg(r,k,false,0,3f,2f,null,.254f);
                    CutSvgCompatibility.verify(svg,OutputDeviceProfile.GENERIC_SVG);
                    java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
                    try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(out)){
                        CutSheetZipWriter writer=new CutSheetZipWriter(zip,OutputDeviceProfile.SILHOUETTE_BASIC,.254f,true);
                        writer.put("layer.svg",svg);writer.finish();
                    }
                    check(out.size()>100,"DXF sheet ZIP generated");
                } catch(Exception e){throw new AssertionError("Prismatic sheet export failed",e);}
            }
        }
        if(!draft&&mm>200){
            PrismaticDiagonalPortraitEngine.Options copy=o.copy();copy.paperArgb=0xff13a1ba;
            StyleKit.Result changed=PrismaticDiagonalPortraitEngine.generate(photo,w,h,copy);
            check(StyleKit.toSvg(r,false).equals(StyleKit.toSvg(changed,false)),label+": mounting color isolated");
        }
    }
    public static void main(String[] args)throws Exception{
        int[] portrait={0xfff4e7d8,0xffefd296,0xffb45983,0xffa881ae,0xff282a37};
        int[] reverse=portrait.clone();for(int i=0;i<reverse.length/2;i++){int t=reverse[i];reverse[i]=reverse[reverse.length-1-i];reverse[reverse.length-1-i]=t;}
        verify("cream/mauve/gold",portrait,279.4f,false);
        verify("reversed physical palette",reverse,76.2f,false);
        verify("grayscale",new int[]{0xfffafafa,0xffcccccc,0xff999999,0xff666666,0xff333333,0xff111111},152.4f,false);
        verify("opaque CMYK",new int[]{0xffffe000,0xff00b8d4,0xffd5008f,0xff161616},152.4f,false);
        verify("two colors",new int[]{0xfffafafa,0xff171717},76.2f,false);
        verify("eight colors final",new int[]{0xffead6c2,0xffffe49b,0xffd48c74,0xffa96d87,0xffa0b5c0,0xff795687,0xff625159,0xff282632},279.4f,false);
        verify("eight colors draft",new int[]{0xffead6c2,0xffffe49b,0xffd48c74,0xffa96d87,0xffa0b5c0,0xff795687,0xff625159,0xff282632},279.4f,true);
        verify("narrow brown",new int[]{0xff966b57,0xff794a37,0xff53301e,0xff2b1d15},279.4f,false);
        check(PrismaticDiagonalPortraitEngine.roundedEnd(.32f,.26f,.30f,.65f),"full width persists near rounded end");
        check(!PrismaticDiagonalPortraitEngine.roundedEnd(.65f,.15f,.30f,.65f),"rounded cap narrows only at terminal arc");
        if(args.length==1){
            int w=180,h=180;int[] p=new int[w*h];
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                double nx=(x-w*.5)/(w*.34),ny=(y-h*.49)/(h*.43);
                if(nx*nx+ny*ny>1){p[y*w+x]=0;continue;}
                int tone=(int)(150+65*nx-33*ny+18*Math.cos(nx*3));
                if(Math.abs(ny+.27)<.075&&Math.abs(nx)>.15&&Math.abs(nx)<.72||Math.abs(ny-.50)<.065&&Math.abs(nx)<.43)tone=35;
                if(Math.abs(nx)<.16&&ny>-.21&&ny<.25)tone+=36;
                tone=Math.max(0,Math.min(225,tone));p[y*w+x]=0xff000000|((Math.min(255,tone+30))<<16)|(tone<<8)|Math.max(0,tone-16);
            }
            PrismaticDiagonalPortraitEngine.Options o=new PrismaticDiagonalPortraitEngine.Options();
            o.widthMm=152.4f;o.layers=portrait.length;o.palette=portrait;o.detail=76;o.toneBalance=50;o.irregularity=78;
            StyleKit.Result r=PrismaticDiagonalPortraitEngine.generate(p,w,h,o);
            Files.writeString(Path.of(args[0]),StyleKit.toSvg(r,true));
        }
        System.out.println("PASS Prismatic weave final-vector palette, Snip, overlap and tone checks");
    }
}
