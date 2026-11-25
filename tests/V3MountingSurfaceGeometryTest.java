package com.halftonestudio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * D5: the mounting surface (Settings.paper) is a display setting. Changing it must not change
 * cut geometry, layer colors, layer order, enabled layers or artwork size, in any standard mode.
 *
 * Every case renders the same photo with white, near-black and tan mounting surfaces and compares
 * the PARSED physical geometry (closed loops, circles, open paths, board size) of every sheet.
 * Fixtures include transparent pixels (circle crop), where the old code substituted the surface
 * color for the missing photo.
 */
public final class V3MountingSurfaceGeometryTest {
    static final int[] SURFACES={0xfffaf9f6,0xff18191e,0xfff4e7ce};
    static final List<String> failures=new ArrayList<>();
    static int checks;
    static void check(boolean ok,String m){checks++;if(!ok)failures.add(m);}

    static int[] photo(int w,int h,boolean transparentCorners){
        int[] p=V3AllStyleExportMatrixTest.portrait(w,h);
        // Include colors close to each surface so spot ownership would be tempted to give pixels away.
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            if(x<w/6)p[y*w+x]=0xff1a1b20;
            else if(x>w-w/6)p[y*w+x]=0xfff2e5cc;
        }
        if(transparentCorners)for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double nx=(x+.5-w*.5)/(w*.5),ny=(y+.5-h*.5)/(h*.5);
            if(nx*nx+ny*ny>.92)p[y*w+x]=0;
        }
        return p;
    }

    /** Order-sensitive digest of every sheet's parsed physical geometry, plus layer state. */
    static String signature(VinylEngine.Result r) throws Exception {
        StringBuilder b=new StringBuilder();
        b.append(String.format(Locale.US,"board %.3f x %.3f\n",r.boardW,r.boardH));
        for(int i=0;i<r.settings.layers.size();i++){
            VinylEngine.Layer l=r.settings.layers.get(i);
            b.append(String.format(Locale.US,"L%d %s #%06X enabled=%s cut=%s shapes=%d\n",i,l.name,l.color&0xffffff,
                l.enabled,VinylEngine.cutLayer(r,i),r.shapes.get(i).size()));
            if(!VinylEngine.cutLayer(r,i))continue;
            SvgDxfR13Exporter.SafetyGeometry g=SvgDxfR13Exporter.safetyGeometry(VinylEngine.svg(r,i));
            StringBuilder geo=new StringBuilder();
            for(float[] loop:g.closedLoops){for(float v:loop)geo.append(String.format(Locale.US,"%.3f ",v));geo.append('|');}
            for(double[] c:g.circles)geo.append(String.format(Locale.US,"c%.3f,%.3f,%.3f ",c[0],c[1],c[2]));
            b.append(String.format(Locale.US,"  loops=%d circles=%d open=%d geo=%08x\n",g.closedLoops.size(),g.circles.size(),g.openPaths,geo.toString().hashCode()));
        }
        return b.toString();
    }

    interface Mode { void apply(VinylEngine.Settings s); }

    static void compare(String label,Mode mode,boolean corners) throws Exception {
        int w=64,h=76;int[] px=photo(w,h,corners);
        String reference=null;
        for(int surface:SURFACES){
            VinylEngine.Settings s=VinylEngine.Settings.defaults();
            s.widthMm=101.6f;s.minimumMm=.4f;s.registration=true;s.circleCrop=corners;
            mode.apply(s);
            s.paper=surface;
            String sig=signature(VinylEngine.generate(px,w,h,s));
            if(reference==null){reference=sig;continue;}
            check(reference.equals(sig),String.format(Locale.US,"%s%s: mounting surface #%06X changed the cut sheets\n--- white\n%s--- #%06X\n%s",
                label,corners?" (transparent corners)":"",surface&0xffffff,reference,surface&0xffffff,sig));
        }
    }

    public static void main(String[] args) throws Exception {
        for(boolean corners:new boolean[]{false,true}){
            compare("CMYK dots",s->{s.mode=VinylEngine.CMYK;s.pattern=0;s.classicCmykLines=false;},corners);
            compare("CMYK lines",s->{s.mode=VinylEngine.CMYK;},corners); // defaults: classic CMYK line screen
            compare("spot colors",s->{s.mode=VinylEngine.SPOT;for(VinylEngine.Layer l:s.layers)l.cmykChannel=-1;
                s.layers.get(0).color=0xff1a1b20;s.layers.get(1).color=0xffc0392b;s.layers.get(2).color=0xfff2e5cc;s.layers.get(3).color=0xff2e86c1;},corners);
            compare("stencil",s->{s.mode=VinylEngine.STENCIL;for(VinylEngine.Layer l:s.layers)l.cmykChannel=-1;
                s.layers.get(0).color=0xff1a1b20;s.layers.get(1).color=0xffc0392b;s.layers.get(2).color=0xfff2e5cc;s.layers.get(3).color=0xff2e86c1;},corners);
        }
        if(!failures.isEmpty()){
            System.out.println("FAIL mounting surface geometry: "+failures.size()+" of "+checks+" comparisons");
            for(String f:failures)System.out.println(f.length()>1400?f.substring(0,1400)+"…":f);
            throw new AssertionError(failures.size()+" mounting-surface geometry changes");
        }
        System.out.println("PASS mounting surface: "+checks+" comparisons; cut geometry, colors, order and size unchanged");
    }
}
