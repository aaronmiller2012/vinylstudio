package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Flat-vinyl interpretation of a European 4-in-1 ring sheet.
 *
 * Each interior ring has two possible links in the course above and two below.
 * The rings genuinely overlap in the 2D projection, and course orientations
 * alternate to show a woven grain rather than a tiled grid. A separate filled
 * L1 silhouette anchors the sheet. Ring contours are unioned by physical vinyl
 * color, never exported as duplicate intersecting cuts on the same sheet.
 */
final class InterlockingChainmailEngine {
    private InterlockingChainmailEngine() {}
    static final class Ring {
        final float x,y,rx,ry,rotation,luminance;
        final int photoRgb,row,col,id;
        final double perceptualLight;
        int slot;
        Ring(float x,float y,float rx,float ry,float rotation,float lum,int rgb,int row,int col,int id){
            this.x=x;this.y=y;this.rx=rx;this.ry=ry;
            this.rotation=rotation;this.luminance=lum;
            this.photoRgb=rgb;this.perceptualLight=PerceptualLayerColorEngine.light(rgb);this.row=row;this.col=col;this.id=id;
        }
    }
    static final class Plan {
        final ArrayList<Ring> rings;
        final int[] occupancy;
        final float pitchX,pitchY,outerRx,outerRy;
        Plan(ArrayList<Ring> rings,int[] occupancy,float px,float py,float rx,float ry){
            this.rings=rings;this.occupancy=occupancy;
            pitchX=px;pitchY=py;outerRx=rx;outerRy=ry;
        }
    }
    static Plan plan(StyleKit.Source src,PatternPortraitTrioEngine.Options o,int[] colors){
        float diameter=Math.max(o.minCutMm*7.0f, 9.7f-4.5f*o.detail/100f);
        float pitchX=diameter*.75f + o.spacingMm*.22f;
        float pitchY=diameter*.43f + o.spacingMm*.09f;
        float rx=diameter*.54f,ry=diameter*.40f;
        ArrayList<Ring> rings=new ArrayList<>();
        int rows=(int)Math.ceil(src.heightMm/pitchY)+2;
        int cols=(int)Math.ceil(src.widthMm/pitchX)+3;
        for(int row=-1;row<=rows;row++){if(((row)&63)==0)CancellationCheckpoint.check();
            if((row&15)==0)StyleKit.checkCancelled();
            float y=row*pitchY;
            // Horizontal half-step creates four candidate links per ring:
            // two above and two below, and no links to same-row neighbors.
            float shift=(row&1)==0?0f:pitchX*.5f;
            for(int col=-1;col<=cols;col++){if(((col)&63)==0)CancellationCheckpoint.check();
                float x=col*pitchX+shift;
                if(!src.validAt(x,y))continue;
                float tilt=((row&1)==0?-.26f:.26f)*(0.84f+o.organic*.0031f);
                rings.add(new Ring(x,y,rx,ry,tilt,src.lumAt(x,y),src.colorAt(x,y),row,col,rings.size()));
            }
        }
        if(rings.isEmpty())return new Plan(rings,new int[colors.length],pitchX,pitchY,rx,ry);
        // Occupancy is assigned from the tones of ACTUAL ring sites, not the
        // source pixel histogram. Sorting exact ties deterministically means a
        // flat photo still fills every enabled overlay swatch when enough rings
        // exist. Physical swatch indices/colors are NEVER sorted or rewritten.
        Integer[] byTone=new Integer[colors.length-1];
        for(int i=0;i<byTone.length;i++)byTone[i]=i+1;
        Arrays.sort(byTone,(a,b)->{
            int comp=Double.compare(PerceptualLayerColorEngine.light(colors[a]),PerceptualLayerColorEngine.light(colors[b]));
            return comp!=0?comp:Integer.compare(a,b);
        });
        ArrayList<Ring> sorted=new ArrayList<>(rings);
        sorted.sort(Comparator.comparingDouble((Ring r)->r.perceptualLight).thenComparingInt(r->r.id));
        int[] occupied=new int[colors.length];
        occupied[0]=1; // Full silhouette base occupies L1.
        for(int i=0;i<sorted.size();i++){if(((i)&63)==0)CancellationCheckpoint.check();
            int rank=(int)((long)i*byTone.length/sorted.size());
            int slot=byTone[Math.min(byTone.length-1,rank)];
            sorted.get(i).slot=slot;
            occupied[slot]++;
        }
        // Retain the cuttable 4-in-1 weave and select the best physical roll.
        // Guard every overlay from becoming empty,
        // including flat photos with indistinguishable site luminance.
        if(byTone.length>1 && rings.size()>byTone.length*3){
            int[] overlays=Arrays.copyOfRange(colors,1,colors.length);
            int[] sites=new int[rings.size()];
            for(int i=0;i<rings.size();i++)sites[i]=rings.get(i).photoRgb;
            PhotoSwatchAssociation association=new PhotoSwatchAssociation(
                overlays,overlays.length,null,sites,null,PhotoSwatchAssociation.TEXTILES);
            
            int max=(int)Math.ceil(rings.size()/(double)byTone.length*1.25);
            for(Ring r:rings){
                CancellationCheckpoint.check();
                int old=r.slot;
                int candidate=association.choose(r.photoRgb)+1;
                if(candidate==old||occupied[old]<=4||occupied[candidate]>=max)continue;
                double oldDistance=PerceptualLayerColorEngine.error(r.photoRgb,colors[old]);
                double newDistance=PerceptualLayerColorEngine.error(r.photoRgb,colors[candidate]);
                if(newDistance<oldDistance*.74){r.slot=candidate;occupied[old]--;occupied[candidate]++;}
            }
        }
        return new Plan(rings,occupied,pitchX,pitchY,rx,ry);
    }


    static int render(PatternPortraitTrioEngine.Surface surface,
                      PatternPortraitTrioEngine.Options opts,int[] colors){
        surface.fillValid(0);
        Plan plan=plan(surface.src,opts,colors);
        if(plan.rings.size()<opts.layers-1)
            throw new IllegalStateException("Not enough cuttable rings to populate all physical colors");
        // Odd/even courses have opposite elliptical grain. Their outlines
        // cross neighboring courses; individual rings are not isolated tiles.
        for(int i=0;i<plan.rings.size();i++){if(((i)&63)==0)CancellationCheckpoint.check();
            if((i&127)==0)StyleKit.checkCancelled();
            Ring ring=plan.rings.get(i);
            float wall=Math.max(Math.max(opts.minCutMm*1.5f,3f*surface.mm),
                plan.outerRy*(.27f+.13f*(1f-ring.luminance)));
            wall=Math.min(wall,ring.ry*.66f);
            // Full annulus is a closed cut path; each ring remains weedable.
            // The row-to-row overlap plus opposite tilt produces the weave.
            surface.ellipseRing(ring.slot,ring.x,ring.y,ring.rx,ring.ry,ring.rotation,wall);
        }
        return plan.rings.size();
    }
}
