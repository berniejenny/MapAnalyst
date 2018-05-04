/*
 * ImageImporter.java
 *
 * Created on April 1, 2005, 12:14 PM
 */

package ika.geoimport;

import ika.geo.*;
import ika.gui.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import ika.utils.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.text.DecimalFormat;
import java.text.ParseException;
import javax.imageio.*;
import javax.imageio.stream.*;
import javax.swing.*;
import java.util.*;

/**
 * Importer for images. Converts images to GeoImages. Runs in its own thread.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class ImageImporter extends Thread {
    
    private String filePath;
    private ika.gui.ImageReaderProgressDialog progressDialog;
    private GeoSet destinationGeoSet;
    private GeoImage geoImage;
    private MapComponent mapComponent;
    private boolean askUserToGeoreferenceImage = true;
    
    private String getNotReadableErrorString() {
        final String notReadableErrorString = "The file at " + filePath +
                " is not a supported format, and cannot be read.";
        return notReadableErrorString;
    }
    
    @Override
    public void run() {
        
        this.geoImage = null;
        String warningMessage = null;
        String warningTitle = "Error - Image Import";
        boolean addImageToGeoSet = true;
        
        try {
            // initialize the progress dialog
            if (progressDialog != null) {
                progressDialog.showProgressDialog();
            }
            
            // read the image into a BufferedImage
            BufferedImage img = this.readImage();
            
            // the following cannot be cancelled, so disable the cancel button.
            if (this.progressDialog != null) {
                progressDialog.disableCancel();
            }
            
            // optimize the image for fast display
            img = ImageUtils.optimizeForGraphicsHardwareIfRequired(img);
            
            // create the GeoImage
            this.geoImage = new GeoImage(img, filePath);
            this.geoImage.setSelectable(false);
            this.geoImage.setName(filePath);
            
            // search and read an associated world file containing georeferencing
            // information. If there is a World file we are done.
            File worldFile = ImageImporter.searchWorldFile(filePath);
            if (worldFile != null) {
                ImageImporter.readWorldFile(geoImage, worldFile);
                return;
            }
            
            // hide the progress dialog
            if (this.progressDialog != null)
                this.progressDialog.finish();
            
            // could not find any world file associated with the image.
            // ask the user to geo-reference the image.
            if (this.askUserToGeoreferenceImage) {
                askPixelSizeOfReferenceMap(this.geoImage);
                addImageToGeoSet = this.geoImage.getPixelSizeX() > 0;
                return; // we are done.
            }
            
            // First, use information of the image file header for the cell size.
            if (!this.setCellSizeFromDPI()) {
                // could not extract information about the resolution form the image
                // file. Ask the user for the resolution of the image.
                askDPI(this.geoImage);
            }
            
            // Then, put the lower left corner of the image on the origin of the
            // coordinate system.
            ImageImporter.placeImageOnOrigin(this.geoImage);
            
        }  // do some exception handling here
        catch (java.lang.OutOfMemoryError e) {
            warningMessage = "There is not enough memory available.";
        } catch (java.io.IOException e) {
            warningMessage = "Could not open the file." + e.getMessage();
        } catch (Exception e) {
            warningMessage = "An error occurred. The image could not be found" +
                    " at this location:\n";
            warningMessage += filePath;
            warningMessage += "\nThe image has probably been moved to another location.";
            warningMessage += "\nTo load the image, use File - Import Old Map Image " +
                    "or File - Import New Map Image.";
        } finally {
            if (this.progressDialog != null)
                this.progressDialog.finish();
            if (warningMessage != null) {
                showErrorMessage(warningMessage, warningTitle);
            }
            
            // finally add the GeoImage to the GeoSet
            if (addImageToGeoSet && this.destinationGeoSet != null && geoImage != null) {
                javax.swing.SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        destinationGeoSet.addGeoObject(geoImage);
                        if (mapComponent != null)
                            mapComponent.showAll();
                    }
                });
            }
            
        }
    }
    
    /**
     * Imports an image and returns a GeoImage. Uses the ImageIO API.
     * If the JAI (JavaAdvancedImaging) package is installed, more
     * file formats are supported than with importGeoImage (also TIFF, etc.).
     * If this method receives a ImageReaderProgressDialog it runs in an separate
     * thread. If progressDialog is null, it does not run in an separate thread.
     * @param filePath The path to the file that will be imported.
     * @param destinationGeoSet The GeoSet that will receive the read GeoImage.
     * @param ImageReaderProgressDialog An optional progress dialog.
     */
    public void importGeoImageWithImageIOAsync(String filePath,
            GeoSet destinationGeoSet,
            ika.gui.ImageReaderProgressDialog progressDialog){
        
        if (filePath == null || destinationGeoSet == null)
            throw new IllegalArgumentException();
        
        // make sure there is an image importer for the file format.
        String fileExtension =  ika.utils.FileUtils.getFileExtension(filePath);
        if (!ika.utils.ImageUtils.canReadImageFile(fileExtension))
            throw new IllegalArgumentException("File format not supported.");
        
        this.filePath = filePath;
        this.destinationGeoSet = destinationGeoSet;
        this.progressDialog = progressDialog;
        if (progressDialog == null)
            this.run();
        else
            this.start();
    }
    
    public GeoImage importGeoImageWithImageIOSync(String filePath){
        
        if (filePath == null)
            throw new IllegalArgumentException();
        
        this.filePath = filePath;
        this.destinationGeoSet = null;
        this.progressDialog = null;
        this.run();
        return this.geoImage;
    }
    
    public static Dimension getImageDimension(File file) throws IOException {
        ImageReader reader = getImageReaderByFile(file);
        if (reader == null)
            return null;
        ImageInputStream iis = null;
        try {
            iis = ImageIO.createImageInputStream(file);
            reader.setInput(iis, false);
            final int width = reader.getWidth(0);
            final int height = reader.getHeight(0);
            return new Dimension(width, height);
        } finally {
            reader.dispose();
            try {
                // reader.dispose() does not close the ImageInputStream!
                // so do this here.
                iis.close();
            } catch (Exception exc) {}
        }
    }
    
    /**
     * Returns the dpi of an image. If it cannot be determined, returns null.
     * Most graphics formats (including jpg and png - but not gif) have a
     * setting for the image dimension in the x and y direction. This is the size
     * of a pixel in millimeter. JPEG don't always have a the dpi stored (e.g
     * when saved with Photoshop using the "Save for Web" command).
     *
     * From the DTD:
     * http://java.sun.com/j2se/1.4.2/docs/api/javax/imageio/
     * metadata/doc-files/standard_metadata.html
     *
     * <!ELEMENT "VerticalPixelSize" EMPTY>
     * <!-- The height of a pixel, in millimeters, as it should be
     * rendered on media -->
     * <!ATTLIST "VerticalPixelSize" "value" #CDATA #REQUIRED>
     * <!-- Data type: Float -->
     *
     * @param imageReader An ImageReader with an associated ImageInputStream.
     * @return The dimension of a pixel in millimeters. Returns null if dimension
     * cannot be identified.
     */
    public Point2D.Float getPixelSizeMillimeter() {
        
        float hps = Float.NaN;
        float vps = Float.NaN;
        
        try {
            org.w3c.dom.Node n = this.getImageMetaData();
            n = n.getFirstChild();
            while (n != null) {
                if (n.getNodeName().equals("Dimension")) {
                    org.w3c.dom.Node n2 = n.getFirstChild();
                    while (n2 != null) {
                        if (n2.getNodeName().equals("HorizontalPixelSize")) {
                            org.w3c.dom.NamedNodeMap nnm = n2.getAttributes();
                            org.w3c.dom.Node n3 = nnm.item(0);
                            hps = Float.parseFloat(n3.getNodeValue());
                        }
                        if (n2.getNodeName().equals("VerticalPixelSize")) {
                            org.w3c.dom.NamedNodeMap nnm = n2.getAttributes();
                            org.w3c.dom.Node n3 = nnm.item(0);
                            vps = Float.parseFloat(n3.getNodeValue());
                        }
                        n2 = n2.getNextSibling();
                    }
                }
                n = n.getNextSibling();
            }
            
        } catch (Exception e) {
            return null;
        }
        
        if (Float.isNaN(hps) 
                || Float.isNaN(vps)
                || Float.isInfinite(hps) 
                || Float.isInfinite(vps)) {
            return null;
        }
        
        return new Point2D.Float(hps, vps);
    }
    
    private boolean setCellSizeFromDPI() {
        Point2D pixelSizeMM = this.getPixelSizeMillimeter();
        if (pixelSizeMM != null) {
            this.geoImage.setPixelSizeX(pixelSizeMM.getX()/1000.);
            this.geoImage.setPixelSizeY(pixelSizeMM.getY()/1000.);
            return true;
        }
        return false;
    }
    
    private BufferedImage readImage() throws IOException {
        ImageReader reader = null;
        ImageInputStream iis = null;
        
        try {
            File file = new File(this.filePath);
            reader = getImageReaderByFile(file);
            if (reader == null)
                throw new java.io.IOException(this.getNotReadableErrorString());
            
            iis = ImageIO.createImageInputStream(file);
            reader.setInput(iis);
            reader.addIIOReadProgressListener(progressDialog);
            final int imageIndex = reader.getMinIndex();
            BufferedImage bufferedImage = reader.read(imageIndex);
            
            // if the user cancels the import, the image is not null,
            // but contains black areas.
            if (this.progressDialog != null && progressDialog.isCanceled())
                return null;
            return bufferedImage;
        } finally {
            if (reader != null) {
                reader.dispose();
                try {
                    // reader.dispose() does not close the ImageInputStream!
                    // so do this here.
                    if (iis != null)
                        iis.close();
                } catch (Exception exc) {
                    // do not throw an exception if an error occurs on closing.
                }
            }
        }
    }
    
    private org.w3c.dom.Node getImageMetaData() throws IOException {
        ImageReader reader = null;
        ImageInputStream iis = null;
        
        try {
            File file = new File(this.filePath);
            reader = getImageReaderByFile(file);
            if (reader == null)
                throw new java.io.IOException(this.getNotReadableErrorString());
            
            iis = ImageIO.createImageInputStream(file);
            reader.setInput(iis);
            final int imgID = reader.getMinIndex();
            javax.imageio.metadata.IIOMetadata meta = reader.getImageMetadata(imgID);
            org.w3c.dom.Node n = meta.getAsTree("javax_imageio_1.0");
            return n;
        } finally {
            if (reader != null) {
                reader.dispose();
                try {
                    // reader.dispose() does not close the ImageInputStream!
                    // so do this here.
                    if (iis != null)
                        iis.close();
                } catch (Exception exc) {
                    // do not throw an exception if an error occurs on closing.
                }
            }
        }
    }
    
    private static ImageReader getImageReaderByFile(File file) {
        String ext = FileUtils.getFileExtension(file.getName());
        Iterator iterator = ImageIO.getImageReadersBySuffix(ext);
        if (iterator.hasNext())
            return (ImageReader) iterator.next();
        else
            return null;
    }
    
    /** Asks the user for the resolution of an image and updates the passed
     * GeoImage accordingly.
     */
    private void askDPI(final GeoImage geoImage) {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                String msg = "The resolution of the image could not be determined.\n" +
                        "This information is very important for all further analysis.\n" +
                        "Please convert the image to another format -\n" +
                        "or re-save the image with another software -\n" +
                        "or enter the number of pixels per inch (DPI) below:";
                String dpiString = javax.swing.JOptionPane.showInputDialog(
                        mapComponent, msg);
                if (dpiString == null) {
                    return; // user cancelled
                }
                try {
                    final double dpi = Double.parseDouble(dpiString);
                    geoImage.setPixelSizeX(25.4 / dpi / 1000.);
                    geoImage.setPixelSizeY(25.4 / dpi / 1000.);
                } catch (Exception e) {
                    msg = "The number entered is invalid.";
                    JOptionPane.showMessageDialog(mapComponent, msg, "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    /** Asks the user for the resolution of an image and updates the passed
     * GeoImage accordingly.
     */
    private void askPixelSizeOfReferenceMap(final GeoImage geoImage) throws InvocationTargetException, InterruptedException {
        javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {

                try {
                    StringBuilder sb = new StringBuilder();
                    sb.append("There is no World file for georeferencing the image.\n");
                    sb.append("The scale of the map can therefore not be computed.\n");
                    sb.append("Press Cancel to first georeference the image with a GIS, \n");
                    sb.append("generate a World file, and then reload the image with MapAnalyst.\n");
                    sb.append("Or enter the size of a pixel in meters and press OK.\n");
                    sb.append("Enter a pixel size of 1 if you are not interested in the scale\n");
                    sb.append("or if you are comparing two non-spatial graphics.");
                    String pxString = JOptionPane.showInputDialog(mapComponent, sb.toString());
                    if (pxString == null) {
                        geoImage.setPixelSizeX(0);
                        geoImage.setPixelSizeY(0);
                        return; // user cancelled
                    }
                    double pxSize = new DecimalFormat().parse(pxString).doubleValue();
                    geoImage.setPixelSizeX(pxSize);
                    geoImage.setPixelSizeY(pxSize);
                } catch (ParseException ex) {
                    //
                }
            }
        });
    }
    
    /** Places a GeoImage so that the lower left corner is on the origin of
     * the coordinate system.
     */
    private static void placeImageOnOrigin(GeoImage geoImage) {
        if (geoImage != null) {
            geoImage.setX(0);
            final int imageHeight = geoImage.getBufferedImage().getHeight();
            geoImage.setY(imageHeight * geoImage.getPixelSizeY());
        }
    }
    
    private void showErrorMessage(final String message, final String title) {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                javax.swing.JOptionPane.showMessageDialog(mapComponent, message,
                        title, javax.swing.JOptionPane.ERROR_MESSAGE);
                
            }
        });
    }
    
    /**
     * Returns whether a WorlFile for an image file exists.
     * @param imageFilePath The path to the raster image for which a World file has to be searched.
     * @return True if a World file exists, false otherwise.
     */
    public static boolean hasWorldFile(String imageFilePath) {
        File file = null;
        try {
            file = ImageImporter.searchWorldFile(imageFilePath);
        } catch (Exception e) {}
        return (file != null);
    }
    
    /**
     * Searches a World file containing georeferencing information for a raster image.
     * @param imageFilePath The path to the raster image for which a World file has to be searched.
     * @return Returns a File object if found, null otherwise.
     */
    private static File searchWorldFile(String imageFilePath) {
        
        File worldFile = null;
        URL url;
        
        String origExtension = ika.utils.FileUtils.getFileExtension(imageFilePath);
        String extension = new String();
        
        // construct name of the new world file
        // if the extension is shorter than 3 characters, just add a "w"
        if (origExtension.length() < 3)	// wenn Extension kŸrzer als 3 Buchstaben: einfach "w" anhŠngen
        {
            extension = origExtension + "w";
            worldFile = new File(FileUtils.replaceExtension(imageFilePath, extension));
            if (worldFile.exists())
                return worldFile;
        }
        // try with a "W"
        if (origExtension.length() < 3) {
            extension = origExtension;
            extension += 'W';
            worldFile = new File(FileUtils.replaceExtension(imageFilePath, extension));
            if (worldFile.exists())
                return worldFile;
        }
        // take first and last character of the extension and append "w"
        extension = "";
        extension += origExtension.charAt(0);
        extension += origExtension.charAt(origExtension.length()-1);
        extension += 'w';
        worldFile = new File(FileUtils.replaceExtension(imageFilePath, extension));
        if (worldFile.exists())
            return worldFile;
        
        // try with "W"
        extension = "";
        extension += origExtension.charAt(0);
        extension += origExtension.charAt(origExtension.length()-1);
        extension += 'W';
        worldFile = new File(FileUtils.replaceExtension(imageFilePath, extension));
        if (worldFile.exists())
            return worldFile;
        
        // directly append "w" to the image file name
        worldFile = new File(imageFilePath + "w");
        if (worldFile.exists())
            return worldFile;
        
        // try with "W"
        worldFile = new File(imageFilePath + "W");
        if (worldFile.exists())
            return worldFile;
        
        // use ".w" as file extension
        worldFile = new File(FileUtils.replaceExtension(imageFilePath, "w"));
        if (worldFile.exists())
            return worldFile;
        
        // try with "W"
        worldFile = new File(FileUtils.replaceExtension(imageFilePath, "W"));
        if (worldFile.exists())
            return worldFile;
        
        return null;
    }
    
    /**
     * Reads georeferencing information for a raster image from a World file and
     * configures a GeoImage accordingly.
     * @param geoImage The GeoImage that will be georeferenced.
     * @param worldFile The World file containing the georeferencing information.
     * @throws java.io.IOException Throws an IOException if any error related to the file occurs.
     */
    private static void readWorldFile(GeoImage geoImage, File worldFile)
    throws java.io.IOException {
        
        BufferedReader in = new BufferedReader(new FileReader(worldFile));
        double pixelSizeHorizontal = Double.parseDouble(in.readLine());
        double rotX = Double.parseDouble(in.readLine());
        double rotY = Double.parseDouble(in.readLine());
        double pixelSizeVertical = Double.parseDouble(in.readLine());
        double west = Double.parseDouble(in.readLine());
        double north = Double.parseDouble(in.readLine());
        
        pixelSizeHorizontal = Math.abs(pixelSizeHorizontal);
        pixelSizeVertical = Math.abs(pixelSizeVertical);
        if (rotX != 0 || rotY != 0)
            return;
        
        geoImage.setPixelSizeX(pixelSizeHorizontal);
        geoImage.setPixelSizeY(pixelSizeVertical);
        
        geoImage.setX(west);
        geoImage.setY(north);
    }
    
    /**
     * Imports an image and returns a GeoImage. Uses standard java image support. Only
     * a limited range of formats is supported (basically GIF and JPEG).
     * @param filePath The path to the file that will be imported.
     * @return Returns a GeoImage, or null if the image could not be imported.
     */
    public static GeoImage importGeoImage(String filePath) {
        Image image = new javax.swing.ImageIcon(filePath).getImage();
        // convert the Image to a BufferedImage
        BufferedImage bufferedImage = ImageUtils.makeBufferedImage(image);
        GeoImage geoImage = new GeoImage(bufferedImage, null);
        
        // search and read an associated World file containing georeferencing
        // information.
        try {
            File worldFile = ImageImporter.searchWorldFile(filePath);
            ImageImporter.readWorldFile(geoImage, worldFile);
        } catch (Exception e) {}
        
        return geoImage;
    }
    
    public MapComponent getMapComponent() {
        return mapComponent;
    }
    
    public void setMapComponent(MapComponent mapComponent) {
        this.mapComponent = mapComponent;
    }
    
    public void setAskUserToGeoreferenceImage(boolean askUserToGeoreferenceImage) {
        this.askUserToGeoreferenceImage = askUserToGeoreferenceImage;
    }
    
}
