package com.halftonestudio;
class EightStyleExportTest{public static void main(String[] z){StyleKit.Result r=new StyleKit.Result(152.4f,120f);StyleKit.Layer l=new StyleKit.Layer("test",0xff123456);l.loops.add(new float[]{1,1,40,1,40,40,1,40});r.layers.add(l);System.out.println(NewStyleExport.layerSvg(r,0,true,0,2.5f,1.5f).substring(0,200));}}
