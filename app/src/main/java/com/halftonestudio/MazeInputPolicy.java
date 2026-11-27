package com.halftonestudio;

/** A no-cut inset is a material/layout setting, not a crop mask.
 * Keep it for all other styles; a perfect rectangular maze must extend
 * to the artwork boundary at its entrance and exit. Outside material
 * spacing remains independently controlled. Do not mutate saved settings.
 */
final class MazeInputPolicy {
    private MazeInputPolicy(){}
    static float effectiveInsetMm(boolean portraitMaze,float savedInsetMm){
        return portraitMaze?0f:savedInsetMm;
    }
    static void requireRectangle(boolean circular,boolean snipped){
        if(circular)throw new IllegalArgumentException("Maze needs a rectangular crop. Turn off circle crop.");
        if(snipped)throw new IllegalArgumentException("Maze needs an uncut rectangle. Remove the Snip before rendering.");
    }
}
