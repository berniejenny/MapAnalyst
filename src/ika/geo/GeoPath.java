package ika.geo;

import java.awt.geom.*;
import java.awt.*;
import java.io.*;
import ika.utils.*;

/**
 * GeoPath - a class that models vector data. It can treat straight lines and
 * bezier curves.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class GeoPath extends GeoObject implements Serializable {

    private static final long serialVersionUID = 7350986432785586245L;
   
    /**
     * A GeneralPath that stores the geometry of this GeoPath.
     */
    transient private GeneralPath path; // cannot be serialized!
    
    /**
     * A VectorSymbol that stores the graphic attributes of this GeoPath.
     */
    private VectorSymbol symbol;
    
    /** Creates a new instance of GeoPath */
    public GeoPath() {
        this.path = new java.awt.geom.GeneralPath();
        this.symbol = new VectorSymbol();
    }
    
    private void writeObject(ObjectOutputStream stream) throws IOException {
        
        // write the serializable part of this GeoPath.
        stream.defaultWriteObject();
        
        // write the GeneralPath, which is not serializable
        final int nbrSegments = this.getNumberOfSegments();
        stream.writeInt(nbrSegments);
        PathIterator pi = this.getPathIterator(null);
        double [] coords = new double [6];
        int segmentType, nbrOfCoordsToWrite;
        while (pi.isDone() == false) {
            segmentType = pi.currentSegment(coords);
            
            // write segment type
            stream.writeInt(segmentType);
            switch (segmentType) {
                case PathIterator.SEG_CLOSE:
                    nbrOfCoordsToWrite = 0;
                    break;
                case PathIterator.SEG_LINETO:
                    nbrOfCoordsToWrite = 2;
                    break;
                case PathIterator.SEG_MOVETO:
                    nbrOfCoordsToWrite = 2;
                    break;
                case PathIterator.SEG_QUADTO:
                    nbrOfCoordsToWrite = 4;
                    break;
                case PathIterator.SEG_CUBICTO:
                    nbrOfCoordsToWrite = 6;
                    break;
                default:
                    throw new IllegalArgumentException();
            }
            
            // write the coordinates
            for (int i = 0; i < nbrOfCoordsToWrite; i++)
                stream.writeDouble(coords[i]);
            
            // move to next segment
            pi.next();
        }
    }
    
    private void readObject(ObjectInputStream stream)
    throws IOException, ClassNotFoundException {
        
        // read the serializable part of this GeoPath.
        stream.defaultReadObject();
        
        // read the GeneralPath, which was serialized by writeObject()
        this.path = new GeneralPath();
        final int nbrSegments = stream.readInt();
        for (int i = 0; i < nbrSegments; i++) {
            switch (stream.readInt()) {
                case PathIterator.SEG_CLOSE:
                    this.path.closePath();
                    break;
                case PathIterator.SEG_LINETO:
                    this.path.lineTo((float)stream.readDouble(),
                            (float)stream.readDouble());
                    break;
                case PathIterator.SEG_MOVETO:
                    this.path.moveTo((float)stream.readDouble(),
                            (float)stream.readDouble());
                    break;
                case PathIterator.SEG_QUADTO:
                    this.path.quadTo((float)stream.readDouble(),
                            (float)stream.readDouble(),
                            (float)stream.readDouble(),
                            (float)stream.readDouble());
                    break;
                case PathIterator.SEG_CUBICTO:
                    this.path.curveTo((float)stream.readDouble(),
                            (float)stream.readDouble(),
                            (float)stream.readDouble(),
                            (float)stream.readDouble(),
                            (float)stream.readDouble(),
                            (float)stream.readDouble());
                    break;
                default:
                    throw new IllegalArgumentException();
            }
        }
    }
    
    /**
     * Append a move-to command to the current path. Places the virtual pen at the
     * specified location without drawing any line.
     * @param x The location to move to.
     * @param y The location to move to.
     */
    public void moveTo(float x, float y) {
        path.moveTo(x, y);
    }
    
    /**
     * Append a move-to command to the current path. Places the virtual pen at the
     * specified location without drawing any line.
     * @param xy An array containing the x and the y coordinate.
     */
    public void moveTo(float[] xy) {
        path.moveTo(xy[0], xy[1]);
    }
    
    /**
     * Append a move-to command to the current path. Places the virtual pen at the
     * specified location without drawing any line.
     * @param point A point containing the x and the y coordinate.
     */
    public void moveTo(Point2D point) {
        final float x = (float)point.getX();
        final float y = (float)point.getY();
        path.moveTo(x, y);
    }
    
    /**
     * Draws a line from the current location of the pen to the specified location. Before
     * calling lineTo, moveTo must be called. Alternatively, use moveOrLineTo that makes
     * sure moveTo is called before lineTo (or quadTo, resp. curveTo).
     * @param x The end point of the new line segment.
     * @param y The end point of the new line segment.
     */
    public void lineTo(float x, float y) {
        path.lineTo(x, y);
    }
    
    /**
     * Draws a line from the current location of the pen to the specified location. Before
     * calling lineTo, moveTo must be called. Alternatively, use moveOrLineTo that makes
     * sure moveTo is called before lineTo (or quadTo, resp. curveTo).
     * @param xy An array containing the x and the y coordinate.
     */
    public void lineTo(float[] xy) {
        path.lineTo(xy[0], xy[1]);
    }
    
    /**
     * Draws a line from the current location of the pen to the specified location. Before
     * calling lineTo, moveTo must be called. Alternatively, use moveOrLineTo that makes
     * sure moveTo is called before lineTo (or quadTo, resp. curveTo).
     * @param point A point containing the x and the y coordinate.
     */
    public void lineTo(Point2D point) {
        final float x = (float)point.getX();
        final float y = (float)point.getY();
        path.lineTo(x, y);
    }
    
    /**
     * Moves the virtual pen to the specified location if this is the first call that
     * changes the geometry. If this is not the first geometry changing call, a straight
     * line is drawn to the specified location.
     * @param x The end point of the new line segment, or the location to move to.
     * @param y The end point of the new line segment, or the location to move to.
     */
    public void moveOrLineTo(float x, float y) {
        if (this.hasOneOrMorePoints())
            path.lineTo(x, y);
        else
            path.moveTo(x, y);
    }
    
    /**
     * Appends a quadratic bezier curve to this GeoPath.
     * @param x1 The location of the control point that is not on the curve.
     * @param y1 The location of the control point that is not on the curve.
     * @param x2 The location of the end point of the new curve segment.
     * @param y2 The location of the control point that is not on the curve.
     */
    public void quadTo(float x1, float y1, float x2, float y2) {
        path.quadTo(x1, y1, x2, y2);
    }
    
    /**
     * Appends a cubic bezier curve to this GeoPath.
     * @param x1 The location of the first control point that is not on the curve.
     * @param y1 The location of the first control point that is not on the curve.
     * @param x2 The location of the second control point that is not on the curve.
     * @param y2 The location of the second control point that is not on the curve.
     * @param x3 The location of the end point of the new curve segment.
     * @param y3 The location of the end point of the new curve segment.
     */
    public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        path.curveTo(x1, y1, x2, y2, x3, y3);
    }
    
    /**
     * Appends a cubic bezier curve to this GeoPath.
     * @param ctrl1 The location of the first control point that is not on the curve.
     * @param ctrl2 The location of the second control point that is not on the curve.
     * @param end The location of the end point of the new curve segment.
     */
    public void curveTo(Point2D ctrl1, Point2D ctrl2, Point2D end) {
        final float ctrl1x = (float)ctrl1.getX();
        final float ctrl1y = (float)ctrl1.getY();
        final float ctrl2x = (float)ctrl2.getX();
        final float ctrl2y = (float)ctrl2.getY();
        final float endx = (float)end.getX();
        final float endy = (float)end.getY();
        
        path.curveTo(ctrl1x, ctrl1y, ctrl2x, ctrl2y, endx, endy);
    }
    
    /**
     * Closes the path by connecting the last point with the first point using a straight line.
     */
    public void closePath() {
        path.closePath();
    }
    
    /**
     * Constructs a path from a series of points that are connected by straight
     * lines.
     */
    public void straightLines(Point2D [] points) {
        path.reset();
        if (points.length >= 1) {
            path.moveTo((float) points[0].getX(), (float) points[0].getY());
            for (int i = 1; i < points.length; i++) {
                path.lineTo((float) points[i].getX(), (float) points[i].getY());
            }
        }
    }
    
    public void straightLines(double [][] points, int firstPoint, int nbrPoints) {
        path.reset();
        if (points.length >= 1) {
            final int lastPoint = firstPoint + nbrPoints;
            path.moveTo((float) points[firstPoint][0], (float) points[firstPoint][1]);
            for (int i = firstPoint + 1; i < lastPoint; i++) {
                path.lineTo((float) points[i][0], (float) points[i][1]);
            }
        }
    }
    
    /**
     * Constructs a bezier control point for two straight lines that meet in a point.
     * The control point lies in backward direction from point 1  towards point 0.
     */
    private void bezierPoint(
            double p0x, double p0y,
            double p1x, double p1y,
            double p2x, double p2y,
            float [] controlPoint,
            double smoothness) {
        
        final double F = 0.39;
        
        // length of the line connecting the previous point P0 with the current
        // point P1.
        final double length = GeometryUtils.length(p1x, p1y, p2x, p2y);
        
        // unary vector from P1 to P0.
        double dx1 = p0x - p1x;
        double dy1 = p0y - p1y;
        final double l1 = Math.sqrt(dx1*dx1+dy1*dy1);
        dx1 /= l1;
        dy1 /= l1;
        if (Double.isNaN(dx1) || Double.isNaN(dy1)
        || Double.isInfinite(dx1) || Double.isInfinite(dy1)) {
            controlPoint[0] = (float)p0x;
            controlPoint[1] = (float)p1y;
            return;
        }
        
        // unary vector from P2 to P1.
        double dx2 = p1x - p2x;
        double dy2 = p1y - p2y;
        final double l2 = Math.sqrt(dx2*dx2+dy2*dy2);
        dx2 /= l2;
        dy2 /= l2;
        
        // direction of tangent where bezier control point lies on.
        double tx = dx1 + dx2;
        double ty = dy1 + dy2;;
        final double l = Math.sqrt(tx*tx+ty*ty);
        tx /= l;
        ty /= l;
        
        // first control point
        controlPoint[0] = (float)(p1x - length * F * smoothness * tx);
        controlPoint[1] = (float)(p1y - length * F * smoothness * ty);
    }
    
    public void smooth(double smoothness, double[][] points,
            int firstPoint, int nbrPoints) {
        
        if (smoothness <= 0. || NumericalUtils.numbersAreClose(0., smoothness)) {
            straightLines(points, firstPoint, nbrPoints);
            return;
        }
        
        final double F = 0.39;
        final int lastPoint = firstPoint + nbrPoints;
        
        if (points[0].length < 2)
            throw new IllegalArgumentException();
        
        path.reset();
        
        final boolean closePath = NumericalUtils.numbersAreClose(
                points[firstPoint][0], points[lastPoint-1][0])
                && NumericalUtils.numbersAreClose(
                points[firstPoint][1], points[lastPoint-1][1]);
        
        double prevX = points[firstPoint][0];
        double prevY = points[firstPoint][1];
        
        float[] ctrlP1 = new float [2];
        float[] ctrlP2 = new float [2];
        
        // move to first point
        path.moveTo((float)points[firstPoint][0], (float)points[firstPoint][1]);
        
        for (int i = firstPoint+1; i < lastPoint - 1; i++) {
            
            // previous point P0
            final double x0 = points[i-1][0];
            final double y0 = points[i-1][1];
            
            // current point P1
            final double x1 = points[i][0];
            final double y1 = points[i][1];
            
            // next point P2
            final double x2 = points[i+1][0];
            final double y2 = points[i+1][1];
            
            bezierPoint(prevX, prevY, x0, y0, x1, y1, ctrlP1, smoothness);
            bezierPoint(x2, y2, x1, y1, x0, y0, ctrlP2, smoothness);
            
            // add a bezier line segment to the path
            path.curveTo(ctrlP1[0], ctrlP1[1], ctrlP2[0], ctrlP2[1], (float)x1, (float)y1);
            prevX = x0;
            prevY = y0;
        }
        
        final double x0 = points[lastPoint-1][0];
        final double y0 = points[lastPoint-1][1];
        bezierPoint(x0, y0, x0, y0, prevX, prevY, ctrlP1, smoothness);
        path.curveTo(ctrlP1[0], ctrlP1[1], (float)x0, (float)y0, (float)x0, (float)y0);
    }
    
    public void circle(float cx, float cy, float r) {
        // Build a Bezier path that approximates a full circle.
        // Based on an web-article by G. Adam Stanislav:
        // "Drawing a circle with BŽzier Curves"
        if (r <= 0.f)
            throw new IllegalArgumentException();
        
        this.reset();
        
        final float kappa = (float)((Math.sqrt(2.) - 1.) * 4. / 3.);
        final float l = r * kappa;
        
        // move to top center
        this.moveTo(cx, cy + r);
        // I. quadrant
        this.curveTo(cx + l, cy + r, cx + r, cy + l, cx + r, cy);
        // II. quadrant
        this.curveTo(cx + r, cy - l, cx + l, cy - r, cx, cy - r);
        // III. quadrant
        this.curveTo(cx - l, cy - r, cx - r, cy - l, cx - r, cy);
        // IV. quadrant
        this.curveTo(cx - r, cy + l, cx - l, cy + r, cx, cy + r);
        
        this.closePath();
    }
    
    public void rectangle(Rectangle2D rect) {
        if (rect == null)
            throw new IllegalArgumentException();
        
        this.reset();
        
        final float xMin = (float)rect.getMinX();
        final float xMax = (float)rect.getMaxX();
        final float yMin = (float)rect.getMinY();
        final float yMax = (float)rect.getMaxY();
        this.moveTo(xMin, yMin);
        this.lineTo(xMax, yMin);
        this.lineTo(xMax, yMax);
        this.lineTo(xMin, yMax);
        this.closePath();
    }
    
    public void reset() {
        path.reset();
    }
    
    /**
     * Removes the last point of the path that was added with moveto, lineto, etc.
     */
    public void removeLastPoint() {
        // copy the whole path to a new instance, except the last point.
        
        PathIterator pi = path.getPathIterator(null);
        
        // make sure there is at least one point
        if (pi.isDone() == true)
            return;
        
        GeneralPath newPath = new GeneralPath();
        float[] current = new float [6];
        float[] next = new float [6];
        
        // ask for the first point
        int currSegmentType = pi.currentSegment(current);
        while (pi.isDone() == false) {
            
            // get the next point
            int nextSegmentType = pi.currentSegment(next);
            
            switch (currSegmentType) {
                case PathIterator.SEG_MOVETO:
                    newPath.moveTo(current[0], current[1]);
                    break;
                    
                case PathIterator.SEG_LINETO:
                    newPath.lineTo(current[0], current[1]);
                    break;
                    
                case PathIterator.SEG_QUADTO:
                    newPath.quadTo(current[0], current[1], current[2], current[3]);
                    break;
                    
                case PathIterator.SEG_CUBICTO:
                    newPath.curveTo(current[0], current[1], current[2], current[3],
                            current[4], current[5]);
                    break;
                    
                case PathIterator.SEG_CLOSE:
                    newPath.closePath();
                    break;
                    
            }
            
            // swap the current point with the next point
            currSegmentType = nextSegmentType;
            for (int i = 0; i < 6; i++)
                current[i] = next[i];
            
            pi.next();
        }
        
        // swap the old path with the shortened one.
        this.path = newPath;
    }
    
    /**
     * Appends the geometry contained by a PathIterator to this GeoPath.
     * @param pi The PathIterator to append.
     * @param connect If true, the currently existing geometry is connected with the new geometry.
     */
    public void append(java.awt.geom.PathIterator pi, boolean connect) {
        path.append(pi, connect);
    }
    
    /**
     * Appends the geometry contained in a GeoPath to this GeoPath.
     * @param geoPath The GeoPath to append.
     * @param connect If true, the currently existing geometry is connected with the new geometry.
     */
    public void append(GeoPath geoPath, boolean connect) {
        path.append(geoPath.getPath(), connect);
    }
    
    /**
     * Appends the geometry contained by a Shape object to this GeoPath.
     * @param s The Shape to append.
     * @param connect If true, the currently existing geometry is connected with the new geometry.
     */
    public void append(java.awt.Shape s, boolean connect) {
        path.append(s, connect);
    }
    
    private class PathSegment {
        public float[] coords;
        int id;
    }
    
    /**
     * Only for straight open lines!
     */
    public void invertDirection() {
        
        PathIterator pathIterator = this.getPathIterator(null);
        if (pathIterator == null)
            return;
        
        java.util.Vector segments = new java.util.Vector();
        
        while (!pathIterator.isDone()) {
            PathSegment ps = new PathSegment();
            ps.coords =  new float [6];
            ps.id = pathIterator.currentSegment(ps.coords);
            segments.add(ps);
            pathIterator.next();
        }
        
        if (segments.size() == 0)
            return;
        
        this.path.reset();
        
        PathSegment ps = (PathSegment)(segments.get(segments.size()-1));
        this.path.moveTo(ps.coords[0], ps.coords[1]);
        
        boolean firstMove = false;
        for (int i = segments.size()-2; i > 0; --i) {
            ps = (PathSegment)(segments.get(i));
            switch (ps.id) {
                case PathIterator.SEG_MOVETO:
                    this.path.moveTo(ps.coords[0], ps.coords[1]);
                    break;
                    
                case PathIterator.SEG_LINETO:
                    this.path.lineTo(ps.coords[0], ps.coords[1]);
                    break;
                    
                /*
                 case PathIterator.SEG_QUADTO:
                    this.path.quadTo(ps.coords[0], ps.coords[1], ps.coords[2], ps.coords[3]);
                    break;
                 
                case PathIterator.SEG_CUBICTO:
                    this.path.curveTo(ps.coords[0], ps.coords[1], ps.coords[2], ps.coords[3],
                            ps.coords[4], ps.coords[5]);
                    break;
                 
                 
                case PathIterator.SEG_CLOSE:
                    this.path.closePath();
                    break;
                 */
            }
        }
        // treat initial moveto
        ps = (PathSegment)(segments.get(0));
        this.path.lineTo(ps.coords[0], ps.coords[1]);
    }
    
    /**
     * Converts all bezier lines to straight lines.
     * @param flatness The maximum distance between the smooth bezier curve and
     * the new straight lines approximating the bezier curve.
     */
    public void flatten(double flatness) {
        this.flatten(flatness, this);
    }
    
    /**
     * Converts all bezier lines of a GeneralPath to straight lines, and stores
     * the resulting path in a other GeneralPath.
     * @param flatness The maximum distance between the smooth bezier curve and
     * the new straight lines approximating the bezier curve.
     * @param generalPath The GeneralPath that will receive the flattened path. If null
     * a new GeneralPath will be created.
     * @return Returns the passed generalPath if not null, or a new GeneralPath otherwise.
     */
    public GeneralPath flatten(double flatness, GeneralPath generalPath) {
        PathIterator pathIterator = this.getPathIterator(null, flatness);
        if (generalPath == null)
            generalPath = new GeneralPath();
        float coords[] = new float [6];
        while (!pathIterator.isDone()) {
            int id = pathIterator.currentSegment(coords);
            switch (id) {
                case PathIterator.SEG_CLOSE:
                    generalPath.closePath();
                    break;
                case PathIterator.SEG_LINETO:
                    generalPath.lineTo(coords[0], coords[1]);
                    break;
                case PathIterator.SEG_MOVETO:
                    generalPath.moveTo(coords[0], coords[1]);
                    break;
                /*case PathIterator.SEG_QUADTO:
                    generalPath.quadTo(coords[0], coords[1],
                            coords[2], coords[3]);
                    break;
                case PathIterator.SEG_CUBICTO:
                    generalPath.curveTo(coords[0], coords[1],
                            coords[2], coords[3],
                            coords[4], coords[5]);
                    break;
                 */
            }
            pathIterator.next();
        }
        return generalPath;
    }
    
    /**
     * Converts all bezier lines to straight lines, and stores the resulting path
     * in an other GeoPath.
     * @param flatness The maximum distance between the smooth bezier curve and
     * the new straight lines approximating the bezier curve.
     * @param geoPath The GeoPath that will receive the flattened path. If null
     * a new GeoPath will be created.
     * @return Returns the passed geoPath if not null, or a new GeoPath otherwise.
     */
    public GeoPath flatten(double flatness, GeoPath geoPath) {
        GeneralPath generalPath = this.flatten(flatness, (GeneralPath)null);
        
        // create a new GeoPath if none was passed
        if (geoPath == null) {
            geoPath = new GeoPath();
            geoPath.setVectorSymbol(this.symbol.copy());
        }
        
        // assign flattened path to geoPath
        geoPath.path = generalPath;
        return geoPath;
    }
    
    public double[][] getFirstFlattenedPolygon(double flatness) {
        GeneralPath generalPath = this.flatten(flatness, (GeneralPath)null);
        if (generalPath == null)
            return null;
        
        // count number of points in flattened path
        int nbrPts = 0;
        PathIterator pathIterator = generalPath.getPathIterator(null, flatness);
        while (!pathIterator.isDone()) {
            pathIterator.next();
            nbrPts++;
        }
        
        // allocate memory for coordinates
        double[][] pts = new double[nbrPts][2];
        
        // copy points
        int ptID = 0;
        pathIterator = generalPath.getPathIterator(null, flatness);
        double coords[] = new double[6];
        while (!pathIterator.isDone()) {
            int id = pathIterator.currentSegment(coords);
            switch (id) {
                case PathIterator.SEG_CLOSE:
                    pts[ptID][0] = pts[0][0];
                    pts[ptID][1] = pts[0][1];
                    return pts;
                case PathIterator.SEG_LINETO:
                    pts[ptID][0] = coords[0];
                    pts[ptID][1] = coords[1];
                    break;
                case PathIterator.SEG_MOVETO:
                    if (ptID > 0)
                        return pts;
                    pts[ptID][0] = coords[0];
                    pts[ptID][1] = coords[1];
                    break;
            }
            pathIterator.next();
            ptID++;
        }
        return pts;
    }
    
    /**
     * Returns true if this GeoPath contains at least one point.
     * @return True if number of points > 0, false otherwise.
     */
    public boolean hasOneOrMorePoints() {
        final PathIterator pi = this.path.getPathIterator(null);
        return (pi.isDone() == false);
    }
    
    /**
     * Returns true if this GeoPath contains at least one segment of a line.
     * <b>Currently supposes that there are no intermediate moveto commands!</b>
     * @return True if number of segments > 0, false otherwise.
     */
    public boolean hasOneOrMoreSegments() {
        PathIterator pi = this.path.getPathIterator(null);
        if (pi.isDone() == false) {
            // there is at least one point. Move over it.
            pi.next();
            // if there is one more point, there is also at least one segment.
            if (pi.isDone() == false)
                return true;
        }
        return false;
    }
    
    /**
     * Returns the number of segments that build this GeoPath.
     * @return The number of segments.
     */
    public int getNumberOfSegments() {
        PathIterator pi = this.path.getPathIterator(null);
        int nbrSegments = 0;
        while (pi.isDone() == false) {
            nbrSegments++;
            pi.next();
        }
        return nbrSegments;
    }
    
    /**
     * Returns a reference on the vector symbol that stores the graphic attributes
     * used to draw this GeoPath.
     * @return The VectorSymbol used to draw this GeoPath.
     */
    public VectorSymbol getVectorSymbol() {
        return symbol;
    }
    
    /**
     * Set the VectorSymbol that stores the graphic attributes used to draw this
     * GeoPath. The VectorSymbol is not copied, but simply a reference to it is retained.
     * @param symbol The new VectorSymbol.
     */
    public void setVectorSymbol(VectorSymbol symbol) {
        this.symbol = symbol;
    }
    
    /**
     * Returns a PathIterator that can be used to draw this GeoPath or iterate over its
     * geometry.
     * @param affineTransform An AffineTransform to apply before the PathIterator is returned.
     * @return The PathIterator.
     */
    public PathIterator getPathIterator(AffineTransform affineTransform) {
        return path.getPathIterator(affineTransform);
    }
    
    /**
     * Returns a flattened PathIterator that can be used to draw this GeoPath or iterate over its
     * geometry. A flattened PathIterator does not contain any quatratic or cubic bezier
     * curve segments, but only straight lines.
     * @param affineTransform An AffineTransform to apply before the PathIterator is returned.
     * @param flatness The maximum deviation of the flattend geometry from the original bezier geometry.
     * @return The PathIterator.
     */
    public PathIterator getPathIterator(AffineTransform affineTransform,
            double flatness) {
        return path.getPathIterator(affineTransform, flatness);
    }
    
    
    public void draw(Graphics2D g2d, double scale, boolean drawSelectionState) {
        if (!visible)
            return;
        
        // Fix for a bug in the Sun rendering enginge that is used on windows 
        // linux and mac starting with 10.5: bezier curves are drawn as straight 
        // lines if they are small, independent of the current transformation.
        // for now this is a very unelegant hack to temporarily solve this
        // problem.
        // Bezier curves are simply converted to flat lines with a maximum flatness
        // of 0.5 pixels
        final GeneralPath flattenedPath = this.flatten(0.5/scale, (GeneralPath)null);
        
        // fill
        if (this.symbol != null && this.symbol.isFilled()) {
            this.symbol.applyFillSymbol(g2d);
            g2d.fill(flattenedPath);
        }
        
        // stroke
        if (this.symbol != null) {
            if (this.symbol.isStroked()) {
                symbol.applyStrokeSymbol(g2d, scale);
                g2d.setColor(this.symbol.getStrokeColor());
                g2d.draw(flattenedPath);
            }
            
        } else {
            // use default stroke if there is no VectorSymbol present
            g2d.setStroke(new BasicStroke(1));
            g2d.setColor(Color.black);
            g2d.draw(flattenedPath);
        }
        
        // selection: color depends on selection state
        if (drawSelectionState && isSelected()) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_OFF);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_SPEED);
            
            g2d.setColor(ika.utils.ColorUtils.getHighlightColor());
            BasicStroke selectionStroke = new BasicStroke((float)(2./scale));
            g2d.setStroke(selectionStroke);
            g2d.draw(flattenedPath);
            
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
        }
    }
    
    public boolean isPointOnSymbol(java.awt.geom.Point2D point, double tolDist,
            double scale) {
        
        /* First test if point is inside the bounding box.
         The rectangle has to be enlarged by tolDist, otherwise contains()
         returns false for a straight horizontal or vertical line. */
        Rectangle2D bounds = this.getBounds2D();
        GeometryUtils.enlargeRectangle(bounds, tolDist);
        if (bounds.contains(point) == false)
            return false;
        
        // if path is filled, test if point is inside path
        if (this.symbol.isFilled() && path.contains(point))
            return true;
        
        // test if distance to line is smaller than tolDist
        // create new path with straight lines only
        PathIterator pi = this.path.getPathIterator(null, tolDist / 2.);
        double x1 = 0;
        double y1 = 0;
        double lastMoveToX = 0;
        double lastMoveToY = 0;
        double [] coords = new double [6];
        int segmentType;
        while (pi.isDone() == false) {
            segmentType = pi.currentSegment(coords);
            switch (segmentType) {
                case PathIterator.SEG_CLOSE:
                    // SEG_CLOSE does not return any point.
                    coords[0] = lastMoveToX;
                    coords[1] = lastMoveToY;
                    // fall thru, no break here
                    
                case PathIterator.SEG_LINETO:
                    double d = Line2D.ptSegDistSq(x1,  y1, coords[0], coords[1],
                            point.getX(), point.getY());
                    if (d < tolDist * tolDist)
                        return true;
                    x1 = coords[0];
                    y1 = coords[1];
                    break;
                    
                case PathIterator.SEG_MOVETO:
                    lastMoveToX = x1 = coords[0];
                    lastMoveToY = y1 = coords[1];
                    break;
            }
            pi.next();
        }
        return false;
    }
    
    public boolean contains(double x, double y) {
        return this.path.contains(x, y);
    }
    
    public boolean isIntersectedByRectangle(Rectangle2D rect, double scale) {
        
        // Test if if the passed rectangle and the bounding box of this object
        // intersect.
        // Use GeometryUtils.rectanglesIntersect and not Rectangle2D.intersects!
        final Rectangle2D bounds = this.getBounds2D();
        if (GeometryUtils.rectanglesIntersect(rect, bounds) == false)
            return false;
        
        // transform curved bezier segments to straight lines
        // tolerance for conversion is 0.5 pixel converted to world coordinates.
        final double tolDist = 0.5 / scale;
        
        // loop over all straight segments of this path
        PathIterator pi = this.path.getPathIterator(null, tolDist);
        double lx1 = 0;
        double ly1 = 0;
        double [] coords = new double [6];
        int segmentType;
        while (pi.isDone() == false) {
            segmentType = pi.currentSegment(coords);
            final double lx2 = coords[0];
            final double ly2 = coords[1];
            switch (segmentType) {
                case PathIterator.SEG_LINETO:
                    
                    // test if rect and the line segment intersect.
                    if (rect.intersectsLine(lx1, ly1, lx2, ly2)) {
                        return true;
                    }
                    
                    // fall thru, no break here
                case PathIterator.SEG_MOVETO:
                    lx1 = lx2;
                    ly1 = ly2;
                    break;
            }
            pi.next();
        }
        return false;
    }
    
    public Rectangle2D getBounds2D() {
        return (path != null) ? path.getBounds2D() : null;
    }
    
    public void move(double dx, double dy) {
        AffineTransform transformation = new AffineTransform();
        transformation.translate(dx, dy);
        path.transform(transformation);
    }
    
    /**
     * Scale this path by a factor relative to a passed origin.
     * @param scale Scale factor.
     * @param cx The x coordinate of the point relativ to which the object is scaled.
     * @param cy The y coordinate of the point relativ to which the object is scaled.
     */
    public void scale(double scale) {
        this.path.transform(AffineTransform.getScaleInstance(scale, scale));
    }
    
    public GeneralPath getPath() {
        return path;
    }
    
    public void setPath(GeneralPath path) {
        this.path = path;
    }
    
    public void rotate(double rotRad){
        this.path.transform(AffineTransform.getRotateInstance(rotRad));
    }
    
    public void translate(double dx, double dy){
        this.path.transform(AffineTransform.getTranslateInstance(dx, dy));
    }
    
    public String toString() {
        
        StringBuffer str = new StringBuffer();
        PathIterator pi = path.getPathIterator(null);
        float[] coord = new float [6];
        while (pi.isDone() == false) {
            switch (pi.currentSegment(coord)) {
                case PathIterator.SEG_MOVETO:
                    str.append("moveto " + coord[0] + " " + coord[1] + "\n");
                    break;
                    
                case PathIterator.SEG_LINETO:
                    str.append("lineto " + coord[0] + " " + coord[1] + "\n");
                    break;
                    
                case PathIterator.SEG_QUADTO:
                    str.append("quad " + coord[0] + " " + coord[1]
                            + "\n\t" + coord[2] + " " + coord[3] + "\n");
                    break;
                    
                case PathIterator.SEG_CUBICTO:
                    str.append("cubic " + coord[0] + " " + coord[1]
                            + "\n\t" + coord[2] + " " + coord[3]
                            + "\n\t" + coord[4] + " " + coord[5] + "\n");
                    break;
                    
                case PathIterator.SEG_CLOSE:
                    str.append("close\n");
                    break;
                    
            }
            pi.next();
        }
        
        return super.toString() + " \n" + str.toString() + this.symbol.toString();
    }
}
