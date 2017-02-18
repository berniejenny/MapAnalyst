package ika.mapanalyst;

import com.jhlabs.map.Ellipsoid;
import com.jhlabs.map.proj.Projection;
import ika.geo.osm.Projector;
import ika.proj.ProjectionsManager;
import ika.transformation.Transformation;
import java.text.DecimalFormat;
import java.util.Vector;

/**
 *
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich
 */
public class ProjectionEvaluator {

    private final double deltaLon0Deg = 0.25;
    private String lastFitReport = "";
    private static final DecimalFormat formatter = new DecimalFormat("#,###.0");
    private static final String nl = System.getProperty("line.separator");

    public ProjectionEvaluator() {
    }

    public Projection getBestFit(double[][] osmPoints, double[][] oldPoints,
            Transformation transformation) {

        assert (osmPoints.length == oldPoints.length);

        // convert new points to geographic coordinates
        double[][] geoNewPointsRad = new double[osmPoints.length][2];
        Projector.OSM2Geo(osmPoints, geoNewPointsRad);

        StringBuilder report = new StringBuilder();
        appendHeaderInfo(report);

        // get all available projections
        Vector<Projection> projections = ProjectionsManager.getProjections();
        double smallestMeanDist = Double.MAX_VALUE;
        Projection bestFitProjection = null;

        // find the range of longitude values covered by the new points
        double[] longitudeRange = longitudeRangeDegrees(geoNewPointsRad);

        // test all projections
        for (Projection p : projections) {
            // find central longitude such that the mean distance is minimized
            double meanDist = fitLongitude(p, transformation,
                    geoNewPointsRad, oldPoints, longitudeRange);
            if (meanDist < smallestMeanDist) {
                smallestMeanDist = meanDist;
                bestFitProjection = p;
            }
            appendProjectionAndTransformationInfo(p, transformation, report, meanDist);
        }

        lastFitReport = report.toString();
        return bestFitProjection;
    }

    private void appendHeaderInfo(StringBuilder sb) {
        sb.append("Projection Comparison");
        sb.append(nl);
        sb.append("---------------------");
        sb.append(nl);
        sb.append(nl);
        sb.append("Mean distance for all control points");
        sb.append(nl);
        sb.append("------------------------------------");
        sb.append(nl);
        sb.append(nl);
    }

    private void appendProjectionAndTransformationInfo(Projection p,
            Transformation transformation,
            StringBuilder sb,
            double meanDist) {
        sb.append(p.toString());
        sb.append(": \t");
        sb.append(formatter.format(meanDist * 1000));
        sb.append(" mm");
        sb.append(nl);
        sb.append("Central Longitude: \t");
        sb.append(formatter.format(p.getProjectionLongitudeDegrees()));
        sb.append("\u00B0");
        sb.append(nl);
        sb.append("with ");
        sb.append(transformation.getName());
        sb.append(" transformation");
        sb.append(nl);
        sb.append(transformation.getShortReport(true));
        sb.append(nl);
    }

    /**
     * Computes the mean distance for a set of control points, a projection and
     * a transformation. The points in the reference map are transformed to
     * the sheet coordinate system of the old map, and the mean distance is
     * computed in the coordinate system of the old map (in meters).
     * @param p Projection, must be initialized.
     * @param osmPoints Control points in the new map in OSM coordinate system.
     * @param oldPoints Control points in meters in the old map
     * @param transformation The transformation to apply to the points in the old map.
     * @return The mean distance in meters in the coordinate system of the old
     * map.
     */
    public double evalProjection(Projection p,
            double[][] osmPoints, double[][] oldPoints,
            Transformation transformation) {

        // convert new points to geographic coordinates
        double[][] geoNewPointsRad = new double[osmPoints.length][2];
        Projector.OSM2Geo(osmPoints, geoNewPointsRad);

        return evalProjection(p, transformation, geoNewPointsRad, oldPoints);
    }

    /**
     * Computes the mean distance for a set of control points, a projection and
     * a transformation. The points in the reference map are transformed to
     * the sheet coordinate system of the old map, and the mean distance is
     * computed in the coordinate system of the old map (in meters).
     * @param p The projection, must be initialized.
     * @param transformation The transformation to apply to the points in the old map.
     * @param geoNewPointsRad Control points in lon/lat in radians.
     * @param oldPoints Control points in meters in the old map
     * @return The mean distance in meters in the coordinate system of the old
     * map.
     */
    private double evalProjection(Projection p,
            Transformation transformation,
            double[][] geoNewPointsRad,
            double[][] oldPoints) {

        final int nPoints = oldPoints.length;

        // project control points in reference map to intermediat coordinate
        // system.
        double[][] projectedSourcePoints = new double[nPoints][2];
        Projector projector = new Projector();
        projector.setInitializedProjection(p);
        projector.geo2Intermediate(geoNewPointsRad, projectedSourcePoints);

        // compute the transformation parameters for the control points
        // in the old map for a best fit with the reproejcted points of the
        // new map.
        transformation.init(oldPoints, projectedSourcePoints);

        // compute the mean distance for all pairs of control points.
        double[][] v = transformation.getResiduals();
        double dSum = 0;
        for (int i = 0; i < v.length; i++) {
            dSum += Math.hypot(v[i][0], v[i][1]);
        }
        return dSum / nPoints;
    }

    private static void initProjection(Projection p, double lon0Deg) {
        p.setProjectionLongitudeDegrees(lon0Deg);
        p.setEllipsoid(Ellipsoid.SPHERE);
        //p.setTrueScaleLatitude(Math.toRadians(lon0Deg));
        p.initialize();
    }

    /**
     * Searches a central longitude such that the mean distance for the passed
     * control points is minimum.
     * @param p
     * @param lonRangeDeg
     * @return
     */
    private double fitLongitude(Projection p,
            Transformation transformation,
            double[][] geoNewPointsRad,
            double[][] oldPoints,
            double[] lonRangeDeg) {

        double smallestDist = Double.MAX_VALUE;
        double bestFitLon0 = lonRangeDeg[0];
        for (double lon0 = lonRangeDeg[0]; lon0 <= lonRangeDeg[1]; lon0 += deltaLon0Deg) {
            initProjection(p, lon0);
            double d = evalProjection(p, transformation, geoNewPointsRad, oldPoints);
            if (d < smallestDist) {
                smallestDist = d;
                bestFitLon0 = lon0;
            }
        }

        initProjection(p, bestFitLon0);
        
        return smallestDist;
    }

    /**
     * Returns the range of longitude values of the passed points
     * @param ptsRad
     * @return
     */
    private static double[] longitudeRangeDegrees(double[][] ptsRad) {

        if (ptsRad == null || ptsRad.length == 0) {
            return new double[]{0, 0};
        }

        double lonMin = Double.MAX_VALUE;
        double lonMax = -Double.MAX_VALUE;
        for (int i = 0; i < ptsRad.length; i++) {
            lonMax = Math.max(ptsRad[i][0], lonMax);
            lonMin = Math.min(ptsRad[i][0], lonMin);
        }
        return new double[]{Math.toDegrees(lonMin), Math.toDegrees(lonMax)};

    }

    /**
     * @return the lastFitReport
     */
    public String getLastFittingReport() {
        return lastFitReport;
    }
}
