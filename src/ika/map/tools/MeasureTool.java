package ika.map.tools;

import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import java.util.*;
import ika.gui.MapComponent;

/**
 * MeasureTool - a tool to measure distances between two points.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class MeasureTool extends DoubleBufferedTool {
    
    /**
     * The location to start measuring.
     */
    private Point2D.Double dragStartPos;
    /**
     * The current position to measur to.
     */
    private Point2D.Double dragCurrentPos;
    
    /**
     * A set of MeasurToolListeners that will be informed when a new distance has
     * been computed.
     */
    private Set listeners = new HashSet();
    
    /**
     * Create a new instance.
     * @param mapComponent The MapComponent for which this MapTool provides its services.
     */
    public MeasureTool(MapComponent mapComponent) {
        super(mapComponent);
    }
    
    @Override
    public void deactivate() {
        reportClearDistance();
        mapComponent.repaint();
    }
    
    /**
     * Adds a MeasureToolListener.
     * @param listener The MeasureToolListener to add.
     */
    public void addMeasureToolListener(MeasureToolListener listener) {
        if (listener == null)
            throw new IllegalArgumentException();
        this.listeners.add(listener);
    }
    
    /**
     * Removes a MeasureToolListener.
     * @param listener The MeasureToolListener to remove.
     */
    public void removeMeasureToolListener(MeasureToolListener listener) {
        this.listeners.remove(listener);
    }
    
    /**
     * Inform all registered MeasureToolListeners of a new distance.
     */
    private void reportDistance() {
        if (dragStartPos == null || dragCurrentPos == null)
            return;
        
        final double dx = dragCurrentPos.x - dragStartPos.x;
        final double dy = dragCurrentPos.y - dragStartPos.y;
        final double d = Math.sqrt(dx*dx+dy*dy);
        final double angle = Math.atan2(dy, dx);
        Iterator iterator = this.listeners.iterator();
        while (iterator.hasNext()) {
            MeasureToolListener listener = (MeasureToolListener)iterator.next();
            listener.distanceChanged(d, angle, this.mapComponent);
            listener.positionChanged(dragCurrentPos.x, dragCurrentPos.y,
                    dragStartPos.x, dragStartPos.y, mapComponent);
        }
    }
    
    /**
     * Inform all registered MeasureToolListeners that the distance is not valid anymore.
     */
    private void reportClearDistance() {
        Iterator iterator = this.listeners.iterator();
        while (iterator.hasNext()) {
            MeasureToolListener listener = (MeasureToolListener)iterator.next();
            listener.clearDistance();
        }
    }
    
    /**
     * The mouse was pressed down, while this MapTool was the active one.
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     */
    public void mouseDown(Point2D.Double point, MouseEvent evt) {
        setMeasureCursor();
        captureBackground();
    }
    
    /**
     * The mouse starts a drag, while this MapTool was the active one.
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     */
    public void startDrag(Point2D.Double point, MouseEvent evt) {
        setMeasureCursor();
        this.dragStartPos = (Point2D.Double)point.clone();
    }
    
    /**
     * The mouse location changed during a drag, while this MapTool was the active one.
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     */
    public void updateDrag(Point2D.Double point, MouseEvent evt) {
        // just in case we didn't get a mousePressed-Event
        if (dragStartPos == null) {
            dragStartPos = (Point2D.Double)point.clone();
            setMeasureCursor();
            return;
        }
        
        // if this is the first time mouseDragged is called, capture the screen.
        if (dragCurrentPos == null)
            captureBackground();
        
        dragCurrentPos = (Point2D.Double)point.clone();
        mapComponent.repaint();
        
        reportDistance();
    }
    
    /**
     * A drag ends, while this MapTool was the active one.
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     */
    public void endDrag(Point2D.Double point, MouseEvent evt) {
        /*
        dragStartPos = null;
        dragCurrentPos = null;
         */
        releaseBackground();
        mapComponent.repaint();
        setDefaultCursor();
    }
    
    /**
     * Draw the interface elements of this MapTool.
     * @param g2d The destination to draw to.
     */
    public void draw(java.awt.Graphics2D g2d) {
        if (dragStartPos == null || dragCurrentPos == null)
            return;
        
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        
        final float scale = (float)this.mapComponent.getScaleFactor();
        g2d.setColor(Color.black);
        final double strokeWidth = 1. / mapComponent.getScaleFactor();
        final BasicStroke stroke = new BasicStroke((float)strokeWidth);
        g2d.setStroke(stroke);
        final Line2D line = new Line2D.Double(dragStartPos, dragCurrentPos);
        g2d.draw(line);
    }
    
    /**
     * Utility method to change the cursor to a cross-hair cursor.
     */
    private void setMeasureCursor() {
        mapComponent.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
    }
}
