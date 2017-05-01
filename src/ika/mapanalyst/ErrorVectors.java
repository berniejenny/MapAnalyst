package ika.mapanalyst;

import java.io.*;
import java.awt.Color;
import ika.geo.*;

/**
 * Computes and writes the errors for each measured control point in the
 * specified GeoSet.
 */
public class ErrorVectors extends MapAnalyzer implements Serializable {

    private static final long serialVersionUID = 7775887218673474533L;

    private VectorSymbol vectorSymbol;
    private VectorSymbol outliersVectorSymbol;
    private Color outliersColor;
    private boolean markOutliers;
    private boolean showVectors;
    private boolean showCircles;
    private GeoSet vectorGeoSet;
    private GeoSet circleGeoSet;

    /**
     * The error vectors are scalable. This variable stores the scale factor.
     */
    private double vectorScale;

    public ErrorVectors() {
        this.vectorSymbol = new VectorSymbol();
        this.vectorSymbol.setStrokeWidth(2f);
        this.vectorSymbol.setFilled(false);
        this.vectorSymbol.setScaleInvariant(true);
        this.vectorScale = 1.;

        this.outliersVectorSymbol = this.vectorSymbol.copy();
        this.outliersColor = Color.RED;
        this.markOutliers = false;
        this.showVectors = true;
        this.showCircles = false;
        this.vectorGeoSet = null;
        this.circleGeoSet = null;

        // default is invisible
        this.oldGeoSet.setVisible(false);
        this.newGeoSet.setVisible(false);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
    }

    public String getName() {
        return "Error Vector";
    }

    private class Circle implements Comparable {

        public double x, y, dist;

        public Circle(double x, double y, double dist) {
            this.x = x;
            this.y = y;
            this.dist = dist;
        }

        public int compareTo(Object o) {
            final Circle circle = (Circle) o;
            return this.dist < circle.dist ? -1
                    : (this.dist > circle.dist ? 1 : 0);
        }
    }

    /**
     * Computes the error vectors graphic.
     *
     * @param VisualizationParameters Contains parameters for the computation.
     */
    public void analyzeMap() {

        GeoSet destGeoSet;
        if (params.isAnalyzeOldMap()) {
            destGeoSet = this.oldGeoSet;
        } else {
            destGeoSet = this.newGeoSet;
        }
        this.vectorGeoSet = new GeoSet();
        this.vectorGeoSet.setName("error vectors");
        this.circleGeoSet = new GeoSet();
        this.circleGeoSet.setName("error circles");
        vectorGeoSet.setVisible(this.showVectors);
        circleGeoSet.setVisible(this.showCircles);
        destGeoSet.addGeoObject(circleGeoSet);
        destGeoSet.addGeoObject(vectorGeoSet);

        final double sigma0 = params.getTransformation().getSigma0();

        double[][] transformedSourcePoints = params.getTransformedSourcePoints();
        Circle[] circles = new Circle[transformedSourcePoints.length];
        double[][] destPoints = params.getDstPoints();

        // if OpenStreetMap is used and vectors are displayed in the OSM,
        // convert the vectors to OSM.
        if (!params.isAnalyzeOldMap() && params.isOSM()) {
            double[][] pts = new double[transformedSourcePoints.length][2];
            params.getProjector().intermediate2OSM(transformedSourcePoints, pts);
            transformedSourcePoints = pts;
            pts = new double[transformedSourcePoints.length][2];
            params.getProjector().intermediate2OSM(destPoints, pts);
            destPoints = pts;
        }

        // Build vectors.
        for (int i = 0; i < transformedSourcePoints.length; i++) {

            final double destPointX = destPoints[i][0];
            final double destPointY = destPoints[i][1];
            final double dx = destPointX - transformedSourcePoints[i][0];
            final double dy = destPointY - transformedSourcePoints[i][1];
            final double vectorLength = Math.sqrt(dx * dx + dy * dy);

            // use special symbol for outliers
            final VectorSymbol symbol = vectorLength > 3 * sigma0
                    ? outliersVectorSymbol : vectorSymbol;

            // build vector
            GeoPath vector = new GeoPath();
            vector.moveTo((float) destPointX, (float) destPointY);
            vector.lineTo((float) (destPointX - this.vectorScale * dx),
                    (float) (destPointY - this.vectorScale * dy));
            vector.setSelectable(false);
            vector.setVectorSymbol(symbol);
            vectorGeoSet.addGeoObject(vector);

            // store circle geometry
            circles[i] = new Circle(destPointX, destPointY, vectorLength);
        }

        // sort circles with increasing radius
        java.util.Arrays.sort(circles);

        // scale radius of circles. Use median of all radii.
        final double medianDist = circles[circles.length / 2].dist;
        final double medianR = Math.sqrt(medianDist / Math.PI / this.vectorScale);
        final double rScale = medianDist / medianR;

        // Build circles. Start with largest circle.
        for (int i = circles.length - 1; i >= 0; --i) {
            Circle circle = circles[i];

            if (circle.dist < 0.00000001) {
                continue;
            }

            // make value to map (=distance) proportional to area of circle
            // A = r*r*PI = d  >>  r = sqrt(d/PI)
            final double r = Math.sqrt(circle.dist / Math.PI) * rScale;
            if (r < 0.00000001) {
                continue;
            }

            GeoPath geoPath = new GeoPath();
            geoPath.circle((float) circle.x, (float) circle.y, (float) r);
            geoPath.setSelectable(false);

            // use special symbol for outliers
            final VectorSymbol symbol = circle.dist > 3 * sigma0
                    ? outliersVectorSymbol : vectorSymbol;
            geoPath.setVectorSymbol(symbol);
            circleGeoSet.addGeoObject(geoPath);
        }
    }

    /**
     * Returns the scale of the vectors
     *
     * @return the scale of the vectors
     */
    public double getVectorScale() {
        return this.vectorScale;
    }

    /**
     * Sets the vector scale
     *
     * @param scale the vectors length will be multiplied with scale
     */
    public void setVectorScale(double scale) {
        this.vectorScale = scale;
    }

    public VectorSymbol getVectorSymbol() {
        return vectorSymbol;
    }

    public void setVectorSymbol(VectorSymbol vectorSymbol) {
        vectorSymbol.copyTo(this.vectorSymbol);

        // copy vectorSymbol to outliersVectorSymbol, but keep its original color
        vectorSymbol.copyTo(this.outliersVectorSymbol);
        if (this.markOutliers) {
            this.outliersVectorSymbol.setStrokeColor(this.outliersColor);
        }
    }

    public Color getOutliersColor() {
        return this.outliersColor;
    }

    public void setOutliersColor(Color outliersColor) {
        this.outliersColor = outliersColor;
        if (this.markOutliers) {
            this.outliersVectorSymbol.setStrokeColor(this.outliersColor);
        }
    }

    public boolean isMarkOutliers() {
        return markOutliers;
    }

    public void setMarkOutliers(boolean markOutliers) {
        this.markOutliers = markOutliers;
        if (markOutliers) {
            this.outliersVectorSymbol.setStrokeColor(this.outliersColor);
        } else {
            this.outliersVectorSymbol.setStrokeColor(this.vectorSymbol.getStrokeColor());
        }
    }

    public boolean isShowVectors() {
        return showVectors;
    }

    public void setShowVectors(boolean showVectors) {
        this.showVectors = showVectors;
        if (vectorGeoSet != null) {
            this.vectorGeoSet.setVisible(showVectors);
        }
    }

    public boolean isShowCircles() {
        return showCircles;
    }

    public void setShowCircles(boolean showCircles) {
        this.showCircles = showCircles;
        if (this.circleGeoSet != null) {
            this.circleGeoSet.setVisible(showCircles);
        }
    }

    @Override
    public void clearAll() {
        super.clearAll();
        this.vectorGeoSet = null;
        this.circleGeoSet = null;
    }
}
