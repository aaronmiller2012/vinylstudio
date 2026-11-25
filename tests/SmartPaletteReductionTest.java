package com.halftonestudio;
import java.util.Arrays;
public final class SmartPaletteReductionTest {
    private static void require(boolean v,String message){if(!v)throw new AssertionError(message);}
    private static boolean has(int[] a,int n,int v){for(int i=0;i<n;i++)if(a[i]==v)return true;return false;}
    public static void main(String[] args){
        int[] original={0xffeee4cb,0xffe6ce94,0xffe5a849,0xff829e74,0xffbc827e,0xffc14633,0xffbd86a0,0xff292523};
        int[] photo=new int[1600];for(int i=0;i<photo.length;i++)photo[i]=i<400?original[7]:i<800?original[0]:i<1100?original[5]:i<1350?original[2]:original[4];
        int[] colors=original.clone(),ranks=SmartPaletteReduction.identity(8);
        for(int n=7;n>=2;n--){int[] p=SmartPaletteReduction.plan(colors,ranks,n,photo,SmartPaletteReduction.PORTRAIT);colors=SmartPaletteReduction.permute(colors,p);ranks=SmartPaletteReduction.permute(ranks,p);
            require(has(colors,n,original[7]),"Lost darkest swatch at "+n);require(has(colors,n,original[0]),"Lost lightest swatch at "+n);
            if(n==5)require(!has(colors,n,original[3]),"Unused green consumed a slot");
        }
        int[] p=SmartPaletteReduction.plan(colors,ranks,8,photo,SmartPaletteReduction.PORTRAIT);
        require(Arrays.equals(original,SmartPaletteReduction.permute(colors,p)),"Full bank not restored");
        for(int style=0;style<VinylStyleSuite.NAMES.length;style++){
            if(!NewStyleColorSlots.multi(style)||VinylStyleSuite.isRetiredStyleId(style))continue;
            int max=NewStyleColorSlots.maximumSelectable(style), min=NewStyleColorSlots.minimumSelectable(style);
            int[] bank=Arrays.copyOf(original,max);if(max==9)bank[8]=0xff6e4964;
            int policy=SmartPaletteReduction.policyForStyle(style,0);
            for(int n=min;n<=max;n++){
                int[] perm=SmartPaletteReduction.plan(bank,SmartPaletteReduction.identity(bank.length),n,photo,policy);
                int[] ranks2=SmartPaletteReduction.validRanks(perm,bank.length);require(Arrays.equals(perm,ranks2),"Invalid permutation "+style);
                if(policy==SmartPaletteReduction.PANE||policy==SmartPaletteReduction.STRUCTURAL||policy==SmartPaletteReduction.TONAL_STRUCTURE)require(perm[0]==0,"Structural foundation moved "+style);
            }
        }
        int w=128,h=100;int[] image=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)image[y*w+x]=x<40?original[7]:x<80?original[0]:original[5];
        colors=original.clone();ranks=SmartPaletteReduction.identity(8);
        for(int n:new int[]{8,7,6,5,2,8}){
            int[] order=SmartPaletteReduction.plan(colors,ranks,n,image,SmartPaletteReduction.PORTRAIT);
            colors=SmartPaletteReduction.permute(colors,order);ranks=SmartPaletteReduction.permute(ranks,order);
            TessellationMotifEngine.Options o=new TessellationMotifEngine.Options();o.widthMm=152.4f;o.detail=50;o.layerCount=n;o.palette=Arrays.copyOf(colors,n);
            StyleKit.Result result=TessellationMotifEngine.generate(image,w,h,o);
            boolean dark=false;for(StyleKit.Layer l:result.layers)if(l.color==original[7]&&!l.loops.isEmpty())dark=true;
            require(dark,"Dark vinyl geometry missing at count "+n);
            require(StyleKit.toSvg(result,false).toLowerCase(java.util.Locale.ROOT).contains("#292523"),"Dark SVG geometry missing at count "+n);
        }
        int[] duplicate={0xff000000,0xff000000,0xffffffff,0xffaa5555};
        int[] perm=SmartPaletteReduction.plan(duplicate,null,2,null,SmartPaletteReduction.PORTRAIT);
        require(Arrays.equals(perm,SmartPaletteReduction.validRanks(perm,4)),"Duplicate identity lost");
        int[] transparent=new int[20];int[] a=SmartPaletteReduction.plan(original,null,5,transparent,SmartPaletteReduction.PORTRAIT);
        require(Arrays.equals(a,SmartPaletteReduction.plan(original,null,5,null,SmartPaletteReduction.PORTRAIT)),"Masked pixels affected selection");
        int[] anchored=SmartPaletteReduction.plan(original,null,2,photo,SmartPaletteReduction.STRUCTURAL,1<<5);
        require(anchored[0]==0&&anchored[1]==5,"Explicit background not protected");
        require(Arrays.equals(SmartPaletteReduction.validRanks(new int[]{0,0},2),new int[]{0,1}),"Bad project ranks not sanitized");
        int[] maskedPhoto=new int[100];Arrays.fill(maskedPhoto,original[5]);
        int[] muted=SmartPaletteReduction.plan(original,null,2,maskedPhoto,SmartPaletteReduction.PORTRAIT);
        require(has(SmartPaletteReduction.permute(original,muted),2,original[5]),"Flat photo lost exact hue");
        int[] eligible=SmartPaletteReduction.plan(original,null,3,photo,SmartPaletteReduction.PORTRAIT,0,0b01111111);
        require(!has(eligible,3,7),"Disabled material was resurrected");
        System.out.println("PASS smart palette: screenshot fixture, full restoration, all registered multicolor specialty policies, duplicates, mask, structural and fixed role protection");
    }
}
