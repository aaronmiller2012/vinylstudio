package com.halftonestudio;

/**
 * ASCII is a glyph *motif* in the ordinary CMYK/spot halftone screen.
 * The screen pipeline retains ownership of color coverage, screen rotation,
 * cell positions, Snips, channel ordering and physical export. This class only
 * selects and returns safe solid cut geometry for one physical footprint.
 */
public final class AsciiScreenMotif {
    private static final char[] SIMPLE={'.','-','|','/','\\','L','T','+'};
    private final int[] counts=new int[128];
    private char lastChoice;
    private float lastArea;
    private int lastRow=Integer.MIN_VALUE;
    private char previousInRow;

    static float clamp(float v,float low,float high){return Math.max(low,Math.min(high,v));}
    private static int hash(int row,int col,int glyph){
        int v=row*0x632be5ab^col*0x9e3779b9^glyph*0x85ebca6b;
        v^=v>>>16;v*=0x7feb352d;v^=v>>>15;return v;
    }
    public char lastSymbol(){return lastChoice;}
    /** Fraction of the physical motif footprint filled by the last chosen glyph. */
    public float lastCoverage(){return lastArea;}
    /** Only accepted, in-bounds glyphs contribute to frequency balancing. */
    public void accepted(int row){
        if(lastChoice!=0&&lastChoice<counts.length){
            counts[lastChoice]++;
            if(row!=lastRow){lastRow=row;previousInRow=0;}
            previousInRow=lastChoice;
        }
    }
    /** Choose a symbol that will be cut as an ordinary filled SVG path. */
    public VinylEngine.Shape choose(float x,float y,float footprint,float coverage,float minimumMm,
            float gx,float gy,int row,int col){
        lastChoice=0;lastArea=0;
        if(!Float.isFinite(footprint)||!Float.isFinite(minimumMm)||!Float.isFinite(coverage)||
                footprint<minimumMm||minimumMm<=0||coverage<=.012f)return null;
        float desired=clamp(coverage,0,1),strength=(float)Math.hypot(gx,gy);
        char winner=0;AsciiVectorGlyphs.Glyph vector=null;
        float winnerSize=0,best=Float.POSITIVE_INFINITY;
        // Fallback shapes remain important below ~2.5 minimum stroke widths.
        for(char c:SIMPLE){
            if(c=='.'&&desired>.27f)continue;
            float size=simpleSize(c,footprint,minimumMm,desired);
            if(size<=0)continue;
            float actual=simpleArea(c,size,minimumMm)/(footprint*footprint);
            float score=Math.abs(actual-desired)*.90f+.05f;
            if(strength>.015f){
                float edge=c=='-'?Math.abs(gy):c=='|'?Math.abs(gx):
                    c=='/'?Math.abs(gx+gy)*.7071f:c=='\\'?Math.abs(gx-gy)*.7071f:0;
                score-=.10f*edge/(strength+.0001f);
            }
            score+=Math.min(.19f,counts[c]*.0022f);
            if(c==previousInRow)score+=.06f;
            score+=(hash(row,col,c)&255)/255f*.032f;
            if(score<best){best=score;winner=c;winnerSize=size;vector=null;}
        }
        // Unlike RC63, this catalog has individually sized continuous vector
        // letterforms. A connected C, H or X can be valid where a fixed 5x7 font
        // cannot. Never shrink below that symbol's own safe-size threshold.
        for(AsciiVectorGlyphs.Glyph glyph:AsciiVectorGlyphs.all()){
            if(!glyph.fits(footprint,minimumMm))continue;
            float candidateSize=clamp(footprint*(.82f+.18f*(float)Math.sqrt(desired)),
                    glyph.minScale*minimumMm,footprint);
            float actual=AsciiVectorGlyphs.estimateArea(glyph,candidateSize,minimumMm)
                    *(candidateSize*candidateSize)/(footprint*footprint);
            float score=Math.abs(actual-desired)*.72f;
            // Reward diversity without choosing pictorially incorrect colors:
            // color belongs to the surrounding halftone screen, not this class.
            score+=Math.min(.20f,counts[glyph.letter]*.0024f);
            if(glyph.letter==previousInRow)score+=.085f;
            // A boundary's tangent is perpendicular to its intensity gradient.
            if(strength>.02f){
                float edge=(Math.abs(gy)*glyph.horizontal+Math.abs(gx)*glyph.vertical
                        +.55f*(Math.abs(gx)+Math.abs(gy))*glyph.diagonal)/(strength+.0001f);
                score-=Math.min(.11f,.10f*edge);
            }
            // Complex lettering on almost blank highlights introduces visual noise.
            if(desired<.12f)score+=.08f;
            score+=((hash(row,col,glyph.letter)&255)/255f)*.047f;
            if(score<best){best=score;winner=glyph.letter;winnerSize=candidateSize;vector=glyph;}
        }
        if(winner==0)return null;
        VinylEngine.Shape shape=vector==null?simpleShape(winner,x,y,winnerSize,minimumMm):
                AsciiVectorGlyphs.shape(vector,x,y,winnerSize,minimumMm);
        if(shape==null)return null;
        lastChoice=winner;
        lastArea=vector==null?clamp(simpleArea(winner,winnerSize,minimumMm)/(footprint*footprint),0,1)
                :clamp(AsciiVectorGlyphs.actualArea(vector,winnerSize,minimumMm)
                        *winnerSize*winnerSize/(footprint*footprint),0,1);
        return shape;
    }
    private static float simpleSize(char c,float footprint,float min,float desired){
        // A diagonal bar needs real length: at size==min it collapses to a
        // zero-length sliver with no area, which cannot be cut.
        float low=c=='+'||c=='T'||c=='L'||c=='/'||c=='\\'?2*min:min;
        if(footprint<low)return 0;
        return clamp((float)Math.sqrt(desired)*footprint,low,footprint);
    }
    private static float simpleArea(char c,float size,float min){
        switch(c){
            case '.':return size*size;
            case '-':case '|':return size*min;
            case '/':case '\\':return size*min*.94f;
            case '+':case 'L':case 'T':return 2*size*min-min*min;
            default:return 0;
        }
    }
    private static VinylEngine.Shape simpleShape(char c,float cx,float cy,float size,float min){
        float r=size*.5f,t=min*.5f;
        switch(c){
            case '.':return VinylEngine.Shape.polygon(cx-r,cy-r,cx+r,cy-r,cx+r,cy+r,cx-r,cy+r);
            case '-':return VinylEngine.Shape.polygon(cx-r,cy-t,cx+r,cy-t,cx+r,cy+t,cx-r,cy+t);
            case '|':return VinylEngine.Shape.polygon(cx-t,cy-r,cx+t,cy-r,cx+t,cy+r,cx-t,cy+r);
            case '/':case '\\':{
                // Offset the bar by +/-t along its true normal. The previous
                // fixed (+1,+1) offset was parallel to '\\', collapsing that
                // glyph into a zero-width sliver the cutter cannot use.
                float q=(r-t)*.70710678f,dx=c=='/'?q:-q;
                float ax=cx-dx,ay=cy+q,bx=cx+dx,by=cy-q;
                float len=(float)Math.hypot(bx-ax,by-ay);
                float nx=len>0?-(by-ay)/len*t:t,ny=len>0?(bx-ax)/len*t:0f;
                return VinylEngine.Shape.polygon(ax+nx,ay+ny,bx+nx,by+ny,bx-nx,by-ny,ax-nx,ay-ny);
            }
            case '+':return VinylEngine.Shape.polygon(cx-t,cy-r,cx+t,cy-r,cx+t,cy-t,cx+r,cy-t,
                        cx+r,cy+t,cx+t,cy+t,cx+t,cy+r,cx-t,cy+r,cx-t,cy+t,cx-r,cy+t,cx-r,cy-t,cx-t,cy-t);
            case 'L':return VinylEngine.Shape.polygon(cx-r,cy-r,cx-r+min,cy-r,cx-r+min,cy+r-min,
                        cx+r,cy+r-min,cx+r,cy+r,cx-r,cy+r);
            case 'T':return VinylEngine.Shape.polygon(cx-r,cy-r,cx+r,cy-r,cx+r,cy-r+min,
                        cx+t,cy-r+min,cx+t,cy+r,cx-t,cy+r,cx-t,cy-r+min,cx-r,cy-r+min);
            default:return null;
        }
    }
}
