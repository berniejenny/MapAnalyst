package ika.proj;

import com.jhlabs.map.Ellipsoid;
import com.jhlabs.map.proj.MercatorProjection;
import com.jhlabs.map.proj.Projection;
import com.jhlabs.map.proj.ProjectionFactory;
import ika.geo.osm.OpenStreetMap;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Vector;

/**
 *
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich
 */
public class ProjectionsManager {

    private static Vector<Projection> list;

    private ProjectionsManager() {
    }

    public static int getProjectionID(String name) {
        if (ProjectionsManager.list == null) {
            loadProjectionsFromFile();
        }
        return list.indexOf(name);
    }

    public static String getProjectionName(int id) {
        if (ProjectionsManager.list == null) {
            loadProjectionsFromFile();
        }
        return list.get(id).toString();
    }

    public static Vector<Projection> getProjections() {
        if (ProjectionsManager.list == null) {
            loadProjectionsFromFile();
        }
        return list;
    }

    private static void loadProjectionsFromFile() {
        BufferedReader reader = null;
        try {
            list = new Vector<>();

            // load projection description
            java.net.URL url = ProjectionsManager.class.getResource("/ika/data/projections.txt");
            BufferedInputStream bis = new BufferedInputStream(url.openStream());
            InputStreamReader isr = new InputStreamReader(bis, "UTF-8");
            reader = new BufferedReader(isr);
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // double slash is a comment
                if (line.startsWith("//")) {
                    continue;
                }
                String proj4Code = line.split(" ")[0];
                Projection p = ProjectionFactory.getNamedPROJ4Projection(proj4Code);
                if (p != null) {
                    list.add(p);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            list = null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    /**
     * Creates and initializes a Mercator projection with a spherical coordinate
     * system commonly used by web map services, such as OpenStreetMap.
     *
     * @return a new Mercator projection for web maps
     */
    public static MercatorProjection createWebMercatorProjection() {
        MercatorProjection mercator = new MercatorProjection();
        mercator.setMaxLatitude(OpenStreetMap.MAX_LAT);
        mercator.setMinLatitude(OpenStreetMap.MIN_LAT);
        Ellipsoid osmSphere = new Ellipsoid("osm", OpenStreetMap.R, OpenStreetMap.R, 0.0, "OSM Sphere");
        mercator.setEllipsoid(osmSphere);
        mercator.initialize();
        return mercator;
    }
}
