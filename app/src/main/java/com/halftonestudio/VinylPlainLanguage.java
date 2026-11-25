package com.halftonestudio;

import java.util.Locale;
/** Visible labels and tap-to-read help. No change to persisted parameter keys. */
public final class VinylPlainLanguage {
    private VinylPlainLanguage(){}
    public static String label(String original){
        if(original==null)return "";
        String out=original.replace("Priority knockout", "Prevent color overlap")
            .replace("Stacked overprint", "Stack colors")
            .replace("Nearest swatch", "Closest vinyl color")
            .replace("Registration marks", "Alignment marks")
            .replace("Trap margin", "Overlap margin")
            .replace("Stipple same-sheet clearance", "Space between cuts")
            .replace("Minimum dot diameter", "Smallest dot")
            .replace("Minimum cut width", "Smallest cut width")
            .replace("Minimum feature", "Smallest cut size")
            .replace("Layer density", "Detail on this layer")
            .replace("Adaptive coverage", "Fill empty areas")
            .replace("Physical stacking order", "Layer stacking order")
            .replace("Edge-aware detail", "Keep sharp edges")
            .replace("Stipple cut shapes", "Dot shapes");
        return out;
    }
    public static String help(String label){
        if(label==null)return null;
        String s=label.toLowerCase(Locale.ROOT);
        if(s.contains("priority knockout")||s.contains("prevent color overlap"))
            return "Removes areas hidden by a higher vinyl color so they are not cut twice.";
        if(s.contains("same-sheet")||s.contains("space between cuts")||s.contains("weed gap")||s.contains("vinyl gap"))
            return "Minimum distance between cuts on one sheet of vinyl. Different color layers may still overlap.";
        if(s.contains("overprint")||s.contains("stack colors"))
            return "Places different vinyl colors on top of one another during assembly.";
        if(s.contains("nearest swatch")||s.contains("closest vinyl color"))
            return "Assigns photo areas to the closest color in your selected vinyl palette.";
        if(s.contains("adaptive coverage")||s.contains("fill empty areas")||s.contains("fill · target"))
            return "Adds cuttable shapes to eligible empty areas; physical spacing and masks still apply.";
        if(s.contains("minimum feature")||s.contains("smallest cut")||s.contains("minimum dot")||s.contains("smallest dot"))
            return "Keeps individual vinyl details from becoming too narrow for the selected material settings.";
        if(s.contains("registration mark")||s.contains("alignment mark"))
            return "Matching reference shapes help line up all vinyl layers during assembly.";
        if(s.contains("layer density")||s.contains("point density")||s.contains("detail on this layer")||s.contains("detail ·"))
            return "Controls the number of shapes, not their individual physical size.";
        if(s.contains("dot size")||s.contains("shape size"))
            return "Changes the size of each cut shape without requesting more shapes.";
        if(s.contains("trap")||s.contains("overlap margin")||s.contains("color seam"))
            return "Adds a small overlap to prevent gaps when different colored layers are assembled.";
        if(s.contains("edge preservation")||s.contains("edge definition")||s.contains("edge-aware")||s.contains("keep sharp edges"))
            return "Keeps important boundaries from being lost when the photo is simplified.";
        if(s.contains("tone")||s.contains("gamma")||s.contains("contrast"))
            return "Changes how strongly light and dark parts of the photo appear in the artwork.";
        if(s.contains("spacing")||s.contains("screen"))
            return "Controls distance between repeating dots or lines. More space generally means easier cutting.";
        return null;
    }
}
