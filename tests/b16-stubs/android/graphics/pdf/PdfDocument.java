package android.graphics.pdf;
import android.graphics.Canvas;
import java.io.IOException;
import java.io.OutputStream;
public class PdfDocument {
 private int count=0;public static final class PageInfo{
  public static final class Builder {public Builder(int w,int h,int index){}public PageInfo create(){return new PageInfo();}}
 }
 public static final class Page{public Canvas getCanvas(){return new Canvas();}}
 public Page startPage(PageInfo p){count++;return new Page();}
 public void finishPage(Page p){}
 public void writeTo(OutputStream out)throws IOException{out.write(count);}
 public void close(){}
}
