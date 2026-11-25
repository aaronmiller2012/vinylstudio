package com.halftonestudio;

public final class CutSvgCompatibilityV2540Test {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void rejected(String svg){
        boolean failed=false;
        try{CutSvgCompatibility.verify(svg);}catch(IllegalArgumentException expected){failed=true;}
        require(failed,"Unsafe SVG should have been rejected");
    }
    public static void main(String[] args){
        String head="<svg xmlns=\"http://www.w3.org/2000/svg\">";
        require(CutSvgCompatibility.verify(head+"<path d=\"M0 0L2 2Z\"/></svg>")==1,"path");
        require(CutSvgCompatibility.verify(head+"<circle cx=\"1\" cy=\"1\" r=\"1\"/><path d=\"M0 0Z\"/></svg>")==2,"mixed paths");
        require(CutSvgCompatibility.verify(head+"<pathway/><path d=\"M0 0Z\"/></svg>")==1,"tag boundaries");
        rejected(head+"<clipPath id=\"m\"/><path d=\"M0 0Z\"/></svg>");
        rejected(head+"<image href=\"remote.png\"/><path d=\"M0 0Z\"/></svg>");
        rejected(head+"<pattern id=\"fill\"/><path d=\"M0 0Z\"/></svg>");
        rejected(head+"<linearGradient id=\"a\"/><path d=\"M0 0Z\"/></svg>");
        rejected(head+"<text>Vinyl</text><path d=\"M0 0Z\"/></svg>");
        rejected(head+"</svg>");
        StringBuilder large=new StringBuilder(head);
        for(int i=0;i<5001;i++)large.append("<circle cx=\"1\" cy=\"1\" r=\"1\"/>");
        large.append("</svg>");rejected(large.toString());
        StringBuilder boundary=new StringBuilder(head);
        for(int i=0;i<5000;i++)boundary.append("<path d=\"M0 0Z\"/>");
        boundary.append("</svg>");
        require(CutSvgCompatibility.verify(boundary.toString())==5000,"5000 limit");
        System.out.println("PASS v2.54.0: SVG path limit, flat vectors, cut and pen shapes, unsupported SVG rejection");
    }
}
