/*
 * MapComponent.java
 *
 * Created on 5. Februar 2005, 16:43
 */
package ika.gui;

import com.jhlabs.map.proj.Projection;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import ika.geo.*;
import ika.map.tools.*;
import ika.proj.ProjectionsManager;
import ika.utils.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * An interactive JComponent for drawing working with map data.<br>
 * This MapComponent contains a GeoMap that is used for all manipulations.
 *
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class MapComponent extends javax.swing.JComponent
        implements GeoSetChangeListener, GeoSetSelectionChangeListener {

    /**
     * The GeoMap that is displayed by this MapComponent.
     */
    private GeoSet geoSet = null;
    /**
     * The current scale to display the GeoMap. Maps between world coordinates
     * of the GeoObjects and the internal coordinate space of this MapComponent.
     */
    private double scale = 1.;
    /**
     * The amount by which the scale factor changes on a simple zoom-in or
     * zoom-out command without specifying the exact new scale factor.
     */
    private static final double ZOOM_STEP = 1. / 3.;

    private static final double MIN_SCALE = 0.0001;

    /**
     * The MapEventHandler is responsible for treating all key and mouse events
     * for this MapComponent. The whole functionality of MapEventHandler could
     * be integrated into MapComponent. By separating the two, the MapComponent
     * is much easier to understand, program and extend.
     */
    private MapEventHandler mapEventHandler = null;

    /**
     * A BufferedImage that is used as double buffer for drawing. Swing is using
     * its own double buffer mechanism. However, with this private double
     * buffer, MapTools that need quick drawing don't have to wait until the
     * complete map is redrawn, but can just draw this doubleBuffer, and then
     * draw their own stuff.
     */
    private BufferedImage doubleBuffer = null;

    /**
     * Keep track of the top left coordinate of the visible area in world
     * coordinates.
     */
    private final Point2D.Double topLeft = new Point2D.Double(0, 1000);

    /**
     * A formatter that can be used to display coordinates of this map.
     */
    private CoordinateFormatter coordinateFormatter
            = new CoordinateFormatter("###,##0.00", "###,##0", 1);

    /**
     * A formatter for displaying distances measured on this map. Can be null,
     * in which case the coordinateFormatter is used to format distances.
     */
    private CoordinateFormatter distanceFormatter = null;

    private CoordinatesTooltip coordinatesTooltip;

    /**
     * listeners for scale change events
     */
    private final PropertyChangeSupport scaleChangePropertyChangeSupport = new PropertyChangeSupport(this);

    /**
     * Creates a new instance of MapComponent
     */
    public MapComponent() {
        geoSet = new GeoSet();
        geoSet.setName("MapComponent GeoSet");
        geoSet.addGeoSetChangeListener(this);
        geoSet.addGeoSetSelectionChangeListener(this);
        mapEventHandler = new MapEventHandler(this);
    }

    /**
     * Adds a GeoObject to the map.
     *
     * @param geoObject A GeoObject that will be added to the map.
     */
    public void addGeoObject(GeoObject geoObject) {
        if (geoObject == null) {
            return;
        }
        geoSet.addGeoObject(geoObject);
        if (!isObjectVisibleOnMap(geoObject)) {
            showAll();
        } else {
            repaint();
        }
    }

    /**
     * Removes all objects from the map. Also removes GeoObjects that are
     * currently not selected, or are not selectable at all.
     */
    public void removeAllGeoObjects() {
        geoSet.removeAllGeoObjects();
        showAll();
    }

    /**
     * Removes all GeoObjects that are currently selected.
     */
    public void removeSelectedGeoObjects() {
        geoSet.removeSelectedGeoObjects();
    }

    /**
     * Selects all objects contained in the map.
     */
    public void selectAllGeoObjects() {
        geoSet.setSelected(true);
    }

    /**
     * Deselects all GeoObjects contained in the map.
     */
    public void deselectAllGeoObjects() {
        geoSet.setSelected(false);
    }

    /**
     * Centers the map display on the passed point.
     *
     * @param center The new center of the visible area in world coordinates.
     */
    public void centerOnPoint(Point2D.Double center) {
        centerOnPoint(center.getX(), center.getY());
    }

    /**
     * Centers the map display on the passed point.
     *
     * @param cx The new center of the visible area in world coordinates.
     * @param cy The new center of the visible area in world coordinates.
     */
    public void centerOnPoint(double cx, double cy) {
        Rectangle2D.Double visibleRect = getVisibleArea();
        double dx = visibleRect.getCenterX() - topLeft.getX();
        double dy = visibleRect.getCenterY() - topLeft.getY();
        topLeft.setLocation(cx - dx, cy - dy);
        repaint();
    }

    /**
     * Shifts the currently displayed area of the map in horizontal and vertical
     * direction.
     *
     * @param dx Offset in horizontal direction in world coordinates.
     * @param dy Offset in vertical direction in world coordinates.
     */
    public void offsetVisibleArea(double dx, double dy) {
        topLeft.x += dx;
        topLeft.y += dy;
        repaint();
    }

    /**
     * Zooms into the map. The currently visible center of the map is
     * maintained.
     */
    public void zoomIn() {
        zoom(1. + ZOOM_STEP);
    }

    /**
     * Zooms into the map, and centers on the passed point.
     *
     ** @param center The new center of the visible area in world coordinates.
     */
    public void zoomIn(Point2D.Double center) {
        zoomOnPoint(1. + ZOOM_STEP, center);
    }

    /**
     * Zooms out of the map. The currently visible center is retained.
     */
    public void zoomOut() {
        zoom(1. / (1. + ZOOM_STEP));
    }

    /**
     * Zooms out and centers the visible area on the passed point.
     *
     * @param center The new center of the visible area in world coordinates.
     */
    public void zoomOut(Point2D.Double center) {
        zoomOnPoint(1. / (1. + ZOOM_STEP), center);
    }

    /**
     * Zooms out and centers the visible area on the passed point.
     *
     * @param zoomFactor The new zoom factor.
     */
    public void zoom(double zoomFactor) {
        setScaleFactorAndKeepMapCenterPoint(getScaleFactor() * zoomFactor);
        repaint();
    }

    /**
     * Changes the current scale by a specified factor and centers the currently
     * visible area on a passed point.
     *
     * @param zoomFactor The new zoom factor.
     * @param pt The new center of the visible area in world coordinates.
     */
    public void zoomOnPoint(double zoomFactor, Point2D.Double pt) {
        double dx = (pt.x - topLeft.x) / zoomFactor;
        double dy = (pt.y - topLeft.y) / zoomFactor;
        topLeft.x = pt.x - dx;
        topLeft.y = pt.y - dy;
        setScaleAndInformScaleChangeListeners(getScaleFactor() * zoomFactor);
        repaint();
    }

    /**
     * Zooms on passed rectangle. Makes sure the area contained in the rectangle
     * becomes entirely visible.
     *
     * @param rect The area that will be at least visible.
     */
    public void zoomOnRectangle(Rectangle2D.Double rect) {
        if (rect == null) {
            return;
        }

        centerOnPoint(rect.getCenterX(), rect.getCenterY());
        double horScale = getVisibleWidth() / rect.getWidth() * getScaleFactor();
        double verScale = getVisibleHeight() / rect.getHeight() * getScaleFactor();
        double newScale = Math.min(horScale, verScale);
        setScaleFactorAndKeepMapCenterPoint(newScale);
        repaint();
    }

    /**
     * Changes the scale factor and the center of the currently visible area
     * such that all map features become visible.
     */
    public void showAll() {
        // a white border on each side of the map data expressed in percentage
        // of the map size.
        final double BORDER_PERCENTAGE = 2;

        if (geoSet == null) {
            return;
        }
        Rectangle2D geoBounds = geoSet.getVisibleBounds2D();
        if (geoBounds == null) {
            return;
        }
        if (geoBounds.getWidth() == 0 || geoBounds.getHeight() == 0) {
            setScaleAndInformScaleChangeListeners(MIN_SCALE);
            geoBounds.setFrame(geoBounds.getMinX() - 1, geoBounds.getMinY() - 1, 2, 2);
        } else {
            Dimension dim = getSize();
            double horScale = dim.getWidth() / geoBounds.getWidth();
            double verScale = dim.getHeight() / geoBounds.getHeight();
            double borderScale = 1 / (1 + 2 * BORDER_PERCENTAGE / 100);
            setScaleAndInformScaleChangeListeners(Math.min(horScale, verScale) * borderScale);
        }
        centerOnPoint(geoBounds.getCenterX(), geoBounds.getCenterY());
    }

    /**
     * Returns the current scale factor used to display the map.
     *
     * @return The current scale factor.
     */
    public double getScaleFactor() {
        return scale;
    }

    /**
     * Changes the scale factor used to display the map. Makes sure the location
     * displayed at the center of the map does not change.
     *
     * @param scale The new scale factor.
     */
    public void setScaleFactorAndKeepMapCenterPoint(double scale) {
        // adjust topLeft to avoid changing the map center
        Rectangle2D.Double visibleRect = getVisibleArea();
        double cx = visibleRect.getCenterX();
        double cy = visibleRect.getCenterY();
        double dx = cx - topLeft.getX();
        double dy = cy - topLeft.getY();
        dx *= getScaleFactor() / scale;
        dy *= getScaleFactor() / scale;
        topLeft.setLocation(cx - dx, cy - dy);

        setScaleAndInformScaleChangeListeners(scale);
    }

    private void setScaleAndInformScaleChangeListeners(double scale) {
        double oldScale = this.scale;
        this.scale = scale;
        scaleChangePropertyChangeSupport.firePropertyChange("scale", oldScale, scale);
    }

    /**
     * Returns whether a GeoObject is currently displayed in the map.
     *
     * @param geoObject The GeoObject to test.
     * @return true if the passed GeoObject is entirely visible in the map,
     * false otherwise.
     */
    public boolean isObjectVisibleOnMap(GeoObject geoObject) {
        if (geoObject == null) {
            throw new IllegalArgumentException();
        }

        if (geoSet == null) {
            return true;
        }

        Rectangle2D geoBounds = geoObject.getBounds2D();
        Rectangle2D.Double visArea = getVisibleArea();

        // make sure null objects are not passed here!
        if (geoBounds != null && visArea != null) {
            return (visArea.contains(geoBounds));
        }

        // return true if geoBounds is null. This is the case for empty GeoSets
        // Empty GeoSets are considered to be always visible.
        return true;
    }

    /**
     * Returns whether currently all GeoObjects contained in the map are
     * displayed in the map.
     *
     * @return True if all GeoObjects of the map are currently visible.
     */
    public boolean isAllVisible() {
        if (geoSet == null) {
            return true;
        }

        Rectangle2D geoBounds = geoSet.getBounds2D();
        if (geoBounds == null) {
            return true;
        }
        Rectangle2D visArea = getVisibleArea();
        if (visArea == null) {
            return true;
        }
        return visArea.contains(geoBounds);
    }

    /**
     * Returns the extension of the visible area in world coordinates (the
     * coordinate system used by the GeoObjects).
     *
     * @return The currently visible area in world coordinates.
     */
    public Rectangle2D.Double getVisibleArea() {
        Dimension dim = getSize();
        Point pt = new Point(dim.width, dim.height);
        Point2D.Double bottomRight = userToWorldSpace(pt);
        Rectangle2D.Double visibleRect = new Rectangle2D.Double();
        visibleRect.setFrame(topLeft.getX(), bottomRight.getY(),
                bottomRight.getX() - topLeft.getX(),
                topLeft.getY() - bottomRight.getY());
        return visibleRect;
    }

    /**
     * Returns the width of the currently visible area in world coordinates (the
     * coordinate system used by the GeoObjects).
     *
     * @return The width of the currently visible area in world coordinates.
     */
    public double getVisibleWidth() {
        Dimension dim = getSize();
        return dim.getWidth() / getScaleFactor();
    }

    /**
     * Returns the height of the currently visible area in world coordinates
     * (the coordinate system used by the GeoObjects).
     *
     * @return The height of the currently visible area in world coordinates.
     */
    public double getVisibleHeight() {
        Dimension dim = getSize();
        return dim.getHeight() / getScaleFactor();
    }

    /**
     * Returns the preferred size of this JComponent.
     *
     * @return The preferred size.
     */
    @Override
    public Dimension getPreferredSize() {
        Dimension dimension = new Dimension(100, 100);
        if (geoSet == null) {
            return dimension;
        }
        Rectangle2D geoBounds = geoSet.getBounds2D();
        if (geoBounds == null) {
            return dimension;
        }
        dimension.width = (int) Math.ceil(geoBounds.getWidth() * getScaleFactor());
        dimension.height = (int) Math.ceil(geoBounds.getHeight() * getScaleFactor());
        return dimension;
    }

    /**
     * Returns the minimum size of this JComponent.
     *
     * @return The minimum size.
     */
    @Override
    public Dimension getMinimumSize() {
        return new Dimension(100, 100);
    }

    /**
     * Returns the GeoMap that is currently used.
     *
     * @return The GeoMap that is used. Can be null!
     */
    public GeoSet getGeoSet() {
        return geoSet;
    }

    /**
     * Converts from the coordinate system of this MapComponent to the world
     * coordinate system used by the GeoObjects.
     *
     * @param pt The point to convert. Will not be changed.
     * @return The convert point in world coordinates.
     */
    public final Point2D.Double userToWorldSpace(Point pt) {
        double x = pt.getX() / getScaleFactor() + topLeft.getX();
        double y = -pt.getY() / getScaleFactor() + topLeft.getY();
        return new Point2D.Double(x, y);
    }

    /**
     * Converts from the world coordinate system used by the GeoObjects to the
     * coordinate system of this MapComponent.
     *
     * @param pt The point to convert. The converted coordinates will also be
     * stored in pt.
     */
    protected final void worldToUserSpace(Point2D.Double pt) {
        double x = (pt.getX() - topLeft.getX()) * getScaleFactor();
        double y = (topLeft.getY() - pt.getY()) * getScaleFactor();
        pt.setLocation(x, y);
    }

    /**
     * Formats a point in world coordinates for display. Works with Cartesian
     * and spherical coordinates.
     *
     * @param point point to format
     * @param spherical if true, format as spherical longitude/latitude,
     * otherwise as Cartesian X/Y.
     * @return an array with two strings
     */
    public String[] coordinatesStrings(java.awt.geom.Point2D.Double point, boolean spherical) {
        final String str1, str2;
        if (spherical) {
            Projection proj = ProjectionsManager.createWebMercatorProjection();
            Point2D.Double lonLat = new Point2D.Double();
            proj.inverseTransform(point, lonLat);
            String lonStr = NumberFormatter.formatDegreesMinutesSeconds(lonLat.x, true);
            String latStr = NumberFormatter.formatDegreesMinutesSeconds(lonLat.y, false);
            str1 = "\u03BB : " + lonStr;
            str2 = "\u03D5 : " + latStr;

        } else {
            CoordinateFormatter formatter = getCoordinateFormatter();
            str1 = "X : " + formatter.format(point.getX());
            str2 = "Y : " + formatter.format(point.getY());
        }
        return new String[]{str1, str2};
    }

    /**
     * Paints the GeoMap.
     *
     * @param g2d The drawing destination.
     * @param drawSelectionState If true, selected GeoObjects will be drawn with
     * some kind of highlight effect.
     */
    private void paintMap(Graphics2D g2d, boolean drawSelectionState) {
        // enable antialiasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        // enable high quality rendering
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        // enable bicubic interpolation of images
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);

        /*
         * Transformation:
         * x_ = (x-west)*scale;
         * y_ = (north-y)*scale = (y-north)*(-scale);
         */
        g2d.scale(getScaleFactor(), -getScaleFactor());
        g2d.translate(-topLeft.getX(), -topLeft.getY());

        // set default appearance of vector elements
        g2d.setStroke(new BasicStroke(1));
        g2d.setColor(Color.black);

        // draw the map tree
        geoSet.draw(g2d, getScaleFactor(), drawSelectionState);
    }

    /**
     * Returns the internal Double Buffer used for drawing the map. This is
     * useful for MapTools that temporarily draw over the map.
     *
     * @return A reference to the double buffer with the content remaining from
     * the last repaint.
     */
    public BufferedImage getDoubleBuffer() {
        return doubleBuffer;
    }

    /**
     * Helper method that calls the current MapTool to give it a chance to draw
     * some background drawing. Returns true if the map does not need to be
     * drawn over the background.
     *
     * @param g2d The destination to draw to.
     * @return Returns true if the map does not need to be drawn over the
     * background.
     */
    private boolean paintToolBackground(Graphics2D g2d) {
        MapTool mapTool = getMapTool();
        if (mapTool instanceof DoubleBufferedTool
                && ((DoubleBufferedTool) mapTool).isDrawingBackground()) {
            ((DoubleBufferedTool) mapTool).drawBackground(g2d);
            return true;
        }
        return false;
    }

    /**
     * Helper method that calls the current MapTool to give it a chance to draw
     * some foreground drawing.
     *
     * @param g2d The destination to draw to.
     */
    private void paintToolForeground(Graphics2D g2d) {
        MapTool mapTool = getMapTool();
        if (mapTool != null) {
            double s = getScaleFactor();
            g2d.translate(-topLeft.getX() * s, topLeft.getY() * s);
            g2d.scale(s, -s);
            mapTool.draw(g2d);
        }
    }

    /**
     * Overwrite paintComponent of JComponent to do our custom drawing.
     *
     * @param g The destination to draw to.
     */
    @Override
    protected void paintComponent(Graphics g) {
        /* From a sun java tutorial:
         *Make sure that when the paintComponent method exits, the Graphics
         object that was passed into it has the same state that it had at the
         start of the method. For example, you should not alter the clip
         Rectangle or modify the transform.
         If you need to do these operations you may find it easier to
         create a new Graphics from the passed in Graphics and manipulate
         it. Further, if you do not invoker super's implementation you
         must honor the opaque property, that is if this component is
         opaque, you must completely fill in the background in a
         non-opaque color. If you do not honor the opaque property you
         will likely see visual artifacts.
         */
        Graphics2D g2d = (Graphics2D) g.create(); //copy g. Recomended by Sun tutorial.

        Insets insets = getInsets();
        int currentWidth = getWidth() - insets.left - insets.right;
        int currentHeight = getHeight() - insets.top - insets.bottom;

        // make sure the doubleBuffer image is allocated
        if (doubleBuffer == null
                || doubleBuffer.getWidth() != currentWidth
                || doubleBuffer.getHeight() != currentHeight) {
            doubleBuffer = (BufferedImage) createImage(currentWidth, currentHeight);
        }

        boolean toolPaintedMap = paintToolBackground(g2d);
        if (toolPaintedMap == false) {
            // draw the map into the doubleBuffer image
            Graphics2D doubleBufferGraphics2D;
            doubleBufferGraphics2D = (Graphics2D) doubleBuffer.getGraphics();
            doubleBufferGraphics2D.setBackground(Color.white);
            doubleBufferGraphics2D.clearRect(insets.left, insets.top,
                    currentWidth, currentHeight);
            paintMap(doubleBufferGraphics2D, true);

            // draw the doubleBuffer image
            g2d.drawImage(doubleBuffer, insets.left, insets.top, this);
        }
        paintToolForeground(g2d);
        g2d.dispose(); //release the copy's resources. Recomended by Sun tutorial.

        if (coordinatesTooltip != null) {
            coordinatesTooltip.paintTooltip((Graphics2D) g);
        }
    }

    /**
     * Inform Swing that this JComponent is opaque, i.e. we are drawing the
     * whole area of this Component. This accelerates the drawing of this
     * component.
     *
     * @return true if opaque.
     */
    @Override
    public boolean isOpaque() {
        return true;
    }

    @Override
    public boolean isOptimizedDrawingEnabled() {
        return true;
    }

    /**
     * A callback method for the GeoSetChangeListener interface.
     *
     * @param geoSet The Geoset that was changed.
     */
    @Override
    public void geoSetChanged(GeoSet geoSet) {
        repaint();
    }

    /**
     * A callback method of the GeoSetSelectionChangeListener interface.
     *
     * @param geoSet The Geoset that was changed.
     */
    @Override
    public void geoSetSelectionChanged(GeoSet geoSet) {
        repaint();
    }

    /**
     * Returns the current MapTool
     *
     * @return The currently active MapTool.
     */
    public MapTool getMapTool() {
        return mapEventHandler.getMapTool();
    }

    /**
     * Sets the current MapTool.
     *
     * @param mapTool The new MapTool
     */
    public void setMapTool(MapTool mapTool) {
        mapEventHandler.setMapTool(mapTool, false);
    }

    public void removeMouseMotionListener(MapToolMouseMotionListener listener) {
        mapEventHandler.removeMouseMotionListener(listener);
    }

    public void addMouseMotionListener(MapToolMouseMotionListener listener) {
        mapEventHandler.addMouseMotionListener(listener);
    }

    public CoordinateFormatter getCoordinateFormatter() {
        return coordinateFormatter;
    }

    public void setCoordinateFormatter(CoordinateFormatter coordinateFormatter) {
        this.coordinateFormatter = coordinateFormatter;
    }

    public CoordinateFormatter getDistanceFormatter() {
        return distanceFormatter == null ? coordinateFormatter : distanceFormatter;
    }

    public void setDistanceFormatter(CoordinateFormatter distanceFormatter) {
        this.distanceFormatter = distanceFormatter;
    }
    
    public CoordinatesTooltip getCoordinatesToolTip() {
        return coordinatesTooltip;
    }

    /**
     * @param coordinatesTooltip the coordinatesTooltip to set
     */
    public void setCoordinatesTooltip(CoordinatesTooltip coordinatesTooltip) {
        this.coordinatesTooltip = coordinatesTooltip;
    }

    /**
     * Add a listener for scale change events. A listener is only added once.
     *
     * @param listener listener to add
     */
    public void addScaleChangePropertyChangeListener(PropertyChangeListener listener) {
        // try removing the listener to make sure it is only registered once
        scaleChangePropertyChangeSupport.removePropertyChangeListener(listener);
        scaleChangePropertyChangeSupport.addPropertyChangeListener("scale", listener);
    }

    /**
     * Remove a listener for scale change events.
     *
     * @param listener listener to remove
     */
    public void removeScaleChangePropertyChangeListener(PropertyChangeListener listener) {
        scaleChangePropertyChangeSupport.removePropertyChangeListener(listener);
    }
}
