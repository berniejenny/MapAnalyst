package ika.geoexport;

import java.io.*;
import java.awt.geom.*;
import ika.geo.*;

/**
 * Exporter for the ESRI Ungenerate file format.<br>
 * Ungenerate is a very simple text format for spaghetti data.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class UngenerateExporter extends GeoSetExporter {
    
    public UngenerateExporter(double mapScale){
        super (mapScale);
    }
    
    public void export(GeoSet geoSet, String filePath)  throws IOException {
        if (geoSet == null || filePath == null)
            throw new IllegalArgumentException();
        this.writeLines(geoSet, filePath);
    }
    
    /**
     * An internal helper method that writes to a PrintWriter.
     */
    private int writeLines(GeoSet geoSet, PrintWriter writer, int id) {
        if (geoSet.isVisible() == false)
            return id;
        
        final int numberOfChildren = geoSet.getNumberOfChildren();
        for (int i = 0; i < numberOfChildren; i++) {
            GeoObject geoObject = geoSet.getGeoObject(i);
            
            // only write visible objects
            if (geoObject.isVisible() == false)
                continue;
            
            if (geoObject instanceof GeoPath) {
                GeoPath geoPath = (GeoPath)geoObject;
                if (geoPath.getNumberOfSegments() < 2) {
                    continue;
                }
                PathIterator iterator = geoPath.getPathIterator(null, flatness);
                double [] coordinates = new double [6];
                boolean firstMove = true;
                while (!iterator.isDone()) {
                    final int type = iterator.currentSegment(coordinates);
                    switch (type) {
                        case PathIterator.SEG_MOVETO:
                            if (firstMove)
                                firstMove  = false;
                            else
                                writer.println("end");
                            writer.println(id++);
                            // fall thru
                        case PathIterator.SEG_LINETO:
                            writer.println(Double.toString(coordinates[0])
                            + "\t" + Double.toString(coordinates[1]));
                            break;
                    }
                    iterator.next();
                }
                writer.println("end");
            } else if (geoObject instanceof GeoSet) {
                GeoSet childGeoSet = (GeoSet)geoObject;
                id = writeLines(childGeoSet, writer, id);
            }
        }
        return id;
    }
    
    /**
     * Writes GeoPaths contained in a GeoSet to an Ungenerate file.
     * @param geoSet The GeoSet containing the GeoPaths to export.
     * @param filePath The path to the file.
     */
    private void writeLines(GeoSet geoSet, String filePath)
    throws IOException {
        if (geoSet.getNumberOfChildren() == 0)
            throw new IllegalArgumentException();
        
        PrintWriter writer = new PrintWriter(new BufferedWriter(
                new FileWriter(filePath)));
        this.writeLines(geoSet, writer, 0);
        writer.println("end");
        writer.close();   
    }

    @Override
    public String getFileExtension() {
        return "lin";
    }
}
