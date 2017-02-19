package ika.geo.osm;

import com.jhlabs.map.Ellipsoid;
import com.jhlabs.map.proj.MercatorProjection;
import com.jhlabs.map.proj.Projection;
import com.jhlabs.map.proj.TCEAProjection;
import ika.proj.ProjectionsManager;
import java.awt.geom.Point2D;

/**
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class Projector {

    /**
     * Spherical Mercator projection for converting to and from OSM.
     */
    private static final MercatorProjection mercator;

    static {
        mercator = ProjectionsManager.createWebMercatorProjection();
    }

    /**
     * Projection to convert to and from an intermediate coordinate system.
     */
    private Projection p;

    public Projector() {
        p = new TCEAProjection();
        p.setEllipsoid(Ellipsoid.SPHERE);
        p.initialize();
    }

    public void setInitializedProjection(Projection newProjection) {
        assert (newProjection != null);
        p = newProjection;
    }

    public Projection getProjection() {
        return this.p;
    }

    /**
     * Computes the mean horizontal and mean vertical coordinate of a set of
     * points.
     *
     * @param pts an array of arrays with x and y coordinates.
     * @return the mean position
     */
    public static Point2D meanPosition(double[][] pts) {

        if (pts == null || pts.length == 0) {
            return new Point2D.Double(0, 0);
        }

        double x = 0;
        double y = 0;
        for (int i = 0; i < pts.length; i++) {
            x += pts[i][0];
            y += pts[i][1];
        }
        x /= pts.length;
        y /= pts.length;
        return new Point2D.Double(x, y);

    }

    /**
     * Converts from an intermediate coordinate system to the OpenStreetMap
     * coordinate system. The intermediate coordinate system is used for
     * generating distortion visualizations.
     *
     * @param in an array of arrays with input x and y coordinates in
     * intermediate coordinates.
     * @param out an array of arrays with output x and y coordinates in OSM
     * coordinates.
     */
    public void intermediate2OSM(double[][] in, double[][] out) {

        // convert points to lon/lat
        Point2D.Double pt = new Point2D.Double();
        for (int i = 0; i < in.length; i++) {
            p.inverseTransformRadians(in[i][0], in[i][1], pt);
            out[i][0] = pt.x;
            out[i][1] = pt.y;
        }

        // inverse project from OSM to lon/lat in radians
        for (int i = 0; i < in.length; i++) {
            mercator.transform(Math.toDegrees(out[i][0]), Math.toDegrees(out[i][1]), pt);
            out[i][0] = pt.x;
            out[i][1] = pt.y;
        }

    }

    /**
     * Converts from the OpenStreetMap coordinate system to an intermediate
     * coordinate system. The intermediate coordinate system is used for
     * generating distortion visualizations.
     *
     * @param in an array of arrays with input x and y coordinates in OSM
     * coordinates.
     * @param out an array of arrays with output x and y coordinates in
     * intermediate coordinates.
     */
    public void OSM2Intermediate(double[][] in, double[][] out) {

        // inverse project from OSM to lon/lat in radians
        Point2D.Double pt = new Point2D.Double();
        for (int i = 0; i < in.length; i++) {
            mercator.inverseTransformRadians(in[i][0], in[i][1], pt);
            out[i][0] = pt.x;
            out[i][1] = pt.y;
        }

        // convert points from lon/lat to the Transverse Cylindrical Equal-Area projection
        for (int i = 0; i < out.length; i++) {
            p.transform(Math.toDegrees(out[i][0]), Math.toDegrees(out[i][1]), pt);
            out[i][0] = pt.x;
            out[i][1] = pt.y;
        }

    }

    /**
     * converts lon/lat in degrees to OSM
     *
     * @param in
     * @param out
     */
    public static void geo2OSM(double[][] in, double[][] out) {

        Point2D.Double pt = new Point2D.Double();

        for (int i = 0; i < in.length; i++) {
            mercator.transform(in[i][0], in[i][1], pt);
            out[i][0] = pt.x;
            out[i][1] = pt.y;
        }

    }

    /**
     * Transform from OSM Mercator to geographic coordinates in radians
     *
     * @param in
     * @param out
     */
    public static void OSM2Geo(double[][] in, double[][] out) {

        // inverse project from OSM to lon/lat in radians
        Point2D.Double pt = new Point2D.Double();
        for (int i = 0; i < in.length; i++) {
            mercator.inverseTransformRadians(in[i][0], in[i][1], pt);
            out[i][0] = pt.x;
            out[i][1] = pt.y;
        }

    }

    public double[][] intermediate2geo(double[][] in) {

        // inverse project from OSM to lon/lat in degrees
        double[][] geographic = new double[in.length][2];
        Point2D.Double pt = new Point2D.Double();
        for (int i = 0; i < in.length; i++) {
            p.inverseTransformRadians(in[i][0], in[i][1], pt);
            geographic[i][0] = Math.toDegrees(pt.x);
            geographic[i][1] = Math.toDegrees(pt.y);
        }

        return geographic;
    }

    public void geo2Intermediate(double[][] in, double[][] out) {

        // inverse project from OSM to lon/lat in radians
        Point2D.Double pt = new Point2D.Double();

        // convert points from lon/lat to the Transverse Cylindrical Equal-Area projection
        for (int i = 0; i < in.length; i++) {
            p.transform(in[i][0], in[i][1], pt);
            out[i][0] = pt.x;
            out[i][1] = pt.y;
        }

    }
}
