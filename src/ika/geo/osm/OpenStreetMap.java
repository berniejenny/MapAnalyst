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
import java.io.IOException;
import java.io.ObjectInputStream;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/**
 * Displays OpenStreetMap tiles
 *
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich; based on OSMMap
 * by Jan Peter Stotz
 * http://www.nabble.com/Java-component-for-displaying-OSM-Maps-td18361634.html
 */
public class OpenStreetMap extends GeoObject implements java.io.Serializable {

    private static final long serialVersionUID = -6328405447473299261L;

    /**
     * Number of cached image tiles. Estimation of required memory:
     * DEF_CACHED_IMAGES * 256 * 256 * 4 [bytes] 200 * 256 * 256 * 4 = 50MB
     */
    private static final int DEF_CACHED_IMAGES = 200;
    /**
     * OSM zoom levels vary between 0 (1 image for the whole globe) and 18
     * (large scale tiles).
     */
    private static final int OSM_MAX_ZOOM = 18;
    private static final int OSM_MIN_ZOOM = 0;
    /**
     * name of image that is displayed when a map tile image is not available
     */
    private static final String MISSING_TILE_IMAGE_NAME = "hourglass.png";
    /**
     * bounding box including all tiles
     */
    protected transient Rectangle2D bounds;
    /**
     * needed to load tiles
     */
    protected transient JobDispatcher jobDispatcher;
    /**
     * cache for tiles
     */
    protected transient MemoryTileCache tileCache;
    /**
     * image rendered when map tile image is not available
     */
    private transient BufferedImage missingTileImage;
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
     * Maximum mapped Cartesian x value
     */
    public static final double MAX_X = Math.PI * R;

    /**
     * Maximum mapped Cartesian y value
     */
    public static final double MAX_Y = Math.log(Math.tan(Math.PI / 4d + 0.5 * MAX_LAT / 180d * Math.PI)) * R;

    private static final double POLAR_CIRCLE_LAT = 66.562944;
    private static final double POLAR_CIRCLE_Y = R * Math.log(Math.tan(Math.PI / 4 + 0.5 * Math.toRadians(POLAR_CIRCLE_LAT)));

    private static final double TROPIC_CIRCLE_LAT = 23.43706;
    private static final double TROPIC_CIRCLE_Y = R * Math.log(Math.tan(Math.PI / 4 + 0.5 * Math.toRadians(TROPIC_CIRCLE_LAT)));

    public OpenStreetMap() {
        this.init();
    }

    public OpenStreetMap(MapComponent map) {
        this.init();
        this.setMap(map);
    }

    public void setMap(MapComponent map) {
        this.map = map;
    }

    private void init() {

        // compute the bounding box including all tiles with a spherical
        // Mercator projection. OSM uses a sphere.
        final double maxLat = Math.toRadians(MAX_LAT);
        final double x = R * Math.PI;
        final double y = R * Math.log(Math.tan(Math.PI / 4 + 0.5 * maxLat));
        bounds = new Rectangle2D.Double(-x, -y, 2 * x, 2 * y);

        // init caching and loading of tiles
        tileCache = new MemoryTileCache();
        tileCache.setCacheSizeMax(DEF_CACHED_IMAGES);
        jobDispatcher = new JobDispatcher(1);

        // load image that is displayed if a map tile image is not available
        try {
            missingTileImage = ImageIO.read(getClass().getResourceAsStream(
                    MISSING_TILE_IMAGE_NAME));
        } catch (IOException e1) {
        }

        this.setSelectable(false);
        this.setName("OpenStreetMap");
    }

    private void readObject(ObjectInputStream stream)
            throws IOException, ClassNotFoundException {

        // read the serializable part of this GeoPath.
        stream.defaultReadObject();

        this.init();
    }

    /**
     * retrieves a tile from the cache. If the tile is not present in the cache
     * a load job is added to the working queue of {@link JobThread}.
     *
     * @param tilex
     * @param tiley
     * @param zoom
     * @return specified tile from the cache or <code>null</code> if the tile
     * was not found in the cache.
     */
    protected Tile getTile(final int tilex, final int tiley, final int zoom) {
        int max = (1 << zoom);
        if (tilex < 0 || tilex >= max || tiley < 0 || tiley >= max) {
            return null;
        }
        Tile tile = tileCache.getTile(tilex, tiley, zoom);
        if (tile == null) {
            tile = new Tile(tilex, tiley, zoom, missingTileImage);
            tileCache.addTile(tile);
        }
        if (!tile.isLoaded()) {
            jobDispatcher.addJob(new Runnable() {

                public void run() {
                    Tile tile = tileCache.getTile(tilex, tiley, zoom);
                    if (tile.isLoaded()) {
                        return;
                    }
                    try {
                        tile.loadTileImage();
                        SwingUtilities.invokeLater(new Runnable() {

                            public void run() {
                                if (map != null) {
                                    map.repaint();
                                }
                            }
                        });

                    } catch (Exception e) {
                        //System.err.println("failed loading " + zoom + "/" + tilex + "/" + tiley + " " + e.getMessage());
                    }
                }
            });
        }
        return tile;
    }

    @Override
    public Rectangle2D getBounds2D() {
        return bounds;
    }

    protected void drawTile(BufferedImage image, Graphics2D g2d,
            double xWC, double yWC, double tileWidthWC, double tileHeightWC) {

        AffineTransform trans = new AffineTransform();
        trans.scale(1, -1);
        trans.translate(xWC, -yWC);
        trans.scale(tileWidthWC / image.getWidth(),
                tileHeightWC / image.getHeight());
        g2d.drawImage(image, trans, null);

    }

    @Override
    public void draw(Graphics2D g2d, double scale, boolean drawSelectionState) {

        if (map == null) {
            return;
        }

        // compute OSM zoom level
        double boundsWidthPx = bounds.getWidth() * scale;
        int zoom = (int) Math.round(log2(boundsWidthPx / Tile.WIDTH));
        if (zoom > OSM_MAX_ZOOM) {
            zoom = OSM_MAX_ZOOM;
        } else if (zoom < OSM_MIN_ZOOM) {
            zoom = OSM_MIN_ZOOM;
        }

        // compute number of tiles of the whole planet
        int tilesH = (int) Math.round(Math.pow(2, zoom));
        int tilesV = tilesH;
        double tileDim = bounds.getWidth() / tilesH;

        // the first and last tiles visible in horizontal and vertical direction
        Rectangle2D.Double visRect = map.getVisibleArea();
        double visBottom = visRect.getMinY();
        double visLeft = visRect.getMinX();
        double visWidth = visRect.getWidth();
        double visHeight = visRect.getHeight();
        int firstRow = (int) ((bounds.getMaxY() - (visBottom + visHeight)) / tileDim);
        firstRow = Math.max(firstRow, 0);
        int firstCol = (int) ((visLeft - bounds.getMinX()) / tileDim);
        firstCol = Math.max(firstCol, 0);
        int lastRow = tilesV - (int) ((visBottom - bounds.getMinY()) / tileDim);
        lastRow = Math.min(tilesV, lastRow);
        int lastCol = tilesH - (int) ((bounds.getMaxX() - (visLeft + visWidth)) / tileDim);
        lastCol = Math.min(tilesH, lastCol);

        // load and draw all visible tiles
        for (int row = firstRow; row < lastRow; row++) {
            double y = this.bounds.getMaxY() - row * tileDim;
            for (int col = firstCol; col < lastCol; col++) {
                double x = this.bounds.getMinX() + col * tileDim;

                // draw the tile
                Tile tile = getTile(col, row, zoom);
                if (tile != null) {
                    drawTile(tile.image, g2d, x, y, tileDim, tileDim);
                }

                /*
            // draw a grid showing the tiling
            Rectangle2D r = new Rectangle2D.Double(x, y-tileDim, tileDim, tileDim);
            rp.g2d.draw(r);
                 */
            }
        }

        drawGraticule(g2d, scale);
    }

    private void drawGraticule(Graphics2D g2d, double scale) {
        // compute OSM zoom level
        double boundsWidthPx = bounds.getWidth() * scale;
        int zoom = (int) Math.round(log2(boundsWidthPx / Tile.WIDTH));

        Rectangle2D.Double visRect = map.getVisibleArea();

        MercatorProjection mercator = ProjectionsManager.createWebMercatorProjection();
        double r = mercator.getEquatorRadius();

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
                double dLonDeg = Math.toDegrees((xy2.x - xy1.x) / r);

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
        g2d.setColor(Color.GRAY);
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
            double meridianX = r * Math.toRadians(lonDeg);
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
            double latRad = Math.toRadians(latDeg);
            double parallelY = r * Math.log(Math.tan(Math.PI / 4 + 0.5 * latRad));
            Line2D parallel = new Line2D.Double(MAX_X, parallelY, -MAX_X, parallelY);
            g2d.draw(parallel);
        }

        // polar circles
        g2d.setColor(new Color(168, 197, 205));
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
        return false;
    }

    public void transform(AffineTransform affineTransform) {
    }

    /**
     * Sets the image that is displayed when a tile image is not available.
     *
     * @param img The image must be 256 x 256 pixels large.
     */
    public void setMissingTileImage(BufferedImage img) {
        if (img.getWidth() != Tile.WIDTH || img.getHeight() != Tile.HEIGHT) {
            throw new IllegalArgumentException();
        }
        this.missingTileImage = img;
    }

    @Override
    public PathIterator getPathIterator(AffineTransform affineTransform) {
        return bounds.getPathIterator(affineTransform);
    }

    @Override
    public void move(double dx, double dy) {
    }

    private static final double log2(double x) {
        return Math.log(x) / Math.log(2.);
    }
}
