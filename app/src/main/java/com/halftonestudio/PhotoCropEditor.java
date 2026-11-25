package com.halftonestudio;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.Consumer;

/** Non-destructive crop/reframe editor. Keeps original bitmap for later reframing.
 * The visual crop frame is the actual rectangle used to create the cut source.
 */
public final class PhotoCropEditor {
    private PhotoCropEditor() {}
    public static final class CropResult {
        public final Bitmap bitmap;
        public final float[] bounds;
        public final boolean circular;
        CropResult(Bitmap b, float[] bb,boolean circular){bitmap=b;bounds=bb;this.circular=circular;}
    }
    private static int dp(Activity a,float v){return Math.round(a.getResources().getDisplayMetrics().density*v);}
    private static GradientDrawable rounded(Activity a,int color,int radius){GradientDrawable b=new GradientDrawable();b.setColor(color);b.setCornerRadius(dp(a,radius));return b;}
    private static TextView button(Activity activity,String text){TextView v=new TextView(activity);v.setText(text);v.setTextColor(Color.WHITE);v.setTextSize(14);v.setGravity(Gravity.CENTER);v.setPadding(dp(activity,14),0,dp(activity,14),0);return v;}
    public static void show(Activity activity,Bitmap original,float[] prior,float targetRatio,Consumer<CropResult> onApply,Runnable onCancel){
        if(original==null)return;
        Dialog dialog=new Dialog(activity,android.R.style.Theme_Material_NoActionBar_Fullscreen);
        LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff101319);
        dialog.setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int[] safe=PlatformCompat.safeInsets(insets);
            root.setPadding(safe[0],safe[1],safe[2],safe[3]);
            return insets;
        });
        LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(activity,12),dp(activity,6),dp(activity,12),dp(activity,6));root.addView(header,new LinearLayout.LayoutParams(-1,dp(activity,65)));
        TextView cancel=button(activity,"Cancel");header.addView(cancel,new LinearLayout.LayoutParams(-2,-1));
        TextView title=button(activity,"Crop photo");title.setTextSize(19);title.setTypeface(null,android.graphics.Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        TextView apply=button(activity,"Apply");apply.setTextColor(0xffA9C8FF);header.addView(apply,new LinearLayout.LayoutParams(-2,-1));
        final CropView crop=new CropView(activity,original,prior,targetRatio);
        root.addView(crop,new LinearLayout.LayoutParams(-1,0,1));
        TextView tip=button(activity,"Drag the center focal point to move · drag edge to resize a circle");tip.setTextSize(12);tip.setTextColor(0xffAFB9C7);root.addView(tip,new LinearLayout.LayoutParams(-1,dp(activity,37)));
        HorizontalScrollView scroll=new HorizontalScrollView(activity);scroll.setHorizontalScrollBarEnabled(false);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,dp(activity,66)));
        LinearLayout options=new LinearLayout(activity);options.setGravity(Gravity.CENTER_VERTICAL);options.setPadding(dp(activity,12),0,dp(activity,12),0);scroll.addView(options);
        String[] names=targetRatio>0.01f?new String[]{"Target ratio","Circle"}:new String[]{"Free","Square","Circle","4:5","2:3","3:4","5:7","8:10","11:14","16:9"};
        float[] ratios=targetRatio>0.01f?new float[]{targetRatio,1}:new float[]{0,1,1,4f/5,2f/3,3f/4,5f/7,8f/10,11f/14,16f/9};
        int circleChoice=targetRatio>0.01f?1:2;
        for(int i=0;i<names.length;i++){
            final int ix=i;TextView choice=button(activity,names[i]);choice.setBackground(rounded(activity,0xff303A49,15));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(activity,41));lp.rightMargin=dp(activity,8);options.addView(choice,lp);
            choice.setOnClickListener(v->{crop.setCircle(ix==circleChoice);crop.setAspect(ratios[ix]);for(int j=0;j<options.getChildCount();j++)options.getChildAt(j).setBackground(rounded(activity,j==ix?0xff45658F:0xff303A49,15));});
        }
        if(targetRatio>0.01f){TextView lock=button(activity,String.format(java.util.Locale.US,"Target artwork proportion: %.3f",targetRatio));lock.setTextColor(0xffB6D3FA);lock.setTextSize(12);root.addView(lock,new LinearLayout.LayoutParams(-1,dp(activity,31)));}
        cancel.setOnClickListener(v->{dialog.dismiss();if(onCancel!=null)onCancel.run();});
        apply.setOnClickListener(v->{CropResult result=crop.crop();dialog.dismiss();onApply.accept(result);});
        dialog.setOnCancelListener(ignored->{if(onCancel!=null)onCancel.run();});
        dialog.show();Window window=dialog.getWindow();if(window!=null){window.setLayout(-1,-1);PlatformCompat.colorSystemBars(window,0xff101319);}
    }
    private static final class CropView extends View {
        private final Bitmap image;private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final RectF normalized=new RectF(0,0,1,1);
        private final RectF frame=new RectF();
        private final RectF imageDestination=new RectF();
        private final Path cropShade=new Path();
        private float aspect; // width / height (image pixels), or 0 for free.
        private boolean circle=false;
        private float left,top,drawW,drawH,lastX,lastY;
        private int handle=-1;private boolean movedDuringGesture;
        CropView(Activity activity,Bitmap bitmap,float[] old,float ratio){super(activity);image=bitmap;aspect=ratio;if(old!=null&&old.length==4){normalized.set(old[0],old[1],old[2],old[3]);normalize();}if(ratio>0)fitAspect(ratio);setLayerType(View.LAYER_TYPE_SOFTWARE,null);setContentDescription(activity.getString(R.string.accessibility_crop_area));setFocusable(true);}
        void setAspect(float r){aspect=circle?1f:r;if(aspect>0)fitAspect(aspect);invalidate();}
        void setCircle(boolean enabled){circle=enabled;if(enabled){aspect=1f;fitAspect(1f);}invalidate();}
        private void normalize(){
            if(aspect>0){fitAspect(aspect);return;}
            float l=Math.max(0,Math.min(.93f,normalized.left)),t=Math.max(0,Math.min(.93f,normalized.top));float rt=Math.max(l+.07f,Math.min(1,normalized.right)),b=Math.max(t+.07f,Math.min(1,normalized.bottom));normalized.set(l,t,Math.min(1,rt),Math.min(1,b));
        }
        private void fitAspect(float ratio){
            float[] b=PhotoCropMath.fitAspect(new float[]{normalized.left,normalized.top,
                normalized.right,normalized.bottom},image.getWidth(),image.getHeight(),ratio);
            normalized.set(b[0],b[1],b[2],b[3]);
        }
        @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(0xff101319);float availW=getWidth()-24,availH=getHeight()-24;float scale=Math.min(availW/image.getWidth(),availH/image.getHeight());drawW=image.getWidth()*scale;drawH=image.getHeight()*scale;left=(getWidth()-drawW)/2;top=(getHeight()-drawH)/2;
            imageDestination.set(left,top,left+drawW,top+drawH);RectF dest=imageDestination;pen.setColor(Color.WHITE);pen.setStyle(Paint.Style.FILL);c.drawBitmap(image,null,dest,pen);
            frame.set(left+normalized.left*drawW,top+normalized.top*drawH,left+normalized.right*drawW,top+normalized.bottom*drawH);
            pen.setColor(0xb7000000);c.drawRect(left,top,left+drawW,frame.top,pen);c.drawRect(left,frame.bottom,left+drawW,top+drawH,pen);c.drawRect(left,frame.top,frame.left,frame.bottom,pen);c.drawRect(frame.right,frame.top,left+drawW,frame.bottom,pen);
            pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(3);pen.setColor(0xffA6CAFF);
            if(circle){
                Path outside=cropShade;outside.rewind();outside.setFillType(Path.FillType.EVEN_ODD);
                outside.addRect(left,top,left+drawW,top+drawH,Path.Direction.CW);
                outside.addOval(frame,Path.Direction.CCW);
                pen.setStyle(Paint.Style.FILL);pen.setColor(0xd0101319);c.drawPath(outside,pen);
                pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(3);pen.setColor(0xffA6CAFF);c.drawOval(frame,pen);
                float fx=frame.right,fy=frame.centerY();pen.setStyle(Paint.Style.FILL);pen.setColor(0xffA9C8FF);c.drawCircle(fx,fy,9,pen);
            }else{
                c.drawRect(frame,pen);
                pen.setColor(0x99FFFFFF);pen.setStrokeWidth(1);
                for(int i=1;i<3;i++){float x=frame.left+frame.width()*i/3,y=frame.top+frame.height()*i/3;c.drawLine(x,frame.top,x,frame.bottom,pen);c.drawLine(frame.left,y,frame.right,y,pen);}
                pen.setStrokeWidth(4);pen.setColor(Color.WHITE);float corner=22;
                for(int i=0;i<4;i++){float x=(i%2==0?frame.left:frame.right),y=(i<2?frame.top:frame.bottom);float sx=i%2==0?1:-1,sy=i<2?1:-1;c.drawLine(x,y,x+sx*corner,y,pen);c.drawLine(x,y,x,y+sy*corner,pen);}
            }
            // Focal-point handle is always visible; drag it without touching an edge.
            pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(2f);pen.setColor(Color.WHITE);
            float midX=frame.centerX(),midY=frame.centerY(),handleRadius=Math.max(8,16*getResources().getDisplayMetrics().density);
            c.drawCircle(midX,midY,handleRadius,pen);
            c.drawLine(midX-handleRadius*.65f,midY,midX+handleRadius*.65f,midY,pen);
            c.drawLine(midX,midY-handleRadius*.65f,midX,midY+handleRadius*.65f,pen);
            pen.setStyle(Paint.Style.FILL);

        }
        @Override public boolean onTouchEvent(MotionEvent e){
            int a=e.getActionMasked();
            if(a==MotionEvent.ACTION_DOWN){
                lastX=e.getX();lastY=e.getY();handle=-1;movedDuringGesture=false;
                float hit=28*getResources().getDisplayMetrics().density;
                if(Math.hypot(frame.centerX()-lastX,frame.centerY()-lastY)<hit)handle=4;
                else if(circle){
                    if(Math.hypot(frame.right-lastX,frame.centerY()-lastY)<hit)handle=5;
                    else if(frame.contains(lastX,lastY))handle=4;
                }else{
                    float best=hit;
                    for(int i=0;i<4;i++){float x=(i%2==0?frame.left:frame.right),y=(i<2?frame.top:frame.bottom);
                        float d=(float)Math.hypot(x-lastX,y-lastY);if(d<best){best=d;handle=i;}}
                    if(handle<0&&frame.contains(lastX,lastY))handle=4;
                }
                return true;
            }
            if(a==MotionEvent.ACTION_MOVE){
                if(handle<0)return true;
                if(Math.hypot(e.getX()-lastX,e.getY()-lastY)>1.5f)movedDuringGesture=true;
                float dx=(e.getX()-lastX)/Math.max(1,drawW),dy=(e.getY()-lastY)/Math.max(1,drawH);
                if(handle==4){
                    float nx=Math.max(0,Math.min(1-normalized.width(),normalized.left+dx));
                    float ny=Math.max(0,Math.min(1-normalized.height(),normalized.top+dy));
                    normalized.offsetTo(nx,ny);
                }else if(handle==5&&circle){
                    float imageRatio=image.getWidth()/(float)image.getHeight();
                    float desired=(normalized.width()+dx)*.5f;
                    float hRadius=desired*imageRatio; // keep physical diameter circular
                    float cx=normalized.centerX(),cy=normalized.centerY();
                    desired=Math.max(.04f,Math.min(desired,Math.min(cx,1-cx)));
                    hRadius=desired*imageRatio;
                    if(hRadius>Math.min(cy,1-cy)){
                        hRadius=Math.min(cy,1-cy);desired=hRadius/imageRatio;
                    }
                    normalized.set(cx-desired,cy-hRadius,cx+desired,cy+hRadius);
                }else{
                    float oldR=normalized.right,oldB=normalized.bottom,oldL=normalized.left,oldT=normalized.top;
                    float[] minimum=aspect>0?PhotoCropMath.minimumSpan(image.getWidth(),image.getHeight(),aspect):new float[]{.07f,.07f};
                    if((handle&1)==0)normalized.left=Math.max(0,Math.min(oldR-minimum[0],oldL+dx));else normalized.right=Math.min(1,Math.max(oldL+minimum[0],oldR+dx));
                    if(handle<2)normalized.top=Math.max(0,Math.min(oldB-minimum[1],oldT+dy));else normalized.bottom=Math.min(1,Math.max(oldT+minimum[1],oldB+dy));
                    if(aspect>0){float target=aspect/(image.getWidth()/(float)image.getHeight());float spanX=normalized.width(),spanY=normalized.height();
                        if(spanX/spanY>target)spanX=spanY*target;else spanY=spanX/target;
                        if(spanX>=minimum[0]&&spanY>=minimum[1]){if((handle&1)==0)normalized.left=normalized.right-spanX;else normalized.right=normalized.left+spanX;
                            if(handle<2)normalized.top=normalized.bottom-spanY;else normalized.bottom=normalized.top+spanY;}normalize();
                    }
                }
                lastX=e.getX();lastY=e.getY();invalidate();return true;
            }
            if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){handle=-1;if(a==MotionEvent.ACTION_UP&&!movedDuringGesture)performClick();return true;}
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
        CropResult crop(){
            if(aspect>0)fitAspect(aspect);
            int w=image.getWidth(),h=image.getHeight();
            int[] pixels=PhotoCropMath.pixelBounds(new float[]{normalized.left,normalized.top,
                normalized.right,normalized.bottom},w,h);
            int x=pixels[0],y=pixels[1],rw=pixels[2],rh=pixels[3];
            Bitmap result=Bitmap.createBitmap(image,x,y,rw,rh);
            if(circle){
                // Transparent corners are intentional paper-negative space, not printed ink.
                // PNG project snapshots preserve this circle on reopen.
                Bitmap cut=Bitmap.createBitmap(rw,rh,Bitmap.Config.ARGB_8888);
                Canvas output=new Canvas(cut);output.drawBitmap(result,0,0,null);
                Paint mask=new Paint(Paint.ANTI_ALIAS_FLAG);
                mask.setColor(Color.WHITE);
                mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
                output.drawCircle(rw/2f,rh/2f,Math.min(rw,rh)/2f,mask);
                mask.setXfermode(null);
                result=cut;
            }
            return new CropResult(result,PhotoCropMath.normalizedPixelBounds(pixels,w,h),circle);
        }

    }
}
