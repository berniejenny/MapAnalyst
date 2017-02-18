package ika.utils;

import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import javax.swing.JOptionPane;

/**
 * A utility class for loading, converting and drawing images.
 */
public class ImageUtils {
    
    /**
     * Required by waitForImage.
     */
    private static final Component sComponent = new Component() {};
    /**
     * Required by waitForImage.
     */
    private static final MediaTracker sTracker = new MediaTracker(sComponent);
    /**
     * Required by waitForImage.
     */
    private static int sID = 0;
    
    /**
     * Waits for an image to load fully. Returns true if everything goes well,
     * false on error. This method should support multi-threading (not tested).<br>
     * From <link>http://examples.oreilly.com/java2d/examples/Utilities.java</link><br>
     * In: Knudsen, J. 1999. Java 2D Graphics. O'Reilly. Page 198.<br>
     * @param image The image to load.
     * @return Returns true if everything goes well, false on error.
     */
    public static boolean waitForImage(Image image) {
        int id;
        synchronized(sComponent) {
            id = sID++;
        }
        sTracker.addImage(image, id);
        try {
            sTracker.waitForID(id);
        } catch (InterruptedException ie){
            return false;
        }
        if (sTracker.isErrorID(id))
            return false;
        return true;
    }
    
    /**
     * Converts an Image to a rgb BufferedImage. The resulting image does not
     * contain transparent pixels.
     * @param image The image to convert.
     * @return Returns the BufferedImage
     */
    public static BufferedImage makeBufferedImage(Image image) {
        return makeBufferedImage(image, BufferedImage.TYPE_INT_RGB);
    }
    
    /**
     * Converts an Image to a BufferedImage.
     * @param image The image to convert.
     * @param imageType The type of the BufferedImage, e.g. BufferedImage.TYPE_INT_RGB
     * @return Returns the BufferedImage
     */
    public static BufferedImage makeBufferedImage(Image image, int imageType) {
        if (waitForImage(image) == false) return null;
        BufferedImage bufferedImage = new BufferedImage(
                image.getWidth(null), image.getHeight(null), imageType);
        Graphics2D g2 = bufferedImage.createGraphics();
        g2.drawImage(image, null, null);
        return bufferedImage;
    }
    
    /**
     * Converts an image to an optimized version for the default screen.<br>
     * The optimized image should draw faster.
     * @param image The image to optimize.
     * @return Returns the optimized image.
     */
    public static BufferedImage optimizeForGraphicsHardware(BufferedImage image) {
        try {
            // create an empty optimized BufferedImage
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice gd = ge.getDefaultScreenDevice();
            GraphicsConfiguration gc = gd.getDefaultConfiguration();
            int w = image.getWidth();
            int h = image.getHeight();

            // strange things happen on Linux and Windows on some systems when
            // monitors are set to 16 bit.
            boolean bit16 = gc.getColorModel().getPixelSize() < 24;
            final int transp;
            if (bit16) {
                transp = Transparency.TRANSLUCENT;
            } else {
                transp = image.getColorModel().getTransparency();
            }
            BufferedImage optimized = gc.createCompatibleImage(w, h, transp);

            // draw the passed image into the optimized image
            optimized.getGraphics().drawImage(image, 0, 0, null);
            return optimized;
        } catch (Exception e) {
            // return the original image if an exception occured.
            return image;
        }
    }

    /**
     * Converts an image to an optimized version for the default screen.<br>
     * The optimized image should draw faster. Only creates a new image if
     * the passed image is not already optimized.
     * @param image The image to optimize.
     * @return Returns the optimized image.
     */
    public static BufferedImage optimizeForGraphicsHardwareIfRequired(BufferedImage image) {
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice gd = ge.getDefaultScreenDevice();
            GraphicsConfiguration gc = gd.getDefaultConfiguration();

            // return passed image if it already is optimized
            if (image.getColorModel().equals(gc.getColorModel())) {
                return image;
            }
            // create an empty optimized BufferedImage
            int w = image.getWidth();
            int h = image.getHeight();

            // strange things happen on Linux and Windows on some systems when
            // monitors are set to 16 bit.
            boolean bit16 = gc.getColorModel().getPixelSize() < 24;
            final int transp;
            if (bit16) {
                transp = Transparency.TRANSLUCENT;
            } else {
                transp = image.getColorModel().getTransparency();
            }
            BufferedImage optimized = gc.createCompatibleImage(w, h, transp);

            // draw the passed image into the optimized image
            optimized.getGraphics().drawImage(image, 0, 0, null);
            return optimized;
        } catch (Exception e) {
            // return the original image if an exception occured.
            return image;
        }
    }
    
    
    /**
     * From The Java Developers Almanac 1.4 <br>
     * e674. Creating and Drawing an Accelerated Image
     * This method draws a volatile image and returns it, or possibly a
     * newly created volatile image object. Subsequent calls to this method
     * should always use the returned volatile image.
     * If the contents of the image is lost, it is recreated using orig.
     * img may be null, in which case a new volatile image is created.
     * @param g The destination for drawing.
     * @param img The image that will be drawn. Might be null, in which case a new volatile image is created.
     * @param x Horizontal position for drawing the image.
     * @param y Vertical position for drawing the image.
     * @return Returns a VolatileImage if one has been created, null otherwise. Pass this
     * VolatileImage to subsequent calls of drawVolatileImage.
     * @param orig The original image that will be converted to a VolatileImage, if img is null, or
     * if img is not valid anymore.
     */
    public static VolatileImage drawVolatileImage(Graphics2D g, VolatileImage img,
            int x, int y, Image orig) {
        final int MAX_TRIES = 5;
        for (int i=0; i<MAX_TRIES; i++) {
            if (img != null) {
                // Draw the volatile image
                g.drawImage(img, x, y, null);
                
                // Check if it is still valid
                if (!img.contentsLost()) {
                    return img;
                }
            } else {
                // Create the volatile image
                img = g.getDeviceConfiguration().createCompatibleVolatileImage(
                        orig.getWidth(null), orig.getHeight(null));
            }
            
            // Determine how to fix the volatile image
            switch (img.validate(g.getDeviceConfiguration())) {
                case VolatileImage.IMAGE_OK:
                    // This should not happen
                    break;
                case VolatileImage.IMAGE_INCOMPATIBLE:
                    // Create a new volatile image object;
                    // this could happen if the component was moved to another device
                    img.flush();
                    img = g.getDeviceConfiguration().createCompatibleVolatileImage(
                            orig.getWidth(null), orig.getHeight(null));
                case VolatileImage.IMAGE_RESTORED:
                    // Copy the original image to accelerated image memory
                    Graphics2D gc = (Graphics2D)img.createGraphics();
                    gc.drawImage(orig, 0, 0, null);
                    gc.dispose();
                    break;
            }
        }
        
        // The image failed to be drawn after MAX_TRIES;
        // draw with the non-accelerated image
        g.drawImage(orig, x, y, null);
        return img;
    }
    
    /**
     * Returns true if the specified file extension can be read
    */
    public static boolean canReadImageFile(String fileExt) {
        java.util.Iterator iter = javax.imageio.ImageIO.getImageReadersBySuffix(fileExt);
        return iter.hasNext();
    }
    
    /**
     * Returns true if the specified file extension can be written.
    */
    public static boolean canWriteImageFile(String fileExt) {
        java.util.Iterator iter = javax.imageio.ImageIO.getImageWritersBySuffix(fileExt);
        return iter.hasNext();
    }
    
    
    public static void printImageReadWriteCapabilites () {
        System.out.println ("Read tif: " + canReadImageFile("tif"));
        System.out.println ("Read jpg: " + canReadImageFile("jpg"));
        System.out.println ("Read png: " + canReadImageFile("png"));
        System.out.println ("Read gif: " + canReadImageFile("gif"));
        
        System.out.println ("Write tif: " + canWriteImageFile("tif"));
        System.out.println ("Write jpg: " + canWriteImageFile("jpg"));
        System.out.println ("Write png: " + canWriteImageFile("png"));
        System.out.println ("Write gif: " + canWriteImageFile("gif"));
    }
     
}
