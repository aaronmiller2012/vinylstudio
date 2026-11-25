package android.graphics;
public class Typeface {
    public static final int BOLD=1;
    public final String family;
    public final int weight;
    private Typeface(String family,int weight){this.family=family;this.weight=weight;}
    public static Typeface create(String family,int weight){return new Typeface(family,weight);}
}
