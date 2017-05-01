/*
 * Transformation.java
 *
 * Created on March 23, 2005, 11:44 AM
 */
package ika.transformation;

import java.io.*;
import ika.utils.NumberFormatter;
import ika.geo.*;
import java.text.DecimalFormat;

/**
 * Base class for geometric 2D transformations between two sets of points, where
 * each set defines a coordinate system.
 *
 * @author Bernhard Jenny
 */
public abstract class Transformation implements Serializable {

    private static final long serialVersionUID = -8347757266449618041L;

    /**
     * If a scale is smaller than SCALE_TO_INVERT, it is inverted when printed
     * to a report.
     */
    protected static final double SCALE_TO_INVERT = 0.01;

    /**
     * The residuals between the two point sets, with the source set transformed
     * to the destination set. Only not null and valid after a call to init().
     */
    protected double[][] v;

    /**
     * Returns the number of points used to compute the parameters of this
     * transformation.
     *
     * @return The number of points
     */
    public int getNumberOfPoints() {
        return v == null ? 0 : v.length;
    }

    /**
     * Returns whether this transformation has been initialized.
     *
     * @return True if initialized.
     */
    public boolean isInitialized() {
        return this.getNumberOfPoints() > 0;
    }

    /**
     * Returns the residuals between the two point sets, with the source set
     * transformed to the destination set.
     */
    public double[][] getResiduals() {
        return v;
    }

    /**
     * Returns the name of the transformation.
     *
     * @return The name.
     */
    public abstract String getName();

    /**
     * Returns an extensive description of the transformation, containing the
     * transformation parameters and their precision.
     *
     * @return The description of the transformation.
     */
    public abstract String getReport(boolean invert);

    /**
     * Returns a short description of the transformation, containing only
     * essential transformation parameters and their precision.
     *
     * @return The description of the transformation.
     */
    public abstract String getShortReport(boolean invert);

    /**
     * Returns a very short description of the transformation, containing only
     * scale and rotation of the transformation.
     *
     * @return The description of the transformation.
     */
    public String getSimpleShortReport(boolean invert) {
        StringBuilder str = new StringBuilder(1024);
        String nl = System.getProperty("line.separator");
        str.append(NumberFormatter.formatScale("Scale", this.getScale(invert)));
        str.append(nl);
        str.append(this.formatRotation("Rotation", this.getRotation(invert)));
        str.append(nl);
        str.append("(rounded values)").append(nl);
        return str.toString();
    }

    /**
     * Returns a short description of the characteristics of the transformation.
     * The description should not contain any numerical values.
     *
     * @return The description.
     */
    public abstract String getShortDescription();

    public String getBreakLineForReport() {
        String nl = System.getProperty("line.separator");
        return nl + nl + "---------------------------------------------"
                + "-------------------------------------------------"
                + nl + nl;
    }

    /**
     * Returns a report containing the standard deviations and the mean position
     * errors in both point sets.
     *
     * @return
     */
    protected String getPointAccuracyReport(boolean invert) {
        String nl = System.getProperty("line.separator");
        StringBuilder str = new StringBuilder();
        final double scale = this.getScale(invert);
        final double sigma0 = this.getSigma0();
        str.append("Standard Deviation in Destination Map [m]:              ");
        str.append(formatPrecise(sigma0 * scale));
        str.append(nl);

        str.append("Standard Deviation in Source Map [m]:                   ");
        str.append(formatPrecise(sigma0));
        str.append(nl);

        double sep = this.getStandardErrorOfPosition();
        str.append("Root Mean Square Position Error in Destination Map [m]: ");
        str.append(formatPrecise(sep * scale));
        str.append(nl);

        str.append("Root Mean Square Position Error in Source Map [m]:      ");
        str.append(formatPrecise(sep));
        str.append(nl);

        return str.toString();
    }

    /**
     * Returns a table of residuals with dx, dy, d (=sqrt(dx*dx+dy*dy)) and an
     * optional asterisk.
     *
     * @param threshold If d > threshold, an asterisk is added. Pass 0 or a
     * value smaller than 0 if this is not required.
     * @return
     */
    public String getResidualsReport(double threshold) {
        String nl = System.getProperty("line.separator");
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            str.append(i + 1);
            str.append("\t");
            str.append(formatPreciseShort(v[i][0]));
            str.append("\t");
            str.append(formatPreciseShort(v[i][1]));
            str.append("\t");
            final double d = Math.hypot(v[i][0], v[i][1]);
            str.append(formatPreciseShort(d));
            if (threshold > 0 && d > threshold) {
                str.append("\t*");
            }
            str.append(nl);
        }
        return str.toString();
    }

    public abstract double getScale();

    public double getScale(boolean invert) {
        return invert ? 1. / this.getScale() : this.getScale();
    }

    public abstract double getRotation();

    public double getRotation(boolean invert) {
        return -this.getRotation();
    }

    public abstract double getSigma0();

    // standard error of position = mittlerer Punktfehler
    public double getStandardErrorOfPosition() {
        return this.getSigma0() * Math.sqrt(2.d);
    }

    public abstract java.awt.geom.AffineTransform getAffineTransform();

    /**
     * Initialize the transformation with two set of control points. The control
     * points are not copied, nor is a reference to them retained. Instead, the
     * transformation parameters are immediately computed by initWithPoints.
     *
     * @param destSet A two-dimensional array (n x 2) containing the x and y
     * coordinates of the points of the destination set. The destSet must be of
     * exactly the same size as sourceSet.
     * @param sourceSet A two-dimensional array (n x 2) containing the x and y
     * coordinates of the points of the source set. The sourceSet must be of
     * exactly the same size as destSet.
     */
    protected abstract void initWithPoints(double[][] destSet, double[][] sourceSet);

    public void init(double[][] destSet, double[][] sourceSet) {

        // make sure both sets have the same number of points.
        if (destSet.length != sourceSet.length) {
            throw new IllegalArgumentException();
        }

        v = new double[destSet.length][2];

        initWithPoints(destSet, sourceSet);
    }

    public void addResidualsToPoints(double[][] pts) {
        // make sure there is the same number of points as residuals.
        if (pts.length != this.v.length) {
            throw new IllegalArgumentException();
        }

        final int nbrPts = pts.length;
        for (int i = 0; i < nbrPts; ++i) {
            pts[i][0] += v[i][0];
            pts[i][1] += v[i][1];
        }
    }

    /**
     * Transform a point from the coordinate system of the source set to the
     * coordinate system of the destination set.
     *
     * @return The transformed coordinates (array of size 1 x 2).
     * @param point The point to be transformed (array of size 1 x 2).
     */
    public abstract double[] transform(double[] point);

    /**
     * Transform an array of points from the coordinate system of the source set
     * to the coordinate system of destination set. The transformed points
     * overwrite the original values in the points array.
     *
     * @param points The points to be transformed.
     * @param xColumn The column of points[] containing the x-coordinate.
     * @param yColumn The column of points[] containing the y-coordinate.
     */
    public void transform(double[][] points, int xColumn, int yColumn) {
        transform(points, xColumn, yColumn, 0, points.length);
    }

    /**
     * Transform an array of points from the coordinate system of the source set
     * to the coordinate system of destination set. The transformed points
     * overwrite the original values in the points array. This method should be
     * overwritten by derived classes to provide a more efficient implementation
     * that avoids multiple calls of transform().
     *
     * @param points The array holding the points to be transformed.
     * @param xColumn The column of points[] containing the x-coordinate.
     * @param yColumn The column of points[] containing the y-coordinate.
     * @param firstPoint The row of the first point to be transformed.
     * @param nbrPoints The number of points to be transformed.
     */
    public void transform(double[][] points, int xColumn, int yColumn,
            int firstPoint, int nbrPoints) {
        final int lastPoint = firstPoint + nbrPoints;
        double[] p = new double[2];
        for (int i = firstPoint; i < lastPoint; i++) {
            p[0] = points[i][xColumn];
            p[1] = points[i][yColumn];
            double[] p_transformed = transform(p);
            points[i][xColumn] = p_transformed[0];
            points[i][yColumn] = p_transformed[1];
        }
    }

    /**
     * Transform an array of points from the coordinate system of the source set
     * to the coordinate system of the destination set. X-coordinates are stored
     * in the first column, y-coordinates in the second column of the points
     * array. The transformed points overwrite the original values in the points
     * array.
     *
     * @param points The points to be transformed.
     */
    public final void transform(double[][] points) {
        this.transform(points, 0, 1);
    }

    public void transform(double[] coords, int nbrPts) {
        double[] pt = new double[2];

        for (int i = 0; i < nbrPts; ++i) {
            pt[0] = coords[i * 2];
            pt[1] = coords[i * 2 + 1];
            pt = transform(pt);
            coords[i * 2] = pt[0];
            coords[i * 2 + 1] = pt[1];
        }
    }

    public java.awt.geom.GeneralPath transform(java.awt.geom.GeneralPath generalPath) {
        java.awt.geom.PathIterator pi = generalPath.getPathIterator(null);
        double[] coords = new double[6];
        int segmentType;
        java.awt.geom.GeneralPath newGeneralPath = new java.awt.geom.GeneralPath();
        while (pi.isDone() == false) {
            segmentType = pi.currentSegment(coords);
            switch (segmentType) {
                case java.awt.geom.PathIterator.SEG_CLOSE:
                    newGeneralPath.closePath();
                    break;
                case java.awt.geom.PathIterator.SEG_LINETO:
                    transform(coords, 1);
                    newGeneralPath.lineTo((float) coords[0], (float) coords[1]);
                    break;
                case java.awt.geom.PathIterator.SEG_MOVETO:
                    transform(coords, 1);
                    newGeneralPath.moveTo((float) coords[0], (float) coords[1]);
                    break;
                case java.awt.geom.PathIterator.SEG_QUADTO:
                    transform(coords, 2);
                    newGeneralPath.quadTo((float) coords[0], (float) coords[1],
                            (float) coords[2], (float) coords[3]);
                    break;
                case java.awt.geom.PathIterator.SEG_CUBICTO:
                    transform(coords, 3);
                    newGeneralPath.curveTo((float) coords[0], (float) coords[1],
                            (float) coords[2], (float) coords[3],
                            (float) coords[4], (float) coords[5]);
                    break;
                default:
                    throw new IllegalArgumentException();
            }
            // move to next segment
            pi.next();
        }

        return newGeneralPath;
    }

    public ika.geo.GeoPath transform(ika.geo.GeoPath geoPath) {
        ika.geo.GeoPath newGeoPath = new ika.geo.GeoPath();
        java.awt.geom.GeneralPath transformedPath = this.transform(geoPath.getPath());
        newGeoPath.setPath(transformedPath);
        newGeoPath.setVectorSymbol(geoPath.getVectorSymbol().copy());
        return newGeoPath;
    }

    public GeoSet transform(GeoSet geoSet) {
        if (geoSet == null) {
            throw new IllegalArgumentException();
        }

        GeoSet newGeoSet = new GeoSet();
        final int nbrGeoObjects = geoSet.getNumberOfChildren();
        for (int i = 0; i < nbrGeoObjects; ++i) {
            GeoObject geoObject = geoSet.getGeoObject(i);
            GeoObject transformedGeoObj = null;
            if (geoObject instanceof GeoSet) {
                transformedGeoObj = this.transform((GeoSet) geoObject);
            } else if (geoObject instanceof GeoPath) {
                transformedGeoObj = this.transform((GeoPath) geoObject);
            } else if (geoObject instanceof GeoPoint) {
                transformedGeoObj = this.transform((GeoPoint) geoObject);
            }
            newGeoSet.addGeoObject(transformedGeoObj);
        }
        return newGeoSet;
    }

    public GeoPoint transform(GeoPoint geoPoint) {
        if (geoPoint == null) {
            throw new IllegalArgumentException();
        }
        double point[] = {geoPoint.getX(), geoPoint.getY()};
        this.transform(point, 1);
        GeoPoint newGeoPoint = new GeoPoint(point[0], point[1]);
        return newGeoPoint;
    }

    /**
     * Utility that sums the square values of a vector.
     *
     * @param v an array with a single column
     * @return The sum of the square values of the elements of vector v.
     */
    protected final static double vTv(double[][] v) {
        if (v[0].length != 1) {
            throw new IllegalArgumentException();
        }
        double vTv = 0;
        for (int i = 0; i < v.length; i++) {
            final double a = v[i][0];
            vTv += a * a;
        }
        return vTv;
    }

    /**
     * Utility that sums the square values of a vector.
     *
     * @param v a Jama.Matrix with a single column
     * @return The sum of the square values of the elements of vector v.
     */
    protected final static double vTv(Jama.Matrix v) {
        return Transformation.vTv(v.getArray());
    }

    /**
     * Utility for formatting a double value. The resulting number won't have
     * any decimal digits.
     *
     * @param value The value to convert to a string.
     * @return The value converted to a string.
     */
    protected String formatNoDecimal(double value) {
        return NumberFormatter.format(value, 20, 0, 0);
    }

    /**
     * Utility for formatting a double value. The resulting number will be quite
     * long, and have some decimal digits.
     *
     * @param value The value to convert to a string.
     * @return The value converted to a string.
     */
    protected String formatPrecise(double value) {
        return NumberFormatter.format(value, 25, 10, 0);
    }

    /**
     * Utility for formatting a double value. The resulting number will be
     * rather short, and have some decimal digits.
     *
     * @param value The value to convert to a string.
     * @return The value converted to a string.
     */
    protected String formatPreciseShort(double value) {
        return NumberFormatter.format(value, 12, 5, 0);
    }

    protected double geometricRadiantToAzimuthDegree(double rad) {
        double azimuth = -Math.toDegrees(rad) + 90.;
        if (azimuth < 0) {
            azimuth += 360;
        }
        return azimuth;
    }

    protected String formatSigma0(double sigma0) {

        java.text.DecimalFormat sigma0Formatter
                = new java.text.DecimalFormat(sigma0 < 10 ? "###,##0.0####" : "###,###");

        StringBuilder str = new StringBuilder();
        /*
        str.append("Mean Error ");
        str.append("\u03c3");   // "greek small letter sigma"
        str.append("\u02f3");   // "modifier letter low ring"
        str.append(":\t");
        str.append("\u00b1");    // plus-minus sign
        str.append(sigma0Formatter.format(sigma0));
        str.append("m");
         */
        str.append("Std. Deviation:\t");
        str.append("\u00b1");    // plus-minus sign
        str.append(sigma0Formatter.format(sigma0));
        str.append("m");

        return str.toString();
    }

    protected String formatStandardErrorOfPosition(double stdErrPos) {

        DecimalFormat formatter
                = new DecimalFormat(stdErrPos < 1 ? "###,##0.0####" : "###,###");

        StringBuilder str = new StringBuilder();
        // use short string with "err." instead of "error" for proper alignment
        str.append("Mean Pos. Err.:\t");
        str.append("\u00b1");    // plus-minus sign
        str.append(formatter.format(stdErrPos));
        str.append("m");

        return str.toString();
    }

    public String formatRotation(String label, double rad) {
        DecimalFormat angleFormatter = new DecimalFormat("###");
        String suffix = "[cw]";
        double deg = Math.toDegrees(rad);
        if (deg < 0) {
            deg += 360;
        }
        if (deg > 180) {
            deg = 360 - deg;
            suffix = "[ccw]";
        }
        StringBuilder str = new StringBuilder();
        if (label != null) {
            str.append(label);
            str.append(":\t");
        }
        str.append(angleFormatter.format(deg));
        str.append("\u00b0 ");    // degree sign
        str.append(suffix);

        return str.toString();
    }
}
