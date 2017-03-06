/*
 * FontSymbol.java
 *
 * Created on August 10, 2005, 11:48 AM
 *
 */
package ika.geo;

import ika.utils.ColorUtils;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.*;

/**
 *
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich
 */
public class FontSymbol extends Symbol {

    private Font font = new Font("SansSerif", Font.PLAIN, 12);

    private boolean scaleInvariant = true;

    private double fontScale = 1d;

    private boolean centerHor = true;

    private boolean centerVer = true;

    private Color color = Color.BLACK;

    /**
     * Creates a new instance of FontSymbol
     */
    public FontSymbol() {
    }

    public void drawFontSymbol(Graphics2D g2d,
            double scale,
            boolean drawSelected,
            double x, double y,
            double dx, double dy,
            String text) {

        g2d = (Graphics2D) g2d.create(); //copy g2d
        g2d.setColor(drawSelected ? ColorUtils.getSelectionColor() : color);

        double tx = x;
        double ty = y;

        if (this.centerHor || this.centerVer) {
            Rectangle2D bounds = getBounds2D(text, x, y, dx, dy, scale);
            if (this.centerHor) {
                tx -= bounds.getWidth() / 2;
            }
            if (this.centerVer) {
                ty -= bounds.getHeight() / 2;
            }
        }

        tx += dx / scale;
        ty += dy / scale;

        g2d.translate(tx, ty);

        double s = this.scaleInvariant ? fontScale / scale : fontScale;
        g2d.scale(s, -s);
        g2d.setFont(font);
        g2d.drawString(text, 0, 0);
        
        g2d.dispose(); //release the copy's resources. Recomended by Sun tutorial.
    }

    public Rectangle2D getBounds2D(String str, double x, double y,
            double dx, double dy, double scale) {

        if (isScaleInvariant()) {
            if (scale <= 0.d) {
                return new java.awt.geom.Rectangle2D.Double(x, y, 0, 0);
            } else {
                dx /= scale;
                dy /= scale;
            }
        } else {
            scale = 1;
        }

        // see http://forum.java.sun.com/thread.jspa?forumID=5&threadID=619854
        // for an example of how to measure text size
        FontRenderContext frc = new FontRenderContext(null, true, false);
        GlyphVector gv = font.createGlyphVector(frc, str);
        Rectangle2D visualBounds = gv.getVisualBounds();

        double voffset = visualBounds.getHeight() + visualBounds.getY();
        Rectangle2D bounds = new Rectangle2D.Double(x + dx,
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

    public void setSize(int size) {
        this.font = this.font.deriveFont((float) size);
    }

    /**
     * @return the color
     */
    public Color getColor() {
        return color;
    }

    /**
     * @param color the color to set
     */
    public void setColor(Color color) {
        this.color = color;
    }
}
