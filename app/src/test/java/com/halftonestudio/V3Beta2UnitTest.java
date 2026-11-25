package com.halftonestudio;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import java.util.Collections;

/** Gradle-run JUnit: mirrors release-critical Beta 2 host contracts. */
public final class V3Beta2UnitTest {
    @Test public void duplicateImageRolesRejectedRegardlessOfExtension() throws Exception {
        ProjectArchiveReader.EntryPolicy roles=new ProjectArchiveReader.EntryPolicy();
        roles.accept("photo.png");
        try{roles.accept("photo.jpg");fail("duplicate photo role accepted");}
        catch(IOException expected){assertTrue(expected.getMessage().contains("Multiple photo"));}
        ProjectArchiveReader.EntryPolicy originals=new ProjectArchiveReader.EntryPolicy();
        originals.accept("original.jpg");
        try{originals.accept("original.png");fail("duplicate original role accepted");}
        catch(IOException expected){assertTrue(expected.getMessage().contains("Multiple original"));}
    }
    private static float[] rotated(double degrees,float width){
        float[] poly=new float[8];
        double c=Math.cos(Math.toRadians(degrees)),s=Math.sin(Math.toRadians(degrees));
        double[] x={0,40,40,0},y={0,0,width,width};
        for(int i=0;i<4;i++){
            poly[2*i]=(float)(50+x[i]*c-y[i]*s);
            poly[2*i+1]=(float)(50+x[i]*s+y[i]*c);
        }
        return poly;
    }
    private static int warnings(double degrees,float width){
        StyleKit.Result r=new StyleKit.Result(130,130);
        StyleKit.Layer l=new StyleKit.Layer("L1",0xff000000);
        l.loops.add(rotated(degrees,width));r.layers.add(l);
        return StyleCutGeometryPreflight.inspect(r,.30f,10).size();
    }
    @Test public void rotatedVinylSliversDetected() {
        for(int angle=0;angle<=90;angle+=5){
            assertTrue("missed angle "+angle,warnings(angle,.10f)>0);
            assertEquals("false positive "+angle,0,warnings(angle,.30f));
        }
    }
    @Test public void unsafeAndUnverifiedCutsRequireExplicitReview(){
        CutSafetyReviewPolicy.Decision crossing=CutSafetyReviewPolicy.assess(
            Collections.singletonList(new StyleCutGeometryPreflight.Warning(0,0,0,
                "Intersecting cut contours")));
        assertTrue(crossing.dangerous());
        CutSafetyReviewPolicy.Decision unknown=CutSafetyReviewPolicy.assess(
            Collections.singletonList(new StyleCutGeometryPreflight.Warning(0,0,0,
                "Detailed local-width scan exceeded complexity budget; cut safety is not verified")));
        assertTrue(unknown.dangerous());
        CutSafetyReviewPolicy.Decision narrow=CutSafetyReviewPolicy.assess(
            Collections.singletonList(new StyleCutGeometryPreflight.Warning(0,0,0,
                "Check narrow or tiny vinyl contour")));
        assertTrue(narrow.requiresConfirmation());
        assertFalse(narrow.dangerous());
    }
}
