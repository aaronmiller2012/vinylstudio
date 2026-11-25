package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

/** RC67 Word Knockout: color-separated OPAQUE PLATES with words removed from
 * each plate, rather than positive lettering. No SVG text, clips, raster embeds,
 * or alpha blending. All geometry remains flat, closed and physically sized.
 * L1's existing solid Snip underlay is generated upstream unchanged.
 */
public final class WordKnockoutEngine {
    private WordKnockoutEngine() {}
    private static final int PATH_BUDGET = 4600;
    private static final int MAX_WORDS = 550;
    private static final int MAX_SIDE = 390;

    private static final class Raster {
        final int w,h;final float pitch;final byte[] labels;
        Raster(StyleKit.Source src,float min,float[][] palette) {
            pitch=Math.max(min*.58f,Math.max(src.widthMm,src.heightMm)/MAX_SIDE);
            w=Math.max(2,(int)Math.ceil(src.widthMm/pitch));
            h=Math.max(2,(int)Math.ceil(src.heightMm/pitch));
            labels=new byte[w*h];
            float[] background=palette[0];
            for(int y=0;y<h;y++){if(((y)&63)==0)CancellationCheckpoint.check();
                if((y&31)==0)StyleKit.checkCancelled();
                for(int x=0;x<w;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                    float xx=Math.min(src.widthMm-1e-4f,(x+.5f)*pitch);
                    float yy=Math.min(src.heightMm-1e-4f,(y+.5f)*pitch);
                    // Conservative Snip erosion: marching-squares contours lie
                    // between grid centers, so every neighboring position must
                    // be allowed before a plate pixel may be exported.
                    boolean inside=true;
                    for(int dy=-1;dy<=1&&inside;dy++)for(int dx=-1;dx<=1;dx++)
                        if(!src.validAt(xx+dx*pitch*.75f,yy+dy*pitch*.75f)){
                            inside=false;break;
                        }
                    if(!inside)continue;
                    float[] col=StyleKit.toLab(src.colorAt(xx,yy));
                    int choice=0;float best=StyleKit.labDist2(col,background);
                    for(int k=1;k<palette.length;k++){if(((k)&63)==0)CancellationCheckpoint.check();
                        float improvement=best-StyleKit.labDist2(col,palette[k]);
                        if(improvement>0){best=StyleKit.labDist2(col,palette[k]);choice=k;}
                    }
                    // Quantized color plates, never an arbitrary brightness-band assignment.
                    // Conservative improvement threshold limits false color islands.
                    if(choice>0&&StyleKit.labDist2(col,background)-best>65f)
                        labels[y*w+x]=(byte)choice;
                }
            }
            // One edge-preserving majority pass removes sub-mm noise, without
            // fabricating pixels in Snip holes or changing a chosen palette.
            byte[] clean=labels.clone();
            for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                int i=y*w+x,v=labels[i]&255;
                if(v==0)continue;
                int same=0;
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)
                    if((labels[i+dy*w+dx]&255)==v)same++;
                if(same<4)clean[i]=0;
            }
            System.arraycopy(clean,0,labels,0,labels.length);
        }
        int ix(float x){return Math.max(0,Math.min(w-1,(int)(x/pitch)));}
        int iy(float y){return Math.max(0,Math.min(h-1,(int)(y/pitch)));}
        boolean contains(int layer,float left,float top,float width,float height,float margin){
            if(left-margin<0||top-margin<0||(left+width+margin)>=w*pitch||
               (top+height+margin)>=h*pitch)return false;
            int x0=ix(left-margin),x1=ix(left+width+margin);
            int y0=iy(top-margin),y1=iy(top+height+margin);
            // Verify the ENTIRE bounding footprint, not only glyph centers.
            for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++)
                if((labels[y*w+x]&255)!=layer)return false;
            return true;
        }
        boolean[] mask(int layer){
            boolean[] m=new boolean[labels.length];
            for(int i=0;i<m.length;i++)m[i]=(labels[i]&255)==layer;
            return m;
        }
    }
    private static float pitch(float height,float min){return height*.83f+min*.18f;}
    private static float width(String token,float height,float min){
        return token.length()*pitch(height,min)-min*.18f;
    }
    private static boolean point(float[] p,float x,float y){
        boolean inside=false;int n=p.length/2;
        for(int i=0,j=n-1;i<n;j=i++){if(((i)&63)==0)CancellationCheckpoint.check();
            float x1=p[2*i],y1=p[2*i+1],x2=p[2*j],y2=p[2*j+1];
            if((y1>y)!=(y2>y)&&x<(x2-x1)*(y-y1)/(y2-y1)+x1)inside=!inside;
        }
        return inside;
    }
    private static ArrayList<float[]> glyphs(String word,float x,float y,float size,float min){
        ArrayList<float[]> loops=new ArrayList<>();float adv=pitch(size,min);
        for(int i=0;i<word.length();i++){if(((i)&63)==0)CancellationCheckpoint.check();
            WordVectorGlyphs.Glyph g=WordVectorGlyphs.knockout(word.charAt(i));
            if(g==null||!g.fits(size,min))return null;
            ArrayList<float[]> rings=WordVectorGlyphs.contours(g,x+i*adv+size*.5f,
                y+size*.5f,size,min);
            if(rings==null||rings.isEmpty())return null;
            loops.addAll(rings);
        }
        return loops;
    }
    private static boolean carve(boolean[] plate,Raster r,ArrayList<float[]> glyphs,
                                  float x,float y,float width,float height,float minimum){
        int xa=r.ix(x),xb=r.ix(x+width),ya=r.iy(y),yb=r.iy(y+height);
        ArrayList<Integer> positions=new ArrayList<>();
        for(int row=ya;row<=yb;row++)for(int col=xa;col<=xb;col++){if(((col)&63)==0)CancellationCheckpoint.check();
            float px=(col+.5f)*r.pitch,py=(row+.5f)*r.pitch;
            boolean remove=false;
            // Even-odd winding gives the same boolean semantics as SVG export.
            for(float[] p:glyphs)if(point(p,px,py))remove=!remove;
            if(remove){int i=row*r.w+col;if(!plate[i])return false;positions.add(i);}
        }
        if(positions.isEmpty())return false;
        // A word must not touch or merge with an earlier negative word.
        // Check full minimum material bridge around every future cutout pixel.
        int gap=Math.max(1,(int)Math.ceil(minimum/r.pitch));
        for(int i:positions){CancellationCheckpoint.check();
            int xx=i%r.w,yy=i/r.w;
            for(int dy=-gap;dy<=gap;dy++)for(int dx=-gap;dx<=gap;dx++){if(((dx)&63)==0)CancellationCheckpoint.check();
                int nx=xx+dx,ny=yy+dy;
                if(nx<0||ny<0||nx>=r.w||ny>=r.h)return false;
                int ni=ny*r.w+nx;
                // The original plate owns all supported pixels. Reject only
                // previously cut holes; region/boundary clearance is checked
                // by Raster.contains before evaluating any word.
                if((r.labels[ni]&255)==(r.labels[i]&255)&&!plate[ni])return false;
            }
        }
        for(int i:positions)plate[i]=false;
        return true;
    }
    private static ArrayList<float[]> trace(boolean[] mask,Raster r){
        int pw=r.w+2,ph=r.h+2;
        float[] field=new float[pw*ph];Arrays.fill(field,-1f);
        for(int y=0;y<r.h;y++){if(((y)&63)==0)CancellationCheckpoint.check();
            if((y&31)==0)StyleKit.checkCancelled();
            for(int x=0;x<r.w;x++)if(mask[y*r.w+x])field[(y+1)*pw+x+1]=1f;
        }
        return TopographicLayersEngine.contours(field,pw,ph,0f,r.pitch);
    }
    /** Conservative tiny-island cleanup. Counter-bearing letters use open
     * outlines so unsupported inner plate islands are not deliberately added. */
    private static void removeSmallComponents(boolean[] mask,Raster r,float minArea){
        int[] queue=new int[mask.length];boolean[] seen=new boolean[mask.length];
        int minimumCells=Math.max(3,(int)Math.ceil(minArea/(r.pitch*r.pitch)));
        for(int i=0;i<mask.length;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            if(!mask[i]||seen[i])continue;
            int head=0,end=0;queue[end++]=i;seen[i]=true;
            while(head<end){
                int at=queue[head++],x=at%r.w,y=at/r.w;
                int[] next={x>0?at-1:-1,x+1<r.w?at+1:-1,
                    y>0?at-r.w:-1,y+1<r.h?at+r.w:-1};
                for(int n:next)if(n>=0&&mask[n]&&!seen[n]){seen[n]=true;queue[end++]=n;}
            }
            if(end<minimumCells)for(int j=0;j<end;j++)mask[queue[j]]=false;
        }
    }
    /** Color plates are quantized once, then words are subtracted ONLY from the
     * same plate. Registered SVG layers have no hidden clip paths or text nodes. */
    static String render(StyleKit.Source src,TextHalftoneEngine.Options options,
        TextPortraitRegions.Map regions,StyleKit.Layer[] layers,float cell){
        String[] tokens=WordPortraitEngine.tokenize(options.text);
        if(tokens.length==0)tokens=new String[]{"VINYL","STUDIO"};
        float min=Math.max(.3f,options.minStrokeMm);
        int[] colors=new int[layers.length];for(int i=0;i<colors.length;i++)colors[i]=layers[i].color;
        Raster raster=new Raster(src,min,StyleKit.labOf(colors));
        int placed=0,paths=0,nonempty=0;
        StringBuilder counts=new StringBuilder();
        for(int layer=1;layer<layers.length;layer++){
            StyleKit.checkCancelled();
            boolean[] plate=raster.mask(layer);
            boolean any=false;for(boolean b:plate)if(b){any=true;break;}
            if(!any)continue;
            int words=0;
            // Large-to-small deterministic packing. Local color regions determine
            // allowed positions; short words fill narrow regions without splitting.
            float base=Math.max(min*4.5f,Math.min(cell*1.45f,min*8f));
            for(int pass=0;pass<3;pass++){if(((pass)&63)==0)CancellationCheckpoint.check();
                float height=base*(pass==0?1.35f:pass==1?1f:.78f);
                float step=Math.max(min*1.2f,height*.52f);
                for(float y=min;y+height<src.heightMm-min;y+=step){CancellationCheckpoint.check();
                    if((((int)(y/step))&15)==0)StyleKit.checkCancelled();
                    float x=min+(((int)(y/step))%3)*step*.27f;
                    for(;x+height<src.widthMm-min;x+=step){CancellationCheckpoint.check();
                        if(words>=MAX_WORDS/layers.length)break;
                        int pick=(int)(Math.abs((long)(x/step)*13+(long)(y/step)*7+layer*11+pass*3)%tokens.length);
                        String token=tokens[pick];
                        float w=width(token,height,min);
                        if(!raster.contains(layer,x,y,w,height,Math.max(min*.58f,raster.pitch*1.2f)))continue;
                        ArrayList<float[]> outlines=glyphs(token,x,y,height,min);
                        if(outlines==null)continue;
                        if(carve(plate,raster,outlines,x,y,w,height,min)){
                            words++;placed++;x+=Math.max(step,w*.66f);
                        }
                    }
                }
            }
            removeSmallComponents(plate,raster,Math.max(min*min*7f,raster.pitch*raster.pitch*5f));
            ArrayList<float[]> contours=trace(plate,raster);
            if(contours.size()>PATH_BUDGET)
                throw new IllegalStateException("Word Knockout layer L"+(layer+1)+
                    " needs "+contours.size()+" contours; enlarge the artwork or reduce detail");
            if(!contours.isEmpty()){
                layers[layer].loops.addAll(contours);paths+=contours.size();nonempty++;
            }
            if(layer>1)counts.append(", ");
            counts.append('L').append(layer+1).append('=').append(words);
        }
        String out="Words knockout (solid Snip L1): "+placed+" whole-word cutouts, "+
            nonempty+" color plates, "+paths+" plate contours; "+counts;
        if(placed==0)out+="; no safe word cutouts fit these colors or dimensions";
        return out;
    }
}
