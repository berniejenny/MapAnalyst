/*
 * PointSymbol.java
 *
 * Created on May 13, 2005, 12:08 PM
 */

package ika.geo;

import java.awt.geom.*;
import java.awt.*;

/**
 *
 * @author jenny
 */
public class PointSymbol extends VectorSymbol implements java.io.Serializable {
    
    private static final long serialVersionUID = -755322865498970894L;
    
    /**
     * The radius used to draw a circle around the point. We mix here geometry
     * and graphic attributes, which is not how it should be done.
     */
    private double radius = 3;
    
    /**
     * The length of the radial lines used to draw this PointSymbol.
     */
    private double lineLength = 6;
    
    public PointSymbol() {
        this.filled = false;
        this.stroked = true;
        this.strokeColor = Color.BLACK;
        this.strokeWidth = 2;
    }
    
    public double getRadius() {
        return radius;
    }
    
    public void setRadius(double radius) {
        this.radius = radius;
    }
    
    public double getLineLength() {
        return lineLength;
    }
    
    public void setLineLength(double lineLength) {
        this.lineLength = lineLength;
    }
    
    /**
     * Returns a Shape that can be used to draw this point.
     * @param mapScale The current scale of the map.
     * @return The Shape that can be used to draw this point.
     */
    public Shape getPointSymbol(double scale, double x, double y) {
        
        final float lineLength = getScaledLineLength(scale);
        final float r = getScaledRadius(scale);
        final float fx = (float)x;
        final float fy = (float)y;

        final Ellipse2D circle = new Ellipse2D.Double(-r+x, -r+y, 2*r, 2*r);
        
        GeneralPath path = new GeneralPath();
        path.append(circle, false);
        if (lineLength > 0) {
            path.moveTo(r+fx, fy);
            path.lineTo(r + lineLength + fx, fy);
            path.moveTo(-r + fx, fy);
            path.lineTo(-r - lineLength + fx, fy);
            path.moveTo(fx, r + fy);
            path.lineTo(fx, r + lineLength + fy);
            path.moveTo(fx, -r + fy);
            path.lineTo(fx, -r - lineLength + fy);
        }
        return path;
    }
    
    public boolean isPointOnSymbol(java.awt.geom.Point2D point, double tolDist,
            double scale, double x, double y) {
        
        final double px = point.getX();
        final double py = point.getY();
        final double r = this.getScaledRadius(scale);
        final double strokeWidth = this.getScaledStrokeWidth(scale);
        final double halfStrokeWidth = strokeWidth / 2;
        final double lineLength = this.getScaledLineLength(scale);
        
        // test if point is in bounding box (including the radial lines).
        if (px < x - r - lineLength
                || px > x + r + lineLength
                || py < y - r - lineLength
                || py > y + r + lineLength)
            return false;
        
        // test if point is inside central circle
        final double dx = point.getX() - x;
        final double dy = point.getY() - y;
        final double dsquare = dx*dx+dy*dy;
        if (dsquare <=  (r+halfStrokeWidth)*(r+halfStrokeWidth))
            return true;
        
        // test if point is on one of the straight lines
        // right
        if (px >= x + r
                && px <= x + r + lineLength
                && py >= y - halfStrokeWidth
                && py <= y + halfStrokeWidth)
            return true;
        // left
        if (px >= x - r - lineLength
                && px <= x - r
                && py >= y - halfStrokeWidth
                && py <= y + halfStrokeWidth)
            return true;
        // bottom
        if (px >= x - halfStrokeWidth
                && px <= x + halfStrokeWidth
                && py >= y - r - lineLength
                && py <= y - r)
            return true;
        // top
        if (px >= x - halfStrokeWidth
                && px <= x + halfStrokeWidth
                && py >= y + r
                && py <= y + r + lineLength)
            return true;
        
        return false;
    }
    
    private float getScaledLineLength(double scale) {
        return scaleInvariant ? (float)(getLineLength() / scale) : (float)getLineLength();
    }
    
    private float getScaledRadius(double scale) {
        final double r = this.getRadius();
        return scaleInvariant ? (float)(r / scale) : (float)r;
    }
    
    public final void drawPointSymbol(Graphics2D g2d,
            double scale,
            boolean drawSelected,
            double x, double y) {
        
        final float ssw = this.getScaledStrokeWidth(scale);
        BasicStroke stroke = new BasicStroke(ssw);
        g2d.setStroke(stroke);
        
        final Shape pointSymbol = this.getPointSymbol(scale, x, y);
        if (this.isFilled()) {
            g2d.setColor(this.getFillColor());
            g2d.fill(pointSymbol);
        }
        if (drawSelected)
            g2d.setColor(ika.utils.ColorUtils.getSelectionColor());
        else
            g2d.setColor(this.getStrokeColor());
        g2d.draw(pointSymbol);
    }
}
