package com.halftonestudio;

import java.util.ArrayList;
import java.util.Locale;

/** RC62 ASCII engraving: region-aware physical palette and feature-aware glyphs. */
public final class AsciiPortraitEngine {
    private AsciiPortraitEngine(){}
    static String render(StyleKit.Source src,TextHalftoneEngine.Options o,
         ArrayList<TextHalftoneEngine.Tile> tiles, TextPortraitRegions.Map regions,
         StyleKit.Layer[] layers,float cell,int nx,int ny){
        int[] colors=new int[layers.length];
        for(int i=0;i<colors.length;i++)colors[i]=layers[i].color;
        PhotoSwatchAssociation association=new PhotoSwatchAssociation(
            colors,colors.length,null,src,PhotoSwatchAssociation.REGIONS);
        int[] counts=new int[layers.length];int total=0,rejected=0;
        int[][] lastCp=new int[ny][nx];
        int[] glyphs=o.ramp.codePoints().toArray();
        for(int z=0;z<tiles.size();z++){if(((z)&63)==0)CancellationCheckpoint.check();
            if((z&31)==0)StyleKit.checkCancelled();
            TextHalftoneEngine.Tile t=tiles.get(z);
            TextPortraitRegions.Region region=regions.at(t.cx,t.cy);
            TextColorSeparation.Analysis analysis=TextColorSeparation.analyze(
                t.rgb35,t.patch,colors,t.edge,region==null?t.color:region.rgb,
                region==null?0:Math.min(.42f,.18f+.25f*(1f-region.edge)));
            float best=-.60f;int bestCp=0,bestLayer=0;
            int nearest=region==null?-1:association.choose(region.rgb);
            for(int cp:glyphs){CancellationCheckpoint.check();
                long mask=PixelFont5x7.mask(cp);if(mask==0)continue;
                int pixels=Long.bitCount(mask);
                float mass=pixels/35f;
                // Prevent slabs of nearly opaque rectangular pixels swallowing facial detail.
                if(mass>.88f && !(t.dark>.90f&&t.edge<.08f))continue;
                float repetition=0;
                if(t.col>0&&lastCp[t.row][t.col-1]==cp)repetition+=.055f;
                if(t.row>0&&lastCp[t.row-1][t.col]==cp)repetition+=.065f;
                if(t.col>0&&t.row>0&&lastCp[t.row-1][t.col-1]==cp)repetition+=.02f;
                for(int slot=1;slot<layers.length;slot++){if(((slot)&63)==0)CancellationCheckpoint.check();
                    float gain=TextColorSeparation.improvement(analysis,mask,slot);
                    float structure=TextColorSeparation.structure(analysis,mask);
                    // A region's dominant swatch should carry much of that region,
                    // without preventing high-contrast fine features from other layers.
                    float regionAffinity=slot==nearest?.085f:0f;
                    float score=gain+structure+regionAffinity
                        +TextColorSeparation.usageBonus(slot,counts,total)*.55f
                        +(o.orderedDither?TextColorSeparation.dither(t.row,t.col)*.55f:0f)
                        -repetition-Math.max(0f,mass-.70f)*.37f;
                    if(score>best){best=score;bestCp=cp;bestLayer=slot;}
                }
            }
            if(bestLayer<1)continue;
            TextHalftoneEngine.Placement placement=TextHalftoneEngine.place(src,o,t,bestCp,cell);
            if(placement==null){rejected++;continue;}
            layers[bestLayer].loops.addAll(placement.loops);
            counts[bestLayer]++;lastCp[t.row][t.col]=bestCp;total++;
        }
        StringBuilder b=new StringBuilder();
        for(int i=1;i<counts.length;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            if(i>1)b.append(',');b.append('L').append(i+1).append(':').append(counts[i]);
        }
        return "ASCII region-aware multilayer (Snip base + "+(layers.length-1)+
            " text colors): "+total+" cut-safe glyphs, "+regions.count()+
            " color regions, "+nx+"×"+ny+" grid, cell "+
            String.format(Locale.US,"%.2f",cell)+" mm, ["+b+"]"+
            (rejected>0?", "+rejected+" Snip/cut rejections":"");
    }
}
