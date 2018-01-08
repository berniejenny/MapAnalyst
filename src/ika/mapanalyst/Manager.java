package ika.mapanalyst;

import au.monash.fit.mapanalyst.warp.ImageWarper;
import com.jhlabs.map.Ellipsoid;
import com.jhlabs.map.proj.Projection;
import com.jhlabs.map.proj.TCEAProjection;
import java.io.*;
import java.util.*;
import ika.transformation.*;
import ika.geo.*;
import ika.geo.osm.Projector;
import ika.geo.osm.OpenStreetMap;
import ika.utils.*;
import ika.gui.*;
import ika.geoimport.*;
import ika.mapanalyst.MapAnalyzer.MapAnalyzerException;
import ika.transformation.robustestimator.*;
import java.awt.Component;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Icon;
import javax.swing.JOptionPane;

public final class Manager implements Serializable {

    private static final long serialVersionUID = 7540690127618320729L;
    private Transformation transformation;
    private final MapAnalyzer mapAnalyzer[];
    private static final int DIST_GRID = 0;
    private static final int ERR_VECT = 1;
    private static final int GEOM_TRANS = 2;
    private static final int MEK_CIRCLE = 3;
    private static final int ISOLINES = 4;
    private static final int NBR_ERR_DISP = 5;
    private final LinkManager linkManager;
    private boolean showErrorInOldMap;
    private GeoSet mapOld;
    private GeoSet mapNew;
    private GeoSet oldImageGeoSet;
    private GeoSet newImageGeoSet;
    private PlaceList placeList;
    private final HuberEstimator huberEstimator;
    private final VEstimator vEstimator;
    private final HampelEstimator hampelEstimator;

    private transient Projection projection = new TCEAProjection(); // FIXME transient
    private transient boolean automaticCentralLongitude = true; // FIXME transient

    /**
     * Constructs a new manager.
     */
    public Manager() {
        this.transformation = new TransformationHelmert();

        mapAnalyzer = new MapAnalyzer[NBR_ERR_DISP];
        mapAnalyzer[DIST_GRID] = new DistortionGrid();
        mapAnalyzer[ERR_VECT] = new ErrorVectors();
        mapAnalyzer[GEOM_TRANS] = new GeometryTransformer();
        mapAnalyzer[MEK_CIRCLE] = new MekenkampCircles();
        mapAnalyzer[ISOLINES] = new Isolines();

        this.linkManager = new LinkManager();

        this.initGeoSets();

        this.showErrorInOldMap = true;

        huberEstimator = new HuberEstimator();
        vEstimator = new VEstimator();
        hampelEstimator = new HampelEstimator();

    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.projection = new TCEAProjection(); // FIXME
        this.automaticCentralLongitude = true; // FIXME
    }

    private void initGeoSets() {

        // the main GeoSets that hold all other map data.
        this.mapOld = new GeoSet();
        this.mapOld.setName("old map");
        this.mapNew = new GeoSet();
        this.mapNew.setName("new map");

        // image
        this.oldImageGeoSet = new GeoSet();
        this.oldImageGeoSet.setName("old image");
        this.newImageGeoSet = new GeoSet();
        this.newImageGeoSet.setName("new image");
        this.mapOld.addGeoObject(this.oldImageGeoSet);
        this.mapNew.addGeoObject(this.newImageGeoSet);

        // points
        this.mapOld.addGeoObject(this.linkManager.getOldPointsGeoSet());
        this.mapNew.addGeoObject(this.linkManager.getNewPointsGeoSet());

        // graphics produced by MapAnalyzer objects
        for (int i = 0; i < Manager.NBR_ERR_DISP; ++i) {
            this.mapOld.addGeoObject(this.mapAnalyzer[i].getOldGeoSet());
            this.mapNew.addGeoObject(this.mapAnalyzer[i].getNewGeoSet());
        }

        this.mapOld.addGeoObject(this.getGeometryTransformer().getOldSourceGeoSet());
        this.mapNew.addGeoObject(this.getGeometryTransformer().getNewSourceGeoSet());
    }

    public byte[] serializeManager() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        java.util.zip.GZIPOutputStream zip
                = new java.util.zip.GZIPOutputStream(baos);
        BufferedOutputStream bos = new BufferedOutputStream(zip);
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(this);
        }
        return baos.toByteArray();
    }

    public static Rectangle2D findBoundingBox(double[][] pts) {
        double minX = pts[0][0];
        double maxX = minX;
        double minY = pts[0][1];
        double maxY = minY;
        for (int i = 1; i < pts.length; i++) {

            if (pts[i][0] < minX) {
                minX = pts[i][0];
            } else if (pts[i][0] > maxX) {
                maxX = pts[i][0];
            }

            if (pts[i][1] < minY) {
                minY = pts[i][1];
            } else if (pts[i][1] > maxY) {
                maxY = pts[i][1];
            }
        }

        return new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
    }

    /**
     * Initializes the transformation between the two maps, and transforms the
     * points in the source map to the destination map.
     *
     * The transformation can be from the old map to the new map, or vice versa.
     *
     * @param oldPoints points in the old map
     * @param newPoints points in the new map; optionally in OSM
     * @param transformation this transformation will be initialized and used
     * for transforming from the source to the destination map
     * @param showErrorInOldMap if true, the new points are transformed to the
     * old map. Vice versa if false.
     * @return a two-dimensional array with the coordinates of the transformed
     * source points.
     */
    private static double[][] transformPointsToDestinationMap(
            double[][] oldPoints,
            double[][] newPoints,
            Transformation transformation,
            boolean showErrorInOldMap) {

        assert (oldPoints.length == newPoints.length);

        // determine the destination points
        double[][] dstPoints;
        if (showErrorInOldMap) {
            // new to old transformation
            transformation.init(oldPoints, newPoints);
            dstPoints = oldPoints;
        } else {
            // old to new transformation
            transformation.init(newPoints, oldPoints);
            dstPoints = newPoints;
        }

        // transform source points to the destination coordinate system
        double[][] transformedSourcePoints = new double[dstPoints.length][2];
        for (int i = 0; i < dstPoints.length; i++) {
            transformedSourcePoints[i][0] = dstPoints[i][0];
            transformedSourcePoints[i][1] = dstPoints[i][1];
        }
        transformation.addResidualsToPoints(transformedSourcePoints);
        return transformedSourcePoints;
    }

    /**
     * Computes the mean longitude in degrees of all linked control points in
     * the reference map.
     *
     * @return The mean longitude in degrees.
     */
    public Double meanLongitudeOfLinkedPointsInNewMap() {
        if (!isUsingOpenStreetMap()) {
            return null;
        }

        double[][][] linkedPoints = getLinkManager().getLinkedPointsCopy(null);
        double[][] newPoints = linkedPoints[1];

        // convert from OSM to spherical radians
        Projector.OSM2Geo(newPoints, newPoints);

        // compute mean longitude of all points: convert to Cartesian unit 
        // vectors, compute mean vector, convert to longitude
        double x = 0;
        double y = 0;
        for (int i = 0; i < newPoints.length; i++) {
            double lon = newPoints[i][0];
            x += Math.cos(lon);
            y += Math.sin(lon);
        }
        x /= newPoints.length;
        y /= newPoints.length;
        return Math.toDegrees(Math.atan2(y, x));
    }

    /**
     * Creates a new instance of Projector. Initializes the projector with a
     * central longitude.
     *
     * @return a new Projector, or null if OSM is not used.
     */
    public Projector createProjector() {
        if (!isUsingOpenStreetMap()) {
            return null;
        }

        // initialize the projection
        if (automaticCentralLongitude) {
            double lon0 = meanLongitudeOfLinkedPointsInNewMap();
            projection.setProjectionLongitudeDegrees(lon0);
        }
        projection.setEllipsoid(Ellipsoid.SPHERE);
        projection.initialize();

        Projector projector = new Projector();
        projector.setInitializedProjection(projection);
        return projector;
    }

    /**
     * Initializes VisualizationParameters used for computing distortion
     * visualizations and image warping.
     *
     * @param visInOldMap true if the error visualizations are computed in the
     * old map, false otherwise. This is identical to this.showErrorInOldMap for
     * visualizations, but is always true warping the old map image to the
     * reference map.
     * @param oldCoordinateFormatter format of coordinate labels added to
     * distortion grid of old map
     * @param newCoordinateFormatter format of coordinate labels added to
     * distortion grid of new map
     * @return a parameter object
     */
    private VisualizationParameters visParams(boolean visInOldMap,
            CoordinateFormatter oldCoordinateFormatter,
            CoordinateFormatter newCoordinateFormatter) {
        Projector projector = createProjector();
        double[][][] pts = linkManager.getLinkedPointsCopy(projector);
        double[][] oldPoints = pts[0];
        double[][] newPoints = pts[1];
        double[][] transformedSourcePoints = transformPointsToDestinationMap(
                oldPoints, newPoints,
                transformation,
                visInOldMap);
        double[][] dstPoints = visInOldMap ? oldPoints : newPoints;

        if (visInOldMap) {
            getDistortionGrid().setMeshSizeScale(1.);
        } else {
            getDistortionGrid().setMeshSizeScale(transformation.getScale());
        }

        // initialize the multiquadric interpolation
        MultiquadricInterpolation multiquadricInterpol;
        try {
            multiquadricInterpol = new MultiquadricInterpolation();
            double exaggeration = getDistortionGrid().getExaggeration();
            multiquadricInterpol.solveCoefficients(transformedSourcePoints, dstPoints,
                    exaggeration);
        } catch (Exception e) { // catch exception due to ill conditioned matrix.
            Logger.getLogger(Manager.class.getName()).log(Level.SEVERE, null, e);
            multiquadricInterpol = null;
            if (!GraphicsEnvironment.isHeadless()) {
                String msg = "A system of linear equations cannot be solved. "
                        + "Some visualizations can therefore not be generated.";
                String title = "Numerical Problem";
                Icon icon = ika.mapanalyst.ApplicationInfo.getApplicationIcon();
                JOptionPane.showMessageDialog(/*parentComponent*/null, msg, title,
                        javax.swing.JOptionPane.ERROR_MESSAGE, icon);
                // TODO improve exception handling
            }
        }

        double[][] newPointsHull = linkManager.getNewPointsHull();
        // convert convex hull around the points in the new reference map from
        // the OpenStreetMap (if used). This could actually turn the convex hull
        // into a concave hull due to the change of projection. The hull, however
        // is only used for clipping graphics, and this change therefore does not
        // matter much.
        if (isUsingOpenStreetMap()) {
            // getNewPointsHull returns a reference, so we need to create a copy,
            // as OSM2Intermediate will change coordinates
            double[][] newPointsHullIntermediate = new double[newPointsHull.length][2];
            projector.OSM2Intermediate(newPointsHull, newPointsHullIntermediate);
            newPointsHull = newPointsHullIntermediate;
        }

        // compute the graphics by calling each MapAnalyzer
        final VisualizationParameters params = new VisualizationParameters(
                transformation,
                oldPoints, newPoints,
                linkManager.getOldPointsHull(),
                newPointsHull,
                transformedSourcePoints,
                visInOldMap,
                multiquadricInterpol,
                oldCoordinateFormatter, newCoordinateFormatter,
                projector,
                isUsingOpenStreetMap());
        return params;
    }

    /**
     * Analyze the map and generate graphics visualizing the results.
     */
    public void analyzeMap(CoordinateFormatter oldCoordinateFormatter,
            CoordinateFormatter newCoordinateFormatter,
            Component parentComponent) throws MapAnalyzerException {

        clearGraphics();
        VisualizationParameters params = visParams(showErrorInOldMap, 
                oldCoordinateFormatter, newCoordinateFormatter);
        for (int i = 0; i < NBR_ERR_DISP; i++) {
            mapAnalyzer[i].setVisualizationParameters(params);
            mapAnalyzer[i].analyzeMap();
        }
    }

    public void warpMap() {
        VisualizationParameters params = visParams(true, null, null);
        
        ImageWarper imageWarper = new ImageWarper(
                params.getTransformation(),
                params.getMultiquadricInterpolation(),
                getOldMap(),
                params.getSrcPoints());
        GeoImage warpedGeoImage = imageWarper.warp();
        setNewMap(warpedGeoImage);
    }

    private String getTransformationReport(Transformation transformation) {
        double[][][] pts = linkManager.getLinkedPointsCopy(createProjector());
        transformPointsToDestinationMap(pts[0], pts[1],
                transformation,
                showErrorInOldMap);
        return transformation.getShortReport(showErrorInOldMap);
    }

    public String compareTransformations() {
        StringBuilder str = new StringBuilder(1024);

        String nl = System.getProperty("line.separator");

        // Helmert
        Transformation trans = new TransformationHelmert();
        str.append(trans.getName());
        str.append(nl);
        str.append(this.getTransformationReport(trans));
        str.append(nl);

        // Afffine 5
        trans = new TransformationAffine5();
        str.append(trans.getName());
        str.append(nl);
        str.append(this.getTransformationReport(trans));
        str.append(nl);

        // Affine 6
        trans = new TransformationAffine6();
        str.append(trans.getName());
        str.append(nl);
        str.append(this.getTransformationReport(trans));
        str.append(nl);

        /*
        // Robust Helmert with VEstimator
        transformation = new TransformationRobustHelmert(new VEstimator());
        str.append(transformation.getName());
        str.append(nl);
        str.append(this.getTransformationReport(transformation));
        str.append(nl);
        
        // Robust Helmert with HuberEstimator
        transformation = new TransformationRobustHelmert(new HuberEstimator());
        str.append(transformation.getName());
        str.append(nl);
        str.append(this.getTransformationReport(transformation));
        str.append(nl);
        
        // Robust Helmert with HampelEstimator
        transformation = new TransformationRobustHelmert(new HampelEstimator());
        str.append(transformation.getName());
        str.append(nl);
        str.append(this.getTransformationReport(transformation));
        str.append(nl);
         */
        return str.toString();
    }

    /**
     * Deletes all graphics generated by the MapAnalyzers
     */
    public void clearGraphics() {
        for (int i = 0; i < NBR_ERR_DISP; i++) {
            this.mapAnalyzer[i].clearAll();
        }
    }

    public void clearMaps() {
        // clear graphics
        this.clearGraphics();

        // clear images
        this.oldImageGeoSet.removeAllGeoObjects();
        this.newImageGeoSet.removeAllGeoObjects();

        // clear points
        this.linkManager.getOldPointsGeoSet().removeAllGeoObjects();
        this.linkManager.getNewPointsGeoSet().removeAllGeoObjects();
    }

    public void setTransformation(Transformation transformation) {
        this.transformation = transformation;
    }

    public Transformation getTransformation() {
        return this.transformation;
    }

    public String getTransformationDescription() {
        return this.getDistortionGrid().toString();
    }

    public GeoImage getOldMap() {
        return (GeoImage) this.oldImageGeoSet.getFirstGeoObject(GeoImage.class, false, false);
    }

    public GeoImage getNewMap() {
        return (GeoImage) this.newImageGeoSet.getFirstGeoObject(GeoImage.class, false, false);
    }

    public GeoSet getOldGeoSet() {
        return mapOld;
    }

    public GeoSet getNewGeoSet() {
        return mapNew;
    }

    public void selectPoints() {
        this.linkManager.getOldPointsGeoSet().setSelected(true);
        this.linkManager.getNewPointsGeoSet().setSelected(true);
    }

    public void deselectPoints() {
        this.linkManager.getOldPointsGeoSet().setSelected(false);
        this.linkManager.getNewPointsGeoSet().setSelected(false);
    }

    public static void importRasterImage(GeoSet destGeoSet, String filePath,
            java.awt.Frame parentFrame, MapComponent mapComponent,
            boolean askUserToGeoreferenceImage) {

        if (filePath == null) {
            throw new IllegalArgumentException();
        }
        ImageReaderProgressDialog progressDialog
                = new ImageReaderProgressDialog(parentFrame, true);

        ImageImporter imageImporter = new ImageImporter();
        imageImporter.setMapComponent(mapComponent);
        imageImporter.setAskUserToGeoreferenceImage(askUserToGeoreferenceImage);
        imageImporter.importGeoImageWithImageIOAsync(filePath,
                destGeoSet, progressDialog);
    }

    public void importNewRasterImage(String filePath, java.awt.Frame parentFrame,
            MapComponent mapComponent) {
        newImageGeoSet.removeAllGeoObjects();
        Manager.importRasterImage(this.newImageGeoSet, filePath, parentFrame,
                mapComponent, true);
    }

    public void importOldRasterImage(String filePath, java.awt.Frame parentFrame,
            MapComponent mapComponent) {
        oldImageGeoSet.removeAllGeoObjects();
        Manager.importRasterImage(oldImageGeoSet, filePath, parentFrame,
                mapComponent, false);
    }

    public boolean isShowInOldMap() {
        return this.showErrorInOldMap;
    }

    public void setShowInOldMap(boolean showInOldMap) {
        this.showErrorInOldMap = showInOldMap;
    }

    public void centerOnLink(String linkName) {
    }

    /**
     * Reads an ASCII file with linked points (for the old and the new map).
     * Values in this file have to be separated by commas (place names can
     * contain other ';', spaces, etc.) There is one link per line, i.e. the ID,
     * x old, y old, x new, y new. Two GeoPoints are created, one for the old
     * coordinates, one for the new coordinates. The two points are linked and
     * the Link is added to the linksList in the LinkManager.
     *
     * @param filePath path to the file that will be read.
     */
    public void importLinksFromFile(String filePath, Component parentComponent) throws Exception {
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(filePath));
            importLinks(in, parentComponent);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {
                }
            }
        }
    }

    /**
     * Reads a String with linked points (for the old and the new map). Values
     * in this file have to be separated by commas (place names can contain
     * other ';', spaces, etc.) There is one link per line, i.e. the ID, x old,
     * y old, x new, y new. Two GeoPoints are created, one for the old
     * coordinates, one for the new coordinates. The two points are linked and
     * the Link is added to the linksList in the LinkManager.
     *
     * @param links The string that will be parsed.
     */
    public void importLinksFromString(String links, Component parentComponent) throws Exception {
        BufferedReader in = null;
        try {
            in = new BufferedReader(new StringReader(links));
            importLinks(in, parentComponent);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {
                }
            }
        }
    }

    /**
     * Reads linked points from a BufferedStream (for the old and the new map).
     * Values in this file have to be seperated by commas (place names can
     * contain other ';', spaces, etc.) There is one link per line, i.e. the ID,
     * x old, y old, x new, y new. Two GeoPoints are created, one for the old
     * coordinates, one for the new coordinates. The two points are linked and
     * the Link is added to the linksList in the LinkManager.
     *
     * @param in The stream to read from. in will not be closed.
     */
    private void importLinks(BufferedReader in, Component parentComponent) throws IOException {
        GeoSet oldPointsGeoSet = null;
        GeoSet newPointsGeoSet = null;

        try {
            oldPointsGeoSet = this.linkManager.getOldPointsGeoSet();
            newPointsGeoSet = this.linkManager.getNewPointsGeoSet();

            oldPointsGeoSet.suspendGeoSetChangeListeners();
            newPointsGeoSet.suspendGeoSetChangeListeners();

            final boolean updateConvexHull = false;

            String str;
            while ((str = in.readLine()) != null) {
                str = str.trim();
                StringTokenizer tokenizer = new StringTokenizer(str, ",\n\r");
                while (tokenizer.hasMoreTokens()) {
                    String name = tokenizer.nextToken().trim();
                    double xOld = Double.parseDouble(tokenizer.nextToken());
                    double yOld = Double.parseDouble(tokenizer.nextToken());
                    double xNew = Double.parseDouble(tokenizer.nextToken());
                    double yNew = Double.parseDouble(tokenizer.nextToken());

                    GeoPoint oldPt = new GeoPoint(xOld, yOld);
                    GeoPoint newPt = new GeoPoint(xNew, yNew);
                    oldPointsGeoSet.addGeoObject(oldPt);
                    newPointsGeoSet.addGeoObject(newPt);

                    try {
                        // addLink may throw an exception when a point with same
                        // coordinates already exists.
                        this.linkManager.addLink(oldPt, newPt, name, updateConvexHull);
                    } catch (Exception exc) {
                        Logger.getLogger(Manager.class.getName()).log(Level.SEVERE, null, exc);
                        if (GraphicsEnvironment.isHeadless()) {
                            break;
                        }
                        String title = "Import Problem";
                        String msg = exc.getMessage();
                        msg += "\nDo you want to continue importing the other points?";
                        javax.swing.Icon icon = ika.mapanalyst.ApplicationInfo.getApplicationIcon();
                        int res = javax.swing.JOptionPane.showConfirmDialog(
                                parentComponent,
                                msg, title,
                                javax.swing.JOptionPane.YES_NO_OPTION,
                                javax.swing.JOptionPane.ERROR_MESSAGE, icon);
                        if (res == 1) {
                            return;
                        }

                    }
                }
            }

        } finally {
            this.linkManager.updateConvexHull();
            if (oldPointsGeoSet != null) {
                oldPointsGeoSet.activateGeoSetChangeListeners(this);
            }
            if (newPointsGeoSet != null) {
                newPointsGeoSet.activateGeoSetChangeListeners(this);
            }
        }
    }

    /**
     * Reads an ASCII file with points. Values in this file have to be seperated
     * by comma. There is one point per line, i.e. the ID, x, y.
     *
     * @param filePath path to the file that will be read.
     * @param dstGeoSet The GeoSet to receive the new GeoPoints.
     */
    public void importPointsFromFile(String filePath,
            GeoSet dstGeoSet,
            double scale) throws Exception {
        BufferedReader reader = null;
        dstGeoSet.suspendGeoSetChangeListeners();
        try {
            reader = new BufferedReader(new FileReader(filePath));
            String str;
            while ((str = reader.readLine()) != null) {
                StringTokenizer tokenizer = new StringTokenizer(str, ",");
                String name = (String) tokenizer.nextToken().trim();
                double x = Double.parseDouble((String) tokenizer.nextToken());
                double y = Double.parseDouble((String) tokenizer.nextToken());

                if (scale == -1d) {
                    double[][] in = new double[][]{{x, y}};
                    double[][] out = new double[1][2];
                    Projector.geo2OSM(in, out);
                    x = out[0][0];
                    y = out[0][1];
                } else {
                    x *= scale;
                    y *= scale;
                }

                GeoPoint pt = new GeoPoint(x, y);
                pt.setName(name);
                dstGeoSet.addGeoObject(pt);
            }
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                }
            }
            dstGeoSet.activateGeoSetChangeListeners(this);
        }
    }

    public void addDrawing(GeoObject geoObject, boolean forOldMap) {
        this.getGeometryTransformer().addDrawing(geoObject, forOldMap);
    }

    public void removeDrawing(boolean removeFromOldMap) {
        this.getGeometryTransformer().removeDrawing(removeFromOldMap);
    }

    public DistortionGrid getDistortionGrid() {
        return (DistortionGrid) this.mapAnalyzer[DIST_GRID];
    }

    public ErrorVectors getErrorVectors() {
        return (ErrorVectors) this.mapAnalyzer[ERR_VECT];
    }

    public MekenkampCircles getMekenkampCircles() {
        return (MekenkampCircles) this.mapAnalyzer[MEK_CIRCLE];
    }

    public GeometryTransformer getGeometryTransformer() {
        return (GeometryTransformer) this.mapAnalyzer[GEOM_TRANS];
    }

    public Isolines getIsolines() {
        return (Isolines) this.mapAnalyzer[ISOLINES];
    }

    public String getTransformationReport() {
        if (this.transformation != null) {
            String nl = System.getProperty("line.separator");
            String report = transformation.getBreakLineForReport();
            report += "Description of Transformation:" + nl + nl;
            report += transformation.getReport(true);
            report += transformation.getBreakLineForReport();
            report += "Residuals (id, vx [m], vy [m], v [m], * if v > 3 sigma0)" + nl + nl;
            double threshold = 3 * transformation.getSigma0();
            report += transformation.getResidualsReport(threshold);
            return report;
        }
        return null;
    }

    public boolean isTransformationInitialized() {
        if (this.transformation != null) {
            return this.transformation.isInitialized();
        }
        return false;
    }

    public void export(ika.geoexport.GeoSetExporter exporter, String filePath,
            boolean oldMap) throws Exception {

        exporter.exportGeoSet(oldMap ? mapOld : mapNew, filePath);
    }

    public void exportLinkedPointsAndVectorsToASCII(String filePath,
            boolean toExcel) throws Exception {
        if (filePath == null) {
            return;        // initialize the transformation
        }
        double[][][] linkedPoints = linkManager.getLinkedPointsCopy(createProjector());
        double[][] oldPoints = linkedPoints[0];
        double[][] newPoints = linkedPoints[1];

        transformation.init(newPoints, oldPoints);

        String sep = toExcel ? "" : ",";
        boolean header = !toExcel;
        String report = getLinkManager().getReport(sep, header, null,
                transformation);
        if (report == null) {
            return;
        }

        filePath = FileUtils.forceFileNameExtension(filePath, toExcel ? "xls" : "txt");
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(
                new FileWriter(filePath)))) {
            writer.print(report);
        }
    }

    /**
     * Export the paired control points to a text file.
     *
     * @param filePath
     * @param oldMap If not null, coordinates of the old map are exported in
     * pixels relative to the top-left corner of the georeferenced image, and
     * not in meters.
     * @param toExcel if true, file extension is xls, otherwise txt.
     * @throws java.lang.Exception
     */
    public void exportLinkedPointsToASCII(String filePath,
            GeoImage oldMap, boolean toExcel) throws Exception {

        PrintWriter writer = null;
        try {
            if (filePath == null) {
                return;
            }
            String separator = toExcel ? "" : ",";
            String report = getLinkManager().getReport(separator, false, oldMap, null);
            if (report == null) {
                return;
            }
            String ext = toExcel ? "xls" : "txt";
            filePath = FileUtils.forceFileNameExtension(filePath, ext);
            writer = new PrintWriter(new BufferedWriter(
                    new FileWriter(filePath)));
            writer.print(report);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }

    }

    /**
     * Writes the point in the old or the new map to a text file.
     *
     * @param filePath The path to the file to write to.
     * @param oldMap True if the points of the old map are to be exported, false
     * otherwise.
     * @param scale Coordinates are scaled by this factor. If -1, coordinates
     * are converted to geographic longitude / latitude values.
     * @param toExcel Generate file with "xls" file extension.
     * @throws Exception
     */
    public void exportPointsToASCII(String filePath,
            boolean oldMap,
            double scale,
            boolean toExcel) throws Exception {

        final int NUMBER_LENGTH = 20;
        final int NBR_DECIMALS = 6;

        String VALUE_SEPARATOR = toExcel ? "" : ",";
        String END_LINE = System.getProperty("line.separator");

        if (filePath == null) {
            return;
        }

        GeoSet ptsGeoSet;
        if (oldMap) {
            ptsGeoSet = this.getLinkManager().getOldPointsGeoSet();
        } else {
            ptsGeoSet = this.getLinkManager().getNewPointsGeoSet();
        }
        int nbrPts = ptsGeoSet.getNumberOfChildren();
        if (nbrPts < 1) {
            return;
        }

        // find longest name
        int maxNameLength = 10; // 10 is minimum
        for (int i = 0; i < ptsGeoSet.getNumberOfChildren(); i++) {
            GeoObject obj = ptsGeoSet.getGeoObject(i);
            if (obj == null || obj.getName() == null) {
                continue;
            }
            final int nameLength = obj.getName().length();
            if (nameLength > maxNameLength) {
                maxNameLength = nameLength;
            }
        }

        if (toExcel) {
            filePath = FileUtils.forceFileNameExtension(filePath, "xls");
        } else {
            filePath = FileUtils.forceFileNameExtension(filePath, "txt");
        }
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(
                new FileWriter(filePath)))) {
            for (int i = 0; i < nbrPts; i++) {
                GeoPoint geoPoint = (GeoPoint) (ptsGeoSet.getGeoObject(i));

                // print name or id of point
                String name = geoPoint.getName();
                if (name == null) {
                    name = Integer.toString(i);
                } else {
                    name = name.trim();
                    if (name.length() == 0) {
                        name = Integer.toString(i);
                    }
                }
                writer.print(name);
                for (int j = name.length(); j < maxNameLength; j++) {
                    writer.print(" ");
                }

                writer.print(VALUE_SEPARATOR);

                double x = geoPoint.getX();
                double y = geoPoint.getY();
                if (scale == -1d) {
                    double[][] in = new double[][]{{x, y}};
                    double[][] out = new double[1][2];
                    Projector.OSM2Geo(in, out);
                    x = Math.toDegrees(out[0][0]);
                    y = Math.toDegrees(out[0][1]);
                } else {
                    x *= scale;
                    y *= scale;
                }

                // print coordinates of point
                // Don't just use a standard DecimalFormat since localized characters for
                // the decimal separator could be used. We require '.'.
                writer.print(NumberFormatter.format(x, NUMBER_LENGTH, NBR_DECIMALS));
                writer.print(VALUE_SEPARATOR);
                writer.print(NumberFormatter.format(y, NUMBER_LENGTH, NBR_DECIMALS));
                writer.print(END_LINE);
            }
        }
    }

    public boolean placePointFromList(Frame parent) {
        if (this.placeList == null || !placeList.isInitialized()) {
            this.placeList = new PlaceList();
            placeList.askUserForFile(parent);
        }

        if (!placeList.isInitialized()) {
            return false; // user canceled
        }
        Object[] res = placeList.askUserForPlace();
        if (res == null) {
            return false; // user canceled
        }
        PlaceList.Point p = (PlaceList.Point) res[1];
        if (p == null) {
            return false;
        }
        final String newPointName = (String) res[0];

        GeoPoint pointWithSameName = this.linkManager.searchPoint(newPointName, false);
        if (pointWithSameName != null) {
            if (!GraphicsEnvironment.isHeadless()) {
                String msg = "A point with the name \"" + newPointName
                        + "\" already exists.\n"
                        + "Do you really want to add another point with the the same name?";
                String title = "Point With Identical Name";
                Object[] options = {"Add Point", "Cancel"};
                javax.swing.Icon icon = ika.mapanalyst.ApplicationInfo.getApplicationIcon();
                final int option = javax.swing.JOptionPane.showOptionDialog(
                        parent, msg, title,
                        javax.swing.JOptionPane.DEFAULT_OPTION,
                        javax.swing.JOptionPane.WARNING_MESSAGE,
                        icon, options, options[1]);
                if (option == 1 || option == javax.swing.JOptionPane.CLOSED_OPTION) {
                    return false;   // user canceled
                }
            }
        }
        GeoPoint newPt = new GeoPoint(p.x, p.y);
        newPt.setName(newPointName);
        newPt.setSelected(true);
        GeoSet newPointsGeoSet = this.getNewPointsGeoSet();
        // deselect all pre-existing points in new map
        newPointsGeoSet.setSelected(false);
        newPointsGeoSet.addGeoObject(newPt);
        return true;
    }

    public void setImagesVisible(boolean showOld, boolean showNew) {
        this.oldImageGeoSet.setVisible(showOld);
        this.newImageGeoSet.setVisible(showNew);
    }

    public void setPointsVisible(boolean visible) {
        this.linkManager.getNewPointsGeoSet().setVisible(visible);
        this.linkManager.getOldPointsGeoSet().setVisible(visible);
    }

    public GeoSet getOldPointsGeoSet() {
        return this.linkManager.getOldPointsGeoSet();
    }

    public GeoSet getNewPointsGeoSet() {
        return this.linkManager.getNewPointsGeoSet();
    }

    public LinkManager getLinkManager() {
        return this.linkManager;
    }

    public void removeOldImage() {
        oldImageGeoSet.removeAllGeoObjects();
    }

    public void removeNewImage() {
        newImageGeoSet.removeAllGeoObjects();
    }

    public void setNewMap(GeoObject item) {
        this.newImageGeoSet.removeAllGeoObjects();
        this.newImageGeoSet.addGeoObject(item);
    }

    public void initOSM(MapComponent map) {
        OpenStreetMap osm = getOpenStreetMap();
        if (osm != null) {
            osm.setMapComponent(map);
        }
    }

    public void disposeOSM() {
        OpenStreetMap osm = getOpenStreetMap();
        if (osm != null) {
            osm.dispose();
        }
    }

    public boolean isUsingOpenStreetMap() {
        return getOpenStreetMap() != null;
    }

    /**
     * Returns the OpenStreetMap of the new map, or null if the new map does not
     * contain an OpenStreetMap.
     *
     * @return the OpenStreetMap or null
     */
    public OpenStreetMap getOpenStreetMap() {
        return (OpenStreetMap) newImageGeoSet.getFirstGeoObject(OpenStreetMap.class, false, false);
    }

    /**
     * Returns true if the passed point is over the OpenStreetMap. Returns false
     * if the point is not on the OpenStreetMap or if the OpenStreetMap is not
     * being used.
     *
     * @param point point in world coordinates
     * @return true if over OSM.
     */
    public boolean isPointOnOpenStreetMap(Point2D point) {
        OpenStreetMap osm = (OpenStreetMap) newImageGeoSet.getFirstGeoObject(OpenStreetMap.class, false, false);
        return osm == null ? false : osm.isPointOnSymbol(point, 0, 0);
    }

    public void undo() {
        System.out.println("undo");
    }

    public void redo() {
        System.out.println("redo");
    }

    public HuberEstimator getHuberEstimator() {
        return huberEstimator;
    }

    public VEstimator getVEstimator() {
        return vEstimator;
    }

    public HampelEstimator getHampelEstimator() {
        return hampelEstimator;
    }

    public void setInitializedProjection(Projection p) {
        assert (p != null);
        this.projection = p;
    }

    public Projection getProjection() {
        return this.projection;
    }

    /**
     * @return the automaticCentralLongitude
     */
    public boolean isAutomaticCentralLongitude() {
        return automaticCentralLongitude;
    }

    /**
     * @param automaticCentralLongitude the automaticCentralLongitude to set
     */
    public void setAutomaticCentralLongitude(boolean automaticCentralLongitude) {
        this.automaticCentralLongitude = automaticCentralLongitude;
    }

}
