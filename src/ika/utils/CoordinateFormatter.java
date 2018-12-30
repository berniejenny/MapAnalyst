/*
 * CoordinateFormatter.java
 *
 * Created on August 12, 2005, 5:54 AM
 *
 */
package ika.utils;

/**
 *
 * @author jenny
 */
public class CoordinateFormatter {

    /**
     * Coordinates are multiplied by scaleFactor before being converted to a
     * string. This scale factor can be used to transform between the global
     * mapping unit of the GeoObjects and the unit for displaying coordinates.
     * e.g. meter - centimeter -> scaleFactor = 100
     */
    private double scaleFactor = 1.d;

    private final java.text.DecimalFormat decimalFormat;
    private final java.text.DecimalFormat shortDecimalFormat;

    public CoordinateFormatter(String format, String shortFormat,
            double scaleFactor) {
        decimalFormat = new java.text.DecimalFormat(format);
        shortDecimalFormat = new java.text.DecimalFormat(shortFormat);
        this.scaleFactor = scaleFactor;
    }

    public String format(double number) {
        return decimalFormat.format(number * scaleFactor);
    }

    public String formatShort(double number) {
        return shortDecimalFormat.format(number * scaleFactor);
    }
}
