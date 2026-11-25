package android.graphics;
public final class Paint {public enum Style{FILL,STROKE}private Style current=Style.STROKE;
public Style getStyle(){return current;}
public void setStyle(Style s){current=s;}}
