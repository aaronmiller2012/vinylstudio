package com.halftonestudio;

import java.util.Random;
import java.util.concurrent.CancellationException;

/** Pure JVM regression/benchmark; no dependency on Android hardware. */
public final class RC134RenderSpeedHostTest {
    private static void check(boolean valid,String detail){if(!valid)throw new AssertionError(detail);}
    private static float segmentDistance(float px,float py,float ax,float ay,float bx,float by){
        float dx=bx-ax,dy=by-ay,len=dx*dx+dy*dy;
        if(len<1e-12f)return (float)Math.hypot(px-ax,py-ay);
        float t=Math.max(0,Math.min(1,((px-ax)*dx+(py-ay)*dy)/len));
        return (float)Math.hypot(px-(ax+t*dx),py-(ay+t*dy));
    }
    private static boolean priorContains(float[] polygon,float x,float y){
        int n=polygon.length/2;boolean result=false;
        for(int i=0,j=n-1;i<n;j=i++){
            float xi=polygon[2*i],yi=polygon[2*i+1],xj=polygon[2*j],yj=polygon[2*j+1];
            if(segmentDistance(x,y,xi,yi,xj,yj)<1e-7)return true;
            if((yi>y)!=(yj>y)&&x<(xj-xi)*(y-yi)/(yj-yi)+xi)result=!result;
        }
        return result;
    }
    private static boolean previousAllows(SnipMask mask,float x,float y){
        if(x<0||x>1||y<0||y>1)return false;
        for(SnipMask.Operation op:mask.operations()){
            boolean inside=priorContains(op.xy,x,y);
            if((op.kind==SnipMask.REMOVE_INSIDE&&inside)||(op.kind==SnipMask.KEEP_INSIDE&&!inside))return false;
        }
        return true;
    }
    /** Frozen RC133 algorithm; comparison prevents a faster but unsafe Snip clip. */
    private static float previousClearance(SnipMask snips,float x,float y,float w,float h,float relevant){
        if(snips.isEmpty())return Float.POSITIVE_INFINITY;
        if(!previousAllows(snips,x/w,y/h))return 0;
        float nearest=relevant+1;
        for(SnipMask.Operation op:snips.operations()){
            float[] p=op.xy;int n=p.length/2;
            float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX;
            for(int k=0;k<p.length;k+=2){float xx=p[k]*w,yy=p[k+1]*h;
                minX=Math.min(minX,xx);maxX=Math.max(maxX,xx);
                minY=Math.min(minY,yy);maxY=Math.max(maxY,yy);
            }
            float dx=Math.max(Math.max(minX-x,x-maxX),0),dy=Math.max(Math.max(minY-y,y-maxY),0);
            if(dx*dx+dy*dy>relevant*relevant)continue;
            for(int j=0;j<n;j++){int k=(j+1)%n;
                nearest=Math.min(nearest,segmentDistance(x,y,p[j*2]*w,p[j*2+1]*h,p[k*2]*w,p[k*2+1]*h));
            }
        }
        return nearest;
    }
    private static SnipMask mask(){
        SnipMask mask=new SnipMask();
        Random r=new Random(44102);
        for(int j=0;j<24;j++){
            int n=30+j%15;
            float[] vertices=new float[n*2];
            float cx=.1f+r.nextFloat()*.8f,cy=.1f+r.nextFloat()*.8f;
            float rx=.02f+r.nextFloat()*.055f,ry=.02f+r.nextFloat()*.055f;
            for(int i=0;i<n;i++){
                double a=Math.PI*2*i/n;
                vertices[i*2]=Math.max(0,Math.min(1,cx+rx*(float)Math.cos(a)));
                vertices[i*2+1]=Math.max(0,Math.min(1,cy+ry*(float)Math.sin(a)));
            }
            mask.add(new SnipMask.Operation(j%3==0?SnipMask.KEEP_INSIDE:SnipMask.REMOVE_INSIDE,vertices));
        }
        return mask;
    }
    private static void testSnipParity(){
        SnipMask mask=mask();Random r=new Random(6412);
        for(int j=0;j<24000;j++){
            float x=r.nextFloat()*1.12f-.06f,y=r.nextFloat()*1.12f-.06f;
            check(previousAllows(mask,x,y)==mask.allows(x,y),"Snip point ownership "+j);
        }
        for(SnipMask.Operation op:mask.operations())for(int i=0;i<op.xy.length;i+=2){
            float x=op.xy[i],y=op.xy[i+1];
            for(float delta:new float[]{-.000001f,0f,.000001f}){
                check(previousAllows(mask,x+delta,y)==mask.allows(x+delta,y),"Boundary X ownership");
                check(previousAllows(mask,x,y+delta)==mask.allows(x,y+delta),"Boundary Y ownership");
            }
        }
        long before=System.nanoTime();
        for(int j=0;j<24000;j++){
            float w=j%2==0?279.4f:381f;
            float h=j%3==0?177.8f:330.2f;
            float x=r.nextFloat()*w,y=r.nextFloat()*h,allowed=j%2==0?1.2f:4.3f;
            float expected=previousClearance(mask,x,y,w,h,allowed);
            float actual=mask.clearanceWithinMm(x,y,w,h,allowed);
            // Preserve exact branch decisions and floating point values.
            if(Float.floatToIntBits(expected)!=Float.floatToIntBits(actual))
                throw new AssertionError("RC133 Snip parity at sample "+j+": "+expected+" vs "+actual);
        }
        long elapsed=(System.nanoTime()-before)/1_000_000L;
        // Mutation is through SnipMask API: copies must have regenerated bounds.
        SnipMask copied=mask.copy();copied.removeLast();
        check(copied.size()==23&&mask.size()==24,"Snip state-copy isolation");
        System.out.println("PASS RC134 exact Snip clearance parity (24,000 cases; "+elapsed+" ms combined reference+new)");
    }
    private static void testDraftCaching(){
        int w=1440,h=1152,dw=720,dh=576;int[] input=new int[w*h];
        for(int i=0;i<input.length;i++)input[i]=0xff000000|(i*173+0x548312);
        RenderDraftSourceCache cache=new RenderDraftSourceCache();
        int[] initial=cache.draft(input,w,h,dw,dh,8L);
        check(initial.length==dw*dh,"Unexpected draft size");
        check(initial==cache.draft(input,w,h,dw,dh,8L),"Draft not reused");
        check(initial!=cache.draft(input,w,h,dw,dh,9L),"Photo revision must invalidate");
        int[] revision=cache.draft(input,w,h,dw,dh,9L);
        for(int y=0;y<dh;y+=43)for(int x=0;x<dw;x+=37){
            int sy=Math.min(h-1,(int)((y+.5)*h/dh));
            int sx=Math.min(w-1,(int)((x+.5)*w/dw));
            check(revision[y*dw+x]==input[sy*w+sx],"Draft pixel parity");
        }
        check(input==cache.draft(input,w,h,w,h,9L),"Full-size input must bypass draft cache");
        int[] another=input.clone();check(cache.draft(another,w,h,dw,dh,9L)!=revision,"Source identity invalidation");
        cache.clear();check(cache.draft(another,w,h,dw,dh,9L)!=revision,"Memory-pressure eviction");
        final boolean[] cancelled={false};
        Thread t=new Thread(()->{Thread.currentThread().interrupt();try{
            cache.draft(another,w,h,dw,dh,12L);
        }catch(CancellationException expected){cancelled[0]=true;}});
        t.start();try{t.join();}catch(InterruptedException e){throw new AssertionError(e);}
        check(cancelled[0],"Draft cancellation must stop allocation before cache publication");
        System.out.println("PASS RC134 draft pixel parity, revision, source identity, cancellation, memory eviction");
    }
    private static void testPhotoLuminanceParity(){
        Random random=new Random(3401);
        final int w=137,h=93,paper=0xffeddbb2;
        int[] pixels=new int[w*h];
        for(int i=0;i<pixels.length;i++){
            int alpha=i%5==0?random.nextInt(256):255;
            pixels[i]=(alpha<<24)|(random.nextInt(256)<<16)|(random.nextInt(256)<<8)|random.nextInt(256);
        }
        ScaleAwarePhotoSampler sampler=new ScaleAwarePhotoSampler(pixels,w,h,274f,186f,paper);
        int bgR=(paper>>>16)&255,bgG=(paper>>>8)&255,bgB=paper&255;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int p=pixels[y*w+x],a=(p>>>24)&255;
            float r=(((p>>>16)&255)*a+bgR*(255-a))/255f;
            float g=(((p>>>8)&255)*a+bgG*(255-a))/255f;
            float b=((p&255)*a+bgB*(255-a))/255f;
            int old=Math.min(255,Math.max(0,Math.round(.2126f*r+.7152f*g+.0722f*b)));
            float expected=old/255f;
            float observed=sampler.lightAt((x+.5f)*274f/w,(y+.5f)*186f/h);
            check(Math.abs(expected-observed)<0.000001f,"Photo luminance mismatch at ("+x+","+y+")");
        }
        System.out.println("PASS RC134 photographic luminance parity: opaque + alpha + substrate (12,741 pixels)");
    }
    public static void main(String[] args){testSnipParity();testDraftCaching();testPhotoLuminanceParity();}
}
