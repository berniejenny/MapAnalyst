package convexhull;

import java.util.*;

/**
 * This class only contains static methods for computing the convex hull.
 *
 * @version 1.1, 12/04/98
 * @author Pavol Federl
 */
public class GrahamScan {
    
    /**
     * Compute a convex hull for a set of points using a modified Graham
     * scan algorithm, where only LEFT TURN calculation is used.
     *
     * @param points java.util.Vector
     * - points for which convex hull is to be calculated
     *
     * @return java.util.Vector
     * - convex hull (in counter-clockwise order)
     */
    public static Vector computeHull(Vector points) {
        // if there are less than 3 points, return the points
        // themselves as the convex hull
        if (points.size() < 3) return points;
        
        // compute the stairs
        Vector stairs = computeStairs( points);
        
        // if there are less than 3 points left in 'stairs', then return
        // stairs as the resulting convex hull
        if( stairs.size() < 3) return stairs;
        
        // get the first stairs-point (which is on the left edge)
        CPoint sm = (CPoint) stairs.firstElement();
        
        // start building the convex hull
        //   - sm is in the convex hull
        Stack hull = new Stack();
        hull.push( sm);
        
        // add the rest of the stairs to the hull
        // - to complete the convex hull properly, we have to make sure that
        //   we try to add stair[0] as the last point
        stairs.addElement( sm);
        int i = 1;
        while (i < stairs.size()) {
            // pi = next point to be processed
            CPoint pi = (CPoint) stairs.elementAt(i);
            
            // pl = top point on the hull stack
            CPoint pl = (CPoint) hull.peek();
            
            // if pi == pl, skip pi
            if (pl.isEqual( pi)) {
                i = i + 1;
                continue;
            }
            
            // if there is only one point on the hull, add pi to the hull
            if (hull.size() == 1) {
                hull.push( pi);
                i = i + 1;
                continue;
            }
            
            // there are at least two points on the hull - do the
            // left-turn test
            hull.pop();
            CPoint pll = (CPoint) hull.peek();
            if (leftTurn(pll, pl, pi)) {
                hull.push(pl);
                hull.push(pi);
                i = i + 1;
                continue;
            }
        }
        
        // get rid of the last point, because it is already
        // at the beginning of the hull
        hull.pop();
        
        // return the convex hull
        return hull;
    }
    
    
    /**
     * Computes the stairs of points. Each quadrant (SW,SE,NE,NW) has stairs
     * in it. These stairs are computed and then concatenated to produce the
     * result. The property of these stairs is that the resulting points are
     * in counterclockwise order, and only candidates for the convex hull
     * are stored in the set.
     *
     * @param pts java.util.Vector
     * - set of points for which stairs are to be calculated
     *
     * @return java.util.Vector
     * - the computed stairs
     */
    public static Vector computeStairs(Vector pts) {
        // if there are no input points, return with no stairs
        if (pts.size() == 0) return new Vector();
        
        // find the bounding polygon of all points
        CPoint upl = (CPoint) pts.elementAt(0);
        CPoint upr = upl;
        CPoint lol = upl;
        CPoint lor = upl;
        CPoint leu = upl;
        CPoint led = upl;
        CPoint riu = upl;
        CPoint rid = upl;
        for (int i = 1; i < pts.size(); i++) {
            CPoint pi = (CPoint) pts.elementAt(i);
            // check if we have new left point
            if (pi.x < leu.x) {
                leu = led = pi;
            } else if (pi.x == leu.x) {
                if (leu.y < pi.y) leu = pi;
                if (led.y > pi.y) led = pi;
            }
            
            // check if we have new right point
            if (pi.x > riu.x) {
                riu = rid = pi;
            } else if (pi.x == riu.x) {
                if (riu.y < pi.y) riu = pi;
                if (rid.y > pi.y) rid = pi;
            }
            
            // check if we have new up point
            if (pi.y > upl.y) {
                upl = upr = pi;
            } else if (pi.y == upl.y) {
                if (pi.x < upl.x) upl = pi;
                if (pi.x > upr.x) upr = pi;
            }
            
            // check if we have new low point
            if (pi.y < lol.y) {
                lol = lor = pi;
            } else if (pi.y == lol.y) {
                if (pi.x < lol.x) lol = pi;
                if (pi.x > lor.x) lor = pi;
            }
        }
        
        // divide the input points into 4 rectangles (SouthEast, SouthWest,
        // NorthEast, NorthWest)
        Vector se = new Vector();
        Vector sw = new Vector();
        Vector ne = new Vector();
        Vector nw = new Vector();
        for (int i = 0; i < pts.size(); i++) {
            CPoint pi = (CPoint) pts.elementAt(i);
            
            // south-east
            if (pi.x > lor.x && pi.y < rid.y) se.addElement(pi);
            // south-west
            if (pi.x < lol.x && pi.y < led.y) sw.addElement(pi);
            // north-east
            if (pi.x > upr.x && pi.y > riu.y) ne.addElement(pi);
            // north-west
            if (pi.x < upl.x && pi.y > leu.y) nw.addElement(pi);
        }
        
        // here we store the result
        Vector res = new Vector();
        
        // add the points on the left edge to the result
        res.addElement( leu);
        if( led != res.lastElement()) res.addElement( led);
        
        // =================================================
        // add the stairs in the SOUTH-WEST rectangle
        // =================================================
        if (sw.size() > 0) {
            // sort SW points by increasing X-coordinate
            Sort.quick(sw, new ComparatorAdapter() {
                public int compare(Object o1, Object o2) {
                    if( ((CPoint) o1).x < ((CPoint) o2).x)
                        return 1;
                    else
                        return -1;
                }
            });
            
            // filter out points with strictly decreasing Y-coordinate
            CPoint p0 = led; CPoint pn = lol;
            CPoint last = p0;
            for (int i = 0; i < sw.size(); i++) {
                CPoint pi = (CPoint) sw.elementAt(i);
                if (last.y > pi.y) {
                    if( leftTurn( p0, pi, pn)) {
                        last = pi;
                        res.addElement(last);
                    }
                }
            }
        }
        
        // add points on the bottom edge
        if( lol != res.lastElement()) res.addElement( lol);
        if( lor != res.lastElement()) res.addElement( lor);
        
        // =================================================
        // add the stairs from the SOUTH-EAST rectangle
        // =================================================
        if (se.size() > 0) {
            // sort SE points by increasing Y-coordinate
            Sort.quick(se, new ComparatorAdapter() {
                public int compare(Object o1, Object o2) {
                    if( ((CPoint) o1).y < ((CPoint) o2).y)
                        return 1;
                    else
                        return -1;
                }
            });
            
            // filter out points with strictly increasing X-coordinate
            CPoint p0 = lor; CPoint pn = rid;
            CPoint last = p0;
            for (int i = 0; i < se.size(); i++) {
                CPoint pi = (CPoint) se.elementAt(i);
                if (last.x < pi.x) {
                    if( leftTurn( p0, pi, pn)) {
                        last = pi;
                        res.addElement(last);
                    }
                }
            }
        }
        
        // add points on the right edge
        if( rid != res.lastElement()) res.addElement( rid);
        if( riu != res.lastElement()) res.addElement( riu);
        
        // =================================================
        // add the stairs from the NORTH-EAST rectangle
        // =================================================
        if (ne.size() > 0) {
            // sort NE points by decreasing X-coordinate
            Sort.quick(ne, new ComparatorAdapter() {
                public int compare(Object o1, Object o2) {
                    if( ((CPoint) o1).x > ((CPoint) o2).x)
                        return 1;
                    else
                        return -1;
                }
            });
            
            // only filter out points with strictly increasing Y-coordinate
            CPoint p0 = riu; CPoint pn = upr;
            CPoint last = p0;
            for (int i = 0; i < ne.size(); i++) {
                CPoint pi = (CPoint) ne.elementAt(i);
                if (last.y < pi.y) {
                    if( leftTurn( p0, pi, pn)) {
                        last = pi;
                        res.addElement(last);
                    }
                }
            }
        }
        
        // add point on the top edge
        if( upr != res.lastElement()) res.addElement( upr);
        if( upl != res.lastElement()) res.addElement( upl);
        
        // =================================================
        // add the stairs from the NORTH-WEST rectangle
        // =================================================
        if (nw.size() > 0) {
            // sort NW points by decreasing Y-coordinate
            Sort.quick(nw, new ComparatorAdapter() {
                public int compare(Object o1, Object o2) {
                    if( ((CPoint) o1).y > ((CPoint) o2).y)
                        return 1;
                    else
                        return -1;
                }
            });
            
            // only filter out points with strictly decreasing in X-coordinate
            CPoint p0 = upl; CPoint pn = leu;
            CPoint last = p0;
            for (int i = 0; i < nw.size(); i++) {
                CPoint pi = (CPoint) nw.elementAt(i);
                if (last.x > pi.x) {
                    if( leftTurn( p0, pi, pn)) {
                        last = pi;
                        res.addElement(last);
                    }
                }
            }
        }
        
        // if the first and last points are the same, then remove
        // the last point
        if( res.size() > 1)
            if( res.firstElement() == res.lastElement())
                res.removeElementAt( res.size()-1);
        
        // return the computed stairs
        return res;
    }
    
    /**
     * Determines whether two oriented line segments form a left turn. The
     * line segments are specified by AB, and BC.
     *
     * @param a convexHull.CPoint - point A
     * @param b convexHull.CPoint - point B
     * @param c convexHull.CPoint - point C
     * @return bool - whether the line segments form a left turn
     */
    public static boolean leftTurn(CPoint a, CPoint b, CPoint c) {
        double a1 = a.x;
        double a2 = a.y;
        double b1 = b.x;
        double b2 = b.y;
        double c1 = c.x;
        double c2 = c.y;
        
        final double det = (b1 - a1) * (c2 - a2) - (b2 - a2) * (c1 - a1);
        return (det > 0);
    }
    
    /**
     * Prints out a list of points.
     *
     * @param str string to be printed before the list
     * @param points list of points
     */
    public static void printPoints(String str, Vector points) {
        System.out.println("");
        System.out.println(str);
        System.out.println("----------------");
        System.out.println("");
        
        printPoints( points);
        System.out.println("");
    }
    
    /**
     * Prints out a list of points.
     *
     * @param points list of points
     */
    public static void printPoints( Vector points) {
        for (int i = 0; i < points.size(); i++) {
            CPoint p = (CPoint) points.elementAt(i);
            System.out.println((i + 1) + ") [" + p.x + "," + p.y + "]");
        }
    }
}
