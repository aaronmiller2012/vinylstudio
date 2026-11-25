package com.halftonestudio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Immutable, Android-independent metadata for an illustrated assembly guide. */
public final class AssemblyGuidePlan {
    public static final int SCHEMA_VERSION=2;
    public enum Kind { VINYL, PEN_OR_ENGRAVE, REFERENCE }

    public static final class Layer {
        public final int physicalIndex;
        public final String name;
        public final int color;
        /** Closed cut contours for vinyl, open paths for pen/engrave, or reference paths. */
        public final int elementCount;
        public final boolean exported;
        public final Kind kind;
        public final String note;

        public Layer(int index,String label,int argb,int elements,boolean include){
            this(index,label,argb,elements,include,Kind.VINYL,"");
        }
        public Layer(int index,String label,int argb,int elements,boolean include,Kind operation,String detail){
            if(index<1)throw new IllegalArgumentException("Physical layer index must be positive");
            if(label==null||label.trim().isEmpty())throw new IllegalArgumentException("Layer name is required");
            if(elements<0)throw new IllegalArgumentException("Element count cannot be negative");
            if(operation==null)throw new IllegalArgumentException("Layer kind is required");
            physicalIndex=index;name=label.trim();color=argb;elementCount=elements;exported=include;kind=operation;
            note=detail==null?"":detail.trim();
        }
        public String countLabel(){
            String unit=kind==Kind.VINYL?"cut contour":"path";
            return elementCount+" "+unit+(elementCount==1?"":"s");
        }
        public String operationLabel(){
            return kind==Kind.VINYL?"Vinyl layer":kind==Kind.PEN_OR_ENGRAVE?"Pen / engrave operation":"Reference page";
        }
    }

    public static final class Builder {
        private final String projectName,styleName;
        private final float widthMm,heightMm;
        private final int mountingColor;
        private boolean registration;
        private boolean referenceOnly;
        private String markName="None";
        private float markSizeMm;
        private boolean layerMappingComplete=true;
        private final ArrayList<Layer> layers=new ArrayList<>();

        public Builder(String project,String style,float width,float height,int mounting){
            if(project==null||project.trim().isEmpty())throw new IllegalArgumentException("Project name is required");
            if(style==null||style.trim().isEmpty())throw new IllegalArgumentException("Style name is required");
            if(!Float.isFinite(width)||!Float.isFinite(height)||width<=0||height<=0)
                throw new IllegalArgumentException("Finished dimensions must be positive");
            projectName=project.trim();styleName=style.trim();widthMm=width;heightMm=height;mountingColor=mounting;
        }
        public Builder registration(boolean enabled,String shape,float sizeMm){
            registration=enabled;markName=shape==null||shape.trim().isEmpty()?"None":shape.trim();
            markSizeMm=Math.max(0f,sizeMm);return this;
        }
        public Builder referenceOnly(boolean value){referenceOnly=value;return this;}
        public Builder addLayer(Layer layer){
            if(layer==null)throw new IllegalArgumentException("Layer is required");
            if(!layers.isEmpty()&&layer.physicalIndex<=layers.get(layers.size()-1).physicalIndex)
                throw new IllegalArgumentException("Physical layers must be added bottom to top");
            layers.add(layer);return this;
        }
        public Builder layerMappingComplete(boolean complete){layerMappingComplete=complete;return this;}
        public AssemblyGuidePlan build(){
            if(layers.isEmpty())throw new IllegalArgumentException("Assembly guide has no physical operations");
            return new AssemblyGuidePlan(projectName,styleName,widthMm,heightMm,mountingColor,
                registration,referenceOnly,markName,markSizeMm,layerMappingComplete,layers);
        }
    }

    public final String projectName,styleName,registrationShape;
    public final float widthMm,heightMm,registrationSizeMm;
    public final int mountingColor;
    public final boolean registration,referenceOnly,layerMappingComplete;
    public final List<Layer> layers;

    private AssemblyGuidePlan(String project,String style,float width,float height,int mounting,
            boolean marks,boolean reference,String markName,float markSize,boolean complete,List<Layer> source){
        projectName=project;styleName=style;widthMm=width;heightMm=height;mountingColor=mounting;
        registration=marks;referenceOnly=reference;registrationShape=markName;registrationSizeMm=markSize;
        layerMappingComplete=complete;layers=Collections.unmodifiableList(new ArrayList<>(source));
    }

    public int vinylLayerCount(){int count=0;for(Layer layer:layers)if(layer.kind==Kind.VINYL)count++;return count;}
    public String previewText(){
        StringBuilder out=new StringBuilder();
        out.append(projectName).append("\n").append(styleName).append("\n")
            .append(String.format(Locale.US,"Finished size: %.2f × %.2f in\n",widthMm/25.4f,heightMm/25.4f))
            .append("Mounting surface: ").append(ExportColorLabels.hex(mountingColor)).append("\n")
            .append("Registration marks: ").append(registration?
                registrationShape+String.format(Locale.US," · %.1f mm",registrationSizeMm):"Off").append("\n\n");
        for(Layer layer:layers){
            out.append(layer.physicalIndex).append(". ").append(layer.name).append(" · ")
                .append(ExportColorLabels.hex(layer.color)).append(" · ").append(layer.countLabel());
            if(!layer.exported)out.append(" · not exported");
            out.append('\n');
        }
        if(referenceOnly)out.append("\nREFERENCE ONLY: Unsafe registration positions omitted. Not a cutter alignment template.\n");
        if(!layerMappingComplete)out.append("\nLayer mapping is incomplete for this artwork engine.");
        return out.toString().trim();
    }
}
