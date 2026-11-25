import com.halftonestudio.NewStyleColorSlots;
import com.halftonestudio.VinylStyleSuite;
/** Host test of the engine's real color slot contract, not the UI's saved detail count. */
public final class PhysicalSlotSafetyRC78Test {
    public static void main(String[] args) {
        for(int style=0;style<VinylStyleSuite.NAMES.length;style++) {
            for(int variant=0;variant<4;variant++) {
                for(int chosen=2;chosen<=8;chosen++) {
                    int slots=NewStyleColorSlots.slotCount(style,variant,chosen);
                    if(slots<1||slots>8)throw new AssertionError("invalid slots: "+style+" / "+variant+" / "+chosen);
                    boolean banknoteTwoInk=style==VinylStyleSuite.BANKNOTE&&
                        (variant==com.halftonestudio.BanknoteEngravingEngine.CROSS_ETCH||
                         variant==com.halftonestudio.BanknoteEngravingEngine.TWO_INK);
                    if(!NewStyleColorSlots.multi(style)&&style!=VinylStyleSuite.LINOCUT&&!banknoteTwoInk&&slots!=1)
                        throw new AssertionError("single-color style must have one physical slot");
                    if(style==VinylStyleSuite.LINOCUT&&slots!=(variant==0?1:2))
                        throw new AssertionError("linocut cross variant physical slot count");
                    if(style==VinylStyleSuite.GLASS&&slots<3)
                        throw new AssertionError("stained glass lead + >=2 glass colors");
                }
            }
        }
        System.out.println("PASS RC78 physical slot ranges for every style, variant and chosen count");
    }
}
