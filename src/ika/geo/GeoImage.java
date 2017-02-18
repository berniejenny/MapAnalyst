package ika.geo;
/*
 * GeoImage.java
 *
 * Created on 5. Februar 2005, 14:52
 */

import ika.utils.GeometryUtils;
import ika.geoimport.ImageImporter;
import java.awt.geom.*;
import java.awt.image.*;
import java.awt.*;
import java.io.*;

/**
 * A simple class to display georeferenced images.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class GeoImage extends GeoObject implements Serializable {
    
    private static final long serialVersionUID = -65438923204671769L;
    
    /**
     * The image to display. GeoImage does not offer any functionality to
     * edit this image.
     */
    transient private BufferedImage image; // cannot be serialized!
    
    /**
     * Path of file that was read.
     */
    private final String filePath;
    
    /**
     * Horizontal coordinate of top left corner of this image.
     */
    private double x;
    
    /**
     * Vertical coordinate of top left corner of this image.
     */
    private double y;
    
    /**
     * Size of a pixel in horizontal direction.
     */
    private double pixelSizeX;
    
    /**
     * Size of a pixel in vertical direction.
     */
    private double pixelSizeY;
    
    public GeoImage() {
        this.image = null;
        this.filePath = null;
        this.x = 0;
        this.y = 0;
        this.pixelSizeX = 1;
        this.pixelSizeY = 1;
    }
    
    /**
     * Create a new instance of GeoImage.
     * @param image A reference to an image to display. The image is not copied,
     * instead a reference is retained.
     * @param x Top left corner of this image.
     * @param y Top left corner of this image.
     * @param pixelSize Size of a pixel.
     */
    public GeoImage(BufferedImage image, double x, double y, double pixelSize) {
        this.image = image;
        this.filePath = null;
        this.x = x;
        this.y = y;
        this.pixelSizeX = pixelSize;
        this.pixelSizeY = pixelSize;
    }
    
    /**
     * Create a new instance of GeoImage. The lower left corner of the image is
     * placed at 0/0, the size of a pixel in world coordinates equals 1.
     * @param image A reference to an image to display. The image is not copied,
     * instead a reference is retained.
     */
    public GeoImage(BufferedImage image, String filePath) {
        this.image = image;
        this.filePath = filePath;
        this.resetGeoreference();
    }
    
    private void readObject(ObjectInputStream stream)
    throws IOException, ClassNotFoundException {
        
        // read the serializable part of this GeoPath.
        stream.defaultReadObject();
        
        ImageImporter importer = new ImageImporter();
        importer.setAskUserToGeoreferenceImage(false);
        GeoImage geoImage = importer.importGeoImageWithImageIOSync(filePath);
        if (geoImage != null)
            this.image = geoImage.getBufferedImage();
        geoImage = null;
    }
    
    public void resetGeoreference() {
        this.x = 0;
        if (this.image != null)
            this.y = this.image.getHeight();
        else
            this.y = 0;
        this.pixelSizeX = 1;
        this.pixelSizeY = 1;
    }
    
    public PathIterator getPathIterator(AffineTransform affineTransform) {
        Rectangle2D bounds = this.getBounds2D();
        if (bounds != null)
            return bounds.getPathIterator(affineTransform);
        else
            return null;
    }
    
    public void draw(Graphics2D g2d, double scale, boolean drawSelectionState) {
        if (image == null || !visible)
            return;
        
        Rectangle2D.Double bounds = (Rectangle2D.Double)this.getBounds2D();
        if (bounds == null)
            return;
        
        AffineTransform trans = new AffineTransform();
        trans.scale(1, -1);
        trans.translate(x, -y);
        trans.scale(bounds.getWidth() / this.image.getWidth(),
                bounds.getHeight() / this.image.getHeight());
        g2d.drawImage(image, trans, null);
        
        if (drawSelectionState && isSelected()) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_OFF);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_SPEED);
            
            g2d.setColor(ika.utils.ColorUtils.getHighlightColor());
            BasicStroke selectionStroke = new BasicStroke((float)(1./scale));
            g2d.setStroke(selectionStroke);
            g2d.draw(bounds);
            
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
        }
    }
    
    public boolean isPointOnSymbol(java.awt.geom.Point2D point, double tolDist,
            double scale) {
        if (image == null)
            return false;
        Rectangle2D bounds = this.getBounds2D();
        GeometryUtils.enlargeRectangle(bounds, tolDist);
        return bounds.contains(point);
    }
    
    public boolean isIntersectedByRectangle(Rectangle2D rect, double scale) {
        // Test if if the passed rectangle and the bounding box of this object
        // intersect.
        // Use GeometryUtils.rectanglesIntersect and not Rectangle2D.intersects!
        final Rectangle2D bounds = this.getBounds2D();
        return ika.utils.GeometryUtils.rectanglesIntersect(rect, bounds);
    }
    
    public Rectangle2D getBounds2D() {
        if (image == null)
            return null;
        
        return new Rectangle2D.Double(x, y - pixelSizeY * image.getHeight(),
                pixelSizeX * image.getWidth(),
                pixelSizeY * image.getHeight());
    }
    
    public void move(double dx, double dy) {
        this.x += dx;
        this.y += dy;
    }
    /*
    public void transform(ika.transformation.Transformation transformation) {
        AffineTransform affineTransform = transformation.getAffineTransform();
        this.transform (affineTransform);
    }
    */
    public void transform(AffineTransform affineTransform) {
        System.out.println ("ATTENTION: GeoImage.transform() has not been tested");
        if (affineTransform == null)
            throw new IllegalArgumentException("Transformation not defined");
        
        RenderingHints hints = new RenderingHints(null);
        hints.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        hints.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
   
        // transform bounding box
        Rectangle2D r2d = this.getBounds2D();
        double points[] = {
            r2d.getMinX(), r2d.getMinY(),
            r2d.getMaxX(), r2d.getMinY(),
            r2d.getMaxX(), r2d.getMaxY(),
            r2d.getMinX(), r2d.getMaxY()};
        affineTransform.transform(points, 0, points, 0, 4);
        
        // find corners of transformed image
        double xMin = Double.MAX_VALUE;
        double xMax = Double.MIN_VALUE;
        double yMin = Double.MAX_VALUE;
        double yMax = Double.MIN_VALUE;        
        for (int i = 0; i < 4; ++i) {
            xMin = Math.min(xMin, points[i*2]);
            xMax = Math.max(xMax, points[i*2]);
            yMin = Math.min(yMin, points[i*2+1]);
            yMax = Math.max(yMax, points[i*2+1]);
        }
        
        // transform the image
        AffineTransformOp op = new AffineTransformOp(affineTransform, hints);
        
        System.out.println("op.getBounds2D(image) " + op.getBounds2D(image));
        System.out.println("this.getBounds2D() " + this.getBounds2D());
        System.out.println("op.getTransform() " + op.getTransform());
        System.out.println("image.getWidth() " + image.getWidth());
        System.out.println("image.getHeight() " + image.getHeight());
        
        // If any dest_image points end up negative, they will be thrown away!
        Rectangle2D transformedBounds = op.getBounds2D(image);
        AffineTransform at = new AffineTransform();
        
        // Make sure the new image is not getting too large.
        final double sx = this.getBounds2D().getWidth() / transformedBounds.getWidth();
        final double sy = this.getBounds2D().getHeight() / transformedBounds.getHeight();
        final double s = Math.max (sx, sy);
        at.scale (s, s);
        
        // Make sure the transformation only produces positive coordinates.
        at.translate(-transformedBounds.getMinX(), -transformedBounds.getMinY());
   //     at.scale(1, -1);
        at.concatenate(affineTransform);
        op = new AffineTransformOp(at, hints);
        
        System.out.println("op.getBounds2D(image) " + op.getBounds2D(image));
        System.out.println("op.getTransform() " + op.getTransform());
        
        BufferedImage transformedImage = op.createCompatibleDestImage(image, null);
        transformedImage = op.filter(image, transformedImage);
        if (transformedImage == null)
            return;

        System.out.println("transformedImage.getWidth() " + transformedImage.getWidth());
        System.out.println("transformedImage.getHeight() " + transformedImage.getHeight());
        
        this.image = transformedImage;
        
        // new position of upper left pixel
        // this.x = xMin;
        this.y = yMax;
        
        // compute new pixel size
        this.pixelSizeX /= sx;
        this.pixelSizeY /= sy;
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
    
    public double getPixelSizeX() {
        return pixelSizeX;
    }
    
    public void setPixelSizeX(double pixelSizeX) {
        this.pixelSizeX = pixelSizeX;
    }
    
    public double getPixelSizeY() {
        return pixelSizeY;
    }
    
    public void setPixelSizeY(double pixelSizeY) {
        this.pixelSizeY = pixelSizeY;
    }
    
    public BufferedImage getBufferedImage() {
        return this.image;
    }
    
    public String getFilePath() {
        return this.filePath;
    }
}
