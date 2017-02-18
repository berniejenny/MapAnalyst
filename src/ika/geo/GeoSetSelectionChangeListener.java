/*
 * GeoSetSelectionChangeListener.java
 *
 * Created on June 6, 2005, 4:57 PM
 *
 */

package ika.geo;

/**
 * GeoSetSelectionChangeListener - a listener for selection change events 
 * triggered by GeoSets.
 * Whenever a GeoSet changes the selection of any of the GeoObjects that it 
 * contains, it calls geoSetSelectionChanged to inform all its listeners.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public interface GeoSetSelectionChangeListener {
    /**
     * geoSetSelectionChanged   is called whenever the selection of an element 
     * changes changes.
     * @param geoSet The parent GeoSet that changed the selection and triggered 
     * the call.
     */
    public void geoSetSelectionChanged (GeoSet geoSet);
}

