/*
 * Isolines.java
 *
 * Created on August 14, 2005, 3:40 PM
 *
 */

package ika.mapanalyst;

import java.awt.geom.*;
import ika.transformation.*;
import ika.geo.*;
import java.util.ArrayList;

/**
 *
 * @author jenny
 */
public class Isolines extends MapAnalyzer {

    private static final long serialVersionUID = -8528113106597981486L;

    private final static int GRID_SIZE = 80;
    private final static double WEIGHT_AT_MAX_DIST = 0.001;
    
    private VectorSymbol isoscalesVectorSymbol;
    private VectorSymbol isorotationVectorSymbol;
    private float isoscaleInterval = 5000;
    private float isorotationInterval = 5;
    
    private boolean showScale = false;
    private boolean showRotation = false;
    private boolean showScaleLabel = false;
    private boolean showRotationLabel = false;
    
    private GeoSet scaleLinesGeoSet;
    private GeoSet scaleLabelsGeoSet;
    private GeoSet rotationLinesGeoSet;
    private GeoSet rotationLabelsGeoSet;
    
    private GeoGrid scaleGeoGrid;
    private GeoGrid rotationGeoGrid;
    
    private boolean analyzeOldMap;
    
    private double[][] closeSrcPts, closeDstPts;
    private TransformationWeightedHelmert transformation =
            new TransformationWeightedHelmert();
    
    private double radiusOfInfluence = 10000;
    
    private final static double MIN_RADIUS_OF_INFLUENCE_PERC = 0.1;
    private final static double MAX_RADIUS_OF_INFLUENCE_PERC = 0.6;
    
    /** Creates a new instance of Isolines */
    public Isolines() {
        this.isoscalesVectorSymbol = new VectorSymbol();
        this.isoscalesVectorSymbol.setStrokeWidth(1f);
        this.isoscalesVectorSymbol.setFilled(false);
        this.isoscalesVectorSymbol.setScaleInvariant(true);
        
        this.isorotationVectorSymbol = new VectorSymbol();
        this.isorotationVectorSymbol.setStrokeWidth(1f);
        this.isorotationVectorSymbol.setFilled(false);
        this.isorotationVectorSymbol.setScaleInvariant(true);
    }
    
    public String getName() {
        return "Isolines";
    }
    
    
    /* Overwrite isVisible - returns true if scale or rotation lines are visible */
    @Override
    public boolean isVisible() {
        return this.showScale || this.showRotation;
    }
    
    /* Overwrite setVisible - does nothing */
    @Override
    public void setVisible(boolean visible) {
        // do nothing
    }
    
    public void analyzeMap() {
        try {
            // remember whether the grids and isolines were computed for the
            // old or the new map
            this.analyzeOldMap = params.isAnalyzeOldMap();
            
            final GeoSet destGeoSet = this.analyzeOldMap ?
                this.oldGeoSet : this.newGeoSet;
            
            // compute the size of the GeoGrid and create it
            Rectangle2D ptsBounds = params.getDstPointsExtension();
            final double pointsWidth = ptsBounds.getWidth();
            final double pointsHeight = ptsBounds.getHeight();
            final double pointsMaxExtension = Math.max(pointsWidth, pointsHeight);
            final double meshSize = pointsMaxExtension / Isolines.GRID_SIZE;
            final int cols = (int)Math.round(pointsWidth / meshSize);
            final int rows = (int)Math.round(pointsHeight / meshSize);
            this.scaleGeoGrid = new GeoGrid(cols, rows, meshSize, Float.NaN);
            this.rotationGeoGrid = new GeoGrid(cols, rows, meshSize, Float.NaN);
            final double gridNorth = ptsBounds.getMaxY();
            final double gridWest = ptsBounds.getMinX();
            scaleGeoGrid.setWest(gridWest);
            scaleGeoGrid.setNorth(gridNorth);
            rotationGeoGrid.setWest(gridWest);
            rotationGeoGrid.setNorth(gridNorth);
            
            // compute parameters for selecting points
            final double radiusOfInfluenceScale = this.analyzeOldMap ? 
                params.getTransformation().getScale() : 1;
            final double rad = this.radiusOfInfluence * radiusOfInfluenceScale;
            final double cutOffDistSqr = rad*rad;
            final double k = -Math.log(Isolines.WEIGHT_AT_MAX_DIST)/cutOffDistSqr;
            
//            System.out.println("Start computing grids");
            
//            ika.utils.NanoTimer timer = new ika.utils.NanoTimer();
//            long start = timer.nanoTime();
            
            // compute the grids
            final float[][] scaleGrid = scaleGeoGrid.getGrid();
            final float[][] rotGrid = rotationGeoGrid.getGrid();
            final double[][] srcPts = params.getSrcPoints();
            final double[][] dstPts = params.getDstPoints();
            final double[][] dstHull = params.getDstPointsHull();
            
            float[] scaleRot = new float[2];
            
            // fill the two grids with scale and rotation values.
            for (int r = 0; r < rows; ++r) {
                final double y = gridNorth - r * meshSize;
                final float[] scaleGrid_row = scaleGrid[r];
                final float[] rotGrid_row = rotGrid[r];
                for (int c = 0; c < cols; ++c) {
                    final double x = gridWest + c * meshSize;
                    
                    // test if point is inside convex hull around points
                    // could possibly be accelerated by first testing against bounding box!
                    if (!ika.utils.GeometryUtils.pointInPolygon(x, y, dstHull))
                        continue;
                    
                    this.computeScaleAndRotation(x, y, srcPts, dstPts,
                            cutOffDistSqr, k, scaleRot);
                    
                    // write scale and rotation to grids
                    if (analyzeOldMap) {
                        scaleGrid_row[c] = 1.f / scaleRot[0];
                        rotGrid_row[c] = scaleRot[1];
                    } else {
                        scaleGrid_row[c] = scaleRot[0];
                        rotGrid_row[c] = scaleRot[1];
                    }
                }
            }
            
/*            long end = timer.nanoTime();
            System.out.println("Time needed: " + (end - start)/1000/1000);
            System.out.println("Start contouring");
            start = timer.nanoTime();
 */
            Contourer contourer = new Contourer();
            
            // compute contours for scale grid
            this.scaleLinesGeoSet = new GeoSet();
            this.scaleLinesGeoSet.setName("scale isolines");
            this.scaleLinesGeoSet.setVisible(this.showScale);
            destGeoSet.addGeoObject(scaleLinesGeoSet);
            contourer.setInterval(this.isoscaleInterval);
            contourer.setGeoGrid(scaleGeoGrid);
            ArrayList< ArrayList <double[][]> > contours = contourer.contour();

            // convert to OpenStreetMap if necessary
            if (!params.isAnalyzeOldMap() && params.isOSM()) {
                for (ArrayList <double[][]> levelContours :  contours) {
                    for (double[][] contour : levelContours) {
                        params.getProjector().intermediate2OSM(contour, contour);
                    }
                }
            }

            Contourer.toPaths(contours, scaleLinesGeoSet);
            this.scaleLinesGeoSet.setVectorSymbol(this.isoscalesVectorSymbol);

            // compute contours for rotation grid
            contourer.setTreatDegreeJump(true);
            this.rotationLinesGeoSet = new GeoSet();
            this.rotationLinesGeoSet.setName("rotation isolines");
            this.rotationLinesGeoSet.setVisible(this.showRotation);
            destGeoSet.addGeoObject(rotationLinesGeoSet);
            contourer.setInterval(this.isorotationInterval);
            contourer.setGeoGrid(rotationGeoGrid);
            contours = contourer.contour();

            // convert to OpenStreetMap if necessary
            if (!params.isAnalyzeOldMap() && params.isOSM()) {
                for (ArrayList <double[][]> levelContours :  contours) {
                    for (double[][] contour : levelContours) {
                        params.getProjector().intermediate2OSM(contour, contour);
                    }
                }
            }

            Contourer.toPaths(contours, rotationLinesGeoSet);
            this.rotationLinesGeoSet.setVectorSymbol(this.isorotationVectorSymbol);
            
/*
            end = timer.nanoTime();
            System.out.println("Time needed: " + (end - start)/1000/1000);
 */
            /*
            ika.geoexport.ESRIASCIIGridExporter exporter =
                    new ika.geoexport.ESRIASCIIGridExporter();
            exporter.export(scaleGeoGrid, "/Users/jenny/Desktop/scale.asc");
            exporter.export(rotationGeoGrid, "/Users/jenny/Desktop/rotation.asc");
            */
        } catch (Exception exc) {
            exc.printStackTrace();
        }
    }
    
    public void computeScaleAndRotation(double x, double y,
            double[][] srcPts, double[][] dstPts, Rectangle2D ptsBounds, 
            double mapScale, float[] scaleRot) {
        
        // compute parameters for selecting points
        final double r = this.radiusOfInfluence / mapScale;
        final double cutOffDistSqr = r * r;
        final double k = -Math.log(Isolines.WEIGHT_AT_MAX_DIST)/cutOffDistSqr;
        
        // compute scale and rotation for point x/y
        this.computeScaleAndRotation(x, y, srcPts, dstPts, cutOffDistSqr, k, scaleRot);
    }
    
    private final void computeScaleAndRotation(double x, double y,
            double[][] srcPts, double[][] dstPts, double cutOffDistSqr, double k,
            float[] scaleRot) {
        
        // prepare space for points. These arrays must be large enough to hold
        // all available points.
        if (this.closeSrcPts == null
                || this.closeSrcPts.length != srcPts.length) {
            this.closeSrcPts = new double[srcPts.length][3];
            this.closeDstPts = new double[srcPts.length][2];
        }
        
        int nbrPts = 0;
        for (int i = 0; i < srcPts.length; ++i) {
            double[] dstPts_row = dstPts[i];
            final double dx = x - dstPts_row[0];
            final double dy = y - dstPts_row[1];
            final double distSqr = dx*dx+dy*dy;
            if (distSqr < cutOffDistSqr) {
                this.closeDstPts[nbrPts][0] = dstPts[i][0];
                this.closeDstPts[nbrPts][1] = dstPts[i][1];
                this.closeSrcPts[nbrPts][0] = srcPts[i][0];
                this.closeSrcPts[nbrPts][1] = srcPts[i][1];
                this.closeSrcPts[nbrPts][2] = Math.exp(-k*distSqr);
                nbrPts++;
            }
        }
        
        if (nbrPts < 2) {
            scaleRot[0] = scaleRot[1] = Float.NaN;
            return;
        }
        
        this.transformation.initWithPoints(closeDstPts, closeSrcPts, nbrPts, scaleRot);
    }
    
    public VectorSymbol getIsoscalesVectorSymbol() {
        return isoscalesVectorSymbol;
    }
    
    public void setIsoscalesVectorSymbol(VectorSymbol isoscalesVectorSymbol) {
        isoscalesVectorSymbol.copyTo(this.isoscalesVectorSymbol);
    }
    
    public VectorSymbol getIsorotationVectorSymbol() {
        return isorotationVectorSymbol;
    }
    
    public void setIsorotationVectorSymbol(VectorSymbol isorotationVectorSymbol) {
        isorotationVectorSymbol.copyTo(this.isorotationVectorSymbol);
    }
    
    public float getIsoscaleInterval() {
        return isoscaleInterval;
    }
    
    public void setIsoscaleInterval(float isoscaleInterval) {
        this.isoscaleInterval = isoscaleInterval;
    }
    
    public float getIsorotationInterval() {
        return isorotationInterval;
    }
    
    public void setIsorotationInterval(float isorotationInterval) {
        this.isorotationInterval = isorotationInterval;
    }
    
    public boolean isShowScale() {
        return showScale;
    }
    
    public void setShowScale(boolean showScale) {
        this.showScale = showScale;
        if (this.scaleLinesGeoSet != null)
            this.scaleLinesGeoSet.setVisible(showScale);
    }
    
    public boolean isShowRotation() {
        return showRotation;
    }
    
    public void setShowRotation(boolean showRotation) {
        this.showRotation = showRotation;
        if (this.rotationLinesGeoSet != null)
            this.rotationLinesGeoSet.setVisible(showRotation);
    }
    
    public boolean isShowScaleLabel() {
        return showScaleLabel;
    }
    
    public void setShowScaleLabel(boolean showScaleLabel) {
        this.showScaleLabel = showScaleLabel;
        if (this.scaleLabelsGeoSet != null)
            this.scaleLabelsGeoSet.setVisible(showScale);
    }
    
    public boolean isShowRotationLabel() {
        return showRotationLabel;
    }
    
    public void setShowRotationLabel(boolean showRotationLabel) {
        this.showRotationLabel = showRotationLabel;
        if (this.rotationLabelsGeoSet != null)
            this.rotationLabelsGeoSet.setVisible(showScale);
    }
    
    public float[] getCachedScaleAndRotation(double x, double y, boolean forOldMap) {
        if (this.scaleGeoGrid == null || this.rotationGeoGrid == null
                || forOldMap != this.analyzeOldMap)
            return null;
        
        
        float scale = this.scaleGeoGrid.getBilinearInterpol(x, y);
        
        // take nearest neighbour and not linear interpolation for rotation,
        // since interpolation between azimuth 356 and 1 causes troubles.
        float rot = this.rotationGeoGrid.getNearestNeighbor(x, y);
        if (Float.isNaN(scale) || Float.isNaN(rot))
            return null;
        return new float[] {scale, rot};
    }
    
    public double getRadiusOfInfluence() {
        return radiusOfInfluence;
    }
    
    public void setRadiusOfInfluence(double radiusOfInfluence) {
        this.radiusOfInfluence = radiusOfInfluence;
    }
    
    public boolean testRadiusOfInfluence(double r, Rectangle2D ptsBounds) {
        final double ptsExtension = Math.max(ptsBounds.getWidth(),
                ptsBounds.getHeight());
        return (r > Isolines.MIN_RADIUS_OF_INFLUENCE_PERC * ptsExtension
                && r < Isolines.MAX_RADIUS_OF_INFLUENCE_PERC * ptsExtension);
    }
    
    public double getRecommendedRadiusOfInfluence(Rectangle2D ptsBounds) {
        final double ptsExtension = Math.max(ptsBounds.getWidth(),
                ptsBounds.getHeight());
        return ptsExtension * (Isolines.MAX_RADIUS_OF_INFLUENCE_PERC
                + Isolines.MIN_RADIUS_OF_INFLUENCE_PERC) / 2;
    }
    
    public double[] getRecommendedRadiusOfInfluenceRange(Rectangle2D ptsBounds) {
        final double ptsExtension = Math.max(ptsBounds.getWidth(),
                ptsBounds.getHeight());
        return new double[] {ptsExtension * Isolines.MIN_RADIUS_OF_INFLUENCE_PERC,
                ptsExtension * Isolines.MAX_RADIUS_OF_INFLUENCE_PERC};
    }
}