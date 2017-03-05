/*
 * MapEventHandler.java
 *
 * Created on April 14, 2005, 9:34 AM
 */
package ika.gui;

import ika.map.tools.MapTool;
import ika.map.tools.MapToolMouseMotionListener;
import ika.map.tools.PanTool;
import ika.map.tools.SelectionTool;
import ika.map.tools.ZoomInTool;
import ika.map.tools.ZoomOutTool;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.Iterator;

/**
 * MapEventHandler - event listener for MapComponent. Receives key and mouse
 * events.
 *
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich
 */
public class MapEventHandler implements java.awt.event.MouseListener,
        java.awt.event.MouseMotionListener,
        java.awt.KeyEventDispatcher, MouseWheelListener {

    /**
     * Keep track whether the space key is pressed.
     */
    private boolean spaceKeyDown = false;

    /**
     * Keep track whether the meta key is pressed.
     */
    private boolean metaKeyDown = false;

    /**
     * Keep track whether the alt key is pressed.
     */
    private boolean altKeyDown = false;

    /**
     * The MapTool that will be restored when a new MapTool is only temporarily
     * active.
     */
    private MapTool temporarilySuspendedTool = null;

    /**
     * mapTool contains a reference on the currently active MapTool. Exactly one
     * MapTool can be active at any time, but mapTool can be null!
     */
    private MapTool mapTool = null;

    /**
     * The MapComponent for which this MapEventHandler receives and treats
     * events.
     */
    private final MapComponent mapComponent;

    /**
     * Keep track whether the user is currently dragging (i.e. move the mouse
     * while keeping the mouse button pressed). Avoid delegating mouseReleased
     * and mouseClicked events when dragging finishes.
     */
    private boolean dragging = false;

    /**
     * Keep track whether the mouse is currently over the MapComponent.
     */
    private boolean mouseOverComponent = false;

    private final HashSet mouseMotionListeners = new HashSet();

    /**
     * Creates a new instance of MapEventHandler
     *
     * @param mapComponent The MapComponent for which this MapEventHandler
     * receives events.
     */
    public MapEventHandler(MapComponent mapComponent) {
        this.mapComponent = mapComponent;
        mapComponent.addMouseListener(this);
        mapComponent.addMouseMotionListener(this);
        mapComponent.addMouseWheelListener(this);

        KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        kfm.addKeyEventDispatcher(this);
        mapTool = new SelectionTool(mapComponent);
    }

    /**
     * Treat mouse pressed. Inform the current MapTool by calling its mouseDown
     * method.
     *
     * @param evt The mouse event to treat.
     */
    @Override
    public void mousePressed(java.awt.event.MouseEvent evt) {
        mouseOverComponent = true;
        dragging = false;
        if (mapTool != null) {
            mapTool.mouseDown(mapComponent.userToWorldSpace(evt.getPoint()), evt);
        }
    }

    /**
     * Treat mouse drag events. Inform the current MapTool by calling its
     * startDrag method if this is the first event in a dragging sequence. Call
     * updateDrag of the current MapTool if this is in the middle of a dragging
     * sequence.
     *
     * @param evt The mouse event to treat.
     */
    @Override
    public void mouseDragged(java.awt.event.MouseEvent evt) {
        mouseOverComponent = true;
        final Point2D.Double point = mapComponent.userToWorldSpace(evt.getPoint());
        if (mapTool != null) {
            if (dragging == false) {
                mapTool.startDrag(point, evt);
            } else {
                mapTool.updateDrag(point, evt);
            }
        }
        dragging = true;
        this.informMouseMotionListeners(point);
    }

    /**
     * Treat mouse released events. Inform the current MapTool by calling its
     * endDrag method if we have been dragging before.
     *
     * @param evt The mouse event to treat.
     */
    @Override
    public void mouseReleased(java.awt.event.MouseEvent evt) {
        mouseOverComponent = true;
        if (mapTool != null && dragging == true) {
            mapTool.endDrag(mapComponent.userToWorldSpace(evt.getPoint()), evt);
        }
        dragging = false;
    }

    /**
     * Treat mouse clicked events. Inform the current MapTool by calling its
     * mouseClicked method.
     *
     * @param evt The mouse event to treat.
     */
    @Override
    public void mouseClicked(java.awt.event.MouseEvent evt) {
        mouseOverComponent = true;
        if (mapTool != null) {
            mapTool.mouseClicked(mapComponent.userToWorldSpace(evt.getPoint()), evt);
        }
        dragging = false;
    }

    /**
     * Treat mouse entered events. Inform the current MapTool by calling its
     * mouseEntered method.
     *
     * @param evt The mouse event to treat.
     */
    @Override
    public void mouseEntered(java.awt.event.MouseEvent evt) {
        mouseOverComponent = true;
        if (mapTool != null) {
            mapTool.mouseEntered(mapComponent.userToWorldSpace(evt.getPoint()), evt);
        }
    }

    /**
     * Treat mouse exited events. Inform the current MapTool by calling its
     * mouseExited method.
     *
     * @param evt The mouse event to treat.
     */
    @Override
    public void mouseExited(java.awt.event.MouseEvent evt) {
        mouseOverComponent = false;
        if (this.mapTool != null) {
            mapTool.mouseExited(mapComponent.userToWorldSpace(evt.getPoint()), evt);
        }
        informMouseMotionListeners(null);
    }

    /**
     * Treat mouse moved events. Inform the current MapTool by calling its
     * mouseMoved method.
     *
     * @param evt The mouse event to treat.
     */
    @Override
    public void mouseMoved(java.awt.event.MouseEvent evt) {
        mouseOverComponent = true;
        java.awt.geom.Point2D.Double point = mapComponent.userToWorldSpace(evt.getPoint());
        if (mapTool != null) {
            mapTool.mouseMoved(point, evt);
        }
        informMouseMotionListeners(point);
    }

    public void removeMouseMotionListener(MapToolMouseMotionListener listener) {
        mouseMotionListeners.remove(listener);
    }

    public void addMouseMotionListener(MapToolMouseMotionListener listener) {
        mouseMotionListeners.add(listener);
    }

    private void informMouseMotionListeners(Point2D.Double point) {
        Iterator iterator = mouseMotionListeners.iterator();
        while (iterator.hasNext()) {
            MapToolMouseMotionListener listener;
            listener = (MapToolMouseMotionListener) iterator.next();
            listener.mouseMoved(point, this.mapComponent);
        }
    }

    /**
     * Returns the currently active MapTool. May return null.
     *
     * @return Returns a reference to the currently active MapTool.
     */
    public MapTool getMapTool() {
        return mapTool;
    }

    /**
     * Set the active MapTool.
     *
     * @param mapTool The new MapTool.
     * @param rememberCurrentTool If true, the current MapTool will be stored in
     * this.temporarilySuspendedTool.
     */
    public void setMapTool(MapTool mapTool, boolean rememberCurrentTool) {
        if (rememberCurrentTool) {
            this.mapTool.pause();
            temporarilySuspendedTool = this.mapTool;
        } else if (this.mapTool != null) {
            this.mapTool.deactivate();
        }
        this.mapTool = mapTool;
        if (this.mapTool != null) {
            this.mapTool.activate();
            this.mapTool.setDefaultCursor();
        }
    }

    /**
     * Helper method that replaces the current MapTool by the previous MapTool
     * that has been temporarily suspended.
     */
    private void restoreTemporarilySuspendedMapTool() {
        if (temporarilySuspendedTool != null) {
            setMapTool(temporarilySuspendedTool, false);
            mapTool.resume();
            mapTool.setDefaultCursor();
        }
        temporarilySuspendedTool = null;
    }

    /**
     * Determine the new MapTool based on the currently pressed keys.
     *
     * @param keyCode The key code.
     * @return A new MapTool (not the current yet) that can be activated.
     */
    private MapTool getNewMapTool(int keyCode) {
        boolean zoomOutCurrent = mapTool instanceof ZoomOutTool;
        boolean zoomInCurrent = mapTool instanceof ZoomInTool;
        boolean panCurrent = mapTool instanceof PanTool;

        // pan tool with space key
        if (spaceKeyDown && !metaKeyDown && !altKeyDown && !panCurrent) {
            return new PanTool(mapComponent);
        }

        // zoom out with meta and alt key
        if (metaKeyDown && altKeyDown && !zoomOutCurrent) {
            return new ZoomOutTool(mapComponent);
        }

        // change to zoom out with alt key when zoom in was previous tool
        if (altKeyDown && zoomInCurrent) {
            return new ZoomOutTool(mapComponent);
        }

        // zoom in with meta key
        if (metaKeyDown && !zoomInCurrent) {
            return new ZoomInTool(mapComponent);
        }

        return null;
    }

    /**
     * Helper method that keeps track of the pressed special keys (space, meta
     * and alt key).
     *
     * @param keyEvent The key event to analyze.
     */
    private void updateKeyStates(KeyEvent keyEvent) {
        // update spaceKeyDown
        if (keyEvent.getKeyCode() == KeyEvent.VK_SPACE) {
            if (keyEvent.getID() == KeyEvent.KEY_RELEASED) {
                spaceKeyDown = false;
            } else if (keyEvent.getID() == KeyEvent.KEY_PRESSED) {
                spaceKeyDown = true;
            }
        }

        // update metaKeyDown
        if (keyEvent.getKeyCode() == KeyEvent.VK_META) {
            if (keyEvent.getID() == KeyEvent.KEY_RELEASED) {
                metaKeyDown = false;
            } else if (keyEvent.getID() == KeyEvent.KEY_PRESSED) {
                metaKeyDown = true;
            }
        }

        // update altKeyDown
        if (keyEvent.getKeyCode() == KeyEvent.VK_ALT) {
            if (keyEvent.getID() == KeyEvent.KEY_RELEASED) {
                altKeyDown = false;
            } else if (keyEvent.getID() == KeyEvent.KEY_PRESSED) {
                altKeyDown = true;
            }
        }
    }

    /**
     * Callback method required by KeyEventDispatcher interface. This method
     * receives key events before any other component can treat them. The events
     * can be consumed (return true) or be delegated to other listeners (return
     * false).
     *
     * @param keyEvent The new key event.
     * @return True if the key event has been consumed, false otherwise.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent keyEvent) {
        updateKeyStates(keyEvent);

        // treat backspace and delete keys
        if (mapComponent != null && mapComponent.hasFocus()) {
            if (keyEvent.getKeyCode() == KeyEvent.VK_DELETE
                    || keyEvent.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                mapComponent.removeSelectedGeoObjects();
                return true;
            }
        }

        if (!mouseOverComponent) {
            return false;
        }

        /*
        System.out.println("Modifiers: " + keyEvent.getModifiers());
        System.out.println("Code: " + KeyEvent.getKeyText(keyEvent.getKeyCode()));
        System.out.println("Key ID: " + keyEvent.getID());
        System.out.println("Meta: " + metaKeyDown);
        System.out.println("Alt: " + altKeyDown);
        System.out.println("Space: " + spaceKeyDown);
        System.out.println("Is Action Key: " + keyEvent.isActionKey());
        System.out.println();
         */
        boolean keyPressed = keyEvent.getID() == KeyEvent.KEY_PRESSED;
        boolean keyReleased = keyEvent.getID() == KeyEvent.KEY_RELEASED;
        boolean panCurrent = mapTool instanceof PanTool;
        boolean panTemporarilySuspended = temporarilySuspendedTool instanceof PanTool;

        if (keyEvent.getKeyCode() == KeyEvent.VK_SPACE
                || keyEvent.getKeyCode() == KeyEvent.VK_META
                || keyEvent.getKeyCode() == KeyEvent.VK_ALT) {

            if (keyEvent.getKeyCode() == KeyEvent.VK_SPACE && keyReleased
                    && panCurrent && !panTemporarilySuspended) {
                restoreTemporarilySuspendedMapTool();
                return true;
            }

            MapTool newMapTool = this.getNewMapTool(keyEvent.getKeyCode());
            if (newMapTool != null) {
                setMapTool(newMapTool, temporarilySuspendedTool == null);
                return true;
            }

            // restore previous tool if space, meta or alt key was released.
            if (keyReleased) {
                restoreTemporarilySuspendedMapTool();
            }

            // consume space key to avoid problems with accessibility action key
            // should be done better.
            if (keyEvent.getKeyCode() == KeyEvent.VK_SPACE) {
                return true;
            }

        }
        return false;
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        int rotations = e.getWheelRotation();
        Point2D.Double loc = mapComponent.userToWorldSpace(e.getPoint());
        for (int i = 0; i < Math.abs(rotations); i++) {
            if (rotations < 0) {
                mapComponent.zoomIn(loc);
            } else {
                mapComponent.zoomOut(loc);
            }
        }
    }
}
