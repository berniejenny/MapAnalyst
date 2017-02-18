/*
 * MapChangeListener.java
 *
 * Created on March 5, 2005, 3:28 PM
 */

package ika.geo;

/**
 * GeoSetChangeListener - a listener for change events triggered by GeoSets.
 * Whenever a GeoSet changes any of the GeoObjects that it contains, it calls
 * geoSetChanged to inform all its listeners.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public interface GeoSetChangeListener {
    /**
     * geoSetChanged  is called whenever the map changes.
     * @param geoSet The GeoSet that changed and triggered the call.
     */
    public void geoSetChanged (GeoSet geoSet);
}
