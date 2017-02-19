/*
 * SelectionTool.java
 *
 * Created on April 7, 2005, 7:21 PM
 */

package ika.map.tools;

import java.awt.geom.*;
import java.awt.event.*;
import ika.geo.*;
import ika.gui.MapComponent;

/**
 * SelectionTool - a tool to select GeoObjects by mouse clicks and mouse drags.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class SelectionTool extends RectangleTool {
    
    /**
     * Tolerance for selection of objects by mouse clicks.
     */
    protected static final int CLICK_PIXEL_TOLERANCE = 2;
    
    /**
     * Create a new instance.
     * @param mapComponent The MapComponent for which this MapTool provides its services.
     */
    public SelectionTool(MapComponent mapComponent) {
        super(mapComponent);
    }
    
    /**
     * A drag ends, while this MapTool was the active one.
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     */
    public void endDrag(Point2D.Double point, MouseEvent evt) {
        Rectangle2D.Double rect = this.getRectangle();
        super.endDrag(point, evt);
        
        final GeoSet geoSet = mapComponent.getGeoSet();
        final double scale = mapComponent.getScaleFactor();
        final boolean extendSelection = evt.isShiftDown();
        if (geoSet != null && rect != null) {
            geoSet.selectByRectangle(rect, scale, extendSelection);
            
            // inform MapToolActionListeners about action
            this.informMapToolActionListeners("Select");
        }
        setDefaultCursor();
    }
    
    /**
     * The mouse was clicked, while this MapTool was the active one.
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     */
    public void mouseClicked(Point2D.Double point, MouseEvent evt) {
        //super.mouseClicked(point, evt);
        
        // try selecting objects close to the mouse click.
        final boolean selectionChanged = selectWithMouseClick(point, evt);
        
        // inform MapToolActionListeners about action
        if (selectionChanged)
            this.informMapToolActionListeners("Select");
    }
    
    /**
     * Utility method that selects GeoObjects close to a mouse click.
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     * @return true if the selection state of any object has been changed. This
     * method also returns true, when an object has been deselected.
     */
    protected boolean selectWithMouseClick(Point2D.Double point, MouseEvent evt) {
        GeoSet geoSet = mapComponent.getGeoSet();
        if (geoSet == null)
            return false;
        final double scale = mapComponent.getScaleFactor();
        final boolean extendSelection = evt.isShiftDown();
        final double worldCoordTol = CLICK_PIXEL_TOLERANCE/scale;
        return geoSet.selectByPoint(point, scale, extendSelection, worldCoordTol);
    }
    
    protected String getCursorName() {
        return "selectionarrow";
    }
}
