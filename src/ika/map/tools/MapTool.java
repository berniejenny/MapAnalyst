/*
 * MapTool.java
 *
 * Created on April 7, 2005, 7:17 PM
 */

package ika.map.tools;

import ika.geo.GeoSet;
import ika.gui.MapComponent;
import ika.utils.CursorUtils;
import java.awt.Cursor;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;

/**
 * MapTool - an abstract base class for map tools. A MapTool offers some kind of
 * interactivity based on mouse events.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public abstract class MapTool {
    
    /**
     * The MapComponent for which this MapTool provides its services.
     */
    protected MapComponent mapComponent;
    
    private final ArrayList<MapToolActionListener> mapToolActionListeners = new ArrayList<>();
    
    protected GeoSet destinationGeoSet;
    
    /**
     * Create a new instance.
     * @param mapComponent The MapComponent for which this MapTool provides its services.
     */
    public MapTool(MapComponent mapComponent) {
        this.mapComponent = mapComponent;
    }
    
    public void addMapToolActionListener (MapToolActionListener listener) {
        this.mapToolActionListeners.add (listener);
    }
    
    public void removeMapToolActionListener (MapToolActionListener listener) {
        this.mapToolActionListeners.remove(listener);
    }
    
    public void clearMapToolActionListener() {
        this.mapToolActionListeners.clear();
    }
    
    protected void informMapToolActionListeners (String description) {
        final int nbrMapToolActionListener = this.mapToolActionListeners.size();
        for (int i = 0; i < nbrMapToolActionListener; i++) {
            MapToolActionListener listener = 
                    (MapToolActionListener)this.mapToolActionListeners.get(i);
            listener.mapToolActionPerformed(this, this.mapComponent, description);
        }
    }
    
    /**
     * This method is called when the MapTool is activated, i.e. made the current tool.
     */
    public void activate() {
    }
    
    /**
     * This method is called when the MapTool is deactivated, i.e. it is no longer the
     * current tool.
     */
    public void deactivate() {
        mapComponent.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
    }
    
    /**
     * pause is called when the MapTool is temporarily suspended, i.e. another
     * MapTool is activated for a certain time. pause will be balanced by a call
     * to resume.
     */
    public void pause(){
    }
    
    /**
     * resume is called when the MapTool was previously temporarily suspended,
     * and can resume again.
     */
    public void resume(){
    }
    
    /**
     * The mouse was clicked, while this MapTool was the active one.
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     */
    public void mouseClicked(Point2D.Double point, MouseEvent evt) {
    }
    
    /**
     * The mouse was pressed down, while this MapTool was the active one.
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     */
    public void mouseDown  (Point2D.Double point, MouseEvent evt) {
    }
    
    /**
     * The mouse moved, while this MapTool was the active one.
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     */
    public void mouseMoved(Point2D.Double point, MouseEvent evt) {
    }
    
    /**
     * The mouse entered the map, while this MapTool was the active one.
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     */
    public void mouseEntered(Point2D.Double point, MouseEvent evt) {
    }
    
    /**
     * The mouse exited the map, while this MapTool was the active one.
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     */
    public void mouseExited(Point2D.Double point, MouseEvent evt) {
    }
    
    /**
     * The mouse starts a drag, while this MapTool was the active one.
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     */
    public void startDrag(Point2D.Double point, MouseEvent evt) {
    }
    
    /**
     * The mouse location changed during a drag, while this MapTool was the active one.
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     */
    public void updateDrag(Point2D.Double point, MouseEvent evt) {
    }
    
    /**
     * A drag ends, while this MapTool was the active one.
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     */
    public void endDrag(Point2D.Double point, MouseEvent evt) {
        /** call mouseClicked
         * This makes sure, a derived tool receives a mouse-event if the mouse is
         * clicked, dragged to another position and released there. E.g. the
         * ZoomOutTool does not overwrite startDrag, updateDrag and endDrag and
         * would therefore not receive an event if the mouse is clicked, dragged
         * and released.
         */
        this.mouseClicked(point, evt);
    }

    /**
     * Indicates whether the tool is currently changing the objects in the map.
     * This is the case while objects are moved, for example with the MoverTool.
     * @return
     */
    public boolean isChangingGeoObject() {
        return false;
    }
    
    /**
     * Draw the interface elements of this MapTool.
     * @param g2d The destination to draw to.
     */
    public void draw(java.awt.Graphics2D g2d) {
    }
    
    /**
     * Returns the default cursor that is used while this MapTool is active.
     * @return The name of the cursor. See CursorUtils for possible names.
     */
    protected String getCursorName() {
        return "arrow";
    }
    
    /**
     * Sets the cursor icon to the default cursor specified by getCursorName.
     */
    public void setDefaultCursor() {
        final String cursorName = this.getCursorName();
        CursorUtils.setCursor(cursorName, this.mapComponent);
    }

    public GeoSet getDestinationGeoSet() {
        return destinationGeoSet;
    }

    public void setDestinationGeoSet(GeoSet destinationGeoSet) {
        this.destinationGeoSet = destinationGeoSet;
    }
}
