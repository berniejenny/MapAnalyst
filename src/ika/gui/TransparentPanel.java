package ika.gui;

import ika.utils.Sys;
import java.awt.LayoutManager;
import javax.swing.JPanel;

/**
 * Makes a JPanel transparent when on Mac OS X 10.5 or newer. This is needed for
 * panels in JTabbedPanes.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class TransparentPanel extends JPanel {

    private void conditionalTransparency() {
        if (Sys.isMacOSX_10_5_orHigherWithJava5()) {
            setOpaque(false);
        }
    }

    public TransparentPanel(LayoutManager layout, boolean isDoubleBuffered) {
        super(layout, isDoubleBuffered);
        conditionalTransparency();
    }

    public TransparentPanel(LayoutManager layout) {
        super(layout);
        conditionalTransparency();
    }

    public TransparentPanel(boolean isDoubleBuffered) {
        super(isDoubleBuffered);
        conditionalTransparency();
    }

    public TransparentPanel() {
        conditionalTransparency();
    }
}
