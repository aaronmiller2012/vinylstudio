package com.halftonestudio;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic synthetic portrait fixtures for the M5 benchmark (generated, no licence
 * constraints). They fill gaps in the CC0 photo set: light and dark skin, eyeglasses, fine
 * hair, low and high contrast, strong highlights, deep shadows, busy backgrounds, a
 * low-resolution source and a landscape composition. Every fixture is a pure function of
 * its name, so the same pixels are produced on every host.
 *
 * These are stylised faces, not photographs; they measure feature retention (eyes, brows,
 * nose, mouth, glasses frames, hair strands) and tone handling, and never replace review on
 * real photographs.
 */
public final class M5Fixtures {
    private M5Fixtures(){}

    public static final String[] NAMES={
        "syn-light-skin","syn-dark-skin","syn-glasses","syn-fine-hair","syn-low-contrast",
        "syn-high-contrast","syn-highlights","syn-deep-shadow","syn-busy-background",
        "syn-low-res","syn-landscape"};

    static final class Spec {
        int w=600,h=760;Color skin=new Color(0xE8C2A6),hair=new Color(0x3A2A20),bg=new Color(0xC9D3DC);
        float contrast=1f,light=0f;boolean glasses,fineHair,busy,lowRes,landscape;float faceX=.5f;
    }

    static Spec spec(String name){
        Spec s=new Spec();
        switch(name){
            case "syn-light-skin": s.skin=new Color(0xF1D3C0);s.hair=new Color(0xB08A5A);break;
            case "syn-dark-skin": s.skin=new Color(0x6B4530);s.hair=new Color(0x161110);s.bg=new Color(0xD8CFC2);break;
            case "syn-glasses": s.glasses=true;s.skin=new Color(0xD9A98A);break;
            case "syn-fine-hair": s.fineHair=true;s.hair=new Color(0x5B4632);break;
            case "syn-low-contrast": s.contrast=.35f;break;
            case "syn-high-contrast": s.contrast=1.8f;break;
            case "syn-highlights": s.light=.45f;s.skin=new Color(0xF0CDB4);break;
            case "syn-deep-shadow": s.light=-.55f;s.skin=new Color(0x8E5E44);break;
            case "syn-busy-background": s.busy=true;break;
            case "syn-low-res": s.lowRes=true;break;
            case "syn-landscape": s.landscape=true;s.w=900;s.h=600;s.faceX=.36f;s.glasses=true;break;
            default: throw new IllegalArgumentException("Unknown fixture "+name);
        }
        return s;
    }

    /** ARGB pixels and size: {w,h} in dims. */
    public static int[] render(String name,int[] dims){
        Spec s=spec(name);
        BufferedImage img=draw(s);
        if(s.lowRes){ // 96 px wide source, as from an old phone thumbnail, then shown at full size
            int lw=96,lh=Math.round(96f*s.h/s.w);
            BufferedImage small=new BufferedImage(lw,lh,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=small.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img,0,0,lw,lh,null);g.dispose();img=small;
        }
        int w=img.getWidth(),h=img.getHeight();
        int[] p=img.getRGB(0,0,w,h,null,0,w);
        for(int i=0;i<p.length;i++)p[i]=adjust(p[i]|0xff000000,s.contrast,s.light);
        dims[0]=w;dims[1]=h;return p;
    }

    static int adjust(int argb,float contrast,float light){
        int r=(argb>>16)&255,g=(argb>>8)&255,b=argb&255;
        return 0xff000000|(ch(r,contrast,light)<<16)|(ch(g,contrast,light)<<8)|ch(b,contrast,light);
    }
    static int ch(int v,float c,float l){
        float x=v/255f;x=(x-.5f)*c+.5f;
        x=l>=0?x+(1-x)*l*x*1.6f:x*(1+l*(1-x)); // lift highlights / crush shadows
        return Math.max(0,Math.min(255,Math.round(x*255)));
    }

    static BufferedImage draw(Spec s){
        int w=s.w,h=s.h;
        BufferedImage img=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
        Random rnd=new Random(0x5EED561L);
        g.setPaint(new GradientPaint(0,0,s.bg.brighter(),w,h,s.bg.darker()));g.fillRect(0,0,w,h);
        if(s.busy){
            for(int i=0;i<160;i++){
                g.setColor(new Color(rnd.nextInt(0xffffff)));
                int x=rnd.nextInt(w),y=rnd.nextInt(h),r=6+rnd.nextInt(60);
                if((i&1)==0)g.fillRect(x,y,r,r/2+4);else g.fillOval(x,y,r,r);
            }
        }
        float u=Math.min(w,h*0.79f); // face scale unit
        float cx=w*s.faceX,cy=h*.47f;
        float fw=u*.52f,fh=u*.70f;
        // shoulders / clothing
        g.setColor(new Color(0x2F4A6B));
        Path2D sh=new Path2D.Float();sh.moveTo(cx-u*.75f,h);sh.curveTo(cx-u*.6f,cy+fh*.55f,cx+u*.6f,cy+fh*.55f,cx+u*.75f,h);sh.closePath();g.fill(sh);
        // neck
        g.setColor(s.skin.darker());g.fill(new Ellipse2D.Float(cx-fw*.22f,cy+fh*.25f,fw*.44f,fh*.55f));
        // hair mass behind
        g.setColor(s.hair);g.fill(new Ellipse2D.Float(cx-fw*.62f,cy-fh*.62f,fw*1.24f,fh*.95f));
        // face with directional shading
        Color lit=blend(s.skin,Color.WHITE,.22f),shade=blend(s.skin,Color.BLACK,.38f);
        g.setPaint(new RadialGradientPaint(cx-fw*.18f,cy-fh*.18f,fw*.75f,new float[]{0f,.6f,1f},new Color[]{lit,s.skin,shade}));
        g.fill(new Ellipse2D.Float(cx-fw/2,cy-fh/2,fw,fh));
        // fringe
        g.setColor(s.hair);
        Path2D fr=new Path2D.Float();fr.moveTo(cx-fw*.52f,cy-fh*.12f);fr.curveTo(cx-fw*.4f,cy-fh*.62f,cx+fw*.45f,cy-fh*.66f,cx+fw*.52f,cy-fh*.1f);
        fr.curveTo(cx+fw*.2f,cy-fh*.38f,cx-fw*.2f,cy-fh*.36f,cx-fw*.52f,cy-fh*.12f);g.fill(fr);
        if(s.fineHair){ // individual strands, 1-2 px, the hardest feature for minimum-width styles
            g.setStroke(new BasicStroke(1.3f));
            for(int i=0;i<140;i++){
                float a=(float)(Math.PI*(1.05+.9*rnd.nextFloat()));
                float r0=fw*.45f,r1=fw*(.62f+.25f*rnd.nextFloat());
                g.setColor(blend(s.hair,Color.WHITE,.25f*rnd.nextFloat()));
                g.draw(new QuadCurve2D.Float(cx+(float)Math.cos(a)*r0,cy-fh*.1f+(float)Math.sin(a)*r0*1.3f,
                    cx+(float)Math.cos(a+.15)*r1*.9f,cy+(float)Math.sin(a+.15)*r1*1.2f,
                    cx+(float)Math.cos(a+.3)*r1,cy+fh*.2f+(float)Math.sin(a+.3)*r1*1.4f));
            }
        }
        // eyes, brows
        for(int side=-1;side<=1;side+=2){
            float ex=cx+side*fw*.2f,ey=cy-fh*.06f;
            g.setColor(new Color(0xF4F1EC));g.fill(new Ellipse2D.Float(ex-fw*.09f,ey-fh*.03f,fw*.18f,fh*.06f));
            g.setColor(new Color(0x4A6A3A));g.fill(new Ellipse2D.Float(ex-fw*.04f,ey-fh*.03f,fw*.08f,fh*.06f));
            g.setColor(new Color(0x101010));g.fill(new Ellipse2D.Float(ex-fw*.02f,ey-fh*.015f,fw*.04f,fh*.03f));
            g.setColor(Color.WHITE);g.fill(new Ellipse2D.Float(ex-fw*.01f,ey-fh*.02f,fw*.015f,fh*.012f)); // catchlight
            g.setColor(blend(s.hair,Color.BLACK,.2f));g.setStroke(new BasicStroke(fw*.028f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            g.draw(new QuadCurve2D.Float(ex-fw*.11f,ey-fh*.07f,ex,ey-fh*.11f,ex+fw*.11f,ey-fh*.075f));
            if(s.glasses){
                g.setColor(new Color(0x1B1B1B));g.setStroke(new BasicStroke(fw*.018f));
                g.draw(new Ellipse2D.Float(ex-fw*.13f,ey-fh*.07f,fw*.26f,fh*.14f));
                g.setColor(new Color(255,255,255,90));g.fill(new Ellipse2D.Float(ex-fw*.08f,ey-fh*.06f,fw*.06f,fh*.03f)); // lens glare
            }
        }
        if(s.glasses){
            g.setColor(new Color(0x1B1B1B));g.setStroke(new BasicStroke(fw*.018f));
            g.draw(new QuadCurve2D.Float(cx-fw*.07f,cy-fh*.06f,cx,cy-fh*.09f,cx+fw*.07f,cy-fh*.06f));
            g.drawLine(Math.round(cx-fw*.33f),Math.round(cy-fh*.07f),Math.round(cx-fw*.5f),Math.round(cy-fh*.09f));
            g.drawLine(Math.round(cx+fw*.33f),Math.round(cy-fh*.07f),Math.round(cx+fw*.5f),Math.round(cy-fh*.09f));
        }
        // nose (shadow side + nostrils)
        g.setColor(blend(s.skin,Color.BLACK,.3f));g.setStroke(new BasicStroke(fw*.02f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.draw(new QuadCurve2D.Float(cx+fw*.03f,cy,cx+fw*.07f,cy+fh*.12f,cx+fw*.01f,cy+fh*.14f));
        g.fill(new Ellipse2D.Float(cx-fw*.06f,cy+fh*.13f,fw*.04f,fh*.02f));g.fill(new Ellipse2D.Float(cx+fw*.02f,cy+fh*.13f,fw*.04f,fh*.02f));
        // mouth
        g.setColor(new Color(0x8E3B3B));
        Path2D m=new Path2D.Float();m.moveTo(cx-fw*.14f,cy+fh*.25f);m.quadTo(cx,cy+fh*.21f,cx+fw*.14f,cy+fh*.25f);m.quadTo(cx,cy+fh*.31f,cx-fw*.14f,cy+fh*.25f);g.fill(m);
        g.setColor(blend(new Color(0x8E3B3B),Color.BLACK,.5f));g.setStroke(new BasicStroke(fw*.008f));g.draw(new QuadCurve2D.Float(cx-fw*.14f,cy+fh*.25f,cx,cy+fh*.26f,cx+fw*.14f,cy+fh*.25f));
        // jawline shadow
        g.setColor(new Color(0,0,0,40));g.setStroke(new BasicStroke(fw*.03f));
        g.draw(new QuadCurve2D.Float(cx-fw*.45f,cy+fh*.15f,cx,cy+fh*.62f,cx+fw*.45f,cy+fh*.15f));
        g.dispose();
        return img;
    }
    static Color blend(Color a,Color b,float t){
        return new Color(Math.round(a.getRed()+(b.getRed()-a.getRed())*t),Math.round(a.getGreen()+(b.getGreen()-a.getGreen())*t),Math.round(a.getBlue()+(b.getBlue()-a.getBlue())*t));
    }

    /** Snip proxies in normalized artwork coordinates (the app clips final vectors to the mask). */
    public static List<float[]> snip(String kind){
        List<float[]> out=new ArrayList<>();
        if("circle".equals(kind)){
            int n=96;float[] p=new float[n*2];
            for(int i=0;i<n;i++){double a=2*Math.PI*i/n;p[2*i]=(float)(.5+.46*Math.cos(a));p[2*i+1]=(float)(.5+.46*Math.sin(a));}
            out.add(p);
        }else if("irregular".equals(kind)){
            int n=64;float[] p=new float[n*2];
            // Radius .27-.45 and a 1.05 vertical stretch keep every point inside [0,1] (SnipMask rule).
            for(int i=0;i<n;i++){double a=2*Math.PI*i/n;double r=.36+.06*Math.sin(3*a)+.03*Math.cos(7*a);
                p[2*i]=(float)(.5+r*Math.cos(a));p[2*i+1]=(float)(.5+r*1.05*Math.sin(a));}
            out.add(p);
        }
        return out;
    }
}
