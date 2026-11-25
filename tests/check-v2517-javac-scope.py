"""Typecheck the actual restored palette block with javac, even without Android SDK.
This small stub is NOT a substitute for compiling the complete Android activity.
"""
from pathlib import Path
import subprocess, tempfile
root=Path(__file__).resolve().parents[1]
src=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
start=src.index('        JSONArray stdBase=json.optJSONArray("standardHueBaseColors");')
end=src.index('        tonalLayerCount=',start)
block=src[start:end]
java='''import java.util.*;
public class PaletteRestoreScopeProbe {
    static final class JSONArray {
      final int[] values; JSONArray(int... v){values=v;}
      int length(){return values.length;}
      int optInt(int i,int def){return i>=0 && i<values.length?values[i]:def;}
    }
    static final class JSONObject {
      final Map<String,JSONArray> arrays=new HashMap<>();
      final Map<String,Integer> ints=new HashMap<>();
      JSONArray optJSONArray(String k){return arrays.get(k);}
      int optInt(String k,int def){return ints.getOrDefault(k,def);}
    }
    static final class PaletteHueShift {
      static int clampDegrees(int n){return Math.max(-180,Math.min(180,n));}
    }
    static final class VinylEngine {
      static final int CMYK=2;
      static final class Layer {final int color;Layer(int c){color=c;}}
      static final class Settings {int mode;ArrayList<Layer> layers=new ArrayList<>();}
    }
    final VinylEngine.Settings settings=new VinylEngine.Settings();
    final int[] standardHueBaseColors=new int[8];
    final int[] cmykAccentHueBaseColors=new int[4];
    int standardHueDegrees,tonalHueDegrees;
    void applyLoadedColorState(JSONObject json){
''' + block + '''
    }
    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args){
      PaletteRestoreScopeProbe p=new PaletteRestoreScopeProbe();
      p.settings.mode=VinylEngine.CMYK;
      for(int i=0;i<6;i++)p.settings.layers.add(new VinylEngine.Layer(0xff000000+i));
      p.cmykAccentHueBaseColors[2]=0xff123456;
      p.applyLoadedColorState(new JSONObject());
      check(p.standardHueBaseColors[0]==0xff000000 && p.standardHueBaseColors[5]==0xff000005,"legacy RGB");
      check(p.cmykAccentHueBaseColors[0]==0xff000003 && p.cmykAccentHueBaseColors[1]==0xff000004,"legacy CMYK accents");
      check(p.cmykAccentHueBaseColors[2]==0,"stale accent leakage");
      JSONObject saved=new JSONObject();
      int[] base=new int[8],accents={111,222,333,444};
      for(int i=0;i<8;i++)base[i]=0xff004400+i;
      saved.arrays.put("standardHueBaseColors",new JSONArray(base));
      saved.arrays.put("cmykAccentHueBaseColors",new JSONArray(accents));
      saved.ints.put("standardHueDegrees",75);
      p.applyLoadedColorState(saved);
      check(Arrays.equals(p.standardHueBaseColors,base),"saved colors");
      check(Arrays.equals(p.cmykAccentHueBaseColors,accents),"saved accents");
      check(p.standardHueDegrees==75,"saved hue");
      check(p.settings.layers.get(0).color==0xff000000,"loaded final RGB untouched");
      System.out.println("PASS javac palette restoration: loaded RGB, legacy accents, stored hue");
    }
}
'''
with tempfile.TemporaryDirectory(prefix='vinyl-palette-scope-') as d:
 p=Path(d)/'PaletteRestoreScopeProbe.java';p.write_text(java)
 subprocess.run(['javac','-d',d,str(p)],check=True)
 subprocess.run(['java','-cp',d,'PaletteRestoreScopeProbe'],check=True)
