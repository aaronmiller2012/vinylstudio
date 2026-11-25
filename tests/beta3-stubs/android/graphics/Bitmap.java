package android.graphics;
import java.io.*;
public class Bitmap {
 public enum CompressFormat {PNG}
 public final String value; public final boolean fail;
 public Bitmap(String s,boolean fail){this.value=s;this.fail=fail;}
 public boolean compress(CompressFormat f,int quality,OutputStream out){
   if(fail) return false;
   try{out.write(value.getBytes("UTF-8"));return true;}catch(IOException e){return false;}
 }
}
