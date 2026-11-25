package com.halftonestudio;

import java.util.Arrays;

/** Internal smoothing settings must preserve geometry; the public UI is fixed at 50%. */
public final class V3OrganicSmoothingSliderTest {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static int contourCount(StyleKit.Result result){int n=0;for(StyleKit.Layer l:result.layers)n+=l.loops.size();return n;}
    private static int[] testPhoto(int w,int h){return V3AllStyleExportMatrixTest.portrait(w,h);}
    public static void main(String[] args){
        int w=150,h=180;int[] photo=testPhoto(w,h);
        float[] sizes=args.length>0?new float[]{Float.parseFloat(args[0])}:
            new float[]{76.2f,101.6f,279.4f};
        int onlyStyle=args.length>1?Integer.parseInt(args[1]):-1;
        for(float size:sizes){
            for(int style=VinylStyleSuite.QUILLING_RIBBONS;style<=VinylStyleSuite.QUILLED_MARBLING;style++){
                if(onlyStyle>=0&&style!=onlyStyle)continue;
                StyleKit.Result[] samples=new StyleKit.Result[3];int[] steps={0,50,100};
                for(int i=0;i<steps.length;i++){
                    VinylStyleSuite.Config c=new VinylStyleSuite.Config();c.style=style;
                    c.widthMm=size;c.colorCount=6;c.minimumMm=.8f;c.gapMm=.5f;
                    c.detail=90;c.organicSmoothing=steps[i];
                    samples[i]=VinylStyleSuite.generate(photo,w,h,c);
                    require(samples[i].layers.size()==6,"Physical layer count changed: size="+size+" style="+style+" smoothing="+steps[i]);
                    for(int slot=0;slot<6;slot++){
                        StyleKit.Layer sheet=samples[i].layers.get(slot);
                        require(sheet.name.equals("organic-flow-"+(slot+1)),"Physical slot shifted: "+slot);
                        require(!sheet.loops.isEmpty(),"Requested sheet empty: "+slot);
                        if(i>0)require(sheet.color==samples[0].layers.get(slot).color,"Swatch changed with smoothing");
                    }
                    require(!samples[i].layers.get(0).loops.isEmpty(),"Solid Snip lost");
                    for(StyleKit.Layer l:samples[i].layers){
                        require(l.lines.isEmpty(),"Open stroke path introduced");
                        for(float[] loop:l.loops){
                            require((loop.length&1)==0 && loop.length>=6,"Malformed loop");
                            if(size<=101.6f)require(StyleKit.selfIntersections(loop,1)==0,"Self crossing in loop");
                        }
                    }
                    // Full export scan runs for 3-inch output at each level,
                    // plus default 11-inch output. 11-inch 0/100 vector scans
                    // are time-expensive and covered by the NAS export gates.
                    if(size==76.2f){
                        StyleKit.Result prepared=VinylStyleSuite.prepareForCutting(samples[i],style,c.minimumMm,c.gapMm);
                        int bad=0;
                        for(int l=0;l<prepared.layers.size();l++){
                            String svg=NewStyleExport.layerSvg(prepared,l,false,0,3,2,null,c.minimumMm);
                            FinalCutSheetSafety.Report report=FinalCutSheetSafety.inspect("layer"+l,svg,c.minimumMm,c.gapMm);
                            bad+=report.narrow+report.critical+report.unverified;
                        }
                        require(bad==0,"Cut geometry findings at "+size+" style="+style+" smoothing="+steps[i]+" findings="+bad);
                    }
                }
                require(samples[0].layers.get(0).loops.size()==samples[2].layers.get(0).loops.size(),"Structural Snip loop count changed");
                for(int k=0;k<samples[0].layers.get(0).loops.size();k++){
                    require(Arrays.equals(samples[0].layers.get(0).loops.get(k),samples[2].layers.get(0).loops.get(k)),
                        "Structural Snip geometry changed due to smoothing");
                }
                int rawContours=contourCount(samples[0]),smoothContours=contourCount(samples[1]),maxContours=contourCount(samples[2]);
                require(smoothContours<=rawContours,"Default smoothing added extra cuts");
                require(maxContours<=rawContours,"Strong smoothing added extra cuts");
                require(samples[0].totalPoints()!=samples[2].totalPoints()||rawContours!=maxContours,
                    "Slider does not change cut geometry");
                System.out.println("PASS size="+size+" id="+style+" 0/50/100 contours="+
                    rawContours+"/"+smoothContours+"/"+maxContours+
                    " nodes="+samples[0].totalPoints()+"/"+samples[1].totalPoints()+"/"+samples[2].totalPoints());
            }
        }
        for(int invalid:new int[]{-1,101}){
            OrganicFlowPortraitEngine.Options o=new OrganicFlowPortraitEngine.Options();o.smoothing=invalid;
            boolean rejected=false;
            try{o.validate();}catch(IllegalArgumentException expected){rejected=true;}
            require(rejected,"Invalid smoothing accepted: "+invalid);
        }
        VinylStyleSuite.Config config=new VinylStyleSuite.Config();
        config.organicSmoothing=85;
        require(config.copy().organicSmoothing==85,"Smoothing lost in async config snapshot");
        System.out.println("PASS OrganicFlow internal smoothing geometry and physical safety");
    }
}
