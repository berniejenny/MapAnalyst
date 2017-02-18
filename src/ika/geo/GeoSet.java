/*
 * GeoSet.java
 *
 * Created on 5. Februar 2005, 16:48
 */

package ika.geo;

import java.io.*;
import java.awt.geom.*;
import java.awt.*;
import java.util.*;

/**
 * GeoSet - models an ordered group of GeoObjects.<br>
 * A GeoSet handles GeoSetChangeListener that are
 * informed when the GeoMap changes. GeoSetChangeListener are automatically informed of
 * changes to this GeoSet if a method of GeoSet is called, but not if any method
 * any other GeoObject is directly called.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class GeoSet extends GeoObject implements Serializable {
    
    private static final long serialVersionUID = -8029643397815392824L;
    /**
     * A set of registered GeoSetChangeListener.
     */
    transient private Vector geoSetChangeListeners;
    transient private boolean informChangeListeners;
    
    /**
     * A set of registered GeoSetSelectionChangeListener.
     */
    transient private Vector geoSetSelectionChangeListeners;
    transient private boolean informSelectionChangeListeners;
    
    /**
     * A set of registered GeoSetSelectionChangeController.
     */
    transient private Vector geoSetSelectionChangeControllers;
    
    /**
     * The parent GeoSet that contains this GeoSet.
     */
    private GeoSet parentGeoSet = null;
    
    /**
     * A vector that contains the GeoObjects pertaining to this GeoSet.
     */
    private java.util.Vector vector = new java.util.Vector();
    
    /** Creates a new instance of GeoSet */
    public GeoSet() {
        this.initListeners();
    }
    
    private void initListeners() {
        this.geoSetChangeListeners = new Vector();
        this.informChangeListeners = true;
        this.geoSetSelectionChangeListeners = new Vector();
        this.informSelectionChangeListeners = true;
        this.geoSetSelectionChangeControllers = new Vector();
    }
    
    private void readObject(ObjectInputStream stream)
    throws IOException, ClassNotFoundException {
        
        // read this object
        stream.defaultReadObject();
        
        // initialize the transient variables
        this.initListeners();
    }
    
    
    /**
     * Set the parent GeoSet. The parent GeoSet contains this GeoSet.
     * @param parentGeoSet The new parent GeoSet.
     */
    private void setParentGeoSet(GeoSet parentGeoSet) {
        this.parentGeoSet = parentGeoSet;
    }
    
    /**
     * Add a GeoObject to this GeoSet. The object will be inserted after all currently
     * contained GeoObjects.
     * @param geoObject The GeoObject to add.
     */
    public void addGeoObject(GeoObject geoObject) {
        if (geoObject == null)
            return;
        
        vector.add(geoObject);
        if (geoObject instanceof GeoSet) {
            final GeoSet childGeoSet = (GeoSet)geoObject;
            childGeoSet.setParentGeoSet(this);
        }
        this.informGeoSetChangeListeners(this);
    }
    
    /**
     * Remove all currently contained GeoObjects from this GeoSet.
     */
    public void removeAllGeoObjects() {
        vector.clear();
        this.informGeoSetChangeListeners(this);
    }
    
    /**
     * Remove all currently selected GeoObjects from this GeoSet.
     */
    public void removeSelectedGeoObjects() {
        for (int i = vector.size() - 1; i >= 0; i--) {
            GeoObject geoObject = (GeoObject)(vector.get(i));
            if (geoObject instanceof GeoSet)
                ((GeoSet)geoObject).removeSelectedGeoObjects();
            else {
                if (geoObject.isSelected()) {
                    Object removedObj = vector.remove(i);
                    removedObj  = null;
                }
            }
        }
        this.informGeoSetChangeListeners(this);
    }
    
    /**
     * Returns the bounding box of all GeoObjects contained by this GeoSet.
     */
    public java.awt.geom.Rectangle2D getBounds2D() {
        return this.getBounds2D(false);
    }
    
    /**
     * Returns the bounding box of all visible GeoObjects contained by this GeoSet.
     */
    public Rectangle2D getVisibleBounds2D() {
        return this.isVisible() ? this.getBounds2D(true) : null;
    }
    
    /**
     * Returns the bounding box of all GeoObjects contained by this GeoSet.
     * @param onlyVisible If true, only the bounding box of the currently
     * visible GeoObjects is returned.
     */
    public java.awt.geom.Rectangle2D getBounds2D(boolean onlyVisible) {
        if (vector.size() == 0)
            return null;
        
        // search through children for first object with valid bounding box
        Rectangle2D rect = null;
        java.util.Iterator iterator = this.vector.iterator();
        while (iterator.hasNext() && rect == null) {
            final GeoObject geoObject = (GeoObject)iterator.next();
            if (onlyVisible)
                rect = geoObject.getVisibleBounds2D();
            else
                rect = geoObject.getBounds2D();
        }
        if (rect == null)
            return null;
        rect = (Rectangle2D)rect.clone();
        
        // compute union with bounding boxes of all following objects
        while (iterator.hasNext()) {
            GeoObject geoObject = (GeoObject)iterator.next();
            final Rectangle2D bounds;
            if (onlyVisible)
                bounds = geoObject.getVisibleBounds2D();
            else
                bounds = geoObject.getBounds2D();
            if (bounds != null)
                Rectangle2D.union(rect, bounds, rect);
        }
        return rect;
    }
    
    /**
     * Required by abstract super class GeoObject. This implementation returns null,
     * since a GeoSet does not have its own geometry.
     */
    public java.awt.geom.PathIterator getPathIterator(java.awt.geom.AffineTransform affineTransform) {
        return null;
    }
    
    /**
     * Required by abstract super class GeoObject. This implementation returns
     * alway false, since a GeoSet does not have its own geometry.
     */
    public boolean isPointOnSymbol(Point2D point, double tolDist, double scale) {
        return false;
    }
    
    /**
     * Required by abstract super class GeoObject. This implementation returns
     * alway false, since a GeoSet does not have its own geometry.
     */
    public boolean isIntersectedByRectangle(Rectangle2D rect, double scale) {
        return false;
    }
    
    /**
     * Returns the visually top-most object contained in this GeoSet that is under
     * a passed point.
     * @param point The point for hit detection.
     * @param tolDist The tolerance to use for hit detection in world coordinates.
     * @param scale The current sccale of the map.
     * @return Returns the GeoObject if any, null otherwise.
     */
    public GeoObject getObjectAtPosition(Point2D point, double tolDist,
            double scale,
            boolean onlySelectable,
            boolean onlyVisible) {
        for (int i = vector.size() - 1; i >= 0; i--) {
            final Object obj = vector.get(i);
            
            // filter invisible GeoObjects
            if (onlyVisible && !((GeoObject)obj).isVisible())
                continue;
            
            if (obj instanceof GeoSet) {
                final GeoSet geoSet = (GeoSet)obj;
                
                // continue search in found GeoSet
                final GeoObject geoObject = geoSet.getObjectAtPosition(
                        point, tolDist, scale, onlySelectable, onlyVisible);
                if (geoObject != null)
                    return geoObject;
            } else {
                final GeoObject geoObject = (GeoObject)obj;
                
                // filter GeoObjects that are not selectable
                if (onlySelectable && !geoObject.isSelectable())
                    continue;
                
                // test if point is on symbolized GeoObject
                if (geoObject.isPointOnSymbol(point, tolDist, scale) == true)
                    return geoObject;
            }
        }
        return null;
    }
    
    public boolean selectByPoint(Point2D point, double scale,
            boolean extendSelection, double tolDist) {
        
        if (canChangeSelectionOfChildren() == false)
            return false;
        
        boolean selectionChanged = false;
        
        java.util.Iterator iterator = this.vector.iterator();
        while (iterator.hasNext()) {
            final GeoObject geoObject = (GeoObject)iterator.next();
            selectionChanged |= geoObject.selectByPoint(point, scale,
                    extendSelection, tolDist);
        }
        if (selectionChanged)
            this.informGeoSetSelectionChangeListeners(this);
        return selectionChanged;
    }
    
    /**
     * Selects all GeoObjects contained by this GeoSet that intersect with the passed
     * rectangle.
     */
    public boolean selectByRectangle(Rectangle2D rect, double scale,
            boolean extendSelection){
        
        if (canChangeSelectionOfChildren() == false)
            return false;
        
        boolean selectionChanged = false;
        
        java.util.Iterator iterator = this.vector.iterator();
        while (iterator.hasNext()) {
            final GeoObject geoObject = (GeoObject)iterator.next();
            selectionChanged |= geoObject.selectByRectangle(rect, scale, extendSelection);
        }
        if (selectionChanged)
            this.informGeoSetSelectionChangeListeners(this);
        return selectionChanged;
    }
    
    /**
     * Overwrite GeoObject's setSelected method. The new selection state is
     * applied to each GeoObject contained by this GeoSet.
     * @param selected The new selection state of all GeoObjects contained by
     * this GeoSet.
     */
    public void setSelected(boolean selected) {
        if (canChangeSelectionOfChildren() == false)
            return;
        
        java.util.Iterator iterator = this.vector.iterator();
        while (iterator.hasNext()) {
            GeoObject geoObject = (GeoObject)iterator.next();
            geoObject.setSelected(selected);
        }
        this.informGeoSetSelectionChangeListeners(this);
    }
    
    /** Assign a shared VectorSymbol to all GeoPath in this GeoSet and all 
     * sub-GeoSets.
     * @param vectorSymbol The shared instance of a VectorSymbol that will be 
     * assigned to all children GeoPaths.
     */
    public void setVectorSymbol (VectorSymbol vectorSymbol){
        java.util.Iterator iterator = this.vector.iterator();
        while (iterator.hasNext()) {
            Object obj = iterator.next();
            if (obj instanceof GeoPath)
                ((GeoPath)obj).setVectorSymbol(vectorSymbol);
            else if (obj instanceof GeoSet)
                ((GeoSet)obj).setVectorSymbol(vectorSymbol);
        }
    }
    
    /**
     * Returns the number of GeoObjects contained by this GeoSet.
     * @return The number of GeoObjects contained by this GeoSet.
     */
    public int getNumberOfChildren() {
        return this.vector.size();
    }
    
    /**
     * Returns the number of GeoSets contained by this GeoSets.
     * @return The number of GeoSets contained by this GeoSets.
     */
    public int getNumberOfSubSets() {
        int numberOfSubSets = 0;
        java.util.Iterator iterator = this.vector.iterator();
        while (iterator.hasNext()) {
            GeoObject geoObject = (GeoObject)iterator.next();
            if (geoObject instanceof GeoSet)
                numberOfSubSets += ((GeoSet)geoObject).getNumberOfSubSets() + 1;
        }
        return numberOfSubSets;
    }
    
    /**
     * Returns the GeoObject at a certain index.
     */
    public GeoObject getGeoObject(int id) {
        return (GeoObject)this.vector.get(id);
    }
    
    /**
     * Returns the GeoObject with a certain name.
     * Returns the first object found with the name.
     */
    public GeoObject getGeoObject(String name) {
        final int nbrObj = this.vector.size();
        for (int i = 0; i < nbrObj; i++) {
            GeoObject geoObject = (GeoObject)(this.vector.get(i));
            if (name.equals(geoObject.getName()))
                return geoObject;
            if (geoObject instanceof GeoSet) {
                geoObject = ((GeoSet)geoObject).getGeoObject(name);
                if (geoObject != null)
                    return geoObject;
            }
        }
        return null;
    }
    
    /**
     * Returns the first GeoObject of a specified class, or the first GeoObject
     * that is not of the specified class. The search can optionally be limited 
     * to selected objects.
     * @param requiredClass The GeoObject must be of this class, unless 
     * exclusive is true.
     * @param exclusive If true, the first GeoObject that is not of the 
     * requiredClass is returned, otherwise the first GeoObject that is of the
     * specified class is returned.
     * @param requireSelected The GeoObject must be selected.
     * @return The first GeoObject in this GeoSet or in one of its sub-GeoSets
     * that is of the specified class.
     */
    public synchronized GeoObject getFirstGeoObject(Class requiredClass, 
            boolean exclusive, boolean requireSelected) {
        
        try {
            
            int classID = requiredClass.hashCode();
            
            // do a scan among all children of this GeoSet
            java.util.Iterator iterator = this.vector.iterator();
            while (iterator.hasNext()) {
                final GeoObject geoObject = (GeoObject)iterator.next();
                final boolean sameClass = geoObject.getClass().hashCode() == classID;
                if (sameClass ^ exclusive) {
                    if (!requireSelected || (requireSelected && geoObject.isSelected()))
                        return (GeoObject)geoObject;
                }
            }
            
            // ask child GeoSets for the object
            iterator = this.vector.iterator();
            while (iterator.hasNext()) {
                GeoObject geoObject = (GeoObject)iterator.next();
                if (geoObject instanceof GeoSet) {
                    final GeoSet geoSet = (GeoSet)geoObject;
                    GeoObject childGeoObject = geoSet.getFirstGeoObject(
                            requiredClass, exclusive, requireSelected);
                    if (childGeoObject != null)
                        return childGeoObject;
                }
            }
            
        } catch (Exception e) {}
        return null;
    }
    
    /**
     * Inform all GeoSetChangeListener that this GeoSet has changed.
     */
    public void informGeoSetChangeListeners(Object caller) {
        if (!this.informChangeListeners)
            return;
        
        Iterator iterator = this.geoSetChangeListeners.iterator();
        while(iterator.hasNext()) {
            GeoSetChangeListener listener = (GeoSetChangeListener)iterator.next();
            if (caller != listener)
                listener.geoSetChanged(this);
        }
        
        // inform the parent GeoSet of change. The parent will again inform its
        // listeners.
        if (this.parentGeoSet != null)
            parentGeoSet.informGeoSetChangeListeners(this);
    }
    
    /**
     * Register a GeoSetChangeListener.
     */
    public void addGeoSetChangeListener(GeoSetChangeListener listener) {
        this.geoSetChangeListeners.add(listener);
    }
    
    /**
     * Unregister a GeoSetChangeListener.
     */
    public void removeGeoSetChangeListener(GeoSetChangeListener listener) {
        this.geoSetChangeListeners.remove(listener);
    }
    
    public void suspendGeoSetChangeListeners() {
        this.informChangeListeners = false;
    }
    
    public void activateGeoSetChangeListeners(Object caller) {
        this.informChangeListeners = true;
        informGeoSetChangeListeners(caller);
    }
    
    /**
     * Inform all GeoSetSelectionChangeListener that this GeoSet has changed.
     */
    public void informGeoSetSelectionChangeListeners(Object caller) {
        if (!this.informSelectionChangeListeners)
            return;
        
        Iterator iterator = this.geoSetSelectionChangeListeners.iterator();
        while(iterator.hasNext()) {
            GeoSetSelectionChangeListener listener = (GeoSetSelectionChangeListener)iterator.next();
            if (caller != listener)
                listener.geoSetSelectionChanged(this);
        }
        
        // inform the parent GeoSet of change. The parent will again inform its
        // listeners.
        if (this.parentGeoSet != null)
            parentGeoSet.informGeoSetSelectionChangeListeners(this);
    }
    
    /**
     * Register a GeoSetSelectionChangeListener.
     */
    public void addGeoSetSelectionChangeListener(GeoSetSelectionChangeListener listener) {
        this.geoSetSelectionChangeListeners.add(listener);
    }
    
    /**
     * Unregister a GeoSetSelectionChangeListener.
     */
    public void removeGeoSetSelectionChangeListener(GeoSetChangeListener listener) {
        this.geoSetSelectionChangeListeners.remove(listener);
    }
    
    public void suspendGeoSetSelectionChangeListeners() {
        this.informSelectionChangeListeners = false;
    }
    
    public void activateGeoSetSelectionChangeListeners(Object caller) {
        this.informSelectionChangeListeners = true;
        informGeoSetSelectionChangeListeners(caller);
    }
    
    /**
     * Asks all registered GeoSetSelectionChangeController
     * if the selection state of any child can be changed.
     */
    public boolean canChangeSelectionOfChildren() {
        
        Iterator iterator = this.geoSetSelectionChangeControllers.iterator();
        while(iterator.hasNext()) {
            GeoSetSelectionChangeController controller =
                    (GeoSetSelectionChangeController)iterator.next();
            if (controller.allowSelectionChange(this) == false)
                return false;
        }
        return true;
    }
    
    /**
     * Register a GeoSetSelectionChangeController.
     */
    public void addGeoSetSelectionChangeController(GeoSetSelectionChangeController controller) {
        this.geoSetSelectionChangeControllers.add(controller);
    }
    
    /**
     * Unregister a GeoSetSelectionChangeController.
     */
    public void removeGeoSetSelectionChangeController(GeoSetSelectionChangeController controller) {
        this.geoSetSelectionChangeControllers.remove(controller);
    }
    
    public void draw(Graphics2D g2d, double scale, boolean drawSelectionState) {
        if (!visible)
            return;
        
        java.util.Iterator iterator = this.vector.iterator();
        while (iterator.hasNext()) {
            final GeoObject geoObject = (GeoObject)iterator.next();
            geoObject.draw(g2d, scale, drawSelectionState);
        }
    }
    
    /**
     * Returns true if this GeoSet contains any GeoObject that is currently selected.
     */
    public boolean hasSelectedGeoObjects() {
        java.util.Iterator iterator = this.vector.iterator();
        while (iterator.hasNext()) {
            GeoObject geoObject = (GeoObject)iterator.next();
            if (geoObject instanceof GeoSet) {
                GeoSet geoSet = (GeoSet)geoObject;
                if (geoSet.hasSelectedGeoObjects())
                    return true;
            } else {
                if (geoObject.isSelected())
                    return true;
            }
        }
        return false;
    }
    
    /**
     * Private helper method. Returns the selected GeoObject, if there is
     * exactly one selected, returns null otherwise.
     * Abuses excpetions. Should be done in a nicer way.
     */
    private GeoObject getSingleSelectedGeoObject(GeoObject selectedObj) throws Exception {
        java.util.Iterator iterator = this.vector.iterator();
        while (iterator.hasNext()) {
            GeoObject geoObject = (GeoObject)iterator.next();
            if (geoObject instanceof GeoSet) {
                GeoSet geoSet = (GeoSet)geoObject;
                GeoObject selectedObjOfSubSet = geoSet.getSingleSelectedGeoObject(selectedObj);
                if (selectedObjOfSubSet != null) {
                    if (selectedObj != null)
                        throw new Exception();
                    else
                        selectedObj = selectedObjOfSubSet;
                }
            } else {
                if (geoObject.isSelected()) {
                    if (selectedObj != null)
                        throw new Exception();
                    selectedObj = geoObject;
                }
            }
        }
        return selectedObj;
    }
    
    /**
     * Returns the selected GeoObject, if there is
     * exactly one selected one, returns null otherwise.
     */
    public GeoObject getSingleSelectedGeoObject() {
        try {
            GeoObject geoObject = getSingleSelectedGeoObject(null);
            if (geoObject != null && geoObject.isSelected())
                return geoObject;
        } catch (Exception e) {}
        return null;
    }
    
    public void move(double dx, double dy) {
        java.util.Iterator iterator = this.vector.iterator();
        while (iterator.hasNext()) {
            GeoObject geoObject = (GeoObject)iterator.next();
            geoObject.move(dx, dy);
        }
        this.informGeoSetChangeListeners(this);
    }
    
    public void scale(double scale) {
        java.util.Iterator iterator = this.vector.iterator();
        while (iterator.hasNext()) {
            GeoObject geoObject = (GeoObject) iterator.next();
            geoObject.scale(scale);
        }
    }
    
     
    /**
     * Moves all selected GeoObjects contained by this GeoSet by a certain distance.
     */
    public void moveSelectedGeoObjects(double dx, double dy) {
        java.util.Iterator iterator = this.vector.iterator();
        while (iterator.hasNext()) {
            GeoObject geoObject = (GeoObject)iterator.next();
            if (geoObject instanceof GeoSet) {
                GeoSet geoSet = (GeoSet)geoObject;
                geoSet.moveSelectedGeoObjects(dx, dy);
            } else if (geoObject.isSelected()) {
                geoObject.move(dx, dy);
            }
        }
        this.informGeoSetChangeListeners(this);
    }

    public void printTree() {
        System.out.println();
        System.out.print("Print of ");
        printNameAndExtension(this, 0, System.out);
        printTree(0, System.out);
    }

    private void printNameAndExtension(GeoObject geoObject, int intent, PrintStream printer) {
        if (geoObject == null) {
            return;
        }
        
        for (int t = 0; t < intent; t++) {
            printer.print("\t");
        }
        String name = geoObject.getClass().getSimpleName();
        if (geoObject.getName() != null) {
            name += "\t" + geoObject.getName();
        }
        if (geoObject.getBounds2D() != null) {
            Rectangle2D r = geoObject.getBounds2D();
            printer.println(name + "\twidth:" + r.getWidth() + "\theight:" + r.getHeight()
                    + "\tw:" + r.getMinX() + "\te:" + r.getMaxX()
                    + "\ts:" + r.getMinY() + "\tn:" + r.getMaxY());
        } else {
            printer.println(name);
        }
    }

    private void printTree(int intent, PrintStream printer) {
        for (int i = vector.size() - 1; i >= 0; i--) {
            GeoObject geoObject = (GeoObject)(vector.get(i));
            if (geoObject instanceof GeoSet) {
                printNameAndExtension(geoObject, intent, printer);
                Rectangle2D ext = geoObject.getBounds2D();
                //if (ext != null && ext.getWidth() > 0 && ext.getHeight() > 0) {
                    ((GeoSet)geoObject).printTree(intent + 1, printer);
                //}
            } else {
                printNameAndExtension(geoObject, intent, printer);
            }
        }
        this.informGeoSetChangeListeners(this);
    }
}
