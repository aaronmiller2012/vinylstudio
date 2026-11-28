package com.halftonestudio;

/** Adapter for the existing vector preview and cut export contract. */
final class PortraitMazeSuiteAdapter {
    private PortraitMazeSuiteAdapter() {}
    static StyleKit.Result generate(int[] pixels,int width,int height,VinylStyleSuite.Config c,float minimum){
        PerfectPortraitMaze.Options o=new PerfectPortraitMaze.Options();
        o.mode=PerfectPortraitMaze.Mode.values()[c.style-VinylStyleSuite.PORTRAIT_MAZE];
        o.widthMm=c.widthMm;o.detail=c.detail;o.difficulty=Math.max(0,Math.min(3,c.variant));
        o.minWallMm=minimum;o.minPassageMm=Math.max(c.weightMm,Math.max(.8f,c.gapMm));
        PerfectPortraitMaze.Result maze=PerfectPortraitMaze.generate(pixels,width,height,c.allowed,o);
        StyleKit.Result result=new StyleKit.Result((float)maze.widthMm,(float)maze.heightMm);
        result.paperArgb=c.paper;result.maze=maze;result.summary=maze.report();
        boolean multi=o.mode==PerfectPortraitMaze.Mode.MULTILAYER;
        int[] palette=c.palette;int base=palette!=null&&palette.length>=2?palette[0]:0xfff2dfb8;
        int ink=palette!=null&&palette.length>0?palette[multi&&palette.length>=2?1:0]:0xff161616;
        if(multi){StyleKit.Layer layer=new StyleKit.Layer("maze-base-1",base);layer.vectorGeometryLocked=true;
            layer.loops.add(new float[]{0,0,result.widthMm,0,result.widthMm,result.heightMm,0,result.heightMm});result.layers.add(layer);}
        StyleKit.Layer walls=new StyleKit.Layer(multi?"maze-walls-2":"maze-walls-1",ink);walls.vectorGeometryLocked=true;
        walls.loops.addAll(maze.wallLoops());result.layers.add(walls);return result;
    }
    static void verify(StyleKit.Result r,int style,float minWall,float minGap){
        boolean multi=style==VinylStyleSuite.MULTILAYER_MAZE;
        if(r==null||r.maze==null||r.layers.size()!=(multi?2:1)||r.maze.wallFloorMm<minWall||r.maze.passageFloorMm<Math.max(.8f,minGap))throw new IllegalStateException("Maze certification missing or cut settings changed; render again");
        if(multi){StyleKit.Layer b=r.layers.get(0);if(b.loops.size()!=1||!b.lines.isEmpty()||!java.util.Arrays.equals(b.loops.get(0),new float[]{0,0,r.widthMm,0,r.widthMm,r.heightMm,0,r.heightMm}))throw new IllegalStateException("Maze base geometry changed");}
        StyleKit.Layer l=r.layers.get(r.layers.size()-1);java.util.List<float[]> expected=r.maze.wallLoops();
        if(!l.vectorGeometryLocked||!l.lines.isEmpty()||l.loops.size()!=expected.size())throw new IllegalStateException("Maze wall geometry changed");
        for(int i=0;i<expected.size();i++)if(!java.util.Arrays.equals(l.loops.get(i),expected.get(i)))throw new IllegalStateException("Maze wall geometry changed");
    }
    static String puzzleSvg(StyleKit.Result r,boolean solved,int mountingColor){
        if(r.maze==null||r.layers.isEmpty())throw new IllegalArgumentException("No verified maze");
        int background=r.layers.size()>1?r.layers.get(0).color:mountingColor;
        return r.maze.svg(solved,r.layers.get(r.layers.size()-1).color,background);
    }
}
