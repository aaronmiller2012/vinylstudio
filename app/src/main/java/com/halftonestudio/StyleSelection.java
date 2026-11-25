package com.halftonestudio;

import java.util.Objects;

/**
 * M3: the ONE artwork style the editor has selected.
 *
 * Historically StudioActivity kept nine independent fields (continuousStyle,
 * tonalStencilMode, mazePortraitMode, tessellationMode, layeredCellMode, offsetCellMode,
 * bottleCapMode, fishScaleMode, overprintCellKind) plus newStyleId, and every picker
 * cleared a different subset of them. This immutable value makes contradictory
 * combinations unrepresentable; StudioActivity derives all of those fields from it in a
 * single place. Pure Java: no Android types (the line style is kept by enum name).
 */
final class StyleSelection {
    enum Kind { HALFTONE, CONTINUOUS_LINE, TONAL_STENCIL, MAZE, TESSELLATION, LAYERED_CELL, SUITE }
    enum Cell { PLAIN, OFFSET, BOTTLE_CAP, FISH_SCALE }

    static final StyleSelection HALFTONE=new StyleSelection(Kind.HALFTONE,null,Cell.PLAIN,0,-1);

    final Kind kind;
    /** {@code ContinuousLineModes.LineStyle} name when {@link Kind#CONTINUOUS_LINE}, else null. */
    final String lineStyle;
    /** Layered-cell variant; PLAIN unless {@link Kind#LAYERED_CELL}. */
    final Cell cell;
    /** Layered-cell overprint kind (0 = none); 0 unless {@link Kind#LAYERED_CELL}. */
    final int overprintKind;
    /** VinylStyleSuite id when {@link Kind#SUITE}, else -1. */
    final int suiteId;

    private StyleSelection(Kind kind,String lineStyle,Cell cell,int overprintKind,int suiteId){
        this.kind=kind;this.lineStyle=lineStyle;this.cell=cell;this.overprintKind=overprintKind;this.suiteId=suiteId;
    }
    static StyleSelection suite(int id){
        if(id<0)throw new IllegalArgumentException("suite id");
        return new StyleSelection(Kind.SUITE,null,Cell.PLAIN,0,id);
    }
    static StyleSelection continuous(String lineStyle){
        if(lineStyle==null||lineStyle.isEmpty())throw new IllegalArgumentException("line style");
        return new StyleSelection(Kind.CONTINUOUS_LINE,lineStyle,Cell.PLAIN,0,-1);
    }
    static StyleSelection tonalStencil(){return new StyleSelection(Kind.TONAL_STENCIL,null,Cell.PLAIN,0,-1);}
    static StyleSelection maze(){return new StyleSelection(Kind.MAZE,null,Cell.PLAIN,0,-1);}
    static StyleSelection tessellation(){return new StyleSelection(Kind.TESSELLATION,null,Cell.PLAIN,0,-1);}
    /** An overprint kind owns the cell geometry, so it always selects the PLAIN variant. */
    static StyleSelection layeredCell(Cell cell,int overprintKind){
        if(overprintKind<0)throw new IllegalArgumentException("overprint kind");
        return new StyleSelection(Kind.LAYERED_CELL,null,overprintKind>0?Cell.PLAIN:Objects.requireNonNull(cell),overprintKind,-1);
    }

    /**
     * Resolve the independent legacy flags (as saved in project JSON) into one selection,
     * with the precedence older releases applied when loading:
     * suite style > continuous line > tessellation > layered cell > tonal stencil > maze > halftone;
     * within layered cell: overprint kind > fish scale > bottle cap > offset.
     */
    static StyleSelection fromLegacyFlags(int suiteId,String lineStyle,boolean tonal,boolean maze,
            boolean tessellation,boolean layered,boolean offset,boolean bottleCap,boolean fishScale,int overprintKind){
        if(suiteId>=0)return suite(suiteId);
        if(lineStyle!=null&&!lineStyle.isEmpty())return continuous(lineStyle);
        if(tessellation)return tessellation();
        if(layered){
            Cell cell=fishScale?Cell.FISH_SCALE:bottleCap?Cell.BOTTLE_CAP:offset?Cell.OFFSET:Cell.PLAIN;
            return layeredCell(cell,Math.max(0,overprintKind));
        }
        if(tonal)return tonalStencil();
        if(maze)return maze();
        return HALFTONE;
    }

    /** Retired overprint kinds (Painterly Cell Weave, and anything out of range) are never
     * reinterpreted as another style: such projects load as normal photo artwork. */
    static boolean isRetiredOverprint(int kind){
        return kind==LayeredCellPortraitEngine.OVERPRINT_PAINTERLY||
            kind<LayeredCellPortraitEngine.OVERPRINT_NONE||kind>LayeredCellPortraitEngine.OVERPRINT_MIXED;
    }
    boolean is(Kind k){return kind==k;}
    boolean offsetCell(){return kind==Kind.LAYERED_CELL&&cell==Cell.OFFSET;}
    boolean bottleCap(){return kind==Kind.LAYERED_CELL&&cell==Cell.BOTTLE_CAP;}
    boolean fishScale(){return kind==Kind.LAYERED_CELL&&cell==Cell.FISH_SCALE;}

    @Override public boolean equals(Object o){
        if(!(o instanceof StyleSelection))return false;
        StyleSelection s=(StyleSelection)o;
        return kind==s.kind&&Objects.equals(lineStyle,s.lineStyle)&&cell==s.cell&&overprintKind==s.overprintKind&&suiteId==s.suiteId;
    }
    @Override public int hashCode(){return Objects.hash(kind,lineStyle,cell,overprintKind,suiteId);}
    @Override public String toString(){
        return kind+(lineStyle!=null?"("+lineStyle+")":"")+(kind==Kind.LAYERED_CELL?"("+cell+","+overprintKind+")":"")+(suiteId>=0?"#"+suiteId:"");
    }
}
