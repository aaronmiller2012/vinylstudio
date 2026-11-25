package android.graphics;
import java.io.OutputStream;
public final class Bitmap {
    public enum Config { ARGB_8888 }
    public enum CompressFormat { PNG }
    public final int identity;
    public int encoded;
    public Bitmap(int i){identity=i;}
    public static Bitmap createBitmap(int[] values,int w,int h,Config config){if(w!=2||h!=2)throw new AssertionError();return new Bitmap(0);}
    public boolean compress(CompressFormat format,int quality,OutputStream os){
        if(format!=CompressFormat.PNG)throw new AssertionError("Lossy photo save");
        try{os.write(new byte[]{(byte)identity});encoded++;return true;}
        catch(java.io.IOException e){return false;}
    }
}
