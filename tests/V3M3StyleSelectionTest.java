package com.halftonestudio;

import java.util.*;

/**
 * M3 StyleSelection. The load-time precedence used to be ~30 lines of flag clearing in
 * StudioActivity.restoreArtworkModes; it is reproduced verbatim below as an ORACLE and
 * compared against StyleSelection.fromLegacyFlags for every combination of saved flags,
 * so existing projects open in exactly the same style as before.
 */
public final class V3M3StyleSelectionTest {
    private static void yes(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}

    /** The nine fields exactly as the pre-558 loader left them. */
    static final class Legacy {
        String line;boolean tonal,maze,tess,layered,offset,bottle,fish;int overprint,suite;
        String key(){return suite+"|"+line+"|"+tonal+maze+tess+layered+offset+bottle+fish+"|"+overprint;}
    }
    /** Verbatim port of StudioActivity's pre-558 normalization (build 557). */
    static Legacy oracle(int suite,String line,boolean tonal,boolean maze,boolean tess,boolean layered,
                         boolean offset,boolean bottle,boolean fish,int savedOverprint){
        Legacy o=new Legacy();
        o.suite=suite;o.line=line;o.tonal=tonal;o.maze=maze;o.tess=tess;o.layered=layered;
        o.offset=layered&&offset;o.bottle=layered&&bottle;o.fish=layered&&fish;
        o.overprint=o.layered?savedOverprint:0;
        if(o.overprint>0){o.offset=false;o.bottle=false;o.fish=false;}
        if(o.fish){o.bottle=false;o.offset=false;}
        else if(o.bottle)o.offset=false;
        if(o.suite>=0){o.line=null;o.tonal=false;o.maze=false;o.tess=false;o.layered=false;}
        if(o.line!=null){o.tonal=false;o.maze=false;o.tess=false;o.layered=false;}
        else if(o.tess){o.tonal=false;o.maze=false;o.layered=false;}
        else if(o.layered){o.tonal=false;o.maze=false;}
        else if(o.tonal){o.maze=false;}
        if(!o.layered){o.offset=false;o.bottle=false;o.fish=false;o.overprint=0;}
        return o;
    }
    /** What StudioActivity.applyStyleSelection now derives from a selection. */
    static Legacy derived(StyleSelection s){
        Legacy o=new Legacy();
        o.line=s.is(StyleSelection.Kind.CONTINUOUS_LINE)?s.lineStyle:null;
        o.tonal=s.is(StyleSelection.Kind.TONAL_STENCIL);o.maze=s.is(StyleSelection.Kind.MAZE);
        o.tess=s.is(StyleSelection.Kind.TESSELLATION);o.layered=s.is(StyleSelection.Kind.LAYERED_CELL);
        o.offset=s.offsetCell();o.bottle=s.bottleCap();o.fish=s.fishScale();
        o.overprint=s.overprintKind;o.suite=s.suiteId;
        return o;
    }
    private static int modesOn(Legacy o){
        int n=0;for(boolean b:new boolean[]{o.line!=null,o.tonal,o.maze,o.tess,o.layered,o.suite>=0})if(b)n++;
        return n;
    }
    public static void main(String[] args){
        int compared=0;
        int[] suites={-1,0,7};String[] lines={null,"SPIRAL","PEANO"};int[] overprints={0,1,4};
        for(int suite:suites)for(String line:lines)for(int bits=0;bits<256;bits++)for(int op:overprints){
            boolean tonal=(bits&1)!=0,maze=(bits&2)!=0,tess=(bits&4)!=0,layered=(bits&8)!=0,
                offset=(bits&16)!=0,bottle=(bits&32)!=0,fish=(bits&64)!=0;
            if((bits&128)!=0)continue;
            int saved=layered?op:0; // the loader passes 0 overprint when not layered
            Legacy expected=oracle(suite,line,tonal,maze,tess,layered,offset,bottle,fish,saved);
            StyleSelection s=StyleSelection.fromLegacyFlags(suite,line,tonal,maze,tess,layered,offset,bottle,fish,saved);
            Legacy actual=derived(s);
            yes(expected.key().equals(actual.key()),"load precedence changed for "+suite+"/"+line+"/"+bits+"/"+op+
                ": was "+expected.key()+" now "+actual.key()+" ("+s+")");
            yes(modesOn(actual)<=1,"two artwork modes on at once: "+s);
            // Round trip: reading the derived fields back gives the same selection.
            yes(StyleSelection.fromLegacyFlags(actual.suite,actual.line,actual.tonal,actual.maze,actual.tess,actual.layered,
                actual.offset,actual.bottle,actual.fish,actual.overprint).equals(s),"selection does not round-trip: "+s);
            compared++;
        }
        // Every constructor yields exactly one mode; overprint always forces the PLAIN cell.
        yes(StyleSelection.layeredCell(StyleSelection.Cell.FISH_SCALE,3).cell==StyleSelection.Cell.PLAIN,"overprint kept a cell variant");
        yes(!StyleSelection.HALFTONE.offsetCell()&&StyleSelection.HALFTONE.suiteId==-1,"halftone carries layered/suite state");
        yes(modesOn(derived(StyleSelection.suite(3)))==1&&modesOn(derived(StyleSelection.maze()))==1,"constructor produced extra mode");
        for(Runnable bad:new Runnable[]{()->StyleSelection.suite(-1),()->StyleSelection.continuous(""),
                ()->StyleSelection.layeredCell(StyleSelection.Cell.PLAIN,-1)}){
            try{bad.run();throw new AssertionError("invalid selection accepted");}catch(IllegalArgumentException expected){}
        }
        // Retired overprint kinds (Painterly, out of range) open as plain photo artwork with no
        // layered mode or cell variant, even when the saved project also set the variant flags.
        yes(StyleSelection.isRetiredOverprint(LayeredCellPortraitEngine.OVERPRINT_PAINTERLY)&&
            StyleSelection.isRetiredOverprint(-1)&&StyleSelection.isRetiredOverprint(LayeredCellPortraitEngine.OVERPRINT_MIXED+1),
            "retired overprint kind accepted");
        for(int k=LayeredCellPortraitEngine.OVERPRINT_NONE;k<=LayeredCellPortraitEngine.OVERPRINT_MIXED;k++)
            yes(StyleSelection.isRetiredOverprint(k)==(k==LayeredCellPortraitEngine.OVERPRINT_PAINTERLY),"live overprint kind "+k+" retired");
        // The loader passes layered=false once the kind is retired:
        Legacy retired=derived(StyleSelection.fromLegacyFlags(-1,null,false,false,false,false,true,true,true,0));
        yes(!retired.layered&&!retired.offset&&!retired.bottle&&!retired.fish&&retired.overprint==0&&modesOn(retired)==0,
            "retired Painterly project kept a layered cell mode");
        // Every real line-style name is accepted (applyStyleSelection calls LineStyle.valueOf).
        for(ContinuousLineModes.LineStyle ls:ContinuousLineModes.LineStyle.values())
            yes(StyleSelection.continuous(ls.name()).lineStyle.equals(ls.name()),"line style name lost");
        System.out.println("PASS M3 StyleSelection matches the 557 load precedence for "+compared+
            " saved-flag combinations; exactly one artwork mode is ever active");
    }
}
