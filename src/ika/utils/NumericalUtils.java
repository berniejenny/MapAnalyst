/*
 * NumericalUtils.java
 *
 * Created on May 10, 2005, 3:21 PM
 */

package ika.utils;

public class NumericalUtils {
    
    public static boolean numbersAreClose (double x, double y) {
        final double TOL = 0.000000001;
        return NumericalUtils.numbersAreClose (x, y, TOL);
    }
    public static boolean numbersAreClose (double x, double y, double tolerance) {
        return (Math.abs(x-y) < tolerance);
    }
    
}
