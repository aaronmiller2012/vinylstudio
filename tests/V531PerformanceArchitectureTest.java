package com.halftonestudio;
import java.util.*;

public final class V531PerformanceArchitectureTest {
    static void check(boolean ok,String s){if(!ok)throw new AssertionError(s);}
    static float[] box(Random r){float x=r.nextInt(400)-100,y=r.nextInt(400)-100;return new float[]{x,y,x+r.nextInt(80),y+r.nextInt(80)};}
    static float[] oldCellShape(int family,double rotation){
        int n=family==3?4:family==4?6:family==5?8:family==1?40:family==6?48:family==7?60:family==8?64:32;
        float[] pts=new float[2*n];double maxX=0,maxY=0;
        for(int i=0;i<n;i++){
            double theta=(i*2*Math.PI)/n-Math.PI/2,c=Math.cos(theta),si=Math.sin(theta);
            if(family==2){c=Math.copySign(Math.pow(Math.abs(c),.53),c);si=Math.copySign(Math.pow(Math.abs(si),.53),si);}
            if(family==6)si*=.62;
            if(family==7){double rr=.77+.23*Math.cos(5*(theta+Math.PI/2));c*=rr;si*=rr;}
            if(family==8){double rr=.88+.12*Math.cos(8*(theta+Math.PI/2));c*=rr;si*=rr;}
            double x=c*Math.cos(rotation)-si*Math.sin(rotation),y=c*Math.sin(rotation)+si*Math.cos(rotation);
            pts[2*i]=(float)x;pts[2*i+1]=(float)y;maxX=Math.max(maxX,Math.abs(x));maxY=Math.max(maxY,Math.abs(y));
        }
        double max=Math.max(maxX,maxY);for(int i=0;i<pts.length;i+=2){pts[i]/=max;pts[i+1]/=max;}return pts;
    }
    public static void main(String[] args){
        Random r=new Random(531);int checks=0;
        for(int n:new int[]{0,1,3,17,128,1500}){
            float[][] bounds=new float[n][];for(int i=0;i<n;i++)bounds[i]=box(r);
            ContourBoundsIndex index=new ContourBoundsIndex(bounds);
            for(int t=0;t<600;t++){
                if(n>0&&t%3==0){int id=r.nextInt(n);bounds[id]=box(r);index.update(id,bounds[id]);}
                float[] q=box(r);int include=n>0&&t%7==0?r.nextInt(n):-1;
                int[] expected=new int[n];int len=0;for(int i=0;i<n;i++)if(i==include||ContourBoundsIndex.overlaps(q,bounds[i]))expected[len++]=i;
                check(Arrays.equals(index.query(q,include),Arrays.copyOf(expected,len)),"indexed bounds mismatch");checks++;
            }
        }
        int patterns=0;for(int i=0;i<TessellationMotifEngine.PATTERN_SLOT_COUNT;i++)if(TessellationMotifEngine.selectablePattern(i)){
            GeometryTemplateCache.clear();List<float[]> a=TessellationMotifEngine.tiles(i,130f,110f,13.5f);
            List<float[]> b=TessellationMotifEngine.tiles(i,130f,110f,13.5f);
            check(a.size()==b.size(),"tile count");for(int j=0;j<a.size();j++)check(Arrays.equals(a.get(j),b.get(j)),"tile cache parity");
            if(!a.isEmpty()){float orig=b.get(0)[0];a.get(0)[0]+=55;check(TessellationMotifEngine.tiles(i,130f,110f,13.5f).get(0)[0]==orig,"caller mutated cache");}patterns++;
        }
        for(int family=0;family<=LayeredCellPortraitEngine.SCALLOP;family++)for(int i=0;i<100;i++){
            double angle=i==0?0:r.nextDouble()*Math.PI*4-Math.PI*2;
            check(Arrays.equals(LayeredCellPortraitEngine.normalizedShape(family,angle),oldCellShape(family,angle)),"cell motif bit parity");
        }
        check(GeometryTemplateCache.retainedBytes()<=2*1024*1024,"template memory budget");
        NewStyleGeometryMemo memo=new NewStyleGeometryMemo();VinylStyleSuite.Config c=new VinylStyleSuite.Config();int[] rgb={1,2,3,4};int[] calls={0};
        java.util.function.Supplier<StyleKit.Result> task=()->{calls[0]++;StyleKit.Result out=new StyleKit.Result(100,100);StyleKit.Layer l=new StyleKit.Layer("test",1);l.loops.add(new float[]{0,0,10,0,10,10});out.layers.add(l);return out;};
        StyleKit.Result a=memo.generate(rgb,2,2,c,task);a.layers.get(0).loops.get(0)[0]=99;
        StyleKit.Result b=memo.generate(rgb,2,2,c,task);check(calls[0]==1&&b.layers.get(0).loops.get(0)[0]==0,"memo isolation");
        c.minimumMm+=.1f;memo.generate(rgb,2,2,c,task);check(calls[0]==2,"minimum invalidation");
        c.allowed=new boolean[]{true,false,true,true};memo.generate(rgb,2,2,c,task);check(calls[0]==3,"Snip invalidation");
        rgb[2]^=1;memo.generate(rgb,2,2,c,task);check(calls[0]==4,"photo invalidation");
        c.draft=true;memo.generate(rgb,2,2,c,task);c.draft=false;memo.generate(rgb,2,2,c,task);check(calls[0]==5,"draft contaminated final");
        for(int t=0;t<60;t++){
            int w=20,h=17;boolean[] mask=new boolean[w*h];for(int i=0;i<mask.length;i++)mask[i]=r.nextBoolean();
            StringBuilder expected=new StringBuilder();for(ArrayList<int[]> ring:MultilayerStencilEngine.straightUnionRings(mask,w,h)){
                for(int i=0;i<ring.size();i++){int[] p=ring.get(i);expected.append(i==0?'M':'L').append(p[0]).append(".000 ").append(p[1]).append(".000");}expected.append('Z');}
            check(expected.toString().equals(MultilayerStencilEngine.geometryForTest(mask,w,h,w,h,0).data),"direct ring/SVG mismatch");
        }
        int w=76,h=66;int[] pixels=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){int v=(x*255/w+y*255/h)/2;pixels[y*w+x]=0xff000000|v*0x10101;}
        for(boolean stacked:new boolean[]{false,true})for(float radius:new float[]{0f,.28f}){
            MultilayerStencilEngine.Options o=new MultilayerStencilEngine.Options();o.autoWhiteBackground=false;o.registration=false;o.layerCount=5;o.stackedVinyl=stacked;o.curveRadiusMm=radius;
            try{LayerParallelism.sequential=true;MultilayerStencilEngine.Result seq=MultilayerStencilEngine.generate(pixels,w,h,o);
                String[] paths=new String[seq.layers.size()];for(int i=0;i<paths.length;i++)paths[i]=seq.cutPathData(i);
                LayerParallelism.sequential=false;MultilayerStencilEngine.Result par=MultilayerStencilEngine.generate(pixels,w,h,o);
                for(int i=0;i<paths.length;i++)check(paths[i].equals(par.cutPathData(i)),"parallel stencil cut parity");
            }finally{LayerParallelism.sequential=false;}
        }
        System.out.println("PASS "+checks+" indexed queries, "+patterns+" tessellations, 900 cell motif comparisons, memo invalidation, 60 direct-ring fixtures, serial/parallel stencil paths");
    }
}
