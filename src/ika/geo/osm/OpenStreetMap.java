package ika.geo.osm;

import com.jhlabs.map.proj.MercatorProjection;
import ika.geo.GeoObject;
import ika.geo.GeoText;
import ika.gui.MapComponent;
import ika.proj.ProjectionsManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import org.openstreetmap.gui.jmapviewer.MemoryTileCache;
import org.openstreetmap.gui.jmapviewer.Tile;
import org.openstreetmap.gui.jmapviewer.TileController;
import org.openstreetmap.gui.jmapviewer.interfaces.TileCache;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoaderListener;
import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.OsmTileSource;

/**
 * Load and displays OpenStreetMap tiles using the excellent JMapViewer project:
 * http://wiki.openstreetmap.org/wiki/JMapViewer
 *
 * @author Bernhard Jenny
 */
public class OpenStreetMap extends GeoObject implements java.io.Serializable, TileLoaderListener {

    private static final long serialVersionUID = -6328405447473299261L;

    /**
     * Number of cached image tiles. Estimation of required memory:
     * NBR_CACHED_IMAGES * 256 * 256 * 4 [bytes] = 1000 * 256 * 256 * 4 = 250MB
     */
    private static final int NBR_CACHED_IMAGES = 1000;

    /**
     * OSM zoom levels vary between 0 (1 image for the whole globe) and 19
     * (large scale tiles).
     */
    private static final int OSM_MAX_ZOOM = 19;
    private static final int OSM_MIN_ZOOM = 0;

    /**
     * Maximum mapped latitude.
     */
    public static final double MAX_LAT = 85.05112877980659;
    /**
     * Minimum mapped latitude.
     */
    public static final double MIN_LAT = -MAX_LAT;

    /**
     * Radius of earth sphere
     */
    public static final double R = 6378137;

    /**
     * spacing between lines in graticule
     */
    private static final double[] MINOR_GRATICULE_SPACING = new double[]{
        90, // zoom 0
        45, // zoom 1
        30, // zoom 2
        15, // zoom 3
        5, // zoom 4
        5, // zoom 5
        2, // zoom 6 
        2, // zoom 7
        1, // zoom 8
        0.5,// zoom 9
        0.25, // zoom 10
        1. / 12.// zoom 11
    // no graticule for zoom 12 and larger
    };

    private static final int[] MAJOR_GRATICULE_SPACING = new int[]{
        0, // zoom 0
        0, // zoom 1
        0, // zoom 2
        45, // zoom 3
        30, // zoom 4
        30, // zoom 5
        10, // zoom 6 
        10, // zoom 7
        5, // zoom 8
        1,// zoom 9
        1, // zoom 10
        1// zoom 11
    // no graticule for zoom 12 and larger
    };

    /**
     * Mercator y coordinate for latitude.
     *
     * @param latDeg latitude in degrees
     * @return y coordinate in meters
     */
    private static double yMercator(double latDeg) {
        return R * Math.log(Math.tan(Math.PI / 4 + 0.5 * Math.toRadians(latDeg)));
    }

    /**
     * Maximum mapped Cartesian x value
     */
    private static final double MAX_X = Math.PI * R;

    /**
     * Maximum mapped Cartesian y value
     */
    private static final double MAX_Y = yMercator(MAX_LAT);

    /**
     * bounding box including all tiles
     */
    private static final Rectangle2D BOUNDS = new Rectangle2D.Double(-MAX_X, -MAX_Y, 2 * MAX_X, 2 * MAX_Y);

    /**
     * location of polar circles on Mercator projection
     */
    private static final double POLAR_CIRCLE_Y = yMercator(66.562944);

    /**
     * location of tropic circles on Mercator projection
     */
    private static final double TROPIC_CIRCLE_Y = yMercator(23.43706);

    /**
     * color for drawing polar and tropic circles
     */
    private static final Color POLAR_TROPIC_COLOR = new Color(150, 180, 150);

    /**
     * Color for drawing graticule
     */
    private static final Color GRATICULE_COLOR = new Color(150, 155, 180, 180);

    /**
     * color for drawing graticule labels
     */
    private static final Color GRATICULE_LABEL_COLOR = Color.GRAY;

    /**
     * empty space between major graticule line ends and labels
     */
    private static final int LABEL_SPACING_PX = 3;

    /**
     * width of major graticule line in anti-aliased pixels
     */
    private static final double MAJOR_LINE_WIDTH_PX = 2;

    /**
     * size of a map tile in pixels
     */
    private static final int TILE_SIZE = 256;

    /**
     * OpenStreetMap JMapViewer access point
     */
    private transient TileController tileController;

    /**
     * parent map component
     */
    protected transient MapComponent mapComponent;

    /**
     * listener for scale change events to stop loading tiles when the OSM zoom
     * level changes
     */
    private transient PropertyChangeListener scaleChangeListener;

    private boolean showGraticule = true;
    private boolean showTropics = false;
    private boolean showPolarCircles = false;

    private static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 10);

    public OpenStreetMap() {
        init();
    }

    public OpenStreetMap(MapComponent mapComponent) {
        init();
        setMapComponent(mapComponent);
    }

    public final void setMapComponent(MapComponent mapComponent) {
        this.mapComponent = mapComponent;
        mapComponent.addScaleChangePropertyChangeListener(scaleChangeListener);
    }

    private void init() {
        setSelectable(false);
        setName("OpenStreetMap");
        TileSource tileSource = new OsmTileSource.Mapnik();
        TileCache cache = new MemoryTileCache(NBR_CACHED_IMAGES);
        tileController = new TileController(tileSource, cache, this);

        scaleChangeListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                double oldScale = (Double) evt.getOldValue();
                double newScale = (Double) evt.getNewValue();
                int oldZoom = zoomLevel(oldScale);
                int newZoom = zoomLevel(newScale);
                if (oldZoom != newZoom) {
                    tileController.cancelOutstandingJobs();
                }
            }
        };
    }

    /**
     * OSM map is being removed from MapComponent
     */
    public void dispose() {
        tileController.cancelOutstandingJobs();
        mapComponent.removeScaleChangePropertyChangeListener(scaleChangeListener);
    }

    private void readObject(java.io.ObjectInputStream stream) throws IOException, ClassNotFoundException {
        // showGraticule is a recent addition. Initialise it to true for projects 
        // created when no graticule was available.
        showGraticule = true;

        // read this object
        stream.defaultReadObject();

        // initialize transient TileController
        init();
    }

    @Override
    public void tileLoadingFinished(Tile tile, boolean success) {
        tile.setLoaded(success);
        mapComponent.repaint();
    }

    @Override
    public Rectangle2D getBounds2D() {
        return BOUNDS;
    }

    /**
     * applies an affine transformation to the Graphics2D to scale and translate
     * the passed tile image.
     *
     * @param image image to draw
     * @param g2d graphics destination
     * @param x horizonal translation in meters
     * @param y vertical translation in meters
     * @param tileWidthWC
     * @param tileHeightWC
     */
    protected void drawTile(BufferedImage image, Graphics2D g2d,
            double x, double y, double tileWidthWC, double tileHeightWC) {
        AffineTransform trans = new AffineTransform();
        trans.scale(1, -1);
        trans.translate(x, -y);
        trans.scale(tileWidthWC / image.getWidth(), tileHeightWC / image.getHeight());
        g2d.drawImage(image, trans, null);
    }

    private static int zoomLevel(double mapScale) {
        double boundsWidthPx = BOUNDS.getWidth() * mapScale;
        int zoom = (int) Math.round(Math.log((boundsWidthPx / TILE_SIZE)) / Math.log(2.));
        return Math.max(0, zoom);
    }

    @Override
    public void draw(Graphics2D g2d, double scale, boolean drawSelectionState) {
        if (mapComponent == null) {
            return;
        }

        // compute OSM zoom level
        int zoom = zoomLevel(scale);
        if (zoom > OSM_MAX_ZOOM) {
            zoom = OSM_MAX_ZOOM;
        } else if (zoom < OSM_MIN_ZOOM) {
            zoom = OSM_MIN_ZOOM;
        }

        // compute number of tiles of the whole planet
        int tilesH = (int) Math.round(Math.pow(2, zoom));
        int tilesV = tilesH;
        double tileDim = BOUNDS.getWidth() / tilesH;

        // the first and last tiles visible in horizontal and vertical direction
        Rectangle2D.Double visRect = mapComponent.getVisibleArea();
        double visBottom = visRect.getMinY();
        double visLeft = visRect.getMinX();
        double visWidth = visRect.getWidth();
        double visHeight = visRect.getHeight();
        int firstRow = (int) ((BOUNDS.getMaxY() - (visBottom + visHeight)) / tileDim);
        firstRow = Math.max(firstRow, 0);
        int firstCol = (int) ((visLeft - BOUNDS.getMinX()) / tileDim);
        firstCol = Math.max(firstCol, 0);
        int lastRow = tilesV - (int) ((visBottom - BOUNDS.getMinY()) / tileDim);
        lastRow = Math.min(tilesV, lastRow);
        int lastCol = tilesH - (int) ((BOUNDS.getMaxX() - (visLeft + visWidth)) / tileDim);
        lastCol = Math.min(tilesH, lastCol);

        // load and draw all visible tiles
        for (int tiley = firstRow; tiley < lastRow; tiley++) {
            double y = BOUNDS.getMaxY() - tiley * tileDim;
            for (int tilex = firstCol; tilex < lastCol; tilex++) {
                double x = BOUNDS.getMinX() + tilex * tileDim;
                Tile tile = tileController.getTile(tilex, tiley, zoom);
                if (tile != null) {
                    drawTile(tile.getImage(), g2d, x, y, tileDim, tileDim);
                }
            }
        }

        if (showGraticule) {
            drawGraticule(g2d);
        }

        // style for polar and tropic circles
        g2d.setColor(POLAR_TROPIC_COLOR);
        g2d.setStroke(new BasicStroke(0, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF);

        if (showPolarCircles) {
            g2d.draw(new Line2D.Double(MAX_X, POLAR_CIRCLE_Y, -MAX_X, POLAR_CIRCLE_Y));
            g2d.draw(new Line2D.Double(MAX_X, -POLAR_CIRCLE_Y, -MAX_X, -POLAR_CIRCLE_Y));
        }

        if (showTropics) {
            g2d.draw(new Line2D.Double(MAX_X, TROPIC_CIRCLE_Y, -MAX_X, TROPIC_CIRCLE_Y));
            g2d.draw(new Line2D.Double(MAX_X, -TROPIC_CIRCLE_Y, -MAX_X, -TROPIC_CIRCLE_Y));
        }

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
    }

    private void drawMinorMeridian(Graphics2D g2d, double lonDeg, double yBottom, double yTop) {
        minorLineStyle(g2d);
        double meridianX = R * Math.toRadians(lonDeg);
        Line2D meridian = new Line2D.Double(meridianX, yBottom, meridianX, yTop);
        g2d.draw(meridian);
    }

    private void drawMajorMeridianAndLabel(Graphics2D g2d, double lonDeg, double yBottom, double yTop) {
        double scale = mapComponent.getScaleFactor();
        double meridianX = R * Math.toRadians(lonDeg);

        // format label
        String str = Long.toString(Math.abs(Math.round(lonDeg))) + "\u00B0";
        if (lonDeg < 0) {
            str += " W";
        } else if (lonDeg > 0) {
            str += " E";
        }

        // draw label
        GeoText geoText = new GeoText(str, meridianX, yTop);
        geoText.setCenterHor(true);
        geoText.setCenterVer(false);
        geoText.setDy(-LABEL_FONT.getSize() - LABEL_SPACING_PX);
        geoText.getFontSymbol().setColor(GRATICULE_LABEL_COLOR);
        geoText.draw(g2d, scale, false);

        // shorten major meridian on top to create space for label
        double lineEndTop = yTop - (LABEL_FONT.getSize() + 2 * LABEL_SPACING_PX) / scale;

        // draw line
        majorLineStyle(g2d);
        Line2D meridian = new Line2D.Double(meridianX, yBottom, meridianX, lineEndTop);
        g2d.draw(meridian);
    }

    private void drawMinorParallel(Graphics2D g2d, double latDeg, double xLeft, double xRight) {
        double parallelY = yMercator(latDeg);
        Line2D parallel = new Line2D.Double(xLeft, parallelY, xRight, parallelY);
        minorLineStyle(g2d);
        g2d.draw(parallel);
    }

    private void drawMajorParallelAndLabel(Graphics2D g2d, double latDeg, double xLeft, double xRight) {
        double scale = mapComponent.getScaleFactor();
        double parallelY = yMercator(latDeg);

        // format label
        String str = Long.toString(Math.abs(Math.round(latDeg))) + "\u00B0";
        if (latDeg < 0) {
            str += " S";
        } else if (latDeg > 0) {
            str += " N";
        }

        // draw label
        GeoText geoText = new GeoText(str, xLeft, parallelY);
        geoText.setCenterHor(false);
        geoText.setCenterVer(true);
        geoText.setDx(LABEL_SPACING_PX);
        geoText.getFontSymbol().setColor(GRATICULE_LABEL_COLOR);
        geoText.draw(g2d, scale, false);

        // shorten major parallel line on left side to create space for label
        double stringWidth = geoText.getBounds2D(scale).getWidth();
        double lineStartLeft = xLeft + stringWidth + (2 * LABEL_SPACING_PX) / scale;

        // draw line
        majorLineStyle(g2d);
        Line2D parallel = new Line2D.Double(lineStartLeft, parallelY, xRight, parallelY);
        g2d.draw(parallel);
    }

    private void drawGraticule(Graphics2D g2d) {
        // OSM zoom level
        double scale = mapComponent.getScaleFactor();
        int zoom = zoomLevel(scale);
        if (zoom >= MINOR_GRATICULE_SPACING.length) {
            return;
        }

        // distance betweeen two graticule lines in degrees
        double graticuleSpacingDeg = MINOR_GRATICULE_SPACING[zoom];

        g2d.setColor(GRATICULE_COLOR);
        g2d.setFont(LABEL_FONT);

        // without the KEY_STROKE_CONTROL rendering hint the meridian at 0 deg
        // longitude is sometimes not drawn on OS X
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_NORMALIZE);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // find extension of visible area
        Rectangle2D.Double visRect = mapComponent.getVisibleArea();
        MercatorProjection mercator = ProjectionsManager.createWebMercatorProjection();

        // bottom left corner of visible area
        Point2D.Double xyBottomLeft = new Point2D.Double(visRect.getMinX(), visRect.getMinY());
        Point2D.Double lonLatBottomLeftDeg = new Point2D.Double();
        mercator.inverseTransform(xyBottomLeft, lonLatBottomLeftDeg);

        // top right corner of visible area
        Point2D.Double xyTopRight = new Point2D.Double(visRect.getMaxX(), visRect.getMaxY());
        Point2D.Double lonLatTopRightDeg = new Point2D.Double();
        mercator.inverseTransform(xyTopRight, lonLatTopRightDeg);

        // draw meridians
        double firstLon = lonLatBottomLeftDeg.x;
        if (firstLon < 0) {
            firstLon -= firstLon % graticuleSpacingDeg;
        } else {
            firstLon += graticuleSpacingDeg - firstLon % graticuleSpacingDeg;
        }
        double lastLon = lonLatTopRightDeg.x - (lonLatTopRightDeg.x % graticuleSpacingDeg);
        int nbrMeridians = (int) Math.round((lastLon - firstLon) / graticuleSpacingDeg) + 1;
        double yTop = Math.min(MAX_Y, visRect.getMaxY());
        double yBottom = Math.max(-MAX_Y, visRect.getMinY());
        for (int i = 0; i < nbrMeridians; i++) {
            double lonDeg = firstLon + i * graticuleSpacingDeg;
            if (isMajorGraticuleLine(lonDeg, zoom)) {
                drawMajorMeridianAndLabel(g2d, lonDeg, yBottom, yTop);
            } else {
                drawMinorMeridian(g2d, lonDeg, yBottom, yTop);
            }
        }

        // draw parallels
        double minLat = Math.max(lonLatBottomLeftDeg.y, mercator.getMinLatitude());
        double maxLat = Math.min(lonLatTopRightDeg.y, mercator.getMaxLatitude());
        double firstLat = minLat;
        if (firstLat < 0d) {
            firstLat -= firstLat % graticuleSpacingDeg;
        } else {
            firstLat += graticuleSpacingDeg - firstLat % graticuleSpacingDeg;
        }
        double lastLat = maxLat - (maxLat % graticuleSpacingDeg);
        int nbrParallels = (int) Math.round((lastLat - firstLat) / graticuleSpacingDeg) + 1;
        double xLeft = Math.max(-MAX_X, visRect.getMinX());
        double xRight = Math.min(MAX_X, visRect.getMaxX());
        for (int i = 0; i < nbrParallels; i++) {
            double latDeg = firstLat + i * graticuleSpacingDeg;
            if (isMajorGraticuleLine(latDeg, zoom)) {
                drawMajorParallelAndLabel(g2d, latDeg, xLeft, xRight);
            } else {
                drawMinorParallel(g2d, latDeg, xLeft, xRight);
            }
        }
    }

    /**
     * Returns whether a longitude or latitude line is a major line.
     *
     * @param deg the latitude or the longitude
     * @param zoom the OSM zoom level
     * @return true if the line is to be highlighted
     */
    private static boolean isMajorGraticuleLine(double deg, int zoom) {
        if (zoom < MAJOR_GRATICULE_SPACING.length) {
            // distance betweeen two graticule lines in degrees
            double majorGraticuleSpacing = MAJOR_GRATICULE_SPACING[zoom];
            return ((Math.abs(deg) + 0.001) % majorGraticuleSpacing) < 0.01;
        } else {
            return false;
        }
    }

    /**
     * setup stroke and rendering hints for major graticule lines
     *
     * @param g2d graphics destination
     */
    private void majorLineStyle(Graphics2D g2d) {
        double scale = mapComponent.getScaleFactor();
        float strokeWidth = (float) (MAJOR_LINE_WIDTH_PX / scale);
        g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
    }

    /**
     * setup stroke and rendering hints for minor graticule lines
     *
     * @param g2d graphics destination
     */
    private void minorLineStyle(Graphics2D g2d) {
        g2d.setStroke(new BasicStroke(0, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    @Override
    public boolean isPointOnSymbol(Point2D point, double tolDist, double scale) {
        return point.getX() >= -MAX_X && point.getX() <= MAX_X
                && point.getY() >= -MAX_Y && point.getY() <= MAX_Y;
    }

    @Override
    public boolean isIntersectedByRectangle(Rectangle2D rect, double scale) {
        return BOUNDS.intersects(rect);
    }

    @Override
    public PathIterator getPathIterator(AffineTransform affineTransform) {
        return BOUNDS.getPathIterator(affineTransform);
    }

    @Override
    public void move(double dx, double dy) {
    }

    /**
     * @return the showGraticule
     */
    public boolean isShowGraticule() {
        return showGraticule;
    }

    /**
     * @param showGraticule the showGraticule to set
     */
    public void setShowGraticule(boolean showGraticule) {
        this.showGraticule = showGraticule;
    }

    /**
     * @return the showTropics
     */
    public boolean isShowTropics() {
        return showTropics;
    }

    /**
     * @param showTropics the showTropics to set
     */
    public void setShowTropics(boolean showTropics) {
        this.showTropics = showTropics;
    }

    /**
     * @return the showPolarCircles
     */
    public boolean isShowPolarCircles() {
        return showPolarCircles;
    }

    /**
     * @param showPolarCircles the showPolarCircles to set
     */
    public void setShowPolarCircles(boolean showPolarCircles) {
        this.showPolarCircles = showPolarCircles;
    }

}
