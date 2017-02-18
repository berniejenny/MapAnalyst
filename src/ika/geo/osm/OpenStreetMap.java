package ika.geo.osm;

import ika.geo.GeoObject;
import ika.gui.MapComponent;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
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

    public void draw(Graphics2D g2d, double scale, boolean drawSelectionState) {

        if (this.map == null) {
            return;
        }

        // compute OSM zoom level
        double boundsWidthPx = this.bounds.getWidth() * scale;
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

    }

    public boolean isPointOnSymbol(Point2D point, double tolDist, double scale) {
        return false;
    }

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
