/*
 * DoubleBufferedTool.java
 *
 * Created on April 11, 2005, 3:19 PM
 */

package ika.map.tools;

import java.awt.image.*;
import java.awt.*;
import ika.utils.ImageUtils;
import ika.gui.MapComponent;

/**
 * DoubleBuffereTool - an abstract tool class that provides quick drawing of the map 
 * background based on a double buffered image.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public abstract class DoubleBufferedTool extends MapTool {
    
    /**
     * Static flag to toggle debugging information on and off.
     */
    private static final boolean VERBOSE = false;
    
    /**
     * The image that contains the map background.
     */
    private BufferedImage backImg = null;
    
    /**
     * A VolatileImage offers hardware supported drawing. Should be faster.
     */
    private VolatileImage volImage = null;
    
    /** Creates a new instance of DoubleBufferedTool 
      * @param mapComponent The MapComponent for which this MapTool provides its services.
     */
    protected DoubleBufferedTool(MapComponent mapComponent) {
        super(mapComponent);
    }
    
    /**
     * Captures the current map background. It will be used be subsequent calls to 
     * drawBackground to draw the map, until releaseBackground is called.
     */
    protected void captureBackground() {       
        backImg = mapComponent.getDoubleBuffer();
        if (VERBOSE)
            System.out.println("captured background");
    }
    
    /**
     * Relases the previously captured background.
     */
    protected void releaseBackground() {
        if (backImg != null)
            backImg.flush();
        backImg = null;
        if (volImage != null)
            volImage.flush();
        volImage = null;
        if (VERBOSE)
            System.out.println("released background");
    }
    
    /**
     * Returns whether this MapTool is currently drawing a double buffered background image.
     * @return True if double buffered background image is used for drawing.
     */
    public boolean isDrawingBackground() {
        return (this.backImg != null);
    }
    
    /**
     * Draws the previously captured double-buffered background image.
     * @param g2d The destination to draw to.
     */
    public void drawBackground(Graphics2D g2d) {
        if (backImg != null) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_SPEED);
            g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING,
                RenderingHints.VALUE_COLOR_RENDER_SPEED);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
            
            volImage = ImageUtils.drawVolatileImage(g2d, volImage, 0, 0, backImg);           
            if (VERBOSE)
                System.out.println("drawing captured background");
        }
    }
    
    public void pause() {
        // don't release this.backImg yet. It will be used in resume() to
        // determine whether this tool had the background previously captured.
        this.releaseBackground();
        this.mapComponent.repaint();
    }
    
    public void resume() {
        // capture the background on resume (background could have been changed
        // by previous tool). Only capture background if it previously was
        // captured too.
        if (backImg != null)
            this.captureBackground();
        this.mapComponent.repaint();
    }
}
