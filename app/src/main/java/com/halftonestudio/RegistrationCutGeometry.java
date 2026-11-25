package com.halftonestudio;

import java.util.ArrayList;
import java.util.List;

/** Physical registration safety, evaluated against the CUTTING geometry, not the photo rectangle.
 * Pure Java so the identical decisions can be tested on the NAS without Android SDK.
 * A full radius (mark's circumcircle + clearance) is used deliberately: no false-safe
 * result for star, corner, cross, or rotated marks. Coordinates are artwork-local mm.
 */
public final class RegistrationCutGeometry {
    private RegistrationCutGeometry() {}
    public static final class Report {
        private final String[] reasons = new String[4];
        public boolean valid(){return firstIssue()==null;}
        public String firstIssue(){for(String s:reasons)if(s!=null)return s;return null;}
        public String reason(int mark){return reasons[mark];}
        public boolean invalid(int mark){return reasons[mark]!=null;}
        void fail(int i,String message){if(reasons[i]==null)reasons[i]=message;}
    }
    /** Authoring safety corridor, in artwork-local millimetres. This is not a
     * cutter's usable sheet size; final device-dependent artboard limits still
     * apply at export. Never rewrite invalid coordinates from a legacy project.
     */
    public static final float MAX_MARK_OVERHANG_MM=25f;
    public static float authoringOverhang(float diameter,float clearance){
        return Math.max(MAX_MARK_OVERHANG_MM,RegistrationMarks.markRadius(diameter,clearance)+2f);
    }
    public static float clampManual(float center,float artworkSpan,float diameter,float clearance){
        float d=authoringOverhang(diameter,clearance);
        if(!Float.isFinite(center))return 0f;
        return Math.max(-d,Math.min(artworkSpan+d,center));
    }
    public static Report basic(float w,float h,float diameter,float clearance,float[][] points){
        Report r=new Report();
        if(!Float.isFinite(w)||!Float.isFinite(h)||w<=0||h<=0)
            throw new IllegalArgumentException("Invalid artwork dimensions");
        float radius=RegistrationMarks.markRadius(diameter,clearance);
        if(!Float.isFinite(clearance)||clearance<0||clearance>50)
            throw new IllegalArgumentException("Invalid registration clearance");
        float[][] marks=points==null?RegistrationMarks.defaultPositions(w,h,diameter,clearance):
            RegistrationMarks.copyPositions(points);
        float overhang=authoringOverhang(diameter,clearance);
        for(int i=0;i<marks.length;i++){
            float x=marks[i][0],y=marks[i][1];
            if(x < -overhang||x>w+overhang||y < -overhang||y>h+overhang)
                r.fail(i,"Registration mark "+(i+1)+" is too far outside the artwork");
            for(int j=0;j<i;j++){
                float dx=x-marks[j][0],dy=y-marks[j][1];
                if(dx*dx+dy*dy < 4f*radius*radius-0.0001f){
                    r.fail(i,"Registration marks "+(j+1)+" and "+(i+1)+" overlap their clearance zones");
                    r.fail(j,"Registration marks "+(j+1)+" and "+(i+1)+" overlap their clearance zones");
                }
            }
        }
        return r;
    }
    /** Conservative fallback for engines without a finalized cut geometry model. */
    public static Report strictArtwork(float w,float h,float diameter,float clearance,float[][] selected){
        Report out=basic(w,h,diameter,clearance,selected);
        float[][] marks=points(w,h,diameter,clearance,selected);
        float r=RegistrationMarks.markRadius(diameter,clearance);
        for(int i=0;i<4;i++)if(!out.invalid(i)){
            float x=marks[i][0],y=marks[i][1];
            if(x+r>0&&x-r<w&&y+r>0&&y-r<h)
                out.fail(i,"Registration mark "+(i+1)+" is within an unverified cut area; move it outside the artwork");
        }
        return out;
    }
    /** Mark centers exactly as the exported sheets place them (ADR-0001 SheetFrame). Invalid
     *  custom marks fall back to the raw positions so this report can still name the problem. */
    private static float[][] framed(float w,float h,float diameter,float clearance,float[][] custom,float pad,boolean circleCrop){
        try{return SheetFrame.of(w,h,true,diameter,clearance,custom,pad,circleCrop).marks();}
        catch(IllegalArgumentException invalid){return points(w,h,diameter,clearance,custom);}
    }
    private static float[][] points(float w,float h,float diameter,float clearance,float[][] points){
        return points==null?RegistrationMarks.defaultPositions(w,h,diameter,clearance):points;
    }
    private static float squareDistanceToSegment(float x,float y,float ax,float ay,float bx,float by){
        float dx=bx-ax,dy=by-ay,denom=dx*dx+dy*dy;
        float t=denom<=1e-12f?0:Math.max(0f,Math.min(1f,((x-ax)*dx+(y-ay)*dy)/denom));
        float xx=x-(ax+t*dx),yy=y-(ay+t*dy);
        return xx*xx+yy*yy;
    }
    private static boolean pointInLoop(float x,float y,float[] loop,float factor){
        boolean in=false;int n=loop.length/2;
        for(int k=0,j=n-1;k<n;j=k++){
            float ax=loop[2*k]*factor,ay=loop[2*k+1]*factor;
            float bx=loop[2*j]*factor,by=loop[2*j+1]*factor;
            if((ay>y)!=(by>y) && x<(bx-ax)*(y-ay)/(by-ay)+ax)in=!in;
        }
        return in;
    }
    /** Even-odd compound contours: holes must not be treated as solid vinyl.
     * Boundary proximity is checked even inside holes so clearance remains safe.
     */
    public static boolean hitsFilled(float x,float y,float radius,List<float[]> loops,float factor){
        if(loops==null||loops.isEmpty())return false;
        boolean filled=false;float rr=radius*radius;
        for(float[] p:loops){
            if(p==null||p.length<6||p.length%2!=0)continue;
            int n=p.length/2;
            float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX;
            for(int k=0;k<p.length;k+=2){
                float px=p[k]*factor,py=p[k+1]*factor;
                minX=Math.min(minX,px);minY=Math.min(minY,py);
                maxX=Math.max(maxX,px);maxY=Math.max(maxY,py);
            }
            if(x+radius<minX||x-radius>maxX||y+radius<minY||y-radius>maxY)continue;
            for(int k=0;k<n;k++){
                int q=(k+1)%n;
                if(squareDistanceToSegment(x,y,p[k*2]*factor,p[k*2+1]*factor,
                        p[q*2]*factor,p[q*2+1]*factor)<=rr+1e-6f)return true;
            }
            if(pointInLoop(x,y,p,factor))filled=!filled;
        }
        return filled;
    }
    private static boolean hitsStroke(float x,float y,float radius,float[] line,float strokeRadius){
        if(line==null||line.length<4||line.length%2!=0)return false;
        float rr=radius+Math.max(0f,strokeRadius);rr*=rr;
        for(int k=0;k+3<line.length;k+=2)
            if(squareDistanceToSegment(x,y,line[k],line[k+1],line[k+2],line[k+3])<=rr)return true;
        return false;
    }
    private static void failVinyl(Report out,int mark,String layer){
        out.fail(mark,"Registration mark "+(mark+1)+" is too close to cut vinyl in "+layer);
    }
    public static Report style(StyleKit.Result result,float diameter,float clearance,float[][] selected){
        if(result==null)throw new IllegalArgumentException("Missing finished style geometry");
        float[][] marks=points(result.widthMm,result.heightMm,diameter,clearance,selected);
        Report out=basic(result.widthMm,result.heightMm,diameter,clearance,selected);
        float radius=RegistrationMarks.markRadius(diameter,clearance);
        for(int k=0;k<4;k++)if(!out.invalid(k)){
            for(int li=0;li<result.layers.size();li++){
                StyleKit.Layer layer=result.layers.get(li);
                if(hitsFilled(marks[k][0],marks[k][1],radius,layer.loops,1f)){
                    failVinyl(out,k,"layer "+(li+1));break;
                }
                for(float[] line:layer.lines){
                    if(hitsStroke(marks[k][0],marks[k][1],radius,line,layer.strokeMm/2f)){
                        failVinyl(out,k,"layer "+(li+1)+" pen/cut path");break;
                    }
                }
                if(out.invalid(k))break;
            }
        }
        return out;
    }
    public static Report standard(VinylEngine.Result result){
        if(result==null)throw new IllegalArgumentException("Missing finished vinyl geometry");
        float w=result.settings.widthMm,h=result.heightMm;
        float size=result.settings.markSizeMm,clear=result.settings.registrationClearanceMm;
        Report out=basic(w,h,size,clear,result.marks);
        float radius=RegistrationMarks.markRadius(size,clear);
        for(int m=0;m<4;m++)if(!out.invalid(m)){
            float x=result.marks[m][0],y=result.marks[m][1];
            for(int li=0;li<result.shapes.size();li++){
                if(!VinylEngine.cutLayer(result,li))continue;
                for(VinylEngine.Shape s:result.shapes.get(li)){
                    if(shape(x,y,radius,s)){
                        failVinyl(out,m,"layer "+(li+1));break;
                    }
                }
                if(out.invalid(m))break;
            }
        }
        return out;
    }
    public static boolean shape(float x,float y,float radius,VinylEngine.Shape s){
        if(s==null)return false;
        if(s.circle){float dx=x-s.x,dy=y-s.y,rr=radius+s.r;return dx*dx+dy*dy<=rr*rr;}
        ArrayList<float[]> rings=new ArrayList<>();
        if(s.contours!=null){for(float[] ring:s.contours)rings.add(ring);}
        else if(s.xy!=null)rings.add(s.xy);
        return hitsFilled(x,y,radius,rings,1f);
    }
    public static Report underlay(Report previous,float w,float h,float diameter,float clearance,
            float[][] selected,AutoSilhouetteUnderlay.Output base){
        if(base==null)return previous;
        float[][] marks=points(w,h,diameter,clearance,selected);
        float r=RegistrationMarks.markRadius(diameter,clearance);
        for(int i=0;i<4;i++)if(!previous.invalid(i)&&shape(marks[i][0],marks[i][1],r,base.shape))
            previous.fail(i,"Registration mark "+(i+1)+" overlaps the solid Snip base");
        return previous;
    }
    public static Report cells(LayeredCellPortraitEngine.Result result){
        LayeredCellPortraitEngine.Options o=result.options;
        float mm=o.widthMm/(float)result.width,h=result.height*mm;
        float[][] marks=framed(o.widthMm,h,o.markSizeMm,o.registrationClearanceMm,o.marks,o.outerSpacingMm,o.circleCrop);
        Report out=basic(o.widthMm,h,o.markSizeMm,o.registrationClearanceMm,marks);
        float r=RegistrationMarks.markRadius(o.markSizeMm,o.registrationClearanceMm);
        for(int i=0;i<4;i++)if(!out.invalid(i))for(LayeredCellPortraitEngine.Piece piece:result.pieces){
            ArrayList<float[]> rings=new ArrayList<>();rings.add(piece.outer);rings.addAll(piece.holes);
            if(hitsFilled(marks[i][0],marks[i][1],r,rings,mm)){
                failVinyl(out,i,"cell layer "+(piece.layer+1));break;
            }
        }
        return out;
    }
    public static Report tessellation(TessellationEngine.Result result){
        TessellationEngine.Options o=result.options;
        float mm=o.widthMm/(float)result.width,h=result.height*mm;
        float[][] marks=framed(o.widthMm,h,o.markSizeMm,o.registrationClearanceMm,o.marks,o.outerSpacingMm,o.circleCrop);
        Report out=basic(o.widthMm,h,o.markSizeMm,o.registrationClearanceMm,marks);
        float r=RegistrationMarks.markRadius(o.markSizeMm,o.registrationClearanceMm);
        for(int i=0;i<4;i++)if(!out.invalid(i))for(TessellationEngine.Tile tile:result.tiles){
            ArrayList<float[]> rings=new ArrayList<>();rings.add(tile.xy);
            if(tile.holes!=null)for(float[] hole:tile.holes)rings.add(hole);
            if(hitsFilled(marks[i][0],marks[i][1],r,rings,mm)){
                failVinyl(out,i,"tessellation layer "+(tile.layer+1));break;
            }
        }
        return out;
    }
    /** Conservative raster intersection, using the area of each actual cut pixel.
     * Enlarge the safety envelope for boundary curvature/smoothing tolerance.
     */
    public static boolean hitsRaster(float x,float y,float radius,boolean[] raster,int width,int height,
            float widthMm,float heightMm){
        if(raster==null||raster.length!=width*height)throw new IllegalArgumentException("Missing stencil cut raster");
        float dx=widthMm/width,dy=heightMm/height;
        int x0=Math.max(0,(int)Math.floor((x-radius)/dx));
        int x1=Math.min(width-1,(int)Math.ceil((x+radius)/dx));
        int y0=Math.max(0,(int)Math.floor((y-radius)/dy));
        int y1=Math.min(height-1,(int)Math.ceil((y+radius)/dy));
        float rr=radius*radius;
        for(int yy=y0;yy<=y1;yy++)for(int xx=x0;xx<=x1;xx++)if(raster[yy*width+xx]){
            float px=Math.max(xx*dx,Math.min(x,(xx+1)*dx));
            float py=Math.max(yy*dy,Math.min(y,(yy+1)*dy));
            float vx=x-px,vy=y-py;
            if(vx*vx+vy*vy<=rr)return true;
        }
        return false;
    }
}
