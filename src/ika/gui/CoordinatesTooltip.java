package ika.gui;

import com.jhlabs.map.proj.Projection;
import ika.mapanalyst.Manager;
import ika.proj.ProjectionsManager;
import ika.utils.CoordinateFormatter;
import ika.utils.NumberFormatter;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Point2D;

/**
 *
 * @author Bernhard Jenny, Faculty of Information Technology, Monash University,
 * Melbourne, Australia
 */
class CoordinatesTooltip implements MouseListener, MouseMotionListener {

    private static final int TEXT_SIZE = 11;
    private static final int TEXT_PADDING = 3;
    private static final int CURSOR_OFFSET = 9;
    private static final Color BACKGROUND_COLOR = new Color(255, 255, 255, 150);

    private final MapComponent mapComponent;
    private final Manager manager;
    private Point2D.Double mousePositionWorld = null;
    private Point mousePositionPixel = null;

    public CoordinatesTooltip(MapComponent mapComponent, Manager manager) {
        this.mapComponent = mapComponent;
        this.manager = manager;
        mapComponent.addMouseMotionListener(this);
        mapComponent.addMouseListener(this);
    }

    /**
     * Clear the current coordinates. Should be called after the map zooms.
     */
    public void clearCoordinates() {
        mousePositionWorld = null;
        mousePositionPixel = null;
        mapComponent.repaint();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        updatePosition(e);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        updatePosition(e);
    }

    void paintTooltip(Graphics2D g2d) {
        if (mousePositionPixel == null) {
            return;
        }

        // test whether spherical coordinates for OpenStreetMap should be displayed
        boolean osm = "new".equals(mapComponent.getName())
                && manager != null && manager.isUsingOpenStreetMap();
        if (osm && manager.isPointOnOpenStreetMap(mousePositionWorld) == false) {
            return;
        }

        // format coordinate strings
        String[] str = mapComponent.coordinatesStrings(mousePositionWorld, osm);

        // background rectangle, which is a bit larger than the text
        g2d.setColor(BACKGROUND_COLOR);
        Font font = new Font("SansSerif", Font.PLAIN, TEXT_SIZE);
        int x = mousePositionPixel.x + CURSOR_OFFSET;
        int y = mousePositionPixel.y + CURSOR_OFFSET;
        FontMetrics metrics = g2d.getFontMetrics(font);
        int h = metrics.getHeight();
        int w = Math.max(metrics.stringWidth(str[0]), metrics.stringWidth(str[1]));
        g2d.fillRect(x, y, w + 2 * TEXT_PADDING, 2 * h + TEXT_PADDING);

        // text
        g2d.setColor(Color.DARK_GRAY);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setFont(font);
        g2d.drawString(str[0], x + TEXT_PADDING, y + h);
        g2d.drawString(str[1], x + TEXT_PADDING, y + 2 * h);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        updatePosition(e);
        mapComponent.repaint();
    }

    @Override
    public void mouseExited(MouseEvent e) {
        clearCoordinates();
    }

    private void updatePosition(MouseEvent e) {
        mousePositionPixel = (Point) e.getPoint().clone();
        mousePositionWorld = mapComponent.userToWorldSpace(e.getPoint());
        mapComponent.repaint();
    }
}
