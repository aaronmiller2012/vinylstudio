package com.halftonestudio;
import java.io.*;
import java.util.*;
/** Local independent oracle; never shipped in Android bytecode. */
final class GeosVectorReferenceBackend implements VectorBooleanEngine.Backend, AutoCloseable {
    private final Process process;
    private final DataInputStream in;
    private final DataOutputStream out;
    private static final class Shape {
        final ArrayList<float[][]> motifs=new ArrayList<>();
        float[][] clip,parity;float vinyl,waste,closeR,openR;boolean clipOnly,conditioned;
    }
    GeosVectorReferenceBackend() throws IOException {
        process=new ProcessBuilder("python3","tests/vector-boolean-reference.py").redirectError(ProcessBuilder.Redirect.INHERIT).start();
        in=new DataInputStream(new BufferedInputStream(process.getInputStream()));out=new DataOutputStream(new BufferedOutputStream(process.getOutputStream()));
    }
    public Object path(float[][] contours){Shape s=new Shape();s.motifs.add(contours);return s;}
    public Object clipPath(float[][] contours){Shape s=new Shape();s.clip=contours;s.clipOnly=true;return s;}
    public Object batch(VectorBooleanEngine.Motif[] motifs,int lo,int hi){Shape s=new Shape();for(int i=lo;i<hi;i++)s.motifs.add(motifs[i].loops);return s;}
    public Object combine(Object a,Object b,boolean disjoint){Shape s=(Shape)a;s.motifs.addAll(((Shape)b).motifs);return s;}
    public Object removeMicroFragments(Object a,float vinyl,float waste){Shape s=(Shape)a;s.vinyl=vinyl;s.waste=waste;return s;}
    public Object intersect(Object a,Object b){Shape s=(Shape)a;s.clip=((Shape)b).clip;return s;}
    @Override public Object cutCondition(Object a,Object b,float closeR,float openR,float tolerance){
        Shape raw=(Shape)a,s=new Shape();s.motifs.addAll(raw.motifs);s.parity=raw.clipOnly?raw.clip:raw.parity;s.clip=((Shape)b).clip;
        s.vinyl=raw.vinyl;s.waste=raw.waste;s.closeR=closeR;s.openR=openR;s.conditioned=true;return s;
    }
    private void loops(float[][] loops)throws IOException {out.writeInt(loops.length);for(float[] p:loops){out.writeInt(p.length);for(float x:p)out.writeFloat(x);}}
    public ArrayList<float[]> contours(Object a){Shape s=(Shape)a;try{
        out.writeInt(s.clipOnly?0:s.conditioned?(s.parity!=null?3:2):1);
        if(!s.clipOnly){if(s.parity!=null)loops(s.parity);else{out.writeInt(s.motifs.size());for(float[][] m:s.motifs)loops(m);out.writeFloat(s.vinyl);out.writeFloat(s.waste);}}
        loops(s.clip);if(s.conditioned){out.writeFloat(s.closeR);out.writeFloat(s.openR);}out.flush();int n=in.readInt();if(n<0)throw new IOException("GEOS reference rejected sheet");
        ArrayList<float[]> result=new ArrayList<>(n);for(int i=0;i<n;i++){float[] p=new float[in.readInt()];for(int j=0;j<p.length;j++)p[j]=in.readFloat();result.add(p);}return result;
    }catch(IOException ex){throw new IllegalStateException("GEOS reference failed",ex);}}
    @Override public ArrayList<float[]> localizedCondition(Object shape,Object clip,List<float[]> raw,List<float[]> windows,float closeR,float openR,float tolerance){
        try{
            out.writeInt(4);loops(raw.toArray(new float[0][]));loops(((Shape)clip).clip);
            out.writeInt(windows.size());for(float[] r:windows)for(float v:r)out.writeFloat(v);
            out.writeFloat(closeR);out.writeFloat(openR);out.flush();
            int n=in.readInt();if(n<0)return null;
            ArrayList<float[]> result=new ArrayList<>(n);
            for(int i=0;i<n;i++){float[] p=new float[in.readInt()];for(int j=0;j<p.length;j++)p[j]=in.readFloat();result.add(p);}return result;
        }catch(IOException ex){throw new IllegalStateException("GEOS reference failed",ex);}
    }
    @Override public void close(){try{out.close();in.close();}catch(IOException ignored){}process.destroy();}
}
