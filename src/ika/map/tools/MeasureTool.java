package ika.map.tools;

import com.jhlabs.map.MapMath;
import ika.geo.GeoPath;
import ika.geo.VectorSymbol;
import ika.geo.osm.OpenStreetMap;
import ika.geo.osm.Projector;
import ika.gui.MapComponent;
import ika.mapanalyst.Manager;
import ika.utils.GeometryUtils;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;

/**
 * MeasureTool - a tool to measure distances between two points.
 *
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class MeasureTool extends DoubleBufferedTool {

    /**
     * latitude extent of web Mercator projection
     */
    private static final double MAX_LAT_RAD = Math.toRadians(OpenStreetMap.MAX_LAT);

    /**
     * maximum number of points between start point and end point for drawing
     * shortest distances on great circles
     */
    private static final int MAX_NBR_INTERMEDIATE_WAY_POINTS_ON_GREAT_CIRCLE = 200;

    /**
     * Add a candidate way point to great circle route if the Cartesian distance
     * between the candidate point and the straight line connecting the last
     * point added and the end point of the great circle route is larger than
     * this distance in pixels.
     */
    private static final double PIXEL_TOL = 1;

    /**
     * manager used to determine whether the map uses OSM
     */
    private final Manager manager;

    /**
     * The start position.
     */
    private Point2D.Double dragStartPos;

    /**
     * The current end position.
     */
    private Point2D.Double dragCurrentPos;

    /**
     * A set of MeasureToolListener that will be informed when a new distance
     * has been computed.
     */
    private final ArrayList<MeasureToolListener> listeners = new ArrayList<>();

    /**
     * Create a new instance.
     *
     * @param mapComponent The MapComponent for which this MapTool provides its
     * services.
     */
    public MeasureTool(MapComponent mapComponent, Manager manager) {
        super(mapComponent);
        this.manager = manager;
    }

    @Override
    public void deactivate() {
        reportClearDistance();
        mapComponent.repaint();
    }

    /**
     * Adds a MeasureToolListener.
     *
     * @param listener The MeasureToolListener to add.
     */
    public void addMeasureToolListener(MeasureToolListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        listeners.add(listener);
    }

    /**
     * Removes a MeasureToolListener.
     *
     * @param listener The MeasureToolListener to remove.
     */
    public void removeMeasureToolListener(MeasureToolListener listener) {
        listeners.remove(listener);
    }

    /**
     * Inform all registered MeasureToolListeners of a new distance.
     */
    private void reportDistance() {
        if (dragStartPos == null || dragCurrentPos == null) {
            return;
        }
        for (MeasureToolListener listener : listeners) {
            listener.updateMeasureTool(dragStartPos.x, dragStartPos.y,
                    dragCurrentPos.x, dragCurrentPos.y, mapComponent);
        }
    }

    /**
     * Inform all registered MeasureToolListeners that the distance is not valid
     * anymore.
     */
    private void reportClearDistance() {
        for (MeasureToolListener listener : listeners) {
            listener.clearDistance();
        }
    }

    /**
     * The mouse was pressed down, while this MapTool was the active one.
     *
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     */
    @Override
    public void mouseDown(Point2D.Double point, MouseEvent evt) {
        setMeasureCursor();
        captureBackground();
    }

    /**
     * The mouse starts a drag, while this MapTool was the active one.
     *
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     */
    @Override
    public void startDrag(Point2D.Double point, MouseEvent evt) {
        setMeasureCursor();
        this.dragStartPos = (Point2D.Double) point.clone();
    }

    /**
     * The mouse location changed during a drag, while this MapTool was the
     * active one.
     *
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     */
    @Override
    public void updateDrag(Point2D.Double point, MouseEvent evt) {
        // just in case we didn't get a mousePressed-event
        if (dragStartPos == null) {
            dragStartPos = (Point2D.Double) point.clone();
            setMeasureCursor();
            return;
        }

        // if this is the first time mouseDragged is called, capture the screen.
        if (dragCurrentPos == null) {
            captureBackground();
        }

        dragCurrentPos = (Point2D.Double) point.clone();
        mapComponent.repaint();

        reportDistance();
    }

    /**
     * A drag ends, while this MapTool was the active one.
     *
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     */
    @Override
    public void endDrag(Point2D.Double point, MouseEvent evt) {
        /*
        dragStartPos = null;
        dragCurrentPos = null;
         */
        releaseBackground();
        mapComponent.repaint();
        setDefaultCursor();
    }

    /**
     * Draw the interface elements of this MapTool.
     *
     * @param g2d The destination to draw to.
     */
    @Override
    public void draw(java.awt.Graphics2D g2d) {
        if (dragStartPos == null || dragCurrentPos == null) {
            return;
        }

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);

        final float scale = (float) mapComponent.getScaleFactor();
        GeoPath geoPath = shortestPath(dragStartPos, dragCurrentPos);
        VectorSymbol symbol = new VectorSymbol();
        geoPath.setVectorSymbol(symbol);
        symbol.setFilled(false);
        symbol.setScaleInvariant(true);
        symbol.setStrokeWidth(7f);
        symbol.setStrokeColor(new Color(0.5f, 0, 0f, 0.2f));
        symbol.setCap(BasicStroke.CAP_BUTT);
        geoPath.draw(g2d, scale, false);
        symbol.setStrokeWidth(1f);
        symbol.setStrokeColor(Color.BLACK);
        geoPath.draw(g2d, scale, false);
    }

    /**
     * Returns true if the passed map is showing the "new" reference map and is
     * using OSM.
     *
     * @param mapComponent
     * @return
     */
    private boolean mapUsesOSM(MapComponent mapComponent) {
        boolean osm = "new".equals(mapComponent.getName())
                && manager != null && manager.isUsingOpenStreetMap();
        return osm;
    }

    /**
     * Returns a GeoPath connecting two points. If this map is using OSM, the
     * returned GeoPath is along a great circle.
     *
     * @param start start point
     * @param end end point
     * @return GeoPath connecting start and end points
     */
    private GeoPath shortestPath(Point2D.Double start, Point2D.Double end) {

        boolean osm = mapUsesOSM(mapComponent);
        GeoPath geoPath = new GeoPath();

        // compute great circle path when OSM is used
        if (osm) {
            // convert to spherical coordinates
            double[] p1 = Projector.OSM2Geo(start.x, start.y);
            double[] p2 = Projector.OSM2Geo(end.x, end.y);
            // add start, end, and intermediate points along great circle
            greatCircleWayPoints(geoPath, p1[0], p1[1], p2[0], p2[1]);
        } else {
            geoPath.moveTo(start);
            geoPath.lineTo(end);
        }
        return geoPath;
    }

    /**
     * Great circle way points for use with the web Mercator projection.
     *
     * See https://en.wikipedia.org/wiki/Great-circle_navigation
     *
     * @param geoPath add points to this path
     * @param lon1 longitude of start point
     * @param lat1 latitude of start point
     * @param lon2 longitude of end point
     * @param lat2 latitude of end point
     */
    private void greatCircleWayPoints(GeoPath geoPath, double lon1, double lat1,
            double lon2, double lat2) {

        // clamp coordinates to extent of OSM
        lon1 = Math.max(Math.min(lon1, Math.PI), -Math.PI);
        lat1 = Math.max(Math.min(lat1, MAX_LAT_RAD), -MAX_LAT_RAD);
        lon2 = Math.max(Math.min(lon2, Math.PI), -Math.PI);
        lat2 = Math.max(Math.min(lat2, MAX_LAT_RAD), -MAX_LAT_RAD);

        double dLon = lon2 - lon1;
        double dLat = lat2 - lat1;

        double cosLat1 = Math.cos(lat1);
        double sinLat1 = Math.sin(lat1);
        double cosLat2 = Math.cos(lat2);
        double sinLat2 = Math.sin(lat2);

        // spherical azimuth of start point
        // The spherical azimuth is the angle on the sphere between the meridian 
        // passing through the start point and the great circle connecting the 
        // start point with the end point.
        double sinDLon = Math.sin(dLon);
        double cosDLon = Math.cos(dLon);
        double a1 = Math.atan2(cosLat2 * sinDLon, cosLat1 * sinLat2 - sinLat1 * cosLat2 * cosDLon);
        double sina1 = Math.sin(a1);
        double cosa1 = Math.cos(a1);

        // angular great circle distance between start point and end point
        // The angular great circle distance is the angle measured at the center
        // of the sphere between the start point and the end point.
        double sinDLatHalf = Math.sin(dLat / 2);
        double sinDLonHalf = Math.sin(dLon / 2);
        double r = Math.sqrt(sinDLatHalf * sinDLatHalf + cosLat1 * cosLat2 * sinDLonHalf * sinDLonHalf);
        double sigma12 = 2.0 * Math.asin(r);

        // central angle along great circle to start point
        final double sigma1;
        if (lat1 == 0 && a1 == MapMath.HALFPI) {
            sigma1 = 0;
        } else {
            sigma1 = Math.atan2(Math.tan(lat1), cosa1);
        }

        // spherical azimuth of point A, where the great circle that passes 
        // through the start point and the end point intersects the equator
        double a0 = Math.atan2(sina1 * cosLat1, Math.sqrt(cosa1 * cosa1 + sina1 * sina1 * sinLat1 * sinLat1));

        // longitude of point A
        double sinA0 = Math.sin(a0);
        double cosA0 = Math.cos(a0);
        double lon0 = lon1 - Math.atan2(sinA0 * Math.sin(sigma1), Math.cos(sigma1));

        // convert the start point to web Mercator and add it to the line
        Point2D startPtOSM = Projector.geo2OSM(Math.toDegrees(lon1), Math.toDegrees(lat1));
        geoPath.moveTo(startPtOSM);
        Point2D endPtOSM = Projector.geo2OSM(Math.toDegrees(lon2), Math.toDegrees(lat2));

        // add intermediate points
        double dSigma = sigma12 / (MAX_NBR_INTERMEDIATE_WAY_POINTS_ON_GREAT_CIRCLE + 1);
        double prevLon = lon1;
        Point2D lastPointAddedOSM = startPtOSM;
        for (int i = 1; i <= MAX_NBR_INTERMEDIATE_WAY_POINTS_ON_GREAT_CIRCLE; i++) {
            // central angle along great circle from A to intermediate point
            double sigma = sigma1 + i * dSigma;
            double cosSigma = Math.cos(sigma);
            double sinSigma = Math.sin(sigma);

            // spherical coordinates of intermediate point
            double lon = Math.atan2(sinA0 * sinSigma, cosSigma) + lon0;
            lon = MapMath.normalizeLongitude(lon);
            double lat = Math.atan2(cosA0 * sinSigma, Math.sqrt(cosSigma * cosSigma + sinA0 * sinA0 * sinSigma * sinSigma));
            lat = clampLatToWebMercator(lat);

            // convert the intermediate way point to web Mercator
            Point2D wayPtOSM = Projector.geo2OSM(Math.toDegrees(lon), Math.toDegrees(lat));

            // test wether line crosses antimeridian
            if (Math.abs(lon - prevLon) > Math.PI) {
                // add end point of first line
                double lonEnd = lon < 0 ? Math.PI : -Math.PI;
                double cotA0 = 1 / Math.tan(a0);
                lat = Math.atan(cotA0 * Math.sin(lonEnd - lon0));
                lat = clampLatToWebMercator(lat);
                Point2D osm = Projector.geo2OSM(Math.toDegrees(lonEnd), Math.toDegrees(lat));
                geoPath.lineTo(osm);

                // add start point of second line
                double lonStart = -lonEnd;
                lat = Math.atan(cotA0 * Math.sin(lonStart - lon0));
                lat = clampLatToWebMercator(lat);
                osm = Projector.geo2OSM(Math.toDegrees(lonStart), Math.toDegrees(lat));
                geoPath.moveTo(osm);
                lastPointAddedOSM = startPtOSM;
            }
            double distOSM = GeometryUtils.distanceToSegment(lastPointAddedOSM, endPtOSM, wayPtOSM);
            double distPixel = distOSM * mapComponent.getScaleFactor();
            if (distPixel > PIXEL_TOL) {
                geoPath.lineTo(wayPtOSM);
                lastPointAddedOSM = wayPtOSM;
            }
            prevLon = lon;
        }

        // add end point to the line
        geoPath.lineTo(endPtOSM);
    }

    /**
     * Clamp latitude to latitude extent of web Mercator.
     *
     * @param lat latitude in radians
     * @return clamped latitude in radians
     */
    private double clampLatToWebMercator(double lat) {
        return Math.max(Math.min(lat, MAX_LAT_RAD), -MAX_LAT_RAD);
    }

    /**
     * Utility method to change the cursor to a cross-hair cursor.
     */
    private void setMeasureCursor() {
        mapComponent.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
    }
}
