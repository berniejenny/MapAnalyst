/*
 * MoverTool.java
 *
 * Created on April 20, 2005, 7:14 PM
 */

package ika.map.tools;

import ika.geo.*;
import java.awt.geom.*;
import java.awt.event.*;
import ika.gui.MapComponent;

/**
 * MoverTool - a tool to move GeoObjects by dragging them with the mouse.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class MoverTool extends MapTool {
    
    /**
     * Remember the start location of the drag.
     */
    private Point2D.Double moveStartPos;
    
    /**
     * Create a new instance.
     * @param mapComponent The MapComponent for which this MapTool provides its services.
     */
    public MoverTool(MapComponent mapComponent) {
        super(mapComponent);
    }
    
    public void deactivate() {
        try {
            super.deactivate();
        } finally {
            // activate change listeners again
            GeoSet geoSet = mapComponent.getGeoSet();
            if (geoSet != null) {
                geoSet.activateGeoSetChangeListeners(this);
            }
        }
    }
    
    /**
     * The mouse starts a drag, while this MapTool was the active one.
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     */
    public void startDrag(Point2D.Double point, MouseEvent evt) {
        GeoSet geoSet= mapComponent.getGeoSet();
        if (geoSet != null) {
            
            // search if mouse started drag by a mouse down event on a GeoObject
            final double scale = mapComponent.getScaleFactor();
            final double worldCoordTol = /*CLICK_PIXEL_TOLERANCE*/2./scale;
            GeoObject geoObject = geoSet.getObjectAtPosition(point,
                    worldCoordTol, scale, true, true);
            
            if (geoObject == null || !geoObject.isSelected()) {
                this.moveStartPos = null;
            } else {
                this.moveStartPos = (Point2D.Double)point.clone();
            }
            
            // don't inform change listeners while dragging
            geoSet.suspendGeoSetChangeListeners();
        }
    }
    
    /**
     * The mouse location changed during a drag, while this MapTool was the active one.
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     */
    public void updateDrag(Point2D.Double point, MouseEvent evt) {
        if (this.moveStartPos == null){
            super.updateDrag(point, evt);
        } else {
            this.move(point);
        }
        
        /* Force redraw of map. Since change listeners are suspended while 
         dragging and one of the change listeners is repainting the map after 
         changes we have to force a redraw here. */
        this.mapComponent.repaint();
    }
    
    /**
     * A drag ends, while this MapTool was the active one.
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     */
    public void endDrag(Point2D.Double point, MouseEvent evt) {
        if (this.moveStartPos == null) {
            super.endDrag(point, evt);
        } else {
            this.move(point);
        }
        moveStartPos = null;
        
        // activate change listeners again
        GeoSet geoSet = mapComponent.getGeoSet();
        if (geoSet != null) {
            geoSet.activateGeoSetChangeListeners(this);
        }
        
        // inform MapToolActionListeners about action
        this.informMapToolActionListeners ("Move");
    }

    public boolean isChangingGeoObject() {
        return moveStartPos != null;
    }
    
    /**
     * Move the selected GeoObjects.
     * @param point The current location of the mouse.
     */
    private void move(Point2D.Double point) {
        final double dx = point.getX() - moveStartPos.getX();
        final double dy = point.getY() - moveStartPos.getY();
        this.moveStartPos = (Point2D.Double)point.clone();
        GeoSet geoSet = mapComponent.getGeoSet();
        if (geoSet != null) {
            geoSet.moveSelectedGeoObjects(dx, dy);
        }
    }
    
    protected String getCursorName() {
        return "movearrow";
    }
}
