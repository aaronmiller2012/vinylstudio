package com.halftonestudio;

import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Reproduce the fresh-photo UI pipeline, including the default 5 mm inset. */
public final class MazeInputPolicyTest {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    static boolean[] mask(int[] p,int w,int h,float width,float margin){return mask(p,w,h,width,margin,false);}
    static boolean[] mask(int[] p,int w,int h,float width,float margin,boolean maze){
        boolean[] allowed=new boolean[w*h];float height=width*h/w;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float mx=(x+.5f)*width/w,my=(y+.5f)*height/h;
            allowed[y*w+x]=(maze?MazeInputPolicy.allowsPixel(true,p[y*w+x]):(p[y*w+x]>>>24)>=128)&&mx>=margin&&my>=margin&&mx<=width-margin&&my<=height-margin;
        }
        return allowed;
    }
    static void rejects(Runnable run){try{run.run();throw new AssertionError("Accepted nonrectangular input");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args)throws Exception{
        int w=160,h=192;int[] p=new int[w*h];Arrays.fill(p,0xff888888);
        float savedInset=5f,width=152.4f;
        MazeInputPolicy.requireRectangle(false,false);
        check(!mask(p,w,h,width,savedInset)[0],"Fixture must reproduce the old default inset failure");
        check(MazeInputPolicy.effectiveInsetMm(false,savedInset)==savedInset,"Other styles must retain inset");
        float effective=MazeInputPolicy.effectiveInsetMm(true,savedInset);
        check(effective==0f&&savedInset==5f,"Maze inset override must not edit saved setting");
        for(PerfectPortraitMaze.Mode mode:PerfectPortraitMaze.Mode.values()){
            PerfectPortraitMaze.Options o=new PerfectPortraitMaze.Options();o.widthMm=width;o.mode=mode;
            PerfectPortraitMaze.Result r=PerfectPortraitMaze.generate(p,w,h,mask(p,w,h,width,effective),o);
            check(r.solutionCells().length>0,"Missing route "+mode);
        }
        rejects(()->MazeInputPolicy.requireRectangle(true,false));
        rejects(()->MazeInputPolicy.requireRectangle(false,true));
        // M8: transparency inside an uncut rectangle is light tone, not a crop (PNG with transparent
        // areas, background-removed photo). A real Snip/circle mask still fails closed.
        for(int y=0;y<40;y++)for(int x=0;x<40;x++)p[y*w+x]=0x00000000;
        PerfectPortraitMaze.Options o=new PerfectPortraitMaze.Options();o.widthMm=width;
        check(MazeInputPolicy.allowsPixel(true,0)&&!MazeInputPolicy.allowsPixel(false,0)&&MazeInputPolicy.allowsPixel(false,0xff000000),
            "maze ignores alpha; other styles keep alpha as the footprint");
        for(PerfectPortraitMaze.Mode mode:PerfectPortraitMaze.Mode.values()){
            PerfectPortraitMaze.Options om=new PerfectPortraitMaze.Options();om.widthMm=width;om.mode=mode;
            PerfectPortraitMaze.Result r=PerfectPortraitMaze.generate(p,w,h,mask(p,w,h,width,effective,true),om);
            check(r.solutionCells().length>0,"transparent-corner photo: missing route "+mode);
        }
        boolean[] snipped=mask(p,w,h,width,effective,true);snipped[w*h/2]=false;
        rejects(()->PerfectPortraitMaze.generate(p,w,h,snipped,o));
        String activity=new String(Files.readAllBytes(Paths.get("app/src/main/java/com/halftonestudio/StudioActivity.java")),"UTF-8");
        check(activity.contains("MazeInputPolicy.effectiveInsetMm(portraitMaze,adjustments.marginMm)"),"UI not using inset override");
        check(activity.contains("MazeInputPolicy.requireRectangle(circular,snips!=null&&!snips.isEmpty())"),"UI not checking intentional Snips");
        check(activity.contains("MazeInputPolicy.allowsPixel(portraitMaze,rgb[i])"),"UI mask not using maze pixel policy");
        System.out.println("PASS fresh opaque photo with 5mm default inset, eight maze modes, transparent-corner photo accepted (eight modes), true crop rejection, other styles unchanged, UI wiring");
    }
}
