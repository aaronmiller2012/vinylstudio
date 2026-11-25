package com.halftonestudio;

/** D15: the live color-dialog preview must never be stored as a committed color. */
public final class V3M3LiveColorPreviewTest {
    private static void yes(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    public static void main(String[] args){
        LiveColorPreview preview=new LiveColorPreview();
        int committed=0xff112233,dragged=0xffaa0000,other=0xff00aa00;
        yes(!preview.active()&&preview.colorFor(2,committed)==committed,"idle preview must draw committed colors");
        preview.begin(2,committed);
        preview.update(dragged);
        yes(preview.colorFor(2,committed)==dragged,"dragged color not shown on the edited layer");
        yes(preview.colorFor(1,other)==other&&preview.colorFor(3,other)==other,"preview leaked onto another layer");
        // Cancel, Apply and dismiss-by-recreation all end the preview; repeated end is harmless.
        preview.end();preview.end();
        yes(!preview.active()&&preview.colorFor(2,committed)==committed,"ended preview still drawn");
        preview.update(dragged);
        yes(preview.colorFor(2,committed)==committed,"late wheel callback revived an ended preview");
        preview.begin(4,other);preview.begin(0,committed);
        yes(preview.colorFor(4,other)==other,"a new dialog must discard the previous preview");
        try{preview.begin(-1,committed);throw new AssertionError("negative layer accepted");}
        catch(IllegalArgumentException expected){}
        System.out.println("PASS D15 live color preview is draw-only and never committed");
    }
}
