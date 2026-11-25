package android.graphics;
/** Test-only contour fixtures: native Android geometry is qualified on device. */
public final class Path {
 public final float[] lengths; public final boolean[] closed;
 public Path(float[] lengths,boolean[] closed){this.lengths=lengths;this.closed=closed;}
 public boolean isEmpty(){return lengths.length==0;}
}
