package ika.geoexport;

import ika.geo.*;
import ika.utils.FileUtils;
import java.io.*;

/**
 *
 * @author jenny
 */
abstract public class GeoSetExporter {

    protected double mapScale = 1;
    protected double flatness = 1;

    public GeoSetExporter(double mapScale) {
        this.mapScale = mapScale;
    }

    public void setPathFlatness(double flatness) {
        if (flatness <= 0.) {
            throw new IllegalArgumentException();
        }
        this.flatness = flatness;
    }

    abstract protected void export(GeoSet geoSet, String filePath) throws IOException;

    abstract public String getFileExtension();

    public void exportGeoSet(GeoSet geoSet, String filePath) throws IOException {
        filePath = FileUtils.forceFileNameExtension(filePath, getFileExtension());
        this.export(geoSet, filePath);
    }
}
