package com.halftonestudio;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.InputSource;

/**
 * Strict, Android-independent converter for the application's FINAL cut SVG subset.
 * The input is generated artwork, not a photograph. Never trace bitmap pixels.
 * Output is true AutoCAD R13 (AC1012) ASCII DXF: CIRCLE or legacy 2D
 * POLYLINE/VERTEX/SEQEND, deliberately avoiding R2000-only dependencies.
 *
 * Unsupported SVG features fail the export rather than silently lose cuts.
 * SVG coordinates, nested transforms and viewBox are resolved into millimeters.
 * Y is inverted consistently for CAD's upward-positive coordinate system.
 * Import scale is NOT guaranteed by Silhouette Studio; measure after opening.
 */
public final class SvgDxfR13Exporter {
    public static final double CURVE_TOLERANCE_MM = 0.02;
    private static final int MAX_ENTITIES=25000, MAX_POINTS=750000, MAX_TOKENS=1600000;
    private static final double MAX_SIZE_MM=10000.0, EPS=1e-7;
    private static final Pattern NUM=Pattern.compile("[-+]?(?:\\d*\\.\\d+|\\d+\\.?\\d*)(?:[eE][-+]?\\d+)?");
    private static final Pattern TOKEN=Pattern.compile("[A-Za-z]|[-+]?(?:\\d*\\.\\d+|\\d+\\.?\\d*)(?:[eE][-+]?\\d+)?");

    public static final class Result {
        public final String dxf;
        public final double widthMm, heightMm;
        public final int entities, closedContours, openPaths, vertices;
        /** Physical bounds of actual cut entities, NOT the uncut SVG artboard. */
        public final double minXmm,minYmm,maxXmm,maxYmm;
        Result(String d,double w,double h,int e,int c,int o,int v,double minX,double minY,double maxX,double maxY){
            dxf=d;widthMm=w;heightMm=h;entities=e;closedContours=c;openPaths=o;vertices=v;
            minXmm=minX;minYmm=minY;maxXmm=maxX;maxYmm=maxY;
        }
    }
    private SvgDxfR13Exporter(){}
    private static final class Affine {
        final double a,b,c,d,e,f;
        Affine(double a,double b,double c,double d,double e,double f){this.a=a;this.b=b;this.c=c;this.d=d;this.e=e;this.f=f;}
        static Affine identity(){return new Affine(1,0,0,1,0,0);}
        static Affine join(Affine p,Affine q){return new Affine(p.a*q.a+p.c*q.b,p.b*q.a+p.d*q.b,
            p.a*q.c+p.c*q.d,p.b*q.c+p.d*q.d,p.a*q.e+p.c*q.f+p.e,p.b*q.e+p.d*q.f+p.f);}
        double[] at(double x,double y){return new double[]{a*x+c*y+e,b*x+d*y+f};}
    }
    private static final class State {
        final Affine t;final boolean fill,hidden;
        State(Affine t,boolean fill,boolean hidden){this.t=t;this.fill=fill;this.hidden=hidden;}
    }
    private static final class Shape {
        final ArrayList<Double> coords=new ArrayList<>();
        boolean closed; // a separate contour; interior rings remain independently closed
        void add(double x,double y){
            if(coords.size()/2>=MAX_POINTS)throw new IllegalArgumentException("SVG contour exceeds point budget");
            int n=coords.size();if(n>=2&&Math.abs(coords.get(n-2)-x)<EPS&&Math.abs(coords.get(n-1)-y)<EPS)return;
            coords.add(x);coords.add(y);
        }
        int count(){return coords.size()/2;}
        void close(){closed=true;int n=coords.size();if(n>=4&&Math.abs(coords.get(0)-coords.get(n-2))<EPS&&
            Math.abs(coords.get(1)-coords.get(n-1))<EPS){coords.remove(n-1);coords.remove(n-2);}}
    }
    private static final class Circle {
        double x,y,r;
        Circle(double x,double y,double r){this.x=x;this.y=y;this.r=r;}
    }
    private static final class Geometry extends DefaultHandler {
        final ArrayDeque<State> stack=new ArrayDeque<>();
        final List<Object> shapes=new ArrayList<>();
        double width,height;
        // Physical origin of the root viewBox and root viewport mapping.
        // All sheets in one ZIP must use an identical placement frame.
        double rootA,rootB,rootC,rootD,rootE,rootF;
        int points,closed,open;
        boolean started;
        @Override public void startElement(String uri,String local,String qName,Attributes a)throws SAXException {
            try {start(local.isEmpty()?qName:local,a);}catch(IllegalArgumentException e){throw new SAXException(e.getMessage(),e);}
        }
        void start(String tag,Attributes a){
            if(stack.size()>32)throw new IllegalArgumentException("SVG nested too deeply");
            final boolean root=stack.isEmpty();
            if(root&&!"svg".equals(tag))throw new IllegalArgumentException("Expected SVG root");
            if(root){
                width=dimension(req(a,"width"));height=dimension(req(a,"height"));
                if(!(width>0&&height>0&&width<=MAX_SIZE_MM&&height<=MAX_SIZE_MM))throw new IllegalArgumentException("Invalid SVG dimensions");
                double[] vb=nums(req(a,"viewBox"),4,4);
                if(!(vb[2]>0&&vb[3]>0))throw new IllegalArgumentException("Invalid SVG viewBox");
                // App-generated cut artboards use 1:1 physical aspect ratios.
                // Reject abnormal root viewBox scaling rather than silently distort cuts.
                double sx=width/vb[2],sy=height/vb[3];
                if(Math.abs(sx-sy)>Math.max(sx,sy)*0.001)
                    throw new IllegalArgumentException("SVG artboard aspect ratio disagrees with physical dimensions");
                Affine viewport=new Affine(width/vb[2],0,0,height/vb[3],-vb[0]*width/vb[2],-vb[1]*height/vb[3]);
                Affine rootMap=Affine.join(viewport,parseTransforms(a.getValue("transform")));
                rootA=rootMap.a;rootB=rootMap.b;rootC=rootMap.c;
                rootD=rootMap.d;rootE=rootMap.e;rootF=rootMap.f;
                String rootFill=a.getValue("fill"),rootStyle=a.getValue("style");
                if(rootStyle!=null)for(String rule:rootStyle.split(";")){
                    String[] kv=rule.split(":",2);if(kv.length==2&&"fill".equals(kv[0].trim()))rootFill=kv[1].trim();
                }
                stack.push(new State(rootMap,!"none".equalsIgnoreCase(rootFill),false));started=true;return;
            }
            State parent=stack.peek();
            boolean hidden=parent.hidden||"none".equals(a.getValue("display"))||"hidden".equals(a.getValue("visibility"));
            String style=a.getValue("style");if(style!=null){for(String rule:style.split(";")){
                String[] kv=rule.split(":",2);if(kv.length==2&&"display".equals(kv[0].trim())&&"none".equals(kv[1].trim()))hidden=true;
            }}
            String fill=a.getValue("fill");if(fill==null&&style!=null)for(String rule:style.split(";")){
                String[] kv=rule.split(":",2);if(kv.length==2&&"fill".equals(kv[0].trim()))fill=kv[1].trim();
            }
            State s=new State(Affine.join(parent.t,parseTransforms(a.getValue("transform"))),
                fill==null?parent.fill:!"none".equalsIgnoreCase(fill),hidden);
            stack.push(s);
            if(hidden)return;
            switch(tag){
                case "g":case "title":case "desc":case "metadata":return;
                case "path":parsePath(req(a,"d"),s);return;
                case "circle":{
                    double cx=attr(a,"cx",0),cy=attr(a,"cy",0),r=attr(a,"r",0);
                    if(!(r>0))throw new IllegalArgumentException("Nonpositive circle radius");
                    double[] p=s.t.at(cx,cy),px=s.t.at(cx+r,cy),py=s.t.at(cx,cy+r);
                    double ux=Math.hypot(px[0]-p[0],px[1]-p[1]);double uy=Math.hypot(py[0]-p[0],py[1]-p[1]);
                    double dot=(px[0]-p[0])*(py[0]-p[0])+(px[1]-p[1])*(py[1]-p[1]);
                    if(Math.abs(ux-uy)<1e-6&&Math.abs(dot)<1e-6){addCircle(p[0],height-p[1],ux);}
                    else ellipse(cx,cy,r,r,s);
                    return;
                }
                case "ellipse":ellipse(attr(a,"cx",0),attr(a,"cy",0),attr(a,"rx",0),attr(a,"ry",0),s);return;
                case "rect":{
                    double x=attr(a,"x",0),y=attr(a,"y",0),w=attr(a,"width",0),h=attr(a,"height",0);
                    if(w<=0||h<=0)throw new IllegalArgumentException("Empty rect");
                    if(attr(a,"rx",0)>0||attr(a,"ry",0)>0)throw new IllegalArgumentException("Rounded rect requires path outlines");
                    Shape p=new Shape();point(p,x,y,s);point(p,x+w,y,s);point(p,x+w,y+h,s);point(p,x,y+h,s);p.close();addShape(p);return;
                }
                case "line":{
                    Shape p=new Shape();point(p,attr(a,"x1",0),attr(a,"y1",0),s);point(p,attr(a,"x2",0),attr(a,"y2",0),s);addShape(p);return;
                }
                case "polygon":case "polyline":{
                    double[] xy=nums(req(a,"points"),4,MAX_TOKENS);
                    if(xy.length%2!=0)throw new IllegalArgumentException("Odd SVG points");
                    Shape p=new Shape();for(int i=0;i<xy.length;i+=2)point(p,xy[i],xy[i+1],s);
                    if("polygon".equals(tag)||s.fill)p.close();addShape(p);return;
                }
                default:throw new IllegalArgumentException("Unsupported cut SVG tag: "+tag);
            }
        }
        @Override public void endElement(String uri,String local,String qName){if(!stack.isEmpty())stack.pop();}
        void point(Shape p,double x,double y,State s){double[] out=s.t.at(x,y);p.add(out[0],height-out[1]);}
        void addCircle(double x,double y,double r){checkCoord(x,y);checkCoord(r,0);if(shapes.size()>=MAX_ENTITIES)throw new IllegalArgumentException("DXF entity limit exceeded");shapes.add(new Circle(x,y,r));closed++;}
        void addShape(Shape p){
            if(p.count()< (p.closed?3:2))throw new IllegalArgumentException("Degenerate cut contour");
            if(shapes.size()>=MAX_ENTITIES||points+p.count()>MAX_POINTS)throw new IllegalArgumentException("DXF detail exceeds safe export budget");
            for(int i=0;i<p.coords.size();i+=2)checkCoord(p.coords.get(i),p.coords.get(i+1));
            shapes.add(p);points+=p.count();if(p.closed)closed++;else open++;
        }
        void ellipse(double cx,double cy,double rx,double ry,State s){
            if(!(rx>0&&ry>0))throw new IllegalArgumentException("Empty ellipse");
            double[] a=s.t.at(cx+rx,cy),b=s.t.at(cx,cy+ry),c=s.t.at(cx,cy);
            double radius=PrecisionCurveSampler.ellipseRadiusBound(a[0]-c[0],a[1]-c[1],b[0]-c[0],b[1]-c[1]);
            int steps=Math.max(24,(int)Math.ceil(Math.PI/Math.acos(Math.max(-1,Math.min(1,1-CURVE_TOLERANCE_MM/Math.max(radius,CURVE_TOLERANCE_MM))))));
            if(steps>16384)throw new IllegalArgumentException("Ellipse exceeds curve accuracy budget");Shape p=new Shape();for(int i=0;i<steps;i++){
                double angle=2*Math.PI*i/steps;point(p,cx+rx*Math.cos(angle),cy+ry*Math.sin(angle),s);
            }p.close();addShape(p);
        }
        void parsePath(String path,State s){
            ArrayList<String> tok=tokens(path);int i=0;char command=0,previous=0;
            double x=0,y=0,startX=0,startY=0,lastCX=0,lastCY=0,lastQX=0,lastQY=0;
            Shape current=null;
            while(i<tok.size()){
                String v=tok.get(i);
                if(letter(v)){command=v.charAt(0);i++;}
                else if(command==0)throw new IllegalArgumentException("Path command missing");
                char u=Character.toUpperCase(command);boolean relative=Character.isLowerCase(command);
                if(u=='Z'){
                    if(current==null)throw new IllegalArgumentException("Close without contour");current.close();addShape(current);current=null;
                    x=startX;y=startY;command=0;previous='Z';continue;
                }
                int needed;
                switch(u){case 'M':case 'L':case 'T':needed=2;break;case 'H':case 'V':needed=1;break;
                    case 'C':needed=6;break;case 'S':case 'Q':needed=4;break;case 'A':needed=7;break;
                    default:throw new IllegalArgumentException("Unknown SVG path command: "+command);}
                if(i+needed>tok.size())throw new IllegalArgumentException("Truncated SVG path");
                for(int k=0;k<needed;k++)if(letter(tok.get(i+k)))throw new IllegalArgumentException("Incomplete SVG path command");
                double[] vls=new double[needed];for(int k=0;k<needed;k++)vls[k]=value(tok.get(i++));
                double xx=x, yy=y;
                if(u=='M'){
                    if(current!=null){if(s.fill)current.close();addShape(current);}
                    x=relative?x+vls[0]:vls[0];y=relative?y+vls[1]:vls[1];startX=x;startY=y;
                    current=new Shape();point(current,x,y,s);previous='M';
                    command=relative?'l':'L';continue;
                }
                if(current==null)throw new IllegalArgumentException("SVG path lacks move-to");
                switch(u){
                    case 'L': x=relative?x+vls[0]:vls[0];y=relative?y+vls[1]:vls[1];point(current,x,y,s);break;
                    case 'H': x=relative?x+vls[0]:vls[0];point(current,x,y,s);break;
                    case 'V': y=relative?y+vls[0]:vls[0];point(current,x,y,s);break;
                    case 'C':{
                        double x1=(relative?xx:0)+vls[0],y1=(relative?yy:0)+vls[1];
                        double x2=(relative?xx:0)+vls[2],y2=(relative?yy:0)+vls[3];
                        x=(relative?xx:0)+vls[4];y=(relative?yy:0)+vls[5];
                        cubic(current,s,xx,yy,x1,y1,x2,y2,x,y,0);lastCX=x2;lastCY=y2;break;
                    }
                    case 'S':{
                        double x1=previous=='C'||previous=='S'?2*xx-lastCX:xx;
                        double y1=previous=='C'||previous=='S'?2*yy-lastCY:yy;
                        double x2=(relative?xx:0)+vls[0],y2=(relative?yy:0)+vls[1];
                        x=(relative?xx:0)+vls[2];y=(relative?yy:0)+vls[3];
                        cubic(current,s,xx,yy,x1,y1,x2,y2,x,y,0);lastCX=x2;lastCY=y2;break;
                    }
                    case 'Q':case 'T':{
                        double qx=u=='Q'?(relative?xx:0)+vls[0]:(previous=='Q'||previous=='T'?2*xx-lastQX:xx);
                        double qy=u=='Q'?(relative?yy:0)+vls[1]:(previous=='Q'||previous=='T'?2*yy-lastQY:yy);
                        x=u=='Q'?(relative?xx:0)+vls[2]:(relative?xx:0)+vls[0];
                        y=u=='Q'?(relative?yy:0)+vls[3]:(relative?yy:0)+vls[1];
                        cubic(current,s,xx,yy,xx+2*(qx-xx)/3,yy+2*(qy-yy)/3,x+2*(qx-x)/3,y+2*(qy-y)/3,x,y,0);
                        lastQX=qx;lastQY=qy;break;
                    }
                    case 'A':{
                        x=(relative?xx:0)+vls[5];y=(relative?yy:0)+vls[6];
                        arc(current,s,xx,yy,vls[0],vls[1],vls[2],vls[3],vls[4],x,y);break;
                    }
                    default:throw new IllegalArgumentException("Unsupported SVG command");
                }
                previous=u;
            }
            if(current!=null){if(s.fill)current.close();addShape(current);}
        }
        void cubic(Shape p,State s,double x0,double y0,double x1,double y1,double x2,double y2,double x3,double y3,int depth){
            double[] a=s.t.at(x0,y0),b=s.t.at(x1,y1),c=s.t.at(x2,y2),d=s.t.at(x3,y3);
            // The whole cubic lies inside the convex hull of its four control points.
            // Distance to the FINITE chord segment (a convex set), not its infinite
            // supporting line, bounds the maximum path-to-segment deviation.
            // Collinear overshooting controls would otherwise disappear completely.
            double flat=Math.max(distanceToSegment(b[0],b[1],a[0],a[1],d[0],d[1]),
                distanceToSegment(c[0],c[1],a[0],a[1],d[0],d[1]));
            if(flat<=CURVE_TOLERANCE_MM){point(p,x3,y3,s);return;}
            if(depth>=16)throw new IllegalArgumentException("Curve exceeds flattening accuracy budget");
            double x01=(x0+x1)/2,y01=(y0+y1)/2,x12=(x1+x2)/2,y12=(y1+y2)/2;
            double x23=(x2+x3)/2,y23=(y2+y3)/2,x012=(x01+x12)/2,y012=(y01+y12)/2;
            double x123=(x12+x23)/2,y123=(y12+y23)/2,xmid=(x012+x123)/2,ymid=(y012+y123)/2;
            cubic(p,s,x0,y0,x01,y01,x012,y012,xmid,ymid,depth+1);
            cubic(p,s,xmid,ymid,x123,y123,x23,y23,x3,y3,depth+1);
        }
        void arc(Shape p,State s,double x1,double y1,double rx,double ry,double degrees,double large,double sweep,double x2,double y2){
            if(rx==0||ry==0||(Math.abs(x1-x2)<EPS&&Math.abs(y1-y2)<EPS)){point(p,x2,y2,s);return;}
            if(large!=0&&large!=1||sweep!=0&&sweep!=1)throw new IllegalArgumentException("Invalid SVG arc flags");
            rx=Math.abs(rx);ry=Math.abs(ry);double phi=Math.toRadians(degrees),cp=Math.cos(phi),sp=Math.sin(phi);
            double dx=(x1-x2)/2,dy=(y1-y2)/2, xp=cp*dx+sp*dy,yp=-sp*dx+cp*dy;
            double lambda=xp*xp/(rx*rx)+yp*yp/(ry*ry);
            if(lambda>1){double stretch=Math.sqrt(lambda);rx*=stretch;ry*=stretch;}
            double numerator=Math.max(0,rx*rx*ry*ry-rx*rx*yp*yp-ry*ry*xp*xp);
            double denom=rx*rx*yp*yp+ry*ry*xp*xp;
            double sign=(large==sweep?-1:1)*Math.sqrt(denom<EPS?0:numerator/denom);
            double cxp=sign*rx*yp/ry,cyp=-sign*ry*xp/rx;
            double cx=cp*cxp-sp*cyp+(x1+x2)/2,cy=sp*cxp+cp*cyp+(y1+y2)/2;
            double a=Math.atan2((yp-cyp)/ry,(xp-cxp)/rx);
            double b=Math.atan2((-yp-cyp)/ry,(-xp-cxp)/rx)-a;
            if(sweep==0&&b>0)b-=2*Math.PI;
            else if(sweep==1&&b<0)b+=2*Math.PI;
            double[] center=s.t.at(cx,cy), ex=s.t.at(cx+cp*rx,cy+sp*rx),ey=s.t.at(cx-sp*ry,cy+cp*ry);
            double radius=PrecisionCurveSampler.ellipseRadiusBound(ex[0]-center[0],ex[1]-center[1],ey[0]-center[0],ey[1]-center[1]);
            double step=2*Math.acos(Math.max(-1,Math.min(1,1-CURVE_TOLERANCE_MM/Math.max(radius,CURVE_TOLERANCE_MM))));
            int n=Math.max(1,(int)Math.ceil(Math.abs(b)/Math.max(step,0.001)));
            if(n>16384)throw new IllegalArgumentException("Arc exceeds curve accuracy budget");
            for(int k=1;k<=n;k++){
                double theta=a+b*k/n;
                if(k==n)point(p,x2,y2,s);else point(p,cx+cp*rx*Math.cos(theta)-sp*ry*Math.sin(theta),cy+sp*rx*Math.cos(theta)+cp*ry*Math.sin(theta),s);
            }
        }
    }
    private static double distanceToSegment(double x,double y,double x0,double y0,double x1,double y1){
        double dx=x1-x0,dy=y1-y0,den=dx*dx+dy*dy;
        double t=den<=EPS*EPS?0:Math.max(0,Math.min(1,
            ((x-x0)*dx+(y-y0)*dy)/den));
        return Math.hypot(x-(x0+t*dx),y-(y0+t*dy));
    }
    private static ArrayList<String> tokens(String raw){
        ArrayList<String> out=new ArrayList<>();Matcher m=TOKEN.matcher(raw);int end=0;
        while(m.find()){
            if(!raw.substring(end,m.start()).trim().replace(",","").isEmpty())throw new IllegalArgumentException("Unsupported SVG path syntax");
            if(out.size()>=MAX_TOKENS)throw new IllegalArgumentException("SVG path too complex");
            out.add(m.group());end=m.end();
        }
        if(!raw.substring(end).trim().replace(",","").isEmpty())throw new IllegalArgumentException("Unsupported SVG path data");
        return out;
    }
    private static boolean letter(String s){return s.length()==1&&Character.isLetter(s.charAt(0));}
    private static double value(String s){double v=Double.parseDouble(s);if(!Double.isFinite(v)||Math.abs(v)>1e8)throw new IllegalArgumentException("Non-finite/oversized SVG value");return v;}
    private static void checkCoord(double x,double y){if(!Double.isFinite(x)||!Double.isFinite(y)||Math.abs(x)>MAX_SIZE_MM*3||Math.abs(y)>MAX_SIZE_MM*3)throw new IllegalArgumentException("Cut coordinate out of range");}
    private static String req(Attributes a,String key){String s=a.getValue(key);if(s==null||s.isEmpty())throw new IllegalArgumentException("Missing SVG "+key);return s;}
    private static double attr(Attributes a,String key,double fallback){String v=a.getValue(key);return v==null?fallback:value(v);}
    private static double dimension(String s){if(!s.endsWith("mm"))throw new IllegalArgumentException("DXF exporter requires physical SVG dimensions in mm");return value(s.substring(0,s.length()-2));}
    private static double[] nums(String s,int minimum,int maximum){
        Matcher m=NUM.matcher(s);ArrayList<Double> out=new ArrayList<>();int pos=0;
        while(m.find()){if(!s.substring(pos,m.start()).trim().replace(",","").isEmpty())throw new IllegalArgumentException("Invalid coordinate list");
            if(out.size()>=maximum)throw new IllegalArgumentException("Coordinate list too long");out.add(value(m.group()));pos=m.end();}
        if(!s.substring(pos).trim().replace(",","").isEmpty()||out.size()<minimum)throw new IllegalArgumentException("Invalid coordinates");
        double[] v=new double[out.size()];for(int i=0;i<v.length;i++)v[i]=out.get(i);return v;
    }
    private static Affine parseTransforms(String raw){
        if(raw==null||raw.trim().isEmpty())return Affine.identity();
        Affine out=Affine.identity();Matcher m=Pattern.compile("([A-Za-z]+)\\s*\\(([^)]*)\\)").matcher(raw);int pos=0;
        while(m.find()){
            if(!raw.substring(pos,m.start()).trim().isEmpty())throw new IllegalArgumentException("Invalid SVG transform");
            double[] v=nums(m.group(2),1,6);Affine next;
            switch(m.group(1)){
                case "translate":if(v.length>2)throw new IllegalArgumentException("Bad translate");next=new Affine(1,0,0,1,v[0],v.length==2?v[1]:0);break;
                case "scale":if(v.length>2)throw new IllegalArgumentException("Bad scale");next=new Affine(v[0],0,0,v.length==2?v[1]:v[0],0,0);break;
                case "matrix":if(v.length!=6)throw new IllegalArgumentException("Bad matrix");next=new Affine(v[0],v[1],v[2],v[3],v[4],v[5]);break;
                case "rotate":{
                    if(v.length!=1&&v.length!=3)throw new IllegalArgumentException("Bad rotate");
                    double c=Math.cos(Math.toRadians(v[0])),s=Math.sin(Math.toRadians(v[0]));next=new Affine(c,s,-s,c,0,0);
                    if(v.length==3)next=Affine.join(Affine.join(new Affine(1,0,0,1,v[1],v[2]),next),new Affine(1,0,0,1,-v[1],-v[2]));break;
                }
                default:throw new IllegalArgumentException("Unsupported transform: "+m.group(1));
            }
            out=Affine.join(out,next);pos=m.end();
        }
        if(!raw.substring(pos).trim().isEmpty())throw new IllegalArgumentException("Invalid SVG transform tail");return out;
    }
    private static void pair(StringBuilder b,int code,Object value){b.append(code).append("\r\n").append(value).append("\r\n");}
    private static String fmt(double v){checkCoord(v,0);if(Math.abs(v)<0.0000005)v=0;return String.format(Locale.US,"%.6f",v);}
    private static Geometry parseGeometry(String svg) throws IOException {
        if(svg==null||svg.length()>48_000_000)throw new IOException("Invalid or excessive source SVG");
        // Generated cut SVG contains no DTD/processing instruction. Reject these
        // *before* the parser, even on Android SAX providers lacking Xerces flags.
        if(svg.contains("<!")||svg.contains("<?"))throw new IOException("Unsafe declaration in cut SVG");
        Geometry g=new Geometry();
        try {
            SAXParserFactory f=SAXParserFactory.newInstance();f.setNamespaceAware(true);
            // Android vendors vary in supported SAX feature URIs. With all
            // declarations rejected above, unsupported flags cannot disable DXF.
            try{f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);}
            catch(Exception unavailable){} // DTD has already been rejected
            try{f.setFeature("http://xml.org/sax/features/external-general-entities",false);}
            catch(Exception unavailable){}
            try{f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);}
            catch(Exception unavailable){}
            try{f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);}
            catch(Exception unavailable){}
            f.newSAXParser().parse(new InputSource(new StringReader(svg)),g);
        }catch(Exception ex){throw new IOException("Cannot convert cut SVG to DXF: "+ex.getMessage(),ex);}
        if(!g.started||g.shapes.isEmpty())throw new IOException("SVG has no exportable vector contours");
        return g;
    }

    /** Physical, flattened final SVG geometry for the shared safety oracle.
     * This uses the EXACT same hardened parser and transforms as DXF output.
     * Coordinates are millimeters in the transformed CAD frame (Y reflected).
     */
    static final class SafetyGeometry {
        final double widthMm,heightMm;
        final double[] rootFrame;
        final List<float[]> closedLoops=new ArrayList<>();
        final List<float[]> openStrokes=new ArrayList<>();
        final List<double[]> circles=new ArrayList<>();
        int openPaths,vertices;
        SafetyGeometry(Geometry g){widthMm=g.width;heightMm=g.height;rootFrame=new double[]{g.rootA,g.rootB,g.rootC,g.rootD,g.rootE,g.rootF};}
    }
    static SafetyGeometry safetyGeometry(String svg) throws IOException {
        Geometry g=parseGeometry(svg);
        SafetyGeometry result=new SafetyGeometry(g);
        for(Object object:g.shapes){
            if(object instanceof Circle){
                Circle circle=(Circle)object;
                result.circles.add(new double[]{circle.x,circle.y,circle.r});
            } else {
                Shape path=(Shape)object;
                if(path.count()>100000)throw new IOException("Cut safety path exceeds vertex budget");
                float[] xy=new float[path.coords.size()];
                for(int i=0;i<xy.length;i++){
                    double value=path.coords.get(i);
                    if(!Double.isFinite(value)||Math.abs(value)>30000.0)
                        throw new IOException("Cut safety coordinate out of bounds");
                    xy[i]=(float)value;
                }
                if(path.closed)result.closedLoops.add(xy);
                else { result.openPaths++;result.openStrokes.add(xy); }
                result.vertices+=path.count();
            }
        }
        return result;
    }
    public static Result convert(String svg) throws IOException {
        Geometry g=parseGeometry(svg);
        // CAD software often discards the empty drawing border. Export the
        // true cutting geometry bounds separately so resize guidance is exact.
        double minX=Double.POSITIVE_INFINITY,minY=Double.POSITIVE_INFINITY;
        double maxX=Double.NEGATIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
        for(Object object:g.shapes){
            if(object instanceof Circle){Circle c=(Circle)object;
                minX=Math.min(minX,c.x-c.r);minY=Math.min(minY,c.y-c.r);
                maxX=Math.max(maxX,c.x+c.r);maxY=Math.max(maxY,c.y+c.r);
            }else{Shape q=(Shape)object;
                for(int i=0;i<q.coords.size();i+=2){
                    minX=Math.min(minX,q.coords.get(i));minY=Math.min(minY,q.coords.get(i+1));
                    maxX=Math.max(maxX,q.coords.get(i));maxY=Math.max(maxY,q.coords.get(i+1));
                }
            }
        }
        // R13 requires CLASSES, TABLES, BLOCK_RECORD, BLOCKS and OBJECTS as
        // well as unique handles and model-space ownership on every entity.
        // A bare HEADER/ENTITIES pair can appear valid while yielding an EMPTY
        // modelspace in standards-aware importers. Keep the structural skeleton
        // generated and checked as an independent test asset.
        StringBuilder out=new StringBuilder(Math.min(32_000_000,24000+g.points*112+g.shapes.size()*140));
        out.append(DxfR13Skeleton.PREAMBLE);
        long handle=0x100000L;
        for(Object object:g.shapes){
            if(Thread.currentThread().isInterrupted())throw new IOException("DXF conversion interrupted");
            String id=Long.toHexString(handle++).toUpperCase(Locale.ROOT);
            if(object instanceof Circle){Circle c=(Circle)object;
                pair(out,0,"CIRCLE");pair(out,5,id);pair(out,330,DxfR13Skeleton.MODEL_OWNER);
                pair(out,100,"AcDbEntity");pair(out,8,"0");pair(out,100,"AcDbCircle");
                pair(out,10,fmt(c.x));pair(out,20,fmt(c.y));pair(out,30,"0.0");pair(out,40,fmt(c.r));
            }else {Shape p=(Shape)object;
                pair(out,0,"POLYLINE");pair(out,5,id);pair(out,330,DxfR13Skeleton.MODEL_OWNER);
                pair(out,100,"AcDbEntity");pair(out,8,"0");pair(out,100,"AcDb2dPolyline");
                pair(out,66,1);pair(out,10,"0.0");pair(out,20,"0.0");pair(out,30,"0.0");pair(out,70,p.closed?1:0);
                for(int i=0;i<p.coords.size();i+=2){
                    pair(out,0,"VERTEX");pair(out,5,Long.toHexString(handle++).toUpperCase(Locale.ROOT));
                    pair(out,330,id);pair(out,100,"AcDbEntity");pair(out,8,"0");
                    pair(out,100,"AcDbVertex");pair(out,100,"AcDb2dVertex");
                    pair(out,10,fmt(p.coords.get(i)));pair(out,20,fmt(p.coords.get(i+1)));pair(out,30,"0.0");pair(out,70,0);
                }
                pair(out,0,"SEQEND");pair(out,5,Long.toHexString(handle++).toUpperCase(Locale.ROOT));
                pair(out,330,id);pair(out,100,"AcDbEntity");pair(out,8,"0");pair(out,100,"AcDbSequenceEnd");
            }
        }
        out.append(DxfR13Skeleton.EPILOGUE);
        String dxf=out.toString();
        if(dxf.length()>48_000_000)throw new IOException("DXF layer exceeds safe ZIP entry budget; reduce detail");
        return new Result(dxf,g.width,g.height,g.shapes.size(),g.closed,g.open,g.points,minX,minY,maxX,maxY);
    }
}
