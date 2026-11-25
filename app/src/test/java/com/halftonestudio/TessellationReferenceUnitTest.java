package com.halftonestudio;

import org.junit.Test;
import static org.junit.Assert.*;

/** Executes on Gradle testDebugUnitTest on the Android build host. */
public final class TessellationReferenceUnitTest {
    @Test public void stableIdsAndArchivedVariants() {
        assertEquals(28,TessellationMotifEngine.ACTIVE_PATTERN_COUNT);
        assertEquals(37,TessellationMotifEngine.PATTERN_SLOT_COUNT);
        for(int id:new int[]{18,19,20,21,24,27,32,33,36}){
            assertFalse(TessellationMotifEngine.selectablePattern(id));
            assertEquals(TessellationMotifEngine.TRIHEX,
                NewStyleVariantPolicy.restore(VinylStyleSuite.TESSELLATION_MOTIF,id));
        }
        for(int id=22;id<=36;id++){
            if(!TessellationMotifEngine.selectablePattern(id))continue;
            assertTrue(TessellationMotifEngine.selectablePattern(id));
            assertEquals(id,NewStyleVariantPolicy.restore(VinylStyleSuite.TESSELLATION_MOTIF,id));
            assertTrue(StudioThumbnailRegistry.isKnown(StudioThumbnailRegistry.tessellationKey(id)));
        }
    }
    @Test public void sevenStylesGenerateClosedCutPaths() {
        final int w=74,h=66;int[] image=new int[w*h];
        boolean[] alpha=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int gray=(x*172/w+y*83/h);
            image[y*w+x]=0xff000000|(gray<<16)|(gray<<8)|gray;
            alpha[y*w+x]=x>3&&x<w-4&&y>3&&y<h-4;
        }
        for(int id=22;id<=28;id++){
            if(!TessellationMotifEngine.selectablePattern(id))continue;
            TessellationMotifEngine.Options options=new TessellationMotifEngine.Options();
            options.widthMm=203.2f;options.detail=55;options.pattern=id;options.allowed=alpha;
            StyleKit.Result result=TessellationMotifEngine.generate(image,w,h,options);
            assertEquals("variant "+id,8,result.layers.size());
            int overlay=0;
            for(int layer=1;layer<result.layers.size();layer++){
                StyleKit.Layer cut=result.layers.get(layer);
                assertTrue("Open toolpath variant "+id,cut.lines.isEmpty());
                assertTrue("Path budget variant "+id,cut.loops.size()<=4800);
                for(float[] poly:cut.loops){
                    assertTrue("Open polygon variant "+id,poly.length>=6&&poly.length%2==0);
                    for(float coordinate:poly)assertTrue("NaN variant "+id,Float.isFinite(coordinate));
                }
                overlay+=cut.loops.size();
            }
            assertTrue("No overlay cut paths variant "+id,overlay>5);
            assertEquals("Non-deterministic variant "+id,StyleKit.toSvg(result,false),
                StyleKit.toSvg(TessellationMotifEngine.generate(image,w,h,options),false));
        }
    }
    @Test public void eightAdditionalStylesProduceClosedPaths() {
        final int w=88,h=78;int[] argb=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int v=40+((x*126/w+y*80/h)%215);
            argb[y*w+x]=0xff000000|(v<<16)|(v<<8)|v;
        }
        for(int id=29;id<=36;id++){
            if(!TessellationMotifEngine.selectablePattern(id))continue;
            TessellationMotifEngine.Options options=new TessellationMotifEngine.Options();
            options.pattern=id;options.detail=70;
            StyleKit.Result result=TessellationMotifEngine.generate(argb,w,h,options);
            int count=0;
            for(int i=1;i<result.layers.size();i++){
                assertTrue(result.layers.get(i).lines.isEmpty());
                for(float[] loop:result.layers.get(i).loops){
                    assertTrue(loop.length>=6 && loop.length%2==0);
                    for(float xy:loop)assertTrue(Float.isFinite(xy));
                    count++;
                }
            }
            assertTrue("No cuttable contours: "+id,count>20);
        }
    }
    @Test public void increasedDetailForReferencePortraits() {
        final int w=112,h=112;
        int[] pixels=new int[w*h];boolean[] allowed=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int c=(x*150/w+y*100/h);
            pixels[y*w+x]=0xff000000|(c<<16)|(c<<8)|c;
            float dx=(x-w/2f)/(w*.31f),dy=(y-h/2f)/(h*.41f);
            allowed[y*w+x]=dx*dx+dy*dy<1f;
        }
        for(int id=22;id<=28;id++){
            if(!TessellationMotifEngine.selectablePattern(id))continue;
            TessellationMotifEngine.Options o=new TessellationMotifEngine.Options();
            o.pattern=id;o.allowed=allowed;o.detail=50;
            StyleKit.Result medium=TessellationMotifEngine.generate(pixels,w,h,o);
            o.detail=100;
            StyleKit.Result high=TessellationMotifEngine.generate(pixels,w,h,o);
            int mediumCount=0,highCount=0;
            for(int i=1;i<medium.layers.size();i++)mediumCount+=medium.layers.get(i).loops.size();
            for(int i=1;i<high.layers.size();i++)highCount+=high.layers.get(i).loops.size();
            assertTrue("Detail 100 failed to add actual cutting tiles for "+id,
                highCount>mediumCount*1.5);
        }
    }
    @Test public void perceptualSwatchesPreserveOwnershipAndBase() {
        final int w=36,h=24;
        int[] ramp=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int v=18+x*217/(w-1);
            ramp[y*w+x]=0xff000000|(v<<16)|(v<<8)|v;
        }
        StyleKit.Source photo=new StyleKit.Source(ramp,w,h,null,152.4f);
        for(int id=0;id<TessellationMotifEngine.PATTERN_SLOT_COUNT;id++){
            if(!TessellationMotifEngine.selectablePattern(id))continue;
            TessellationMotifEngine.Options o=new TessellationMotifEngine.Options();o.pattern=id;
            int[] originals=o.palette.clone();
            TessellationToneMapper mapper=new TessellationToneMapper(photo,o,true);
            assertEquals("Base must be a visible swatch: "+id,TessellationToneMapper.BASE,
                mapper.select(o.palette[0]));
            for(int k=1;k<8;k++)
                assertEquals("Actual swatch ID: "+id+"/"+k,k,mapper.select(o.palette[k]));
            assertArrayEquals("Tonal calibration edited user swatches",originals,o.palette);
        }
    }

}
