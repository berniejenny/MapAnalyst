/*
 * VisualizationParameters.java
 *
 * Created on August 11, 2005, 6:16 PM
 *
 */

package ika.mapanalyst;

import ika.geo.osm.Projector;
import java.awt.geom.*;
import ika.transformation.Transformation;
import ika.utils.CoordinateFormatter;

/**
 *
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich
 */
public final class VisualizationParameters {
    
    private Transformation transformation;
    private double[][] oldPoints;
    private double[][] newPoints;
    private double[][] oldPointsHull;
    private double[][] newPointsHull;
    private double[][] transformedSourcePoints;
    private boolean analyzeOldMap;
    private MultiquadraticInterpolation multiquadraticInterpolation;
    private double oldMapScale;
    private double newMapScale;
    private CoordinateFormatter oldCoordinateFormatter;
    private CoordinateFormatter newCoordinateFormatter;
    private Projector projector;
    private boolean OSM;
    
    /** Creates a new instance of VisualizationParameters */
    public VisualizationParameters(Transformation transformation,
            double[][] oldPoints,
            double[][] newPoints,
            double[][] oldPointsHull,
            double[][] newPointsHull,
            double[][] transformedSourcePoints,
            boolean analyzeOldMap,
            MultiquadraticInterpolation multiquadraticInterpolation,
            double oldMapScale,
            double newMapScale,
            CoordinateFormatter oldCoordinateFormatter,
            CoordinateFormatter newCoordinateFormatter,
            Projector projector,
            boolean OSM) {
        
        this.transformation = transformation;
        this.oldPoints = oldPoints;
        this.newPoints = newPoints;
        this.oldPointsHull = oldPointsHull;
        this.newPointsHull = newPointsHull;
        this.transformedSourcePoints = transformedSourcePoints;
        this.analyzeOldMap = analyzeOldMap;
        this.multiquadraticInterpolation = multiquadraticInterpolation;
        this.oldMapScale = oldMapScale;
        this.newMapScale = newMapScale;
        this.oldCoordinateFormatter = oldCoordinateFormatter;
        this.newCoordinateFormatter = newCoordinateFormatter;
        this.projector = projector;
        this.OSM = OSM;
    }
    
    protected Transformation getTransformation() {
        return transformation;
    }
    
    protected double getTransformationScale() {
        return 1. / transformation.getScale();
    }
    
    protected double[][] getOldPoints() {
        return oldPoints;
    }
    
    protected void setNewPoints(double[][] newPoints) {
        this.newPoints = newPoints;
    }
    
    /* Returns the points of the map that will NOT contain the generated graphics */
    protected double[][] getSrcPoints() {
        return this.analyzeOldMap ? newPoints : oldPoints;
    }
    
    /** Returns the points of the map that will contain the generated graphics */
    protected double[][] getDstPoints() {
        return this.analyzeOldMap ? oldPoints : newPoints;
    }
    
    protected Rectangle2D getSrcPointsExtension() {
        return Manager.findBoundingBox(analyzeOldMap ? newPoints : oldPoints);
    }
    
    protected Rectangle2D getDstPointsExtension() {
        return Manager.findBoundingBox(analyzeOldMap ? oldPoints : newPoints);
    }
    
    public double[][] getOldPointsHull() {
        return oldPointsHull;
    }
    
    public double[][] getNewPointsHull() {
        return newPointsHull;
    }
    
    protected double[][] getSrcPointsHull() {
        return this.analyzeOldMap ? newPointsHull : oldPointsHull;
    }
    
    protected double[][] getDstPointsHull() {
        return this.analyzeOldMap ? oldPointsHull : newPointsHull;
    }
    
    protected double[][] getTransformedSourcePoints() {
        return transformedSourcePoints;
    }
    
    protected boolean isAnalyzeOldMap() {
        return analyzeOldMap;
    }
    
    protected MultiquadraticInterpolation getMultiquadraticInterpolation() {
        return multiquadraticInterpolation;
    }
    
    protected double getOldMapScale() {
        return oldMapScale;
    }
    
    protected double getNewMapScale() {
        return newMapScale;
    }
    
    protected CoordinateFormatter getOldCoordinateFormatter() {
        return oldCoordinateFormatter;
    }
    
    protected CoordinateFormatter getNewCoordinateFormatter() {
        return newCoordinateFormatter;
    }

    public boolean isOSM() {
        return OSM;
    }

    /**
     * @return the projector
     */
    public Projector getProjector() {
        return projector;
    }
}
