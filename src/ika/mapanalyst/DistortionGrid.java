package ika.mapanalyst;

import ika.geo.GeoPath;
import ika.geo.GeoSet;
import ika.geo.GeoText;
import ika.geo.VectorSymbol;
import ika.geo.osm.OpenStreetMap;
import ika.utils.CoordinateFormatter;
import ika.utils.Median;
import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.text.DecimalFormat;

/**
 * DistortionGrid.java
 *
 * @author Bernhard Jenny and Adrian Weber, Institute of Cartography ETH Zurich
 */
public class DistortionGrid extends MapAnalyzer implements Serializable {

    private static final long serialVersionUID = -5990761709834452077L;

    /**
     * size of a grid mesh in coordinates of the reference map
     */
    private double meshSize;
    /**
     * meshSizeScale is 1, if the grid is transformed from the new reference map
     * to the old map, i.e. the distorted grid is displayed in the old map.
     * meshSizeScale is > 1 if the grid is transformed from the old map to the
     * new reference map, i.e. the distorted grid is displayed in the new
     * reference map.
     */
    private double meshSizeScale;

    public enum Unit {

        METERS, DEGREES
    }
    private Unit meshUnit;

    // factor for Bezier curve interpolation
    private double smoothness;

    // alpha value for rendering uncertain grid areas
    private float uncertaintyAlpha;

    // if a grid is not further away from the closest control point than the
    // uncertaintyQuantile of all closest distances between control points, then 
    // it is considered uncertain.
    private double uncertaintyQuantile;

    /**
     * 0: no clipping 1: clip with convex hull 2: clip with custom polygon
     */
    private int clipWithHull;
    private double[][] oldClipPolygon;
    private double[][] newClipPolygon;
    // the minium and maximum number of lines that the grid can contain in
    // one direction.
    private final static int MIN_NODES = 4;
    private final static int MAX_NODES = 1000;
    private final static int DEF_NODES = 15;
    /**
     * text size of coordinate labels
     */
    private int labelSize;
    /**
     * 1: draw each label; 2: every second; etc.
     */
    private int labelSequence;
    /**
     * color and stroke width of distortion grid
     */
    private final VectorSymbol vectorSymbol;
    /**
     * exaggeration factor for the grid. Added in MapAnalyst 1.3
     */
    private double exaggeration;
    /**
     * show a rotated and scaled, but not distorted grid. Added in MapAnalyst
     * 1.3
     */
    private boolean showUndistorted;
    /**
     * horizontal offset of grid. Added in MapAnalyst 1.3.12
     */
    private double offsetX;
    /**
     * vertical offset of grid. Added in MapAnalyst 1.3.12
     */
    private double offsetY;

    public DistortionGrid() {
        this.meshSize = 5000;
        this.meshUnit = Unit.METERS;
        this.meshSizeScale = 1.;
        this.smoothness = 1.;
        this.uncertaintyAlpha = 0.25f;
        this.uncertaintyQuantile = 0.75;
        this.clipWithHull = 0;
        this.oldClipPolygon = null;
        this.newClipPolygon = null;
        this.labelSize = 12;
        this.labelSequence = 4;
        this.vectorSymbol = new VectorSymbol();
        this.vectorSymbol.setStrokeWidth(1.f);
        this.vectorSymbol.setFilled(false);
        this.vectorSymbol.setScaleInvariant(true);
        this.exaggeration = 1;
        this.showUndistorted = false;
        this.offsetX = 0;
        this.offsetY = 0;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        // alpha for uncertainty and uncertaintyQuantile were added with 1.3.32
        // minimum value for uncertaintyAlpha is 0.05, so if 0, an older file is opened
        if (uncertaintyAlpha == 0f) {
            uncertaintyAlpha = 0.25f;
        }
        if (uncertaintyQuantile == 0) {
            uncertaintyQuantile = 0.75;
        }

        // exaggeration was added with 1.3.?
        if (exaggeration == 0) {
            exaggeration = 1;
        }

        // meshUnit was added with 1.3.8
        if (meshUnit == null) {
            meshUnit = Unit.METERS;
        }
    }

    @Override
    public String getName() {
        return "Distortion Grid";
    }

    private final class Grid {

        public double[][] grid;
        public double[] horizontalLabels;
        public double[] verticalLabels;
        public int numberNodesX;
        public int numberNodesY;
        public double west;
        public double south;
        public double meshSize;

        public Grid(Rectangle2D ptsExtension, double meshSize) throws MapAnalyzerException {

            this.meshSize = meshSize;

            computeGridGeometry(ptsExtension);
            if (!isNumberOfGridLinesCorrect()) {
                String msg = getErrorMessageForIncorrectNumberOfGridLines(this,
                        params.getSrcPointsExtension());
                throw new MapAnalyzerException(msg);
            }

            // Create the grid. The points are stored in an array with two columns (x and y).
            this.grid = new double[numberNodesX * numberNodesY][2];
            this.horizontalLabels = new double[numberNodesY];
            this.verticalLabels = new double[numberNodesX];
            int pointID = 0;
            final double scaledMeshSize = getScaledMeshSize();
            for (int i = 0; i < this.numberNodesX; i++) {
                verticalLabels[i] = i * scaledMeshSize;
                for (int j = 0; j < this.numberNodesY; j++) {
                    this.grid[pointID][0] = west + i * meshSize;
                    this.grid[pointID][1] = south + j * meshSize;
                    pointID++;
                }
            }
            for (int j = 0; j < this.numberNodesY; j++) {
                horizontalLabels[j] = j * scaledMeshSize;
            }
        }

        double[] getPosition(int col, int row) {
            int pos = col * this.numberNodesY + row;
            return grid[pos];
        }

        /**
         * Returns whether a grid is larger than a minimum size and smaller than
         * a maximum size.
         */
        public boolean isNumberOfGridLinesCorrect() {
            return (numberNodesX >= MIN_NODES && numberNodesY >= MIN_NODES
                    && numberNodesX <= MAX_NODES && numberNodesY <= MAX_NODES);
        }

        /**
         * computes a cell size for the distortion grid that would result in a
         * useful number of lines.
         *
         * @param ext
         * @return
         */
        public double getSuggestedCellSize(Rectangle2D ext) {

            if (meshUnit == Unit.DEGREES) {
                double[][] coords = new double[4][2];
                coords[0][0] = ext.getMinX();
                coords[0][1] = ext.getMinY();
                coords[1][0] = ext.getMaxX();
                coords[1][1] = ext.getMinY();
                coords[2][0] = ext.getMaxX();
                coords[2][1] = ext.getMaxY();
                coords[3][0] = ext.getMinX();
                coords[3][1] = ext.getMaxY();
                coords = params.getProjector().intermediate2geo(coords);
                double minX = coords[0][0];
                double maxX = coords[0][0];
                double minY = coords[0][1];
                double maxY = coords[0][1];
                for (int i = 1; i < 4; i++) {
                    if (coords[i][0] < minX) {
                        minX = coords[i][0];
                    }
                    if (coords[i][0] > maxX) {
                        maxX = coords[i][0];
                    }
                    if (coords[i][1] < minY) {
                        minY = coords[i][1];
                    }
                    if (coords[i][1] > maxY) {
                        maxY = coords[i][1];
                    }
                }
                ext.setRect(minX, minY, maxX - minX, maxY - minY);
            }

            double cellSize = Math.min(ext.getWidth(), ext.getHeight()) / DEF_NODES;
            if (cellSize <= 0d) {
                return -1;
            }

            if (params.isOSM() && !params.isAnalyzeOldMap() && meshUnit == Unit.METERS) {
                // grid in OSM map and mesh dimension specified in meters.
                // convert from degrees to meters
                cellSize = cellSize / 180 * Math.PI * OpenStreetMap.R;
            }

            // scale the cell size such that it is > 1
            double scale = 1; // scale factor
            double cellSize_s = cellSize;
            while (cellSize_s < 1) {
                cellSize_s *= 10;
                scale /= 10;
            }

            // compute the number of digits of the integral part of the scaled value
            double ndigits = (int) Math.floor(Math.log10(cellSize_s));

            // find the index into bases for the first limit after cellSize
            final double[] bases = new double[]{7.5, 5, 2.5, 1.5, 1};
            int baseID = 0;
            for (int i = 0; i < bases.length; ++i) {
                if (cellSize_s >= bases[i]) {
                    baseID = i;
                    break;
                }
            }

            return bases[baseID] * Math.pow(10, ndigits) * scale;
        }

        /**
         * Computes the numbers of lines and the position of the grid.
         *
         * @param ext A bounding box around the source points.
         */
        private void computeGridGeometry(Rectangle2D extension) {

            // numbers of cells left of 0
            // use floor and not rounding for negative values
            int cellsLeft = (int) Math.floor((extension.getMinX()) / meshSize);
            // number of cells right of 0
            int cellsRight = (int) Math.ceil((extension.getMaxX()) / meshSize);
            // number of nodes is 1 larger than number of cells
            this.numberNodesX = cellsRight - cellsLeft + 1;
            // align west border of grid with mesh size
            // apply offset of grid
            this.west = cellsLeft * meshSize + getScaledOffsetX();

            int cellsBottom = (int) Math.floor((extension.getMinY()) / meshSize);
            int cellsTop = (int) Math.ceil((extension.getMaxY()) / meshSize);
            this.numberNodesY = cellsTop - cellsBottom + 1;
            this.south = cellsBottom * meshSize + getScaledOffsetY();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Grid: ");
            sb.append(numberNodesX);
            sb.append(" x ");
            sb.append(numberNodesY);
            sb.append(" west: ");
            sb.append(west);
            sb.append(" south: ");
            sb.append(south);
            sb.append(" mesh size: ");
            sb.append(meshSize);
            return sb.toString();
        }
    }

    @Override
    public void analyzeMap() throws MapAnalyzerException {

        final MultiquadricInterpolation multiQuad = params.getMultiquadricInterpolation();
        if (multiQuad == null) {
            throw new MapAnalyzerException("Undefined Interpolation", this);
        }

        // determine the destination and source GeoSet
        GeoSet destGeoSet;
        GeoSet sourceGeoSet;
        if (params.isAnalyzeOldMap()) {
            destGeoSet = this.oldGeoSet;
            sourceGeoSet = this.newGeoSet;
        } else {
            destGeoSet = this.newGeoSet;
            sourceGeoSet = this.oldGeoSet;
        }

        // make sure a longitude/latitude graticule is only generated if
        // OSM is used and if the old map is analyzed
        if (meshUnit == Unit.DEGREES) {
            if (!params.isAnalyzeOldMap() || !params.isOSM()) {
                String msg = "Graticules of longitude / latitude lines can only\n"
                        + "be generated when OpenStreetMap is used, and when\n"
                        + "the old map is analyzed.\n";
                throw new MapAnalyzerException(msg, this);
            }
        }

        // compute the numbers of lines and the extension of the grid.
        // make sure we have a reasonable number of lines in the grid.
        final Grid grid;
        if (isGeographicalGrid(params)) {
            // create a lon/lat grid in the old map
            // the source points are in OSM, convert them to spherical coordinates in degrees
            double[][] geographicSrcPts = params.getProjector().intermediate2geo(params.getSrcPoints());

            // generate graticule (grid of longitude / latitude lines)
            Rectangle2D box = Manager.findBoundingBox(geographicSrcPts);

            if (box.getMinY() < OpenStreetMap.MIN_LAT) {
                double x = box.getMinX();
                double y = OpenStreetMap.MIN_LAT;
                double w = box.getWidth();
                double h = box.getMaxY() - y;
                box.setRect(x, y, w, h);
            }
            if (box.getMaxY() > OpenStreetMap.MAX_LAT) {
                double x = box.getMinX();
                double y = box.getMinY();
                double w = box.getWidth();
                double h = OpenStreetMap.MAX_LAT - y;
                box.setRect(x, y, w, h);
            }
            grid = new Grid(box, meshSize);

            for (int row = 0; row < grid.numberNodesY; row++) {
                double[] gridPos = grid.getPosition(0, row);
                if (gridPos[1] > OpenStreetMap.MAX_LAT) {
                    for (int col = 0; col < grid.numberNodesX; col++) {
                        grid.getPosition(col, row)[1] = OpenStreetMap.MAX_LAT;
                    }
                    grid.horizontalLabels[row] = OpenStreetMap.MAX_LAT - grid.south;
                }

                if (gridPos[1] < OpenStreetMap.MIN_LAT) {
                    for (int col = 0; col < grid.numberNodesX; col++) {
                        grid.getPosition(col, row)[1] = OpenStreetMap.MIN_LAT;
                    }
                    grid.horizontalLabels[row] = OpenStreetMap.MIN_LAT - grid.south;
                }

            }

            if (!grid.isNumberOfGridLinesCorrect()) {
                String msg = getErrorMessageForIncorrectNumberOfGridLines(grid, box);
                throw new MapAnalyzerException(msg, this);
            }

            // convert the grid back to the local projection
            params.getProjector().geo2Intermediate(grid.grid, grid.grid);
        } else {
            // grid in meters
            final double scaledMeshSize = getScaledMeshSize();
            grid = new Grid(params.getSrcPointsExtension(), scaledMeshSize);
        }

        // get the convex hulls around the two point sets
        double[][] srcHull;
        double[][] dstHull;
        switch (clipWithHull) {
            case 1: //1: clip with convex hull
                dstHull = params.getDstPointsHull();
                srcHull = params.getSrcPointsHull();
                break;
            case 2: // 2: clip with custom polygon
                if (params.isAnalyzeOldMap()) {
                    srcHull = null;
                    dstHull = oldClipPolygon;
                } else {
                    srcHull = null;
                    dstHull = newClipPolygon;
                }
                break;
            default: // 0: no clipping
                srcHull = null;
                dstHull = null;
        }

        // reference distance for uncertainty visualisation
        // use upper quartile of distances to closest neighbors of points in the destination map
        double uncertaintyRefDistance = quantileDistanceToClosestPoint(params.getDstPoints(), uncertaintyQuantile);

        // the uncertainty reference distance is at least as long as the size of a grid mesh
        double s = getScaledMeshSize();
        if (params.isAnalyzeOldMap()) {
            s /= params.getTransformationScale();
        }
        uncertaintyRefDistance = Math.max(uncertaintyRefDistance, s);

        // Create GeoPaths from the undistorted grid
        // add the undistorted grid to the source map
        createVerticalLinesFromGrid(
                grid,
                sourceGeoSet,
                srcHull,
                !params.isAnalyzeOldMap(),
                false,
                uncertaintyRefDistance);
        createHorizontalLinesFromGrid(
                grid,
                sourceGeoSet,
                srcHull,
                !params.isAnalyzeOldMap(),
                false,
                uncertaintyRefDistance);

        // apply an affine transformation to the grid
        params.getTransformation().transform(grid.grid);

        // Create GeoPaths from the undistorted transformed grid and add them 
        // to the destination map if required.
        if (showUndistorted) {
            createVerticalLinesFromGrid(
                    grid,
                    destGeoSet,
                    dstHull,
                    params.isAnalyzeOldMap(),
                    true,
                    uncertaintyRefDistance);
            createHorizontalLinesFromGrid(
                    grid, destGeoSet,
                    dstHull,
                    params.isAnalyzeOldMap(),
                    true,
                    uncertaintyRefDistance);
        }

        // apply a multiquadric interpolation to the grid
        multiQuad.transform(grid.grid);

        // Create GeoPaths from the distorted grid and add them to the destination map
        createVerticalLinesFromGrid(
                grid,
                destGeoSet,
                dstHull,
                params.isAnalyzeOldMap(),
                true,
                uncertaintyRefDistance);
        createHorizontalLinesFromGrid(
                grid, destGeoSet,
                dstHull,
                params.isAnalyzeOldMap(),
                true,
                uncertaintyRefDistance);
    }

    /**
     * For each passed point first computes the distance to the closest
     * neighbor, and then computes the median of these distances.
     *
     * @param pts points
     * @param quantile quantile to compute, between 0 and 1
     * @return the median distance to the closest neighbor for all points in the
     * destination map.
     */
    private static double quantileDistanceToClosestPoint(double[][] pts, double quantile) {
        // square distance to closest neighbor for each point
        double[] shortestDistSquared = new double[pts.length];

        // find square distance to closest neighbor for each point
        for (int i = 0; i < pts.length; i++) {
            shortestDistSquared[i] = Double.MAX_VALUE;
            for (int j = 0; j < pts.length; j++) {
                if (i == j) {
                    continue;
                }
                double dx = pts[i][0] - pts[j][0];
                double dy = pts[i][1] - pts[j][1];
                double dsq = dx * dx + dy * dy;
                shortestDistSquared[i] = Math.min(shortestDistSquared[i], dsq);
            }
        }

        return Math.sqrt(Median.quantile(shortestDistSquared, quantile));
    }

    /**
     * Returns the distance to the closest point.
     *
     * @param pts points to search closest point
     * @param xy position in destination map
     * @return shortest distance
     */
    private static double distanceToClosestPoint(double[][] pts, double x, double y) {
        double shortestDistSq = Double.MAX_VALUE;

        // find square distance to each point
        for (double[] pt : pts) {
            double dx = pt[0] - x;
            double dy = pt[1] - y;
            double dsq = dx * dx + dy * dy;
            shortestDistSq = Math.min(shortestDistSq, dsq);
        }
        return Math.sqrt(shortestDistSq);
    }

    /**
     * Returns the distance to the closest point of two point sets.
     *
     * @param ptSet1 point set 1
     * @param ptSet2 point set 2
     * @param xy position in destination map
     * @return shortest distance
     */
    private static double distanceToClosestPoint(double[][] ptSet1,
            double[][] ptSet2, double x, double y) {
        return Math.min(distanceToClosestPoint(ptSet1, x, y),
                distanceToClosestPoint(ptSet2, x, y));
    }

    /**
     * Returns whether a graticule with longitude/latitude lines should be
     * generated. This is the case if the mesh units are degrees, the grid is
     * displayed in the old map, and the OpenStreetMap is used as reference map.
     *
     * @return true if a lon/lat grid is to be created.
     */
    private boolean isGeographicalGrid(VisualizationParameters params) {
        return this.meshUnit == Unit.DEGREES
                && params.isAnalyzeOldMap()
                && params.isOSM();
    }

    /**
     * Returns whether the number of horizontal and vertical cells in a grid are
     * within reasonable bounds.
     */
    public boolean isNumberOfGridLinesCorrect(Rectangle2D srcPointsExtension) {
        final double scaledMeshSize = this.getScaledMeshSize();
        try {
            Grid grid = new Grid(srcPointsExtension, scaledMeshSize);
            return grid.isNumberOfGridLinesCorrect();
        } catch (MapAnalyzerException exc) {
            return false;
        }
    }

    /**
     * Returns a string that can be used in a dialog to inform the user that the
     * grid has not a correct size, i.e. the number of lines is too small or too
     * large.
     */
    private String getErrorMessageForIncorrectNumberOfGridLines(Grid grid,
            Rectangle2D srcPointsExtension) {

        double cellSize = grid.getSuggestedCellSize(srcPointsExtension);
        String msg = "With the selected mesh size, the new distortion"
                + "\ngrid would contain less than ";
        msg += MIN_NODES;
        msg += " or more than ";
        msg += MAX_NODES;
        msg += "\nvertical or horizontal lines.";
        msg += "\nPlease enter a different value in the Mesh Size field. ";
        msg += "\nA suggested value is ";
        msg += new DecimalFormat("#,##0.#########").format(cellSize);
        if (meshUnit == Unit.DEGREES) {
            msg += "\u00B0";
        } else {
            msg += " meters";
        }
        msg += ".";
        return msg;
    }

    /**
     * Create a label for a grid line.
     *
     * @param label text for the label
     * @param xy position
     * @param labelVerticalLine true if the label is for a vertical grid line
     * @param geoSet the destination for the new GeoText
     */
    private void addLabel(String label, double[] xy,
            boolean labelVerticalLine, GeoSet geoSet) {

        double dx = 0;
        double dy = 0;
        if (labelVerticalLine) {
            dy = -this.labelSize * 1.2;
        } else {
            dx = this.labelSize / 2;
        }

        GeoText geoText = new GeoText(label, xy[0], xy[1], dx, dy);
        geoText.setSize(this.labelSize);
        geoText.setCenterHor(labelVerticalLine);
        geoText.setCenterVer(!labelVerticalLine);
        geoText.setScaleInvariant(true);
        geoText.setSelectable(false);
        geoSet.addGeoObject(geoText);
    }

    /**
     * Add a label to the start or the end of a line
     *
     * @param pt1 start point of line
     * @param pt2 end point of line
     * @param labelStr the label
     * @param geoSet destination for new GeoText
     * @param gridInOldMap true if the grid is in the old map
     */
    private void addVerticalLabel(double[] pt1,
            double[] pt2,
            String labelStr,
            GeoSet geoSet,
            boolean gridInOldMap) {

        double[] xy = new double[2]; // label position

        // position label at lower end of line
        if (pt1[1] < pt2[1]) {
            xy[0] = pt1[0];
            xy[1] = pt1[1];
        } else {
            xy[0] = pt2[0];
            xy[1] = pt2[1];
        }

        // transform to OSM if needed
        if (params.isOSM() && !gridInOldMap) {
            xy = params.getProjector().intermediate2OSM(xy);
        }

        addLabel(labelStr, xy, true, geoSet);
    }

    /**
     * Add a label to the start or the end of a line
     *
     * @param pt1 start point of line
     * @param pt2 end point of line
     * @param labelStr the label
     * @param geoSet destination for new GeoText
     * @param gridInOldMap true if the grid is in the old map
     */
    private void addHorizontalLabel(double[] pt1,
            double[] pt2,
            String labelStr,
            GeoSet geoSet,
            boolean gridInOldMap) {

        double[] xy = new double[2]; // label position

        // position label at right end of line
        if (pt1[0] > pt2[0]) {
            xy[0] = pt1[0];
            xy[1] = pt1[1];
        } else {
            xy[0] = pt2[0];
            xy[1] = pt2[1];
        }

        // transform to OSM if needed
        if (params.isOSM() && !gridInOldMap) {
            xy = params.getProjector().intermediate2OSM(xy);
        }

        addLabel(labelStr, xy, false, geoSet);
    }

    /**
     *
     * @param polyline
     * @param polygon
     * @param geoSet
     * @param useBezier
     * @param gridInOldMap
     * @param gridInDestinationMap true if the grid is for the destination map,
     * which shows the distorted grid.
     * @param pathName
     * @param uncertaintyRefDistance if a grid vertex is further away from any
     * control point than this distance, the vertex is considered uncertain and
     * rendered semi-transparent. The distance is in the coordinate system of
     * the map with the distorted grid. If zero, uncertainty is not visualized.
     * @return
     */
    private double[][] clipLine(double[][] polyline,
            double[][] polygon,
            GeoSet geoSet,
            boolean useBezier,
            boolean gridInOldMap,
            boolean gridInDestinationMap,
            String pathName,
            double uncertaintyRefDistance) {

        // clip the polyline with the mask polygon
        java.util.Vector lines = ika.utils.GeometryUtils.clipPolylineWithPolygon(
                polyline, polygon);

        if (lines.size() < 1) {
            return null;
        }

        // add the clipped lines to the GeoSet
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i) == null) {
                continue;
            }
            double[][] line = (double[][]) lines.get(i);
            addGridLine(line, geoSet, useBezier, gridInOldMap,
                    gridInDestinationMap, pathName, uncertaintyRefDistance);
        }

        // return the first and the last point of the clipped lines
        double[][] firstLine = (double[][]) lines.get(0);
        double[] firstPoint = firstLine[0];
        double[][] lastLine = (double[][]) lines.get(lines.size() - 1);
        double[] lastPoint = lastLine[lastLine.length - 1];
        double[][] extremaPoints = new double[][]{firstPoint, lastPoint};
        return extremaPoints;
    }

    /**
     * Create a GeoPath from a set of points.
     *
     * @param line points of the line
     * @param firstPoint id of first point
     * @param nbrPoints number of total points in line
     * @param useBezier if true, Bezier curve segments are created
     * @param name name of the GeoPath
     * @return a new GeoPath
     */
    private GeoPath gridLineToGeoPath(double[][] line, int firstPoint,
            int nbrPoints, boolean useBezier, String name) {
        GeoPath geoPath = new GeoPath();
        geoPath.setSelectable(false);
        geoPath.setVectorSymbol(vectorSymbol);
        geoPath.setName(name);

        // only use Bezier splines if smoothness is larger then 0
        if (useBezier && smoothness > 0) {
            geoPath.smooth(this.smoothness, line, firstPoint, nbrPoints);
        } else {
            geoPath.straightLines(line, firstPoint, nbrPoints);
        }

        return geoPath;
    }

    /**
     * Adds a line to the grid. The line may be split into segments to show
     * uncertainty.
     *
     * @param line coordinates of the line
     * @param geoSet destination line
     * @param useBezier smoothed lines
     * @param gridInOldMap true if grid is for the old map
     * @param gridInDestinationMap true if the grid is for the destination map,
     * which shows the distorted grid.
     * @param lineName name for the new GeoLine
     * @param uncertaintyRefDistance if a grid vertex is further away from any
     * control point than this distance, the vertex is considered uncertain and
     * rendered semi-transparent. The distance is in the coordinate system of
     * the map with the distorted grid. If zero, uncertainty is not visualized.
     */
    private void addGridLine(double[][] line,
            GeoSet geoSet,
            boolean useBezier,
            boolean gridInOldMap,
            boolean gridInDestinationMap,
            String lineName,
            double uncertaintyRefDistance) {

        if (line.length < 2) {
            return;
        }

        // convert from intermediate coordinate system to OSM coordinate system
        // if the distorted grid is in the new map and OSM is used.
        if (params.isOSM() && !gridInOldMap) {
            double[][] osmLine = new double[line.length][2];
            params.getProjector().intermediate2OSM(line, osmLine);
            line = osmLine;
        }

        if (gridInDestinationMap && uncertaintyRefDistance > 0) {
            Color c = vectorSymbol.getStrokeColor();
            int alpha = (int) (255f * uncertaintyAlpha);
            Color uncertainColor = new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
            VectorSymbol uncertainVectorSymbol = vectorSymbol.copy();
            uncertainVectorSymbol.setStrokeColor(uncertainColor);

            // if the grid is for an OSM map, the dst and transformed src points are 
            // in an intermediate coordinate system.
            double[][] dstPoints = params.getDstPoints();
            double[][] srcPointsTrans = params.getTransformedSourcePoints();

            int firstPoint = 0;
            double d = distanceToClosestPoint(srcPointsTrans, dstPoints, line[firstPoint][0], line[firstPoint][1]);
            boolean uncertain = d > uncertaintyRefDistance;
            for (int i = 1; i < line.length; i++) {
                d = distanceToClosestPoint(srcPointsTrans, dstPoints, line[i][0], line[i][1]);
                boolean nextIsUncertain = d > uncertaintyRefDistance;
                if (uncertain != nextIsUncertain || i == line.length - 1) {
                    int nbrPts = i - firstPoint;
                    if (!uncertain || i == line.length - 1) {
                        ++nbrPts;
                    }
                    if (nbrPts >= 2) {
                        GeoPath p = gridLineToGeoPath(line, firstPoint, nbrPts, useBezier, lineName);
                        geoSet.addGeoObject(p);
                        if (uncertain) {
                            p.setVectorSymbol(uncertainVectorSymbol);
                        }

                        // start point of next line
                        firstPoint = uncertain ? i - 1 : i;
                    }
                    uncertain = nextIsUncertain;
                }
            }

        } else {
            GeoPath geoPath = new GeoPath();
            geoPath.setSelectable(false);
            geoPath.setVectorSymbol(this.vectorSymbol);
            geoPath.setName(lineName);

            // only use Bezier splines if smoothness is larger then 0
            if (useBezier && smoothness > 0) {
                geoPath.smooth(this.smoothness, line, 0, line.length);
            } else {
                geoPath.straightLines(line, 0, line.length);
            }
            geoSet.addGeoObject(geoPath);
        }
    }

    /**
     * Optionally clip a grid line, then create a GeoPath for the line.
     *
     * @param line coordinates of line
     * @param mask clip line with this mask polygon. If null, no clipping is
     * applied.
     * @param geoSet destination for new line
     * @param useBezier if true, the new line is smooth
     * @param labelString label for the line
     * @param lineName name for the new GeoLine
     * @param horizontalLabel true if the label to add is for a horizontal line
     * @param gridInOldMap true if grid is for the old map
     * @param gridInDestinationMap true if the grid is for the destination map,
     * which shows the distorted grid.
     * @param uncertaintyRefDistance if a grid vertex is further away from any
     * control point than this distance, the vertex is considered uncertain and
     * rendered semi-transparent. The distance is in the coordinate system of
     * the map with the distorted grid. If zero, uncertainty is not visualized.
     */
    private void clipAndAddPath(double[][] line,
            double[][] mask,
            GeoSet geoSet,
            boolean useBezier,
            String labelString,
            String lineName,
            boolean horizontalLabel,
            boolean gridInOldMap,
            boolean gridInDestinationMap,
            double uncertaintyRefDistance) {

        // clip the line with the mask.
        if (mask != null) {
            double[][] extremaPoints = clipLine(line, mask, geoSet, useBezier,
                    gridInOldMap, gridInDestinationMap, lineName, uncertaintyRefDistance);
            if (labelString != null && extremaPoints != null) {
                if (horizontalLabel) {
                    addHorizontalLabel(extremaPoints[0], extremaPoints[1],
                            labelString, geoSet, gridInOldMap);
                } else {
                    addVerticalLabel(extremaPoints[0], extremaPoints[1],
                            labelString, geoSet, gridInOldMap);
                }
            }
        } else { // no mask: add the whole line
            addGridLine(line, geoSet, useBezier, gridInOldMap,
                    gridInDestinationMap, lineName, uncertaintyRefDistance);
            if (labelString != null) {
                if (horizontalLabel) {
                    addHorizontalLabel(line[0], line[line.length - 1],
                            labelString, geoSet, gridInOldMap);
                } else {
                    addVerticalLabel(line[0], line[line.length - 1],
                            labelString, geoSet, gridInOldMap);
                }
            }
        }
    }

    private double horizontalLabelOffset(VisualizationParameters params, Grid grid) {

        if (!params.isOSM()) {
            // OSM is not used, because no local coordinate system is used
            // the label should include the border coordinate in this case
            return grid.west;
        } else {
            // OSM is used, because a local coordinate system is used.
            // The local coordinate system is meaningless to the user, so
            // start counting the coordinates at 0.
            // However, if a graticule of longitutude/latitude values is
            // generated, the correct values should be shown.
            if (isGeographicalGrid(params)) {
                return grid.west;
            } else {
                return getScaledOffsetX();
            }
        }

    }

    private double verticalLabelOffset(VisualizationParameters params, Grid grid) {

        if (!params.isOSM()) {
            // OSM is not used, because no local coordinate system is used
            // the label should include the border coordinate in this case
            return grid.south;
        } else {
            // OSM is used, because a local coordinate system is used.
            // The local coordinate system is meaningless to the user, so
            // start counting the coordinates at 0.
            // However, if a graticule of longitutude/latitude values is
            // generated, the correct values should be shown.
            if (isGeographicalGrid(params)) {
                return grid.south;
            } else {
                return getScaledOffsetY();
            }
        }

    }

    /**
     * Create all vertical lines of a grid
     *
     * @param grid grid geometry
     * @param geoSet destination for new grid lines
     * @param mask mask to clip grid lines with
     * @param gridInOldMap true if the gird is for the old map
     * @param gridInDestinationMap true if the grid is for the destination map,
     * which shows the distorted grid.
     * @param uncertaintyRefDistance if a grid vertex is further away from any
     * control point than this distance, the vertex is considered uncertain and
     * rendered semi-transparent. The distance is in the coordinate system of
     * the map with the distorted grid. If zero, uncertainty is not visualized.
     */
    private void createVerticalLinesFromGrid(
            Grid grid,
            GeoSet geoSet,
            double[][] mask,
            boolean gridInOldMap,
            boolean gridInDestinationMap,
            double uncertaintyRefDistance) {

        final CoordinateFormatter coordinateFormatter
                = params.isAnalyzeOldMap()
                ? params.getNewCoordinateFormatter()
                : params.getOldCoordinateFormatter();

        // draw the distorted grid with interpolated bezier curves.
        boolean useBezier = (gridInOldMap == params.isAnalyzeOldMap());

        double[][] line = new double[grid.numberNodesY][2];
        for (int i = 0, lineID = 0; i < grid.grid.length; i += grid.numberNodesY, lineID++) {
            int ptID = 0;
            for (int y = i; y < i + grid.numberNodesY; y++) {
                line[ptID][0] = grid.grid[y][0];
                line[ptID][1] = grid.grid[y][1];
                ++ptID;
            }

            // prepare label
            boolean placeLabel = this.labelSize > 0 && lineID % this.labelSequence == 0;
            double labelVal = grid.verticalLabels[i / grid.numberNodesY];
            labelVal += horizontalLabelOffset(params, grid);
            String coordStr = coordinateFormatter.formatShort(labelVal);

            String labelStr = null;
            if (placeLabel) {
                labelStr = coordStr;
                if (isGeographicalGrid(params)) {
                    labelStr += "\u00B0";
                }
            }

            clipAndAddPath(line, mask, geoSet, useBezier, labelStr, coordStr, false,
                    gridInOldMap, gridInDestinationMap, uncertaintyRefDistance);
        }
    }

    /**
     * Create all horizontal lines of a grid
     *
     * @param grid grid geometry
     * @param geoSet destination for new grid lines
     * @param mask mask to clip grid lines with
     * @param gridInOldMap true if the gird is for the old map
     * @param gridInDestinationMap true if the grid is for the destination map,
     * which shows the distorted grid.
     * @param uncertaintyRefDistance if a grid vertex is further away from any
     * control point than this distance, the vertex is considered uncertain and
     * rendered semi-transparent. The distance is in the coordinate system of
     * the map with the distorted grid. If zero, uncertainty is not visualized.
     */
    private void createHorizontalLinesFromGrid(
            Grid grid, GeoSet geoSet,
            double[][] mask,
            boolean gridInOldMap,
            boolean gridInDestinationMap,
            double uncertaintyRefDistance) {

        final CoordinateFormatter coordinateFormatter
                = params.isAnalyzeOldMap()
                ? params.getNewCoordinateFormatter()
                : params.getOldCoordinateFormatter();

        // draw the distorted grid with interpolated bezier curves.
        boolean useBezier = (gridInOldMap == params.isAnalyzeOldMap());

        double[][] line = new double[grid.numberNodesX][2];
        for (int y = 0; y < grid.numberNodesY; y++) {
            for (int x = 0; x < grid.numberNodesX; x++) {
                int row = y + x * grid.numberNodesY;
                line[x][0] = grid.grid[row][0];
                line[x][1] = grid.grid[row][1];
            }

            boolean placeLabel = this.labelSize > 0 && y % this.labelSequence == 0;
            double labelVal = grid.horizontalLabels[y];
            labelVal += verticalLabelOffset(params, grid);
            String coordStr = coordinateFormatter.formatShort(labelVal);
            String labelStr = null;
            if (placeLabel) {
                labelStr = coordStr;
                if (isGeographicalGrid(params)) {
                    labelStr += "\u00B0";
                }
            }

            clipAndAddPath(line, mask, geoSet, useBezier, labelStr, coordStr, true,
                    gridInOldMap, gridInDestinationMap, uncertaintyRefDistance);
        }
    }

    public void setMeshSize(double meshsize) {
        this.meshSize = meshsize;
    }

    public double getMeshSize() {
        return this.meshSize;
    }

    private double getScaledMeshSize() {
        return this.meshSize / this.meshSizeScale;
    }

    public VectorSymbol getVectorSymbol() {
        return vectorSymbol;
    }

    public void setVectorSymbol(VectorSymbol vectorSymbol) {
        vectorSymbol.copyTo(this.vectorSymbol);
    }

    public double getSmoothness() {
        return smoothness;
    }

    public void setSmoothness(double smoothness) {
        this.smoothness = smoothness;
    }

    /**
     * Alpha value for rendering uncertain values, between 0.05 and 1.
     *
     * @return the uncertaintyAlpha
     */
    public float getUncertaintyAlpha() {
        return uncertaintyAlpha;
    }

    /**
     * Alpha value for rendering uncertain areas
     *
     * @param uncertaintyAlpha alpha value, changed to 0.05 if smaller than this
     * value to make sure the grid is always visible. Max value is 1f.
     */
    public void setUncertaintyAlpha(float uncertaintyAlpha) {
        this.uncertaintyAlpha = Math.min(1f, Math.max(0.05f, uncertaintyAlpha));
    }

    /**
     * Quantile to extract reference distance from all shortest distances
     * between control points to determine whether a gird node is considered
     * uncertain.
     *
     * @return the uncertaintyQuantile
     */
    public double getUncertaintyQuantile() {
        return uncertaintyQuantile;
    }

    /**
     * Quantile to extract reference distance from all shortest distances
     * between control points to determine whether a gird node is considered
     * uncertain.
     *
     * @param uncertaintyQuantile the quantile between 0.01 and 1
     */
    public void setUncertaintyQuantile(double uncertaintyQuantile) {
        this.uncertaintyQuantile = Math.min(1f, Math.max(0.01f, uncertaintyQuantile));
    }

    public double getMeshSizeScale() {
        return meshSizeScale;
    }

    public void setMeshSizeScale(double meshSizeScale) {
        this.meshSizeScale = meshSizeScale;
    }

    public int getClipWithHull() {
        return clipWithHull;
    }

    public void setClipWithHull(int clipWithHull) {
        this.clipWithHull = clipWithHull;
    }

    public int getLabelSize() {
        return labelSize;
    }

    public void setLabelSize(int labelSize) {
        this.labelSize = labelSize;
    }

    public int getLabelSequence() {
        return labelSequence;
    }

    public void setLabelSequence(int labelSequence) {
        this.labelSequence = labelSequence;
    }

    public double[][] getOldClipPolygon() {
        return oldClipPolygon;
    }

    public boolean hasOldClipPolygon() {
        return this.oldClipPolygon != null;
    }

    public void setOldClipPolygon(double[][] oldClipPolygon) {
        this.oldClipPolygon = oldClipPolygon;
    }

    public double[][] getNewClipPolygon() {
        return newClipPolygon;
    }

    public boolean hasNewClipPolygon() {
        return this.newClipPolygon != null;
    }

    public void setNewClipPolygon(double[][] newClipPolygon) {
        this.newClipPolygon = newClipPolygon;
    }

    /**
     * @return the meshUnit
     */
    public Unit getMeshUnit() {
        return meshUnit;
    }

    /**
     * @param meshUnit the meshUnit to set
     */
    public void setMeshUnit(Unit meshUnit) {
        this.meshUnit = meshUnit;
    }

    /**
     * @return the offsetX
     */
    public double getOffsetX() {
        return offsetX;
    }

    public double getScaledOffsetX() {
        double dx = offsetX != 0 ? offsetX % meshSize : 0;
        return dx / meshSizeScale;
    }

    /**
     * @param offsetX the offsetX to set
     */
    public void setOffsetX(double offsetX) {
        this.offsetX = offsetX;
    }

    /**
     * @return the offsetY
     */
    public double getOffsetY() {
        return offsetY;
    }

    public double getScaledOffsetY() {
        double dy = offsetY != 0 ? offsetY % meshSize : 0;
        return dy / meshSizeScale;
    }

    /**
     * @param offsetY the offsetY to set
     */
    public void setOffsetY(double offsetY) {
        this.offsetY = offsetY;
    }

    /**
     * @return the exaggeration
     */
    public double getExaggeration() {
        return exaggeration;
    }

    /**
     * @param exaggeration the exaggeration to set
     */
    public void setExaggeration(double exaggeration) {
        this.exaggeration = exaggeration;
    }

    /**
     * @return the showUndistorted
     */
    public boolean isShowUndistorted() {
        return showUndistorted;
    }

    /**
     * @param showUndistorted the showUndistorted to set
     */
    public void setShowUndistorted(boolean showUndistorted) {
        this.showUndistorted = showUndistorted;
    }

}
