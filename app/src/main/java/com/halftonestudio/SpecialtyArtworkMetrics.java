package com.halftonestudio;

import java.util.*;

/** Read-only pre-export measurements. Specialty export may condition a copy later. */
final class SpecialtyArtworkMetrics {
    static final class Input {
        final List<List<float[]>> sheets=new ArrayList<>();
        final List<List<VinylEngine.Shape>> independent=new ArrayList<>();
        final List<String> names=new ArrayList<>();
        final List<Integer> open=new ArrayList<>();
        final List<Double> openLength=new ArrayList<>();
        long points;
        boolean footprintLimited;
        final List<Integer> closed=new ArrayList<>();
        final List<Double> closedLength=new ArrayList<>();
        void limitFootprint(){if(!footprintLimited){footprintLimited=true;for(List<float[]> sheet:sheets)sheet.clear();for(List<VinylEngine.Shape> sheet:independent)sheet.clear();}}
        void sheet(String name){names.add(name);sheets.add(new ArrayList<>());independent.add(new ArrayList<>());open.add(0);openLength.add(0d);closed.add(0);closedLength.add(0d);}
        void ring(int layer,float[] xy,float scale){
            CancellationCheckpoint.check();
            if(xy.length<6||xy.length%2!=0||!Float.isFinite(scale)||scale<=0)throw new IllegalArgumentException("Invalid contour");
            points+=xy.length/2;if(points>120000)limitFootprint();
            float[] copy=xy.clone();for(int j=0;j<copy.length;j++){copy[j]*=scale;if(!Float.isFinite(copy[j]))throw new IllegalArgumentException("Invalid coordinate");}
            closed.set(layer,closed.get(layer)+1);closedLength.set(layer,closedLength.get(layer)+length(copy,true));
            if(!footprintLimited)sheets.get(layer).add(copy);
        }
        void piece(int layer,float[] outer,List<float[]> holes,float scale){
            int start=sheets.get(layer).size();ring(layer,outer,scale);for(float[] h:holes)ring(layer,h,scale);
            if(footprintLimited)return;
            List<float[]> all=sheets.get(layer);independent.get(layer).add(VinylEngine.Shape.compound(all.subList(start,all.size()).toArray(new float[0][])));
        }
        void line(int layer,float[] xy){
            if(xy.length<4||xy.length%2!=0)throw new IllegalArgumentException("Invalid open path");
            points+=xy.length/2;if(points>120000)limitFootprint();
            open.set(layer,open.get(layer)+1);openLength.set(layer,openLength.get(layer)+length(xy,false));
        }
    }
    static Input collect(StyleKit.Result style,int styleId,TessellationEngine.Result tiles,LayeredCellPortraitEngine.Result cells,MultilayerStencilEngine.Result tonal,SpaceFillingRibbon.Result ribbon,AutoSilhouetteUnderlay.Output base,boolean only,Runnable guard){
        Input in=new Input();
        if(base!=null){in.sheet("Solid Snip base");VinylEngine.Shape s=base.shape;
            if(s.circle)throw new IllegalArgumentException("Unexpected circular base");
            for(float[] r:s.contours==null?new float[][]{s.xy}:s.contours)in.ring(0,r,1);}
        int offset=in.sheets.size();if(only){if(base==null)throw new IllegalStateException("Base pending");return in;}
        if(style!=null){
            ArrayList<Integer> order=new ArrayList<>();for(int i=0;i<style.layers.size();i++)order.add(i);
            order.sort(Comparator.comparingInt(i->NewStyleColorSlots.slot(styleId,style.layers.get(i).name,i)));
            for(int i:order){guard.run();StyleKit.Layer l=style.layers.get(i);if(l.loops.isEmpty()&&l.lines.isEmpty())continue;
                int slot=in.sheets.size();in.sheet(l.name);for(float[] r:l.loops){guard.run();in.ring(slot,r,1);}
                for(float[] r:l.lines){guard.run();in.line(slot,r);}}
        }else if(tiles!=null){
            // Keep stable swatch IDs while measuring visibility in the actual application order.
            int[] slots=new int[tiles.options.layerCount];boolean[] seen=new boolean[slots.length];
            for(int pos=0;pos<slots.length;pos++){
                int id=tiles.stipple==null?pos:tiles.stipple.options.order[pos];
                if(id<0||id>=slots.length||seen[id])throw new IllegalArgumentException("Invalid physical layer order");
                seen[id]=true;slots[id]=offset+pos;in.sheet("Color "+(id+1));
            }
            float scale=tiles.options.widthMm/tiles.width;
            for(TessellationEngine.Tile t:tiles.tiles){guard.run();in.piece(slots[t.layer],t.xy,tiles.stipple==null?Collections.emptyList():Arrays.asList(t.holes),scale);}
        }else if(cells!=null){
            for(int i=0;i<cells.options.layerCount;i++)in.sheet("Color "+(i+1));float scale=cells.options.widthMm/cells.width;
            for(LayeredCellPortraitEngine.Piece p:cells.pieces){guard.run();in.piece(offset+p.layer,p.outer,p.holes,scale);}
        }else if(tonal!=null){
            for(int i=0;i<tonal.layers.size();i++){guard.run();int slot=in.sheets.size();in.sheet("Stencil color "+(i+1));appendPath(in,slot,tonal.cutPathData(i),guard);}
        }else if(ribbon!=null){
            in.sheet("Vinyl ribbon");float scale=ribbon.widthMm/ribbon.width;
            for(ArrayList<int[]> loop:MultilayerStencilEngine.straightUnionRings(ribbon.mask,ribbon.width,ribbon.height)){
                guard.run();float[] xy=new float[loop.size()*2];int j=0;for(int[] q:loop){xy[j++]=q[0];xy[j++]=q[1];}in.ring(offset,xy,scale);}
        }else throw new IllegalArgumentException("Unsupported renderer");
        return in;
    }
    static Input collectSpiral(SpiralPortraitRibbon.Result spiral,AutoSilhouetteUnderlay.Output base,boolean only,Runnable guard){
        Input in=new Input();if(base!=null){in.sheet("Solid Snip base");VinylEngine.Shape s=base.shape;for(float[] r:s.contours==null?new float[][]{s.xy}:s.contours)in.ring(0,r,1);}
        if(only)return in;guard.run();if(spiral.draft)throw new IllegalStateException("Spiral analysis budget");
        String svg=spiral.svg();int start=svg.indexOf(" d=\"")+4,end=svg.indexOf('"',start);if(start<4||end<start)throw new IllegalArgumentException("Missing spiral path");
        int slot=in.sheets.size();in.sheet("Photo Spiral");appendPath(in,slot,svg.substring(start,end),spiral.widthMm/spiral.imageWidth,guard);return in;
    }
    /** Flatten exported stencil M/L/C/Z paths in millimeters; cubic length/area are approximate. */
    static void appendPath(Input in,int layer,String data,Runnable guard){appendPath(in,layer,data,1f,guard);}
    static void appendPath(Input in,int layer,String data,float scale,Runnable guard){
        if(!Float.isFinite(scale)||scale<=0)throw new IllegalArgumentException("Invalid path scale");
        int pos=0;ArrayList<Float> ring=null;float x=0,y=0;
        while(pos<data.length()){
            guard.run();char op=data.charAt(pos++);if(Character.isWhitespace(op))continue;
            if(op=='Z'){if(ring==null)throw new IllegalArgumentException("Unexpected close");float[] q=new float[ring.size()];for(int j=0;j<q.length;j++)q[j]=ring.get(j);in.ring(layer,q,1);ring=null;continue;}
            int n=op=='C'?6:op=='Q'?4:op=='M'||op=='L'?2:0;if(n==0)throw new IllegalArgumentException("Unsupported contour command");float[] v=new float[n];
            for(int j=0;j<n;j++){while(pos<data.length()&&Character.isWhitespace(data.charAt(pos)))pos++;int from=pos;while(pos<data.length()&&"0123456789.+-".indexOf(data.charAt(pos))>=0)pos++;v[j]=Float.parseFloat(data.substring(from,pos))*scale;if(!Float.isFinite(v[j]))throw new IllegalArgumentException("Nonfinite contour");}
            if(op=='M'){if(ring!=null)throw new IllegalArgumentException("Open contour");ring=new ArrayList<>();}
            if(ring==null)throw new IllegalArgumentException("Missing move");
            if(op=='Q'){
                v=new float[]{x+2*(v[0]-x)/3,y+2*(v[1]-y)/3,v[2]+2*(v[0]-v[2])/3,v[3]+2*(v[1]-v[3])/3,v[2],v[3]};op='C';n=6;
            }
            if(op=='C'){
                final ArrayList<Float> points=ring;
                PrecisionCurveSampler.cubic(x,y,v[0],v[1],v[2],v[3],v[4],v[5],(px,py)->{
                    if(points.size()>=4_000_000)throw new IllegalStateException("Single contour exceeds memory budget");
                    points.add(px);points.add(py);
                },guard);
            }else{ring.add(v[0]);ring.add(v[1]);}
            x=v[n-2];y=v[n-1];if(ring.size()>4_000_000)throw new IllegalStateException("Single contour exceeds memory budget");
        }
        if(ring!=null)throw new IllegalArgumentException("Unclosed contour");
    }
    static double length(float[] xy,boolean closed){double total=0;for(int i=0;i<xy.length;i+=2){CancellationCheckpoint.check();if(!Float.isFinite(xy[i])||!Float.isFinite(xy[i+1]))throw new IllegalArgumentException("Invalid coordinate");if(i>0)total+=Math.hypot(xy[i]-xy[i-2],xy[i+1]-xy[i-1]);}if(closed)total+=Math.hypot(xy[0]-xy[xy.length-2],xy[1]-xy[xy.length-1]);return total;}
    static String report(Input in,Runnable guard){return report(in,0,guard);}
    static String report(Input in,float minimum,Runnable guard){return report(in,minimum,minimum,guard);}
    static String report(Input in,float minimum,float gap,Runnable guard){
        StringBuilder b=new StringBuilder("Current rendered artwork before export conditioning. Registration excluded. Curved contours are approximated. Open paths are not vinyl area.\n\n");
        for(int i=0;i<in.sheets.size();i++){guard.run();double len=in.closedLength.get(i);
            b.append(String.format(Locale.US,"%s\n%d closed contours · %.1f mm closed length\n%d open paths · %.1f mm open length\n\n",in.names.get(i),in.closed.get(i),len,in.open.get(i),in.openLength.get(i)));}
        if(in.footprintLimited){b.append("Filled footprint skipped: geometry exceeds 120,000 analysis points. Contour measurements above remain available.");return b.toString();}
        try{long start=System.nanoTime();ArtworkVisibility.Layer[] m=ArtworkVisibility.measureSpecialty(in.sheets,in.independent,()->{guard.run();if(System.nanoTime()-start>5_000_000_000L)throw new IllegalStateException("Analysis time limit");});
            b.append("Filled footprint, bottom to top:\n\n");for(int i=0;i<m.length;i++){guard.run();b.append(String.format(Locale.US,"%s\n%d footprint components · %d holes\n%.2f cm² footprint · %.2f visible · %.2f hidden\n\n",in.names.get(i),m[i].pieces,m[i].holes,m[i].area/100,m[i].visible/100,m[i].hidden/100));b.append(ArtworkVisibility.diagnostics(m[i],minimum,gap,()->{guard.run();if(System.nanoTime()-start>5_000_000_000L)throw new IllegalStateException("Analysis time limit");})).append("\n");}
        }catch(java.util.concurrent.CancellationException cancelled){throw cancelled;}catch(RuntimeException|OutOfMemoryError e){b.append("Filled footprint unavailable for this geometry or analysis budget. Contour measurements above remain available.");}
        return b.toString();
    }
}
