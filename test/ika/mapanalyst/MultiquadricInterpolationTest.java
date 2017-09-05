package ika.mapanalyst;

import junit.framework.TestCase;

/**
 *
 * @author Bernhard Jenny, Faculty of Information Technology, Monash University,
 * Melbourne, Australia
 */
public class MultiquadricInterpolationTest extends TestCase {

    public MultiquadricInterpolationTest(String testName) {
        super(testName);
    }

    /**
     * Test of solveCoefficients method, of class MultiquadricInterpolation.
     *
     * A test with a data set provided by Roel Nicolai. Source and destination
     * points are assumed to be in a common coordinate system for this test,
     * hence no affine transformation is applied to the source points.
     */
    public void testSolveCoefficients() {
        System.out.println("MultiquadricInterpolationTest: solveCoefficients");

        double[][] srcPoints = new double[17][2];
        double[][] dstPoints = new double[17][2];

        srcPoints[0][0] = 1200;
        srcPoints[0][1] = 1000;
        srcPoints[1][0] = 1800;
        srcPoints[1][1] = 1000;
        srcPoints[2][0] = 2000;
        srcPoints[2][1] = 1000;
        srcPoints[3][0] = 1000;
        srcPoints[3][1] = 1200;
        srcPoints[4][0] = 1600;
        srcPoints[4][1] = 1200;
        srcPoints[5][0] = 2000;
        srcPoints[5][1] = 1200;
        srcPoints[6][0] = 1000;
        srcPoints[6][1] = 1400;
        srcPoints[7][0] = 1400;
        srcPoints[7][1] = 1400;
        srcPoints[8][0] = 1600;
        srcPoints[8][1] = 1400;
        srcPoints[9][0] = 1200;
        srcPoints[9][1] = 1600;
        srcPoints[10][0] = 1400;
        srcPoints[10][1] = 1600;
        srcPoints[11][0] = 1800;
        srcPoints[11][1] = 1600;
        srcPoints[12][0] = 1400;
        srcPoints[12][1] = 1800;
        srcPoints[13][0] = 2000;
        srcPoints[13][1] = 1800;
        srcPoints[14][0] = 1000;
        srcPoints[14][1] = 2000;
        srcPoints[15][0] = 1600;
        srcPoints[15][1] = 2000;
        srcPoints[16][0] = 1800;
        srcPoints[16][1] = 2000;

        dstPoints[0][0] = 1220;
        dstPoints[0][1] = 1000;
        dstPoints[1][0] = 1800;
        dstPoints[1][1] = 980;
        dstPoints[2][0] = 1980;
        dstPoints[2][1] = 1020;
        dstPoints[3][0] = 1000;
        dstPoints[3][1] = 1240;
        dstPoints[4][0] = 1620;
        dstPoints[4][1] = 1220;
        dstPoints[5][0] = 2020;
        dstPoints[5][1] = 1180;
        dstPoints[6][0] = 980;
        dstPoints[6][1] = 1400;
        dstPoints[7][0] = 1400;
        dstPoints[7][1] = 1380;
        dstPoints[8][0] = 1560;
        dstPoints[8][1] = 1400;
        dstPoints[9][0] = 1220;
        dstPoints[9][1] = 1620;
        dstPoints[10][0] = 1400;
        dstPoints[10][1] = 1580;
        dstPoints[11][0] = 1820;
        dstPoints[11][1] = 1600;
        dstPoints[12][0] = 1360;
        dstPoints[12][1] = 1780;
        dstPoints[13][0] = 2020;
        dstPoints[13][1] = 1780;
        dstPoints[14][0] = 1000;
        dstPoints[14][1] = 2040;
        dstPoints[15][0] = 1620;
        dstPoints[15][1] = 1990;
        dstPoints[16][0] = 1780;
        dstPoints[16][1] = 1990;

        MultiquadricInterpolation mi = new MultiquadricInterpolation();
        mi.solveCoefficients(srcPoints, dstPoints, 1);

        // compute transformation along a profile
        final int N = 81;
        final double dx = 700;
        final double dy = 1400;
        double[][] xy = new double[N][2];
        for (int i = 0; i < N; i++) {
            xy[i][0] = dx + i * 20;
            xy[i][1] = dy;
        }

        mi.transform(xy);

        double[] u = new double[]{-9.65, -10.00, -10.38, -10.79, -11.23, -11.71, -12.23, -12.80, -13.43, -14.11, -14.86, -15.69, -16.61, -17.63, -18.75, -20.00, -16.70, -13.55, -10.57, -7.78, -5.21, -2.87, -0.79, 1.01, 2.51, 3.71, 4.59, 5.16, 5.43, 5.41, 5.12, 4.57, 3.78, 2.74, 1.48, 0.00, -2.90, -6.03, -9.38, -12.96, -16.78, -20.85, -25.20, -29.83, -34.76, -40.00, -34.25, -28.82, -23.72, -18.93, -14.47, -10.31, -6.45, -2.89, 0.37, 3.35, 6.04, 8.45, 10.61, 12.51, 14.17, 15.60, 16.80, 17.79, 18.57, 19.15, 19.55, 19.76, 19.83, 19.76, 19.57, 19.29, 18.94, 18.52, 18.07, 17.59, 17.09, 16.59, 16.07, 15.57, 15.06};
        double[] v = new double[]{32.57, 31.38, 30.11, 28.77, 27.34, 25.80, 24.14, 22.34, 20.39, 18.25, 15.90, 13.31, 10.45, 7.29, 3.82, 0.00, 1.63, 2.90, 3.81, 4.38, 4.61, 4.51, 4.10, 3.38, 2.36, 1.08, -0.45, -2.21, -4.16, -6.26, -8.47, -10.75, -13.07, -15.40, -17.72, -20.00, -17.90, -15.76, -13.60, -11.43, -9.29, -7.21, -5.20, -3.32, -1.58, 0.00, 0.35, 0.52, 0.50, 0.32, -0.03, -0.52, -1.15, -1.89, -2.74, -3.67, -4.66, -5.70, -6.77, -7.83, -8.86, -9.83, -10.72, -11.50, -12.15, -12.66, -13.02, -13.24, -13.31, -13.26, -13.09, -12.83, -12.49, -12.08, -11.63, -11.13, -10.60, -10.06, -9.49, -8.92, -8.34};
        
        for (int i = 0; i < N; i++) {
            double x = dx + i * 20;
            double y = dy;
            assertEquals(u[i], xy[i][0] - x, 0.01);
            assertEquals(v[i], xy[i][1] - y, 0.01);
        }

    }

}
