/*
 * ColorUtils.java
 *
 * Created on May 16, 2005, 8:47 PM
 */

package ika.utils;

import java.awt.*;

/**
 * Utility methods for color related stuff.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class ColorUtils {
    
    /**
     * Converts a Color to a CSS string of the format rgb(12,34,56)
     * @param color The color to convert.
     * @return The CSS string.
     */
    public static String colorToCSSString(Color color) {
        StringBuffer str = new StringBuffer();
        str.append("rgb(");
        str.append(color.getRed());
        str.append(",");
        str.append(color.getGreen());
        str.append(",");
        str.append(color.getBlue());
        str.append(")");
        return str.toString();
    }
    
    /**
     * Returns a highlight-color that can be used to draw selected objects.
     * @return The color to use for selected objects.
     */
    public static final java.awt.Color getSelectionColor() {
        return java.awt.Color.red;
    }
    
    /**
     * Returns a highlight-color that can be used to draw selected objects.
     * @return The color to use for selected objects.
     */
    public static final java.awt.Color getHighlightColor() {
        return new java.awt.Color (75, 123, 181);
    }
}