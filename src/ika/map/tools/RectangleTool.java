/*
 * RectangleTool.java
 *
 * Created on April 8, 2005, 2:47 PM
 */

package ika.map.tools;

import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import ika.gui.MapComponent;

/**
 * RectangleTool - an abstract tool that draws a rectangle when dragging the mouse.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public abstract class RectangleTool extends DoubleBufferedTool {
    
    /**
     * The start position of the drag.
     */
    protected Point2D.Double dragStartPos;
    
    /**
     * The current position of the drag.
     */
    protected Point2D.Double dragCurrentPos;
    
    /**
     * The dash length used for drawing the rectangle with dashes.
     */
    protected static final int DASH_LENGTH = 3;
    
    /**
     * MIN_RECT_DIM_PX is used by isRectangleLargeEnough to test whether
     * the currently drawn rectangle is considered to be large enough.
     * E.g. with the magnifier tool, if the user only draws a rectangle of
     * 1x1 pixel, the view will not zoom to such a small area, which would
     * be confusing.
     * Unit: screen pixel
     */
    protected static final int MIN_RECT_DIM_PX = 3;
    
    /**
     * Create a new instance.
     * @param mapComponent The MapComponent for which this MapTool provides its services.
     */
    protected RectangleTool(MapComponent mapComponent) {
        super(mapComponent);
    }
    
    /**
     * The mouse starts a drag, while this MapTool was the active one.
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     */
    public void startDrag(Point2D.Double point, MouseEvent evt) {
        dragStartPos = (Point2D.Double)point.clone();
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
            return;
        }
        
        // if this is the first time mouseDragged is called, capture the screen.
        if (dragCurrentPos == null)
            captureBackground();
        
        dragCurrentPos = (Point2D.Double)point.clone();
        mapComponent.repaint();
    }
    
    /**
     * A drag ends, while this MapTool was the active one.
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     */
    public void endDrag(Point2D.Double point, MouseEvent evt) {
        // release the corner points of the drag rectangle
        this.dragStartPos = this.dragCurrentPos = null;
        
        releaseBackground();
        mapComponent.repaint();
    }
    
    /**
     * The mouse was clicked, while this MapTool was the active one.
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     */
    public void mouseClicked(Point2D.Double point, MouseEvent evt) {
        this.dragStartPos = this.dragCurrentPos = null;
        releaseBackground();
        mapComponent.repaint();
    }
    
    /**
     * Treat escape key events. Stop drawing the rectangle and revert to the
     * previous state, without doing anything.
     * The event can be consumed (return true) or be delegated to other
     * listeners (return false).
     * @param keyEvent The new key event.
     * @return True if the key event has been consumed, false otherwise.
     */
    public boolean keyEvent(KeyEvent keyEvent) {
        final boolean keyReleased = keyEvent.getID() == KeyEvent.KEY_RELEASED;
        final boolean isEscapeKey = keyEvent.getKeyCode() == KeyEvent.VK_ESCAPE;
        
        if (keyReleased && isEscapeKey) {
            // release the corner points of the drag rectangle
            this.dragStartPos = this.dragCurrentPos = null;
            
            // repaint the map
            releaseBackground();
            mapComponent.repaint();
        }
        
        return false;
    }
    
    /**
     * Draw the interface elements of this MapTool.
     * @param rp The destination to draw to.
     */
    public void draw(Graphics2D g2d) {
        if (dragStartPos == null || dragCurrentPos == null)
            return;
        
        // disable antialiasing
       g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF);
        // disable high quality rendering
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_SPEED);
        
        // setup stroke
        final float scale = (float)this.mapComponent.getScaleFactor();
        g2d.setColor(Color.black);
        final double strokeWidth = 1. / scale;
        float[] dashArray = new float[] {DASH_LENGTH/scale, DASH_LENGTH/scale};
        BasicStroke stroke = new BasicStroke((float)strokeWidth,
                BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, dashArray, 0);
        g2d.setStroke(stroke);
        
        // draw 4 lines forming a rectangle.
        // drawing individual lines reduces the "marching ants effect".
        Rectangle2D.Double rect = this.getRectangle();
        Line2D line = new Line2D.Double();
        final double west = rect.getMinX();
        final double east = rect.getMaxX();
        final double south = rect.getMinY();
        final double north = rect.getMaxY();
        line.setLine(west, south, east, south);
        g2d.draw(line);
        line.setLine(east, north, east, south);
        g2d.draw(line);
        line.setLine(west, north, east, north);
        g2d.draw(line);
        line.setLine(west, north, west, south);
        g2d.draw(line);
    }
    
    /**
     * Returns the rectangle formed by the start location and the current drag location.
     * @return The rectangle.
     */
    protected Rectangle2D.Double getRectangle() {
        if (dragStartPos == null || dragCurrentPos == null)
            return null;
        final double x = Math.min(dragStartPos.getX(), dragCurrentPos.getX());
        final double y = Math.min(dragStartPos.getY(), dragCurrentPos.getY());
        final double w = Math.abs(dragCurrentPos.getX()-dragStartPos.getX());
        final double h = Math.abs(dragCurrentPos.getY()-dragStartPos.getY());
        return new Rectangle2D.Double(x, y, w, h);
    }
    
    /**
     * Returns whether the the currently drawn rectangle is considered to be large enough.
     * E.g. with the magnifier tool, if the user only draws a rectangle of
     * 1x1 pixel, the view will not zoom to such a small area, which would
     * be confusing.
     */
    protected boolean isRectangleLargeEnough() {
        final Rectangle2D.Double rect = this.getRectangle();
        if (rect == null)
            return false;
        final double minRectDim = MIN_RECT_DIM_PX / this.mapComponent.getScaleFactor();
        return (rect.width >= minRectDim && rect.height >= minRectDim);
    }
}
