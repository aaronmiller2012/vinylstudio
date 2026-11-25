package com.halftonestudio;
import java.util.*;
import java.util.concurrent.*;
/** Build 529 reference parity and bounded cache tests. Timing is diagnostic only. */
public final class V3RenderPipelineOptimizationTest {
    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    static void same(float[] a,float[] b,String label){check(Arrays.equals(a,b),label);}
    static float[] oldBlur(float[] a,int w,int h,float sigma){
        if(sigma<.3f)return a.clone();int r=Math.max(1,(int)Math.ceil(sigma*3));
        float[] k=new float[2*r+1];float sum=0;
        for(int i=-r;i<=r;i++){k[i+r]=(float)Math.exp(-(i*i)/(2*sigma*sigma));sum+=k[i+r];}
        for(int i=0;i<k.length;i++)k[i]/=sum;
        float[] t=new float[a.length],o=new float[a.length];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){float s=0;for(int i=-r;i<=r;i++)s+=a[y*w+StyleKit.clampI(x+i,0,w-1)]*k[i+r];t[y*w+x]=s;}
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){float s=0;for(int i=-r;i<=r;i++)s+=t[StyleKit.clampI(y+i,0,h-1)*w+x]*k[i+r];o[y*w+x]=s;}
        return o;
    }
    static void oldLevels(StyleKit.Source s){int cnt=0;for(boolean v:s.valid)if(v)cnt++;if(cnt<16)return;
        float[] t=new float[cnt];int k=0;for(int i=0;i<s.lum.length;i++)if(s.valid[i])t[k++]=s.lum[i];
        Arrays.sort(t);float lo=t[(int)(cnt*.02f)],hi=t[(int)(cnt*.98f)];if(hi-lo<.04f)return;
        for(int i=0;i<s.lum.length;i++)s.lum[i]=StyleKit.clampF((s.lum[i]-lo)/(hi-lo),0,1);
    }
    public static void main(String[] args)throws Exception{
        Random rnd=new Random(530);int blurs=0,plans=0;
        for(int w:new int[]{1,2,7,35,127})for(int h:new int[]{1,3,18,83})for(float sig:new float[]{.1f,.3f,.75f,1.2f,3,12}){
            float[] a=new float[w*h];for(int i=0;i<a.length;i++)a[i]=rnd.nextFloat()*2-1;
            same(oldBlur(a,w,h,sig),StyleKit.blur(a,w,h,sig),"blur "+w+"x"+h+" sigma "+sig);blurs++;
        }
        for(int w:new int[]{9,57,1451})for(int mode=0;mode<4;mode++){
            int h=31;int[] p=new int[w*h];boolean[] mask=new boolean[p.length];
            for(int i=0;i<p.length;i++){p[i]=mode==0?0xff707070:rnd.nextInt();mask[i]=mode==1||rnd.nextBoolean();}
            StyleKit.Source a=new StyleKit.Source(p,w,h,mask,150),b=new StyleKit.Source(p,w,h,mask,150);
            if(w<1400)for(int i=0;i<p.length;i++){int c=p[i];check(a.valid[i]==((c>>>24)>=128&&mask[i]),"mask");float v=(.2126f*((c>>16)&255)+.7152f*((c>>8)&255)+.0722f*(c&255))/255f;check(Float.floatToIntBits(v)==Float.floatToIntBits(a.lum[i]),"luminance");}
            oldLevels(a);b.autoLevels(.02f,.98f);same(a.lum,b.lum,"levels");
        }
        RenderImageFieldCache cache=new RenderImageFieldCache(2,800);
        float[] input=new float[100];for(int i=0;i<100;i++)input[i]=rnd.nextFloat();
        float[] original=oldBlur(input,10,10,1);
        float[] first=StyleKit.withImageFieldCache(cache,()->StyleKit.blur(input,10,10,1));first[0]=999;
        same(original,StyleKit.withImageFieldCache(cache,()->StyleKit.blur(input.clone(),10,10,1)),"cached copy");check(cache.hitCount()==1,"content hit");
        input[0]=.99f;same(oldBlur(input,10,10,1),StyleKit.withImageFieldCache(cache,()->StyleKit.blur(input,10,10,1)),"changed input");
        same(oldBlur(input,5,20,1),StyleKit.withImageFieldCache(cache,()->StyleKit.blur(input,5,20,1)),"dimensions");
        same(StyleKit.edgeMap(input,5,20),StyleKit.withImageFieldCache(cache,()->StyleKit.edgeMap(input,5,20)),"filter kind");check(cache.retainedBytes()<=800,"budget");
        CountDownLatch start=new CountDownLatch(1),resume=new CountDownLatch(1);
        Thread worker=new Thread(()->cache.compute(input,10,10,99,0,()->{start.countDown();try{resume.await();}catch(InterruptedException e){throw new AssertionError(e);}return input.clone();}));
        worker.start();check(start.await(2,TimeUnit.SECONDS),"worker");cache.clear();resume.countDown();worker.join(2000);check(!worker.isAlive()&&cache.retainedBytes()==0,"obsolete cache insertion");
        Thread.currentThread().interrupt();try{StyleKit.blur(input,10,10,1);throw new AssertionError("no cancellation");}catch(CancellationException expected){}finally{Thread.interrupted();}
        SmartPaletteReduction.Workspace ws=new SmartPaletteReduction.Workspace();
        for(int trial=0;trial<22;trial++){
            int n=2+rnd.nextInt(8);int[] colors=new int[n],ranks=SmartPaletteReduction.identity(n);
            for(int i=0;i<n;i++)colors[i]=0xff000000|rnd.nextInt(0x1000000);
            int[] samples=trial==0?null:new int[trial==1?3400:250];
            if(samples!=null)for(int i=0;i<samples.length;i++)samples[i]=trial==2?0:rnd.nextInt();
            for(int policy=0;policy<5;policy++)for(int count=n;count>=1;count--){int eligible=(1<<n)-1,required=policy==1||policy==2||policy==4?1:0;
                int[] old=PaletteReduction529Reference.plan(colors,ranks,count,samples,policy,required,eligible),next=ws.plan(colors,ranks,count,samples,policy,required,eligible);
                check(Arrays.equals(old,next),"palette policy="+policy+" count="+count);colors=SmartPaletteReduction.permute(colors,next);ranks=SmartPaletteReduction.permute(ranks,next);plans++;}
            if(samples!=null&&samples.length>0)samples[0]=0xff010203;
            check(Arrays.equals(PaletteReduction529Reference.plan(colors,ranks,1,samples,0),ws.plan(colors,ranks,1,samples,0)),"palette mutation");
        }
        check(ws.hits()>0&&ws.misses()>0,"workspace reuse");
        int w=850,h=650;float[] field=new float[w*h];for(int i=0;i<field.length;i++)field[i]=rnd.nextFloat();
        for(int i=0;i<3;i++){oldBlur(field,w,h,4);StyleKit.blur(field,w,h,4);}
        long oldNs=0,newNs=0;float[] a=null,b=null;
        for(int i=0;i<5;i++){long t=System.nanoTime();a=oldBlur(field,w,h,4);oldNs+=System.nanoTime()-t;t=System.nanoTime();b=StyleKit.blur(field,w,h,4);newNs+=System.nanoTime()-t;same(a,b,"large blur");}
        RenderImageFieldCache c=new RenderImageFieldCache(2,12L*1024*1024);StyleKit.withImageFieldCache(c,()->StyleKit.blur(field,w,h,4));
        long t=System.nanoTime();float[] hit=StyleKit.withImageFieldCache(c,()->StyleKit.blur(field,w,h,4));long hitNs=System.nanoTime()-t;same(a,hit,"large cache");
        System.out.printf("PASS render pipeline: %d blur cases, 12 source/levels cases, %d palette plans; cache ownership, keys, budget, invalidation, cancellation. Host mean blur %.2f -> %.2f ms; cache hit %.2f ms%n",blurs,plans,oldNs/5e6,newNs/5e6,hitNs/1e6);
    }
}
