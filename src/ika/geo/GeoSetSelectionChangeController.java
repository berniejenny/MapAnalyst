/*
 * GeoSetSelectionChangeController.java
 *
 * Created on May 14, 2005, 11:49 AM
 */

package ika.geo;

/**
 * Before changing the selection state of any object, the parent GeoSet
 * asks its GeoSetSelectionChangeController for permission.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public interface GeoSetSelectionChangeController {
    
    
    /**
     * Returns whether the selection state of any child can be changed.
     * @param geoSet The GeoSet that asks for permission to change the selection
     * state of a child.
     * @return True if the selection state can be changed, false otherwise.
     */
    public boolean allowSelectionChange(GeoSet geoSet);
}