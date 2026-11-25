package com.halftonestudio;

/**
 * D15: an uncommitted color shown while a color dialog is open.
 *
 * The preview lives here, never in {@code VinylEngine.Settings}. Autosave, project save,
 * undo snapshots and process death therefore can only ever see committed colors; a
 * dialog that is dismissed by recreation (without Cancel) leaves nothing behind.
 * UI-thread only.
 */
final class LiveColorPreview {
    private int layer=-1;
    private int color;

    /** Start previewing {@code layer}; any previous preview is discarded. */
    void begin(int layer,int color){
        if(layer<0)throw new IllegalArgumentException("layer");
        this.layer=layer;this.color=color;
    }
    /** Change the previewed color; ignored when no preview is active. */
    void update(int color){if(layer>=0)this.color=color;}
    /** Stop previewing. Safe to call more than once (Apply, Cancel and dismiss all call it). */
    void end(){layer=-1;}
    boolean active(){return layer>=0;}
    /** The color to DRAW for {@code index}; {@code committed} is the saved layer color. */
    int colorFor(int index,int committed){return index==layer&&layer>=0?color:committed;}
}
