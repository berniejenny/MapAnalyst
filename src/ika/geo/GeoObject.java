package ika.geo;

import java.awt.geom.*;
import java.awt.*;

/**
 * GeoObject - an abstract base class for GeoObjects. A GeoObject has a spatial
 * extension and can be selected and drawn in a map.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public abstract class GeoObject implements java.io.Serializable {
    
    private static final long serialVersionUID = 3361920857810179938L;
    
    /**
     * Flag that indicates whether this GeoObject has been selected by some user action.
     */
    private boolean selected;
    
    /**
     * selectable determines whether this GeoObject can be selected.
     */
    private boolean selectable;
    
    /**
     * selectable determines whether this GeoObject should be drawn in a map.
     */
    protected boolean visible;
    
    /**
     * The name of this GeoObject
     */
    private String name;
    
    /**
     * Protected constructor of GeoObject.
     * @param selectable Determines whether this GeoObject can be selected or not.
     */
    protected GeoObject(boolean selectable) {
        this.selected = false;
        this.selectable = selectable;
        this.visible = true;
        this.name = null;
    }
    
    /**
     * Protected consructor. Sets the selectable flag to true.
     */
    protected GeoObject() {
        this.selected = false;
        this.selectable = true;
        this.visible = true;
        this.name = null;
    }
    
    /**
     * Returns whether this GeoObject can be selected. An attempt to select an object
     * that cannot be selected using setSelected (true) will have no effect.
     * @return True if the object can be selected.
     */
    public final boolean isSelectable() {
        return this.selectable;
    }
    
    /**
     * Set the selectable state of this GeoObject.
     * @param selected The new selection state.
     */
    public void setSelectable(boolean selectable) {
        this.selectable = selectable;
    }
    
    /**
     * Return whether this GeoObject is selected.
     * @return The selection state.
     */
    public final boolean isSelected() {
        return this.selected;
    }
    
    /**
     * Set the selection state of this GeoObject. An command to select an object
     * that cannot be selected using setSelected will be ignored.
     * @param selected The new selection state.
     */
    public void setSelected(boolean selected) {
        this.selected = (selected && this.selectable);
    }
        
    /**
     * Returns a PathIterator that describes the geometry of this GeoObject.
     */
    abstract public PathIterator getPathIterator(AffineTransform affineTransform);
    
    /**
     * Returns a bounding box in world coordinates.
     */
    abstract public Rectangle2D getBounds2D();

    /**
     * Returns a bounding box in world coordinates if this GeoObject is visible.
     */
    public Rectangle2D getVisibleBounds2D() {
        return this.isVisible() ? this.getBounds2D() : null;
    }
    
    /**
     * Draw this GeoObject into a Graphics2D object.
     * @param g2d The destination to draw to.
     * @param scale The scale factor transforming from this GeoObject's coordinate system to
     * the coordinate system of the Graphics2D. Usually, the geometry of this GeoObject
     * does not need to be scaled. scale can be used for scale-invariant drawing, e.g.
     * a line width can be scaled so that its width is the same at any scale.
     * @param drawSelectionState Flag that indicates whether the selection state of this GeoObject should visualized,
     * e.g. a selected path can be drawn with a special highlight color.
     */
    abstract public void draw(Graphics2D g2d, double scale,
            boolean drawSelectionState);
    
    /**
     * Tests whether a point hits the graphical representation of this GeoObject.
     * @param point The point to test.
     * @param tolDist The tolerance distance for hit detection.
     * @param scale The current scale of the map.
     * @return Returns true if the passed point hits this GeoObject.
     */
    abstract public boolean isPointOnSymbol(Point2D point, double tolDist,
            double scale);
    
    
    abstract public boolean isIntersectedByRectangle(Rectangle2D rect, double scale);
      
    /**
     * Select this GeoObject if it intersects with a rectangle.
     * @param rect The rectangle to test.
     * @param scale The current scale of the map.
     * @param extendSelection If true, this GeoObject is not deselected if the
     * rectangle does not interesect
     * with it. Otherwise, the GeoObject is deselected.
     */
    public boolean selectByRectangle(Rectangle2D rect, double scale,
            boolean extendSelection) {
        // don't do anything if this object cannot be selected.
        if (!this.selectable)
            return false;
        
        // remember the inital selection state
        final boolean initialSelection = this.isSelected();
        
        // default is that the rectangle does not intersect with the object.
        boolean newSelection = extendSelection && initialSelection;
        
        // test if this object is hit by the passed point.
        if (this.isIntersectedByRectangle(rect, scale) == true) {
            // The object is hit and already selected.
            // Toggle selection state if needed.
            if (this.isSelected())
                newSelection = !extendSelection;
            else // object is hit but not selected yet.
                newSelection = true;
        }
        
        this.setSelected(newSelection);
        return initialSelection != newSelection;
    }
    /**
     * Select this GeoObject if it is hit by a passed point.
     * @param point The point to test.
     * @param scale The current scale of the map.
     * @param extendSelection If true, this GeoObject is not deselected if the
     * point does not hit this object. Otherwise, it is deselected.
     * @return Returns true if the selection state has been changed
     */
    public boolean selectByPoint(Point2D point, double scale,
            boolean extendSelection, double tolDist) {
        
        // don't do anything if this object cannot be selected.
        if (!this.selectable)
            return false;
        
        // remember the inital selection state
        final boolean initialSelection = this.isSelected();
        
        // default is that the point does not hit the object.
        boolean newSelection = extendSelection && initialSelection;
        
        // test if this object is hit by the passed point.
        if (this.isPointOnSymbol(point, tolDist, scale) == true) {
            // The object is hit and already selected.
            // Toggle selection state if needed.
            if (this.isSelected())
                newSelection = !extendSelection;
            else // object is hit but not selected yet.
                newSelection = true;
        }
        
        this.setSelected(newSelection);
        return initialSelection != newSelection;
    }
    
    /**
     * Move this object by a specified amount horizontally and vertically.
     * @param dx The distance by which this object should be moved in horizontal
     * direction.
     * @param dy The distance by which this object should be moved in vertical
     * direction.
     */
    abstract public void move(double dx, double dy);

    public void scale(double scale) {
        // empty
    }
    
    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    
    public String toString() {
        StringBuffer str = new StringBuffer();
        str.append (this.getClass());
        str.append ("\n");
        str.append ("Name\t: " + this.name);
        
        str.append ("\n");
        str.append ("Selectable\t: " + this.selectable);
        
        str.append ("\n");
        str.append ("Selected\t: " + this.selected);
        
        str.append ("\n");
        str.append ("Visible\t: " + this.visible);
        
        return str.toString();
    }
}

