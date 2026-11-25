package android.graphics;
public final class PathMeasure {
 private final Path p;private int i;
 public PathMeasure(Path p,boolean forceClosed){if(forceClosed)throw new AssertionError("Unexpected forced closure");this.p=p;}
 public float getLength(){return p.lengths[i];}
 public boolean isClosed(){return p.closed[i];}
 public boolean nextContour(){return ++i<p.lengths.length;}
}
