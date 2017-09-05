package ika.mapanalyst;

import java.io.*;
import java.awt.geom.*;
import ika.geo.*;
import ika.geo.osm.OpenStreetMap;
import ika.utils.*;
import java.text.DecimalFormat;

/**
 * DistortionGrid.java
 *
 * @author Bernhard Jenny and Adrian Weber, Institute of Cartography ETH Zurich
 */
public class DistortionGrid extends MapAnalyzer implements Serializable {

    private static final long serialVersionUID = -5990761709834452077L;
    private double meshSize;
    /**
     * meshSizeScale is 1, if the grid is transformed from the new reference map
     * to the old map, i.e. the distorted grid is displayed in the old map.
     * meshSizeScale is > 1 if the grid is transformed from the old map to the
     * new reference map, i.e. the distorted grid is diplayed in the new
     * reference map.
     */
    private double meshSizeScale;

    public enum Unit {

        METERS, DEGREES
    }
    private Unit meshUnit;
    private double smoothness;
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
     * color and stroke width of distortiong grid
     */
    private VectorSymbol vectorSymbol;
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

        // exaggeration was added with 1.3.?
        if (exaggeration == 0) {
            exaggeration = 1;
        }

        // meshUnit was added with 1.3.8
        if (meshUnit == null) {
            meshUnit = Unit.METERS;
        }
    }

    public String getName() {
        return "Distortion Grid";
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

    private class Grid {

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
            final double[] bases = new double[]{7.5, 5, 2.5, 1.5, 1};
            double cellSize = Math.min(ext.getWidth(), ext.getHeight()) / DEF_NODES;
            if (cellSize <= 0d) {
                return -1;
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
            final double scaledMeshSize = this.getScaledMeshSize();
            grid = new Grid(params.getSrcPointsExtension(), scaledMeshSize);
        }

        // get the convex hulls around the two point sets
        double[][] srcHull;
        double[][] dstHull;
        switch (this.clipWithHull) {
            case 1: //1: clip with convex hull
                dstHull = params.getDstPointsHull();
                srcHull = params.getSrcPointsHull();
                break;
            case 2: // 2: clip with custom polygon
                if (params.isAnalyzeOldMap()) {
                    srcHull = null;
                    dstHull = this.oldClipPolygon;
                } else {
                    srcHull = null;
                    dstHull = this.newClipPolygon;
                }
                break;
            default: // 0: no clipping
                srcHull = null;
                dstHull = null;
        }

        // Create GeoPaths from the undistorted grid
        // add the undistorted grid to the source map
        this.createVerticalLinesFromGrid(
                grid,
                sourceGeoSet, srcHull,
                !params.isAnalyzeOldMap());
        this.createHorizontalLinesFromGrid(
                grid,
                sourceGeoSet,
                srcHull,
                !params.isAnalyzeOldMap());

        // apply an affine transformation to the grid
        params.getTransformation().transform(grid.grid);

        // Create GeoPaths from the undistorted transformed grid and add them 
        // to the destination map if required.
        if (showUndistorted) {
            this.createVerticalLinesFromGrid(
                    grid,
                    destGeoSet,
                    dstHull,
                    params.isAnalyzeOldMap());
            this.createHorizontalLinesFromGrid(
                    grid, destGeoSet,
                    dstHull,
                    params.isAnalyzeOldMap());
        }

        // apply a multiquadric interpolation to the grid
        multiQuad.transform(grid.grid);

        // Create GeoPaths from the distorted grid and add them
        // to the destination map
        this.createVerticalLinesFromGrid(
                grid,
                destGeoSet,
                dstHull,
                params.isAnalyzeOldMap());
        this.createHorizontalLinesFromGrid(
                grid, destGeoSet,
                dstHull,
                params.isAnalyzeOldMap());
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
     * Returns whether the number of hoirizontal and vertical cells in a grid
     * are within reasonable bounds.
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
        String msg = "With the current mesh size, the new distortion"
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

    private void addLabel(String label, double x, double y,
            boolean labelVerticalLine, GeoSet geoSet) {

        double dx = 0;
        double dy = 0;
        if (labelVerticalLine) {
            dy = -this.labelSize * 1.2;
        } else {
            dx = this.labelSize / 2;
        }

        GeoText geoText = new GeoText(label, x, y, dx, dy);
        geoText.setSize(this.labelSize);
        geoText.setCenterHor(labelVerticalLine);
        geoText.setCenterVer(!labelVerticalLine);
        geoText.setScaleInvariant(true);
        geoSet.addGeoObject(geoText);
    }

    private void addVerticalLabel(double[] pt1,
            double[] pt2,
            String labelStr,
            GeoSet geoSet,
            boolean gridInOldMap) {

        // position label at lower end of line
        double x, y;
        if (pt1[1] < pt2[1]) {
            x = pt1[0];
            y = pt1[1];
        } else {
            x = pt2[0];
            y = pt2[1];
        }
        if (params.isOSM() && !gridInOldMap) {
            double[][] ptIn = new double[][]{{x, y}};
            double[][] ptOut = new double[1][2];
            params.getProjector().intermediate2OSM(ptIn, ptOut);
            x = ptOut[0][0];
            y = ptOut[0][1];
        }
        this.addLabel(labelStr, x, y, true, geoSet);
    }

    private void addHorizontalLabel(double[] pt1,
            double[] pt2,
            String labelStr,
            GeoSet geoSet,
            boolean gridInOldMap) {

        // position label at right end of line
        double x, y;
        if (pt1[0] > pt2[0]) {
            x = pt1[0];
            y = pt1[1];
        } else {
            x = pt2[0];
            y = pt2[1];
        }
        if (params.isOSM() && !gridInOldMap) {
            double[][] ptIn = new double[][]{{x, y}};
            double[][] ptOut = new double[1][2];
            params.getProjector().intermediate2OSM(ptIn, ptOut);
            x = ptOut[0][0];
            y = ptOut[0][1];
        }
        this.addLabel(labelStr, x, y, false, geoSet);
    }

    private double[][] clipLine(double[][] polyline,
            double[][] polygon,
            GeoSet geoSet,
            boolean useBezier,
            boolean gridInOldMap,
            String pathName) {

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
            this.addPath(line, geoSet, useBezier, gridInOldMap, pathName);
        }

        // return the first and the last point of the clipped lines
        double[][] firstLine = (double[][]) lines.get(0);
        double[] firstPoint = firstLine[0];
        double[][] lastLine = (double[][]) lines.get(lines.size() - 1);
        double[] lastPoint = lastLine[lastLine.length - 1];
        double[][] extremaPoints = new double[][]{firstPoint, lastPoint};
        return extremaPoints;
    }

    private void addPath(double[][] line,
            GeoSet geoSet,
            boolean useBezier,
            boolean gridInOldMap,
            String name) {

        if (params.isOSM() && !gridInOldMap) {
            double[][] projLine = new double[line.length][2];
            params.getProjector().intermediate2OSM(line, projLine);
            line = projLine;
        }

        GeoPath geoPath = new GeoPath();
        geoPath.setSelectable(false);
        geoPath.setVectorSymbol(this.vectorSymbol);
        geoPath.setName(name);

        // only use Bezier splines if smoothness is larger then 0
        if (useBezier && smoothness > 0) {
            geoPath.smooth(this.smoothness, line, 0, line.length);
        } else {
            geoPath.straightLines(line, 0, line.length);
        }
        geoSet.addGeoObject(geoPath);
    }

    private void clipAndAddPath(double[][] line,
            double[][] mask,
            GeoSet geoSet,
            boolean useBezier,
            String labelString,
            String pathName,
            boolean horizontalLabel,
            boolean gridInOldMap) {

        // clip the line with the mask.
        if (mask != null) {
            double[][] extremaPoints = clipLine(line, mask, geoSet, useBezier, gridInOldMap, pathName);
            if (labelString != null && extremaPoints != null) {
                if (horizontalLabel) {
                    addHorizontalLabel(extremaPoints[0], extremaPoints[1], labelString, geoSet, gridInOldMap);
                } else {
                    addVerticalLabel(extremaPoints[0], extremaPoints[1], labelString, geoSet, gridInOldMap);
                }
            }
        } else { // no mask: add the whole line
            addPath(line, geoSet, useBezier, gridInOldMap, pathName);
            if (labelString != null) {
                if (horizontalLabel) {
                    addHorizontalLabel(line[0], line[line.length - 1], labelString, geoSet, gridInOldMap);
                } else {
                    addVerticalLabel(line[0], line[line.length - 1], labelString, geoSet, gridInOldMap);
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

    private void createVerticalLinesFromGrid(
            Grid grid, GeoSet geoSet,
            double[][] mask,
            boolean gridInOldMap) {

        final CoordinateFormatter coordinateFormatter
                = params.isAnalyzeOldMap()
                ? params.getNewCoordinateFormatter()
                : params.getOldCoordinateFormatter();

        // draw the distorted grid with interpolated bezier curves.
        boolean useBezier = (gridInOldMap == params.isAnalyzeOldMap());

        double[][] line = new double[grid.numberNodesY][2];
        for (int i = 0, lineID = 0; i < grid.grid.length; i += grid.numberNodesY, lineID++) {
            int nbrPointsInLine = 0;
            for (int y = i; y < i + grid.numberNodesY; y++) {
                line[nbrPointsInLine][0] = grid.grid[y][0];
                line[nbrPointsInLine][1] = grid.grid[y][1];
                ++nbrPointsInLine;
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

            clipAndAddPath(line, mask, geoSet, useBezier, labelStr, coordStr, false, gridInOldMap);
        }
    }

    private void createHorizontalLinesFromGrid(
            Grid grid, GeoSet geoSet,
            double[][] mask,
            boolean gridInOldMap) {

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

            clipAndAddPath(line, mask, geoSet, useBezier, labelStr, coordStr, true, gridInOldMap);
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
}
