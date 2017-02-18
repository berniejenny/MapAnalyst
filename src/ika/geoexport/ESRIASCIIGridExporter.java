/*
 * ESRIASCIIGridExporter.java
 *
 * Created on August 14, 2005, 4:17 PM
 *
 */

package ika.geoexport;

import ika.geo.*;
import java.io.*;

/**
 *
 * @author jenny
 */
public class ESRIASCIIGridExporter {
    
    /** Creates a new instance of ESRIASCIIGridExporter */
    public ESRIASCIIGridExporter() {
    }
    
    public void export(GeoGrid geoGrid, String filePath)
    throws java.io.IOException {
        
        PrintWriter writer = new PrintWriter(new BufferedWriter(
                new FileWriter(filePath)));
        
        String lineSeparator = System.getProperty("line.separator");
        writer.write("ncols " + geoGrid.getCols() + lineSeparator);
        writer.write("nrows " + geoGrid.getRows() + lineSeparator);
        writer.write("xllcorner  " + geoGrid.getWest() + lineSeparator);
        writer.write("yllcorner" + geoGrid.getSouth() + lineSeparator);
        writer.write("cellsize " + geoGrid.getMeshSize() + lineSeparator);
        writer.write("nodata_value " + Float.MIN_VALUE + lineSeparator);
        float[][] grid = geoGrid.getGrid();
        for (int r = 0; r < grid.length; ++r) {
            for (int c = 0; c < grid[0].length; ++c) {
                float v = grid[r][c];
                // A "NaN" value is not equal to itself. So use isNan().
                if (Float.isNaN(v)) {
                    v = Float.MIN_VALUE;
                }
                writer.write(Float.toString(v));
                writer.write (" ");
            }
            writer.write (lineSeparator);
        }
        writer.close();
    }
}
