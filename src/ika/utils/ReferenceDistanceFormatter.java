/*
 * CoordinateFormatter.java
 *
 * Created on August 12, 2005, 5:54 AM
 *
 */
package ika.utils;

import java.text.DecimalFormat;

/**
 * Formatter for distances in meters or kilometers.
 *
 * @author Bernhard Jenny, Faculty of Information Technology, Monash University,
 * Melbourne, Australia
 */
public class ReferenceDistanceFormatter extends CoordinateFormatter {

    private static final double THRESHOLD_DIST = 100 * 1000;

    private final java.text.DecimalFormat kmFormat = new DecimalFormat("###,##0.000 km");
    private final java.text.DecimalFormat shortKmFormat = new DecimalFormat("###,##0.### km");

    public ReferenceDistanceFormatter() {
        super("###,##0.00 m", "###,##0.###", 1);
    }

    @Override
    public String format(double number) {
        if (number > THRESHOLD_DIST) {
            return kmFormat.format(number / 1000);
        } else {
            return super.format(number);
        }
    }

    @Override
    public String formatShort(double number) {
        if (number > THRESHOLD_DIST) {
            return shortKmFormat.format(number / 1000);
        } else {
            return super.formatShort(number);
        }
    }
}
