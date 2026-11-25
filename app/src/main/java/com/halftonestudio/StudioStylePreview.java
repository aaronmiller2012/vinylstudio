package com.halftonestudio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import java.util.List;

/**
 * 101 deterministic UI-only vector previews, keyed by StudioThumbnailRegistry.
 * Art is drawn in 100 x 60 logical units; never runs an image-processing engine.
 * The 33 tessellation thumbnails use the same source polygons as the exported style.
 */
// Created only from code with its required collaborators; never inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
public final class StudioStylePreview extends View {
    // Each tile receives a category-appropriate, style-specific palette once.
    // The onDraw path creates no new palettes or textures.
    private final int BG,DARK;
    private final int[] C;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final RectF scratchBounds=new RectF();
    private final String key;
    private final int tessellation;
    private final List<float[]> cells;
    private final List<float[]> tessSampleA,tessSampleB;

    public StudioStylePreview(Context context,String thumbnailKey){
        super(context);
        key=thumbnailKey==null?"":thumbnailKey;
        if(!StudioThumbnailRegistry.isKnown(key))
            throw new IllegalArgumentException("Unregistered style thumbnail: "+key);
        tessellation=StudioThumbnailRegistry.tessellationVariant(key);
        BG=StudioThumbnailPalette.background(key);
        DARK=StudioThumbnailPalette.outline(key);
        C=StudioThumbnailPalette.colors(key);
        // One small, deterministic polygon list per tessellation tile. Never recompute in onDraw.
        cells=tessellation>=0?TessellationMotifEngine.tiles(tessellation,100f,60f,19f):null;
        boolean sample="multilayer.tessellation".equals(key);
        tessSampleA=sample?TessellationMotifEngine.tiles(3,100f,60f,19f):null;
        tessSampleB=sample?TessellationMotifEngine.tiles(4,100f,60f,19f):null;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    private void color(int argb){p.setColor(argb);p.setStyle(Paint.Style.FILL);p.setStrokeWidth(1f);}
    private void rect(Canvas c,float l,float t,float r,float b,int col){color(col);c.drawRect(l,t,r,b,p);}
    private void round(Canvas c,float l,float t,float r,float b,float radius,int col){color(col);c.drawRoundRect(l,t,r,b,radius,radius,p);}
    private void oval(Canvas c,float cx,float cy,float rx,float ry,int col){color(col);c.drawOval(cx-rx,cy-ry,cx+rx,cy+ry,p);}
    private void polygon(Canvas c,int col,float... points){
        if(points.length<6)return;path.reset();path.moveTo(points[0],points[1]);
        for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);
        path.close();color(col);c.drawPath(path,p);
    }
    private void trace(Canvas c,int col,float w,boolean closed,float... coords){
        if(coords.length<4)return;path.reset();path.moveTo(coords[0],coords[1]);
        for(int i=2;i<coords.length;i+=2)path.lineTo(coords[i],coords[i+1]);
        if(closed)path.close();p.setColor(col);p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
    }
    private void cubic(Canvas c,int col,float w,float x,float y,float... bezier){
        path.reset();path.moveTo(x,y);
        for(int i=0;i+5<bezier.length;i+=6)
            path.cubicTo(bezier[i],bezier[i+1],bezier[i+2],bezier[i+3],bezier[i+4],bezier[i+5]);
        p.setColor(col);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(w);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
    }
    private void regular(Canvas c,int col,float cx,float cy,float rx,float ry,int sides,float spin){
        path.reset();
        for(int i=0;i<sides;i++){
            double a=spin+2*Math.PI*i/sides;float x=cx+rx*(float)Math.cos(a),y=cy+ry*(float)Math.sin(a);
            if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
        }path.close();color(col);c.drawPath(path,p);
    }
    private void star(Canvas c,int col,float cx,float cy,float r,int arms){
        path.reset();for(int i=0;i<arms*2;i++){
            double a=-Math.PI/2+i*Math.PI/arms;float d=i%2==0?r:r*.43f;
            float x=cx+d*(float)Math.cos(a),y=cy+d*(float)Math.sin(a);
            if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
        }path.close();color(col);c.drawPath(path,p);
    }
    private void drawTiling(Canvas c,int variant){
        List<float[]> tiles=variant==tessellation&&cells!=null?cells:
            variant==3&&tessSampleA!=null?tessSampleA:
            variant==4&&tessSampleB!=null?tessSampleB:null;
        if(tiles==null)throw new IllegalStateException("Tessellation geometry was not prepared: "+variant);
        int i=0;
        for(float[] xy:tiles){
            if(xy.length<6)continue;
            float cx=0f,cy=0f;
            for(int v=0;v<xy.length;v+=2){cx+=xy[v];cy+=xy[v+1];}
            cx/=xy.length/2;cy/=xy.length/2;
            // Reject exterior tiles only; canvas clips the rest.
            if(cx< -25||cx>125||cy< -25||cy>85)continue;
            path.reset();path.moveTo(xy[0],xy[1]);
            for(int v=2;v<xy.length;v+=2)path.lineTo(xy[v],xy[v+1]);path.close();
            color(C[(i+Math.abs(Math.round(cx*.03f+cy*.08f)))%C.length]);c.drawPath(path,p);
            p.setColor(DARK);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.15f);
            c.drawPath(path,p);p.setStyle(Paint.Style.FILL);i++;
        }
    }
    private void mosaic(Canvas c,int kind){
        switch(kind){
            case 0: // tonal: broad smooth stacked relief plates
                for(int i=0;i<5;i++){
                    float y=-2+i*9;path.reset();path.moveTo(-5,y);
                    path.cubicTo(21,y-8,28,y+21,51,y+10);
                    path.cubicTo(67,y-2,85,y-8,105,y+7);
                    path.lineTo(105,64);path.lineTo(-5,64);path.close();color(C[4-i]);c.drawPath(path,p);
                }break;
            case 1: // Voronoi: visibly unequal vertices and cells
                polygon(c,C[0],0,0,30,0,40,16,26,29,3,23);
                polygon(c,C[1],30,0,67,0,61,23,40,16);
                polygon(c,C[2],67,0,100,0,100,32,77,25,61,23);
                polygon(c,C[3],0,23,26,29,35,55,0,60);
                polygon(c,C[4],26,29,40,16,61,23,58,43,35,55);
                polygon(c,C[0],61,23,77,25,100,32,100,60,58,60,58,43);
                trace(c,DARK,1.8f,false,0,0,30,0,40,16,26,29,3,23);
                break;
            case 2: // regular small photo grid
                for(int y=0;y<4;y++)for(int x=0;x<6;x++)
                    rect(c,x*16.9f,y*15.3f,x*16.9f+15.8f,y*15.3f+14.3f,C[(x+2*y)%5]);
                break;
            case 3: // nested colored motifs, large enough to see all levels
                for(int y=0;y<2;y++)for(int x=0;x<3;x++){
                    float l=x*34+1,t=y*30+1;
                    rect(c,l,t,l+31,t+28,C[(x+y)%5]);
                    round(c,l+5,t+4,l+26,t+24,5,C[(x+y+2)%5]);
                    oval(c,l+15.5f,t+14,5,7,C[(x+y+3)%5]);
                }break;
            case 4: // offset: brick half-row shift
                for(int y=0;y<4;y++)for(int x=-1;x<5;x++){
                    float l=x*29+(y%2==0?0:14.5f);
                    rect(c,l+0.6f,y*15+.6f,l+28.4f,y*15+14.4f,C[Math.floorMod(x+y,5)]);
                }break;
            case 5: // crimped physical bottle caps with concentric metal rim
                for(int y=0;y<3;y++)for(int x=0;x<5;x++){
                    float cx=5+x*22+(y%2)*11,cy=9+y*21;
                    regular(c,C[Math.floorMod(x+2*y,5)],cx,cy,9.5f,9.5f,16,(float)Math.PI/16);
                    p.setColor(0xfff2d5c2);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.6f);
                    c.drawCircle(cx,cy,6.8f,p);p.setStyle(Paint.Style.FILL);
                    oval(c,cx-2,cy-2,1.5f,1,0xfff2d5c2);
                }break;
            case 6: // shared-boundary fish scales, pointed junctions, large rounded lobes
                for(int r=-1;r<5;r++)for(int col=-1;col<7;col++){
                    float pitch=20f,height=20f,shift=(r&1)==0?0:10;
                    float left=col*pitch+shift,top=r*height;
                    path.reset();path.moveTo(left+1,top+1);
                    path.cubicTo(left+2,top+9,left+4,top+15,left+10,top+18.6f);
                    path.cubicTo(left+16,top+15,left+18,top+9,left+19,top+1);
                    path.cubicTo(left+17,top-2,left+13,top-3,left+10,top-6);
                    path.cubicTo(left+7,top-3,left+3,top-2,left+1,top+1);
                    path.close();color(C[Math.floorMod(col+r,5)]);c.drawPath(path,p);
                    p.setColor(DARK);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1f);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
                }break;
            case 7: // dynamic weighted stipple from large to tiny
                for(int x=0;x<10;x++)for(int y=0;y<6;y++){
                    float r=4.8f-x*.41f;float xx=4+x*10+(y%2)*1.3f;
                    if((x*3+y*5)%10>x+3)continue;
                    oval(c,xx,5+y*10,r,r,C[(x+y)%5]);
                }break;
            case 8: // triangulated asymmetric face-like light-dark facet pattern
                float[] xs={0,18,42,65,100}, ys={0,18,37,60};
                for(int y=0;y<3;y++)for(int x=0;x<4;x++){
                    float l=xs[x],t=ys[y],right=xs[x+1],bottom=ys[y+1];
                    polygon(c,C[(x+y)%5],l,t,right,t,(l+right)*.45f,bottom);
                    polygon(c,C[(x+y+2)%5],right,t,right,bottom,(l+right)*.45f,bottom);
                }break;
            case 9: // glass: stained panes with continuous black leading
                mosaic(c,1);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3.5f);p.setColor(0xff151821);
                trace(c,0xff151821,4,false,30,0,40,16,26,29,0,23);
                trace(c,0xff151821,4,false,67,0,61,23,58,43,58,60);
                trace(c,0xff151821,4,false,100,32,77,25,61,23,40,16);
                trace(c,0xff151821,4,false,26,29,35,55,0,60);
                p.setStyle(Paint.Style.FILL);break;
            case 10: // topographic: filled nested irregular islands, NOT relief lines
                oval(c,50,30,48,28,C[3]);
                oval(c,49,30,37,23,C[4]);oval(c,49,30,28,16,C[2]);
                oval(c,49,30,17,10,C[1]);oval(c,49,30,6,5,C[0]);break;
            case 11: // sampler of two compatible repeating geometries
                c.save();c.clipRect(0,0,52,60);drawTiling(c,3);c.restore();
                c.save();c.clipRect(52,0,100,60);drawTiling(c,4);c.restore();break;
            case 12: // prismatic diagonal: continuous interwoven colored ribbons
                rect(c,0,0,100,60,C[0]);
                c.save();c.rotate(45,50,30);
                for(int row=-2;row<8;row++){
                    float y=row*15f;
                    path.reset();path.moveTo(-25,y);
                    for(int x=-25;x<130;x+=24)
                        path.cubicTo(x+6,y-8,x+18,y+8,x+24,y);
                    p.setColor(C[1+Math.floorMod(row,3)]);p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
                    c.drawPath(path,p);
                }
                for(int col=-2;col<10;col++){
                    float x=col*15f;
                    path.reset();path.moveTo(x,-40);
                    for(int y=-40;y<100;y+=24)
                        path.cubicTo(x+8,y+6,x-8,y+18,x,y+24);
                    p.setColor(C[2+Math.floorMod(col,3)]);p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(5f);c.drawPath(path,p);
                }
                p.setStyle(Paint.Style.FILL);c.restore();break;
            case 37: // Prismatic Contour Flow: diagonal ribbons bending around a face contour
                rect(c,0,0,100,60,C[0]);
                p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
                for(int row=-4;row<9;row++){
                    path.reset();
                    for(int s=0;s<=40;s++){
                        float t=-10+s*3.2f,x=t,y=row*9f+t*.55f;
                        float dx=x-50,dy=y-30,r=(float)Math.hypot(dx/1.25f,dy);
                        float push=r<26?(26-r)*.55f*(dy>=0?1:-1):0;
                        if(s==0)path.moveTo(x,y+push);else path.lineTo(x,y+push);
                    }
                    p.setColor(C[1+Math.floorMod(row,4)]);p.setStrokeWidth(4.6f);c.drawPath(path,p);
                }
                p.setStyle(Paint.Style.FILL);break;
            case 38: // Prismatic Twill: staggered diagonal steps, warp over two under two
                rect(c,0,0,100,60,C[0]);
                for(int row=0;row<8;row++)for(int col=-2;col<14;col++){
                    float x=col*8f+row*4f,y=row*7.5f;
                    boolean over=Math.floorMod(col-row,4)<2;
                    round(c,x,y+1,x+(over?14f:6f),y+6.3f,2.6f,C[over?1+Math.floorMod(row,2):3+Math.floorMod(col,2)]);
                }break;
            case 39: // Prismatic Silk: broad soft waves with fine secondary threads
                rect(c,0,0,100,60,C[0]);
                p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
                for(int row=-1;row<6;row++){
                    path.reset();
                    for(int x=-5;x<=105;x+=3){float y=row*12f+6f*(float)Math.sin(x*.075f+row*.6f);
                        if(x==-5)path.moveTo(x,y);else path.lineTo(x,y);}
                    p.setColor(C[1+Math.floorMod(row,3)]);p.setStrokeWidth(7.5f);c.drawPath(path,p);
                    path.offset(0,6f);p.setColor(C[4]);p.setStrokeWidth(1.4f);c.drawPath(path,p);
                }
                p.setStyle(Paint.Style.FILL);break;
            case 40: // Prismatic Braided Flow: paired strands in opposite phase
                rect(c,0,0,100,60,C[0]);
                p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
                c.save();c.rotate(-30,50,30);
                for(int pair=-3;pair<6;pair++)for(int k=0;k<2;k++){
                    path.reset();
                    for(int x=-30;x<=130;x+=3){float y=pair*14f+(k==0?-1:1)*3.6f*(float)Math.sin(x*.12f);
                        if(x==-30)path.moveTo(x,y);else path.lineTo(x,y);}
                    p.setColor(C[1+Math.floorMod(pair*2+k,4)]);p.setStrokeWidth(3.8f);c.drawPath(path,p);
                }
                c.restore();p.setStyle(Paint.Style.FILL);break;
            case 41: // Prismatic Chevron: rounded triangular-wave bands
                rect(c,0,0,100,60,C[0]);
                p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
                for(int row=-1;row<7;row++){
                    path.reset();
                    for(int x=-6;x<=106;x+=2){
                        float y=row*9f+7f*(float)(Math.asin(.91*Math.sin(x*.16))/Math.asin(.91));
                        if(x==-6)path.moveTo(x,y);else path.lineTo(x,y);
                    }
                    p.setColor(C[1+Math.floorMod(row,4)]);p.setStrokeWidth(5f);c.drawPath(path,p);
                }
                p.setStyle(Paint.Style.FILL);break;
            case 13:case 15:case 16:case 17:case 18:case 19:
                confettiTile(c,kind);break;
            case 14: // Actual staggered, intersecting ring envelopes.
                round(c,2,2,98,58,8,C[0]);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3.8f);
                for(int row=-1;row<6;row++)for(int col=-1;col<10;col++){
                    float x=col*15.4f+((row&1)==0?0:7.7f);
                    float y=row*11f+5f;
                    p.setColor(C[Math.floorMod(col+row,5)]);
                    c.save();c.rotate((row&1)==0?-16:16,x,y);
                    c.drawOval(x-9.5f,y-6.6f,x+9.5f,y+6.6f,p);
                    c.restore();
                }
                p.setStyle(Paint.Style.FILL);break;
            case 20: // Camouflage: merged-looking organic color islands
                rect(c,0,0,100,60,C[0]);
                polygon(c,C[1],-4,8,14,0,35,7,29,19,8,25,-4,19);
                polygon(c,C[2],29,3,57,-4,75,6,65,22,42,25,28,17);
                polygon(c,C[3],68,15,89,3,107,12,101,36,78,39,62,27);
                polygon(c,C[4],-5,38,16,24,42,36,34,58,10,67);
                polygon(c,C[2],37,36,61,25,78,38,70,64,43,64);
                break;
            case 21: // Leaf: a rooted midrib and branched lateral venation
                rect(c,0,0,100,60,C[0]);
                cubic(c,C[3],2.8f,50,63,40,40,63,24,51,-5);
                for(int i=0;i<9;i++){
                    float y=7+i*5.7f,x=50+4*(float)Math.sin(y*.08f);
                    float to=(i%2==0)?4:96;
                    cubic(c,C[(i+1)%5],1.6f,x,y,x+(to-x)*.30f,y-7,
                        x+(to-x)*.72f,y-6,to,y-15);
                }
                break;
            case 22: // Fishnet: shared staggered diamond nodes, diagonal cords, wrapped knots
                rect(c,0,0,100,60,C[0]);
                for(int row=-1;row<6;row++)for(int col=-1;col<10;col++){
                    float x=col*14+7+((row&1)*7),y=row*12+7;
                    int color=C[1+Math.floorMod(col+row,4)];
                    trace(c,color,2.4f,false,x,y,x-7,y+12);
                    trace(c,color,2.4f,false,x,y,x+7,y+12);
                    oval(c,x,y,2.6f,2.3f,color);
                    trace(c,C[1+Math.floorMod(col+row+1,4)],1.6f,false,
                        x-2.0f,y-1,x+2.0f,y+1);
                }
                break;
            case 23: // Lace: flower rosettes joined with diagonal filigree grounds
                rect(c,0,0,100,60,C[0]);
                for(int row=0;row<3;row++)for(int col=0;col<5;col++){
                    float x=10+col*20,y=10+row*19;
                    int color=C[(row+col)%5];
                    for(int arm=0;arm<6;arm++){
                        double a=2*Math.PI*arm/6;
                        oval(c,x+5*(float)Math.cos(a),y+5*(float)Math.sin(a),2.5f,2.5f,color);
                    }
                    oval(c,x,y,2.4f,2.4f,C[(row+col+2)%5]);
                    trace(c,C[(row+col+3)%5],1.5f,false,x-7,y-7,x+7,y+7);
                }
                break;
            case 24: // Crochet: aligned filet squares, open chain mesh and filled double crochets
                rect(c,0,0,100,60,C[0]);
                for(int row=0;row<5;row++)for(int col=0;col<10;col++){
                    float x=col*10,y=row*12;
                    int color=C[1+Math.floorMod(row+col/2,4)];
                    // Two short chain arches, shared post, and occasional filled block.
                    trace(c,color,1.7f,false,x,y+2,x+2.5f,y,x+5,y+2);
                    trace(c,color,1.7f,false,x+5,y+2,x+7.5f,y,x+10,y+2);
                    trace(c,color,2.0f,false,x,y+2,x,y+11);
                    if((row+col)%4<2){
                        trace(c,color,1.6f,false,x+3.3f,y+2,x+3.3f,y+11);
                        trace(c,color,1.6f,false,x+6.6f,y+2,x+6.6f,y+11);
                        trace(c,color,1.5f,false,x,y+11,x+10,y+11);
                    }else{
                        trace(c,color,1.5f,false,x,y+11,x+5,y+10,x+10,y+11);
                    }
                }
                break;
            case 25: // U.S.-inspired desert: long, open sand brush marks
                rect(c,0,0,100,60,C[0]);
                polygon(c,C[2],-8,9,27,-2,51,7,45,15,16,18,-6,27);
                polygon(c,C[1],43,14,73,5,112,17,102,31,69,35,39,26);
                polygon(c,C[3],-9,43,18,34,51,44,44,55,20,63,-5,56);
                polygon(c,C[4],52,43,82,35,105,49,98,64,71,59);break;
            case 26: // U.S.-inspired urban: angular grayscale hard-edged regions
                rect(c,0,0,100,60,C[0]);
                polygon(c,C[2],0,6,33,0,41,11,17,23,0,20);
                polygon(c,C[1],42,3,75,4,92,15,75,29,49,20);
                polygon(c,C[3],-6,34,24,20,47,29,38,55,4,61);
                polygon(c,C[4],45,38,71,27,108,42,101,61,62,58);break;
            case 28: // Quilling: large nested coils with tapered ribbons
                rect(c,0,0,100,60,C[0]);
                for(int k=0;k<5;k++){
                    float cx=10+k*21,cy=30+(k%2==0?-7:8);
                    for(int j=0;j<4;j++){
                        float r=3+j*2.55f;
                        p.setColor(C[(k+j)%5]);p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(1.9f+j*.12f);
                        c.drawArc(cx-r,cy-r*.85f,cx+r,cy+r*.85f,15+j*24,275,false,p);
                    }
                }p.setStyle(Paint.Style.FILL);break;
            case 29: // Marbled color pools, bright comb-like waving bands
                rect(c,0,0,100,60,C[0]);
                for(int j=0;j<8;j++){
                    path.reset();float x=8+j*12;
                    path.moveTo(x-10,-6);
                    for(int y=0;y<=70;y+=5)
                        path.lineTo(x+(float)Math.sin(y*.11+j*.75f)*7,y);
                    for(int y=70;y>=-6;y-=5)
                        path.lineTo(x+5+(float)Math.sin(y*.11+j*.75f)*7,y);
                    path.close();color(C[j%5]);c.drawPath(path,p);
                }break;
            case 30: // Marble flow with embedded paper-quill centers
                mosaic(c,29);
                for(int k=0;k<4;k++){
                    float cx=15+k*25,cy=15+(k%2)*22;
                    p.setColor(C[(k+3)%5]);p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2.1f);
                    for(int n=0;n<3;n++)c.drawArc(cx-3-n*2.6f,cy-3-n*2.6f,
                        cx+3+n*2.6f,cy+3+n*2.6f,40+n*36,270,false,p);
                    p.setStyle(Paint.Style.FILL);
                }break;
            case 31: // Pop Panels: 2x2 repeated flat-ink portrait studies.
                for(int row=0;row<2;row++)for(int col=0;col<2;col++){
                    int i=row*2+col;float x=col*50+1,y=row*30+1;
                    rect(c,x,y,x+48,y+28,C[i%5]);
                    oval(c,x+24,y+14,12,13,C[(i+1)%5]);
                    polygon(c,C[4],x+13,y+23,x+33,y+23,x+24,y+17);
                    oval(c,x+20,y+11,2.4f,2.1f,DARK);
                    oval(c,x+29,y+11,2.4f,2.1f,DARK);
                }break;
            case 32: // Poster Stencil: bold rays, 4-tone graphic subject and band.
                rect(c,1,1,99,59,C[0]);
                for(int i=0;i<12;i++){
                    float a=(float)(i*Math.PI/6);float x=50+100*(float)Math.cos(a),y=20+100*(float)Math.sin(a);
                    polygon(c,i%2==0?C[2]:C[0],50,20,x,y,x+17,y+10);
                }
                oval(c,50,29,19,23,C[1]);
                polygon(c,C[3],35,27,44,9,65,17,70,27,64,38,46,43);
                rect(c,5,46,95,57,C[3]);
                color(C[0]);p.setTextSize(10);p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                c.drawText("POSTER",32,55,p);
                break;
            case 33:case 34:case 35:case 36: // Overprint: two offset solid marks per tile
                rect(c,0,0,100,60,C[0]);
                for(int r=0;r<3;r++)for(int x=0;x<5;x++){
                    float cx=10+x*20,cy=10+r*20;
                    int low=C[(x+r)%5],high=C[(x+2*r+2)%5];
                    if(kind==33){
                        oval(c,cx-2,cy+2,7.5f,8,low);
                        oval(c,cx+3,cy-2,7,6,high);
                    }else if(kind==34){
                        round(c,cx-9,cy-4,cx+5,cy+3,3.3f,low);
                        round(c,cx-2,cy-1,cx+9,cy+6,3.0f,high);
                    }else if(kind==35){
                        polygon(c,low,cx-8,cy-1,cx-1,cy-8,cx+6,cy-1,cx-1,cy+6);
                        polygon(c,high,cx-4,cy+2,cx+3,cy-5,cx+9,cy+2,cx+3,cy+9);
                    }else{
                        oval(c,cx-3,cy,7.5f,6,low);
                        if((x+r)%2==0)round(c,cx-1,cy-8,cx+6,cy+6,3,high);
                        else polygon(c,high,cx-2,cy+4,cx+4,cy-7,cx+9,cy+4);
                    }
                }break;
            case 27: // U.S.-inspired digital: coherent multi-scale square stair steps
                rect(c,0,0,100,60,C[0]);
                for(int row=0;row<5;row++)for(int col=0;col<9;col++){
                    int bit=(row*31+col*17+(row/2)*13)%11;
                    if(bit>7)continue;
                    float x=col*12-2,y=row*12-1;
                    int color=C[(col/2+row/2)%5];
                    rect(c,x,y,x+12,y+9,color);
                    if(bit%3==0)rect(c,x+8,y+7,x+16,y+14,color);
                }break;
            default:break;
        }
    }
    /** Distinct visual vocabulary for each user-selectable confetti substyle. */
    private void confettiTile(Canvas c,int kind){
        round(c,2,2,98,58,8,C[0]);
        int mode=kind==13?0:kind==15?1:kind==16?2:kind==17?3:kind==18?4:5;
        int count=kind==19?64:49;
        for(int i=0;i<count;i++){
            long v=(i+39L+kind*103L)*0x9E3779B97F4A7C15L;
            v=(v^(v>>>30))*0xBF58476D1CE4E5B9L;
            v=(v^(v>>>27))*0x94D049BB133111EBL;
            v^=v>>>31;
            float x=3+(v&65535L)/65535f*94;
            float y=3+((v>>>16)&65535L)/65535f*54;
            if(mode==5&&i>28){x=49+(x-49)*.65f;y=34+(y-34)*.62f;}
            float size=2f+((v>>>32)&255L)/255f*2.8f;
            int tone=C[1+Math.floorMod(i+kind,4)];
            int shape=mode==0?i%4:mode==5?(i*7+3)%4:mode;
            if(shape==1)oval(c,x,y,size,size*.84f,tone);
            else if(shape==2){c.save();c.rotate((i*31)%90,x,y);
                rect(c,x-size,y-size,x+size,y+size,tone);c.restore();}
            else if(shape==3)polygon(c,tone,x-size,y-size*.55f,x+size*.18f,y-size,
                x+size,y-size*.25f,x+size*.66f,y+size*.87f,x-size*.65f,y+size);
            else{c.save();c.rotate((i*43)%120,x,y);
                round(c,x-size*1.6f,y-size*.40f,x+size*1.6f,y+size*.40f,size*.16f,tone);
                c.restore();}
        }
    }
    private void motif(Canvas c,int type){
        if(type==15){ // Natural confetti: varied scattered shapes on a solid vinyl base.
            round(c,2,2,98,58,10,C[0]);
            for(int i=0;i<55;i++){
                long state=(i+193L)*0x9E3779B97F4A7C15L;
                state=(state^(state>>>30))*0xBF58476D1CE4E5B9L;
                state=(state^(state>>>27))*0x94D049BB133111EBL;
                state^=state>>>31;
                float x=5+(state&0xFFFFL)/65535f*90;
                float y=5+((state>>>16)&0xFFFFL)/65535f*50;
                float size=1.7f+((state>>>32)&255L)/255f*3.0f;
                int shade=C[1+(int)((state>>>49)&3L)];
                int shape=i%3;
                if(shape==0)oval(c,x,y,size,size*.77f,shade);
                else if(shape==1)polygon(c,shade,x-size,y-size*.53f,x+size*.18f,y-size,
                    x+size,y-size*.20f,x+size*.72f,y+size*.83f,x-size*.66f,y+size);
                else polygon(c,shade,x-size*1.4f,y-size*.36f,x+size*1.2f,y-size*.64f,
                    x+size*1.3f,y+size*.40f,x-size*1.3f,y+size*.66f);
            }
            return;
        }
        if(type==16){ // Chainmail: alternate course tilt and visible annular holes
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3.0f);
            for(int row=0;row<4;row++)for(int col=0;col<6;col++){
                float x=7+col*17+(row%2)*8.0f,y=8+row*14;
                p.setColor(C[(col+row)%4]);
                c.save();c.rotate(row%2==0?-18:18,x,y);
                c.drawOval(x-9,y-6,x+9,y+6,p);c.restore();
            }
            p.setStyle(Paint.Style.FILL);return;
        }
        if(type==14){ // ASCII: visual density actually made from glyphs
            color(C[3]);p.setTypeface(android.graphics.Typeface.MONOSPACE);p.setTextSize(13f);
            String[] lines={"@@##%%%**+", "@#%##*+++.","##*%++*.. ","@##**+...."};
            for(int i=0;i<lines.length;i++){p.setColor(C[i%4]);c.drawText(lines[i],4,13+i*14,p);}
            return;
        }
        for(int i=0;i<(type==9||type==11?2:3);i++){
            int col=C[i%5];float cx=17+i*33,cy=type==11?30:26+i%2*7;
            switch(type){
                case 0: oval(c,cx,cy,9,9,col);break;
                case 1: rect(c,cx-9,cy-9,cx+9,cy+9,col);break;
                case 2: c.save();c.rotate(-25+i*25,cx,cy);oval(c,cx,cy,13,5,col);c.restore();break;
                case 3:polygon(c,col,cx,cy-12,cx+11,cy,cx,cy+12,cx-11,cy);break;
                case 4:regular(c,col,cx,cy,11,11,6,0);break;
                case 5:star(c,col,cx,cy,i==0?15:9,5);break;
                case 6:rect(c,cx-4,cy-13,cx+4,cy+13,col);rect(c,cx-13,cy-4,cx+13,cy+4,col);break;
                case 7:regular(c,col,cx,cy,12,11,3,-(float)Math.PI/2+i*.55f);break;
                case 8:regular(c,col,cx,cy,11,11,8,-(float)Math.PI/8);break;
                case 9:round(c,cx-14,cy-14,cx+14,cy+14,8,col);break;
                case 10: // pointed top, rounded bottom
                    path.reset();path.moveTo(cx,cy-15);path.cubicTo(cx-9,cy-2,cx-12,cy+3,cx-9,cy+9);
                    path.cubicTo(cx,cy+19,cx+13,cy+10,cx+10,cy+2);
                    path.cubicTo(cx+8,cy-4,cx+4,cy-10,cx,cy-15);path.close();color(col);c.drawPath(path,p);break;
                case 11: // rounded heart with two lobes
                    path.reset();path.moveTo(cx,cy+15);path.cubicTo(cx-15,cy+2,cx-18,cy-8,cx-9,cy-12);
                    path.cubicTo(cx-3,cy-14,cx,cy-8,cx,cy-5);
                    path.cubicTo(cx,cy-8,cx+3,cy-14,cx+9,cy-12);
                    path.cubicTo(cx+18,cy-8,cx+15,cy+2,cx,cy+15);path.close();color(col);c.drawPath(path,p);break;
                case 12:c.save();c.rotate(i==1?30:-20,cx,cy);round(c,cx-15,cy-6,cx+15,cy+6,6,col);c.restore();break;
                case 13:for(int j=0;j<4;j++)round(c,6+i*30+j*6,11+(i+j)%2*20,15+i*30+j*6,15+(i+j)%2*20,2,col);break;
                default:break;
            }
        }
    }
    private void lines(Canvas c,int type){
        if(type==8){ // Spider Web: eccentric capture rings + radial framework
            float cx=50,cy=28;
            for(int ring=1;ring<=5;ring++){
                float rx=ring*10.0f,ry=ring*5.3f;
                path.reset();for(int k=0;k<=16;k++){
                    double theta=2*Math.PI*k/16.0;
                    float x=cx+rx*(float)Math.cos(theta),y=cy+ry*(float)Math.sin(theta);
                    if(k==0)path.moveTo(x,y);else path.lineTo(x,y);
                }
                p.setColor(C[(ring-1)%5]);p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.7f);c.drawPath(path,p);
            }
            for(int k=0;k<12;k++){
                double a=2*Math.PI*k/12.0;
                trace(c,C[k%5],1.5f,false,cx,cy,
                    cx+54*(float)Math.cos(a),cy+31*(float)Math.sin(a));
            }
            p.setStyle(Paint.Style.FILL);return;
        }
        switch(type){
            case 0: // visibly changing width
                for(int i=0;i<5;i++){
                    float y=8+i*11;path.reset();path.moveTo(1,y-1);
                    path.cubicTo(22,y-1,34,y-7-i*.35f,51,y-6-i*.35f);
                    path.cubicTo(66,y-8,82,y-4,99,y-1);
                    path.lineTo(99,y+1);path.cubicTo(82,y+6,65,y+6+i*.5f,50,y+4+i*.5f);
                    path.cubicTo(30,y+2,20,y+1,1,y+1);path.close();color(C[i%5]);c.drawPath(path,p);
                }break;
            case 1:for(int i=-3;i<11;i++)trace(c,C[i<0?-i%5:i%5],2,false,i*14,-2,i*14+30,62);break;
            case 2:for(int i=0;i<7;i++)cubic(c,C[i%5],2,-3,i*8,
                    19,i*8-10,33,i*8+7,44,i*8+3,60,i*8-6,69,i*8-15,100,i*8-6);break;
            case 3:for(int i=0;i<5;i++){
                path.reset();for(int j=0;j<=72;j++){
                    float x=j*100f/72f,y=8+i*11+5*(float)Math.sin(x*.17+i*.15);
                    if(j==0)path.moveTo(x,y);else path.lineTo(x,y);
                }p.setColor(C[i]);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.2f);
                c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
            }break;
            case 4: // hatch wedge with variable start and ending lengths
                for(int i=0;i<18;i++){
                    float x=5+i*5.4f;float height=8+37*(float)Math.sin(Math.PI*i/18f);
                    trace(c,C[i%5],1.1f+i*.07f,false,x,54,x+height*.38f,54-height);
                }break;
            case 5: // two crossing, strongly visible line directions
                for(int i=-7;i<11;i++){
                    trace(c,C[0],1.7f,false,i*12,0,i*12+45,60);
                    trace(c,C[3],1.7f,false,i*12,60,i*12+45,0);
                }break;
            case 6:for(int i=0;i<6;i++){
                float y=6+i*10;path.reset();path.moveTo(1,y);
                for(int j=1;j<=8;j++)path.lineTo(j*12.6f,y+(j%2==0?0:7));
                p.setColor(C[i%5]);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.3f);
                p.setStrokeJoin(Paint.Join.MITER);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
            }break;
            case 7:for(int i=0;i<5;i++){
                float y=8+i*11;
                trace(c,C[i],2,false,2,y,17+i%3*3,y);
                trace(c,C[i],2,false,31+i%2*5,y,65-i%2*6,y);
                trace(c,C[i],2,false,78+i%3*2,y,98,y);
            }break;
            default:break;
        }
    }
    private void spiral(Canvas c){
        path.reset();for(int i=0;i<=330;i++){
            double t=i*Math.PI*9/330.0,r=1.1+i*.133;
            float x=50+(float)(r*Math.cos(t)),y=30+(float)(r*.59*Math.sin(t));
            if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
        }p.setColor(C[0]);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.3f);
        c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
    }
    private void single(Canvas c,int type){
        if(type==0){spiral(c);return;}
        path.reset();
        switch(type){
            case 1: // Hilbert level 3: proper recursive traversal, U-turn lattice
                float[] xs={6,18,18,6,6,6,18,18,30,30,30,42,42,42,30,30};
                // Generated directly from the Hilbert d -> xy mapping, 8x8 resolution.
                for(int i=0;i<64;i++){
                    int x=0,y=0,t=i;
                    for(int s=1;s<8;s*=2){int rx=(t/2)&1,ry=(t^rx)&1;
                        if(ry==0){if(rx==1){x=s-1-x;y=s-1-y;}int z=x;x=y;y=z;}
                        x+=s*rx;y+=s*ry;t/=4;}
                    float px=12+x*10.7f,py=5+y*7.1f;
                    if(i==0)path.moveTo(px,py);else path.lineTo(px,py);
                }break;
            case 2: // Gosper: 60-degree fractal generated from two rewriting systems
                String word="A";
                for(int k=0;k<2;k++){
                    StringBuilder sb=new StringBuilder();for(int n=0;n<word.length();n++){
                        char ch=word.charAt(n);
                        if(ch=='A')sb.append("A-B--B+A++AA+B-");
                        else if(ch=='B')sb.append("+A-BB--B-A++A+B");
                        else sb.append(ch);
                    }word=sb.toString();
                }
                float gx=13,gy=42,angle=-.24f;path.moveTo(gx,gy);
                for(int i=0;i<word.length();i++){
                    char ch=word.charAt(i);
                    if(ch=='+'||ch=='-')angle+=(ch=='+'?1:-1)*(float)Math.PI/3;
                    else{gx+=4.9f*Math.cos(angle);gy+=4.9f*Math.sin(angle);path.lineTo(gx,gy);}
                }break;
            case 3: // Peano: 3x3 boustrophedon route, three repeated bands
                path.moveTo(5,5);
                for(int row=0;row<9;row++){
                    for(int col=0;col<9;col++){
                        float x=5+(row%2==0?col:8-col)*11.25f,y=5+row*6.15f;
                        path.lineTo(x,y);
                    }
                }break;
            case 4: // Wunderlich: stair-step square loops
                path.moveTo(5,5);for(int row=0;row<6;row++){
                    float y=5+row*10;
                    for(int col=0;col<9;col++){
                        float x=5+col*11.1f;
                        path.lineTo(x,y);path.lineTo(x,y+4);
                        path.lineTo(x+5.5f,y+4);path.lineTo(x+5.5f,y+8);
                    }
                }break;
            case 5: // closed Moore-like square filling route
                path.moveTo(7,29);path.lineTo(7,7);path.lineTo(28,7);path.lineTo(28,21);
                path.lineTo(20,21);path.lineTo(20,14);path.lineTo(39,14);path.lineTo(39,7);
                path.lineTo(61,7);path.lineTo(61,21);path.lineTo(54,21);path.lineTo(54,14);
                path.lineTo(80,14);path.lineTo(80,7);path.lineTo(93,7);path.lineTo(93,52);
                path.lineTo(78,52);path.lineTo(78,39);path.lineTo(85,39);path.lineTo(85,47);
                path.lineTo(56,47);path.lineTo(56,54);path.lineTo(39,54);path.lineTo(39,41);
                path.lineTo(46,41);path.lineTo(46,49);path.lineTo(18,49);path.lineTo(18,39);
                path.lineTo(7,39);path.close();break;
            case 6: // side-to-side U-shaped coil, not a spiral
                path.moveTo(7,8);for(int i=0;i<6;i++){
                    float y=7+i*9.3f;
                    if(i%2==0){path.lineTo(91,y);path.cubicTo(102,y,102,y+9,91,y+9);}
                    else{path.lineTo(9,y);path.cubicTo(-2,y,-2,y+9,9,y+9);}
                }break;
            default:break;
        }
        RectF bounds=scratchBounds;path.computeBounds(bounds,true);
        c.save();
        if(bounds.width()>0.01f && bounds.height()>0.01f){
            c.translate(5f,5f);
            c.scale(90f/bounds.width(),50f/bounds.height());
            c.translate(-bounds.left,-bounds.top);
        }
        p.setColor(C[type%5]);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(type==3?1.5f:2.3f);
        p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);c.drawPath(path,p);
        c.restore();p.setStyle(Paint.Style.FILL);
    }
    private void art(Canvas c,int type){
        switch(type){
            case 0: // irregular TSP route with visible anchor locations
                trace(c,C[0],2,false,4,11,28,35,9,48,47,51,36,5,75,12,91,46,67,55,54,24,97,7);
                for(int i=0;i<9;i++){float x=5+(i*29)%92,y=8+(i*17)%45;oval(c,x,y,2.3f,2.3f,C[2]);}
                break;
            case 1: // linocut gouges sculpt curved facial features
                for(int i=0;i<8;i++){
                    float y=7+i*7;cubic(c,C[(i+1)%5],1.4f+i*.22f,-4,y,
                        23,y-17,31,y+16,47,y+2,62,y-9,79,y-11,104,y+4);
                }oval(c,68,27,2.5f,2.5f,C[3]);break;
            case 2: // paired quarter arcs in Truchet tiles
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(C[0]);
                for(int y=0;y<3;y++)for(int x=0;x<5;x++){
                    int l=x*20,t=y*20;
                    if((x+y)%2==0)c.drawArc(l-10,t-10,l+10,t+10,0,90,false,p);
                    else c.drawArc(l+10,t+10,l+30,t+30,180,90,false,p);
                }p.setStyle(Paint.Style.FILL);break;
            case 3: // layered solid terrain ridges
                for(int i=0;i<5;i++){
                    float base=24+i*9;path.reset();path.moveTo(-5,65);
                    path.lineTo(-5,base);path.cubicTo(13,base-17,23,base-12,33,base-21);
                    path.cubicTo(49,base-8,54,base-14,69,base-19);
                    path.cubicTo(78,base-20,92,base-6,106,base-11);
                    path.lineTo(106,65);path.close();color(C[i]);c.drawPath(path,p);
                }break;
            case 4: // banknote engraved eye: iris plus dense thin curved hatching
                oval(c,55,28,35,16,C[0]);oval(c,55,28,14,14,C[3]);
                oval(c,55,28,6,8,DARK);oval(c,51,25,2.5f,3,C[0]);
                for(int i=0;i<18;i++){
                    float y=4+i*3.4f;cubic(c,i%3==0?C[3]:DARK,.65f,-5,y,
                        25,y-9,38,y+4,56,y-4,75,y-11,85,y-4,105,y+3);
                }break;
            case 5: // organic marquetry: smooth inlaid regions without lead
                rect(c,0,0,100,60,C[4]);
                path.reset();path.moveTo(-5,43);path.cubicTo(14,5,33,10,44,33);
                path.cubicTo(57,58,76,50,105,12);path.lineTo(105,65);path.lineTo(-5,65);path.close();color(C[1]);c.drawPath(path,p);
                path.reset();path.moveTo(12,0);path.cubicTo(32,17,39,15,51,4);path.cubicTo(63,-6,77,12,91,33);
                path.cubicTo(71,27,60,34,51,45);path.cubicTo(40,31,29,18,12,0);path.close();color(C[0]);c.drawPath(path,p);break;
            case 6: // fingerprint whorl with genuine loop direction changes
                p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
                for(int i=0;i<11;i++){float inset=3+i*2.4f;p.setStrokeWidth(1.25f);p.setColor(C[i%5]);
                    RectF ring=scratchBounds;ring.set(10+inset,3+inset*.65f,90-inset,61-inset*.45f);c.drawArc(ring,198,-278,false,p);}
                p.setStyle(Paint.Style.FILL);break;
            case 7: // reduction woodcut: registered carved color passes
                rect(c,0,0,100,60,C[0]);
                path.reset();path.moveTo(0,52);path.cubicTo(18,18,34,8,49,33);path.cubicTo(65,55,79,12,100,7);path.lineTo(100,60);path.lineTo(0,60);path.close();color(C[3]);c.drawPath(path,p);
                for(int i=0;i<10;i++)trace(c,i%2==0?DARK:C[1],1.2f,false,-4+i*12,55,18+i*12,8);break;
            case 8: // woven portrait ribbons
                for(int i=0;i<7;i++)cubic(c,C[i%2],2.6f,-3,6+i*8,23,1+i*8,37,12+i*8,55,6+i*8,72,0+i*8,87,12+i*8,103,7+i*8);
                for(int i=0;i<9;i++)cubic(c,C[3],2.1f,5+i*12,-4,0+i*12,15,13+i*12,25,7+i*12,36,1+i*12,45,13+i*12,64+i*12,64);break;
            case 9: // flowing woodgrain knots
                p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
                for(int i=0;i<12;i++){p.setColor(C[i%5]);p.setStrokeWidth(1.25f);float rx=7+i*4.1f,ry=3+i*2.1f;c.drawOval(50-rx,30-ry,50+rx,30+ry,p);}
                p.setStyle(Paint.Style.FILL);oval(c,35,27,3,6,DARK);oval(c,70,35,2.5f,5,DARK);break;
            case 10: // torn-paper collage: deliberately jagged boundaries
                polygon(c,C[4],0,0,30,0,26,11,36,19,28,31,0,27);
                polygon(c,C[0],30,0,68,0,63,12,72,21,58,30,36,19,26,11);
                polygon(c,C[2],68,0,100,0,100,32,72,21,63,12);
                polygon(c,C[3],0,27,28,31,39,45,30,60,0,60);
                polygon(c,C[1],28,31,58,30,72,21,100,32,100,60,30,60,39,45);break;
            default:break;
        }
    }
    @Override protected void onDraw(Canvas output){
        super.onDraw(output);
        if(getWidth()==0||getHeight()==0)return;
        output.save();output.scale(getWidth()/100f,getHeight()/60f);
        output.clipRect(0,0,100,60);rect(output,0,0,100,60,BG);
        if(tessellation>=0)drawTiling(output,tessellation);
        else if(key.startsWith("multilayer.")){
            for(int i=0;i<StudioThumbnailRegistry.MULTILAYER.length;i++)
                if(key.equals(StudioThumbnailRegistry.MULTILAYER[i])){
                    mosaic(output,i>=31?i+2:i>=26?i+2:i);break;}
        }else if(key.startsWith("shape.")){
            for(int i=0;i<StudioThumbnailRegistry.SHAPES.length;i++)
                if(key.equals(StudioThumbnailRegistry.SHAPES[i])){motif(output,i);break;}
        }else if(key.startsWith("line.")){
            for(int i=0;i<StudioThumbnailRegistry.LINES.length;i++)
                if(key.equals(StudioThumbnailRegistry.LINES[i])){lines(output,i);break;}
        }else if(key.startsWith("single.")){
            for(int i=0;i<StudioThumbnailRegistry.SINGLE.length;i++)
                if(key.equals(StudioThumbnailRegistry.SINGLE[i])){single(output,i);break;}
        }else if(key.startsWith("art.")){
            for(int i=0;i<StudioThumbnailRegistry.ART.length;i++)
                if(key.equals(StudioThumbnailRegistry.ART[i])){art(output,i>=3?i+1:i);break;}
        }
        output.restore();
    }
}
