/*
 * MacWindowsManager.java
 *
 * Created on October 25, 2005, 12:09 PM
 *
 */

package ika.gui;

import java.awt.*;

/**
 * Mac OS X specific code. Integrates a Java application to the standard Mac 
 * look and feel.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public final class MacWindowsManager extends MainWindow {

    public static Frame emptyWindow;

    static void updateVisibilityOfEmptyWindow() {
        if (ika.utils.Sys.isMacOSX() && emptyWindow != null) {
            emptyWindow.setVisible(MainWindow.windows.isEmpty());
        }
    }

    /**
     * Creates a new instance of MacWindowsManager 
     */
    public MacWindowsManager() {
        super("<invisible manager window>", false);

        // hide the window: make transparent, undecorate, move out of screen area.
        removeNotify();    // call this otherwise setUndecorated will fail.
        setUndecorated(true);
        setBackground(new Color(1.0F, 1.0F, 1.0F, 0.0F));
        getContentPane().removeAll();
        setFocusable(false);
        setLocation(0, 0);
        setSize(new Dimension(0, 0));
        setResizable(false);
        getRootPane().putClientProperty("Window.style", "small");
        getRootPane().putClientProperty("Window.alpha", new Float(0.0f));
        setAlwaysOnTop(true);
        pack();
    }
}
