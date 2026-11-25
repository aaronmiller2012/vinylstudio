package com.halftonestudio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure-Java, bounded and ordered model for user-named vinyl palettes.
 * Android storage/UI are intentionally outside this class so its core
 * constraints can be tested with a real host javac compilation.
 */
public final class SavedVinylPaletteLibrary {
    public static final int MAX_ENTRIES=50;
    public static final int MAX_NAME_LENGTH=40;
    private SavedVinylPaletteLibrary(){}

    public static final class Entry {
        public final String id;
        public final String name;
        public final int[] baseColors;
        public final int hueDegrees;
        public final boolean cmykSource;
        public final int cmykBlackBase;
        public final int[] cmykAccentBases;
        public Entry(String id,String name,int[] colors,int hueDegrees){
            this(id,name,colors,hueDegrees,false,0,new int[4]);
        }
        public Entry(String id,String name,int[] colors,int hueDegrees,
                     boolean sourceCmyk,int blackBase,int[] accentBases){
            if(id==null||id.trim().isEmpty()||id.length()>128)
                throw new IllegalArgumentException("Invalid palette identifier");
            if(colors==null||(colors.length!=8&&colors.length!=9))
                throw new IllegalArgumentException("Eight or nine swatches required");
            if(hueDegrees< -180||hueDegrees>180)
                throw new IllegalArgumentException("Hue must be -180 to 180 degrees");
            this.id=id;this.name=checkedName(name);
            if(accentBases==null||accentBases.length!=4)
                throw new IllegalArgumentException("CMYK needs four accent swatches");
            this.baseColors=colors.clone();this.hueDegrees=hueDegrees;
            this.cmykSource=sourceCmyk;this.cmykBlackBase=blackBase;
            this.cmykAccentBases=accentBases.clone();
        }
    }
    public static String checkedName(String name){
        if(name==null||name.trim().isEmpty())throw new IllegalArgumentException("Enter a palette name");
        String trimmed=name.trim();
        if(trimmed.length()>MAX_NAME_LENGTH)
            throw new IllegalArgumentException("Use at most "+MAX_NAME_LENGTH+" characters");
        for(int i=0;i<trimmed.length();i++){
            if(Character.isISOControl(trimmed.charAt(i)))
                throw new IllegalArgumentException("Remove control characters from name");
        }
        return trimmed;
    }
    public static Entry findName(List<Entry> entries,String name){
        if(entries==null||name==null)return null;
        for(Entry entry:entries)if(entry.name.toLowerCase(Locale.ROOT)
            .equals(name.trim().toLowerCase(Locale.ROOT)))return entry;
        return null;
    }
    public static Entry findId(List<Entry> entries,String id){
        if(entries==null)return null;
        for(Entry entry:entries)if(entry.id.equals(id))return entry;
        return null;
    }
    /** Save to top, replacing the same item by id; never silently discard older entries. */
    public static ArrayList<Entry> saveFirst(List<Entry> existing,Entry item){
        if(existing==null||item==null)throw new IllegalArgumentException("Missing palette");
        ArrayList<Entry> next=new ArrayList<>();next.add(item);
        for(Entry entry:existing){
            if(entry.id.equals(item.id))continue;
            if(entry.name.equalsIgnoreCase(item.name))
                throw new IllegalArgumentException("A palette with that name already exists");
            next.add(entry);
        }
        if(next.size()>MAX_ENTRIES)throw new IllegalArgumentException("Library full — delete a saved palette first");
        return next;
    }
    public static ArrayList<Entry> remove(List<Entry> existing,String id){
        ArrayList<Entry> next=new ArrayList<>();
        for(Entry entry:existing)if(!entry.id.equals(id))next.add(entry);
        return next;
    }
}
