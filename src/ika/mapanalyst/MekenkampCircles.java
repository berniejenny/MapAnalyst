/*
 * MekenkampCircles.java
 *
 * Created on June 27, 2005, 7:59 PM
 *
 */
package ika.mapanalyst;

import ika.geo.*;

/**
 * Standard inaccuracy circles after Mekenkamp Mekenkamp, P. G. M. (1990). Die
 * Entwicklung einer neuen Methode für die Bestimmung der Genauigkeit von alten
 * Karten. 5. Kartographiehistorisches Colloquium 1990, Oldenburg, Dietrich
 * Reimer Verlag, Berlin.
 *
 * The use of this visualisation method is not recommended and no longer
 * available in the MapAnalyst GUI.
 *
 * @author Bernhard Jenny, Institut of Cartography, ETH Zurich
 */
public class MekenkampCircles extends MapAnalyzer implements java.io.Serializable {

    static final long serialVersionUID = -3091175900026825633L;

    private VectorSymbol vectorSymbol;

    private double circleScale;

    /**
     * Creates a new instance of MekenkampCircles
     */
    public MekenkampCircles() {
        this.vectorSymbol = new VectorSymbol();
        this.vectorSymbol.setScaleInvariant(true);
        this.circleScale = 0.005;

        // default is invisible
        this.oldGeoSet.setVisible(false);
        this.newGeoSet.setVisible(false);

    }

    @Override
    public String getName() {
        return "Mekenkamp Circles";
    }

    @Override
    public void analyzeMap() {
        final boolean showMekenkamp = false;
        if (showMekenkamp == false) {
            return;
        }

        final GeoSet destGeoSet = params.isAnalyzeOldMap()
                ? this.oldGeoSet : this.newGeoSet;

        final double[][] dstPoints = params.getDstPoints();
        final double[][] srcPoints = params.getSrcPoints();

        int nbrPts = dstPoints.length;

        // compute the distance between all points in the destination map
        // store the distances in the symetric matrix A
        double A[][] = new double[nbrPts][nbrPts];
        for (int i = 0; i < nbrPts; i++) {
            for (int j = i + 1; j < nbrPts; j++) {
                final double dx = dstPoints[i][0] - dstPoints[j][0];
                final double dy = dstPoints[i][1] - dstPoints[j][1];
                A[i][j] = A[j][i] = Math.sqrt(dx * dx + dy * dy);
            }
        }

        // compute the distance between all points in the source map
        // store the distances in the symetric matrix B
        double B[][] = new double[nbrPts][nbrPts];
        final double mapScale = params.getTransformation().getScale();
        for (int i = 0; i < nbrPts; i++) {
            for (int j = i + 1; j < nbrPts; j++) {
                final double dx = srcPoints[i][0] - srcPoints[j][0];
                final double dy = srcPoints[i][1] - srcPoints[j][1];
                B[i][j] = B[j][i] = Math.sqrt(dx * dx + dy * dy) * mapScale;
            }
        }

        /* Test with sample data from Mekenkamp's publication.
         * Mekenkamp, P. G. M. (1990). Die Entwicklung einer neuen Methode für
         * die Bestimmung der Genauigkeit von alten Karten.
         * 5. Kartographiehistorisches Colloquium 1990, Oldenburg,
         * Dietrich Reimer Verlag, Berlin.
         */
 /*
        nbrPts = 3;
        A = new double[][] {{0, 9, 10}, {9, 0, 6}, {10, 6, 0}};
        B = new double[][] {{0, 8.33, 8.33}, {8.33, 0, 8.33}, {8.33, 8.33, 0}};
         */
        // compute the differences of the distances between A and B
        // store the results in the symetric matrix C
        double C[][] = new double[nbrPts][nbrPts];
        double[] A_row, B_row, C_row;
        for (int i = 0; i < nbrPts; i++) {
            A_row = A[i];
            B_row = B[i];
            C_row = C[i];
            for (int j = i + 1; j < nbrPts; j++) {
                C_row[j] = C[j][i] = Math.abs(A_row[j] - B_row[j]);
            }
        }

        // wheight the differences by the distances in the source map
        // store the results in the symetric matrix H
        double H[][] = new double[nbrPts][nbrPts];
        double[] H_row;
        for (int i = 0; i < nbrPts; i++) {
            H_row = H[i];
            B_row = B[i];
            C_row = C[i];
            for (int j = i + 1; j < nbrPts; j++) {
                if (B_row[j] != 0.) {
                    H_row[j] = H[j][i] = 100 * (C_row[j] / B_row[j]);
                }
            }
        }

        double scale = this.circleScale;
        if (!params.isAnalyzeOldMap()) {
            scale *= params.getTransformation().getScale();
        }

        // Compute the radii of the circles.
        // Sum the squared values of each line and divide by the number of points-1.
        // The square root of this value is proportional to the radius.
        for (int i = 0; i < nbrPts; ++i) {
            double lineTotal = 0;
            H_row = H[i];
            for (int j = 0; j < nbrPts; j++) {
                lineTotal += H_row[j] * H_row[j];
            }
            final double r = Math.sqrt(lineTotal / (nbrPts - 1));
            if (r < 0.00001) {
                continue;
            }

            // if OpenStreetMap is used and circles are displayed in the OSM,
            // convert the points to OSM.
            final double[] cxy; // FIXME
            /*
            if (!params.isAnalyzeOldMap() && params.getLon0lat0() != null) {
                cxy = OSM.local_to_OSM(dstPoints[i], params.getLon0lat0());

            } else {
             */ cxy = dstPoints[i];
            //}
            // construct a circle
            GeoPath circle = new GeoPath();
            circle.circle((float) cxy[0], (float) cxy[1], (float) (r * scale));
            circle.setSelectable(false);

            // assign symbol and add the circle to GeoSet
            circle.setVectorSymbol(vectorSymbol);
            destGeoSet.addGeoObject(circle);
        }

    }

    public VectorSymbol getVectorSymbol() {
        return vectorSymbol;
    }

    public void setVectorSymbol(VectorSymbol vectorSymbol) {
        vectorSymbol.copyTo(this.vectorSymbol);
    }

    public double getCircleScale() {
        return circleScale;
    }

    public void setCircleScale(double circleScale) {
        this.circleScale = circleScale;
    }
}
