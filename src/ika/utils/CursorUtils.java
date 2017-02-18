/*
 * CursorUtils.java
 *
 * Created on April 8, 2005, 1:53 PM
 */

package ika.utils;

import java.awt.*;
import java.awt.image.*;
import java.net.*;
import javax.swing.*;
import ika.icons.*;

/**
 * CursorUtils - a utility class to set the shape of the cursor.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class CursorUtils {
    
    /**
     * Set the cursor for a JComponent.
     * @param cursorName The name of the cursor. See loadCustomCursor for valid names.
     * @param jComponent The JComponent for which the cursor will be set.
     */
    public static void setCursor(String cursorName, JComponent jComponent) {
        Cursor cursor = loadCustomCursor(cursorName);
        jComponent.setCursor(cursor);
    }
    
    /**
     * Loads a custom cursor from a graphics file and configures the cursor.<br>
     * This is not how it should be done in a clean and portable program, but it works.
     * @param cursorName The name of the cursor. Have a look at the code for valid names.
     * @return The loaded cursor, or a default cursor if the specified cursor cannot be found.
     */
    public static Cursor loadCustomCursor(String cursorName) {
        
        /* If the system does not support custom cursor, getBestCursorSize
         *returns 0, 0. Return a default cursor in this case. */
        Dimension bestCursorSize = Toolkit.getDefaultToolkit().
                getBestCursorSize(32, 32);
        if (bestCursorSize.width == 0 || bestCursorSize.height == 0)
            return new Cursor(Cursor.DEFAULT_CURSOR);
        
        cursorName = cursorName.toLowerCase();
        
        String fileName = null;
        int backupCursorID = Cursor.DEFAULT_CURSOR;
        int x = 8;
        int y = 8;
        String accessibleCursorName = null;
        
        if (cursorName == "arrow") {
            return new Cursor(Cursor.DEFAULT_CURSOR);
        } else if (cursorName == "pan") {
            fileName = "Hand32x32.gif";
            accessibleCursorName = "Pan";
            backupCursorID = Cursor.MOVE_CURSOR;
        } else if (cursorName == "setpointarrow") {
            x = 0;
            y = 0;
            fileName = "SetPoint32x32.gif";
            accessibleCursorName = "Set Point";
            backupCursorID = Cursor.DEFAULT_CURSOR;
        } else if (cursorName == "selectionarrow") {
            x = 0;
            y = 0;
            fileName = "SelectPoints32x32.gif";
            accessibleCursorName = "Select Points";
            backupCursorID = Cursor.DEFAULT_CURSOR;
        } else if (cursorName == "movearrow") {
            x = 0;
            y = 0;
            fileName = "MovePoint32x32.gif";
            accessibleCursorName = "Move Points";
            backupCursorID = Cursor.DEFAULT_CURSOR;
        } else if (cursorName == "panclicked") {
            fileName = "ClosedHand32x32.gif";
            accessibleCursorName = "Pan";
            backupCursorID = Cursor.MOVE_CURSOR;
        } else if (cursorName == "polyselect") {
            x = 0;
            y = 0;
            fileName = "PolySelect32x32.gif";
            accessibleCursorName = "Select by Polygon";
            backupCursorID = Cursor.CROSSHAIR_CURSOR;
        } else if (cursorName == "pen") {
            fileName = "Pen32x32.gif";
            x = 4;
            y = 1;
            accessibleCursorName = "Pen";
            backupCursorID = Cursor.CROSSHAIR_CURSOR;
        } else if (cursorName == "zoomin") {
            fileName = "ZoomIn32x32.gif";
            x = 6;
            y = 6;
            accessibleCursorName = "Zoom In";
            backupCursorID = Cursor.HAND_CURSOR;
        } else if (cursorName == "zoomout") {
            fileName = "ZoomOut32x32.gif";
            x = 6;
            y = 6;
            accessibleCursorName = "Zoom Out";
            backupCursorID = Cursor.HAND_CURSOR;
        }
        try {
            ImageIcon imageIcon = IconLoader.createImageIcon(fileName, accessibleCursorName);
            Image image = imageIcon.getImage();
            // convert the Image to a BufferedImage with transparency
            BufferedImage bufferedImage = ImageUtils.makeBufferedImage(image,
                    BufferedImage.TYPE_INT_ARGB);
            return Toolkit.getDefaultToolkit().createCustomCursor(image,
                    (new Point(x, y)),accessibleCursorName);
        } catch (Exception exc) {
            Cursor cursor = new Cursor(backupCursorID);
            if (cursor == null)
                cursor = new Cursor(Cursor.DEFAULT_CURSOR);
            return cursor;
        }
    }
}