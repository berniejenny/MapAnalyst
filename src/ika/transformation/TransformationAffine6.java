/*
 * TransformationAffine6
 *
 * Created on March 23, 2005, 12:10 PM
 */

package ika.transformation;

import Jama.*;
import java.io.*;
import ika.utils.*;

/**
 * Affine Transformation with 6 parameters:
 * translation in horizontal and vertical direction, two rotation angles
 * and two scale factors.
 * Transformation:
 * Xi = a1 + a2*xi + a3*yi
 * Yi = b1 + b2*xi + b3*yi
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich
 */

public class TransformationAffine6 extends Transformation implements Serializable {

    public static void main(String[] args) {
        TransformationAffine6 t = new TransformationAffine6();

        double[][] srcPoints = new double[][]{
            {291.038074, 790.062628},
            {1319.870845, 201.478064},
            {658.054894, 679.173371},
            {1526.325668, 671.249458}
        };

        double[][] dstPoints = new double[][]{
            {611276.093272,	267705.809892},
            {657906.982493,	259807.101659},
            {626390.864067,	267229.789865},
            {658249.068840,	275199.310619}
        };
        t.init(dstPoints, srcPoints);

    }

    private static final long serialVersionUID = -7509256281043772488L;
    
    
    /**
     * a1 = X0
     */
    protected double a1;
    
    /**
     * a2 = mx*cos(alphax)
     */
    protected double a2;
    
    /**
     * a3 = -my*sin(alphay)
     */
    protected double a3;
    
    /**
     * b1 = Y0
     */
    protected double b1;

    /**
     * b2 = mx * sin(alphax)
     */
    protected double b2;

    /**
     * b3 = my * cos(alphay)
     */
    protected double b3;

    /**
     * Sigma 0 is the error per unit.
     */
    private double sigma0;
    
    /**
     * root mean square sum of all the residuals
     * this value is indicated by ArcGIS in the Georeferencing dialog
     */
    private double totalRMSError_after_ArcGIS;

    /**
     * Sigma of the translation parameters. Horizontally and vertically equal.
     */
    private double transSigma;
    /**
     * The sigma of the horizontal scale parameter.
     */
    private double scaleXSigma;
    /**
     * The sigma of the vertical scale parameter.
     */
    private double scaleYSigma;
    /**
     * The sigma of the t rotation parameter.
     */
    private double rotXSigma;
    
    /**
     * The sigma of the y rotation parameter.
     */
    private double rotYSigma;
    
    /**
     * Returns the name of the transformation.
     * @return The name.
     */
    public String getName() {
        return "Affine (6 Parameters)";
    }
    
    /**
     * Returns a short description of this transformation.
     * getShortDescription can be called before the transformation is
     * initialized using initWithPoints
     * @return The description.
     */
    public String getShortDescription() {
        String nl = System.getProperty("line.separator");
        StringBuffer str = new StringBuffer(1024);
        str.append(this.getName());
        str.append(nl);
        str.append("6 Parameters:" + nl);
        
        str.append("X = x0 + mx*cos(alpha)*x - my*sin(beta)*y" + nl);
        str.append("Y = y0 + mx*sin(alpha)*x + my*cos(beta)*y" + nl);
        str.append("a1 = x0" + nl);
        str.append("a2 = mx*cos(alpha)" + nl);
        str.append("a3 = my*sin(beta)" + nl);
        str.append("b1 = y0" + nl);
        str.append("b2 = mx*sin(alpha)" + nl);
        str.append("b3 = my*cos(beta)" + nl);
        
        str.append("x0:    Horizontal Translation" + nl);
        str.append("y0:    Vertical Translation" + nl);
        str.append("mx:    Horizontal Scale Factor" + nl);
        str.append("my:    Vertical Scale Factor" + nl);
        str.append("alpha: Rotation in Counter-Clockwise Direction for Horizontal Axis." + nl);
        str.append("beta:  Rotation in Counter-Clockwise Direction for Vertical Axis." + nl);
        return str.toString();
    }
    
    /**
     * Returns a report containing the computed parameters of this
     * transformation.9
     * @return The description.
     */
    public String getReport(boolean invert) {
        String nl = System.getProperty("line.separator");
        StringBuffer str = new StringBuffer(1024);
        str.append(this.getShortDescription());
        str.append(getBreakLineForReport());
        
        str.append("Transformation parameters and standard deviations computed with ");
        str.append(this.getNumberOfPoints());
        str.append(" points:" + nl + nl);
        str.append("x0 Translation Horizontal:                         ");
        str.append(formatPrecise(this.getTranslationX()));
        str.append(" +/-");
        str.append(formatPreciseShort(this.getTranslationXSigma()));
        str.append(nl);
        str.append("y0 Translation Vertical:                           ");
        str.append(formatPrecise(this.getTranslationY()));
        str.append(" +/-");
        str.append(formatPreciseShort(this.getTranslationYSigma()));
        str.append(nl);
        str.append("alpha Horizontal Rotation [deg ccw]:               ");
        str.append(formatPrecise(Math.toDegrees(this.getRotationX(invert))));
        str.append(" +/-");
        str.append(formatPreciseShort(Math.toDegrees(this.getRotationXSigma())));
        str.append(nl);
        str.append("beta Vertical Rotation [deg ccw]:                  ");
        str.append(formatPrecise(Math.toDegrees(this.getRotationY(invert))));
        str.append(" +/-");
        str.append(formatPreciseShort(Math.toDegrees(this.getRotationYSigma())));
        str.append(nl);
        
        final double scaleX = this.getScaleX(invert);
        final double scaleY = this.getScaleY(invert);
        final boolean invertScale = scaleX < SCALE_TO_INVERT && scaleY < SCALE_TO_INVERT;

        if (invertScale) {
            str.append("mx Horizontal Scale Factor (inverted):             ");
        } else {
            str.append("mx Horizontal Scale Factor:                        ");
        }
        str.append(formatPrecise(invertScale ? 1. / scaleX : scaleX));
        str.append(" +/-");
        final double sXSigma = this.getScaleXSigma(invert);
        str.append(formatPreciseShort(invertScale ? scaleX / sXSigma : sXSigma));
        str.append(nl);

        if (invertScale) {
            str.append("my Vertical Scale Factor (inverted):               ");
        } else {
            str.append("my Vertical Scale Factor:                          ");
        }
        str.append(formatPrecise(invertScale ? 1. / scaleY : scaleY));
        str.append(" +/-");
        final double sYSigma = this.getScaleYSigma(invert);
        str.append(formatPreciseShort(invertScale ? scaleY / sYSigma : sYSigma));
        str.append(nl);
        
        str.append("a2:                                                ");
        str.append(formatPrecise(this.a2));
        str.append(nl);
        str.append("a3:                                                ");
        str.append(formatPrecise(this.a3));
        str.append(nl);
        str.append("b2:                                                ");
        str.append(formatPrecise(this.b2));
        str.append(nl);
        str.append("b3:                                                ");
        str.append(formatPrecise(this.b3));
        
        str.append(getBreakLineForReport());
        str.append("Standard deviation and root mean square position error for all points:" + nl + nl);
        str.append(getPointAccuracyReport(invert));

        str.append(nl);
        str.append("Root mean square sum of all residuals: ");
        str.append(formatPreciseShort(totalRMSError_after_ArcGIS));
        str.append(nl);
        
        return str.toString();
    }
    
    public String getShortReport(boolean invert) {
        String nl = System.getProperty("line.separator");
        StringBuffer str = new StringBuffer(1024);
        
        str.append(NumberFormatter.formatScale("Scale Hor.", this.getScaleX(invert)));
        str.append(nl);
        
        str.append(NumberFormatter.formatScale("Scale Vert.", this.getScaleY(invert)));
        str.append(nl);
        
        str.append (this.formatRotation("Rotation X", this.getRotationX(invert)));
        str.append(nl);
        
        str.append (this.formatRotation("Rotation Y", this.getRotationY(invert)));
        str.append(nl);
        
        double scale = this.getScale(invert);
        if (!invert)
            scale = 1;
        str.append(this.formatSigma0(this.getSigma0() * scale));
        str.append(nl);
        str.append(this.formatStandardErrorOfPosition(this.getStandardErrorOfPosition() * scale));
        str.append(nl);
        
        return str.toString();
    }
    
    /**
     * Returns the horizontal rotation used by this transformation.
     * @return The rotation.
     */
    public double getRotationX(boolean invert) {
        double rotX = Math.atan2(b2, a2);
        while (rotX < 0.)
            rotX += Math.PI * 2.;
        return invert ? -rotX : rotX;
    }
    
    /**
     * Returns the precision of the horizontal rotation.
     * @return The precision.
     */
    public double getRotationXSigma() {
        return this.rotXSigma;
    }
    
    /**
     * Returns the vertical rotation used by this transformation.
     * @return The rotation.
     */
    public double getRotationY(boolean invert) {
        double rotY = Math.atan2(-a3, b3);
        while (rotY < 0.)
            rotY += Math.PI * 2.;
        return invert ? -rotY : rotY;
    }
    
    public double getRotation() {
        double rot = (this.getRotationX(false) + this.getRotationY(false)) * 0.5;
        if (rot < 0.)
            rot += Math.PI * 2.;
        if (rot > Math.PI * 2.)
            rot -= Math.PI * 2.;
        return rot;
    }
    
    /**
     * Returns the precision of the vertical rotation.
     * @return The precision.
     */
    public double getRotationYSigma() {
        return this.rotYSigma;
    }
    
    public double getScale() {
        return (this.getScaleX(false) + this.getScaleY(false)) / 2.;
    }
    
    /**
     * Returns the scale factor in horizontal direction used by this transformation.
     * @return The scale factor.
     */
    public double getScaleX(boolean invert) {
        final double scale = Math.sqrt(a2*a2+b2*b2);
        return invert ? 1. / scale : scale;
    }
    
    /**
     * Returns the precision of the scale factor in horizontal direction .
     * @return The precision of the scale factor.
     */
    public double getScaleXSigma(boolean invert) {
        if (invert) {
            final double scaleX = getScaleX(false);
            return this.scaleXSigma/(scaleX*scaleX);
        }
        return this.scaleXSigma;
    }
    
    /**
     * Returns the scale factor in vertical direction used by this transformation.
     * @return The scale factor.
     */
    public double getScaleY(boolean invert) {
        final double scale = Math.sqrt(a3*a3+b3*b3);
        return invert ? 1. / scale : scale;
    }
    
    /**
     * Returns the precision of the scale factor in vertical direction .
     * @return The precision of the scale factor.
     */
    public double getScaleYSigma(boolean invert) {
        if (invert) {
            final double scaleY = getScaleY(false);
            return this.scaleYSigma/(scaleY*scaleY);
        }
        return this.scaleYSigma;
    }
    
    /**
     * Returns the horizontal translation parameter
     * used by this transformation
     * @return The horizontal translation.
     */
    public double getTranslationX() {
        return a1;
    }
    
    /**
     *  Returns the precision of the horizontal translation
     * paramater used by this transformation.
     * @return The precision of the horizontal translation.
     */
    public double getTranslationXSigma() {
        return this.transSigma;
    }
    
    /**
     *  Returns the vertical translation parameter
     * used by this transformation.
     * @return The vertical translation.
     */
    public double getTranslationY() {
        return b1;
    }
    
    /**
     *   Returns the precision of the vertical translation
     * paramater used by this transformation.
     * @return The precision of the vertical translation.
     */
    public double getTranslationYSigma() {
        return this.transSigma;
    }
    
    /**
     * Returns sigma 0.
     * @return sigma 0
     */
    public double getSigma0() {
        return this.sigma0;
    }
    
    /**
     * Initialize the transformation with two set of control points.
     * The control points are not copied, nor is a reference to them
     * retained. Instead, the transformation parameters are
     * immmediately computed by initWithPoints.
     * @param dstPoints A two-dimensional array (nx2) containing the x and y
     * coordinates of the destination points.
     * The destSet must be of exactly the same size as sourceSet.
     * @param srcPoints A two-dimensional array (nx2) containing the x and y
     * coordinates of the source points.
     * The sourceSet must be of exactly the same size as destSet.
     */
    protected void initWithPoints(double[][] dstPoints, double[][] srcPoints) {

        /* Transformation:
         * Xi = a1 + a2*xi + a3*yi
         * Yi = b1 + b2*xi + b3*yi
         * Parameters a1, a2 and a3 for X can be computed independently of
         * the parameters b1, b2 and b3 for Y. This reduces the sizes of the
         * matrices to invert and multiply, and thereby accelerates computations.
         * Hence, solve two equation systems:
         * x = Aa
         * y = Ab
         * where:
         *
         *     |X1|
         * x = |..|
         *     |Xn|
         *
         *     |Y1|
         * y = |..|
         *     |Yn|
         *
         *     |1 x1 y1|
         * A = |. .  . |
         *     |1 xn yn|
         *
         *     |a1|
         * a = |a2|
         *     |a3|
         *
         *     |b1|
         * b = |b2|
         *     |b3|
         *
         * improvements u and w:
         * u = Aa-x
         * w = Aa-y
         *
         * solution for a and b:
         * a = (ATA)'ATx
         * b = (ATA)'ATy
         *
         * Estimation of precision:
         * sigma0 = sqrt((uTu+wTw)/(2n-6)) with n = number of points
         *
         * For more details see:
         * Beineke, D. (2001). Verfahren zur Genauigkeitsanalyse für Altkarten.
         */

        // allocate matrices x, y, and A.
        final int nPoints = this.getNumberOfPoints();
        Matrix mat_x = new Matrix(nPoints, 1);
        double[][] xArray = mat_x.getArray();
        Matrix mat_y = new Matrix(nPoints, 1);
        double[][] yArray = mat_y.getArray();
        Matrix mat_A = new Matrix(nPoints, 3);
        double[][] AArray = mat_A.getArray();

        // initialize matrices x, y, and A.
        for (int i = 0; i < nPoints; i++) {
            xArray[i][0] = dstPoints[i][0];
            yArray[i][0] = dstPoints[i][1];
            AArray[i][0] = 1.;
            AArray[i][1] = srcPoints[i][0];
            AArray[i][2] = srcPoints[i][1];
        }

        // compute a and b
        Matrix mat_Atrans = mat_A.transpose();
        Matrix mat_Q = mat_Atrans.times(mat_A).inverse();
        Matrix mat_a = mat_Q.times(mat_Atrans.times(mat_x));
        Matrix mat_b = mat_Q.times(mat_Atrans.times(mat_y));

        // compute residuals u, w
        Matrix mat_u = mat_A.times(mat_a).minus(mat_x);
        Matrix mat_w = mat_A.times(mat_b).minus(mat_y);

        // compute  vTv and Sigma aposteriori (sigma0)
        final double vTv = Transformation.vTv(mat_u) + Transformation.vTv(mat_w);
        final double sigma0square = vTv / (2 * nPoints - 6);
        this.sigma0 = Math.sqrt(sigma0square);
        this.totalRMSError_after_ArcGIS = Math.sqrt(vTv/nPoints);

        // copy residuals to this.v
        final double[][] v_x = mat_u.getArray();
        final double[][] v_y = mat_w.getArray();
        for (int i = 0; i < nPoints; i++) {
            this.v[i][0] = v_x[i][0];
            this.v[i][1] = v_y[i][0];
        }

        // copy paramters to instance variables
        this.a1 = mat_a.get(0,0);
        this.a2 = mat_a.get(1,0);
        this.a3 = mat_a.get(2,0);
        this.b1 = mat_b.get(0,0);
        this.b2 = mat_b.get(1,0);
        this.b3 = mat_b.get(2,0);

        // compute standard deviations of parameters, see Beineke p. 18
        final double s1 = Math.sqrt(sigma0square * mat_Q.get(0,0));
        final double s2 = Math.sqrt(sigma0square * mat_Q.get(1,1));
        final double s3 = Math.sqrt(sigma0square * mat_Q.get(2,2));
        this.transSigma = s1;
        this.scaleXSigma = s2;
        this.scaleYSigma = s3;
        this.rotXSigma = s2 / this.getScaleX(false);
        this.rotYSigma = s3 / this.getScaleY(false);

    }
    
    /**
     * Transform a point from the coordinate system of the source set
     * to the coordinate system of the destination set.
     * @return The transformed coordinates (array of size 1x2).
     * @param point The point to be transformed (array of size 1x2).
     */
    public double[] transform(double[] point) {
        final double x = point[0];
        final double y = point[1];
        return new double[]{
                    a1 + a2 * x + a3 * y,
                    b1 + b2 * x + b3 * y};
    }
    
    /**
     * Transform an array of points from the coordinate system of the source set
     * to the coordinate system of destination set.
     * The transformed points overwrite the original values in the points[] array.
     * @param points The point to be transformed.
     * @param xid The column of points[] containing the t-coordinate.
     * @param yid The column of points[] containing the y-coordinate.
     */
    @Override
    public void transform(double[][] points, int xid, int yid) {
        for (int i = 0; i < points.length; i++) {
            final double x = points[i][xid];
            final double y = points[i][yid];
            points[i][xid] = a1 + a2 * x + a3 * y;
            points[i][yid] = b1 + b2 * x + b3 * y;
        }
    }
    
    public java.awt.geom.AffineTransform getAffineTransform() {
       return null; 
    }
}
