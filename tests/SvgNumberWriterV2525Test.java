package com.halftonestudio;
import java.util.Locale;
public class SvgNumberWriterV2525Test {
    public static void main(String[] args){
        for(int decimals:new int[]{3,5}){
            for(float f:new float[]{0,1,-1,1.2345f,-13.025f,0.001f,-0.001f,125.0f,42.3333f}){
                String s=SvgNumberWriter.append(new StringBuilder(),f,decimals).toString();
                double delta=Math.abs(Double.parseDouble(s)-f);
                if(delta>Math.pow(10,-decimals)*.52)throw new AssertionError(f+" => "+s);
                if(s.contains("E"))throw new AssertionError(s);
            }
        }
        System.out.println("PASS fixed-decimal SVG precision and finite checks");
    }
}
