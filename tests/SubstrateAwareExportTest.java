import com.halftonestudio.*;
public class SubstrateAwareExportTest {
 public static void main(String[] args){
  VinylEngine.Settings s=VinylEngine.Settings.defaults();s.paper=0xff131313;s.substrateAware=true;
  VinylEngine.Result r=VinylEngine.generate(new int[]{0xff151515,0xffdd00dd,0xffaabbcc,0xff556677},2,2,s);
  if(!VinylEngine.cutLayer(r,3))throw new AssertionError("Mounting-surface similarity must not silently omit black vinyl");
  r.settings.layers.get(3).excludeFromExport=true;
  if(VinylEngine.cutLayer(r,3))throw new AssertionError("Explicitly skipped sheet still exported");
  if(!VinylEngine.cutLayer(r,1))throw new AssertionError("Other swatches should remain intact");
  System.out.println("Manual sheet inclusion and substrate independence PASS");
 }
}
