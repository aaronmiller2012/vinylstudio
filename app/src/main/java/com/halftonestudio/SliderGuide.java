package com.halftonestudio;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Detailed, per-slider explanations shown when the user taps a slider's ⓘ button.
 *
 * <p>Every adjustment slider gets its own entry: what it controls, what happens when it
 * is moved right (higher) and left (lower), how it affects cutting and weeding, and a
 * practical tip. Short hover tooltips stay in {@link VinylPlainLanguage#help(String)};
 * this class is only for the tap-to-read dialog, so it may be longer.
 *
 * <p>Lookup is by the slider's internal label with the step number ("2 · ") and unit
 * suffix (" · %", " · mm", "(stops)") removed, so the same entry serves every style that
 * reuses a control. Labels whose meaning depends on the unit (Line width in % vs mm) are
 * keyed with the unit. Unknown labels return null and the caller falls back to the
 * generic help. Pure Java: no Android types, host-testable.
 */
final class SliderGuide {
    static final class Entry {
        final String what, right, left, cutting, tip;
        Entry(String what,String right,String left,String cutting,String tip){
            this.what=what;this.right=right;this.left=left;this.cutting=cutting;this.tip=tip;
        }
    }

    private static final Map<String,Entry> GUIDE=new HashMap<>();
    private SliderGuide(){}

    private static void add(String what,String right,String left,String cutting,String tip,String... keys){
        Entry e=new Entry(what,right,left,cutting,tip);
        for(String k:keys){
            if(GUIDE.put(k,e)!=null)throw new IllegalStateException("Duplicate slider guide key: "+k);
        }
    }

    /** Normalized lookup key, e.g. "2 · Shadow emphasis · %" → "shadow emphasis". */
    static String key(String label){
        if(label==null)return "";
        String k=label.trim().toLowerCase(Locale.ROOT);
        k=k.replaceFirst("^\\d+ · ","");
        k=k.replaceFirst("^level of detail · .*$","level of detail");
        k=k.replace(" · mm (cut-safe)","").replace(" · gamma (0=shared)","");
        k=k.replaceFirst(" · (%|mm|°|px|degrees)$","");
        k=k.replaceFirst(" \\((mm|stops|gamma)\\)$","");
        k=k.replace(" %","");
        return k.trim();
    }
    private static String unit(String label){
        String l=label==null?"":label.toLowerCase(Locale.ROOT);
        if(l.endsWith("· mm")||l.contains("(mm)"))return "mm";
        if(l.endsWith("· %"))return "%";
        return "";
    }

    static Entry lookup(String label){
        String k=key(label);
        Entry withUnit=GUIDE.get(k+"|"+unit(label));
        return withUnit!=null?withUnit:GUIDE.get(k);
    }

    /** Formatted dialog body, or null when this slider has no dedicated entry. */
    static String explain(String label,String low,String high){
        Entry e=lookup(label);
        if(e==null)return null;
        StringBuilder out=new StringBuilder();
        out.append("WHAT IT DOES\n").append(e.what);
        out.append("\n\nSLIDE RIGHT (toward ").append(high).append(")\n").append(e.right);
        out.append("\n\nSLIDE LEFT (toward ").append(low).append(")\n").append(e.left);
        if(e.cutting!=null)out.append("\n\nCUTTING & WEEDING\n").append(e.cutting);
        if(e.tip!=null)out.append("\n\nTIP\n").append(e.tip);
        return out.toString();
    }

    static int size(){return GUIDE.size();}
    static boolean has(String label){return lookup(label)!=null;}

    static {
        // ---------------- Shared screen / pattern geometry ----------------
        add("Distance between the centers of neighboring dots, lines or shapes in the repeating screen. It sets how coarse or fine the whole pattern is.",
            "Wider spacing: fewer, larger shapes. The pattern reads as a bold graphic up close and the photo needs more viewing distance.",
            "Tighter spacing: more, smaller shapes and finer photo detail, but many more cuts.",
            "Tighter spacing multiplies the number of cut paths and makes weeding much slower; very small spacing can exceed Cricut's 5,000-shape upload limit.",
            "Start around 0.15 in for an 11 in wide piece. Go tighter only for large pieces viewed close up.",
            "spacing","line / dot spacing");
        add("Base spacing of the color regions. Smaller values divide the photo into finer regions.",
            "Larger regions: simpler shapes, fewer pieces, flatter look.",
            "Smaller regions: more photographic detail and more pieces per color.",
            "Finer regions produce more small islands to weed and more chances of slivers below the minimum cut width.",
            "Lower it gradually; stop as soon as faces and edges read clearly.",
            "region spacing · smaller = finer");
        add("Angle of the line or dot grid for this color layer, in degrees.",
            "Rotates the screen counter-clockwise.",
            "Rotates the screen clockwise.",
            "Angle does not change cut count much, but lines at 0° and 90° can align with the vinyl grain and look stiffer.",
            "Keep each color at least 15–30° apart from the others to avoid moiré (wavy interference patterns) when layers stack.",
            "screen angle");
        add("How much of each cell this layer's shapes cover. Acts like ink coverage for this one color.",
            "More coverage: bigger shapes, darker and more solid color.",
            "Less coverage: smaller shapes, lighter color, more background showing.",
            "High coverage can make neighboring shapes touch or leave hair-thin bridges; low coverage can create dots too small to cut.",
            "Adjust coverage per layer to balance a color that looks too strong or too faint.",
            "coverage");
        add("Overall percentage of each cell that can be filled by dots.",
            "Denser dots: darker tones and a more solid image.",
            "Sparser dots: lighter, airier image with more background.",
            "At the top of the range dots can merge or leave very thin gaps; the cut-safety check flags these.",
            "Lower it if shadows turn into solid blobs.",
            "dot coverage");
        add("Size of each cut shape relative to its cell.",
            "Bigger shapes: bolder pattern and more color.",
            "Smaller shapes: more space between shapes and a lighter, more delicate look.",
            "Larger shapes leave smaller weed gaps; smaller shapes may approach the minimum cut size.",
            "Keep below about 85% if the shapes must not touch.",
            "shape size","bead size");
        add("Width of each line relative to its spacing for this layer.",
            "Thicker lines: darker, bolder layer.",
            "Thinner lines: lighter layer with more background between lines.",
            "Thin lines can drop below the minimum cut width and tear while weeding; very thick lines leave narrow gaps.",
            "Use it to rebalance one color without changing the others.",
            "line width|%");
        add("Widest a line is allowed to grow in the darkest parts of the photo, as a share of the line spacing.",
            "Darker shadows: lines swell more and shadows get bolder.",
            "Lighter shadows: lines stay slimmer and the gaps between them stay wider.",
            "Higher values shrink the gap between neighboring lines; keep the gap above your minimum weed gap.",
            "Around 70–80% gives strong shadows without lines merging.",
            "maximum line width","line weight in shadows");
        add("How thickly lines are drawn in shadow regions of the photo.",
            "More shadow density: dark areas fill in more strongly.",
            "Less shadow density: shadows stay open and lighter.",
            "Dense shadows create closely packed lines that are harder to weed.",
            null,
            "shadow density");

        // ---------------- Tone and contrast ----------------
        add("Contrast of the photo before it becomes vinyl shapes.",
            "More contrast: darks get darker and lights lighter; bolder, more graphic result.",
            "Less contrast: flatter, softer result with more mid-tones.",
            "More contrast pushes shapes to their size extremes (very small and very large), which can create tiny dots and narrow gaps.",
            "1.0 is neutral. Raise it for dull or hazy photos.",
            "contrast","photo contrast","shape contrast");
        add("Brightens or darkens the whole photo in photographic stops (each stop doubles or halves the light).",
            "Brighter: lighter image, less vinyl in the light areas.",
            "Darker: heavier image, more vinyl overall.",
            null,
            "Fix exposure first, then contrast, then the other tone sliders.",
            "exposure");
        add("Lifts or deepens just the dark parts of the photo.",
            "Opens up shadows so detail in dark areas becomes visible.",
            "Deepens shadows for a moodier, heavier look.",
            null,
            "Use it when faces in shade turn into solid dark patches.",
            "shadows");
        add("Recovers or compresses detail in the brightest parts of the photo.",
            "Brings back texture in bright areas such as skin highlights or sky.",
            "Lets bright areas wash out to plain background.",
            null,
            null,
            "highlight detail");
        add("Pixel brightness (0–255) that becomes pure black. Everything darker is treated as fully black.",
            "More of the image counts as black: deeper shadows and more contrast.",
            "Fewer pixels clip to black: more shadow detail.",
            null,
            "Raise it slightly when blacks look gray and washed out.",
            "black point");
        add("Pixel brightness (0–255) that becomes pure white. Everything brighter is treated as plain background.",
            "Fewer pixels clip to white: more highlight detail kept.",
            "More of the image becomes background: cleaner, brighter result.",
            null,
            "Lower it when the background has a gray cast you want removed.",
            "white point");
        add("Gamma curve for the middle tones, without moving black or white.",
            "Brighter mid-tones.",
            "Darker mid-tones.",
            null,
            "1.0 is neutral. Small changes (±0.2) make a big difference.",
            "midtone gamma");
        add("Gamma curve that decides how quickly shapes or lines grow as the photo gets darker.",
            "Shapes stay small through the mid-tones and only grow in deep shadows: lighter, higher-key result.",
            "Shapes grow quickly even in mid-tones: darker, heavier result.",
            null,
            "Around 1.0 follows the photo directly.",
            "tonal response","break response","bead tonal response");
        add("Per-layer gamma override for this color only. 0 uses the shared tonal response.",
            "This layer's lines stay thin until the darkest tones.",
            "This layer's lines thicken early in the mid-tones.",
            null,
            "Leave at 0 unless one color needs different tonal behavior.",
            "tone response");
        add("Shifts the split between dark and light in the posterized photo.",
            "More of the image falls into lighter tones.",
            "More of the image falls into darker tones.",
            null,
            null,
            "tone balance","tonal balance · dark / light");
        add("Amount of separation between tone levels in the curve style.",
            "Stronger difference between light and dark areas.",
            "Gentler, more even line density.",
            null,
            null,
            "tone contrast");
        add("How strongly the darkest areas get extra line work (hatching).",
            "More of the image receives shadow lines; heavier, darker artwork.",
            "Only the very darkest areas are hatched; cleaner, lighter look.",
            "More shadow lines means more cut paths in the dark areas.",
            null,
            "shadow emphasis");
        add("Brightness level that counts as shadow in the poster styles.",
            "More of the photo is treated as shadow.",
            "Only the darkest areas are treated as shadow.",
            null,
            null,
            "shadow threshold");
        add("Brightness level at which the black (key) layer begins.",
            "Black covers less of the image; only the deepest darks.",
            "Black covers more of the image.",
            null,
            null,
            "key threshold");
        add("Keeps light parts of the photo from being covered by lines or shapes.",
            "Highlights stay clean and open.",
            "Lines run through highlights too.",
            null,
            null,
            "highlight preservation");

        // ---------------- Detail and edges ----------------
        add("How much the artwork follows strong edges in the photo (eyes, lips, outlines).",
            "Edges and features stay crisp; shapes bend to follow outlines.",
            "Smoother, more uniform pattern that ignores edges.",
            "High values can create small irregular pieces along edges.",
            "Raise it for portraits where facial features get lost.",
            "edge definition","edge-aware detail","edge-following lines");
        add("How much lines bend to follow the shape of the subject instead of staying straight.",
            "Lines curve around forms like an engraving.",
            "Lines stay straight and regular.",
            null,
            null,
            "follow form (bend)","contour flow");
        add("Adds extra thin strokes to pick out fine details in the photo.",
            "More fine detail strokes; more realistic, busier image.",
            "Fewer detail strokes; cleaner, simpler image.",
            "Fine strokes are the first to fall below the minimum cut width; watch the cut-safety report.",
            null,
            "fine-detail strokes","fine-detail ribbon strokes");
        add("Smooths the edges of traced contour lines.",
            "Rounder, smoother edges that cut and weed cleanly.",
            "Edges follow the photo more exactly, including small jitters.",
            "Smoother contours cut faster and with fewer blade direction changes.",
            null,
            "contour smoothing");
        add("Blurs the photo slightly before tracing, removing noise and grain.",
            "Softer, simpler shapes with fewer tiny specks.",
            "Sharper tracing, more small detail and more specks.",
            "More smoothing removes many tiny unweedable islands.",
            "Raise it for phone photos taken in low light.",
            "photo smoothing");
        add("How much small photo detail is sampled into the pattern.",
            "More detail from the photo; busier result.",
            "Simpler, more graphic result.",
            "More detail generally means more and smaller pieces.",
            null,
            "photo detail","photo sampling detail","artistic detail","portrait detail","tile detail","maze detail","level of detail");
        add("How many dots the stipple places in the image.",
            "More dots: finer tonal detail.",
            "Fewer dots: simpler, faster to cut.",
            "Each dot is a separate cut; the count rises quickly near the top of the range.",
            null,
            "detail · point density","layer density");
        add("Size of each stipple dot.",
            "Larger dots: darker image and bigger, easier-to-weed pieces.",
            "Smaller dots: lighter image and more delicate texture.",
            "Dots smaller than about 1 mm are hard to weed in most vinyl.",
            null,
            "dot size");
        add("Smallest dot this layer is allowed to cut, in millimeters.",
            "Small dots are dropped or enlarged to this size: safer to weed.",
            "Allows tinier dots and finer texture.",
            "Below about 0.8 mm, dots often lift with the backing during weeding.",
            "Match it to your vinyl: thicker vinyl needs larger minimum dots.",
            "minimum dot diameter");
        add("Target share of empty eligible areas to fill with extra shapes.",
            "Fills more empty space for a fuller image.",
            "Leaves more open background.",
            null,
            null,
            "fill · target eligible coverage");
        add("Line density of the space-filling curve (how many times it folds).",
            "Denser, finer curve with more photo detail.",
            "Coarser curve with fewer, longer runs.",
            "Each step up roughly quadruples the path length.",
            null,
            "recursion / line density");
        add("How strongly the photo controls the curve's line weight.",
            "Line weight follows the photo closely.",
            "Lines stay more uniform and decorative.",
            null,
            null,
            "image influence");
        add("Rounds the corners where the curve turns.",
            "Rounder turns: smoother cuts and less tearing at corners.",
            "Sharp, geometric corners.",
            "Sharp inside corners are where vinyl tears most often.",
            null,
            "corner rounding","curve roundness");
        add("Rotates the whole pattern.",
            "Rotates counter-clockwise.",
            "Rotates clockwise.",
            null,
            null,
            "rotation");

        // ---------------- Line-style specific ----------------
        add("Height of the wave or zigzag in each line.",
            "Taller waves: more movement and a more decorative look.",
            "Flatter lines, closer to straight.",
            "Tall waves on tight spacing can bring neighboring lines close together.",
            null,
            "wave intensity","zigzag height");
        add("How many zigzags each line makes per inch.",
            "Tighter, more frequent zigzags.",
            "Longer, gentler zigzags.",
            "Many zigzags mean many sharp corners for the blade.",
            null,
            "zigzags per inch");
        add("Angle between the two directions of cross-hatching.",
            "Crossing lines become more perpendicular.",
            "Crossing lines become more parallel.",
            null,
            null,
            "hatch rotation");
        add("Horizontal position of the center of the radial pattern, as a share of the image width.",
            "Moves the center to the right.",
            "Moves the center to the left.",
            null,
            "Put the center on the subject's eye or face.",
            "focal point x");
        add("Vertical position of the center of the radial pattern, as a share of the image height.",
            "Moves the center down.",
            "Moves the center up.",
            null,
            null,
            "focal point y");
        add("Keeps faint lines in light areas instead of dropping them.",
            "More lines survive in light areas; more detail in skin and sky.",
            "Light areas become clean background.",
            "Retained light-area lines are often thin; check the minimum cut width.",
            null,
            "light-area lines");
        add("Joins short broken line segments into continuous lines.",
            "Fewer gaps; longer, sturdier lines that weed in one piece.",
            "Lines break wherever the photo is faint.",
            "Continuous lines are much easier to weed than many short pieces.",
            null,
            "micro-gap recovery");
        add("Restores portrait lines that would otherwise disappear.",
            "More face detail stays visible.",
            "Cleaner, simpler portrait.",
            null,
            null,
            "portrait line retention");
        add("Adds back mid-tone detail in faces.",
            "More shading in cheeks and forehead.",
            "Faces become flatter and more graphic.",
            null,
            null,
            "portrait midtone recovery","facial detail emphasis");
        add("Reduces line work in the outer background around the subject.",
            "Background becomes cleaner and the subject stands out.",
            "Background keeps its pattern.",
            "A cleaner background removes many cuts you would otherwise weed away.",
            null,
            "outer background reduction");
        add("Weight of the drawn line in millimeters.",
            "Thicker line.",
            "Thinner line.",
            "For vinyl cutting, lines thinner than about 0.6 mm are fragile.",
            null,
            "line weight","line width|mm","pen / engraving stroke");

        // ---------------- CMYK balance ----------------
        add("Keeps the black layer from covering areas that should show color.",
            "Colored areas stay colorful; black is held back.",
            "Black can cover more of the colored areas.",
            null,
            null,
            "protect black in colored areas");
        add("Adds cyan, magenta and yellow under the black in shadows for richer darks.",
            "Richer, deeper shadows built from several layers.",
            "Shadows rely on black alone.",
            "More colors in shadows means more overlapping vinyl and thicker buildup.",
            null,
            "recover cmy in shadows");
        add("Shifts the black screen slightly so it does not land exactly on the colors.",
            "Larger shift.",
            "Black lines up exactly with the color layers.",
            null,
            null,
            "black screen offset");
        add("Shifts the color blocks slightly for a printed, off-register look.",
            "More offset: stronger retro misregistration effect.",
            "Colors line up exactly.",
            null,
            null,
            "color block offset");

        // ---------------- Specialty styles ----------------
        add("Number of cells across the image.",
            "More, smaller cells: more photo detail.",
            "Fewer, larger cells: bolder mosaic.",
            "Cell count drives the total number of pieces.",
            null,
            "cell density","cap density","scale density");
        add("How many marks or motifs are cut into each cell.",
            "More marks per cell: richer texture.",
            "Fewer marks: simpler cells.",
            null,
            null,
            "marks per cell","motif depth");
        add("How much neighboring layer shapes overlap each other.",
            "More overlap: no gaps between colors when assembled.",
            "Less overlap: less vinyl stacking.",
            null,
            null,
            "layer overlap");
        add("Width of the overlap between neighboring colors so no gaps appear when layers are applied.",
            "Wider seam: more forgiving alignment.",
            "Narrower seam: less overlapping vinyl.",
            "The minimum is set by your cut material; it cannot go below what is safe to cut.",
            null,
            "color seam");
        add("Smallest piece allowed in the Layered Cell style.",
            "Tiny pieces are removed or merged: easier weeding.",
            "Allows finer detail.",
            null,
            null,
            "layered cell minimum feature");
        add("Width of the lead lines in the stained-glass style.",
            "Bolder leading, more like real stained glass.",
            "Thinner leading, more glass visible.",
            "Thin lead lines are delicate; stay above about 1 mm for easy weeding.",
            null,
            "lead thickness");
        add("Bend in the serpentine stripes.",
            "More winding stripes.",
            "Straighter stripes.",
            null,
            null,
            "serpentine bend");
        add("How strongly the Prismatic ribbon style shows its own pattern: contour steering for Contour Flow, the over/under stagger for Twill, the dominant flowing family for Silk, the paired sway for Braided Flow and the zigzag depth for Chevron.",
            "A bolder, more recognisable pattern. Ribbons may narrow where neighbours approach, so deep shadows can lighten slightly.",
            "Calmer ribbons that behave more like Prismatic Diagonal, with the fullest tonal range.",
            "Ribbon widths and gaps are recomputed for the material's minimum cut and weed gap at every setting; the summary reports any area changed by cut conditioning.",
            "Start at the default and raise it until the pattern reads at your artwork size; check faces at actual size.",
            "pattern strength");
        add("Number of rays in the sunburst background.",
            "More, narrower rays.",
            "Fewer, wider rays.",
            "Narrow rays get sharp points near the center.",
            null,
            "ray count");
        add("Spacing of the stripe texture.",
            "Wider stripes.",
            "Finer stripes.",
            null,
            null,
            "stripe spacing");
        add("How much of each stripe period is filled.",
            "Thicker stripes.",
            "Thinner stripes.",
            null,
            null,
            "stripe fill");
        add("Width of the border frame around the poster, as a share of the size.",
            "Wider frame.",
            "Thinner or no frame.",
            null,
            null,
            "frame");
        add("Gap between panels in the poster layout.",
            "Wider gutters between panels.",
            "Panels close together.",
            null,
            null,
            "panel gutter");
        add("Brightness level treated as background in the poster styles.",
            "More of the photo becomes background.",
            "Less becomes background.",
            null,
            null,
            "background tone");
        add("How different a color can be and still count as the automatic background.",
            "More areas count as background and are removed.",
            "Only colors very close to the background are removed.",
            null,
            "Raise it if bits of a plain background remain.",
            "auto background tolerance");
        add("Overlap of the colored layers onto the underlay.",
            "More overlap: hides small alignment errors.",
            "Less overlap: less vinyl stacked.",
            null,
            null,
            "underlay trap");
        add("Maze difficulty from easy to hardest.",
            "Harder maze with more dead ends.",
            "Easier maze.",
            null,
            null,
            "difficulty");

        // ---------------- Material and cut safety ----------------
        add("The narrowest piece of vinyl the export will keep.",
            "Only sturdier details are kept; easier weeding.",
            "Finer details are allowed but may tear or lift.",
            "This is a safety floor; the cut-safety check reports anything narrower.",
            "Match it to your vinyl: thin adhesive vinyl handles less than heat-transfer vinyl.",
            "minimum cut width","minimum vinyl feature");
        add("The narrowest gap between two cuts on the same sheet.",
            "Wider gaps are easier to weed.",
            "Allows tighter gaps, which may tear while weeding.",
            "Gaps narrower than the blade can handle can merge into one cut.",
            null,
            "minimum gap / bridge","minimum weed gap");
        add("Widest the vinyl ribbon may become in the curve styles.",
            "Bolder ribbons.",
            "Slimmer ribbons.",
            null,
            null,
            "maximum vinyl ribbon width");
        add("Minimum distance between cuts on one sheet in the stipple styles.",
            "More space between dots: easier weeding.",
            "Dots can sit closer together.",
            null,
            null,
            "stipple same-sheet clearance");
        add("Margin inside the crop where nothing is cut.",
            "Wider empty border.",
            "Artwork goes closer to the edge.",
            null,
            null,
            "no-cut inset");
        add("Extra vinyl around the artwork for handling.",
            "More material margin.",
            "Tighter fit on the material.",
            null,
            null,
            "outside material space");
        add("Diameter of the registration (alignment) marks.",
            "Bigger marks: easier to see and line up.",
            "Smaller marks: less wasted vinyl.",
            null,
            null,
            "mark diameter");
        add("Space kept clear between registration marks and the artwork.",
            "Marks sit farther from the artwork.",
            "Marks sit closer.",
            null,
            null,
            "registration clearance");
    }
}
