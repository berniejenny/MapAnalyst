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

    private final Transformation transformation;
    private final double[][] oldPoints;
    private final double[][] newPoints;
    private final double[][] oldPointsHull;
    private final double[][] newPointsHull;
    private final double[][] transformedSourcePoints;
    private final boolean analyzeOldMap;
    private final MultiquadricInterpolation multiquadricInterpolation;

    // format of coordinate labels added to  distortion grid of old map
    private final CoordinateFormatter oldCoordinateFormatter;

    // format of coordinate labels added to  distortion grid of new map
    private final CoordinateFormatter newCoordinateFormatter;

    private final Projector projector;
    private final boolean OSM;

    /**
     * Creates a new instance of VisualizationParameters
     */
    public VisualizationParameters(Transformation transformation,
            double[][] oldPoints,
            double[][] newPoints,
            double[][] oldPointsHull,
            double[][] newPointsHull,
            double[][] transformedSourcePoints,
            boolean analyzeOldMap,
            MultiquadricInterpolation multiquadricInterpolation,
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
        this.multiquadricInterpolation = multiquadricInterpolation;
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

    /* Returns the points of the map that will NOT contain the generated graphics */
    protected double[][] getSrcPoints() {
        return analyzeOldMap ? newPoints : oldPoints;
    }

    /**
     * Returns the points of the map that will contain the generated graphics
     */
    protected double[][] getDstPoints() {
        return analyzeOldMap ? oldPoints : newPoints;
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
        return analyzeOldMap ? newPointsHull : oldPointsHull;
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

    protected MultiquadricInterpolation getMultiquadricInterpolation() {
        return multiquadricInterpolation;
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
