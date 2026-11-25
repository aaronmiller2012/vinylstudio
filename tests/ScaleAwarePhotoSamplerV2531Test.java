package com.halftonestudio;

import java.util.Arrays;

/** Deterministic host-only physical-coordinate and geometry-strategy contract. */
public final class ScaleAwarePhotoSamplerV2531Test {
    private static void check(boolean value,String message){
        if(!value)throw new AssertionError(message);
    }
    private static void close(float a,float b,float eps,String msg){
        check(Float.isFinite(a)&&Float.isFinite(b)&&Math.abs(a-b)<=eps,msg+": "+a+" vs "+b);
    }
    private static final ScaleAwarePhotoSampler.Coverage BLACKNESS = pixel ->
        1f - ((pixel>>>16)&255)/255f;
    private static int[] image(int size){
        int[] out=new int[size*size];
        for(int y=0;y<size;y++)for(int x=0;x<size;x++){
            // Half dark, half white: an exact scale-invariance fixture.
            out[y*size+x]=x<size/2?0xff101010:0xffffffff;
        }
        return out;
    }
    private static void scaleAndRegions(){
        ScaleAwarePhotoSampler a=new ScaleAwarePhotoSampler(image(64),64,64,64,64,0xffffffff);
        ScaleAwarePhotoSampler b=new ScaleAwarePhotoSampler(image(128),128,128,64,64,0xffffffff);
        close(a.darknessAt(12,12),b.darknessAt(12,12),.001f,"Dark physical coordinate changed with source resolution");
        close(a.lightAt(50,20),b.lightAt(50,20),.001f,"Light physical coordinate changed with source resolution");
        ScaleAwarePhotoSampler.RegionSignal r=a.region(12,12,52,52),t=b.region(12,12,52,52);
        check(r.gradientX>0 && Math.abs(r.gradientY)<.1f,"Physical vertical edge missing/rotated");
        close(r.gradientX,t.gradientX,6.0f,"Region gradient changes excessively with source resolution");
        check(r.contrast>2 && r.contrast<255,"Region contrast is invalid");
        check(r.light>0 && r.light<1,"Region light mean is invalid");
    }
    private static void ribbonsAndMotifs(){
        ScaleAwarePhotoSampler a=new ScaleAwarePhotoSampler(image(64),64,64,64,64,0xffffffff);
        float d=a.motifCoverage(16,32,3,BLACKNESS);
        check(d>.85f,"Dark-region motif coverage lost");
        check(a.motifCoverage(48,32,3,BLACKNESS)==0f,"Blank-center motifs gained phantom cuts");
        float positive=a.ribbonBias(31.5f,32,1,0,2f,.8f,BLACKNESS);
        check(positive<0f,"Independent side ribbon samples did not follow dark side");
        check(Math.abs(positive)<=.20f,"Ribbon bias exceeded physical bound");
        check(a.ribbonBias(31.5f,32,1,0,2f,0,BLACKNESS)==0f,"Blank layer gained ribbon asymmetry");
        check(a.ribbonBias(0.05f,5,-1,0,1,.8f,BLACKNESS)==0f,"Outside neighbor generated ribbon bias");
    }
    private static void alphaAndBounds(){
        int[] pixels=new int[16*16];Arrays.fill(pixels,0xffffffff);
        pixels[8*16+8]=0xff000000;
        pixels[8*16+9]=0x00000000;
        ScaleAwarePhotoSampler a=new ScaleAwarePhotoSampler(pixels,16,16,16,16,0xffffffff);
        check(a.darknessAt(9.5f,8.5f)<.01f,"Transparent black must composite to white");
        close(a.motifCoverage(8.5f,8.5f,1f,BLACKNESS),.89f,.12f,"Alpha exclusion should retain center evidence");
        check(a.coverageAt(-1,8,BLACKNESS)==0,"Outside physical frame must have zero coverage");
        boolean rejected=false;
        try {new ScaleAwarePhotoSampler(new int[3],2,2,10,10,0xffffffff);}
        catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"Invalid buffer lengths must fail fast");
        rejected=false;
        try {a.region(4,4,2,2);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"Invalid region geometry must fail fast");
    }
    public static void main(String[] args){
        scaleAndRegions();ribbonsAndMotifs();alphaAndBounds();
        System.out.println("PASS physical-mm scaling, 3 sampling strategies, transparency, bounds, no phantom cuts");
    }
}
