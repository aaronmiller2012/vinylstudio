package com.halftonestudio;

/**
 * M7: exact, linear-time binary morphology with the same semantics as the original
 * brute-force kernel loop in OrganicFlowPortraitEngine (kept as {@link #bruteForce} for tests).
 *
 * Kernel: all integer offsets with x*x+y*y &lt;= r*r (disk) or |x|,|y| &lt;= r (orthogonal).
 * Pixels outside the image are background: dilation ignores them; erosion fails on them.
 * <ul>
 * <li>Disk: dilation(p) == (squared Euclidean distance from p to the nearest set pixel) &lt;= r^2;
 *   erosion(p) == (squared distance to the nearest unset-or-outside pixel) &gt; r^2. Distances
 *   come from an exact integer two-pass lower-envelope transform (Felzenszwalb-Huttenlocher).</li>
 * <li>Orthogonal: the square is separable, so a sliding-window count along rows then columns.</li>
 * </ul>
 * Cost O(w*h) instead of O(w*h*kernel); the Camouflage/Organic Flow mask conditioning used
 * 4 passes of up to ~300-tap kernels per colour sheet. Pure Java.
 */
final class BinaryMorphology {
    private BinaryMorphology(){}

    static void apply(boolean[] in,boolean[] out,int w,int h,int r,boolean orthogonal,boolean dilation){
        if(r<=0){System.arraycopy(in,0,out,0,w*h);return;}
        if(orthogonal)square(in,out,w,h,r,dilation);else disk(in,out,w,h,r,dilation);
    }

    // ------------------------------------------------------------ orthogonal (separable)
    private static void square(boolean[] in,boolean[] out,int w,int h,int r,boolean dilation){
        int win=2*r+1;
        int[] rows=new int[w*h];
        for(int y=0;y<h;y++){
            if((y&31)==0)StyleKit.checkCancelled();
            int base=y*w,count=0;
            for(int x=0;x<=Math.min(r,w-1);x++)if(in[base+x])count++;
            for(int x=0;x<w;x++){
                rows[base+x]=count;
                int add=x+r+1,rem=x-r;
                if(add<w&&in[base+add])count++;
                if(rem>=0&&in[base+rem])count--;
            }
        }
        // rows[p] = set pixels in the horizontal window; full row window == win (out of range counts 0)
        for(int x=0;x<w;x++){
            if((x&31)==0)StyleKit.checkCancelled();
            int count=0,full=0;
            for(int y=0;y<=Math.min(r,h-1);y++){int v=rows[y*w+x];count+=v;if(v==win)full++;}
            for(int y=0;y<h;y++){
                out[y*w+x]=dilation?count>0:full==win;
                int add=y+r+1,rem=y-r;
                if(add<h){int v=rows[add*w+x];count+=v;if(v==win)full++;}
                if(rem>=0){int v=rows[rem*w+x];count-=v;if(v==win)full--;}
            }
        }
    }

    // ------------------------------------------------------------ disk (exact EDT)
    private static void disk(boolean[] in,boolean[] out,int w,int h,int r,boolean dilation){
        long r2=(long)r*r;
        if(dilation){
            int[] d=edt(w,h,i->in[i]);
            for(int i=0;i<w*h;i++)out[i]=d[i]<=r2;
        }else{
            // Pad by r so outside pixels are features of the background set.
            int pw=w+2*r,ph=h+2*r;
            int[] d=edt(pw,ph,i->{int x=i%pw-r,y=i/pw-r;return x<0||y<0||x>=w||y>=h||!in[y*w+x];});
            for(int y=0;y<h;y++){int row=(y+r)*pw+r,o=y*w;for(int x=0;x<w;x++)out[o+x]=d[row+x]>r2;}
        }
    }
    private interface Feature{boolean at(int i);}
    /** Exact squared distance to the nearest feature pixel. "No feature" uses w+h+1, which is
     * larger than any in-image distance, so every intermediate stays exact in a double. */
    private static int[] edt(int w,int h,Feature feature){
        final int INF=w+h+1;
        int[] g=new int[w*h];
        // Column pass: 1D distance (squared later) to the nearest feature in the column.
        for(int x=0;x<w;x++){
            if((x&31)==0)StyleKit.checkCancelled();
            int last=-1;
            for(int y=0;y<h;y++){int i=y*w+x;if(feature.at(i))last=y;g[i]=last<0?INF:Math.min(INF,y-last);}
            last=-1;
            for(int y=h-1;y>=0;y--){int i=y*w+x;if(g[i]==0)last=y;if(last>=0)g[i]=Math.min(g[i],last-y);}
        }
        // Row pass: lower envelope of parabolas f(q)+(x-q)^2 with f(q)=g^2.
        int[] out=new int[w*h];
        long[] f=new long[w];int[] v=new int[w];double[] z=new double[w+1];
        for(int y=0;y<h;y++){
            if((y&31)==0)StyleKit.checkCancelled();
            int base=y*w;
            for(int x=0;x<w;x++){long t=g[base+x];f[x]=t*t;}
            int k=0;v[0]=0;z[0]=Double.NEGATIVE_INFINITY;z[1]=Double.POSITIVE_INFINITY;
            for(int q=1;q<w;q++){
                double s=((f[q]+(long)q*q)-(f[v[k]]+(long)v[k]*v[k]))/(2.0*(q-v[k]));
                while(s<=z[k]){k--;s=((f[q]+(long)q*q)-(f[v[k]]+(long)v[k]*v[k]))/(2.0*(q-v[k]));}
                k++;v[k]=q;z[k]=s;z[k+1]=Double.POSITIVE_INFINITY;
            }
            k=0;
            for(int q=0;q<w;q++){
                while(z[k+1]<q)k++;
                long dq=q-v[k],val=f[v[k]]+dq*dq;
                out[base+q]=(int)Math.min(Integer.MAX_VALUE,val);
            }
        }
        return out;
    }

    /** The original O(w*h*kernel) loop (reference for equivalence tests). */
    static void bruteForce(boolean[] input,boolean[] output,int w,int h,int radius,boolean orthogonal,boolean dilation){
        int count=0;
        for(int y=-radius;y<=radius;y++)for(int x=-radius;x<=radius;x++)if(orthogonal||x*x+y*y<=radius*radius)count++;
        int[] dx=new int[count],dy=new int[count];int n=0;
        for(int y=-radius;y<=radius;y++)for(int x=-radius;x<=radius;x++)if(orthogonal||x*x+y*y<=radius*radius){dx[n]=x;dy[n]=y;n++;}
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            boolean value=!dilation;
            for(int k=0;k<dx.length;k++){
                int xx=x+dx[k],yy=y+dy[k];
                boolean inside=xx>=0&&xx<w&&yy>=0&&yy<h&&input[yy*w+xx];
                if(dilation&&inside){value=true;break;}
                if(!dilation&&!inside){value=false;break;}
            }
            output[y*w+x]=value;
        }
    }
}
