/*
 * HullConverter.java
 *
 * Created on July 10, 2005, 12:29 AM
 *
 */

package convexhull;

import java.util.*;
import ika.geo.*;

/**
 *
 * @author jenny
 */
public class HullConverter {
    
    public static GeoPath hullArrayToGeoPath(Vector hull) {
        GeoPath hullPath = new GeoPath();
        VectorSymbol symbol = new VectorSymbol();
        symbol.setScaleInvariant(true);
        symbol.setStrokeWidth(3);
        symbol.setFilled(false);
        symbol.setStroked(true);
        hullPath.setVectorSymbol(symbol);
        
        convexhull.CPoint p = (convexhull.CPoint)hull.get(0);
        hullPath.moveTo(p.x, p.y);
        final int nbrPts = hull.size();
        for (int i = 1; i < nbrPts; ++i) {
            p = (convexhull.CPoint)hull.get(i);
            hullPath.lineTo(p.x, p.y);
        }
        hullPath.closePath();
        return hullPath;
    }
    
    public static double[][] hullArray(Vector hull) {
        final int nbrPts = hull.size();
        double[][] a = new double [nbrPts+1][2];
        for (int i = 0; i < nbrPts; ++i) {
            convexhull.CPoint p = (convexhull.CPoint)hull.get(i);
            a[i][0] = p.x;
            a[i][1] = p.y;
        }
        a[nbrPts][0] = a[0][0];
        a[nbrPts][1] = a[0][1];
        return a;
    }
}