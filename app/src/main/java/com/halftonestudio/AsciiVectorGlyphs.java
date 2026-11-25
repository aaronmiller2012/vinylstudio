package com.halftonestudio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cut-size-aware connected-stroke ASCII alphabet. No Android font or SVG text
 * elements: every glyph is unioned into explicit closed vector contours.
 *
 * Skeleton definitions use 0..8 coordinates; | ends a continuous stroke.
 * A single glyph can have several strokes, but overlapping joins are united
 * before export. Cached outlines are normalized; per-cell placement is cheap.
 */
public final class AsciiVectorGlyphs {
    private AsciiVectorGlyphs() {}
    public static final class Glyph {
        public final char letter;
        public final float minScale, length, horizontal, vertical, diagonal;
        private final float[][] paths;
        Glyph(char c,float minScale,String plan){
            this.letter=c;this.minScale=minScale;
            String[] strands=plan.split("\\|");paths=new float[strands.length][];
            float len=0,h=0,v=0,d=0;
            for(int j=0;j<strands.length;j++){
                String[] tokens=strands[j].split(",");
                paths[j]=new float[tokens.length*2];
                for(int i=0;i<tokens.length;i++){
                    if(tokens[i].length()!=2)throw new IllegalArgumentException("Bad vector glyph "+c);
                    int px=tokens[i].charAt(0)-'0',py=tokens[i].charAt(1)-'0';
                    if(px<0||px>8||py<0||py>8)throw new IllegalArgumentException("Glyph range "+c);
                    // Reserve 6% on all sides. Centerline stroke stays in footprint.
                    paths[j][i*2]=.20f+.6f*px/8f;
                    paths[j][i*2+1]=.20f+.6f*py/8f;
                    if(i>0){
                        float dx=paths[j][i*2]-paths[j][(i-1)*2],dy=paths[j][i*2+1]-paths[j][(i-1)*2+1];
                        float ds=(float)Math.hypot(dx,dy);len+=ds;
                        if(ds>0){h+=ds*Math.abs(dx)/ds;v+=ds*Math.abs(dy)/ds;d+=ds*Math.min(Math.abs(dx),Math.abs(dy))/ds;}
                    }
                }
            }
            length=len;float sum=h+v+d+.001f;horizontal=h/sum;vertical=v/sum;diagonal=d/sum;
        }
        public boolean fits(float size,float minimum){return size>=minimum*minScale-1e-4f;}
    }
    private static final LinkedHashMap<Character,Glyph> GLYPHS=new LinkedHashMap<>();
    private static void add(char c,float factor,String paths){GLYPHS.put(c,new Glyph(c,factor,paths));}
    static {
        // Small marks and open connected letters; their dimensional limits are
        // deliberately independent from the old seven-pixel height threshold.
        add('I',2.25f,"40,48");
        add('L',2.55f,"00,08,88");
        add('T',2.55f,"00,80|40,48");
        add('V',2.65f,"00,48,80");
        add('X',2.70f,"00,88|80,08");
        add('Y',3.00f,"00,44,80|44,48");
        add('C',3.00f,"80,20,00,08,28,88");
        add('U',3.00f,"00,08,28,68,88,80");
        add('J',3.00f,"80,88,68,28,08");
        add('1',2.35f,"20,40,48|18,78");
        add('7',2.75f,"00,80,28");
        add('N',3.15f,"08,00,88,80");
        add('H',3.15f,"00,08|80,88|04,84");
        add('K',3.20f,"00,08|80,04,88");
        add('Z',3.30f,"00,80,08,88");
        add('A',3.65f,"08,40,88|26,66");
        add('F',3.55f,"08,00,80|04,64");
        add('E',3.75f,"80,00,08,88|04,64");
        add('M',3.85f,"08,00,44,80,88");
        add('W',3.85f,"00,08,44,88,80");
        add('S',4.15f,"80,20,00,04,84,88,08");
        add('D',4.10f,"00,08,58,88,80,50,00");
        add('P',4.10f,"08,00,60,80,84,64,04");
        add('R',4.15f,"08,00,60,80,84,64,04|44,88");
        add('O',4.15f,"20,00,60,80,88,68,08,00,20");
        add('G',4.25f,"80,20,00,08,68,88,84,54");
        add('B',4.60f,"00,08,68,88,84,64,04|64,80,60,00");
        add('2',3.90f,"00,20,80,84,08,88");
        add('3',4.30f,"00,80,84,44,84,88,08");
        add('4',3.75f,"00,08,80|60,68");
        add('5',4.05f,"80,00,04,64,84,88,08");
        add('6',4.30f,"80,20,00,08,68,88,84,64,04");
        add('8',4.75f,"20,00,60,80,84,44,04,00,20|44,08,68,88,84");
        add('9',4.25f,"80,88,68,08,00,40,80");
        add('0',4.30f,"20,00,60,80,88,68,08,00,20");
        add('#',4.35f,"20,28|60,68|04,84|06,86");
        add('*',4.15f,"04,84|40,48|11,77|17,71");
        add('%',5.40f,"08,80|10,30,32,12,10|56,76,78,58,56");
        add('@',5.80f,"70,20,00,08,68,88,84,54,44,64");
        add('&',5.45f,"80,08,28,64,22,00,40,84,88");
    }
    public static Iterable<Glyph> all(){return GLYPHS.values();}
    public static Glyph find(char c){return GLYPHS.get(c);}

    private static final int RES=28;
    private static final int MAX_TEMPLATES=256;
    private static final LinkedHashMap<Integer,Template> CACHE=new LinkedHashMap<Integer,Template>(128,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<Integer,Template> e){return size()>MAX_TEMPLATES;}
    };
    private static final class Template {
        final ArrayList<float[]> loops; final float area;
        Template(ArrayList<float[]> loops,float area){this.loops=loops;this.area=area;}
    }
    /** physical stroke expansion is rounded UP to ensure the requested minimum width. */
    private static Template template(Glyph g,float strokeRatio){
        int bucket=Math.max(1,Math.min(63,(int)Math.ceil(strokeRatio*64)));
        int key=(g.letter<<6)|bucket;
        synchronized(CACHE){Template found=CACHE.get(key);if(found!=null)return found;}
        float half=bucket/(128f)+.6f/RES;
        boolean[] filled=new boolean[RES*RES];int count=0;
        for(int y=0;y<RES;y++)for(int x=0;x<RES;x++){
            float px=(x+.5f)/RES,py=(y+.5f)/RES;
            boolean hit=false;
            for(float[] path:g.paths){
                for(int i=2;i<path.length;i+=2){
                    float ax=path[i-2],ay=path[i-1],bx=path[i],by=path[i+1];
                    float dx=bx-ax,dy=by-ay;
                    float t=Math.max(0,Math.min(1,((px-ax)*dx+(py-ay)*dy)/(dx*dx+dy*dy+1e-10f)));
                    float ex=px-(ax+t*dx),ey=py-(ay+t*dy);
                    if(ex*ex+ey*ey<=half*half){hit=true;break;}
                }
                if(hit)break;
            }
            filled[y*RES+x]=hit;if(hit)count++;
        }
        // Cells meeting only at a corner would make a self-touching outline (a
        // zero-width vinyl joint). Bridge every such corner with one cell.
        for(int pass=0;pass<8;pass++){
            boolean changed=false;
            for(int y=0;y+1<RES;y++)for(int x=0;x+1<RES;x++){
                int i00=y*RES+x,i01=i00+1,i10=i00+RES,i11=i10+1;
                if(filled[i00]&&filled[i11]&&!filled[i01]&&!filled[i10]){filled[i01]=true;count++;changed=true;}
                else if(filled[i01]&&filled[i10]&&!filled[i00]&&!filled[i11]){filled[i00]=true;count++;changed=true;}
            }
            if(!changed)break;
        }
        // Produce a union of all occupied cells. Unlike individual stroke
        // rectangles this has no overlapping contours or internal duplicate cuts.
        float[] vertices=new float[2*(RES+1)*(RES+1)];
        for(int y=0;y<=RES;y++)for(int x=0;x<=RES;x++){
            int p=(y*(RES+1)+x)*2;vertices[p]=x/(float)RES;vertices[p+1]=y/(float)RES;
        }
        int[] triangles=new int[count*6],selected=new int[count*2];int ti=0,si=0;
        for(int y=0;y<RES;y++)for(int x=0;x<RES;x++)if(filled[y*RES+x]){
            int a=y*(RES+1)+x,b=a+1,c=a+RES+1,d=c+1;
            triangles[ti++]=a;triangles[ti++]=b;triangles[ti++]=d;
            triangles[ti++]=a;triangles[ti++]=d;triangles[ti++]=c;
            selected[si++]=0;selected[si++]=0;
        }
        // selected indexes must cover each triangle, not cell indexes.
        for(int i=0;i<selected.length;i++)selected[i]=i;
        ArrayList<float[]> loops=StyleKit.mergeTriangles(vertices,triangles,selected);
        Template created=new Template(loops,count/(float)(RES*RES));
        synchronized(CACHE){CACHE.put(key,created);}
        return created;
    }
    /** Cut-contour count is included in SVG path preflight via Shape.compound. */
    public static VinylEngine.Shape shape(Glyph glyph,float cx,float cy,float size,float minimum){
        if(glyph==null||!glyph.fits(size,minimum)||!(minimum>0)||!(size>0))return null;
        Template t=template(glyph,minimum/size);
        if(t.loops.isEmpty()||t.loops.size()>12)return null;
        float[][] rings=new float[t.loops.size()][];
        for(int i=0;i<rings.length;i++){
            float[] loop=t.loops.get(i),poly=new float[loop.length];
            for(int j=0;j<poly.length;j+=2){
                poly[j]=cx+(loop[j]-.5f)*size;poly[j+1]=cy+(loop[j+1]-.5f)*size;
            }
            rings[i]=poly;
        }
        return VinylEngine.Shape.compound(rings);
    }
    public static float estimateArea(Glyph g,float size,float min){
        // Approximate Minkowski union area; penalize intersections in the score.
        float ratio=min/size;
        return Math.min(.96f,Math.max(.02f,g.length*ratio*.88f+2.0f*ratio*ratio-g.paths.length*.4f*ratio*ratio));
    }
    public static float actualArea(Glyph g,float size,float min){return template(g,min/size).area;}
}
