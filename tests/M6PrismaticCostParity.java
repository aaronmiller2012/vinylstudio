package com.halftonestudio;
import java.security.MessageDigest;
import java.nio.ByteBuffer;
/** Compare geometry SHA-256 on the same fixtures before/after the palette-cost hoist. */
public final class M6PrismaticCostParity {
    static String hash(StyleKit.Result r)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");ByteBuffer q=ByteBuffer.allocate(4);for(StyleKit.Layer l:r.layers){md.update(q.clear().putInt(l.color).array());md.update(q.clear().putInt(l.loops.size()).array());for(float[] p:l.loops){md.update(q.clear().putInt(p.length).array());for(float f:p)md.update(q.clear().putInt(Float.floatToIntBits(f)).array());}}return java.util.HexFormat.of().formatHex(md.digest());}
    public static void main(String[] args)throws Exception{
        int w=104,h=96;int[] p=PrismaticWeaveV3Test.ramp(w,h);int[] colors={0xfff4e7d8,0xffefd296,0xffb45983,0xffa881ae,0xff282a37};
        boolean[] valid=new boolean[p.length];for(int y=0;y<h;y++)for(int x=0;x<w;x++){float dx=(x-w*.5f)/(w*.48f),dy=(y-h*.5f)/(h*.47f);valid[y*w+x]=dx*dx+dy*dy<1&&dx*dx+dy*dy>.035f;}
        String[] expected={"521c2326c3a7cad22afd45453caf11f04f2be974ee1c2e009d7ae69c489435ae","e457fc1c2385d5b47641836c948f861bc704975006c44c6d2645c4c1c3983971","3a7f48866891b9fbe2115c349bd25937d869873153eca44ca476681342016e4f","bed47b2d26fea63c4a4d088ca9183c4c79217252dae23de4ac0993d8f0f1463c","b73700463515ccfcb0623c8a7f9b7fbe01928dd23348f25db6312dea88c42f90","2a10351b42bf46cd129137536cd4db8f00cf707f8fb99714c1fb670184579a85","dffe481b327a988a02a788a20d3637f0a1361a20a899142ba7e73c69740d0b92","9440da43a1242561976ebfe375e0a65b654bb1c891fa9d3b0d586746e264cecb","a6176dc154295d48ca7945fd7237b67936e5106042b3c9a1035392cf50f992d1","bcae9955863cad233c85adde258824241fcb212c281eb22b329eff46ba99c6b3","692cdee52ae3e1182546a6d80fe7a5678b29e4d570a6c400177903fb5d6c82fd","fc27c189686fe1ada3f16e5d50ea4dd4eca2e08e7125f178210e7d54278528ea"};int index=0;
        for(int id:new int[]{VinylStyleSuite.PRISMATIC_DIAGONAL,VinylStyleSuite.PRISMATIC_CONTOUR_FLOW,VinylStyleSuite.PRISMATIC_TWILL,VinylStyleSuite.PRISMATIC_SILK,VinylStyleSuite.PRISMATIC_BRAIDED_FLOW,VinylStyleSuite.PRISMATIC_CHEVRON})for(float size:new float[]{152.4f,279.4f}){
            VinylStyleSuite.Config c=new VinylStyleSuite.Config();c.style=id;c.widthMm=size;c.detail=70;c.colorCount=colors.length;c.palette=colors;c.allowed=valid;c.minimumMm=.8f;c.gapMm=.5f;c.draft=true;
            long t=System.nanoTime();StyleKit.Result r=VinylStyleSuite.generate(p,w,h,c);String actual=hash(r);if(!actual.equals(expected[index++]))throw new AssertionError("563 parity changed: "+id+" / "+size+" "+actual);System.out.println(id+" "+size+" "+actual+" ms="+(System.nanoTime()-t)/1_000_000L);
        }
        System.out.println("PASS all 12 Prismatic draft geometry hashes match 563");
    }
}
