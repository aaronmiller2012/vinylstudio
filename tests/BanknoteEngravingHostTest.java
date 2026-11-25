package com.halftonestudio;

import java.util.Arrays;

/** RC105 host-only banknote geometry / registration regression. */
public final class BanknoteEngravingHostTest {
    private static void require(boolean ok,String note){if(!ok)throw new AssertionError(note);}
    private static final int W=124,H=136;
    private static int[] source(){
        int[] a=new int[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            float dx=(x-62)/52f,dy=(y-70)/60f;
            float face=dx*dx+dy*dy;
            int value=face>.95?245: 90+(int)(145*Math.min(1,face));
            // Feature shadows to ensure curves follow genuine portrait content.
            if(y>45&&y<55&&((x>37&&x<53)||(x>70&&x<88)))value=27;
            if(x>59&&x<67&&y>57&&y<87)value=60;
            if(y>92&&y<102&&x>39&&x<88)value=32;
            value=Math.max(0,Math.min(255,value));
            a[y*W+x]=0xff000000 | value<<16 | value<<8 | value;
        }
        return a;
    }
    private static boolean[] mask(){
        boolean[] b=new boolean[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)
            b[y*W+x]=(x>6&&x<W-7&&y>7&&y<H-9 && !(x>21&&x<28&&y>45&&y<69));
        return b;
    }
    private static int shapes(StyleKit.Result r){int n=0;for(StyleKit.Layer l:r.layers)n+=l.loops.size();return n;}
    public static void main(String[] args){
        int[] photo=source();boolean[] allowed=mask();
        require(VinylStyleSuite.restoredStyleId(8)==-1,"retired style 8 returned");
        require(VinylStyleSuite.restoredStyleId(9)==9,"new style ID changed");
        require(NewStyleColorSlots.slotCount(VinylStyleSuite.BANKNOTE,2,8)==2,"cross color slots");
        require(NewStyleColorSlots.slotCount(VinylStyleSuite.BANKNOTE,6,8)==1,"retired variant has no decor slot");
        require(NewStyleColorSlots.slotCount(VinylStyleSuite.BANKNOTE,0,8)==1,"mono color slots");
        for(int variant=0;variant<BanknoteEngravingEngine.VARIANTS.length;variant++){
            if(variant==BanknoteEngravingEngine.RETIRED_GUILLOCHE)continue;
            BanknoteEngravingEngine.Options o=new BanknoteEngravingEngine.Options();
            o.widthMm=135f;o.pitchMm=2.2f;o.minMm=.254f;
            o.stepMm=.4f;o.marginMm=.3f;o.variant=variant;o.allowed=allowed;
            long now=System.nanoTime();
            StyleKit.Result r=BanknoteEngravingEngine.generate(photo,W,H,o);
            require(r.layers.size()==(variant==2||variant==7?2:1),"physical sheet count variant="+variant);
            require(shapes(r)>5,"No cuttable banknote geometry variant="+variant);
            require(r.totalPoints()>20,"Empty geometry variant="+variant);
            StyleKit.Source checkingSource=new StyleKit.Source(photo,W,H,allowed,o.widthMm);
            for(StyleKit.Layer layer:r.layers){
                require(layer.lines.isEmpty(),"Open cutter strokes");
                require(layer.loops.size()<=5000,"Exceeded 5000-shape Cricut ceiling");
                for(float[] poly:layer.loops){
                    require(poly.length>=6&&poly.length%2==0,"Invalid ribbon polygon");
                    for(int k=0;k<poly.length;k+=2){
                        require(Float.isFinite(poly[k])&&Float.isFinite(poly[k+1]),"NaN geometry");
                        require(poly[k]>=0&&poly[k]<=r.widthMm&&poly[k+1]>=0&&poly[k+1]<=r.heightMm,"Out of image");
                        require(checkingSource.validAt(poly[k],poly[k+1]),"Ribbon outside mask");
                    }
                }
            }
            String svg=StyleKit.toSvg(r,false);
            require(svg.contains("<path")&&svg.endsWith("</svg>\n"),"Bad SVG variant "+variant);
            System.out.println("Banknote variant "+variant+" -> "+shapes(r)+" closed loops / "+r.totalPoints()+" verts / "+((System.nanoTime()-now)/1000000)+" ms");
        }
        // A solid dark plate must retain weedable, non-overlapping strips.
        final int solidW=110,solidH=90;
        int[] solid=new int[solidW*solidH];Arrays.fill(solid,0xff171717);
        BanknoteEngravingEngine.Options strip=new BanknoteEngravingEngine.Options();
        strip.widthMm=110;strip.variant=BanknoteEngravingEngine.STRAIGHT;
        strip.angleDeg=0;strip.pitchMm=2.5f;strip.minMm=.254f;strip.gapMm=.30f;
        strip.weightMm=1.4f;strip.dashHighlights=false;strip.autoLevels=false;
        StyleKit.Result stripOut=BanknoteEngravingEngine.generate(solid,solidW,solidH,strip);
        java.util.ArrayList<float[]> spans=new java.util.ArrayList<>();
        for(float[] loop:stripOut.layers.get(0).loops){
            float low=Float.POSITIVE_INFINITY,high=Float.NEGATIVE_INFINITY;
            for(int i=1;i<loop.length;i+=2){low=Math.min(low,loop[i]);high=Math.max(high,loop[i]);}
            require(high-low>=strip.minMm-1e-3,"Under-minimum strip width");
            spans.add(new float[]{low,high});
        }
        require(spans.size()>15,"Not enough full-width strips");
        spans.sort((a,b)->Float.compare(a[0],b[0]));
        for(int i=1;i<spans.size();i++)
            require(spans.get(i)[0]-spans.get(i-1)[1]>=strip.gapMm-.015f,
                    "Adjacent strips violate weed gap");
        // Wavy engraving must visibly depart from straight parallel strips.
        BanknoteEngravingEngine.Options wavy=new BanknoteEngravingEngine.Options();
        wavy.widthMm=110f;wavy.variant=BanknoteEngravingEngine.WAVY;
        wavy.angleDeg=0;wavy.pitchMm=2.6f;wavy.minMm=.254f;wavy.gapMm=.30f;
        wavy.weightMm=.9f;wavy.autoLevels=false;
        StyleKit.Result wavyOut=BanknoteEngravingEngine.generate(solid,solidW,solidH,wavy);
        float maxBandSpan=0f;
        for(float[] loop:wavyOut.layers.get(0).loops){
            float low=Float.POSITIVE_INFINITY,high=Float.NEGATIVE_INFINITY;
            for(int i=1;i<loop.length;i+=2){low=Math.min(low,loop[i]);high=Math.max(high,loop[i]);}
            maxBandSpan=Math.max(maxBandSpan,high-low);
        }
        require(maxBandSpan>2.1f,"Wavy variant still looks straight");

        BanknoteEngravingEngine.Options retired=new BanknoteEngravingEngine.Options();
        retired.variant=BanknoteEngravingEngine.RETIRED_GUILLOCHE;
        try{BanknoteEngravingEngine.generate(photo,W,H,retired);
            throw new AssertionError("Retired decorative variant is still available");}
        catch(IllegalArgumentException expected){}
        require(NewStyleVariantPolicy.restore(VinylStyleSuite.BANKNOTE,6)==BanknoteEngravingEngine.CONTOUR,
                "Legacy guilloche variant did not migrate to Contour");
        require(NewStyleVariantPolicy.next(VinylStyleSuite.BANKNOTE,5)==7,
                "Variant picker still visits retired guilloche");
        require(NewStyleVariantPolicy.next(VinylStyleSuite.BANKNOTE,8)==0,
                "Variant picker must wrap to the first active variant");

        BanknoteEngravingEngine.Options invalid=new BanknoteEngravingEngine.Options();
        invalid.crossEtch=true;invalid.crossArgb=invalid.inkArgb;
        try{BanknoteEngravingEngine.generate(photo,W,H,invalid);
            throw new AssertionError("Same-colored crossing layers accepted");}
        catch(IllegalArgumentException expected){ }
        try{
            Thread.currentThread().interrupt();
            BanknoteEngravingEngine.generate(photo,W,H,new BanknoteEngravingEngine.Options());
            throw new AssertionError("Cancellation ignored");
        }catch(java.util.concurrent.CancellationException expected){ }
        finally {Thread.interrupted();}
        VinylStyleSuite.Config c=new VinylStyleSuite.Config();c.style=VinylStyleSuite.BANKNOTE;
        c.widthMm=135;c.detail=70;c.minimumMm=.254f;c.variant=BanknoteEngravingEngine.CROSS_ETCH;
        c.palette=new int[]{0xff174532,0xff202020};c.allowed=allowed;
        StyleKit.Result viaSuite=VinylStyleSuite.generate(photo,W,H,c);
        require(viaSuite.layers.size()==2,"StyleSuite failed to register two-ink option");
        require(viaSuite.layers.get(0).color==c.palette[0]&&viaSuite.layers.get(1).color==c.palette[1],"Colors swapped");
        try { c.style=8;VinylStyleSuite.generate(photo,W,H,c);throw new AssertionError("Retired style generated");}
        catch(IllegalArgumentException good){ }
        System.out.println("PASS RC105 Banknote Engraving JVM geometry, mask, SVG, style IDs and color slots");
    }
}
