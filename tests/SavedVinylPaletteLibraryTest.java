import java.util.*;
import com.halftonestudio.SavedVinylPaletteLibrary;
public class SavedVinylPaletteLibraryTest {
 static void check(boolean p,String reason){if(!p)throw new AssertionError(reason);}
 static SavedVinylPaletteLibrary.Entry entry(String id,String name,int color,int hue){
   int[] c=new int[8];Arrays.fill(c,color);
   return new SavedVinylPaletteLibrary.Entry(id,name,c,hue);
 }
 public static void main(String[] args){
   ArrayList<SavedVinylPaletteLibrary.Entry> items=new ArrayList<>();
   items=SavedVinylPaletteLibrary.saveFirst(items,entry("1","Ocean",0xff203040,-25));
   items=SavedVinylPaletteLibrary.saveFirst(items,entry("2","Pink",0xffef1133,90));
   check(items.size()==2 && items.get(0).name.equals("Pink"),"Newest first");
   // The model copies the source array, so mutating the source after saving never mutates saved state.
   int[] src=new int[8];Arrays.fill(src,0xff114477);
   SavedVinylPaletteLibrary.Entry e=new SavedVinylPaletteLibrary.Entry("3","Green",src,-180);
   src[0]=0;
   check(e.baseColors[0]==0xff114477,"Palette copy on creation");
   int[] accents={0xff100000,0xff200000,0xff300000,0xff400000};
   SavedVinylPaletteLibrary.Entry cm=new SavedVinylPaletteLibrary.Entry("cmyk","Ink",src,60,true,0xff010203,accents);
   accents[0]=0;
   check(cm.cmykSource && cm.cmykBlackBase==0xff010203 && cm.cmykAccentBases[0]==0xff100000,
         "CMYK accent and black identity retention");
   items=SavedVinylPaletteLibrary.saveFirst(items,entry("1","Ocean revised",0xff334455,-10));
   check(items.size()==2 && items.get(0).name.equals("Ocean revised") && items.get(0).hueDegrees==-10,"Overwrite moves first with hue");
   check(SavedVinylPaletteLibrary.findName(items,"  ocean REVISED ")!=null,"Case insensitive lookup");
   try{SavedVinylPaletteLibrary.saveFirst(items,entry("4","PINK",0xff223344,50));throw new AssertionError("Duplicate name accepted");}
   catch(IllegalArgumentException expected){}
   items=SavedVinylPaletteLibrary.remove(items,"1");
   check(items.size()==1 && items.get(0).name.equals("Pink"),"Delete only chosen palette");
   try{SavedVinylPaletteLibrary.checkedName("  ");throw new AssertionError("Empty name accepted");}
   catch(IllegalArgumentException expected){}
   try{entry("e","E",0xff111111,181);throw new AssertionError("Hue accepted out of range");}
   catch(IllegalArgumentException expected){}
   items.clear();
   for(int i=0;i<SavedVinylPaletteLibrary.MAX_ENTRIES;i++)
      items=SavedVinylPaletteLibrary.saveFirst(items,entry("id"+i,"Palette "+i,0xff111111,0));
   check(items.size()==SavedVinylPaletteLibrary.MAX_ENTRIES,"Cap at 50");
   try{SavedVinylPaletteLibrary.saveFirst(items,entry("excess","Full",0xff000000,0));throw new AssertionError("Accepted over cap");}
   catch(IllegalArgumentException expected){}
   items=SavedVinylPaletteLibrary.remove(items,"id1");
   items=SavedVinylPaletteLibrary.saveFirst(items,entry("excess","Full",0xff000000,0));
   check(items.size()==SavedVinylPaletteLibrary.MAX_ENTRIES&&items.get(0).name.equals("Full"),"Delete frees capacity");
   System.out.println("PASS named palette library: order, uniqueness, rename, hue, delete, migration capacity");
 }
}
