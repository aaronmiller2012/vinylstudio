package android.graphics;
public final class Bitmap {
    private final int w,h;private final int[] pixels;
    public Bitmap(int width,int height,int[] data){w=width;h=height;pixels=data.clone();}
    public int getWidth(){return w;}
    public int getHeight(){return h;}
    public boolean isRecycled(){return false;}
    public void getPixels(int[] dst,int offset,int stride,int x,int y,int width,int height){
        for(int iy=0;iy<height;iy++)System.arraycopy(pixels,(iy+y)*w+x,dst,offset+iy*stride,width);
    }
}
