/*
 * ShapeExporter.java
 *
 * Created on April 13, 2007, 3:41 PM
 *
 */

package ika.geoexport;

import ika.geo.GeoSet;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 *
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class ShapeExporter extends GeoSetExporter {
    
    private ShapeGeometryExporter shapeGeometryExporter;
    
    private boolean shapeTypeSet = false;
    
    /** Creates a new instance of ShapeExporter */
    public ShapeExporter(double mapScale){
        super(mapScale);
        this.shapeGeometryExporter  = new ShapeGeometryExporter(mapScale);
    }
    
    public String getFileFormatName() {
        return "Shape";
    }
    
    public String getFileExtension() {
        return "shp";
    }
    
    protected void export(GeoSet geoSet, OutputStream outputStream)
    throws IOException {
        
        if (!this.shapeTypeSet)
            shapeGeometryExporter.setShapeTypeFromFirstGeoObject(geoSet);
        shapeGeometryExporter.export(geoSet, outputStream);
        
    }
    
    public int getFeatureCount() {
        return this.shapeGeometryExporter.getWrittenRecordCount();
    }
    
    public void exportTableForGeometry(String geometryFilePath,
            Table table, GeoSet geoSet) throws IOException {
        
        FileOutputStream dbfOutputStream = null;
        FileOutputStream shxOutputStream = null;
        
        try {
            String dbfPath = ika.utils.FileUtils.replaceExtension(geometryFilePath, "dbf");
            dbfOutputStream = new FileOutputStream(dbfPath);
            new DBFExporter().exportTable(dbfOutputStream, table);
            
            String shxPath = ika.utils.FileUtils.replaceExtension(geometryFilePath, "shx");
            shxOutputStream = new FileOutputStream(shxPath);
            shapeGeometryExporter.writeSHXFile(shxOutputStream, geoSet);
            
        } finally {
            if (dbfOutputStream != null)
                dbfOutputStream.close();
            if (shxOutputStream != null)
                shxOutputStream.close();
        }
    }

    @Override
    public void setPathFlatness(double flatness) {
        super.setPathFlatness(flatness);
        this.shapeGeometryExporter.setPathFlatness(flatness);
    }

    /**
     * Set the type of shape file that will be generated. Valid values are 
     * POINT_SHAPE_TYPE, POLYLINE_SHAPE_TYPE, and POLYGON_SHAPE_TYPE.
     * The default value is POLYLINE_SHAPE_TYPE.
     */
    public void setShapeType(int shapeType) {
        
        this.shapeTypeSet = true;
        this.shapeGeometryExporter.setShapeType(shapeType);
        
    }

     public final void export (GeoSet geoSet, String filePath) throws IOException {

        if (geoSet == null || filePath == null)
            throw new IllegalArgumentException();

        OutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(filePath);
            this.export(geoSet, outputStream);
        } finally {
            if (outputStream != null)
                outputStream.close();
        }

    }
   
}
