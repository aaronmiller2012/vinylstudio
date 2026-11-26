package com.halftonestudio;
/** Linear-light area mixture of distinct opaque surfaces, not translucent ink. */
final class RibbonColorMath {
 private RibbonColorMath(){}
 private static final float[] LINEAR=new float[256];
 static{for(int i=0;i<256;i++){double v=i/255.;LINEAR[i]=(float)(v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4));}}
 static float[] rgb(int c){return new float[]{LINEAR[(c>>>16)&255],LINEAR[(c>>>8)&255],LINEAR[c&255]};}
 static float[] lab(float r,float g,float b){double x=(.4124*r+.3576*g+.1805*b)/.95047,y=.2126*r+.7152*g+.0722*b,z=(.0193*r+.1192*g+.9505*b)/1.08883;double fx=f(x),fy=f(y),fz=f(z);return new float[]{(float)(116*fy-16),(float)(500*(fx-fy)),(float)(200*(fy-fz))};}
 private static double f(double t){return t>.008856?Math.cbrt(t):7.787*t+16./116;}
 static float[] composite(float[][] rgb,int a,int b,float wa,float wb){float base=(1-wa)*(1-wb),ca=a>=b?wa:wa*(1-wb),cb=b>a?wb:wb*(1-wa);return lab(base*rgb[0][0]+ca*rgb[a][0]+cb*rgb[b][0],base*rgb[0][1]+ca*rgb[a][1]+cb*rgb[b][1],base*rgb[0][2]+ca*rgb[a][2]+cb*rgb[b][2]);}
 static float chroma(float[] a,float[] b){float x=a[1]-b[1],y=a[2]-b[2];return x*x+y*y;}
 static float cost(float[] a,float[] b){float d=a[0]-b[0];return 2.1f*d*d+.85f*chroma(a,b);}
 static void fillCosts(float[] target,float[][] palette,float[] out){for(int i=0;i<palette.length;i++)out[i]=cost(target,palette[i]);}
 static int[] strand(float[][] targets,float[][] palette){int n=targets.length,m=palette.length;int[] out=new int[n];float[] prev=new float[m],next=new float[m];byte[] back=new byte[n*m];float[][] transitions=new float[m][m];for(int i=0;i<m;i++)for(int j=0;j<m;j++)transitions[i][j]=i==j?0:115f+.035f*StyleKit.labDist2(palette[i],palette[j]);for(int k=0;k<n;k++){CancellationCheckpoint.check();for(int j=0;j<m;j++){float best=Float.POSITIVE_INFINITY;int from=0;for(int i=0;i<m;i++){float v=prev[i]+transitions[i][j];if(v<best){best=v;from=i;}}next[j]=best+(targets[k]==null?0:cost(targets[k],palette[j]));back[k*m+j]=(byte)from;}float min=Float.POSITIVE_INFINITY;for(float x:next)min=Math.min(min,x);for(int j=0;j<m;j++)next[j]-=min;float[] swap=prev;prev=next;next=swap;}int best=0;for(int j=1;j<m;j++)if(prev[j]<prev[best])best=j;for(int k=n-1;k>=0;k--){out[k]=best;best=back[k*m+best]&255;}return out;}
}
