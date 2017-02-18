package ika.geoexport;

import ika.geo.GeoObject;
import ika.geo.GeoPath;
import ika.geo.GeoSet;
import java.awt.geom.PathIterator;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.Hashtable;
import java.util.Map;

/**
 *
 * @author jenny
 */
public class GeoJSONExporter extends GeoSetExporter {

    private Map<String, String> properties = new Hashtable<String, String>();

    public GeoJSONExporter(double mapScale) {
        super(mapScale);
    }

    @Override
    public void export(GeoSet geoSet, String filePath) throws IOException {
        if (geoSet == null || filePath == null) {
            throw new IllegalArgumentException();
        }
        if (geoSet.getNumberOfChildren() == 0) {
            throw new IllegalArgumentException();
        }
        PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(filePath)));
        try {
            this.export(geoSet, writer, true);
        } finally {
            writer.close();
        }
    }

    public void export(GeoSet geoSet, PrintWriter writer, boolean toplevel) {

        if (toplevel) {
            writer.println("{ \"type\": \"FeatureCollection\",\n  \"features\": [{");
            this.writeProperties(writer);
            writer.println("    \"geometry\":\n      { \"type\": \"GeometryCollection\", \"geometries\": [");
        }
        final int childrenCount = geoSet.getNumberOfChildren();
        for (int i = 0; i < childrenCount; i++) {
            GeoObject geoObject = geoSet.getGeoObject(i);
            // only write visible paths
            if (geoObject.isVisible()) {
                if (geoObject instanceof GeoPath) {
                    this.writeGeoPath(writer, (GeoPath) geoObject);
                } else if (geoObject instanceof GeoSet) {
                    this.export((GeoSet)geoObject, writer, false);
                }

            }
        }
        if (toplevel) {
            writer.println("     ] }");
            writer.println("    }");
            writer.println("  ]");
            writer.println("}");
        }
    }

    public void addProperty(String key, String value) {
        this.properties.put(key, value);
    }

    private void writeProperties(PrintWriter writer) {
        writer.print("    \"properties\": { ");

        int i = 0;
        for (String key : properties.keySet()) {
            String value = (String) this.properties.get(key);
            writer.print("\"" + key + "\": \"" + value + "\"");

            if (i++ < this.properties.size() - 1) {
                writer.print(", ");
            }
        }


        writer.println("},");
    }

    private void writeGeoPath(PrintWriter writer, GeoPath geoPath) {
        writer.write("        { \"type\": \"LineString\", \"coordinates\":\n          [");

        DecimalFormat format = new DecimalFormat("#.###");
        PathIterator iterator = geoPath.getPathIterator(null, flatness);
        double[] coordinates = new double[6];
        while (!iterator.isDone()) {
            final int type = iterator.currentSegment(coordinates);
            switch (type) {
                case PathIterator.SEG_MOVETO:
                case PathIterator.SEG_LINETO:
                    writer.print(" [ ");
                    writer.print(format.format(coordinates[0]));
                    writer.print(",");
                    writer.print(format.format(coordinates[1]));
                    writer.print(" ]");
                    iterator.next();
                    if (!iterator.isDone()) {
                        writer.print(",");
                    }
                    break;
            }
            
        }
        writer.println(" ] },");
    }

    @Override
    protected String getFileExtension() {
        return "geojson";
    }

}
