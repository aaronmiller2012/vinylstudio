package com.halftonestudio;

/** Immutable presentation settings captured for one specialty preview render. */
final class SpecialtyPreviewSettings {
    final int paper,tonalSelection;
    final float width,stroke;
    final boolean collisions,solution,cutLayer;
    SpecialtyPreviewSettings(int paper,float width,float stroke,boolean collisions,
                             boolean solution,boolean cutLayer,int tonalSelection){
        this.paper=paper;this.width=width;this.stroke=stroke;this.collisions=collisions;
        this.solution=solution;this.cutLayer=cutLayer;this.tonalSelection=tonalSelection;
    }
}
