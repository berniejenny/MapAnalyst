/*
 * GeoText.java
 *
 * Created on August 10, 2005, 11:51 AM
 *
 */

package ika.geo;

import java.awt.image.*;
import java.awt.*;
import java.awt.font.*;

/**
 *
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich
 */
public class GeoText extends GeoObject {
    
    private double x = 0;
    private double y = 0;
    private double dx = 0;
    private double dy = 0;
    private FontSymbol fontSymbol = new FontSymbol();
    private String text = "";
    
    /** Creates a new instance of GeoText */
    public GeoText() {
    }
    
    public GeoText (String text, double x, double y) {
        this.text = text;
        this.x = x;
        this.y = y;
    }
    
    public GeoText (String text, double x, double y, double dx, double dy) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.dx = dx;
        this.dy = dy;
    }
    
    public void draw(java.awt.Graphics2D g2d, double scale, boolean drawSelectionState) {
        if (!visible)
            return;
        final boolean drawSelected = drawSelectionState && isSelected();
        this.fontSymbol.drawFontSymbol(g2d, scale, drawSelected, x, y, dx, dy, this.text);
    }

    public java.awt.geom.Rectangle2D getBounds2D() {
        return this.fontSymbol.getBounds2D (this.text, x, y, dx, dy, -1);
     }

    public java.awt.geom.PathIterator getPathIterator(java.awt.geom.AffineTransform affineTransform) {
        return null;
    }

    public boolean isIntersectedByRectangle(java.awt.geom.Rectangle2D rect, double scale) {
        // Test if if the passed rectangle and the bounding box of this object
        // intersect.
        // Use GeometryUtils.rectanglesIntersect and not Rectangle2D.intersects!
        final java.awt.geom.Rectangle2D bounds = this.getBounds2D();
        return ika.utils.GeometryUtils.rectanglesIntersect(rect, bounds);
    }

    public boolean isPointOnSymbol(java.awt.geom.Point2D point, double tolDist, double scale) {
        java.awt.geom.Rectangle2D bounds = this.getBounds2D();
        ika.utils.GeometryUtils.enlargeRectangle(bounds, tolDist);
        return bounds.contains(point);
    }

    public void move(double dx, double dy) {
        this.x += dx;
        this.y += dy;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }
    
    public double getDx() {
        return dx;
    }

    public void setDx(double dx) {
        this.dx = dx;
    }

    public double getDy() {
        return dy;
    }

    public void setDy(double dy) {
        this.dy = dy;
    }
        
    public FontSymbol getFontSymbol() {
        return fontSymbol;
    }

    public void setFontSymbol(FontSymbol fontSymbol) {
        this.fontSymbol = fontSymbol;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
    
    public boolean isScaleInvariant() {
        return this.fontSymbol.isScaleInvariant();
    }
    
    public void setScaleInvariant(boolean scaleInvariant) {
        this.fontSymbol.setScaleInvariant(scaleInvariant);
    }
    
    public boolean isCenterHor() {
        return this.fontSymbol.isCenterHor();
    }

    public void setCenterHor(boolean centerHor) {
        this.fontSymbol.setCenterHor(centerHor);
    }

    public boolean isCenterVer() {
        return this.fontSymbol.isCenterVer();
    }

    public void setCenterVer(boolean centerVer) {
        this.fontSymbol.setCenterVer(centerVer);
    }
    
    public int getSize() {
        return this.fontSymbol.getSize();
    }

    public void setSize (int size) {
        this.fontSymbol.setSize (size);
    }
}
