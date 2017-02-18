package ika.mapanalystonline;

import ika.geo.GeoSet;
import ika.geoexport.GeoJSONExporter;
import ika.mapanalyst.Manager;
import ika.mapanalyst.MapAnalyzer;
import ika.transformation.Transformation;
import ika.utils.CoordinateFormatter;
import ika.utils.NumberFormatter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 *
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich
 */
public class MapAnalystHeadless {

    public static final String DATE_FORMAT_NOW = "yyyy-MM-dd HH:mm:ss";

    public enum VisType {

        GRID, VECTOR, SCALE_ROT
    };

    private static String getScaleRotInfo(Manager manager) {
        Transformation trans = manager.getTransformation();
        if (trans == null || !manager.isTransformationInitialized()) {
            return "-; -";
        }
        final boolean invert = manager.isShowInOldMap();
        String scaleStr = NumberFormatter.formatScale(null, trans.getScale(invert));
        String rotStr = trans.formatRotation(null, trans.getRotation(invert));
        return scaleStr + "; " + rotStr;
    }

    public static void computeVisualization(
            String points,
            VisType visType,
            boolean forOldMap,
            double dpi,
            double bezierTolerance,
            double gridCellSize,
            boolean oldPointsInPixel,
            PrintWriter writer) throws Exception {

        double pixelSize = 2.54 / 100. / dpi;
        Manager manager = new Manager();
        
        // import the points
        manager.importLinksFromString(points, null);
        if(!manager.getLinkManager().hasEnoughLinkedPointsForComputation()) {
            throw new IllegalStateException("not enough points");
        }
        
        // convert from pixels to meters if necessary
        if (oldPointsInPixel) {
            manager.getLinkManager().scaleLinkedPointsInOldMap(pixelSize);
        }
        
        // compute visualization for coordinate system of old or new map
        manager.setShowInOldMap(forOldMap);

        // init visualzations
        CoordinateFormatter cf = new CoordinateFormatter("###,##0.00", "###,##0", 1);
        manager.getDistortionGrid().setMeshSize(gridCellSize);
        manager.analyzeMap(1, 1, cf, cf, null);
        MapAnalyzer analyser = null;
        switch (visType) {
            case GRID:
                analyser = manager.getDistortionGrid();
                break;
            case VECTOR:
                analyser = manager.getErrorVectors();
                break;
            case SCALE_ROT:
                // return simple string
                writer.println(MapAnalystHeadless.getScaleRotInfo(manager));
                return;
        }
        
        // compute visualizations
        GeoSet visGeoSet = forOldMap ? analyser.getOldGeoSet() : analyser.getNewGeoSet();

        // scale geometry to convert from meter to pixels
        if (forOldMap && oldPointsInPixel) {
            visGeoSet.scale(1. / pixelSize);
        }

        // export to GeoJSON
        GeoJSONExporter exporter = new GeoJSONExporter(1);
        exporter.setPathFlatness(bezierTolerance);
        exporter.addProperty("title", analyser.getName());
        exporter.addProperty("author", "MapAnalystOnline");
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_NOW);
        String date = sdf.format(Calendar.getInstance().getTime());
        exporter.addProperty("date", date);
        exporter.export(visGeoSet, writer, true);

    }
}
