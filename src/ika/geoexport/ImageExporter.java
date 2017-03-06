/*
 * ImageExporter.java
 *
 * Created on May 13, 2005, 6:47 PM
 */

package ika.geoexport;

import ika.geo.*;
import java.io.*;
import java.awt.image.*;
import javax.imageio.*;
import ika.utils.*;

/**
 *
 * @author jenny
 */
public class ImageExporter {
    
    public static void exportGeoImage(GeoImage geoImage, String filePath)
    throws IOException {
        if (filePath == null || geoImage == null)
            return;
        filePath = FileUtils.forceFileNameExtension(filePath, "jpg");
        File file = new File(filePath);
        
        BufferedImage image = geoImage.getBufferedImage();
        BufferedImage exportImage = new BufferedImage(image.getWidth(),
                image.getHeight(), BufferedImage.TYPE_INT_RGB);
        // draw the passed image into the exportImage
        exportImage.getGraphics().drawImage(image, 0, 0, null);
        
        ImageIO.write(exportImage, "jpeg", file);
        
        String worldFilePath = ImageExporter.constructWorldFilePath(filePath);
        ImageExporter.writeWorldFile(worldFilePath, geoImage);
    }
    
    private static String constructWorldFilePath(String imageFilePath) {
        return FileUtils.replaceExtension(imageFilePath, "jgw");
    }
    
    private static void writeWorldFile(String worldFilePath, GeoImage geoImage)
    throws IOException{
        
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(
                new FileWriter(worldFilePath)))) {
            writer.println(geoImage.getPixelSizeX());
            writer.println(0);
            writer.println(0);
            writer.println(-geoImage.getPixelSizeY());
            writer.println(geoImage.getX());
            writer.println(geoImage.getY());
        }
    }
    
}
