/*
 * PointImporter.java
 *
 * Created on June 29, 2005, 4:28 PM
 *
 */

package ika.geoimport;

import java.io.*;
import ika.geo.*;
import java.awt.Component;
import java.util.*;


/**
 *
 * @author jenny
 */
public class PointImporter {
    
    /**
     * Reads an ASCII file with points.
     * @param filePath path to the file.
     */
    public static GeoSet importPoints (String filePath, Component parentComponent) {
        
        GeoSet geoSet = new GeoSet();
        
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(filePath));           
            String str;           
            while ((str = in.readLine()) != null) {
                StringTokenizer tokenizer = new StringTokenizer(str, " \t,;");
                String name = (String)tokenizer.nextToken();
                double x = Double.parseDouble((String)tokenizer.nextToken());
                double y = Double.parseDouble((String)tokenizer.nextToken());
                
                GeoPoint point = new GeoPoint(x, y);
                point.setName (name);
                geoSet.addGeoObject(point);
            }
        } catch (Exception e) {
            String msg = "The points could not be imported.\n" + e.toString();
            javax.swing.JOptionPane.showMessageDialog(parentComponent, msg,
                    "Import Error", javax.swing.JOptionPane.ERROR_MESSAGE);
        } finally {
            if (in != null)
                try {
                    in.close();
                } catch (Exception e) {
                }
        }
        return geoSet;
    }
}
