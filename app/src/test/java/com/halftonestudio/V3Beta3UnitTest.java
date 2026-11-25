package com.halftonestudio;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;

/** Real Gradle JUnit checks for the Beta 3 safety and transaction contracts. */
public final class V3Beta3UnitTest {
    @Test public void denseSquareSameClassificationAsFourCorners() {
        for(int steps:new int[]{1,4,16,200,500}){
            float[] pts=new float[8*steps];int ix=0;
            int[][] corners={{0,0},{100,0},{100,100},{0,100}};
            for(int i=0;i<4;i++)for(int j=0;j<steps;j++){
                double t=j/(double)steps;
                pts[ix++]=(float)(corners[i][0]*(1-t)+corners[(i+1)%4][0]*t);
                pts[ix++]=(float)(corners[i][1]*(1-t)+corners[(i+1)%4][1]*t);
            }
            StyleKit.Result result=new StyleKit.Result(100,100);
            StyleKit.Layer layer=new StyleKit.Layer("square",0);layer.loops.add(pts);result.layers.add(layer);
            assertEquals("Subdivided square incorrectly narrowed",0,
                StyleCutGeometryPreflight.inspect(result,.30f,500).size());
        }
    }
    @Test public void denseCircleIsNotANarrowCut(){
        for(int vertices:new int[]{32,256,1000,2000}){
            float[] points=new float[vertices*2];
            for(int i=0;i<vertices;i++){
                double theta=2*Math.PI*i/vertices;
                points[2*i]=(float)(50+25*Math.cos(theta));
                points[2*i+1]=(float)(50+25*Math.sin(theta));
            }
            StyleKit.Result result=new StyleKit.Result(100,100);
            StyleKit.Layer layer=new StyleKit.Layer("circle",0);
            layer.loops.add(points);result.layers.add(layer);
            assertEquals("Dense smooth circle incorrectly flagged",0,
                StyleCutGeometryPreflight.inspect(result,.30f,500).size());
        }
    }
    @Test public void standardOverlappingCutsAreReported(){
        VinylEngine.Settings s=new VinylEngine.Settings();s.registration=false;
        s.layers.add(new VinylEngine.Layer("Black",0xff000000));
        VinylEngine.Result r=new VinylEngine.Result(s,50f,0f,new float[0][]);
        r.shapes.add(new ArrayList<>(Arrays.asList(
            VinylEngine.Shape.polygon(1,1,12,1,12,12,1,12),
            VinylEngine.Shape.polygon(7,7,18,7,18,18,7,18))));
        assertTrue(CutSafetyReviewPolicy.assess(StandardCutSafety.inspect(r,.30f,100)).critical>0);
    }
    @Test public void photoPointerKeepsPreviousPairAfterFailedWrite()throws IOException{
        File dir=Files.createTempDirectory("vinyl-unit-pair-").toFile();
        try{
            PhotoRevisionStore.commit(dir,(p,o,prior)->{
                Files.write(p.toPath(),new byte[]{1});Files.write(o.toPath(),new byte[]{2});
            });
            try{
                PhotoRevisionStore.commit(dir,(p,o,prior)->{
                    Files.write(p.toPath(),new byte[]{3});throw new IOException("injected failure");
                });
                fail("Failure was not propagated");
            }catch(IOException expected){assertEquals("injected failure",expected.getMessage());}
            PhotoRevisionStore.Pair pair=PhotoRevisionStore.active(dir);
            assertArrayEquals(new byte[]{1},Files.readAllBytes(pair.photo.toPath()));
            assertArrayEquals(new byte[]{2},Files.readAllBytes(pair.original.toPath()));
        }finally{PhotoRevisionStore.clear(dir);dir.delete();}
    }
    @Test public void projectSourceSnapshotDoesNotFollowMutableCache()throws IOException{
        File dir=Files.createTempDirectory("vinyl-unit-snapshot-").toFile();
        try{
            File encoded=new File(dir,"editing-archive-original.png");
            Files.write(encoded.toPath(),new byte[]{1,2,3});
            try(ProjectSourceSnapshot snap=ProjectSourceSnapshot.capture(dir,encoded,null)){
                Files.write(encoded.toPath(),new byte[]{4,5,6});
                assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(snap.archiveOriginal.toPath()));
            }
        }finally{for(File f:dir.listFiles()==null?new File[0]:dir.listFiles())f.delete();dir.delete();}
    }
}
