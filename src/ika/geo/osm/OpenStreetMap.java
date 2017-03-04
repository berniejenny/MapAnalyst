package ika.geo.osm;

import com.jhlabs.map.proj.MercatorProjection;
import ika.geo.GeoObject;
import ika.gui.MapComponent;
import ika.proj.ProjectionsManager;
import java.awt.BasicStroke;
import java.awt.Color;
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
     * OpenStreetMap JMapViewer access point
     */
    private transient TileController tileController;

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
     * parent map component
     */
    protected transient MapComponent map;

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
     * Color for drawing polar and tropic circles
     */
    private static final Color POLAR_TROPIC_COLOR = new Color(150, 180, 150);

    /**
     * Color for drawing graticule
     */
    private static final Color GRATICULE_COLOR = new Color(150, 155, 180);

    /**
     * size of a map tile in pixels
     */
    private static final int TILE_SIZE = 256;

    /**
     * listener for scale change events to stop loading tiles when the OSM zoom
     * level changes
     */
    PropertyChangeListener scaleChangeListener = new PropertyChangeListener() {
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

    public OpenStreetMap() {
        init();
    }

    public OpenStreetMap(MapComponent map) {
        init();
        setMap(map);
    }

    public final void setMap(MapComponent map) {
        this.map = map;
        map.addScaleChangePropertyChangeListener(scaleChangeListener);
    }

    private void init() {
        setSelectable(false);
        setName("OpenStreetMap");
        TileSource tileSource = new OsmTileSource.Mapnik();
        TileCache cache = new MemoryTileCache(NBR_CACHED_IMAGES);
        tileController = new TileController(tileSource, cache, this);
    }

    /**
     * OSM map is being removed from MapComponent
     */
    public void dispose() {
        tileController.cancelOutstandingJobs();
        map.removeScaleChangePropertyChangeListener(scaleChangeListener);
    }
    
    private void readObject(java.io.ObjectInputStream stream) throws IOException, ClassNotFoundException {
        // read this object
        stream.defaultReadObject();

        // initialize transient TileController
        init();
    }

    @Override
    public void tileLoadingFinished(Tile tile, boolean success) {
        tile.setLoaded(success);
        map.repaint();
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

    private int zoomLevel(double mapScale) {
        double boundsWidthPx = BOUNDS.getWidth() * mapScale;
        return (int) Math.round(Math.log((boundsWidthPx / TILE_SIZE)) / Math.log(2.));
    }

    @Override
    public void draw(Graphics2D g2d, double scale, boolean drawSelectionState) {

        if (map == null) {
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
        Rectangle2D.Double visRect = map.getVisibleArea();
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

        drawGraticule(g2d, scale);
    }

    private void drawGraticule(Graphics2D g2d, double scale) {
        // compute OSM zoom level
        int zoom = zoomLevel(scale);

        Rectangle2D.Double visRect = map.getVisibleArea();

        MercatorProjection mercator = ProjectionsManager.createWebMercatorProjection();

        double dDeg;
        switch (zoom) {
            case 0:
                dDeg = 90;
                break;
            case 1:
                dDeg = 45;
                break;
            case 2:
                dDeg = 30;
                break;
            case 3:
                dDeg = 15;
                break;
            case 4:
                dDeg = 10;
                break;
            case 5:
                dDeg = 5;
                break;
            case 6:
                dDeg = 2;
                break;
            case 7:
                dDeg = 2;
                break;
            case 8:
                dDeg = 1;
                break;
            default:
                // find a spacing between meridians that results in one meridian per approximately 150 pixels
                Point2D.Double xy1 = map.userToWorldSpace(new java.awt.Point(0, 0));
                Point2D.Double xy2 = map.userToWorldSpace(new java.awt.Point(150, 0));
                // inverse projection of horizontal distance to longitude
                double dLonDeg = Math.toDegrees((xy2.x - xy1.x) / R);

                final double[] bases = new double[]{5, 2, 1};
                // scale the cell size such that it is > 1
                double s = 1; // scale factor
                double dLonDeg_s = dLonDeg;
                while (dLonDeg_s < 1d) {
                    dLonDeg_s *= 10d;
                    s /= 10d;
                }

                // compute the number of digits of the integral part of the scaled value
                double ndigits = (int) Math.floor(Math.log10(dLonDeg_s));

                // find the index into bases for the first limit after dLongDeg
                int baseID = 0;
                for (int i = 0; i < bases.length; ++i) {
                    if (dLonDeg_s >= bases[i]) {
                        baseID = i;
                        break;
                    }
                }
                dDeg = bases[baseID] * Math.pow(10, ndigits) * s;
        }

        g2d.setStroke(new BasicStroke(0, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
        g2d.setColor(GRATICULE_COLOR);
        // without the KEY_STROKE_CONTROL rendering hint the meridian at 0 deg longitude is sometimes not drawn on OS X
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF);

        // find map extension in degrees
        Point2D.Double lonLatBottomLeftDeg = new Point2D.Double();
        mercator.inverseTransform(new Point2D.Double(visRect.getMinX(), visRect.getMinY()), lonLatBottomLeftDeg);
        Point2D.Double lonLatTopRightDeg = new Point2D.Double();
        mercator.inverseTransform(new Point2D.Double(visRect.getMaxX(), visRect.getMaxY()), lonLatTopRightDeg);

        // meridians
        double firstLon = lonLatBottomLeftDeg.x;
        if (firstLon < 0) {
            firstLon -= firstLon % dDeg;
        } else {
            firstLon += dDeg - firstLon % dDeg;
        }
        double lastLon = lonLatTopRightDeg.x - (lonLatTopRightDeg.x % dDeg);
        int nbrMeridians = (int) Math.round((lastLon - firstLon) / dDeg) + 1;
        for (int i = 0; i < nbrMeridians; i++) {
            double lonDeg = firstLon + i * dDeg;
            double meridianX = R * Math.toRadians(lonDeg);
            Line2D meridian = new Line2D.Double(meridianX, -MAX_Y, meridianX, MAX_Y);
            g2d.draw(meridian);
        }

        // parallels
        double minLat = Math.max(lonLatBottomLeftDeg.y, mercator.getMinLatitude());
        double maxLat = Math.min(lonLatTopRightDeg.y, mercator.getMaxLatitude());
        double firstLat = minLat;
        if (firstLat < 0d) {
            firstLat -= firstLat % dDeg;
        } else {
            firstLat += dDeg - firstLat % dDeg;
        }
        double lastLat = maxLat - (maxLat % dDeg);
        int nbrParallels = (int) Math.round((lastLat - firstLat) / dDeg) + 1;
        for (int i = 0; i < nbrParallels; i++) {
            double latDeg = firstLat + i * dDeg;
            double parallelY = yMercator(latDeg);
            Line2D parallel = new Line2D.Double(MAX_X, parallelY, -MAX_X, parallelY);
            g2d.draw(parallel);
        }

        // polar circles
        g2d.setColor(POLAR_TROPIC_COLOR);
        g2d.draw(new Line2D.Double(MAX_X, POLAR_CIRCLE_Y, -MAX_X, POLAR_CIRCLE_Y));
        g2d.draw(new Line2D.Double(MAX_X, -POLAR_CIRCLE_Y, -MAX_X, -POLAR_CIRCLE_Y));
        // tropic circles
        g2d.draw(new Line2D.Double(MAX_X, TROPIC_CIRCLE_Y, -MAX_X, TROPIC_CIRCLE_Y));
        g2d.draw(new Line2D.Double(MAX_X, -TROPIC_CIRCLE_Y, -MAX_X, -TROPIC_CIRCLE_Y));

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
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

}
