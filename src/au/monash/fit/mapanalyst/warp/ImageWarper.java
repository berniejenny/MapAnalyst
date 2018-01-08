package au.monash.fit.mapanalyst.warp;

import ika.geo.GeoImage;
import ika.mapanalyst.MultiquadricInterpolation;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import ika.transformation.Transformation;

/**
 * Warping a source image to a destination image with a combination of an affine
 * and a multiquadric interpolation.
 * 
 * Important: This is experimental and not included in the release version.
 *
 * @author Bernhard Jenny, Faculty of Information Technology, Monash University,
 * Melbourne, Australia
 */
public class ImageWarper {

    /**
     * A transformation from the destination image to the source image.
     */
    private final Transformation dstToSrcTransformation;

    /**
     * A transformation of the source image.
     */
    private final MultiquadricInterpolation interpolation;

    /**
     * The source image to transform
     */
    private final GeoImage srcGeoImage;

    private final double[][] controlPointsInDestinationImage;

    public ImageWarper(Transformation dstToSrcTransformation,
            MultiquadricInterpolation interpolation,
            GeoImage srcGeoImage,
            double[][] controlPointsInDestinationImage) {

        // TODO test for valid parameters
        this.dstToSrcTransformation = dstToSrcTransformation;
        this.interpolation = interpolation;
        this.srcGeoImage = srcGeoImage;
        this.controlPointsInDestinationImage = controlPointsInDestinationImage;
    }

    /**
     * Warp the source image to create the destination image.
     *
     * @return the new destination image
     */
    public GeoImage warp() {
        Rectangle2D dstBounds = warpedBounds();
        BufferedImage dstImage = createDestinationImage(dstBounds, srcGeoImage.getBufferedImage());
        double cellSize = dstBounds.getWidth() / dstImage.getWidth();
        double destinationWest = dstBounds.getMinX();
        double destinationNorth = dstBounds.getMaxY();
        GeoImage dstGeoImage = new GeoImage(dstImage, destinationWest, destinationNorth, cellSize);
        double[] destXY = new double[2];
        int dstImageWidth = dstImage.getWidth();
        int dstImageHeight = dstImage.getHeight();
        System.out.println("image size: " + dstImageHeight + " " + dstImageWidth);
        for (int row = 0; row < dstImageHeight; row++) {
            for (int col = 0; col < dstImageWidth; col++) {
                destXY[0] = destinationWest + (col + 0.5) * cellSize;
                destXY[1] = destinationNorth - (row + 0.5) * cellSize;
                double[] srcXY = dstToSrcTransformation.transform(destXY);
                interpolation.transform(srcXY, 1);
//                // Convert srcPxX/srcPxY from meters to pixels.
//                int srcPxX = (int) Math.round(srcXY[0]);
//                int srcPxY = (int) Math.round(srcXY[1]);
                
                //if (srcPxX >= 0 && srcPxX < dstImageWidth && srcPxY >= 0 && srcPxY < dstImageHeight) {
                if (srcGeoImage.getBounds2D().contains(srcXY[0], srcXY[1])) {
                    int argb = srcGeoImage.getBilinearInterpol(srcXY[0], srcXY[1]);
                    dstImage.setRGB(col, row, argb);
                }
                // System.out.println(srcPxX + " " + srcPxY);

            }
        }
        return dstGeoImage;
    }

    /**
     * Finds the bounding box of the destination image.
     *
     * TODO currently only finds the bounding box of the control points.
     *
     * @return bounding box
     */
    private Rectangle2D warpedBounds() {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int i = 0; i < controlPointsInDestinationImage.length; i++) {
            double x = controlPointsInDestinationImage[i][0];
            double y = controlPointsInDestinationImage[i][1];
            if (x > maxX) {
                maxX = x;
            }
            if (x < minX) {
                minX = x;
            }
            if (y > maxY) {
                maxY = y;
            }
            if (y < minY) {
                minY = y;
            }
        }
        return new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
    }

    /**
     * Creates the destination image, which will have approximately the same
     * total number of pixels as the source image.
     *
     * @param dstBounds bounding box for the destination image
     * @param sourceImage the source image
     * @return
     */
    private BufferedImage createDestinationImage(Rectangle2D dstBounds, BufferedImage sourceImage) {
        int nbrPixels = sourceImage.getHeight() * sourceImage.getWidth();
        double destinationImageRatio = dstBounds.getWidth() / dstBounds.getHeight();
        double destinationImageHeight = Math.sqrt(nbrPixels / destinationImageRatio);
        double destinationImageWidth = nbrPixels / destinationImageHeight;
        int w = (int) Math.ceil(destinationImageWidth);
        int h = (int) Math.ceil(destinationImageHeight);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        return img;
    }

}
