/*
 * FontSymbol.java
 *
 * Created on August 10, 2005, 11:48 AM
 *
 */

package ika.geo;

import java.awt.*;
import java.awt.font.*;
import java.awt.image.*;
import java.awt.geom.*;

/**
 *
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich
 */
public class FontSymbol extends Symbol{
    
    private Font font = new Font("SansSerif", Font.PLAIN, 12);
    
    private boolean scaleInvariant = true;
    
    private double fontScale = 1d;
    
    private boolean centerHor = true;
    
    private boolean centerVer = true;
    
    /** Creates a new instance of FontSymbol */
    public FontSymbol() {
    }
    
    public void drawFontSymbol(Graphics2D g2d,
            double scale,
            boolean drawSelected,
            double x, double y,
            double dx, double dy,
            String text) {
        
        g2d = (Graphics2D)g2d.create(); //copy g2d
        
        g2d.setColor(drawSelected ?
            ika.utils.ColorUtils.getSelectionColor() :
            java.awt.Color.black);
        
        double tx = x;
        double ty = y;
        
        if (this.centerHor || this.centerVer) {
            Rectangle2D bounds = this.getBounds2D(text, x, y, dx, dy, scale);
            if (this.centerHor)
                tx -= bounds.getWidth() / 2;
            if (this.centerVer)
                ty -= bounds.getHeight() / 2;
        }
        
        tx += dx / scale;
        ty += dy / scale;
        
        g2d.translate(tx, ty);
        
        final double s =  this.scaleInvariant ? this.fontScale/scale : this.fontScale;
        g2d.scale(s, -s);
        g2d.setFont(this.font);
        g2d.drawString(text, 0, 0);
    }
    
    public Rectangle2D getBounds2D(String str, double x, double y, 
            double dx, double dy, double scale) {
        
        if (this.isScaleInvariant()) {
            if (scale <= 0.d)
                return new java.awt.geom.Rectangle2D.Double(x, y, 0, 0);
            else {
                dx /= scale;
                dy /= scale;
            }
        } else {
            scale = 1;
        }
        
        // see http://forum.java.sun.com/thread.jspa?forumID=5&threadID=619854
        // for an example of how to measure text size
        final FontRenderContext frc = new FontRenderContext(null, true, false);
        final LineMetrics lineMetrics = this.font.getLineMetrics(str, frc);
        final GlyphVector gv = font.createGlyphVector(frc, str);
        final Rectangle2D visualBounds  = gv.getVisualBounds();
        
        final double voffset = visualBounds.getHeight() + visualBounds.getY();
        final Rectangle2D bounds = new Rectangle2D.Double(x + dx,
                y - voffset + dy,
                visualBounds.getWidth() * this.fontScale / scale,
                visualBounds.getHeight() * this.fontScale / scale);
        return bounds;
    }
    
    public Font getFont() {
        return font;
    }
    
    public void setFont(Font font) {
        this.font = font;
    }
    
    public boolean isScaleInvariant() {
        return scaleInvariant;
    }
    
    public void setScaleInvariant(boolean scaleInvariant) {
        this.scaleInvariant = scaleInvariant;
    }
    
    public double getFontScale() {
        return fontScale;
    }
    
    public void setFontScale(double fontScale) {
        this.fontScale = fontScale;
    }
    
    public boolean isCenterHor() {
        return centerHor;
    }
    
    public void setCenterHor(boolean centerHor) {
        this.centerHor = centerHor;
    }
    
    public boolean isCenterVer() {
        return centerVer;
    }
    
    public void setCenterVer(boolean centerVer) {
        this.centerVer = centerVer;
    }
    
    public int getSize() {
        return this.font.getSize();
    }

    public void setSize (int size) {
       this.font = this.font.deriveFont((float)size);
    }
}
