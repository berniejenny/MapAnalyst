/*
 * UngenerateImporter.java
 *
 * Created on April 1, 2005, 12:02 PM
 */

package ika.geoimport;

import ika.geo.*;
import java.util.*;
import java.io.*;

/**
 * An importer for the Ungenerate file format.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class UngenerateImporter {
    
    /**
     * Reads an Ungenerate file and returns the found GeoObjects in a GeoSet.
     * @param filePath The file to import.
     * @return A GeoSet containing all read GeoObjects.
     */
    public static GeoSet read(String filePath) throws IOException {
        GeoSet geoSet = new GeoSet();
        boolean foundEnd = false;
        BufferedReader in = new BufferedReader(new FileReader(filePath));
        try {
            String str;
            boolean firstPoint;
            while ((str = in.readLine()) != null) {
                GeoPath geoPath = new GeoPath();
                firstPoint = true;
                
                while (true) {
                    str = in.readLine();
                    if (str == null || str.length() == 0)
                        break;
                    str = str.trim().toLowerCase();
                    foundEnd |= str.startsWith("end");
                    
                    if (str.startsWith("end") )
                        break;
                    try {
                        StringTokenizer tokenizer = new StringTokenizer(str, " \t");
                        float x = Float.parseFloat((String)tokenizer.nextToken());
                        float y = Float.parseFloat((String)tokenizer.nextToken());
                        if (firstPoint) {
                            geoPath.moveTo(x, y);
                            firstPoint = false;
                        } else
                            geoPath.lineTo(x, y);
                    } catch (NoSuchElementException e) {
                        // found a line without any readable data. Just read the next line
                    }
                }
                
                if (geoPath.hasOneOrMoreSegments()) {
                    VectorSymbol symbol = new VectorSymbol(java.awt.Color.blue, java.awt.Color.black, 1);
                    symbol.setScaleInvariant(true);
                    geoPath.setVectorSymbol(symbol);
                    geoSet.addGeoObject(geoPath);
                }
            }
            // test if last line was "end"
            if (!foundEnd)
                throw new IOException("Invalid Ungenerate file");
            
        } finally {
            in.close();
        }
        
        return geoSet;
    }
}
