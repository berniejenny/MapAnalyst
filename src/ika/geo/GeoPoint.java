package ika.geo;

import java.awt.geom.*;
import java.awt.*;

/**
 * GeoPoint - A point that is drawn with a graphic symbol.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class GeoPoint extends GeoObject implements java.io.Serializable {
    
    private static final long serialVersionUID = -6822508477473599463L;
    /**
     * The x coordinate of the point.
     */
    private double x = 0;
    
    /**
     * The y coordinate of the point.
     */
    private double y = 0;
    
    /**
     * The PointSymbol handles the graphic attributes of this GeoPoint.
     */
    private PointSymbol pointSymbol = new PointSymbol();
    
    /**
     * Create a new instance.
     * @param x The x coordinate of the point.
     * @param y The y coordinate of the point.
     * @param r The radius of the circle used to represent the point.
     */
    public GeoPoint(double x, double y, double r) {
        this.x = x;
        this.y = y;
        pointSymbol.setRadius(r);
    }
    
    /**
     * Create a new instance.
     * @param point The location of the point.
     * @param r The radius of the circle used to represent the point.
     * @param scaleInvariant Whether the point symbol should grow with the map
     *  scale or not.
     */
    public GeoPoint(Point2D point, double r, boolean scaleInvariant) {
        this.x = point.getX();
        this.y = point.getY();
        pointSymbol.setRadius(r);
        pointSymbol.setScaleInvariant(scaleInvariant);
    }
    
    /**
     * Create a new scale invariant instance.
     */
    public GeoPoint(double x, double y) {
        this.x = x;
        this.y = y;
        pointSymbol.setScaleInvariant(true);
    }
    
    /**
     * Create a new instance.
     * @param point The location of the point.
     * @param scaleInvariant Whether the point symbol should grow with the map
     *  scale or not.
     */
    public GeoPoint(Point2D point, boolean scaleInvariant) {
        this.x = point.getX();
        this.y = point.getY();
        pointSymbol.setScaleInvariant(scaleInvariant);
    }
    
    /**
     * Set the x coordinate of this point.
     * @param x The horizontal coordinate.
     */
    public void setX(double x) {
        this.x = x;
    }
    
    /**
     * Returns the x coordinate of this point.
     * @return The horizontal coordinate.
     */
    public double getX() {
        return this.x;
    }
    
    /**
     * Set the y coordinate of this point.
     * @param y The vertical coordinate.
     */
    public void setY(double y) {
        this.y = y;
    }
    
    /**
     * Returns the y coordinate of this point.
     * @return The vertical coordinate.
     */
    public double getY() {
        return this.y;
    }
    
    /**
     * Returns an default VectorSymbol.
     * @return VectorSymbol.
     */
    public VectorSymbol getVectorSymbol() {
        return new VectorSymbol();
    }
        
    public PathIterator getPathIterator(AffineTransform affineTransform) {
        final Shape pointSymbol = this.pointSymbol.getPointSymbol(1., x, y);
        return pointSymbol.getPathIterator(affineTransform);
    }
    
    public void draw(Graphics2D g2d, double scale, boolean drawSelectionState) {
        if (!visible)
            return;
        final boolean drawSelected = drawSelectionState && isSelected();
        this.pointSymbol.drawPointSymbol(g2d, scale, drawSelected, x, y);
    }
    
    public boolean isPointOnSymbol(java.awt.geom.Point2D point, double tolDist,
            double scale) {
        return this.pointSymbol.isPointOnSymbol(point, tolDist, scale, x, y);
    }
    
    public boolean isIntersectedByRectangle(Rectangle2D rect, double scale) {
        return this.pointSymbol.getPointSymbol(scale, x, y).intersects(rect);
    }
    
    public java.awt.geom.Rectangle2D getBounds2D() {
        return new java.awt.geom.Rectangle2D.Double (x, y, 0, 0);
    }
    
    public void move(double dx, double dy) {
        this.x += dx;
        this.y += dy;
    }

    public PointSymbol getPointSymbol() {
        return pointSymbol;
    }

    public void setPointSymbol(PointSymbol pointSymbol) {
        this.pointSymbol = pointSymbol;
    }
    
    public final boolean isPointClose (GeoPoint geoPoint, double tolerance) {
        return (Math.abs(this.x - geoPoint.x) < tolerance
                && Math.abs(this.y - geoPoint.y) < tolerance);
    }
    
    public final boolean isPointClose (GeoPoint geoPoint) {
        return this.isPointClose(geoPoint, 1e-10);
    }
    
    public String toString(){
       return super.toString() + "\nCoordinates\t: " + this.x + " / " + this.y + "\n";
    }
}
