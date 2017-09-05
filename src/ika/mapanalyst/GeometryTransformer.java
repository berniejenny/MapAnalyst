/*
 * GeometryTransformer.java
 *
 * Created on June 27, 2005, 6:08 PM
 *
 */

package ika.mapanalyst;

import ika.geo.*;
import ika.transformation.Transformation;

/**
 *
 * @author jenny
 */
public class GeometryTransformer extends MapAnalyzer implements java.io.Serializable {
    static final long serialVersionUID = -8896023758644042810L;
    
    private GeoSet oldSourceGeoSet;
    private GeoSet newSourceGeoSet;
    
    /** Creates a new instance of GeometryTransformer */
    public GeometryTransformer() {
        this.oldSourceGeoSet = new GeoSet();
        this.newSourceGeoSet = new GeoSet();
    }
    
    public String getName() {
        return "Geometry Transformer";
    }
    
    
    public void analyzeMap() {
        
        GeoSet destGeoSet, sourceGeoSet;
        if(params.isAnalyzeOldMap()){
            destGeoSet = this.oldGeoSet;
            sourceGeoSet = this.newSourceGeoSet;
        } else{
            destGeoSet = this.newGeoSet;
            sourceGeoSet = this.oldSourceGeoSet;
        }
        if (sourceGeoSet == null || destGeoSet == null)
            return;
        
        final Transformation transformation = params.getTransformation();
        final MultiquadricInterpolation multiQuadra =
                params.getMultiquadricInterpolation();
        GeoSet transformedGeoSet= transformation.transform(sourceGeoSet);
        if (transformation == null
                || multiQuadra == null
                || transformedGeoSet == null)
            return;
        
        transformedGeoSet = multiQuadra.transform(transformedGeoSet);
        if (transformedGeoSet == null)
            return;
        
        final int nbrGeoObj = transformedGeoSet.getNumberOfChildren();
        for (int i = 0; i < nbrGeoObj; ++i) {
            destGeoSet.addGeoObject(transformedGeoSet.getGeoObject(i));
        }
    }
    
    public void addDrawing(GeoObject geoObject, boolean addToOldMap) {
        if (addToOldMap)
            this.oldSourceGeoSet.addGeoObject(geoObject);
        else
            this.newSourceGeoSet.addGeoObject(geoObject);
    }
    
    public void removeDrawing(boolean removeFromOldMap) {
        if (removeFromOldMap)
            this.oldSourceGeoSet.removeAllGeoObjects();
        else
            this.newSourceGeoSet.removeAllGeoObjects();
    }
    
    public GeoSet getOldSourceGeoSet() {
        return oldSourceGeoSet;
    }
    
    public GeoSet getNewSourceGeoSet() {
        return newSourceGeoSet;
    }
}
