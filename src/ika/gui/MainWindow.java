/*
 * MainWindow.java
 *
 * Created on 23. August 2004, 20:50
 */
package ika.gui;

import ch.ethz.karto.gui.LineProjector;
import ch.ethz.karto.gui.MapLine;
import ch.ethz.karto.gui.UngenerateImporter;
import com.fizzysoft.sdu.RecentDocumentsManager;
import com.jhlabs.map.Ellipsoid;
import com.jhlabs.map.proj.ConicProjection;
import com.jhlabs.map.proj.CylindricalEqualAreaProjection;
import com.jhlabs.map.proj.EquidistantCylindricalProjection;
import com.jhlabs.map.proj.LambertConformalConicProjection;
import com.jhlabs.map.proj.MercatorProjection;
import com.jhlabs.map.proj.Projection;
import ika.geo.*;
import ika.geo.osm.OpenStreetMap;
import ika.utils.*;
import ika.map.tools.*;
import ika.transformation.*;
import ika.transformation.robustestimator.*;
import ika.mapanalyst.*;
import ika.geoexport.*;
import ika.proj.ProjectionsManager;
import java.io.*;
import java.awt.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.*;
import java.util.prefs.Preferences;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

/**
 *
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class MainWindow extends javax.swing.JFrame
        implements GeoSetChangeListener,
        GeoSetSelectionChangeListener,
        MapToolActionListener {

    /**
     * ungenerate file with continents outlines
     */
    private static final String CONTINENTS_PATH = "/ika/data/continents.ung";

    // 1 m tolerance when converting bezier curves in new map to straight lines
    private static final double NEW_MAP_PATH_FLATNESS = 1;

    // 0.5 mm tolerance when converting bezier curves in old map to straight lines
    private static final double OLD_MAP_PATH_FLATNESS = 0.0005;

    /**
     * file path to location on hard disk where this document is stored.
     */
    private String filePath = null;
    /**
     * dimension of the window when minimized.
     */
    private java.awt.Dimension packedSize;
    /**
     * Undo/Redo manager.
     */
    private Undo undo = new Undo();
    /**
     * vector holding all windows currently open.
     */
    protected static Vector windows = new Vector();
    /**
     * counts the windows created.
     */
    private static int projectCounter = 1;
    /**
     * non-system-wide clipboard.
     */
    private static byte[] clipboard;
    /**
     * flag: true means document must be saved before closing.
     */
    boolean dirty = false;
    /**
     * data manager
     */
    private Manager manager;
    private static final String linkButtonSelectedLabel = "Unlink";
    private static final String linkButtonDeselectedLabel = "Link";
    /**
     * A text window displaying a report for the last transformation computed.
     */
    private ika.utils.TextWindow transformationReportWin;
    /**
     * A window displaying the coordinates of the currently linked points.
     */
    private JDialog pointsDialog;
    private boolean updatingGUI = false;

    // latitude of true scale for the
    private Double latTrueScaleDeg = new Double(0);

    private RecentDocumentsManager rdm;

    /**
     * Creates new form MainWindow
     */
    public MainWindow(String title, boolean showInWindowsMenu) {

        super(title + (Sys.isWindows() ? " - MapAnalyst" : ""));

        try {
            this.updatingGUI = true;

            // build the GUI
            initRecentDocumentsMenu();
            initComponents();

            // add window to list.
            if (showInWindowsMenu) {
                windows.add(this);
            }
            MainWindow.updateAllMenusOfAllWindows();

            resetManager(new Manager());

            CoordinateFormatter oldFormat, newFormat;
            oldFormat = new CoordinateFormatter("###,##0.0 cm", "###,##0.0", 100);
            newFormat = new CoordinateFormatter("###,##0.00 m", "###,##0.###", 1);
            this.oldMapComponent.setCoordinateFormatter(oldFormat);
            this.newMapComponent.setCoordinateFormatter(newFormat);
            newMapComponent.setDistanceFormatter(new ReferenceDistanceFormatter());

            // register the info panels with the two maps
            this.coordinateInfoPanel.registerWithMapComponent(oldMapComponent);
            this.coordinateInfoPanel.registerWithMapComponent(newMapComponent);
            this.geoObjectInfoPanel.registerWithMapComponent(this.oldMapComponent);
            this.geoObjectInfoPanel.registerWithMapComponent(this.newMapComponent);

            // Don't register the NEW map panel for displaying the local scale and
            // rotation. This would confuse users.
            // this.newMapComponent.addMouseMotionListener(this.localScaleRotationInfoPanel);
            this.oldMapComponent.addMouseMotionListener(this.localScaleRotationInfoPanel);

            // display coordinates in tooltip for the new map
            newMapComponent.setCoordinatesTooltip(new CoordinatesTooltip(newMapComponent, manager));

            // set the initial tool
            this.oldMapComponent.setMapTool(new ZoomInTool(this.oldMapComponent));
            this.newMapComponent.setMapTool(new ZoomInTool(this.newMapComponent));

            // store the minimum size of this window
            this.packedSize = this.getSize();

            // register a ComponentListener to get resize events.
            // the resize-event-handler makes sure the window is not gettting too small.
            this.addComponentListener(new ComponentAdapter() {

                @Override
                public void componentResized(ComponentEvent e) {

                    // Check if either the width or the height are below minimum
                    // and reset size if necessary.
                    // Note: this is not elegant, but SUN recommends doing it that way.
                    int width = getWidth();
                    int height = getHeight();
                    boolean resize = false;

                    if (width < packedSize.width) {
                        resize = true;
                        width = packedSize.width;
                    }
                    if (height < packedSize.height) {
                        resize = true;
                        height = packedSize.height;
                    }
                    if (resize) {
                        setSize(width, height);
                    }
                }
            });

            // maximise the size of this window. Fill the primary screen.
            setExtendedState(JFrame.MAXIMIZED_BOTH);

            // default is the OpenStreetMap as reference map
            manager.setNewMap(new OpenStreetMap(newMapComponent));

            // show everything in the two maps. On macOS wait for window zoom animation to finish.
            {
                int delay = Sys.isMacOSX() ? 1000 : 0;
                javax.swing.Timer timer = new javax.swing.Timer(delay, new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        showAllInNewMap();
                        oldMapComponent.showAll();
                    }
                });
                timer.setRepeats(false);
                timer.start();
            }

            String nl = System.getProperty("line.separator");
            transformationInfoTextArea.setText("Scale:\t-" + nl + "Rotation:\t-");
            undo.setUndoMenuItems(this.undoMenuItem, this.redoMenuItem);
            addUndo(null);

            // hide the GUI for a visualisation method by Mekenkamp that is not recommended
            setMekenkampVisible(false);
            udpateTransformationInfoGUI();

            addWindowListener(new WindowAdapter() {

                @Override
                public void windowClosed(WindowEvent e) {
                    // show the invisible window that shows the menu bar when no other
                    // window is visible
                    if (windows.isEmpty()) {
                        MacWindowsManager.updateVisibilityOfEmptyWindow();
                    }
                }

                @Override
                public void windowOpened(WindowEvent e) {
                    if (windows.isEmpty()) {
                        MacWindowsManager.updateVisibilityOfEmptyWindow();
                    }
                }
            });

            // write all project settings to GUI
            writeGUI();

            // hide the invisible window that shows the menu bar when no other
            // window is visible
            MacWindowsManager.updateVisibilityOfEmptyWindow();

            // add zoom-in and zoom-out actions for alternative keys
            Action zoomInAction = new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    zoomInMenuItemActionPerformed(null);
                }
            };
            Action zoomOutAction = new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    zoomOutMenuItemActionPerformed(null);
                }
            };
            GUIUtil.zoomMenuCommands(zoomInAction, zoomOutAction, getRootPane());

            // hide debug menu
            menuBar.remove(debugMenu);

        } finally {
            updatingGUI = false;
        }
    }

    private void initRecentDocumentsMenu() {
        final String APPNAME = "MapAnalyst";
        rdm = new RecentDocumentsManager() {

            private Preferences getPreferences() {
                return Preferences.userNodeForPackage(MainWindow.class);
            }

            @Override
            protected byte[] readRecentDocs() {
                return getPreferences().getByteArray("RecentDocuments" + APPNAME, null);
            }

            @Override
            protected void writeRecentDocs(byte[] data) {
                getPreferences().putByteArray("RecentDocuments" + APPNAME, data);
            }

            @Override
            protected void openFile(File file, ActionEvent event) {
                if (file != null) {
                    try {
                        openProject(file.getCanonicalPath());
                    } catch (IOException ex) {
                        Logger.getLogger(MainWindow.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }

            @Override
            protected Icon getFileIcon(File file) {
                return null;
            }
        };
        rdm.setMaxRecentDocuments(25);
    }

    private void showAllInNewMap() {
        // when the OpenStreetMap is used, only zoom on existing points and
        // do not show the entire OSM map.
        if (manager.isUsingOpenStreetMap()
                && manager.getNewPointsGeoSet().getNumberOfChildren() > 1) {
            Rectangle2D bounds = manager.getNewPointsGeoSet().getBounds2D();
            double w = bounds.getWidth();
            double h = bounds.getHeight();
            bounds.setFrame(bounds.getMinX() - w / 4, bounds.getMinY() - h / 4, 1.5 * w, 1.5 * h);
            newMapComponent.zoomOnRectangle((Rectangle2D.Double) bounds);
        } else {
            // OSM is not used, zoom on all existing data.
            newMapComponent.showAll();
        }
    }

    private void showAll() {
        oldMapComponent.showAll();
        showAllInNewMap();
    }

    /**
     * Replaces old data manager with new one.
     */
    private void resetManager(Manager newManager) {
        if (newManager == null) {
            return;
        }

        this.manager = newManager;

        this.manager.getOldPointsGeoSet().addGeoSetChangeListener(this);
        this.manager.getNewPointsGeoSet().addGeoSetChangeListener(this);

        this.manager.getOldPointsGeoSet().addGeoSetSelectionChangeListener(this);
        this.manager.getNewPointsGeoSet().addGeoSetSelectionChangeListener(this);

        this.oldMapComponent.removeAllGeoObjects();
        this.newMapComponent.removeAllGeoObjects();
        this.oldMapComponent.addGeoObject(manager.getOldGeoSet());
        this.newMapComponent.addGeoObject(manager.getNewGeoSet());

        localScaleRotationInfoPanel.setManager(manager);
        coordinateInfoPanel.setManager(manager);
        
        CoordinatesTooltip ctt = newMapComponent.getCoordinatesToolTip();
        if (ctt != null) {
            ctt.setManager(manager);
        }

        // create a new MapTool to make sure the tool knows about the new Manager.
        // only for MapTools that have reference to some data of the Manager.
        // not really elegant!
        MapTool oldTool = this.oldMapComponent.getMapTool();
        if (oldTool instanceof PointSetterTool) {
            this.enablePointSetterMapTool(false);
        } else if (oldTool instanceof PolygonTool) {
            this.enablePenMapTool();
        }
    }

    private void enablePointSetterMapTool(boolean showPoints) {
        PointSetterTool oldTool = new PointSetterTool(this.oldMapComponent,
                this.manager.getLinkManager().getUnlinkedPointSymbol());
        PointSetterTool newTool = new PointSetterTool(this.newMapComponent,
                this.manager.getLinkManager().getUnlinkedPointSymbol());

        oldTool.setDestinationGeoSet(this.manager.getOldPointsGeoSet());
        newTool.setDestinationGeoSet(this.manager.getNewPointsGeoSet());
        oldTool.addMapToolActionListener(this);
        newTool.addMapToolActionListener(this);
        this.oldMapComponent.setMapTool(oldTool);
        this.newMapComponent.setMapTool(newTool);
        if (showPoints) {
            this.showPoints();
        }
    }

    private void enableSelectionMapTool(boolean showPoints) {
        SelectionTool oldTool = new SelectionTool(this.oldMapComponent);
        SelectionTool newTool = new SelectionTool(this.newMapComponent);
        oldTool.addMapToolActionListener(this);
        newTool.addMapToolActionListener(this);
        this.oldMapComponent.setMapTool(oldTool);
        this.newMapComponent.setMapTool(newTool);
        if (showPoints) {
            this.showPoints();
        }
    }

    private void enableMoverMapTool(boolean showPoints) {
        MoverTool oldTool = new MoverTool(this.oldMapComponent);
        MoverTool newTool = new MoverTool(this.newMapComponent);
        oldTool.addMapToolActionListener(this);
        newTool.addMapToolActionListener(this);

        this.oldMapComponent.setMapTool(oldTool);
        this.newMapComponent.setMapTool(newTool);

        if (showPoints) {
            this.showPoints();
        }
    }

    private void enablePenMapTool() {
        PolygonTool oldTool = new PolygonTool(this.oldMapComponent);
        PolygonTool newTool = new PolygonTool(this.newMapComponent);
        oldTool.addMapToolActionListener(this);
        newTool.addMapToolActionListener(this);
        GeometryTransformer transformer = this.manager.getGeometryTransformer();
        oldTool.setDestinationGeoSet(transformer.getOldSourceGeoSet());
        newTool.setDestinationGeoSet(transformer.getNewSourceGeoSet());
        this.oldMapComponent.setMapTool(oldTool);
        this.newMapComponent.setMapTool(newTool);
    }

    /**
     * Inform all windows of change in window list
     */
    protected static void updateAllMenusOfAllWindows() {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                Enumeration e = windows.elements();
                while (e.hasMoreElements()) {
                    MainWindow w = (MainWindow) e.nextElement();
                    w.updateAllMenus();
                }
            }
        });
    }

    /**
     * Update all menus of this window.
     */
    private void updateAllMenus() {
        this.updateEditMenu();
        this.updateMapsMenu();
        this.updateWindowMenu();
    }

    /**
     * Update the enabled/disabled state of the items in the edit menu.
     */
    private void updateEditMenu() {
        boolean mapHasSelectedObj
                = this.oldMapComponent.getGeoSet().hasSelectedGeoObjects()
                || this.newMapComponent.getGeoSet().hasSelectedGeoObjects();
        this.deleteMenuItem.setEnabled(mapHasSelectedObj);
        this.copyMenuItem.setEnabled(mapHasSelectedObj);
        this.cutMenuItem.setEnabled(mapHasSelectedObj);
        this.pasteMenuItem.setEnabled(MainWindow.clipboard != null);
    }

    private void updateMapsMenu() {
        OpenStreetMap osm = manager.getOpenStreetMap();
        boolean isUsingOSM = manager.isUsingOpenStreetMap();
        boolean hasNewPoints = manager.getNewPointsGeoSet().getNumberOfChildren() > 0;
        addOSMMenuItem.setEnabled(!isUsingOSM);
        removeOSMMenuItem.setEnabled(isUsingOSM);
        correctOSMMisalignmentBugMenuItem.setEnabled(isUsingOSM && hasNewPoints);
        showOSMGraticuleCheckBoxMenuItem.setEnabled(isUsingOSM);
        showOSMGraticuleCheckBoxMenuItem.setSelected(isUsingOSM && osm.isShowGraticule());
        showOSMPolarCirclesCheckBoxMenuItem.setEnabled(isUsingOSM);
        showOSMPolarCirclesCheckBoxMenuItem.setSelected(isUsingOSM && osm.isShowPolarCircles());
        showOSMTropicsCheckBoxMenuItem.setEnabled(isUsingOSM);
        showOSMTropicsCheckBoxMenuItem.setSelected(isUsingOSM && osm.isShowTropics());

        removeNewRasterImageMenuItem.setEnabled(manager.getNewMap() != null);
        removeOldRasterImageMenuItem.setEnabled(manager.getOldMap() != null);
    }

    /**
     * Update the menu bar of this window. Each window has its own copy of the
     * menu bar.
     */
    private void updateWindowMenu() {

        int nbrMenuItems = this.windowMenu.getMenuComponentCount();
        for (int i = nbrMenuItems - 1; i >= 0; i--) {
            this.windowMenu.remove(i);
        }

        int nbrWindows = MainWindow.windows.size();
        for (int i = 0; i < nbrWindows; i++) {
            MainWindow w = (MainWindow) MainWindow.windows.elementAt(i);
            JMenuItem menuItem;
            if (this == w) {
                menuItem = new JCheckBoxMenuItem(this.getTitle(), true);
            } else {
                menuItem = new JMenuItem(w.getTitle());
            }
            this.windowMenu.add(menuItem);
            menuItem.setName(Integer.toString(i));
            menuItem.addActionListener(new java.awt.event.ActionListener() {

                @Override
                public void actionPerformed(java.awt.event.ActionEvent evt) {
                    JMenuItem menuItem = (JMenuItem) evt.getSource();
                    try {
                        ((JCheckBoxMenuItem) menuItem).setState(true);
                    } catch (ClassCastException ex) {
                    }

                    int id = Integer.parseInt(menuItem.getName());
                    MainWindow w = (MainWindow) MainWindow.windows.elementAt(id);
                    if (w.getExtendedState() == Frame.ICONIFIED) {
                        w.setExtendedState(Frame.NORMAL);
                    }

                    // bring the selected window to the front
                    w.toFront();
                }
            });
        }
        this.windowMenu.invalidate();
        this.menuBar.invalidate();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        toolBarButtonGroup = new javax.swing.ButtonGroup();
        showErrorInMapButtonGroup = new javax.swing.ButtonGroup();
        imageRenderingQualityButtonGroup = new javax.swing.ButtonGroup();
        transformationButtonGroup = new javax.swing.ButtonGroup();
        hampelEstimatorParametersPanel = new javax.swing.JPanel();
        hampelEstimatorATextField = new javax.swing.JTextField();
        hampelEstimatorBTextField = new javax.swing.JTextField();
        hampelEstimatorCTextField = new javax.swing.JTextField();
        hampelEstimatorATextArea = new javax.swing.JTextArea();
        hampelEstimatorBTextArea = new javax.swing.JTextArea();
        hampelEstimatorCTextArea = new javax.swing.JTextArea();
        hampelEstimatorALabel = new javax.swing.JLabel();
        hampelEstimatorBLabel = new javax.swing.JLabel();
        hampelEstimatorCLabel = new javax.swing.JLabel();
        hampelEstimatorMinMaxATextArea = new javax.swing.JTextArea();
        hampelEstimatorMinMaxBTextArea = new javax.swing.JTextArea();
        hampelEstimatorMinMaxCTextArea = new javax.swing.JTextArea();
        vEstimatorParametersPanel = new javax.swing.JPanel();
        vEstimatorELabel = new javax.swing.JLabel();
        vEstimatorETextField = new javax.swing.JTextField();
        vEstimatorETextArea = new javax.swing.JTextArea();
        vEstimatorMinMaxETextArea = new javax.swing.JTextArea();
        vEstimatorKLabel = new javax.swing.JLabel();
        vEstimatorKTextField = new javax.swing.JTextField();
        vEstimatorKTextArea = new javax.swing.JTextArea();
        vEstimatorMinMaxKTextArea = new javax.swing.JTextArea();
        huberEstimatorParametersPanel = new javax.swing.JPanel();
        huberEstimatorKTextField = new javax.swing.JTextField();
        huberEstimatorKTextArea = new javax.swing.JTextArea();
        jLabel1 = new javax.swing.JLabel();
        huberEstimatorMinMaxTextArea = new javax.swing.JTextArea();
        gridOptionsPanel = new javax.swing.JPanel();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        uncertaintyPanel = new javax.swing.JPanel();
        if (ika.utils.Sys.isMacOSX_10_5_orHigherWithJava5())     uncertaintyPanel.setOpaque(false);
        javax.swing.JLabel uncertaintyLabel = new javax.swing.JLabel();
        javax.swing.JTextArea uncertaintyTextArea = new javax.swing.JTextArea();
        distortionGridUncertaintyQuantileSlider = new javax.swing.JSlider();
        exaggerationPanel = new javax.swing.JPanel();
        if (ika.utils.Sys.isMacOSX_10_5_orHigherWithJava5())
        exaggerationPanel.setOpaque(false);
        javax.swing.JLabel exaggerationLabel = new javax.swing.JLabel();
        distortionGridExaggerationFormattedTextField = new javax.swing.JFormattedTextField();
        javax.swing.JTextArea exaggerationTextArea = new javax.swing.JTextArea();
        positionPanel = new javax.swing.JPanel();
        if (ika.utils.Sys.isMacOSX_10_5_orHigherWithJava5())
        positionPanel.setOpaque(false);
        javax.swing.JLabel gridWestLabel = new javax.swing.JLabel();
        javax.swing.JLabel gridSouthLabel = new javax.swing.JLabel();
        distortionGridOffsetXFormattedTextField = new javax.swing.JFormattedTextField();
        distortionGridOffsetYFormattedTextField = new javax.swing.JFormattedTextField();
        javax.swing.JTextArea gridPositionTextArea = new javax.swing.JTextArea();
        pointTablePanel = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        pointsTable = new javax.swing.JTable();
        projectionPanel = new javax.swing.JPanel();
        javax.swing.JLabel jLabel5 = new javax.swing.JLabel();
        projectionComboBox = new javax.swing.JComboBox();
        compareProjectionsButton = new javax.swing.JButton();
        longitudeSlider = new javax.swing.JSlider();
        jLabel6 = new javax.swing.JLabel();
        projectionMeanDistanceLabel = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        projectionMeanLongitudeLabel = new javax.swing.JLabel();
        longitudeFormattedTextField = new javax.swing.JFormattedTextField();
        projectionLiveUpdateCheckBox = new javax.swing.JCheckBox();
        automaticCentralLongitudeRadioButton = new javax.swing.JRadioButton();
        customCentralLongitudeRadioButton = new javax.swing.JRadioButton();
        projectionOptionsButton = new javax.swing.JButton();
        projectionDescriptionTextArea = new javax.swing.JTextArea();
        mapComponent = new ch.ethz.karto.gui.MapComponent();
        centralLongitudeButtonGroup = new javax.swing.ButtonGroup();
        oldMapDisplayUnitButtonGroup = new javax.swing.ButtonGroup();
        splitPane = new javax.swing.JSplitPane();
        oldMapPanel = new javax.swing.JPanel();
        oldMapLabelPanel = new javax.swing.JPanel();
        oldMapLabel = new javax.swing.JLabel();
        oldMapComponent = new ika.gui.MapComponent();
        newMapPanel = new javax.swing.JPanel();
        newMapComponent = new ika.gui.MapComponent();
        newMapLabelPanel = new javax.swing.JPanel();
        newMapLabel = new javax.swing.JLabel();
        osmCopyrightLabel = new javax.swing.JLabel();
        topPanel = new javax.swing.JPanel();
        topLeftPanel = new javax.swing.JPanel();
        computeButton = new javax.swing.JButton();
        pointsToolBar = new javax.swing.JToolBar();
        rectangularSelectionToggleButton = new javax.swing.JToggleButton();
        setPointToggleButton = new javax.swing.JToggleButton();
        panPointsToggleButton = new javax.swing.JToggleButton();
        navigationToolBar = new javax.swing.JToolBar();
        zoomInToggleButton = new javax.swing.JToggleButton();
        zoomOutToggleButton = new javax.swing.JToggleButton();
        handToggleButton = new javax.swing.JToggleButton();
        distanceToggleButton = new javax.swing.JToggleButton();
        penToggleButton = new javax.swing.JToggleButton();
        jSeparator19 = new javax.swing.JToolBar.Separator();
        showAllButton = new javax.swing.JButton();
        infoToolBar = new javax.swing.JToolBar();
        coordinateInfoPanel = new ika.gui.CoordinateInfoPanel();
        geoObjectInfoPanel = new ika.gui.GeoObjectInfoPanel();
        localScaleRotationInfoPanel = new ika.gui.LocalScaleRotationInfoPanel();
        jSeparator20 = new javax.swing.JSeparator();
        bottomPanel1 = new javax.swing.JPanel();
        bottomPanel2 = new javax.swing.JPanel();
        visualizationTabbedPane = new javax.swing.JTabbedPane();
        DistortionGridPanel = new TransparentPanel();
        distortionGridPanel = new TransparentPanel();
        distortionGridVisibleCheckBox = new javax.swing.JCheckBox();
        distortionControlSizePanel = new TransparentPanel();
        javax.swing.JLabel distortionGridMeshSizeLabel = new javax.swing.JLabel();
        javax.swing.JLabel distortionGridSmoothnessLabel = new javax.swing.JLabel();
        distortionGridSmoothnessSlider = new javax.swing.JSlider();
        javax.swing.JLabel distortionGridLabelLabel = new javax.swing.JLabel();
        distortionGridExtensionComboBox = new javax.swing.JComboBox();
        distortionGridLabelComboBox = new javax.swing.JComboBox();
        distortionGridLabelSequenceComboBox = new javax.swing.JComboBox();
        javax.swing.JLabel distortionGridFrequencyLabel = new javax.swing.JLabel();
        distortionGridLineAppearancePanel = new ika.gui.LineAppearancePanel();
        javax.swing.JLabel jLabel2 = new javax.swing.JLabel();
        distortionGridMeshSizeNumberField = new ika.gui.NumberField();
        gridUnitComboBox = new javax.swing.JComboBox();
        javax.swing.JLabel jLabel8 = new javax.swing.JLabel();
        javax.swing.JLabel jLabel9 = new javax.swing.JLabel();
        distortionGridUncertaintyAlphaSlider = new javax.swing.JSlider();
        distortionGridShowUndistortedCheckBox = new javax.swing.JCheckBox();
        gridExaggerationWarningLabel = new javax.swing.JLabel();
        gridOptionsButton = new javax.swing.JButton();
        ErrorVectorsPanel = new TransparentPanel();
        errorVectorsPanel = new TransparentPanel();
        errorVectorsVisibleCheckBox = new javax.swing.JCheckBox();
        errorVectorScalePanel = new TransparentPanel();
        errorVectorOutlierPanel = new TransparentPanel();
        errorVectorsOutliersCheckBox = new javax.swing.JCheckBox();
        errorVectorsOutliersColorButton = new ika.gui.ColorButton();
        errorVectorsShowComboBox = new javax.swing.JComboBox();
        errorVectorsScalePanel = new TransparentPanel();
        errorVectorsScaleLabel = new javax.swing.JLabel();
        errorVectorsScaleNumberField = new ika.gui.NumberField();
        errorVectorsLineAppearancePanel = new ika.gui.LineAppearancePanel();
        errorVectorsFillCirclesCheckBox = new javax.swing.JCheckBox();
        errorVectorsFillColorButton = new ika.gui.ColorButton();
        IsoscalesPanel = new TransparentPanel();
        isoscalesPanel = new TransparentPanel();
        isolinesScaleIntervalLabel = new javax.swing.JLabel();
        isolinesScaleLabel = new javax.swing.JLabel();
        isolinesRotationLabel = new javax.swing.JLabel();
        isolinesRotationIntervalLabel = new javax.swing.JLabel();
        isolinesScaleVisibleCheckBox = new javax.swing.JCheckBox();
        isolinesRotationVisibleCheckBox = new javax.swing.JCheckBox();
        isolinesScaleAppearancePanel = new ika.gui.LineAppearancePanel();
        isolinesRotationAppearancePanel = new ika.gui.LineAppearancePanel();
        jPanel1 = new TransparentPanel();
        isolinesSmoothnessLabel = new javax.swing.JLabel();
        isolinesRadiusNumberField = new ika.gui.NumberField();
        jLabel3 = new javax.swing.JLabel();
        isolinesScaleIntervalNumberField = new ika.gui.NumberField();
        isolinesRotationIntervalNumberField = new ika.gui.NumberField();
        DrawingPanel = new TransparentPanel();
        drawingPanel = new TransparentPanel();
        drawingVisibleCheckBox = new javax.swing.JCheckBox();
        drawingInfoTextArea = new javax.swing.JTextArea();
        drawingImportButton = new javax.swing.JButton();
        drawingClearButton = new javax.swing.JButton();
        CirclePanel = new TransparentPanel();
        circlePanel = new TransparentPanel();
        circlesVisibleCheckBox = new javax.swing.JCheckBox();
        circlesScalePanel = new javax.swing.JPanel();
        circlesScaleLabel = new javax.swing.JLabel();
        circleScaleSlider = new javax.swing.JSlider();
        circlesInfoTextArea = new javax.swing.JTextArea();
        circlesInfoHeader = new javax.swing.JLabel();
        circlesLineAppearancePanel = new ika.gui.LineAppearancePanel();
        linksPanel = new javax.swing.JPanel();
        linkNamePanel = new javax.swing.JPanel();
        linkToggleButton = new javax.swing.JToggleButton();
        // linkToggleButton.putClientProperty("JButton.buttonType", "roundRect");
        linkNameLabel = new javax.swing.JLabel();
        nameLabel = new javax.swing.JLabel();
        linkNameButton = new javax.swing.JButton();
        oldMapCoordsTitleLabel = new javax.swing.JLabel();
        pointOldYLabel = new javax.swing.JLabel();
        xLabel = new javax.swing.JLabel();
        pointOldXLabel = new javax.swing.JLabel();
        yLabel = new javax.swing.JLabel();
        pointNewXLabel = new javax.swing.JLabel();
        newMapCoordsTitleLabel = new javax.swing.JLabel();
        pointNewYLabel = new javax.swing.JLabel();
        jSeparator23 = new javax.swing.JSeparator();
        jLabel4 = new javax.swing.JLabel();
        jSeparator21 = new javax.swing.JSeparator();
        transformationInfoPanel = new javax.swing.JPanel();
        transformationLabel = new javax.swing.JLabel();
        transformationInfoTextArea = new javax.swing.JTextArea();
        menuBar = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        newProjectMenuItem = new javax.swing.JMenuItem();
        openProjectMenuItem = new javax.swing.JMenuItem();
        openRecentMenu = rdm.createOpenRecentMenu();
        jSeparator5 = new javax.swing.JSeparator();
        closeProjectMenuItem = new javax.swing.JMenuItem();
        saveProjectMenuItem = new javax.swing.JMenuItem();
        saveProjectAsMenuItem = new javax.swing.JMenuItem();
        jSeparator8 = new javax.swing.JSeparator();
        importOldMapMenuItem = new javax.swing.JMenuItem();
        importNewMapMenuItem = new javax.swing.JMenuItem();
        importPointsMenu = new javax.swing.JMenu();
        importLinkedPointsMenuItem = new javax.swing.JMenuItem();
        jSeparator15 = new javax.swing.JSeparator();
        importOldPointsMenuItem = new javax.swing.JMenuItem();
        importNewPointsMenuItem = new javax.swing.JMenuItem();
        jSeparator2 = new javax.swing.JSeparator();
        exportOldMapGraphicsMenu = new javax.swing.JMenu();
        exportOldShapeMenuItem = new javax.swing.JMenuItem();
        exportOldSVGMenuItem = new javax.swing.JMenuItem();
        exportOldWMFMenuItem = new javax.swing.JMenuItem();
        exportOldUngenerateMenuItem = new javax.swing.JMenuItem();
        exportOldDXFMenuItem = new javax.swing.JMenuItem();
        exportOldJPEGMenuItem = new javax.swing.JMenuItem();
        exportOldPNGMenuItem = new javax.swing.JMenuItem();
        exportNewMapGraphicsMenu = new javax.swing.JMenu();
        exportNewShapeMenuItem = new javax.swing.JMenuItem();
        exportNewSVGMenuItem = new javax.swing.JMenuItem();
        exportNewWMFMenuItem = new javax.swing.JMenuItem();
        exportNewUngenerateMenuItem = new javax.swing.JMenuItem();
        exportNewDXFMenuItem = new javax.swing.JMenuItem();
        exportNewJPEGMenuItem = new javax.swing.JMenuItem();
        exportNewPNGMenuItem = new javax.swing.JMenuItem();
        exportPointsMenu = new javax.swing.JMenu();
        exportOldPointsMenuItem = new javax.swing.JMenuItem();
        exportNewPointsMenuItem = new javax.swing.JMenuItem();
        jSeparator16 = new javax.swing.JSeparator();
        exportLinkedPointsMenuItem = new javax.swing.JMenuItem();
        exportLinkedPointsAndVectorsInNewMap = new javax.swing.JMenuItem();
        exportLinkedPointsInPixelsMenuItem = new javax.swing.JMenuItem();
        exitMenuSeparator = new javax.swing.JSeparator();
        exitMenuItem = new javax.swing.JMenuItem();
        editMenu = new javax.swing.JMenu();
        undoMenuItem = new javax.swing.JMenuItem();
        redoMenuItem = new javax.swing.JMenuItem();
        javax.swing.JSeparator jSeparator3 = new javax.swing.JSeparator();
        cutMenuItem = new javax.swing.JMenuItem();
        copyMenuItem = new javax.swing.JMenuItem();
        pasteMenuItem = new javax.swing.JMenuItem();
        deleteMenuItem = new javax.swing.JMenuItem();
        javax.swing.JSeparator separator = new javax.swing.JSeparator();
        selectPointsMenuItem = new javax.swing.JMenuItem();
        selectUnlinkedPointsMenuItem = new javax.swing.JMenuItem();
        deselectPointsMenuItem = new javax.swing.JMenuItem();
        javax.swing.JPopupMenu.Separator jSeparator7 = new javax.swing.JPopupMenu.Separator();
        findLinkMenuItem = new javax.swing.JMenuItem();
        javax.swing.JPopupMenu.Separator jSeparator13 = new javax.swing.JPopupMenu.Separator();
        placePointMenuItem = new javax.swing.JMenuItem();
        javax.swing.JPopupMenu.Separator jSeparator10 = new javax.swing.JPopupMenu.Separator();
        showPointListMenuItem = new javax.swing.JMenuItem();
        javax.swing.JPopupMenu.Separator jSeparator1 = new javax.swing.JPopupMenu.Separator();
        linkedPointsColorMenuItem = new javax.swing.JMenuItem();
        unlinkedPointsColorMenuItem = new javax.swing.JMenuItem();
        pointSymbolMenu = new javax.swing.JMenu();
        pointSymbol1MenuItem = new javax.swing.JMenuItem();
        pointSymbol2MenuItem = new javax.swing.JMenuItem();
        pointSymbol3MenuItem = new javax.swing.JMenuItem();
        pointSymbol4MenuItem = new javax.swing.JMenuItem();
        pointSymbol5MenuItem = new javax.swing.JMenuItem();
        pointSymbol6MenuItem = new javax.swing.JMenuItem();
        mapsMenu = new javax.swing.JMenu();
        mapSizeMenuItem = new javax.swing.JMenuItem();
        jSeparator14 = new javax.swing.JPopupMenu.Separator();
        removeOldRasterImageMenuItem = new javax.swing.JMenuItem();
        removeNewRasterImageMenuItem = new javax.swing.JMenuItem();
        javax.swing.JPopupMenu.Separator jSeparator17 = new javax.swing.JPopupMenu.Separator();
        addOSMMenuItem = new javax.swing.JMenuItem();
        removeOSMMenuItem = new javax.swing.JMenuItem();
        javax.swing.JPopupMenu.Separator jSeparator22 = new javax.swing.JPopupMenu.Separator();
        javax.swing.JMenu osmMenu = new javax.swing.JMenu();
        showOSMGraticuleCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        showOSMTropicsCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        showOSMPolarCirclesCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        javax.swing.JPopupMenu.Separator jSeparator24 = new javax.swing.JPopupMenu.Separator();
        correctOSMMisalignmentBugMenuItem = new javax.swing.JMenuItem();
        analysisMenu = new javax.swing.JMenu();
        computeMenuItem = new javax.swing.JMenuItem();
        showReportMenuItem = new javax.swing.JMenuItem();
        jSeparator11 = new javax.swing.JSeparator();
        transformationMenu = new javax.swing.JMenu();
        helmertCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        affine5CheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        affine6CheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        robustEstimatorMenu = new javax.swing.JMenu();
        huberEstimatorCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        setHuberEstimatorParametersMenuItem = new javax.swing.JMenuItem();
        jSeparator4 = new javax.swing.JSeparator();
        vEstimatorCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        setVEstimatorParametersMenuItem = new javax.swing.JMenuItem();
        jSeparator18 = new javax.swing.JSeparator();
        hampelEstimatorCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        setHampelEstimatorParametersMenuItem = new javax.swing.JMenuItem();
        compareTransformationsMenuItem = new javax.swing.JMenuItem();
        jSeparator6 = new javax.swing.JSeparator();
        projectionMenuItem = new javax.swing.JMenuItem();
        projectionSeparator = new javax.swing.JPopupMenu.Separator();
        showErrorInOldMapCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        showErrorInNewMapCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        viewMenu = new javax.swing.JMenu();
        zoomInMenuItem = new javax.swing.JMenuItem();
        zoomOutMenuItem = new javax.swing.JMenuItem();
        jSeparator12 = new javax.swing.JSeparator();
        showAllMenuItem = new javax.swing.JMenuItem();
        showAllOldMenuItem = new javax.swing.JMenuItem();
        showAllNewMenuItem = new javax.swing.JMenuItem();
        jSeparator9 = new javax.swing.JSeparator();
        showPointsCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        showOldCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        showNewCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        jSeparator25 = new javax.swing.JPopupMenu.Separator();
        oldMapCoordinateDisplayUnitMenu = new javax.swing.JMenu();
        oldUnitCmCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        oldUnitMCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        oldUnitInchCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        oldUnitPxCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        windowMenu = new javax.swing.JMenu();
        helpMenu = new javax.swing.JMenu();
        infoMenuItem = new javax.swing.JMenuItem();
        debugMenu = new javax.swing.JMenu();
        warpMenuItem = new javax.swing.JMenuItem();

        hampelEstimatorParametersPanel.setLayout(new java.awt.GridBagLayout());

        hampelEstimatorATextField.setPreferredSize(new java.awt.Dimension(50, 20));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 8, 0, 0);
        hampelEstimatorParametersPanel.add(hampelEstimatorATextField, gridBagConstraints);

        hampelEstimatorBTextField.setPreferredSize(new java.awt.Dimension(50, 20));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(12, 8, 0, 0);
        hampelEstimatorParametersPanel.add(hampelEstimatorBTextField, gridBagConstraints);

        hampelEstimatorCTextField.setPreferredSize(new java.awt.Dimension(50, 20));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(12, 8, 0, 0);
        hampelEstimatorParametersPanel.add(hampelEstimatorCTextField, gridBagConstraints);

        hampelEstimatorATextArea.setEditable(false);
        hampelEstimatorATextArea.setText("If 'a' is small then uncertain points have a smaller influence on \nthe transformation, and vice versa.\nDefault value for 'a' is 1");
        hampelEstimatorATextArea.setFocusable(false);
        hampelEstimatorATextArea.setOpaque(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 0, 0);
        hampelEstimatorParametersPanel.add(hampelEstimatorATextArea, gridBagConstraints);

        hampelEstimatorBTextArea.setEditable(false);
        hampelEstimatorBTextArea.setText("Parameter 'b' marks a split point, where the weight of outlying\npoints are reduced even more. 'b' must be in-between 'a' and 'c'.\nDefault value for 'b' is 2");
        hampelEstimatorBTextArea.setFocusable(false);
        hampelEstimatorBTextArea.setOpaque(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 0, 0);
        hampelEstimatorParametersPanel.add(hampelEstimatorBTextArea, gridBagConstraints);

        hampelEstimatorCTextArea.setEditable(false);
        hampelEstimatorCTextArea.setText("Parameter 'c' is a limit beyond which data points aren't considered\nfor the computation.\nDefault value for 'c' is 4");
        hampelEstimatorCTextArea.setFocusable(false);
        hampelEstimatorCTextArea.setOpaque(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 0, 0);
        hampelEstimatorParametersPanel.add(hampelEstimatorCTextArea, gridBagConstraints);

        hampelEstimatorALabel.setText("Hampel Estimator a:");
        hampelEstimatorALabel.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        hampelEstimatorParametersPanel.add(hampelEstimatorALabel, gridBagConstraints);

        hampelEstimatorBLabel.setText("Hampel Estimator b:");
        hampelEstimatorBLabel.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(12, 0, 0, 0);
        hampelEstimatorParametersPanel.add(hampelEstimatorBLabel, gridBagConstraints);

        hampelEstimatorCLabel.setText("Hampel Estimator c:");
        hampelEstimatorCLabel.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(12, 0, 0, 0);
        hampelEstimatorParametersPanel.add(hampelEstimatorCLabel, gridBagConstraints);

        hampelEstimatorMinMaxATextArea.setEditable(false);
        hampelEstimatorMinMaxATextArea.setText("MIN MAX");
        hampelEstimatorMinMaxATextArea.setFocusable(false);
        hampelEstimatorMinMaxATextArea.setOpaque(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 0, 0);
        hampelEstimatorParametersPanel.add(hampelEstimatorMinMaxATextArea, gridBagConstraints);

        hampelEstimatorMinMaxBTextArea.setEditable(false);
        hampelEstimatorMinMaxBTextArea.setText("MIN MAX");
        hampelEstimatorMinMaxBTextArea.setFocusable(false);
        hampelEstimatorMinMaxBTextArea.setOpaque(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 0, 0);
        hampelEstimatorParametersPanel.add(hampelEstimatorMinMaxBTextArea, gridBagConstraints);

        hampelEstimatorMinMaxCTextArea.setEditable(false);
        hampelEstimatorMinMaxCTextArea.setText("MIN MAX");
        hampelEstimatorMinMaxCTextArea.setFocusable(false);
        hampelEstimatorMinMaxCTextArea.setOpaque(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 0, 0);
        hampelEstimatorParametersPanel.add(hampelEstimatorMinMaxCTextArea, gridBagConstraints);

        vEstimatorParametersPanel.setLayout(new java.awt.GridBagLayout());

        vEstimatorELabel.setText("V Estimator e:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(12, 0, 0, 0);
        vEstimatorParametersPanel.add(vEstimatorELabel, gridBagConstraints);

        vEstimatorETextField.setMinimumSize(new java.awt.Dimension(50, 20));
        vEstimatorETextField.setPreferredSize(new java.awt.Dimension(50, 20));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(12, 8, 0, 0);
        vEstimatorParametersPanel.add(vEstimatorETextField, gridBagConstraints);

        vEstimatorETextArea.setEditable(false);
        vEstimatorETextArea.setText("The parameter 'e' is the degree of contamination.\nA small value means a slight contamination, and\nvice versa.\nDefault value for point data with slight contamination:\n0.0 ... 0.3\nDefault value for point data with average contamination:\n0.3 ... 0.7\nDefault value for point data with high contamination:\n0.0 ... 0.3");
        vEstimatorETextArea.setFocusable(false);
        vEstimatorETextArea.setOpaque(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 0, 0);
        vEstimatorParametersPanel.add(vEstimatorETextArea, gridBagConstraints);

        vEstimatorMinMaxETextArea.setEditable(false);
        vEstimatorMinMaxETextArea.setText("MIN MAX");
        vEstimatorMinMaxETextArea.setFocusable(false);
        vEstimatorMinMaxETextArea.setOpaque(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 0, 0);
        vEstimatorParametersPanel.add(vEstimatorMinMaxETextArea, gridBagConstraints);

        vEstimatorKLabel.setText("V Estimator k:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        vEstimatorParametersPanel.add(vEstimatorKLabel, gridBagConstraints);

        vEstimatorKTextField.setMinimumSize(new java.awt.Dimension(50, 20));
        vEstimatorKTextField.setPreferredSize(new java.awt.Dimension(50, 20));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 8, 0, 0);
        vEstimatorParametersPanel.add(vEstimatorKTextField, gridBagConstraints);

        vEstimatorKTextArea.setEditable(false);
        vEstimatorKTextArea.setText("If 'k' is small then uncertain points have a smaller\ninfluence on the transformation, and vice versa.\nDefault value for 'k' is 1.5");
        vEstimatorKTextArea.setFocusable(false);
        vEstimatorKTextArea.setOpaque(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 0, 0);
        vEstimatorParametersPanel.add(vEstimatorKTextArea, gridBagConstraints);

        vEstimatorMinMaxKTextArea.setEditable(false);
        vEstimatorMinMaxKTextArea.setText("MIN MAX");
        vEstimatorMinMaxKTextArea.setFocusable(false);
        vEstimatorMinMaxKTextArea.setOpaque(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 0, 0);
        vEstimatorParametersPanel.add(vEstimatorMinMaxKTextArea, gridBagConstraints);

        huberEstimatorParametersPanel.setLayout(new java.awt.GridBagLayout());

        huberEstimatorKTextField.setMinimumSize(new java.awt.Dimension(40, 20));
        huberEstimatorKTextField.setPreferredSize(new java.awt.Dimension(40, 20));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 8, 0, 0);
        huberEstimatorParametersPanel.add(huberEstimatorKTextField, gridBagConstraints);

        huberEstimatorKTextArea.setEditable(false);
        huberEstimatorKTextArea.setText("If 'k' is small then uncertain points have a smaller\ninfluence on the transformation, and vice versa.\nDefault value for 'k' is 1.5");
        huberEstimatorKTextArea.setFocusable(false);
        huberEstimatorKTextArea.setOpaque(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 0, 0);
        huberEstimatorParametersPanel.add(huberEstimatorKTextArea, gridBagConstraints);

        jLabel1.setText("Huber Estimator k:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        huberEstimatorParametersPanel.add(jLabel1, gridBagConstraints);

        huberEstimatorMinMaxTextArea.setEditable(false);
        huberEstimatorMinMaxTextArea.setText("MIN MAX");
        huberEstimatorMinMaxTextArea.setFocusable(false);
        huberEstimatorMinMaxTextArea.setOpaque(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 0, 0);
        huberEstimatorParametersPanel.add(huberEstimatorMinMaxTextArea, gridBagConstraints);

        gridOptionsPanel.setLayout(new java.awt.BorderLayout());

        uncertaintyPanel.setLayout(new java.awt.GridBagLayout());

        uncertaintyLabel.setText("Uncertainty Quantile");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 3);
        uncertaintyPanel.add(uncertaintyLabel, gridBagConstraints);

        uncertaintyTextArea.setEditable(false);
        uncertaintyTextArea.setColumns(20);
        uncertaintyTextArea.setFont(new java.awt.Font("SansSerif", 0, 11)); // NOI18N
        uncertaintyTextArea.setLineWrap(true);
        uncertaintyTextArea.setRows(10);
        uncertaintyTextArea.setText("Uncertain areas of the distortion grid are shown with transparency. Use the \"Uncertain Areas\" slider in the main window to adjust the transparency value.\n\nGrid nodes are considered uncertain if they are far away from any control point. To decide whether a grid node is far away, the distance to the closest control point is computed and then compared to a reference distance. To compute this reference distance, for each control point its shortest distance is computed, then a quantile of all shortest distances is computed. The upper quartile at 75% is the default quantile.");
        uncertaintyTextArea.setWrapStyleWord(true);
        uncertaintyTextArea.setOpaque(false);
        uncertaintyTextArea.setPreferredSize(new java.awt.Dimension(400, 140));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        uncertaintyPanel.add(uncertaintyTextArea, gridBagConstraints);

        distortionGridUncertaintyQuantileSlider.setMajorTickSpacing(25);
        distortionGridUncertaintyQuantileSlider.setMinorTickSpacing(5);
        distortionGridUncertaintyQuantileSlider.setPaintLabels(true);
        distortionGridUncertaintyQuantileSlider.setPaintTicks(true);
        { java.util.Hashtable labels = distortionGridUncertaintyQuantileSlider.createStandardLabels(distortionGridUncertaintyQuantileSlider.getMajorTickSpacing()); java.util.Enumeration e = labels.elements(); while(e.hasMoreElements()) {     javax.swing.JComponent comp = (javax.swing.JComponent)e.nextElement();     if (comp instanceof javax.swing.JLabel) {         javax.swing.JLabel label = (javax.swing.JLabel)(comp);         label.setText(label.getText() + "%");     } } distortionGridUncertaintyQuantileSlider.setLabelTable(labels); }
        distortionGridUncertaintyQuantileSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                distortionGridUncertaintyQuantileSliderStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        uncertaintyPanel.add(distortionGridUncertaintyQuantileSlider, gridBagConstraints);

        jTabbedPane1.addTab("Uncertainty", uncertaintyPanel);

        exaggerationPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        exaggerationPanel.setLayout(new java.awt.GridBagLayout());

        exaggerationLabel.setText("Exaggeration Factor");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 3);
        exaggerationPanel.add(exaggerationLabel, gridBagConstraints);

        distortionGridExaggerationFormattedTextField.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter()));
        distortionGridExaggerationFormattedTextField.setPreferredSize(new java.awt.Dimension(100, 28));
        distortionGridExaggerationFormattedTextField.setValue(new Double(1));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        exaggerationPanel.add(distortionGridExaggerationFormattedTextField, gridBagConstraints);

        exaggerationTextArea.setEditable(false);
        exaggerationTextArea.setColumns(20);
        exaggerationTextArea.setFont(new java.awt.Font("SansSerif", 0, 11)); // NOI18N
        exaggerationTextArea.setLineWrap(true);
        exaggerationTextArea.setRows(10);
        exaggerationTextArea.setText("Undulations in the distortion grid are enforced when the exaggeration factor is larger than 1 and diminished when smaller than 1.\n\nThe exaggeration factor should be set to 1 in most cases. A factor different to 1 is only useful when maps with very low distortion are analyzed. In such cases, an exaggeration factor larger than 1 can show otherwise unseen undulations.");
        exaggerationTextArea.setWrapStyleWord(true);
        exaggerationTextArea.setOpaque(false);
        exaggerationTextArea.setPreferredSize(new java.awt.Dimension(400, 140));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        exaggerationPanel.add(exaggerationTextArea, gridBagConstraints);

        jTabbedPane1.addTab("Exaggeration", exaggerationPanel);

        positionPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        positionPanel.setLayout(new java.awt.GridBagLayout());

        gridWestLabel.setText("Horizontal Offset");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 3);
        positionPanel.add(gridWestLabel, gridBagConstraints);

        gridSouthLabel.setText("Vertical Offset");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 3);
        positionPanel.add(gridSouthLabel, gridBagConstraints);

        distortionGridOffsetXFormattedTextField.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter(new java.text.DecimalFormat("#,##0.######"))));
        distortionGridOffsetXFormattedTextField.setPreferredSize(new java.awt.Dimension(200, 28));
        distortionGridOffsetXFormattedTextField.setValue(new Double(0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        positionPanel.add(distortionGridOffsetXFormattedTextField, gridBagConstraints);

        distortionGridOffsetYFormattedTextField.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter(new java.text.DecimalFormat("#,##0.######"))));
        distortionGridOffsetYFormattedTextField.setPreferredSize(new java.awt.Dimension(200, 28));
        distortionGridOffsetYFormattedTextField.setValue(new Double(0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        positionPanel.add(distortionGridOffsetYFormattedTextField, gridBagConstraints);

        gridPositionTextArea.setEditable(false);
        gridPositionTextArea.setColumns(20);
        gridPositionTextArea.setFont(new java.awt.Font("SansSerif", 0, 11)); // NOI18N
        gridPositionTextArea.setLineWrap(true);
        gridPositionTextArea.setRows(11);
        gridPositionTextArea.setText("Grid lines are usually placed at multiples of the mesh size. If the horizontal or vertical offset differs from 0, grid lines are computed at intermediate positions.\n\nIf the offset values are larger than the mesh size, the grid is shifted by the remainder of the following division: \noffset / mesh size.\n\nFor a grid along lines of equal longitude and latitude, the offsets must be in degrees.");
        gridPositionTextArea.setWrapStyleWord(true);
        gridPositionTextArea.setOpaque(false);
        gridPositionTextArea.setPreferredSize(new java.awt.Dimension(400, 154));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        positionPanel.add(gridPositionTextArea, gridBagConstraints);

        jTabbedPane1.addTab("Offset", positionPanel);

        gridOptionsPanel.add(jTabbedPane1, java.awt.BorderLayout.CENTER);

        pointTablePanel.setLayout(new java.awt.BorderLayout());

        jScrollPane1.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        jScrollPane1.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        pointsTable.setModel(new PointsTableModel());
        jScrollPane1.setViewportView(pointsTable);
        pointsTable.getSelectionModel().addListSelectionListener(new PointsTableSelectionListener());

        pointTablePanel.add(jScrollPane1, java.awt.BorderLayout.CENTER);

        projectionPanel.setLayout(new java.awt.GridBagLayout());

        jLabel5.setText("Projection");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 5);
        projectionPanel.add(jLabel5, gridBagConstraints);

        projectionComboBox.setMaximumRowCount(100);
        projectionComboBox.setModel(new DefaultComboBoxModel(ProjectionsManager.getProjections()));
        projectionComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                projectionComboBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        projectionPanel.add(projectionComboBox, gridBagConstraints);

        compareProjectionsButton.setText("Find Best Fit Projection…");
        compareProjectionsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                compareProjectionsButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 15;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 30, 0, 0);
        projectionPanel.add(compareProjectionsButton, gridBagConstraints);
        compareProjectionsButton.setVisible(false);

        longitudeSlider.setMajorTickSpacing(45);
        longitudeSlider.setMaximum(180);
        longitudeSlider.setMinimum(-180);
        longitudeSlider.setPaintLabels(true);
        longitudeSlider.setPaintTicks(true);
        longitudeSlider.setValue(0);
        longitudeSlider.setEnabled(false);
        longitudeSlider.setPreferredSize(new java.awt.Dimension(350, 52));
        longitudeSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                longitudeSliderStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 40, 0, 0);
        projectionPanel.add(longitudeSlider, gridBagConstraints);
        {
            int[] values = new int[] {-180, -135, -90, -45, 0, 45, 90, 135, 180};
            String[] labels = new String[] {
                "180\u00B0W",
                "135\u00B0W",
                "90\u00B0W",
                "45\u00B0W",
                "0\u00B0",
                "45\u00B0E",
                "90\u00B0E",
                "135\u00B0E",
                "180\u00B0E",
            };
            SliderUtils.setSliderLabels(longitudeSlider, values, labels);
            SliderUtils.reapplyFontSize(longitudeSlider);
        }

        jLabel6.setText("Central Longitude");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(20, 0, 0, 0);
        projectionPanel.add(jLabel6, gridBagConstraints);

        projectionMeanDistanceLabel.setText("-");
        projectionMeanDistanceLabel.setPreferredSize(new java.awt.Dimension(80, 16));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 14;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        projectionPanel.add(projectionMeanDistanceLabel, gridBagConstraints);
        projectionMeanDistanceLabel.setVisible(false);

        jLabel7.setText("Mean distance between projected control points:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 13;
        gridBagConstraints.gridwidth = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(26, 0, 26, 0);
        projectionPanel.add(jLabel7, gridBagConstraints);
        jLabel7.setVisible(false);

        projectionMeanLongitudeLabel.setFont(new java.awt.Font("SansSerif", 0, 11)); // NOI18N
        projectionMeanLongitudeLabel.setText("Mean longitude: -");
        projectionMeanLongitudeLabel.setPreferredSize(new java.awt.Dimension(60, 16));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 0, 0);
        projectionPanel.add(projectionMeanLongitudeLabel, gridBagConstraints);

        longitudeFormattedTextField.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter()));
        longitudeFormattedTextField.setEnabled(false);
        longitudeFormattedTextField.setMinimumSize(new java.awt.Dimension(60, 28));
        longitudeFormattedTextField.setPreferredSize(new java.awt.Dimension(60, 28));
        longitudeFormattedTextField.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                longitudeFormattedTextFieldPropertyChange(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        projectionPanel.add(longitudeFormattedTextField, gridBagConstraints);

        projectionLiveUpdateCheckBox.setSelected(true);
        projectionLiveUpdateCheckBox.setText("Live Update of Distortion Visualizations");
        projectionLiveUpdateCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                projectionLiveUpdateCheckBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.SOUTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(20, 0, 0, 0);
        projectionPanel.add(projectionLiveUpdateCheckBox, gridBagConstraints);

        centralLongitudeButtonGroup.add(automaticCentralLongitudeRadioButton);
        automaticCentralLongitudeRadioButton.setSelected(true);
        automaticCentralLongitudeRadioButton.setText("Use mean longitude of linked points in reference map");
        automaticCentralLongitudeRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                centralLongitudeButton(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(20, 30, 0, 0);
        projectionPanel.add(automaticCentralLongitudeRadioButton, gridBagConstraints);

        centralLongitudeButtonGroup.add(customCentralLongitudeRadioButton);
        customCentralLongitudeRadioButton.setText("Use other central longitude");
        customCentralLongitudeRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                centralLongitudeButton(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(20, 30, 0, 0);
        projectionPanel.add(customCentralLongitudeRadioButton, gridBagConstraints);

        projectionOptionsButton.setText("Options…");
        projectionOptionsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                projectionOptionsButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 8, 0, 0);
        projectionPanel.add(projectionOptionsButton, gridBagConstraints);

        projectionDescriptionTextArea.setEditable(false);
        projectionDescriptionTextArea.setFont(projectionDescriptionTextArea.getFont().deriveFont(projectionDescriptionTextArea.getFont().getSize()-2f));
        projectionDescriptionTextArea.setLineWrap(true);
        projectionDescriptionTextArea.setRows(10);
        projectionDescriptionTextArea.setText("–");
        projectionDescriptionTextArea.setWrapStyleWord(true);
        projectionDescriptionTextArea.setMinimumSize(new java.awt.Dimension(200, 200));
        projectionDescriptionTextArea.setOpaque(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 0);
        projectionPanel.add(projectionDescriptionTextArea, gridBagConstraints);

        mapComponent.setFocusable(false);
        mapComponent.setPreferredSize(new java.awt.Dimension(500, 350));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridheight = 10;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(0, 12, 0, 0);
        projectionPanel.add(mapComponent, gridBagConstraints);

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setName(""); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeWindow(evt);
            }
        });

        splitPane.setBorder(null);
        splitPane.setResizeWeight(0.5);

        oldMapPanel.setLayout(new java.awt.BorderLayout());

        oldMapLabelPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 2, 2, 2));
        oldMapLabelPanel.setLayout(new java.awt.BorderLayout());

        oldMapLabel.setText("Old Map");
        oldMapLabel.setFocusable(false);
        oldMapLabelPanel.add(oldMapLabel, java.awt.BorderLayout.EAST);

        oldMapPanel.add(oldMapLabelPanel, java.awt.BorderLayout.NORTH);

        oldMapComponent.setName("old"); // NOI18N
        oldMapPanel.add(oldMapComponent, java.awt.BorderLayout.CENTER);

        splitPane.setLeftComponent(oldMapPanel);

        newMapPanel.setLayout(new java.awt.BorderLayout());

        newMapComponent.setName("new"); // NOI18N
        newMapPanel.add(newMapComponent, java.awt.BorderLayout.CENTER);

        newMapLabelPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 2, 2, 2));
        newMapLabelPanel.setLayout(new java.awt.BorderLayout());

        newMapLabel.setText("New Reference Map");
        newMapLabel.setFocusable(false);
        newMapLabelPanel.add(newMapLabel, java.awt.BorderLayout.WEST);

        osmCopyrightLabel.setFont(osmCopyrightLabel.getFont().deriveFont(osmCopyrightLabel.getFont().getSize()-2f));
        osmCopyrightLabel.setForeground(java.awt.Color.lightGray);
        osmCopyrightLabel.setText("\u00A9 OpenStreetMap contributors");
        osmCopyrightLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 10, 0, 3));
        newMapLabelPanel.add(osmCopyrightLabel, java.awt.BorderLayout.EAST);

        newMapPanel.add(newMapLabelPanel, java.awt.BorderLayout.NORTH);

        splitPane.setRightComponent(newMapPanel);

        getContentPane().add(splitPane, java.awt.BorderLayout.CENTER);

        topPanel.setLayout(new javax.swing.BoxLayout(topPanel, javax.swing.BoxLayout.Y_AXIS));

        topLeftPanel.setFocusCycleRoot(true);
        topLeftPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 2));

        computeButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/update.png"))); // NOI18N
        computeButton.setText("Compute");
        computeButton.setToolTipText("Click to compute the scale, the rotation and statistical values of the map to analyze; and update the visualizations.");
        computeButton.setMaximumSize(new java.awt.Dimension(100, 29));
        computeButton.setMinimumSize(new java.awt.Dimension(100, 29));
        computeButton.setPreferredSize(new java.awt.Dimension(100, 31));
        computeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                computeButtonActionPerformed(evt);
            }
        });
        topLeftPanel.add(computeButton);

        toolBarButtonGroup.add(rectangularSelectionToggleButton);
        rectangularSelectionToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/SelectPoints16x16.gif"))); // NOI18N
        rectangularSelectionToggleButton.setToolTipText("Rectanglar Selection of Points");
        rectangularSelectionToggleButton.setMaximumSize(new java.awt.Dimension(27, 27));
        rectangularSelectionToggleButton.setMinimumSize(new java.awt.Dimension(27, 27));
        rectangularSelectionToggleButton.setPreferredSize(new java.awt.Dimension(27, 27));
        rectangularSelectionToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rectangularSelectionToggleButtonActionPerformed(evt);
            }
        });
        pointsToolBar.add(rectangularSelectionToggleButton);

        toolBarButtonGroup.add(setPointToggleButton);
        setPointToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/SetPoint16x16.gif"))); // NOI18N
        setPointToggleButton.setToolTipText("Set New Point");
        setPointToggleButton.setMaximumSize(new java.awt.Dimension(27, 27));
        setPointToggleButton.setMinimumSize(new java.awt.Dimension(27, 27));
        setPointToggleButton.setPreferredSize(new java.awt.Dimension(27, 27));
        setPointToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                setPointToggleButtonActionPerformed(evt);
            }
        });
        pointsToolBar.add(setPointToggleButton);

        toolBarButtonGroup.add(panPointsToggleButton);
        panPointsToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/MovePoint16x16.gif"))); // NOI18N
        panPointsToggleButton.setToolTipText("Move Selected Point");
        panPointsToggleButton.setMaximumSize(new java.awt.Dimension(27, 27));
        panPointsToggleButton.setMinimumSize(new java.awt.Dimension(27, 27));
        panPointsToggleButton.setPreferredSize(new java.awt.Dimension(27, 27));
        panPointsToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                panPointsToggleButtonActionPerformed(evt);
            }
        });
        pointsToolBar.add(panPointsToggleButton);

        topLeftPanel.add(pointsToolBar);

        toolBarButtonGroup.add(zoomInToggleButton);
        zoomInToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/ZoomIn16x16.gif"))); // NOI18N
        zoomInToggleButton.setSelected(true);
        zoomInToggleButton.setToolTipText("Zoom In (Press Cmd or Ctrl for quick access.)");
        zoomInToggleButton.setPreferredSize(new java.awt.Dimension(24, 24));
        zoomInToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomInToggleButtonActionPerformed(evt);
            }
        });
        navigationToolBar.add(zoomInToggleButton);

        toolBarButtonGroup.add(zoomOutToggleButton);
        zoomOutToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/ZoomOut16x16.gif"))); // NOI18N
        zoomOutToggleButton.setToolTipText("Zoom Out (Press Alt+Cmd  or Alt+Ctrl for quick access.)");
        zoomOutToggleButton.setPreferredSize(new java.awt.Dimension(24, 24));
        zoomOutToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomOutToggleButtonActionPerformed(evt);
            }
        });
        navigationToolBar.add(zoomOutToggleButton);

        toolBarButtonGroup.add(handToggleButton);
        handToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/Hand16x16.gif"))); // NOI18N
        handToggleButton.setToolTipText("Pan (Press space key for quick access.)");
        handToggleButton.setPreferredSize(new java.awt.Dimension(24, 24));
        handToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                handToggleButtonActionPerformed(evt);
            }
        });
        navigationToolBar.add(handToggleButton);

        toolBarButtonGroup.add(distanceToggleButton);
        distanceToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/Ruler16x16.gif"))); // NOI18N
        distanceToggleButton.setToolTipText("Measure Distance and Angle");
        distanceToggleButton.setPreferredSize(new java.awt.Dimension(24, 24));
        distanceToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                distanceToggleButtonActionPerformed(evt);
            }
        });
        navigationToolBar.add(distanceToggleButton);

        toolBarButtonGroup.add(penToggleButton);
        penToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/Pen16x16.gif"))); // NOI18N
        penToggleButton.setToolTipText("Draw Polygons. Double-click to close a polygon.");
        penToggleButton.setPreferredSize(new java.awt.Dimension(24, 24));
        penToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                penToggleButtonActionPerformed(evt);
            }
        });
        navigationToolBar.add(penToggleButton);
        navigationToolBar.add(jSeparator19);

        showAllButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/ShowAll20x14.png"))); // NOI18N
        showAllButton.setToolTipText("Show All");
        showAllButton.setBorderPainted(false);
        showAllButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showAllButtonActionPerformed(evt);
            }
        });
        navigationToolBar.add(showAllButton);

        topLeftPanel.add(navigationToolBar);

        coordinateInfoPanel.setFocusable(false);
        infoToolBar.add(coordinateInfoPanel);

        geoObjectInfoPanel.setFocusable(false);
        geoObjectInfoPanel.setPreferredSize(null);
        infoToolBar.add(geoObjectInfoPanel);

        localScaleRotationInfoPanel.setFocusable(false);
        localScaleRotationInfoPanel.setPreferredSize(null);
        infoToolBar.add(localScaleRotationInfoPanel);

        topLeftPanel.add(infoToolBar);

        topPanel.add(topLeftPanel);

        jSeparator20.setRequestFocusEnabled(false);
        topPanel.add(jSeparator20);

        getContentPane().add(topPanel, java.awt.BorderLayout.NORTH);

        bottomPanel1.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

        bottomPanel2.setOpaque(false);
        bottomPanel2.setRequestFocusEnabled(false);
        bottomPanel2.setVerifyInputWhenFocusTarget(false);
        bottomPanel2.setLayout(new java.awt.GridBagLayout());

        visualizationTabbedPane.setToolTipText("");
        visualizationTabbedPane.setFocusCycleRoot(true);
        visualizationTabbedPane.setPreferredSize(new java.awt.Dimension(800, 230));

        DistortionGridPanel.setFocusCycleRoot(true);

        distortionGridPanel.setMinimumSize(new java.awt.Dimension(325, 195));
        distortionGridPanel.setLayout(new java.awt.GridBagLayout());

        distortionGridVisibleCheckBox.setSelected(true);
        distortionGridVisibleCheckBox.setText("Show");
        distortionGridVisibleCheckBox.setToolTipText("Show or hide the distortion grid.");
        distortionGridVisibleCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                distortionGridVisibleCheckBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        distortionGridPanel.add(distortionGridVisibleCheckBox, gridBagConstraints);

        distortionControlSizePanel.setForeground(new java.awt.Color(255, 0, 0));
        distortionControlSizePanel.setLayout(new java.awt.GridBagLayout());

        distortionGridMeshSizeLabel.setText("Mesh Size:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        distortionControlSizePanel.add(distortionGridMeshSizeLabel, gridBagConstraints);

        distortionGridSmoothnessLabel.setText("Smoothness:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        distortionControlSizePanel.add(distortionGridSmoothnessLabel, gridBagConstraints);

        distortionGridSmoothnessSlider.setToolTipText("Set the smoothness of the grid lines.");
        distortionGridSmoothnessSlider.setValue(100);
        distortionGridSmoothnessSlider.setPreferredSize(new java.awt.Dimension(100, 29));
        distortionGridSmoothnessSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                distortionGridSmoothnessSliderStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 1, 3, 0);
        distortionControlSizePanel.add(distortionGridSmoothnessSlider, gridBagConstraints);

        distortionGridLabelLabel.setText("Labels:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        distortionControlSizePanel.add(distortionGridLabelLabel, gridBagConstraints);

        distortionGridExtensionComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Rectangular", "Around Points", "Custom Polygon" }));
        distortionGridExtensionComboBox.setToolTipText("Define the extent of the distortion grid.");
        distortionGridExtensionComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                distortionGridExtensionComboBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 4, 3, 0);
        distortionControlSizePanel.add(distortionGridExtensionComboBox, gridBagConstraints);

        distortionGridLabelComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "None", "10 Pt", "12 Pt", "15 Pt" }));
        distortionGridLabelComboBox.setSelectedIndex(2);
        distortionGridLabelComboBox.setToolTipText("Select the size of the coordinate labels");
        distortionGridLabelComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                distortionComboBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 4, 3, 0);
        distortionControlSizePanel.add(distortionGridLabelComboBox, gridBagConstraints);

        distortionGridLabelSequenceComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "1", "2", "3", "4", "5", "6", "10", "20" }));
        distortionGridLabelSequenceComboBox.setSelectedIndex(3);
        distortionGridLabelSequenceComboBox.setToolTipText("Display a label each nth line.");
        distortionGridLabelSequenceComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                distortionComboBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 3, 0, 0);
        distortionControlSizePanel.add(distortionGridLabelSequenceComboBox, gridBagConstraints);

        distortionGridFrequencyLabel.setText("Frequency:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(0, 8, 0, 0);
        distortionControlSizePanel.add(distortionGridFrequencyLabel, gridBagConstraints);

        distortionGridLineAppearancePanel.setName("grid"); // NOI18N
        distortionGridLineAppearancePanel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                distortionGridLineAppearancePanelActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 4, 3, 10);
        distortionControlSizePanel.add(distortionGridLineAppearancePanel, gridBagConstraints);

        jLabel2.setText("Extent:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        distortionControlSizePanel.add(jLabel2, gridBagConstraints);

        distortionGridMeshSizeNumberField.setToolTipText("Enter the mesh size in the unit selected.");
        distortionGridMeshSizeNumberField.setNumber(5000.0);
        distortionGridMeshSizeNumberField.setPreferredSize(new java.awt.Dimension(100, 22));
        distortionGridMeshSizeNumberField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                distortionGridMeshSizeNumberFieldFocusLost(evt);
            }
        });
        distortionGridMeshSizeNumberField.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                distortionGridMeshSizeNumberFieldPropertyChange(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 4, 3, 0);
        distortionControlSizePanel.add(distortionGridMeshSizeNumberField, gridBagConstraints);

        gridUnitComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "m", "\u00B0" }));
        gridUnitComboBox.setToolTipText("Units of the mesh size");
        gridUnitComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                gridUnitComboBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 0);
        distortionControlSizePanel.add(gridUnitComboBox, gridBagConstraints);

        jLabel8.setText("Appearance:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        distortionControlSizePanel.add(jLabel8, gridBagConstraints);

        jLabel9.setText("Uncertain Areas:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        distortionControlSizePanel.add(jLabel9, gridBagConstraints);

        distortionGridUncertaintyAlphaSlider.setMaximum(50);
        distortionGridUncertaintyAlphaSlider.setMinimum(5);
        distortionGridUncertaintyAlphaSlider.setToolTipText("Transparency for uncertain grid areas.");
        distortionGridUncertaintyAlphaSlider.setValue(25);
        distortionGridUncertaintyAlphaSlider.setInverted(true);
        distortionGridUncertaintyAlphaSlider.setPreferredSize(new java.awt.Dimension(100, 29));
        distortionGridUncertaintyAlphaSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                distortionGridUncertaintyAlphaSliderStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 6;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 1, 3, 0);
        distortionControlSizePanel.add(distortionGridUncertaintyAlphaSlider, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 12, 0, 12);
        distortionGridPanel.add(distortionControlSizePanel, gridBagConstraints);

        distortionGridShowUndistortedCheckBox.setText("Show Undistorted Grid");
        distortionGridShowUndistortedCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                distortionGridShowUndistortedCheckBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
        distortionGridPanel.add(distortionGridShowUndistortedCheckBox, gridBagConstraints);

        gridExaggerationWarningLabel.setForeground(new java.awt.Color(255, 0, 0));
        gridExaggerationWarningLabel.setText("Exaggeration Factor not 1!");
        gridExaggerationWarningLabel.setVisible(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        distortionGridPanel.add(gridExaggerationWarningLabel, gridBagConstraints);

        gridOptionsButton.setText("More…");
        gridOptionsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                gridOptionsButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LAST_LINE_START;
        distortionGridPanel.add(gridOptionsButton, gridBagConstraints);

        DistortionGridPanel.add(distortionGridPanel);

        visualizationTabbedPane.addTab("Distortion Grid", null, DistortionGridPanel, "Configure the computation of the distortion grid.");

        ErrorVectorsPanel.setFocusCycleRoot(true);
        ErrorVectorsPanel.setPreferredSize(new java.awt.Dimension(600, 195));

        errorVectorsPanel.setLayout(new java.awt.GridBagLayout());

        errorVectorsVisibleCheckBox.setText("Show");
        errorVectorsVisibleCheckBox.setToolTipText("Show or hide the error vectors.");
        errorVectorsVisibleCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                errorVectorsVisibleCheckBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(10, 12, 0, 6);
        errorVectorsPanel.add(errorVectorsVisibleCheckBox, gridBagConstraints);

        errorVectorScalePanel.setLayout(new java.awt.GridBagLayout());

        errorVectorsOutliersCheckBox.setSelected(true);
        errorVectorsOutliersCheckBox.setText("Mark Outliers (Length > 3 Sigma)");
        errorVectorsOutliersCheckBox.setToolTipText("Highlight vectors and circles that are greater than 3 times the Standard Deviation (Sigma 0).");
        errorVectorsOutliersCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                errorVectorsOutliersCheckBoxActionPerformed(evt);
            }
        });
        errorVectorOutlierPanel.add(errorVectorsOutliersCheckBox);

        errorVectorsOutliersColorButton.setToolTipText("Choose the color of outliers.");
        errorVectorsOutliersColorButton.setColor(new java.awt.Color(255, 0, 0));
        errorVectorsOutliersColorButton.setIconHeight(14);
        errorVectorsOutliersColorButton.setIconTextGap(0);
        errorVectorsOutliersColorButton.setIconWidth(14);
        errorVectorsOutliersColorButton.setMaximumSize(null);
        errorVectorsOutliersColorButton.setMinimumSize(null);
        errorVectorsOutliersColorButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                errorVectorsOutliersColorButtonActionPerformed(evt);
            }
        });
        errorVectorOutlierPanel.add(errorVectorsOutliersColorButton);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        errorVectorScalePanel.add(errorVectorOutlierPanel, gridBagConstraints);

        errorVectorsShowComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Vectors", "Circles", "Vectors & Circles" }));
        errorVectorsShowComboBox.setToolTipText("Show vectors between the original points and the transformed points and/or circles with areas proportional to the length of the vectors.");
        errorVectorsShowComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                errorVectorsShowComboBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        errorVectorScalePanel.add(errorVectorsShowComboBox, gridBagConstraints);

        errorVectorsScaleLabel.setText("Scale Factor");
        errorVectorsScalePanel.add(errorVectorsScaleLabel);

        errorVectorsScaleNumberField.setToolTipText("Set the scale factor applied to the length of vectors and to the area of circles. ");
        errorVectorsScaleNumberField.setNumber(1.0);
        errorVectorsScaleNumberField.setPreferredSize(new java.awt.Dimension(50, 22));
        errorVectorsScaleNumberField.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                errorVectorsScaleNumberFieldPropertyChange(evt);
            }
        });
        errorVectorsScalePanel.add(errorVectorsScaleNumberField);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        errorVectorScalePanel.add(errorVectorsScalePanel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(10, 12, 4, 12);
        errorVectorsPanel.add(errorVectorScalePanel, gridBagConstraints);

        errorVectorsLineAppearancePanel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                errorVectorsLineAppearancePanelActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 12, 0, 0);
        errorVectorsPanel.add(errorVectorsLineAppearancePanel, gridBagConstraints);

        errorVectorsFillCirclesCheckBox.setSelected(true);
        errorVectorsFillCirclesCheckBox.setText("Fill Circles");
        errorVectorsFillCirclesCheckBox.setToolTipText("Fill circles with opaque color.");
        errorVectorsFillCirclesCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                errorVectorsFillCirclesCheckBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 12, 0, 0);
        errorVectorsPanel.add(errorVectorsFillCirclesCheckBox, gridBagConstraints);

        errorVectorsFillColorButton.setToolTipText("Choose the color to fill circles.");
        errorVectorsFillColorButton.setColor(new java.awt.Color(255, 255, 255));
        errorVectorsFillColorButton.setIconHeight(14);
        errorVectorsFillColorButton.setIconTextGap(0);
        errorVectorsFillColorButton.setIconWidth(14);
        errorVectorsFillColorButton.setMaximumSize(null);
        errorVectorsFillColorButton.setMinimumSize(null);
        errorVectorsFillColorButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                errorVectorsFillColorButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 0, 0);
        errorVectorsPanel.add(errorVectorsFillColorButton, gridBagConstraints);

        ErrorVectorsPanel.add(errorVectorsPanel);

        visualizationTabbedPane.addTab("Displacements", null, ErrorVectorsPanel, "Configure the computation of the vectors of displacements that connect the original points with the transformed points.");

        IsoscalesPanel.setPreferredSize(new java.awt.Dimension(600, 141));

        isoscalesPanel.setFocusCycleRoot(true);
        isoscalesPanel.setLayout(new java.awt.GridBagLayout());

        isolinesScaleIntervalLabel.setText("Interval: 1:");
        isolinesScaleIntervalLabel.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(0, 20, 0, 0);
        isoscalesPanel.add(isolinesScaleIntervalLabel, gridBagConstraints);

        isolinesScaleLabel.setText("Scale");
        isolinesScaleLabel.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(17, 0, 17, 0);
        isoscalesPanel.add(isolinesScaleLabel, gridBagConstraints);

        isolinesRotationLabel.setText("Rotation");
        isolinesRotationLabel.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        isoscalesPanel.add(isolinesRotationLabel, gridBagConstraints);

        isolinesRotationIntervalLabel.setText("Interval:");
        isolinesRotationIntervalLabel.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(0, 20, 0, 0);
        isoscalesPanel.add(isolinesRotationIntervalLabel, gridBagConstraints);

        isolinesScaleVisibleCheckBox.setText("Show");
        isolinesScaleVisibleCheckBox.setToolTipText("Show or hide scale.");
        isolinesScaleVisibleCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                isolinesScaleVisibleCheckBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        isoscalesPanel.add(isolinesScaleVisibleCheckBox, gridBagConstraints);

        isolinesRotationVisibleCheckBox.setText("Show");
        isolinesRotationVisibleCheckBox.setToolTipText("Show or hide rotation.");
        isolinesRotationVisibleCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                isolinesRotationVisibleCheckBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        isoscalesPanel.add(isolinesRotationVisibleCheckBox, gridBagConstraints);

        isolinesScaleAppearancePanel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                isolinesAppearancePanelActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 20, 16, 150);
        isoscalesPanel.add(isolinesScaleAppearancePanel, gridBagConstraints);

        isolinesRotationAppearancePanel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                isolinesAppearancePanelActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 20, 16, 0);
        isoscalesPanel.add(isolinesRotationAppearancePanel, gridBagConstraints);

        isolinesSmoothnessLabel.setText("Radius of Influence:");
        isolinesSmoothnessLabel.setFocusable(false);
        jPanel1.add(isolinesSmoothnessLabel);

        isolinesRadiusNumberField.setToolTipText("Set the radius of influence. A larger radius results in smoother lines, and vice versa.");
        isolinesRadiusNumberField.setNumber(10000.0);
        isolinesRadiusNumberField.setNumberFieldChecker(new NumberFieldChecker() {
            public boolean testValue (NumberField numberField) {
                return testIsolinesRadiusOfInfluence();
            }
        });
        isolinesRadiusNumberField.setPreferredSize(new java.awt.Dimension(100, 22));
        isolinesRadiusNumberField.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                isolinesScaleIntervalNumberFieldPropertyChange(evt);
            }
        });
        jPanel1.add(isolinesRadiusNumberField);

        jLabel3.setText("[m]");
        jLabel3.setFocusable(false);
        jPanel1.add(jLabel3);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 5;
        isoscalesPanel.add(jPanel1, gridBagConstraints);

        isolinesScaleIntervalNumberField.setToolTipText("Set the interval between scale isolines.");
        isolinesScaleIntervalNumberField.setNumber(10000.0);
        isolinesScaleIntervalNumberField.setPreferredSize(new java.awt.Dimension(100, 22));
        isolinesScaleIntervalNumberField.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                isolinesScaleIntervalNumberFieldPropertyChange(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        isoscalesPanel.add(isolinesScaleIntervalNumberField, gridBagConstraints);

        isolinesRotationIntervalNumberField.setToolTipText("Set the interval between rotation isolines.");
        isolinesRotationIntervalNumberField.setNumber(5.0);
        isolinesRotationIntervalNumberField.setPreferredSize(new java.awt.Dimension(100, 22));
        isolinesRotationIntervalNumberField.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                isolinesScaleIntervalNumberFieldPropertyChange(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 0);
        isoscalesPanel.add(isolinesRotationIntervalNumberField, gridBagConstraints);

        IsoscalesPanel.add(isoscalesPanel);

        visualizationTabbedPane.addTab("Isolines", null, IsoscalesPanel, "Visualize local variation of scale and rotation with isolines.");

        DrawingPanel.setFocusCycleRoot(true);
        DrawingPanel.setPreferredSize(new java.awt.Dimension(600, 163));

        drawingPanel.setLayout(new java.awt.GridBagLayout());

        drawingVisibleCheckBox.setSelected(true);
        drawingVisibleCheckBox.setText("Show Transformed Drawings");
        drawingVisibleCheckBox.setToolTipText("Show or hide transformed drawings.");
        drawingVisibleCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                drawingVisibleCheckBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(10, 12, 0, 6);
        drawingPanel.add(drawingVisibleCheckBox, gridBagConstraints);

        drawingInfoTextArea.setEditable(false);
        drawingInfoTextArea.setLineWrap(true);
        drawingInfoTextArea.setText("Transforms drawings from the source to the target map. Use the \"Analyze\" menu to select the target map.");
        drawingInfoTextArea.setWrapStyleWord(true);
        drawingInfoTextArea.setFocusable(false);
        drawingInfoTextArea.setMinimumSize(new java.awt.Dimension(300, 80));
        drawingInfoTextArea.setOpaque(false);
        drawingInfoTextArea.setPreferredSize(new java.awt.Dimension(350, 80));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(6, 6, 6, 6);
        drawingPanel.add(drawingInfoTextArea, gridBagConstraints);

        drawingImportButton.setText("Import Drawings…");
        drawingImportButton.setToolTipText("Import drawing from Ungenerate file or from ASCII point list (ID, X, Y).");
        drawingImportButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                drawingImportButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        drawingPanel.add(drawingImportButton, gridBagConstraints);

        drawingClearButton.setText("Clear Drawings");
        drawingClearButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                drawingClearButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        drawingPanel.add(drawingClearButton, gridBagConstraints);

        DrawingPanel.add(drawingPanel);

        visualizationTabbedPane.addTab("Drawings", null, DrawingPanel, "Transform drawings from one map to another.");

        CirclePanel.setFocusCycleRoot(true);
        CirclePanel.setPreferredSize(new java.awt.Dimension(600, 212));

        circlePanel.setLayout(new java.awt.GridBagLayout());

        circlesVisibleCheckBox.setText("Show");
        circlesVisibleCheckBox.setToolTipText("Show or hide circles computed after Mekenkamp.");
        circlesVisibleCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                circlesVisibleCheckBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(10, 12, 0, 6);
        circlePanel.add(circlesVisibleCheckBox, gridBagConstraints);

        circlesScalePanel.setLayout(new java.awt.GridBagLayout());

        circlesScaleLabel.setText("Circle Scale:");
        circlesScalePanel.add(circlesScaleLabel, new java.awt.GridBagConstraints());

        circleScaleSlider.setMaximum(250);
        circleScaleSlider.setMinimum(1);
        circleScaleSlider.setToolTipText("Set the scale applied to the circles that are proportional to the sum of the relative errors in distance to all points. ");
        circleScaleSlider.setPreferredSize(new java.awt.Dimension(150, 29));
        circleScaleSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                circleScaleSliderStateChanged(evt);
            }
        });
        circlesScalePanel.add(circleScaleSlider, new java.awt.GridBagConstraints());

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(10, 12, 4, 12);
        circlePanel.add(circlesScalePanel, gridBagConstraints);

        circlesInfoTextArea.setEditable(false);
        circlesInfoTextArea.setLineWrap(true);
        circlesInfoTextArea.setText("The bigger the circles, the more the distances to the neighbors in the old map differ from the true distances (method by Mekenkamp). ");
        circlesInfoTextArea.setWrapStyleWord(true);
        circlesInfoTextArea.setFocusable(false);
        circlesInfoTextArea.setMinimumSize(new java.awt.Dimension(300, 55));
        circlesInfoTextArea.setOpaque(false);
        circlesInfoTextArea.setPreferredSize(new java.awt.Dimension(300, 55));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(6, 6, 6, 6);
        circlePanel.add(circlesInfoTextArea, gridBagConstraints);

        circlesInfoHeader.setText("Standard Inaccuracy Circles");
        circlesInfoHeader.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(6, 6, 0, 6);
        circlePanel.add(circlesInfoHeader, gridBagConstraints);

        circlesLineAppearancePanel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                circlesLineAppearancePanelActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 12, 0, 0);
        circlePanel.add(circlesLineAppearancePanel, gridBagConstraints);

        CirclePanel.add(circlePanel);

        visualizationTabbedPane.addTab("Circles", null, CirclePanel, "The size of these circles is proportional to the sum of the relative errors in distance to all points. ");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        bottomPanel2.add(visualizationTabbedPane, gridBagConstraints);
        visualizationTabbedPane.remove(DrawingPanel);

        linksPanel.setFocusCycleRoot(true);
        linksPanel.setName(""); // NOI18N
        linksPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));

        linkNamePanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        linkNamePanel.setLayout(new java.awt.GridBagLayout());

        linkToggleButton.setText("Link");
        linkToggleButton.setToolTipText("Link or unlink two selected points.");
        linkToggleButton.setEnabled(false);
        linkToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                linkToggleButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        linkNamePanel.add(linkToggleButton, gridBagConstraints);

        linkNameLabel.setFont(linkNameLabel.getFont().deriveFont(linkNameLabel.getFont().getSize()-2f));
        linkNameLabel.setToolTipText("The name of two linked points that are currently selected.");
        linkNameLabel.setMaximumSize(new java.awt.Dimension(200, 40));
        linkNameLabel.setMinimumSize(new java.awt.Dimension(150, 16));
        linkNameLabel.setPreferredSize(new java.awt.Dimension(120, 16));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        linkNamePanel.add(linkNameLabel, gridBagConstraints);

        nameLabel.setText("Selected Link");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        linkNamePanel.add(nameLabel, gridBagConstraints);

        linkNameButton.setText("Rename");
        linkNameButton.setToolTipText("Rename two linked points.");
        linkNameButton.setEnabled(false);
        //linkNameButton.putClientProperty("JButton.buttonType", "roundRect"); linkNameButton.putClientProperty("JComponent.sizeVariant", "small");
        linkNameButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                linkNameButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        linkNamePanel.add(linkNameButton, gridBagConstraints);

        oldMapCoordsTitleLabel.setFont(oldMapCoordsTitleLabel.getFont().deriveFont(oldMapCoordsTitleLabel.getFont().getSize()-2f));
        oldMapCoordsTitleLabel.setText("Old Map");
        oldMapCoordsTitleLabel.setToolTipText("The coordinates of the currently selected point in the old map.");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(6, 2, 3, 0);
        linkNamePanel.add(oldMapCoordsTitleLabel, gridBagConstraints);

        pointOldYLabel.setFont(pointOldYLabel.getFont().deriveFont(pointOldYLabel.getFont().getSize()-2f));
        pointOldYLabel.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        pointOldYLabel.setToolTipText("The vertical coordinate of a selected point in the old map.");
        pointOldYLabel.setMinimumSize(new java.awt.Dimension(80, 16));
        pointOldYLabel.setPreferredSize(new java.awt.Dimension(80, 16));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 30);
        linkNamePanel.add(pointOldYLabel, gridBagConstraints);

        xLabel.setFont(xLabel.getFont().deriveFont(xLabel.getFont().getSize()-2f));
        xLabel.setText("x");
        xLabel.setMinimumSize(new java.awt.Dimension(30, 15));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        linkNamePanel.add(xLabel, gridBagConstraints);

        pointOldXLabel.setFont(pointOldXLabel.getFont().deriveFont(pointOldXLabel.getFont().getSize()-2f));
        pointOldXLabel.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        pointOldXLabel.setToolTipText("The horizontal coordinate of a selected point in the old map.");
        pointOldXLabel.setMinimumSize(new java.awt.Dimension(80, 16));
        pointOldXLabel.setPreferredSize(new java.awt.Dimension(80, 16));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 30);
        linkNamePanel.add(pointOldXLabel, gridBagConstraints);

        yLabel.setFont(yLabel.getFont().deriveFont(yLabel.getFont().getSize()-2f));
        yLabel.setText("y");
        yLabel.setMinimumSize(new java.awt.Dimension(30, 15));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        linkNamePanel.add(yLabel, gridBagConstraints);

        pointNewXLabel.setFont(pointNewXLabel.getFont().deriveFont(pointNewXLabel.getFont().getSize()-2f));
        pointNewXLabel.setToolTipText("The horizontal coordinate of a selected point in the new map.");
        pointNewXLabel.setMinimumSize(new java.awt.Dimension(80, 16));
        pointNewXLabel.setPreferredSize(new java.awt.Dimension(130, 16));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        linkNamePanel.add(pointNewXLabel, gridBagConstraints);

        newMapCoordsTitleLabel.setFont(newMapCoordsTitleLabel.getFont().deriveFont(newMapCoordsTitleLabel.getFont().getSize()-2f));
        newMapCoordsTitleLabel.setText("New Map");
        newMapCoordsTitleLabel.setToolTipText("The coordinates of the currently selected point in the new map.");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(6, 5, 3, 0);
        linkNamePanel.add(newMapCoordsTitleLabel, gridBagConstraints);

        pointNewYLabel.setFont(pointNewYLabel.getFont().deriveFont(pointNewYLabel.getFont().getSize()-2f));
        pointNewYLabel.setToolTipText("The vertical coordinate of a selected point in the new map.");
        pointNewYLabel.setMinimumSize(new java.awt.Dimension(80, 16));
        pointNewYLabel.setPreferredSize(new java.awt.Dimension(130, 16));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        linkNamePanel.add(pointNewYLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(20, 0, 20, 0);
        linkNamePanel.add(jSeparator23, gridBagConstraints);

        jLabel4.setText("Selected Points");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        linkNamePanel.add(jLabel4, gridBagConstraints);

        linksPanel.add(linkNamePanel);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(4, 8, 4, 0);
        bottomPanel2.add(linksPanel, gridBagConstraints);

        jSeparator21.setOrientation(javax.swing.SwingConstants.VERTICAL);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        bottomPanel2.add(jSeparator21, gridBagConstraints);

        transformationInfoPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        transformationInfoPanel.setFocusable(false);
        transformationInfoPanel.setLayout(new java.awt.BorderLayout(0, 6));

        transformationLabel.setText("Old Map Information");
        transformationInfoPanel.add(transformationLabel, java.awt.BorderLayout.NORTH);

        transformationInfoTextArea.setEditable(false);
        transformationInfoTextArea.setFont(transformationInfoTextArea.getFont().deriveFont(transformationInfoTextArea.getFont().getSize()-2f));
        transformationInfoTextArea.setRows(8);
        transformationInfoTextArea.setTabSize(10);
        transformationInfoTextArea.setFocusable(false);
        transformationInfoTextArea.setMinimumSize(new java.awt.Dimension(200, 21));
        transformationInfoTextArea.setOpaque(false);
        transformationInfoTextArea.setPreferredSize(new java.awt.Dimension(250, 136));
        transformationInfoPanel.add(transformationInfoTextArea, java.awt.BorderLayout.CENTER);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(4, 0, 4, 0);
        bottomPanel2.add(transformationInfoPanel, gridBagConstraints);

        bottomPanel1.add(bottomPanel2);

        getContentPane().add(bottomPanel1, java.awt.BorderLayout.SOUTH);

        fileMenu.setText("File");

        newProjectMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N,
            java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    newProjectMenuItem.setText("New Project");
    newProjectMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            newProjectMenuItemActionPerformed(evt);
        }
    });
    fileMenu.add(newProjectMenuItem);

    openProjectMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
openProjectMenuItem.setText("Open Project…");
openProjectMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        openProjectMenuItemActionPerformed(evt);
    }
    });
    fileMenu.add(openProjectMenuItem);

    openRecentMenu.setText("Open Recent Project");
    fileMenu.add(openRecentMenu);
    fileMenu.add(jSeparator5);

    closeProjectMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
closeProjectMenuItem.setText("Close Window");
closeProjectMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        closeProjectMenuItemActionPerformed(evt);
    }
    });
    fileMenu.add(closeProjectMenuItem);

    saveProjectMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
saveProjectMenuItem.setText("Save Project");
saveProjectMenuItem.setEnabled(false);
saveProjectMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        saveProjectMenuItemActionPerformed(evt);
    }
    });
    fileMenu.add(saveProjectMenuItem);

    saveProjectAsMenuItem.setText("Save Copy of Project As...");
    saveProjectAsMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            saveProjectAsMenuItemActionPerformed(evt);
        }
    });
    fileMenu.add(saveProjectAsMenuItem);
    fileMenu.add(jSeparator8);

    importOldMapMenuItem.setText("Import Old Map Image…");
    importOldMapMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            importOldMapMenuItemActionPerformed(evt);
        }
    });
    fileMenu.add(importOldMapMenuItem);

    importNewMapMenuItem.setText("Import New Map Image…");
    importNewMapMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            importNewMapMenuItemActionPerformed(evt);
        }
    });
    fileMenu.add(importNewMapMenuItem);

    importPointsMenu.setText("Import Points");

    importLinkedPointsMenuItem.setText("Linked Points for Old and New Map");
    importLinkedPointsMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            importLinkedPointsMenuItemActionPerformed(evt);
        }
    });
    importPointsMenu.add(importLinkedPointsMenuItem);
    importPointsMenu.add(jSeparator15);

    importOldPointsMenuItem.setText("For Old Map");
    importOldPointsMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            importOldPointsMenuItemActionPerformed(evt);
        }
    });
    importPointsMenu.add(importOldPointsMenuItem);

    importNewPointsMenuItem.setText("For New Map");
    importNewPointsMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            importNewPointsMenuItemActionPerformed(evt);
        }
    });
    importPointsMenu.add(importNewPointsMenuItem);

    fileMenu.add(importPointsMenu);
    fileMenu.add(jSeparator2);

    exportOldMapGraphicsMenu.setText("Export Old Map Graphics");

    exportOldShapeMenuItem.setText("ESRI Shape");
    exportOldShapeMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            exportOldShapeMenuItemActionPerformed(evt);
        }
    });
    exportOldMapGraphicsMenu.add(exportOldShapeMenuItem);

    exportOldSVGMenuItem.setText("SVG (Scalable Vector Graphics)");
    exportOldSVGMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            exportOldSVGMenuItemActionPerformed(evt);
        }
    });
    exportOldMapGraphicsMenu.add(exportOldSVGMenuItem);

    exportOldWMFMenuItem.setText("WMF (Windows Metafile)");
    exportOldWMFMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            exportOldWMFMenuItemActionPerformed(evt);
        }
    });
    exportOldMapGraphicsMenu.add(exportOldWMFMenuItem);

    exportOldUngenerateMenuItem.setText("Ungenerate");
    exportOldUngenerateMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            exportOldUngenerateMenuItemActionPerformed(evt);
        }
    });
    exportOldMapGraphicsMenu.add(exportOldUngenerateMenuItem);

    exportOldDXFMenuItem.setText("DXF");
    exportOldDXFMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            exportOldDXFMenuItemActionPerformed(evt);
        }
    });
    exportOldMapGraphicsMenu.add(exportOldDXFMenuItem);

    exportOldJPEGMenuItem.setText("JPEG");
    exportOldJPEGMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            exportOldJPEGMenuItemActionPerformed(evt);
        }
    });
    exportOldMapGraphicsMenu.add(exportOldJPEGMenuItem);

    exportOldPNGMenuItem.setText("PNG");
    exportOldPNGMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            exportOldPNGMenuItemActionPerformed(evt);
        }
    });
    exportOldMapGraphicsMenu.add(exportOldPNGMenuItem);

    fileMenu.add(exportOldMapGraphicsMenu);

    exportNewMapGraphicsMenu.setText("Export New Map Graphics");

    exportNewShapeMenuItem.setText("ESRI Shape");
    exportNewShapeMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            exportNewShapeMenuItemActionPerformed(evt);
        }
    });
    exportNewMapGraphicsMenu.add(exportNewShapeMenuItem);

    exportNewSVGMenuItem.setText("SVG (Scalable Vector Graphics)");
    exportNewSVGMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            exportNewSVGMenuItemActionPerformed(evt);
        }
    });
    exportNewMapGraphicsMenu.add(exportNewSVGMenuItem);

    exportNewWMFMenuItem.setText("WMF (Windows Metafile)");
    exportNewWMFMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            exportNewWMFMenuItemActionPerformed(evt);
        }
    });
    exportNewMapGraphicsMenu.add(exportNewWMFMenuItem);

    exportNewUngenerateMenuItem.setText("Ungenerate");
    exportNewUngenerateMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            exportNewUngenerateMenuItemActionPerformed(evt);
        }
    });
    exportNewMapGraphicsMenu.add(exportNewUngenerateMenuItem);

    exportNewDXFMenuItem.setText("DXF");
    exportNewDXFMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            exportNewDXFMenuItemActionPerformed(evt);
        }
    });
    exportNewMapGraphicsMenu.add(exportNewDXFMenuItem);

    exportNewJPEGMenuItem.setText("JPEG");
    exportNewJPEGMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            exportNewJPEGMenuItemActionPerformed(evt);
        }
    });
    exportNewMapGraphicsMenu.add(exportNewJPEGMenuItem);

    exportNewPNGMenuItem.setText("PNG");
    exportNewPNGMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            exportNewPNGMenuItemActionPerformed(evt);
        }
    });
    exportNewMapGraphicsMenu.add(exportNewPNGMenuItem);

    fileMenu.add(exportNewMapGraphicsMenu);

    exportPointsMenu.setText("Export Points");

    exportOldPointsMenuItem.setText("Of Old Map");
    exportOldPointsMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            exportOldPointsMenuItemActionPerformed(evt);
        }
    });
    exportPointsMenu.add(exportOldPointsMenuItem);

    exportNewPointsMenuItem.setText("Of New Map");
    exportNewPointsMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            exportNewPointsMenuItemActionPerformed(evt);
        }
    });
    exportPointsMenu.add(exportNewPointsMenuItem);
    exportPointsMenu.add(jSeparator16);

    exportLinkedPointsMenuItem.setText("Linked Points of Old and New Map");
    exportLinkedPointsMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            exportLinkedPointsMenuItemActionPerformed(evt);
        }
    });
    exportPointsMenu.add(exportLinkedPointsMenuItem);

    exportLinkedPointsAndVectorsInNewMap.setText("Linked Points of Old and New Map and Vectors in New Map");
    exportLinkedPointsAndVectorsInNewMap.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            exportLinkedPointsAndVectorsInNewMapActionPerformed(evt);
        }
    });
    exportPointsMenu.add(exportLinkedPointsAndVectorsInNewMap);

    exportLinkedPointsInPixelsMenuItem.setText("Linked Points: Old in Pixels and New in Meters");
    exportLinkedPointsInPixelsMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            exportLinkedPointsInPixelsMenuItemActionPerformed(evt);
        }
    });
    exportPointsMenu.add(exportLinkedPointsInPixelsMenuItem);

    fileMenu.add(exportPointsMenu);
    fileMenu.add(exitMenuSeparator);
    if (Sys.isMacOSX()) {
        fileMenu.remove(exitMenuSeparator);
    }

    exitMenuItem.setText("Exit");
    exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            exitMenuItemActionPerformed(evt);
        }
    });
    fileMenu.add(exitMenuItem);
    if (Sys.isMacOSX()) {
        fileMenu.remove(exitMenuItem);
    }

    menuBar.add(fileMenu);

    editMenu.setText("Edit");

    undoMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
undoMenuItem.setText("Undo");
undoMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        undoMenuItemActionPerformed(evt);
    }
    });
    editMenu.add(undoMenuItem);

    redoMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Y,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
redoMenuItem.setText("Redo");
redoMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        redoMenuItemActionPerformed(evt);
    }
    });
    editMenu.add(redoMenuItem);
    editMenu.add(jSeparator3);

    cutMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
cutMenuItem.setText("Cut");
cutMenuItem.setEnabled(false);
cutMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        cutMenuItemActionPerformed(evt);
    }
    });
    editMenu.add(cutMenuItem);

    copyMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
copyMenuItem.setText("Copy");
copyMenuItem.setEnabled(false);
copyMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        copyMenuItemActionPerformed(evt);
    }
    });
    editMenu.add(copyMenuItem);

    pasteMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
pasteMenuItem.setText("Paste");
pasteMenuItem.setEnabled(false);
pasteMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        pasteMenuItemActionPerformed(evt);
    }
    });
    editMenu.add(pasteMenuItem);

    deleteMenuItem.setText("Delete");
    deleteMenuItem.setEnabled(false);
    deleteMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            deleteMenuItemActionPerformed(evt);
        }
    });
    editMenu.add(deleteMenuItem);
    editMenu.add(separator);

    selectPointsMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
selectPointsMenuItem.setText("Select All Points");
selectPointsMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        selectPointsMenuItemActionPerformed(evt);
    }
    });
    editMenu.add(selectPointsMenuItem);

    selectUnlinkedPointsMenuItem.setText("Select Unlinked Points");
    selectUnlinkedPointsMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            selectUnlinkedPointsMenuItemActionPerformed(evt);
        }
    });
    editMenu.add(selectUnlinkedPointsMenuItem);

    deselectPointsMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_D,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
deselectPointsMenuItem.setText("Deselect All Points");
deselectPointsMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        deselectPointsMenuItemActionPerformed(evt);
    }
    });
    editMenu.add(deselectPointsMenuItem);
    editMenu.add(jSeparator7);

    findLinkMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
findLinkMenuItem.setText("Find Link…");
findLinkMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        findLinkMenuItemActionPerformed(evt);
    }
    });
    editMenu.add(findLinkMenuItem);
    editMenu.add(jSeparator13);

    placePointMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
placePointMenuItem.setText("Place Point From Coordinate List…");
placePointMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        placePointMenuItemActionPerformed(evt);
    }
    });
    editMenu.add(placePointMenuItem);
    editMenu.add(jSeparator10);

    showPointListMenuItem.setText("Show List of Linked Points");
    showPointListMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            showPointListMenuItemActionPerformed(evt);
        }
    });
    editMenu.add(showPointListMenuItem);
    editMenu.add(jSeparator1);

    linkedPointsColorMenuItem.setText("Color Of Linked Points");
    linkedPointsColorMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            linkedPointsColorMenuItemActionPerformed(evt);
        }
    });
    editMenu.add(linkedPointsColorMenuItem);

    unlinkedPointsColorMenuItem.setText("Color Of Unlinked Points");
    unlinkedPointsColorMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            unlinkedPointsColorMenuItemActionPerformed(evt);
        }
    });
    editMenu.add(unlinkedPointsColorMenuItem);

    pointSymbolMenu.setText("Point Symbol");

    pointSymbol1MenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/point1.gif"))); // NOI18N
    pointSymbol1MenuItem.setName("symbol1"); // NOI18N
    pointSymbol1MenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            pointSymbolMenuItemActionPerformed(evt);
        }
    });
    pointSymbolMenu.add(pointSymbol1MenuItem);

    pointSymbol2MenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/point2.gif"))); // NOI18N
    pointSymbol2MenuItem.setName("symbol2"); // NOI18N
    pointSymbol2MenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            pointSymbolMenuItemActionPerformed(evt);
        }
    });
    pointSymbolMenu.add(pointSymbol2MenuItem);

    pointSymbol3MenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/point3.gif"))); // NOI18N
    pointSymbol3MenuItem.setName("symbol3"); // NOI18N
    pointSymbol3MenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            pointSymbolMenuItemActionPerformed(evt);
        }
    });
    pointSymbolMenu.add(pointSymbol3MenuItem);

    pointSymbol4MenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/point4.gif"))); // NOI18N
    pointSymbol4MenuItem.setName("symbol4"); // NOI18N
    pointSymbol4MenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            pointSymbolMenuItemActionPerformed(evt);
        }
    });
    pointSymbolMenu.add(pointSymbol4MenuItem);

    pointSymbol5MenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/point5.gif"))); // NOI18N
    pointSymbol5MenuItem.setName("symbol5"); // NOI18N
    pointSymbol5MenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            pointSymbolMenuItemActionPerformed(evt);
        }
    });
    pointSymbolMenu.add(pointSymbol5MenuItem);

    pointSymbol6MenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/point6.gif"))); // NOI18N
    pointSymbol6MenuItem.setName("symbol6"); // NOI18N
    pointSymbol6MenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            pointSymbolMenuItemActionPerformed(evt);
        }
    });
    pointSymbolMenu.add(pointSymbol6MenuItem);

    editMenu.add(pointSymbolMenu);

    menuBar.add(editMenu);

    mapsMenu.setText("Maps");
    mapsMenu.addMenuListener(new javax.swing.event.MenuListener() {
        public void menuCanceled(javax.swing.event.MenuEvent evt) {
        }
        public void menuDeselected(javax.swing.event.MenuEvent evt) {
        }
        public void menuSelected(javax.swing.event.MenuEvent evt) {
            mapsMenuMenuSelected(evt);
        }
    });

    mapSizeMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_M,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
mapSizeMenuItem.setText("About the Maps…");
mapSizeMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        mapSizeMenuItemActionPerformed(evt);
    }
    });
    mapsMenu.add(mapSizeMenuItem);
    mapsMenu.add(jSeparator14);

    removeOldRasterImageMenuItem.setText("Remove Old Map Image");
    removeOldRasterImageMenuItem.setEnabled(false);
    removeOldRasterImageMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            removeOldRasterImageMenuItemActionPerformed(evt);
        }
    });
    mapsMenu.add(removeOldRasterImageMenuItem);

    removeNewRasterImageMenuItem.setText("Remove New Map Image");
    removeNewRasterImageMenuItem.setEnabled(false);
    removeNewRasterImageMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            removeNewRasterImageMenuItemActionPerformed(evt);
        }
    });
    mapsMenu.add(removeNewRasterImageMenuItem);
    mapsMenu.add(jSeparator17);

    addOSMMenuItem.setText("Add OpenStreetMap");
    addOSMMenuItem.setEnabled(false);
    addOSMMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            addOSMMenuItemActionPerformed(evt);
        }
    });
    mapsMenu.add(addOSMMenuItem);

    removeOSMMenuItem.setText("Remove OpenStreetMap");
    removeOSMMenuItem.setEnabled(false);
    removeOSMMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            removeOSMMenuItemActionPerformed(evt);
        }
    });
    mapsMenu.add(removeOSMMenuItem);
    mapsMenu.add(jSeparator22);

    osmMenu.setText("OpenStreetMap");

    showOSMGraticuleCheckBoxMenuItem.setSelected(true);
    showOSMGraticuleCheckBoxMenuItem.setText("Show Longitude/Latitude");
    showOSMGraticuleCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            showOSMGraticuleCheckBoxMenuItemActionPerformed(evt);
        }
    });
    osmMenu.add(showOSMGraticuleCheckBoxMenuItem);

    showOSMTropicsCheckBoxMenuItem.setText("Show Tropics");
    showOSMTropicsCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            showOSMTropicsCheckBoxMenuItemActionPerformed(evt);
        }
    });
    osmMenu.add(showOSMTropicsCheckBoxMenuItem);

    showOSMPolarCirclesCheckBoxMenuItem.setText("Show Polar Circles");
    showOSMPolarCirclesCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            showOSMPolarCirclesCheckBoxMenuItemActionPerformed(evt);
        }
    });
    osmMenu.add(showOSMPolarCirclesCheckBoxMenuItem);
    osmMenu.add(jSeparator24);

    correctOSMMisalignmentBugMenuItem.setText("Correct OpenStreetMap Misalignment…");
    correctOSMMisalignmentBugMenuItem.setEnabled(false);
    correctOSMMisalignmentBugMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            correctOSMMisalignmentBugMenuItemActionPerformed(evt);
        }
    });
    osmMenu.add(correctOSMMisalignmentBugMenuItem);

    mapsMenu.add(osmMenu);

    menuBar.add(mapsMenu);

    analysisMenu.setText("Analyze");

    computeMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_K,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
computeMenuItem.setText("Compute");
computeMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        computeMenuItemActionPerformed(evt);
    }
    });
    analysisMenu.add(computeMenuItem);

    showReportMenuItem.setText("Show Report of Last Computation");
    showReportMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            showReportMenuItemActionPerformed(evt);
        }
    });
    analysisMenu.add(showReportMenuItem);
    analysisMenu.add(jSeparator11);

    transformationMenu.setText("Transformation");

    transformationButtonGroup.add(helmertCheckBoxMenuItem);
    helmertCheckBoxMenuItem.setSelected(true);
    helmertCheckBoxMenuItem.setText("Helmert 4 Parameters");
    helmertCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            helmertCheckBoxMenuItemActionPerformed(evt);
        }
    });
    transformationMenu.add(helmertCheckBoxMenuItem);

    transformationButtonGroup.add(affine5CheckBoxMenuItem);
    affine5CheckBoxMenuItem.setText("Affine 5 Parameters");
    affine5CheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            affine5CheckBoxMenuItemActionPerformed(evt);
        }
    });
    transformationMenu.add(affine5CheckBoxMenuItem);

    transformationButtonGroup.add(affine6CheckBoxMenuItem);
    affine6CheckBoxMenuItem.setText("Affine 6 Parameters");
    affine6CheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            affine6CheckBoxMenuItemActionPerformed(evt);
        }
    });
    transformationMenu.add(affine6CheckBoxMenuItem);

    robustEstimatorMenu.setText("Robust Helmert Estimator");

    transformationButtonGroup.add(huberEstimatorCheckBoxMenuItem);
    huberEstimatorCheckBoxMenuItem.setText("Huber Estimator");
    huberEstimatorCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            huberEstimatorCheckBoxMenuItemActionPerformed(evt);
        }
    });
    robustEstimatorMenu.add(huberEstimatorCheckBoxMenuItem);

    setHuberEstimatorParametersMenuItem.setText("Set Huber Estimator Parameters");
    setHuberEstimatorParametersMenuItem.setEnabled(false);
    setHuberEstimatorParametersMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            setHuberEstimatorParametersMenuItemActionPerformed(evt);
        }
    });
    robustEstimatorMenu.add(setHuberEstimatorParametersMenuItem);
    robustEstimatorMenu.add(jSeparator4);

    transformationButtonGroup.add(vEstimatorCheckBoxMenuItem);
    vEstimatorCheckBoxMenuItem.setText("V Estimator");
    vEstimatorCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            vEstimatorCheckBoxMenuItemActionPerformed(evt);
        }
    });
    robustEstimatorMenu.add(vEstimatorCheckBoxMenuItem);

    setVEstimatorParametersMenuItem.setText("Set V Estimator Parameters");
    setVEstimatorParametersMenuItem.setEnabled(false);
    setVEstimatorParametersMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            setVEstimatorParametersMenuItemActionPerformed(evt);
        }
    });
    robustEstimatorMenu.add(setVEstimatorParametersMenuItem);
    robustEstimatorMenu.add(jSeparator18);

    transformationButtonGroup.add(hampelEstimatorCheckBoxMenuItem);
    hampelEstimatorCheckBoxMenuItem.setText("Hampel Estimator");
    hampelEstimatorCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            hampelEstimatorCheckBoxMenuItemActionPerformed(evt);
        }
    });
    robustEstimatorMenu.add(hampelEstimatorCheckBoxMenuItem);

    setHampelEstimatorParametersMenuItem.setText("Set Hampel Estimator Parameters");
    setHampelEstimatorParametersMenuItem.setEnabled(false);
    setHampelEstimatorParametersMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            setHampelEstimatorParametersMenuItemActionPerformed(evt);
        }
    });
    robustEstimatorMenu.add(setHampelEstimatorParametersMenuItem);

    transformationMenu.add(robustEstimatorMenu);

    analysisMenu.add(transformationMenu);

    compareTransformationsMenuItem.setText("Compare Transformations");
    compareTransformationsMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            compareTransformationsMenuItemActionPerformed(evt);
        }
    });
    analysisMenu.add(compareTransformationsMenuItem);
    analysisMenu.add(jSeparator6);

    projectionMenuItem.setText("Projection of Old Map…");
    projectionMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            projectionMenuItemActionPerformed(evt);
        }
    });
    analysisMenu.add(projectionMenuItem);
    analysisMenu.remove(projectionMenuItem);

    analysisMenu.add(projectionSeparator);
    analysisMenu.remove(projectionSeparator);

    showErrorInMapButtonGroup.add(showErrorInOldMapCheckBoxMenuItem);
    showErrorInOldMapCheckBoxMenuItem.setSelected(true);
    showErrorInOldMapCheckBoxMenuItem.setText("Old Map");
    showErrorInOldMapCheckBoxMenuItem.addChangeListener(new javax.swing.event.ChangeListener() {
        public void stateChanged(javax.swing.event.ChangeEvent evt) {
            showErrorInOldMapCheckBoxMenuItemStateChanged(evt);
        }
    });
    showErrorInOldMapCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            showErrorInOldMapCheckBoxMenuItemActionPerformed(evt);
        }
    });
    analysisMenu.add(showErrorInOldMapCheckBoxMenuItem);

    showErrorInMapButtonGroup.add(showErrorInNewMapCheckBoxMenuItem);
    showErrorInNewMapCheckBoxMenuItem.setText("New Map");
    showErrorInNewMapCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            showErrorInNewMapCheckBoxMenuItemActionPerformed(evt);
        }
    });
    analysisMenu.add(showErrorInNewMapCheckBoxMenuItem);

    menuBar.add(analysisMenu);

    viewMenu.setText("View");

    zoomInMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ADD,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
zoomInMenuItem.setText("Zoom In");
zoomInMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        zoomInMenuItemActionPerformed(evt);
    }
    });
    viewMenu.add(zoomInMenuItem);

    zoomOutMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SUBTRACT,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
zoomOutMenuItem.setText("Zoom Out");
zoomOutMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        zoomOutMenuItemActionPerformed(evt);
    }
    });
    viewMenu.add(zoomOutMenuItem);
    viewMenu.add(jSeparator12);

    showAllMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_NUMPAD0,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
showAllMenuItem.setText("Show All");
showAllMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        showAllMenuItemActionPerformed(evt);
    }
    });
    viewMenu.add(showAllMenuItem);

    showAllOldMenuItem.setText("Show All in Old Map");
    showAllOldMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            showAllOldMenuItemActionPerformed(evt);
        }
    });
    viewMenu.add(showAllOldMenuItem);

    showAllNewMenuItem.setText("Show All in New Map");
    showAllNewMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            showAllNewMenuItemActionPerformed(evt);
        }
    });
    viewMenu.add(showAllNewMenuItem);
    viewMenu.add(jSeparator9);

    showPointsCheckBoxMenuItem.setSelected(true);
    showPointsCheckBoxMenuItem.setText("Show Points");
    showPointsCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            showPointsCheckBoxMenuItemActionPerformed(evt);
        }
    });
    viewMenu.add(showPointsCheckBoxMenuItem);

    showOldCheckBoxMenuItem.setSelected(true);
    showOldCheckBoxMenuItem.setText("Show Old Map");
    showOldCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            showMapCheckBoxMenuItemActionPerformed(evt);
        }
    });
    viewMenu.add(showOldCheckBoxMenuItem);

    showNewCheckBoxMenuItem.setSelected(true);
    showNewCheckBoxMenuItem.setText("Show New Reference Map");
    showNewCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            showMapCheckBoxMenuItemActionPerformed(evt);
        }
    });
    viewMenu.add(showNewCheckBoxMenuItem);
    viewMenu.add(jSeparator25);

    oldMapCoordinateDisplayUnitMenu.setText("Old Map Coordinate Display Unit");

    oldMapDisplayUnitButtonGroup.add(oldUnitCmCheckBoxMenuItem);
    oldUnitCmCheckBoxMenuItem.setSelected(true);
    oldUnitCmCheckBoxMenuItem.setText("cm");
    oldUnitCmCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            oldUnitCmCheckBoxMenuItemActionPerformed(evt);
        }
    });
    oldMapCoordinateDisplayUnitMenu.add(oldUnitCmCheckBoxMenuItem);

    oldMapDisplayUnitButtonGroup.add(oldUnitMCheckBoxMenuItem);
    oldUnitMCheckBoxMenuItem.setText("m");
    oldUnitMCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            oldUnitMCheckBoxMenuItemActionPerformed(evt);
        }
    });
    oldMapCoordinateDisplayUnitMenu.add(oldUnitMCheckBoxMenuItem);

    oldMapDisplayUnitButtonGroup.add(oldUnitInchCheckBoxMenuItem);
    oldUnitInchCheckBoxMenuItem.setText("inch");
    oldUnitInchCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            oldUnitInchCheckBoxMenuItemActionPerformed(evt);
        }
    });
    oldMapCoordinateDisplayUnitMenu.add(oldUnitInchCheckBoxMenuItem);

    oldMapDisplayUnitButtonGroup.add(oldUnitPxCheckBoxMenuItem);
    oldUnitPxCheckBoxMenuItem.setText("pixel");
    oldUnitPxCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            oldUnitPxCheckBoxMenuItemActionPerformed(evt);
        }
    });
    oldMapCoordinateDisplayUnitMenu.add(oldUnitPxCheckBoxMenuItem);

    viewMenu.add(oldMapCoordinateDisplayUnitMenu);

    menuBar.add(viewMenu);

    windowMenu.setText("Window");
    menuBar.add(windowMenu);

    helpMenu.setText(Sys.isMacOSX() ? "Help" : "?");

    infoMenuItem.setText("Info");
    infoMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            infoMenuItemActionPerformed(evt);
        }
    });
    helpMenu.add(infoMenuItem);

    menuBar.add(helpMenu);

    debugMenu.setText("Debug");

    warpMenuItem.setText("Warp Image");
    warpMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            warpMenuItemActionPerformed(evt);
        }
    });
    debugMenu.add(warpMenuItem);

    menuBar.add(debugMenu);

    setJMenuBar(menuBar);

    pack();
    }// </editor-fold>//GEN-END:initComponents

    private void errorVectorsFillColorButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_errorVectorsFillColorButtonActionPerformed
        readErrorVectorFromGUIAndRepaint();
    }//GEN-LAST:event_errorVectorsFillColorButtonActionPerformed

    private void errorVectorsFillCirclesCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_errorVectorsFillCirclesCheckBoxActionPerformed
        readErrorVectorFromGUIAndRepaint();
    }//GEN-LAST:event_errorVectorsFillCirclesCheckBoxActionPerformed

    private void cutMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cutMenuItemActionPerformed
        final LinkManager linkManager = this.manager.getLinkManager();
        MainWindow.clipboard = linkManager.serializePoints(true);
        linkManager.deletePointsAndLinks(true);
        this.addUndo("Cut Points");
        MainWindow.updateAllMenusOfAllWindows();
    }//GEN-LAST:event_cutMenuItemActionPerformed

    private void pasteMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pasteMenuItemActionPerformed
        try {
            this.manager.getLinkManager().deserializePoints(MainWindow.clipboard);
            this.showAll();
        } catch (Exception e) {
            // Inform the user about the exception.
            String title = "Error when Pasting";
            String msg = "An error occured when pasting the point(s).\n";
            if (e.getMessage() != null) {
                msg += e.getMessage();
            }
            JOptionPane.showMessageDialog(this, msg, title,
                    JOptionPane.ERROR_MESSAGE, null);
        }
        this.addUndo("Paste Points");
        MainWindow.updateAllMenusOfAllWindows();
    }//GEN-LAST:event_pasteMenuItemActionPerformed

    private void closeWindow(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_closeWindow
        if (this.closeProject() && Sys.isWindows()) {
            System.exit(0);
        }
    }//GEN-LAST:event_closeWindow

    private void closeProjectMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeProjectMenuItemActionPerformed
        closeProject();
}//GEN-LAST:event_closeProjectMenuItemActionPerformed

    private void newProjectMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newProjectMenuItemActionPerformed
        MainWindow.newProject();
    }//GEN-LAST:event_newProjectMenuItemActionPerformed

    private void isolinesScaleIntervalNumberFieldPropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_isolinesScaleIntervalNumberFieldPropertyChange
        if ("value".equals(evt.getPropertyName())) {
            readIsolinesFromGUI();
            clearTemporaryGUI();
        }
    }//GEN-LAST:event_isolinesScaleIntervalNumberFieldPropertyChange

    private void errorVectorsScaleNumberFieldPropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_errorVectorsScaleNumberFieldPropertyChange
        if ("value".equals(evt.getPropertyName())) {
            readErrorVectorFromGUI();
            clearTemporaryGUI();
        }
    }//GEN-LAST:event_errorVectorsScaleNumberFieldPropertyChange

    private void distortionGridMeshSizeNumberFieldPropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_distortionGridMeshSizeNumberFieldPropertyChange
        if ("value".equals(evt.getPropertyName())) {
            this.readDistortionGridFromGUI();
            clearTemporaryGUI();
        }
    }//GEN-LAST:event_distortionGridMeshSizeNumberFieldPropertyChange

    private void distortionGridMeshSizeNumberFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_distortionGridMeshSizeNumberFieldFocusLost
//        this.readDistortionFromGUI();
//        clearTemporaryGUI();
    }//GEN-LAST:event_distortionGridMeshSizeNumberFieldFocusLost

    private void robustEstimatorInvalidValue() {
        // warns if the user entered aika.mapanalystid value or type
        String msg = "At least one invalid value has been entered.\n"
                + "The parameters haven't been changed.";
        String messageDialogTitle = "Invalid value";
        javax.swing.JOptionPane.showMessageDialog(this, msg, messageDialogTitle,
                javax.swing.JOptionPane.ERROR_MESSAGE, null);
    }

    private void setHampelEstimatorParametersMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setHampelEstimatorParametersMenuItemActionPerformed

        HampelEstimator hampelEstimator = manager.getHampelEstimator();

        double currA = hampelEstimator.getA();
        double currB = hampelEstimator.getB();
        double currC = hampelEstimator.getC();
        double minA = hampelEstimator.getMinA();
        double minB = hampelEstimator.getMinB();
        double minC = hampelEstimator.getMinC();
        double maxA = hampelEstimator.getMaxA();
        double maxB = hampelEstimator.getMaxB();
        double maxC = hampelEstimator.getMaxC();

        // writes the range of valid values in the dialog box
        String strATextArea = "a has to be between " + minA + " and " + maxA;
        String strBTextArea = "b has to be between " + minB + " and " + maxB;
        String strCTextArea = "c has to be between " + minC + " and " + maxC;
        hampelEstimatorMinMaxATextArea.setText(strATextArea);
        hampelEstimatorMinMaxBTextArea.setText(strBTextArea);
        hampelEstimatorMinMaxCTextArea.setText(strCTextArea);

        // writes the current values of the parameters in the textfields
        hampelEstimatorATextField.setText(String.valueOf(currA));
        hampelEstimatorBTextField.setText(String.valueOf(currB));
        hampelEstimatorCTextField.setText(String.valueOf(currC));

        String optionDialogTitle = "Parameters for Hampel Estimator";
        int response = JOptionPane.showOptionDialog(this,
                this.hampelEstimatorParametersPanel,
                optionDialogTitle,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null, null, null);

        if (response == 0) {
            String strA = hampelEstimatorATextField.getText();
            String strB = hampelEstimatorBTextField.getText();
            String strC = hampelEstimatorCTextField.getText();

            // checks if the input values are valid
            try {
                double inputA = Double.parseDouble(strA);
                double inputB = Double.parseDouble(strB);
                double inputC = Double.parseDouble(strC);
                if (inputA >= minA && inputA <= maxA && inputA < inputB && inputA < inputC) {
                    hampelEstimator.setA(inputA);
                } else {
                    throw new Exception();
                }
                if (inputB >= minB && inputB <= maxB && inputB < inputC) {
                    hampelEstimator.setB(inputB);
                } else {
                    throw new Exception();
                }
                if (inputC >= minC && inputC <= maxC) {
                    hampelEstimator.setC(inputC);
                } else {
                    throw new Exception();
                }
                this.clearTemporaryGUI();
            } catch (Exception e) {
                robustEstimatorInvalidValue();
            }
        }
    }//GEN-LAST:event_setHampelEstimatorParametersMenuItemActionPerformed

    private void setVEstimatorParametersMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setVEstimatorParametersMenuItemActionPerformed

        VEstimator vEstimator = manager.getVEstimator();
        double currE = vEstimator.getE();
        double currK = vEstimator.getK();
        double minE = vEstimator.getMinE();
        double maxE = vEstimator.getMaxE();
        double minK = vEstimator.getMinK();
        double maxK = vEstimator.getMaxK();

        // writes the range of valid values in the dialog box
        String strETextArea = "e has to be between " + minE + " and " + maxE;
        String strKTextArea = "k has to be between " + minK + " and " + maxK;
        vEstimatorMinMaxETextArea.setText(strETextArea);
        vEstimatorMinMaxKTextArea.setText(strKTextArea);

        // writes the current values of the parameters in the textfields
        vEstimatorETextField.setText(String.valueOf(currE));
        vEstimatorKTextField.setText(String.valueOf(currK));

        String optionDialogTitle = "Parameters for V Estimator";
        int response = JOptionPane.showOptionDialog(this,
                this.vEstimatorParametersPanel,
                optionDialogTitle,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null, null, null);
        if (response == JOptionPane.OK_OPTION) {
            String strE = vEstimatorETextField.getText();
            String strK = vEstimatorKTextField.getText();

            // checks if the input values are valid
            try {
                double inputE = Double.parseDouble(strE);
                double inputK = Double.parseDouble(strK);
                if (inputE >= minE && inputE <= maxE) {
                    vEstimator.setE(inputE);
                } else {
                    throw new Exception();
                }
                if (inputK >= minK && inputK <= maxK) {
                    vEstimator.setK(inputK);
                } else {
                    throw new Exception();
                }
                this.clearTemporaryGUI();
            } catch (Exception e) {
                robustEstimatorInvalidValue();
            }
        }
    }//GEN-LAST:event_setVEstimatorParametersMenuItemActionPerformed

    private void setHuberEstimatorParametersMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setHuberEstimatorParametersMenuItemActionPerformed

        HuberEstimator huberEstimator = this.manager.getHuberEstimator();
        double currK = huberEstimator.getK();
        double minK = huberEstimator.getMinK();
        double maxK = huberEstimator.getMaxK();

        // writes the range of valid values in the dialog box
        String strKTextArea = "k has to be between " + minK + " and " + maxK;
        huberEstimatorMinMaxTextArea.setText(strKTextArea);

        // writes the current value of the parameter in the textfield
        huberEstimatorKTextField.setText(String.valueOf(currK));

        String optionDialogTitle = "Parameters for Huber Estimator";
        int response = JOptionPane.showOptionDialog(this,
                this.huberEstimatorParametersPanel,
                optionDialogTitle,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null, null, null);
        if (response == 0) {
            String strK = huberEstimatorKTextField.getText();
            // checks if the input value is valid
            try {
                double inputK = Double.parseDouble(strK);
                if (inputK >= minK && inputK <= maxK) {
                    huberEstimator.setK(inputK);
                } else {
                    throw new Exception();
                }
                this.clearTemporaryGUI();
            } catch (Exception e) {
                robustEstimatorInvalidValue();
            }
        }
    }//GEN-LAST:event_setHuberEstimatorParametersMenuItemActionPerformed

    private void selectUnlinkedPointsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectUnlinkedPointsMenuItemActionPerformed
        this.manager.getLinkManager().selectUnlinkedPoints();
        this.addUndo("Select Unlinked Points");
    }//GEN-LAST:event_selectUnlinkedPointsMenuItemActionPerformed

    private void circlesLineAppearancePanelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_circlesLineAppearancePanelActionPerformed
        this.readCirclesFromGUIAndRepaint();
    }//GEN-LAST:event_circlesLineAppearancePanelActionPerformed

    private void isolinesAppearancePanelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_isolinesAppearancePanelActionPerformed
        this.readIsolinesFromGUIAndRepaint();
    }//GEN-LAST:event_isolinesAppearancePanelActionPerformed

    private void errorVectorsLineAppearancePanelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_errorVectorsLineAppearancePanelActionPerformed
        readErrorVectorFromGUIAndRepaint();
    }//GEN-LAST:event_errorVectorsLineAppearancePanelActionPerformed

    private void distortionGridLineAppearancePanelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_distortionGridLineAppearancePanelActionPerformed
        readDistortionFromGUIAndRepaint();
    }//GEN-LAST:event_distortionGridLineAppearancePanelActionPerformed

    private void distortionGridExtensionComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_distortionGridExtensionComboBoxItemStateChanged

        if (evt.getStateChange() != java.awt.event.ItemEvent.SELECTED) {
            return;
        }

        if (this.distortionGridExtensionComboBox.getSelectedIndex() == 2) {
            // the user wants to use a custom polygon
            // search the polygon
            GeometryTransformer transformer = this.manager.getGeometryTransformer();
            DistortionGrid distortionGrid = this.manager.getDistortionGrid();

            boolean analyseOldMap = this.showErrorInOldMapCheckBoxMenuItem.isSelected();
            final GeoSet geoSet;
            final boolean hasCustomClipPoly;
            if (analyseOldMap) {
                geoSet = transformer.getOldSourceGeoSet();
                hasCustomClipPoly = distortionGrid.hasOldClipPolygon();
            } else {
                geoSet = transformer.getNewSourceGeoSet();
                hasCustomClipPoly = distortionGrid.hasNewClipPolygon();
            }
            GeoObject geoObject = geoSet.getSingleSelectedGeoObject();
            if (geoObject == null && hasCustomClipPoly == false) {

                // no polygon found.
                // switch menu back to the previous setting
                int id = distortionGrid.getClipWithHull();
                this.distortionGridExtensionComboBox.setSelectedIndex(id);

                // Inform the user and return.
                String msg = "Please first draw a polygon in the map with the "
                        + "distorted grid using the pen tool.\n"
                        + "The polygon must be selected.\n"
                        + "Only one polygon can be used as mask.";
                String title = "No Polygon in Target Map";
                JOptionPane.showMessageDialog(this, msg, title,
                        JOptionPane.ERROR_MESSAGE, null);
                return;
            }

            // remove the GeoPath from the map
            geoSet.removeSelectedGeoObjects();

            if (geoObject != null) {
                // convert the found GeoPath to a polygon and pass it to DistortionGrid.
                GeoPath geoPath = (GeoPath) (geoObject);
                // make sure the polygon is closed
                geoPath.closePath();
                double[][] clipPolygon = geoPath.getFirstFlattenedPolygon(0);
                if (analyseOldMap) {
                    this.manager.getDistortionGrid().setOldClipPolygon(clipPolygon);
                } else {
                    this.manager.getDistortionGrid().setNewClipPolygon(clipPolygon);
                }
            }
        }

        this.readDistortionGridFromGUI();
        clearTemporaryGUI();
    }//GEN-LAST:event_distortionGridExtensionComboBoxItemStateChanged

    private void distortionComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_distortionComboBoxItemStateChanged
        this.readDistortionGridFromGUI();
        clearTemporaryGUI();
    }//GEN-LAST:event_distortionComboBoxItemStateChanged

    public void addUndo(String name) {
        try {
            byte[] points = this.manager.getLinkManager().serializePoints(false);
            this.undo.add(name, points);
        } catch (Exception e) {
            Logger.getLogger(MainWindow.class.getName()).log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void mapToolActionPerformed(
            MapTool mapTool,
            MapComponent mapComponent,
            String description) {
        this.addUndo(description);
        this.setDirty();
    }

    private void redoMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_redoMenuItemActionPerformed
        try {
            byte[] points = (byte[]) this.undo.getRedo();
            if (points != null) {
                this.manager.getLinkManager().deletePointsAndLinks(false);
                this.manager.getLinkManager().deserializePoints(points);
            }

            // it's likely redoing changed the points, so show them.
            this.showPoints();
        } catch (Exception e) {
            Logger.getLogger(MainWindow.class.getName()).log(Level.SEVERE, null, e);
        }
    }//GEN-LAST:event_redoMenuItemActionPerformed

    private void undoMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_undoMenuItemActionPerformed
        try {
            byte[] points = (byte[]) this.undo.getUndo();
            if (points != null) {
                this.manager.getLinkManager().deletePointsAndLinks(false);
                this.manager.getLinkManager().deserializePoints(points);
            }

            // it's likely undoing changed the points, so show them.
            this.showPoints();
        } catch (Exception e) {
            Logger.getLogger(MainWindow.class.getName()).log(Level.SEVERE, null, e);
        }
    }//GEN-LAST:event_undoMenuItemActionPerformed

    private void compareTransformationsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_compareTransformationsMenuItemActionPerformed
        if (!this.manager.getLinkManager().hasEnoughLinkedPointsForComputation()) {
            notEnoughLinkedPointsErrorMessage();
            return;
        }
        String nl = System.getProperty("line.separator");
        StringBuilder sb = new StringBuilder();

        // title
        sb.append("Comparison of Transformations").append(nl);
        sb.append("-----------------------------").append(nl).append(nl);

        // application name and version
        sb.append(ika.mapanalyst.ApplicationInfo.getApplicationName());
        sb.append(" version ");
        sb.append(ika.mapanalyst.ApplicationInfo.getApplicationVersion()).append(".").append(nl);

        // date
        sb.append("Computation date: ");
        sb.append(ika.mapanalyst.ApplicationInfo.getCurrentTimeAndDate()).append(".");
        sb.append(nl).append(nl);

        // project file name
        sb.append("Project file: ");
        sb.append(filePath == null ? "undefined" : filePath);
        sb.append(nl);

        // old map image
        String oldMapName = null;
        if (manager.getOldMap() != null) {
            oldMapName = manager.getOldMap().getFilePath();
        }
        if (oldMapName != null) {
            sb.append("Old map image: ").append(oldMapName).append(nl);
        }

        // new map image
        if (manager.isUsingOpenStreetMap()) {
            sb.append("New map: OpenStreetMap").append(nl);
        } else if (manager.getNewMap() != null) {
            String newMapName = manager.getNewMap().getFilePath();
            if (newMapName != null) {
                sb.append("New map image: ").append(newMapName).append(nl);
            }
        }

        // points used
        sb.append(nl);
        sb.append("Computation with ");
        sb.append(this.manager.getLinkManager().getNumberLinks());
        sb.append(" linked points.").append(nl).append(nl);

        // comparison of transformations
        sb.append(manager.compareTransformations());
        String title = "Comparison of Transformations";

        // show text in new window
        new TextWindow(this, true, true, sb.toString(), title);
    }//GEN-LAST:event_compareTransformationsMenuItemActionPerformed

    private void pointSymbolMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pointSymbolMenuItemActionPerformed
        PointSymbol lps = this.manager.getLinkManager().getLinkedPointSymbol();
        PointSymbol ups = this.manager.getLinkManager().getUnlinkedPointSymbol();

        JMenuItem menuItem = (JMenuItem) evt.getSource();
        String menuItemName = menuItem.getName();
        if ("symbol1".equals(menuItemName)) {
            lps.setRadius(4);
            lps.setStrokeWidth(3);
            lps.setLineLength(8);
            ups.setRadius(4);
            ups.setStrokeWidth(3);
            ups.setLineLength(8);
        } else if ("symbol2".equals(menuItemName)) {
            lps.setRadius(3);
            lps.setStrokeWidth(2);
            lps.setLineLength(6);
            ups.setRadius(3);
            ups.setStrokeWidth(2);
            ups.setLineLength(6);
        } else if ("symbol3".equals(menuItemName)) {
            lps.setRadius(3);
            lps.setStrokeWidth(2);
            lps.setLineLength(3);
            ups.setRadius(3);
            ups.setStrokeWidth(2);
            ups.setLineLength(3);
        } else if ("symbol4".equals(menuItemName)) {
            lps.setRadius(4);
            lps.setStrokeWidth(2);
            lps.setLineLength(0);
            ups.setRadius(4);
            ups.setStrokeWidth(2);
            ups.setLineLength(0);
        } else if ("symbol5".equals(menuItemName)) {
            lps.setRadius(3);
            lps.setStrokeWidth(1.5f);
            lps.setLineLength(0);
            ups.setRadius(3);
            ups.setStrokeWidth(1.5f);
            ups.setLineLength(0);
        } else if ("symbol6".equals(menuItemName)) {
            lps.setRadius(2);
            lps.setStrokeWidth(1.5f);
            lps.setLineLength(0);
            ups.setRadius(2);
            ups.setStrokeWidth(1.5f);
            ups.setLineLength(0);
        }

        showPoints();
    }//GEN-LAST:event_pointSymbolMenuItemActionPerformed

    private void drawingClearButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_drawingClearButtonActionPerformed
        boolean removeFromOldMap;

        Object[] possibleValues = {"Old Map", "New Map", "Cancel"};
        String title = "Clear Drawing";
        String msg = "Clear all drawings from the old or the new map?\n"
                + "This will not remove ika.mapanalystrmed drawings.";
        int res = JOptionPane.showOptionDialog(this,
                msg, title,
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null,
                possibleValues, possibleValues[0]);
        if (res == 2 || res == JOptionPane.CLOSED_OPTION) {
            return; // user canceled
        }
        if (res == 0) {
            removeFromOldMap = true;
        } else {
            removeFromOldMap = false;
        }
        this.manager.getGeometryTransformer().removeDrawing(removeFromOldMap);
    }//GEN-LAST:event_drawingClearButtonActionPerformed

    private void removeNewRasterImageMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeNewRasterImageMenuItemActionPerformed
        this.manager.setNewMap(null);
        // the visualization have to be recalculated when switching from/to OSM
        clearTemporaryGUI();
    }//GEN-LAST:event_removeNewRasterImageMenuItemActionPerformed

    private void removeOldRasterImageMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeOldRasterImageMenuItemActionPerformed
        this.manager.removeOldImage();
    }//GEN-LAST:event_removeOldRasterImageMenuItemActionPerformed

    private void pointExportError(Exception exc) {
        String msg = "The points could not be exported.\n" + exc.toString();
        javax.swing.JOptionPane.showMessageDialog(this, msg, "Export Error",
                javax.swing.JOptionPane.ERROR_MESSAGE, null);
    }

    /**
     * Asks the user whether to export to excel or text format.
     *
     * @return 0 for Excel, 1 for text, -1 if canceled
     */
    private int askFileFormat(boolean includeShape) {
        Object[] options;
        if (includeShape) {
            options = new String[]{"Excel (XLS)", "Text (CSV)", "ESRI Shape", "Cancel"};
        } else {
            options = new String[]{"Excel (XLS)", "Text (CSV)", "Cancel"};
        }
        int res = JOptionPane.showOptionDialog(this,
                "Please Select a File Format",
                "File Format",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, null);
        if (res == options.length - 1) {
            return -1; // user canceled
        }
        return res;
    }

    private void exportPoints(boolean forOldMap) {

        // test if there are any points to export
        GeoSet ptsGeoSet;
        if (forOldMap) {
            ptsGeoSet = manager.getLinkManager().getOldPointsGeoSet();
        } else {
            ptsGeoSet = manager.getLinkManager().getNewPointsGeoSet();
        }
        final int nbrPoints = ptsGeoSet.getNumberOfChildren();

        // if there are no points to export, inform the user.
        if (nbrPoints < 1) {
            String msg = "There are no points to export.\n";
            javax.swing.JOptionPane.showMessageDialog(this, msg, "Export Error",
                    javax.swing.JOptionPane.ERROR_MESSAGE, null);
            return;
        }

        // ask the user for a file to export to
        String msg = "Export Points of " + (forOldMap ? "Old Map" : "New Map");
        String fileName = forOldMap ? "Old map points" : "New map points";
        // don't require a file extension. The extension will be set when the format is selected
        String exportFilePath = ika.utils.FileUtils.askFile(this, msg, fileName, false, null, null);
        if (exportFilePath == null) {
            return; // user canceled
        }

        // ask user for the unit of the coordinates
        boolean askForLonLat = manager.isUsingOpenStreetMap() && !forOldMap;
        final double pixelSize = getMapPixelSize(forOldMap);
        final double scale = this.askCoordinateUnit(askForLonLat, pixelSize);
        if (scale == 0) {
            return; // user canceled
        }

        // ask for file format
        int res = askFileFormat(true);
        if (res < 0) {
            return; // user canceled
        }

        if (res == 2) {
            // export to Shape file
            exportToShape(ShapeGeometryExporter.POINT_SHAPE_TYPE, forOldMap, exportFilePath);
        } else {
            // export to ASCII file
            try {
                manager.exportPointsToASCII(exportFilePath, forOldMap, 1. / scale, res == 0);
            } catch (Exception e) {
                pointExportError(e);
            }
        }
    }

    private void exportNewPointsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportNewPointsMenuItemActionPerformed
        this.exportPoints(false);
    }//GEN-LAST:event_exportNewPointsMenuItemActionPerformed

    private void exportOldPointsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportOldPointsMenuItemActionPerformed
        this.exportPoints(true);
    }//GEN-LAST:event_exportOldPointsMenuItemActionPerformed

    private void exportLinkedPointsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportLinkedPointsMenuItemActionPerformed
        exportLinkedPoints(null);
    }//GEN-LAST:event_exportLinkedPointsMenuItemActionPerformed

    private void exportLinkedPoints(GeoImage oldMap) {

        final int nbrLinks = this.manager.getLinkManager().getNumberLinks();
        if (nbrLinks < 1) {
            noLinkedPointsErrorMessage();
            return;
        }

        String fileName = "Linked points.txt";
        String exportFilePath = FileUtils.askFile(this, "Export Linked Points", fileName, false, "txt", null);
        if (exportFilePath == null) {
            return; // user canceled
        }
        // ask for file format
        int res = askFileFormat(false);
        if (res < 0) {
            return; // user canceled
        }
        try {
            manager.exportLinkedPointsToASCII(exportFilePath, oldMap, res == 0);
        } catch (Exception e) {
            pointExportError(e);
        }

    }

    private void pointImportError(Exception exc) {
        String msg = "The points could not be imported.\n"
                + "Please make sure values are separated by comas.\n"
                + exc.toString();
        javax.swing.JOptionPane.showMessageDialog(this, msg, "Import Error",
                javax.swing.JOptionPane.ERROR_MESSAGE, null);
    }
    private final static String UNIT_M = "Meter";
    private final static String UNIT_CM = "Centimeter";
    private final static String UNIT_MM = "Millimeter";
    private final static String UNIT_IN = "Inch";
    private final static String UNIT_GEO = "Geographic Longitude / Latitude";
    private final static String UNIT_PIXELS = "Pixels";

    /**
     * Asks the user to select a unit for coordinates.
     *
     * @return 1 for meter, 0.01 for cm, 0.001 for mm, and 2.54/100. for inch. 0
     * if the user cancels. -1 if values should be converte to lon/lat.
     */
    private double askCoordinateUnit(boolean askForLonLat, double pixelSize) {
        String msg = "Please Select the Unit of the Coordinates: ";
        String title = "Coordinates Unit";
        final Object[] possibleValues;
        if (askForLonLat) {
            possibleValues = new Object[]{UNIT_M, UNIT_CM, UNIT_MM, UNIT_IN, UNIT_GEO};
        } else if (pixelSize > 0) {
            possibleValues = new Object[]{UNIT_M, UNIT_CM, UNIT_MM, UNIT_IN, UNIT_PIXELS};
        } else {
            possibleValues = new Object[]{UNIT_M, UNIT_CM, UNIT_MM, UNIT_IN};
        }
        Object selectedValue = JOptionPane.showInputDialog(this,
                msg, title,
                JOptionPane.INFORMATION_MESSAGE, null,
                possibleValues, possibleValues[0]);
        if (selectedValue == null) {
            return 0;  // user canceled
        }

        String unitStr = (String) selectedValue;
        if (unitStr.equals(UNIT_M)) {
            return 1;
        } else if (unitStr.equals(UNIT_CM)) {
            return 0.01;
        } else if (unitStr.equals(UNIT_MM)) {
            return 0.001;
        } else if (unitStr.equals(UNIT_IN)) {
            return 2.54 / 100.;
        } else if (unitStr.equals(UNIT_GEO)) {
            return -1;
        } else if (unitStr.equals(UNIT_PIXELS)) {
            return pixelSize;
        } else {
            return 0;
        }
    }

    /**
     * Returns the size of a pixel in a map image. Returns -1 if no map image
     * has been loaded.
     *
     * @param forOldMap
     * @return
     */
    private double getMapPixelSize(boolean forOldMap) {

        if (forOldMap) {
            if (manager.getOldMap() == null) {
                return -1;
            }
            return manager.getOldMap().getPixelSizeX();
        } else {
            if (manager.getNewMap() == null) {
                return -1;
            }
            return manager.getNewMap().getPixelSizeX();
        }
    }

    /**
     * Creates a file name that can be used to export data.
     *
     * @param fileExtension
     * @return
     */
    private String exportFileName(String fileExtension) {
        String fileName = getTitle();
        if (fileName == null || fileName.trim().length() == 0) {
            fileName = "Export";
        }
        return FileUtils.forceFileNameExtension(fileName, fileExtension);
    }

    private void importPoints(boolean forOldMap) {
        GeoSet dstGeoSet;
        GeoSet siblingGeoSet;
        String fileDialogTitle;
        String linkMessage;
        if (forOldMap) {
            dstGeoSet = this.manager.getOldPointsGeoSet();
            siblingGeoSet = this.manager.getNewPointsGeoSet();
            fileDialogTitle = "Import Points for Old Map";
            linkMessage = "<html>Do you want to link the imported points with the"
                    + " points in the new map?<br>"
                    + "The first column will be used to identify pairs of points.</html>";
        } else {
            dstGeoSet = this.manager.getNewPointsGeoSet();
            siblingGeoSet = this.manager.getOldPointsGeoSet();
            fileDialogTitle = "Import Points for New Map";
            linkMessage = "<html>Do you want to link the imported points with the"
                    + " points in the old map?<br>"
                    + "The first column will be used to identify pairs of points.</html>";
        }

        // ask user for file
        String importFilePath = FileUtils.askFile(this, fileDialogTitle, true);
        if (importFilePath == null) {
            return; // user canceled
        }

        // ask user for unit of points
        boolean askForLonLat = !forOldMap && manager.isUsingOpenStreetMap();
        final double pixelSize = getMapPixelSize(forOldMap);
        double scale = this.askCoordinateUnit(askForLonLat, pixelSize);
        if (scale == 0) {
            return; // user canceled
        }

        // import the points
        try {
            this.manager.importPointsFromFile(importFilePath, dstGeoSet, scale);
        } catch (Exception exc) {
            this.pointImportError(exc);
            return;
        }

        // ask user whether the points should be linked with existing points
        // in the sibling GeoSet
        if (siblingGeoSet.getNumberOfChildren() > 0) {
            Object[] options = {"Link Points", "Don't Link Points"};
            int res = JOptionPane.showOptionDialog(this, linkMessage, "Link Points",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, options, options[0]);
            if (res == 0) {
                this.manager.getLinkManager().linkPointsByName();
            }
        }

        // make sure points are visible and adjust scale and center of map to show all
        showPoints();
        showAll();

        this.addUndo("Import Points");
    }

    private void importNewPointsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importNewPointsMenuItemActionPerformed
        this.importPoints(false);
    }//GEN-LAST:event_importNewPointsMenuItemActionPerformed

    private void importOldPointsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importOldPointsMenuItemActionPerformed
        this.importPoints(true);
    }//GEN-LAST:event_importOldPointsMenuItemActionPerformed

    private void importLinkedPointsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importLinkedPointsMenuItemActionPerformed
        String importFilePath = FileUtils.askFile(this, "Import Points", true);
        if (importFilePath == null) {
            return; // user canceled
        }
        // import the points
        try {
            manager.importLinksFromFile(importFilePath, this);
        } catch (Exception exc) {
            pointImportError(exc);
        }

        // make sure points are visible and adjust scale and center of map to show all
        showPoints();
        this.showAll();

        this.addUndo("Import Points");
    }//GEN-LAST:event_importLinkedPointsMenuItemActionPerformed

    private void isolinesRotationVisibleCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_isolinesRotationVisibleCheckBoxActionPerformed
        this.readIsolinesFromGUIAndRepaint();
    }//GEN-LAST:event_isolinesRotationVisibleCheckBoxActionPerformed

    private void isolinesScaleVisibleCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_isolinesScaleVisibleCheckBoxActionPerformed
        this.readIsolinesFromGUIAndRepaint();
    }//GEN-LAST:event_isolinesScaleVisibleCheckBoxActionPerformed

    private void zoomOutMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomOutMenuItemActionPerformed
        this.oldMapComponent.zoomOut();
        this.newMapComponent.zoomOut();
    }//GEN-LAST:event_zoomOutMenuItemActionPerformed

    private void zoomInMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomInMenuItemActionPerformed
        this.oldMapComponent.zoomIn();
        this.newMapComponent.zoomIn();
    }//GEN-LAST:event_zoomInMenuItemActionPerformed

    private void exportNewWMFMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportNewWMFMenuItemActionPerformed
        this.export("WMF", false);
    }//GEN-LAST:event_exportNewWMFMenuItemActionPerformed

    private void exportOldWMFMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportOldWMFMenuItemActionPerformed
        this.export("WMF", true);
    }//GEN-LAST:event_exportOldWMFMenuItemActionPerformed

    private void drawingImportButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_drawingImportButtonActionPerformed
        // ask user for destination: old map or new map
        Object[] options = {"Cancel", "New Map", "Old Map"};
        int res = JOptionPane.showOptionDialog(this, "Import to the old map or to the new map?",
                "Import Destination",
                JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, null);
        if (res == JOptionPane.CLOSED_OPTION || res == 0) {
            return; // user canceled
        }
        final boolean forOldMap = (res == 2);

        // ask user for file
        String file = ika.utils.FileUtils.askFile(this, "Import Drawing", true);
        if (file == null) {
            return;
        }

        GeoSet geoSet;
        // try importing with Ungenerate importer
        try {
            geoSet = ika.geoimport.UngenerateImporter.read(file);
        } catch (Exception exc) {
            // try importing as ASCII point list
            geoSet = ika.geoimport.PointImporter.importPoints(file, this);
        }

        if (geoSet == null) {
            return;
        }

        this.manager.addDrawing(geoSet, forOldMap);
        this.oldMapComponent.showAll();
        this.oldMapComponent.repaint();
        this.newMapComponent.showAll();
        this.newMapComponent.repaint();

    }//GEN-LAST:event_drawingImportButtonActionPerformed

    private void exportNewPNGMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportNewPNGMenuItemActionPerformed
        exportToRasterImage(false, "png");
    }//GEN-LAST:event_exportNewPNGMenuItemActionPerformed

    private void exportOldPNGMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportOldPNGMenuItemActionPerformed
        exportToRasterImage(true, "png");
    }//GEN-LAST:event_exportOldPNGMenuItemActionPerformed

    private void exportNewJPEGMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportNewJPEGMenuItemActionPerformed
        exportToRasterImage(false, "jpg");
    }//GEN-LAST:event_exportNewJPEGMenuItemActionPerformed

    private void exportOldJPEGMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportOldJPEGMenuItemActionPerformed
        exportToRasterImage(true, "jpg");
    }//GEN-LAST:event_exportOldJPEGMenuItemActionPerformed

    private void export(String format, boolean oldMap) {
        try {
            String classPath = "ika.geoexport." + format + "Exporter";
            Class exporterClass = Class.forName(classPath);

            double mapScale = oldMap ? oldMapComponent.getScaleFactor()
                    : newMapComponent.getScaleFactor();
            double f = oldMap ? OLD_MAP_PATH_FLATNESS : NEW_MAP_PATH_FLATNESS;

            Class[] argsClass = new Class[]{double.class};
            Constructor constructor = exporterClass.getConstructor(argsClass);
            Object[] args = new Object[]{mapScale};
            GeoSetExporter exporter = (GeoSetExporter) constructor.newInstance(args);
            exporter.setPathFlatness(f);

            String msg = "Save " + format + " File";
            String ext = exporter.getFileExtension();
            String fileName = exportFileName(ext);
            String path = ika.utils.FileUtils.askFile(this, msg, fileName, false, ext, null);
            if (path == null) {
                return; // user canceled
            }
            manager.export(exporter, path, oldMap);

        } catch (Exception e) {
            showExportError(e);
        }
    }

    private void export(GeoSetExporter exporter,
            String filePath, boolean oldMap) {
        try {
            double f = oldMap ? OLD_MAP_PATH_FLATNESS : NEW_MAP_PATH_FLATNESS;
            exporter.setPathFlatness(f);
            this.manager.export(exporter, filePath, oldMap);
        } catch (Exception e) {
            String msg = "The data could not be exported.\n" + e.toString();
            String title = "Export Error";
            javax.swing.JOptionPane.showMessageDialog(this, msg, title,
                    javax.swing.JOptionPane.ERROR_MESSAGE, null);
        }
    }

    private void exportToRasterImage(boolean oldMap, String format) {
        // ask the user for a file to store the image
        String fileName = exportFileName(format);
        String path = FileUtils.askFile(this, "Save Raster Image", fileName, false, format, null);
        if (path == null) {
            return; // user canceled
        }
        // ask the user for the number of horizontal pixels of the new image.
        String imageWidthStr = (String) JOptionPane.showInputDialog(this,
                "Please enter the width of the image in pixel:",
                "Export To Raster Image",
                JOptionPane.PLAIN_MESSAGE, null, null, "5000");
        if (imageWidthStr == null) {
            return; // user canceled
        }
        int imageWidth;
        try {
            imageWidth = Integer.parseInt(imageWidthStr);
        } catch (NumberFormatException exc) {
            JOptionPane.showMessageDialog(this, "Invalid number of pixels.",
                    "Invalid Number", JOptionPane.ERROR_MESSAGE, null);
            return;
        }

        // write the map to a raster image image
        try {
            final double mapScale = oldMap
                    ? this.oldMapComponent.getScaleFactor()
                    : this.newMapComponent.getScaleFactor();
            RasterImageExporter exporter = new RasterImageExporter(mapScale);
            exporter.setImageWidth(imageWidth);
            exporter.setFormat(format);
            this.export(exporter, path, oldMap);
        } catch (NumberFormatException exc) {
            String msg = "An error occured while exporting to a raster image.";
            String title = "Raster Image Export Error";
            ErrorDialog.showErrorDialog(msg, title, exc, this);
        }
    }

    private void penToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_penToggleButtonActionPerformed
        this.enablePenMapTool();
    }//GEN-LAST:event_penToggleButtonActionPerformed

    private void showAllMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showAllMenuItemActionPerformed
        showAll();
    }//GEN-LAST:event_showAllMenuItemActionPerformed

    private void drawingVisibleCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_drawingVisibleCheckBoxActionPerformed
        readDrawingFromGUIAndRepaint();
    }//GEN-LAST:event_drawingVisibleCheckBoxActionPerformed

    private void circleScaleSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_circleScaleSliderStateChanged
        this.readCirclesFromGUI();
        this.clearTemporaryGUI();
    }//GEN-LAST:event_circleScaleSliderStateChanged

    private void circlesVisibleCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_circlesVisibleCheckBoxActionPerformed
        this.readCirclesFromGUIAndRepaint();
    }//GEN-LAST:event_circlesVisibleCheckBoxActionPerformed

    private void huberEstimatorCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_huberEstimatorCheckBoxMenuItemActionPerformed
        this.readGUI();
        this.clearTemporaryGUI();
    }//GEN-LAST:event_huberEstimatorCheckBoxMenuItemActionPerformed

    private void vEstimatorCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_vEstimatorCheckBoxMenuItemActionPerformed
        this.readGUI();
        this.clearTemporaryGUI();
    }//GEN-LAST:event_vEstimatorCheckBoxMenuItemActionPerformed

    private void hampelEstimatorCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_hampelEstimatorCheckBoxMenuItemActionPerformed
        this.readGUI();
        this.clearTemporaryGUI();
    }//GEN-LAST:event_hampelEstimatorCheckBoxMenuItemActionPerformed

    private void affine6CheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_affine6CheckBoxMenuItemActionPerformed
        this.readGUI();
        this.clearTemporaryGUI();
    }//GEN-LAST:event_affine6CheckBoxMenuItemActionPerformed

    private void affine5CheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_affine5CheckBoxMenuItemActionPerformed
        this.readGUI();
        this.clearTemporaryGUI();
    }//GEN-LAST:event_affine5CheckBoxMenuItemActionPerformed

    private void helmertCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_helmertCheckBoxMenuItemActionPerformed
        this.readGUI();
        this.clearTemporaryGUI();
    }//GEN-LAST:event_helmertCheckBoxMenuItemActionPerformed

    private void errorVectorsShowComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_errorVectorsShowComboBoxItemStateChanged
        readErrorVectorFromGUIAndRepaint();
    }//GEN-LAST:event_errorVectorsShowComboBoxItemStateChanged

    private void showMapCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showMapCheckBoxMenuItemActionPerformed
        final boolean showOld = this.showOldCheckBoxMenuItem.isSelected();
        final boolean showNew = this.showNewCheckBoxMenuItem.isSelected();
        this.manager.setImagesVisible(showOld, showNew);
        this.oldMapComponent.repaint();
        this.newMapComponent.repaint();
}//GEN-LAST:event_showMapCheckBoxMenuItemActionPerformed

    public void openProject(String filePath) {
        ObjectInputStream objectStream = null;
        try {
            // read data from project file
            FileInputStream fileStream = new FileInputStream(filePath);
            BufferedInputStream bufferedStream = new BufferedInputStream(fileStream);
            java.util.zip.GZIPInputStream zip
                    = new java.util.zip.GZIPInputStream(bufferedStream);
            objectStream = new ObjectInputStream(zip);
            Manager newManager = (Manager) objectStream.readObject();

            // successfully read new data. Create new window.
            final File file = new File(filePath);
            String fileName = FileUtils.cutFileExtension(file.getName());
            MainWindow w = new MainWindow(fileName, true);
            w.resetManager(newManager);
            w.addUndo("Open File");
            w.filePath = filePath;
            w.manager.initOSM(w.newMapComponent);
            w.showPoints();
            w.showImages();
            w.writeGUI();
            w.setVisible(true);
            w.cleanDirty();

            // add document to the File > Open Recent Project menu
            rdm.addDocument(new File(filePath), null);

            MainWindow.updateAllMenusOfAllWindows();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "The file could not be opened.",
                    "File Error", JOptionPane.ERROR_MESSAGE, null);
            Logger.getLogger(MainWindow.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (objectStream != null) {
                try {
                    objectStream.close();
                } catch (IOException ex) {
                    Logger.getLogger(MainWindow.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    protected boolean closeProject() {
        if (this.dirty) {

            // document has been edited. Ask user whether to save or not.
            String msg = "Do you want to save changes to this project before closing?\n"
                    + "If you don't save, your changes to \"" + this.getTitle() + "\" will be lost.";
            String title = "Save Before Closing the Project?";
            Object[] options = {"Don't Save", "Cancel", "Save"};
            int res = JOptionPane.showOptionDialog(this, msg, title,
                    JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, options, options[2]);

            switch (res) {
                case 0:
                    /* document has been edited and user does not want to save it*/
                    break;

                case 1:/* document has been edited and user canceled */
                case JOptionPane.CLOSED_OPTION:
                    return false;

                case 2:
                    /* document has been edited and user wants to save it */
                    if (this.filePath == null || !new File(this.filePath).exists()) {
                        String ext = ApplicationInfo.getDocumentExtension();
                        String fileName = exportFileName(ext);
                        filePath = FileUtils.askFile(this, "Save Project", fileName, false, ext, null);
                        if (filePath == null) {
                            return false; // user canceled
                        }
                    }
                    if (!this.saveProject(this.filePath)) {
                        return false;
                    }
                    break;
            }
        }

        MainWindow.windows.remove(this);
        MainWindow.updateAllMenusOfAllWindows();
        this.setVisible(false);
        this.dispose();
        return true;
    }

    static public MainWindow newProject() {
        MainWindow w = new MainWindow("Untitled " + MainWindow.projectCounter++, true);
        w.setVisible(true);
        MainWindow.updateAllMenusOfAllWindows();
        return w;
    }

    private boolean saveProject(String path) {
        try {
            byte[] serializedManager = manager.serializeManager();
            FileOutputStream fileStream = new FileOutputStream(path);
            fileStream.write(serializedManager);
        } catch (Exception e) {
            Logger.getLogger(MainWindow.class.getName()).log(Level.SEVERE, null, e);
            JOptionPane.showMessageDialog(this, "An error occured. "
                    + "The file could not be saved.\n"
                    + "Please try saving the file to another location.",
                    "File Error", JOptionPane.ERROR_MESSAGE, null);
            return false;
        }
        return true;
    }

    private void openProjectMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openProjectMenuItemActionPerformed
        String extension = ika.mapanalyst.ApplicationInfo.getDocumentExtension();
        String newFilePath = FileUtils.askFile(this, "Open Project", null, true,
                null, new FileNameExtensionFilter("MapAnalyst", extension));
        if (newFilePath != null) {
            openProject(newFilePath);
        }
    }//GEN-LAST:event_openProjectMenuItemActionPerformed

    private void placePointMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_placePointMenuItemActionPerformed
        boolean newPointPlaced = this.manager.placePointFromList(this);
        // make new point visible on map
        showAllInNewMap();
        if (newPointPlaced) {
            this.addUndo("Place Point");
        }
    }//GEN-LAST:event_placePointMenuItemActionPerformed

    private void errorVectorsOutliersColorButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_errorVectorsOutliersColorButtonActionPerformed
        readErrorVectorFromGUIAndRepaint();
    }//GEN-LAST:event_errorVectorsOutliersColorButtonActionPerformed

    private void errorVectorsOutliersCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_errorVectorsOutliersCheckBoxActionPerformed
        readErrorVectorFromGUIAndRepaint();
    }//GEN-LAST:event_errorVectorsOutliersCheckBoxActionPerformed

    private void errorVectorsVisibleCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_errorVectorsVisibleCheckBoxActionPerformed
        readErrorVectorFromGUIAndRepaint();
    }//GEN-LAST:event_errorVectorsVisibleCheckBoxActionPerformed

    private void distortionGridVisibleCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_distortionGridVisibleCheckBoxActionPerformed
        this.readDistortionGridFromGUI();
        this.oldMapComponent.repaint();
        this.newMapComponent.repaint();
    }//GEN-LAST:event_distortionGridVisibleCheckBoxActionPerformed

    private void showPointsCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showPointsCheckBoxMenuItemActionPerformed
        final boolean visible = this.showPointsCheckBoxMenuItem.isSelected();
        this.manager.setPointsVisible(visible);
        this.oldMapComponent.repaint();
        this.newMapComponent.repaint();
    }//GEN-LAST:event_showPointsCheckBoxMenuItemActionPerformed

    private void rectangularSelectionToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rectangularSelectionToggleButtonActionPerformed
        this.enableSelectionMapTool(true);
    }//GEN-LAST:event_rectangularSelectionToggleButtonActionPerformed

    private void setPointToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setPointToggleButtonActionPerformed
        this.enablePointSetterMapTool(true);
    }//GEN-LAST:event_setPointToggleButtonActionPerformed

    private void panPointsToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_panPointsToggleButtonActionPerformed
        this.enableMoverMapTool(true);
    }//GEN-LAST:event_panPointsToggleButtonActionPerformed

    private void showAllButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showAllButtonActionPerformed
        this.showAll();
    }//GEN-LAST:event_showAllButtonActionPerformed

    private void distortionGridSmoothnessSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_distortionGridSmoothnessSliderStateChanged
        if (distortionGridSmoothnessSlider.getValueIsAdjusting() == false) {
            readDistortionGridFromGUI();
            clearTemporaryGUI();
        }
    }//GEN-LAST:event_distortionGridSmoothnessSliderStateChanged

    private void infoMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_infoMenuItemActionPerformed
        ika.gui.ProgramInfo.show(this);
    }//GEN-LAST:event_infoMenuItemActionPerformed

    private void unlinkedPointsColorMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_unlinkedPointsColorMenuItemActionPerformed
        Color oldColor = manager.getLinkManager().getLinkedPointColor();
        Color newColor = JColorChooser.showDialog(this,
                "Color of Unlinked Points", oldColor);
        if (newColor != null) {
            manager.getLinkManager().setUnlinkedPointColor(newColor);
            showPoints();
        }
    }//GEN-LAST:event_unlinkedPointsColorMenuItemActionPerformed

    private void computeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_computeMenuItemActionPerformed
        compute();
    }//GEN-LAST:event_computeMenuItemActionPerformed

    private void findLinkMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_findLinkMenuItemActionPerformed
        // ask the user for the name of the link that will be searched
        String message = "Find Link:";
        String title = "Find Link by Name";
        String linkName = (String) JOptionPane.showInputDialog(this, message, title,
                JOptionPane.PLAIN_MESSAGE, null, null, null);
        if (linkName == null) {
            return; // user canceled
        }
        // search and select the link
        Link link = manager.getLinkManager().selectLink(linkName);

        // inform the user if the link could not be found
        if (link == null) {
            String msg = "No Link Found With the Name " + linkName;
            JOptionPane.showMessageDialog(this, msg,
                    "No Link Found", JOptionPane.ERROR_MESSAGE, null);
        } else {
            showPoints();
        }

    }//GEN-LAST:event_findLinkMenuItemActionPerformed

    private void showReportMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showReportMenuItemActionPerformed
        if (this.transformationReportWin == null) {
            this.updateTransformationReportWindow(false);
        }
        this.transformationReportWin.show();
    }//GEN-LAST:event_showReportMenuItemActionPerformed

    private void linkToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_linkToggleButtonActionPerformed
        // check if there is a selected link
        LinkManager linkManager = this.manager.getLinkManager();

        Link link = linkManager.getSingleSelectedLink();
        if (link != null) {
            linkManager.deleteSelectedLinks();

            // update GUI
            this.enableLinkingGUIForUnlinkedPoints(link.getPtOld(), link.getPtNew());
            this.addUndo("Unlink Points");
        } else {
            // get the selected point in the old map and see if it has a name.
            GeoPoint newPt = linkManager.getSingleSelectedGeoPoint(false);
            String linkName = "link";
            if (newPt != null && newPt.getName() != null) {
                linkName = newPt.getName();
            }

            // ask the user for a name for the new link
            linkName = linkManager.generateUniqueName(linkName);
            linkName = this.askLinkName(linkName);
            if (linkName != null) {

                // make sure the name contains no coma, which is used to
                // separate values when exporting to a text file.
                if (linkName.indexOf(',') != -1) {
                    String msg = "A name cannot contain any comma.\n"
                            + "Please choose another name.";
                    String title = "Comma Not Posible In Name";
                    javax.swing.JOptionPane.showMessageDialog(this, msg, title,
                            javax.swing.JOptionPane.ERROR_MESSAGE, null);
                } else {
                    // create the new link
                    Link newLink = linkManager.createLinkFromSelectedPoints(linkName);
                    // update GUI
                    this.enableLinkingGUIForSelectedLink(newLink);
                    this.addUndo("Link Points");
                }
            }
        }
        this.manager.clearGraphics();
        this.updatePointCoordinatesWindow(true);
    }//GEN-LAST:event_linkToggleButtonActionPerformed

    private void linkedPointsColorMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_linkedPointsColorMenuItemActionPerformed
        Color oldColor = manager.getLinkManager().getLinkedPointColor();
        Color newColor = JColorChooser.showDialog(this,
                "Color of Linked Points", oldColor);
        if (newColor != null) {
            showPoints();
            manager.getLinkManager().setLinkedPointColor(newColor);
        }
    }//GEN-LAST:event_linkedPointsColorMenuItemActionPerformed

    private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
        if (this.closeProject()) {
            System.exit(0);
        }
    }//GEN-LAST:event_exitMenuItemActionPerformed

    private void deselectPointsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deselectPointsMenuItemActionPerformed
        this.manager.deselectPoints();
        showPoints();
        this.addUndo("Deselect All Points");
    }//GEN-LAST:event_deselectPointsMenuItemActionPerformed

    private void selectPointsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectPointsMenuItemActionPerformed
        this.manager.selectPoints();
        showPoints();
        this.addUndo("Select All Points");
    }//GEN-LAST:event_selectPointsMenuItemActionPerformed

    private void noLinkedPointsErrorMessage() {
        String msg = "There are no linked points.\n"
                + "Please set corresponding points in the old and the new map\n"
                + "and link them using the \"Link Points\" button.";
        String title = "No Linked Points";
        javax.swing.JOptionPane.showMessageDialog(this, msg, title,
                javax.swing.JOptionPane.ERROR_MESSAGE, null);
    }

    private void notEnoughLinkedPointsErrorMessage() {
        final int minNbrLinks = this.manager.getLinkManager().
                getMinNbrOfLinkedPointsForComputation();

        String msg = "There are not enough linked points.\n"
                + "At least " + minNbrLinks + " pairs of linked points "
                + "are needed.\n"
                + "Please set corresponding points in the old and the new map\n"
                + "and link them using the \"Link\" button.";
        String title = "Not Enough Linked Points";
        javax.swing.JOptionPane.showMessageDialog(this, msg, title,
                javax.swing.JOptionPane.ERROR_MESSAGE, null);
    }

    private void showPointListMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showPointListMenuItemActionPerformed
        this.updatePointCoordinatesWindow(true); // this creates pointsDialog if necessary
        this.pointsDialog.setVisible(true);
    }//GEN-LAST:event_showPointListMenuItemActionPerformed

    private void exportNewDXFMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportNewDXFMenuItemActionPerformed
        this.export("DXF", false);
    }//GEN-LAST:event_exportNewDXFMenuItemActionPerformed

    private void exportOldDXFMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportOldDXFMenuItemActionPerformed
        this.export("DXF", true);
    }//GEN-LAST:event_exportOldDXFMenuItemActionPerformed

    private void exportNewUngenerateMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportNewUngenerateMenuItemActionPerformed
        this.export("Ungenerate", false);
    }//GEN-LAST:event_exportNewUngenerateMenuItemActionPerformed

    private void exportOldUngenerateMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportOldUngenerateMenuItemActionPerformed
        this.export("Ungenerate", true);
    }//GEN-LAST:event_exportOldUngenerateMenuItemActionPerformed

    private void exportNewSVGMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportNewSVGMenuItemActionPerformed
        String fileName = exportFileName("svg");
        String path = FileUtils.askFile(this, "Save SVG File", fileName, false, "svg", null);
        if (path == null) {
            return; // user canceled
        }
        Object scaleStr = JOptionPane.showInputDialog(this, "Scale:",
                "SVG Export", JOptionPane.PLAIN_MESSAGE, null,
                null, "100000");
        if (scaleStr == null) {
            return; // user canceled
        }
        final double mapScale = this.newMapComponent.getScaleFactor();
        SVGExporter exporter = new SVGExporter(mapScale);

        double scale;
        try {
            scale = Double.parseDouble((String) scaleStr);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "The scale is not valid. Please enter another scale.",
                    "Invalid Number", JOptionPane.ERROR_MESSAGE, null);
            return;
        }
        exporter.setScale(1000. / scale); // assume conversion from meter to millimeter
        this.export(exporter, path, false);
    }//GEN-LAST:event_exportNewSVGMenuItemActionPerformed

    private void distanceToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_distanceToggleButtonActionPerformed
        MeasureTool oldMeasureTool = new MeasureTool(oldMapComponent, manager);
        MeasureTool newMeasureTool = new MeasureTool(newMapComponent, manager);
        oldMeasureTool.addMeasureToolListener(coordinateInfoPanel);
        newMeasureTool.addMeasureToolListener(coordinateInfoPanel);
        oldMapComponent.setMapTool(oldMeasureTool);
        newMapComponent.setMapTool(newMeasureTool);
    }//GEN-LAST:event_distanceToggleButtonActionPerformed

    public String askLinkName(String oldName) {
        String message = "Please enter the name of the location:";
        String title = "Link Name";
        Object newName = JOptionPane.showInputDialog(this,
                message, title, JOptionPane.INFORMATION_MESSAGE, null,
                null, oldName);
        if (newName == null) {
            return null;
        }
        return (String) newName;
    }

    private void linkNameButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_linkNameButtonActionPerformed
        LinkManager linkManager = manager.getLinkManager();

        // search the only selected link
        Link link = linkManager.getSingleSelectedLink();
        if (link == null) {
            return;
        }

        // ask the user for a new name for the link
        String oldName = link.getName();
        String newName = askLinkName(oldName);
        if (newName == null || oldName.equals(newName)) {
            return;
        }

        if (newName.indexOf(',') != -1) {
            String msg = "A name cannot contain any comma.\n"
                    + "Please choose another name.";
            String title = "Comma Not Posible In Name";
            javax.swing.JOptionPane.showMessageDialog(this, msg, title,
                    javax.swing.JOptionPane.ERROR_MESSAGE, null);
            return;
        }

        // change the name
        linkManager.renameSelectedLink(newName);

        // update GUI with new name
        linkNameLabel.setText(link.getName());
        this.addUndo("Rename Link");
    }//GEN-LAST:event_linkNameButtonActionPerformed

    private void deleteMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteMenuItemActionPerformed

        // first remove selected links from LinkManager
        this.manager.getLinkManager().deleteSelectedLinks();

        // then remove points from Maps. This triggers a geoSetChanged event,
        // which will use the now updated LinkManager.
        this.oldMapComponent.removeSelectedGeoObjects();
        this.newMapComponent.removeSelectedGeoObjects();

        clearTemporaryGUI();

        this.addUndo("Delete");
    }//GEN-LAST:event_deleteMenuItemActionPerformed

    private void showErrorInNewMapCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showErrorInNewMapCheckBoxMenuItemActionPerformed
        if (!showErrorInNewMapCheckBoxMenuItem.isSelected()
                && !showErrorInOldMapCheckBoxMenuItem.isSelected()) {
            showErrorInNewMapCheckBoxMenuItem.setSelected(true);
            return;
        }

        this.readShowErrorInOldMapFromGUI();

        // disable "Custom Polygon" in grid extension menu if there has not been
        // a clip polygon defined.
        boolean hasClipPoly = this.manager.getDistortionGrid().hasNewClipPolygon();
        if (!hasClipPoly) {
            this.distortionGridExtensionComboBox.setSelectedIndex(0);
        }

        clearTemporaryGUI();
    }//GEN-LAST:event_showErrorInNewMapCheckBoxMenuItemActionPerformed

    private void exportOldSVGMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportOldSVGMenuItemActionPerformed
        this.export("SVG", true);
    }//GEN-LAST:event_exportOldSVGMenuItemActionPerformed

    private void showErrorInOldMapCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showErrorInOldMapCheckBoxMenuItemActionPerformed
        if (!showErrorInNewMapCheckBoxMenuItem.isSelected()
                && !showErrorInOldMapCheckBoxMenuItem.isSelected()) {
            showErrorInOldMapCheckBoxMenuItem.setSelected(true);
            return;
        }

        this.readShowErrorInOldMapFromGUI();

        // disable "Custom Polygon" in grid extension menu if there has not been
        // a clip polygon defined.
        boolean hasClipPoly = this.manager.getDistortionGrid().hasOldClipPolygon();
        if (!hasClipPoly) {
            this.distortionGridExtensionComboBox.setSelectedIndex(0);
        }

        clearTemporaryGUI();
    }//GEN-LAST:event_showErrorInOldMapCheckBoxMenuItemActionPerformed

    private void readErrorVectorFromGUIAndRepaint() {
        readErrorVectorFromGUI();
        this.oldMapComponent.repaint();
        this.newMapComponent.repaint();
    }

    private void readErrorVectorFromGUI() {
        if (manager == null || updatingGUI) {
            return;
        }

        ErrorVectors errorVectors = manager.getErrorVectors();

        // visibility
        boolean visible = errorVectorsVisibleCheckBox.isSelected();
        errorVectors.setVisible(visible);

        // show circles or vectors
        final int index = errorVectorsShowComboBox.getSelectedIndex();
        final boolean showVectors = (index == 0 || index == 2);
        final boolean showCircles = (index == 1 || index == 2);
        errorVectors.setShowVectors(showVectors);
        errorVectors.setShowCircles(showCircles);

        // outliers
        final boolean markOutliers = this.errorVectorsOutliersCheckBox.isSelected();
        final Color outliersColor = this.errorVectorsOutliersColorButton.getColor();
        errorVectors.setMarkOutliers(markOutliers);
        errorVectors.setOutliersColor(outliersColor);

        // sets the scale of the error vectors. Default is the scale = 1
        double scale;
        try {
            scale = errorVectorsScaleNumberField.getNumber();
        } catch (Exception e) { // use 1 if user entered invalid number
            scale = 1;
            this.errorVectorsScaleNumberField.setNumber(scale);
        }
        errorVectors.setVectorScale(scale);

        // appearance of lines
        VectorSymbol symbol = errorVectorsLineAppearancePanel.getVectorSymbol();
        boolean fillCircles = this.errorVectorsFillCirclesCheckBox.isSelected();
        Color fillColor = this.errorVectorsFillColorButton.getColor();
        symbol.setFilled(fillCircles);
        symbol.setFillColor(fillColor);
        errorVectors.setVectorSymbol(symbol);
    }

    private void writeErrorVectorToGUI() {
        try {
            updatingGUI = true;
            if (manager == null) {
                return;
            }

            ErrorVectors errorVectors = manager.getErrorVectors();

            // visibility
            errorVectorsVisibleCheckBox.setSelected(errorVectors.isVisible());

            // show circles or vectors
            final boolean showVectors = errorVectors.isShowVectors();
            final boolean showCircles = errorVectors.isShowCircles();
            int index = 0;
            if (showCircles) {
                index = 1;
            }
            if (showVectors && showCircles) {
                index = 2;
            }
            errorVectorsShowComboBox.setSelectedIndex(index);

            // vector scale
            double scale = errorVectors.getVectorScale();
            errorVectorsScaleNumberField.setNumber(scale);

            // outliers
            this.errorVectorsOutliersCheckBox.setSelected(errorVectors.isMarkOutliers());
            this.errorVectorsOutliersColorButton.setColor(errorVectors.getOutliersColor());

            // appearance of lines
            VectorSymbol symbol = errorVectors.getVectorSymbol();
            this.errorVectorsFillCirclesCheckBox.setSelected(symbol.isFilled());
            this.errorVectorsFillColorButton.setColor(symbol.getFillColor());
            errorVectorsLineAppearancePanel.setVectorSymbol(symbol);
        } finally {
            updatingGUI = false;
        }
    }

    private void readDistortionFromGUIAndRepaint() {
        readDistortionGridFromGUI();
        this.oldMapComponent.repaint();
        this.newMapComponent.repaint();
    }

    private void readDistortionGridFromGUI() {
        if (manager == null || updatingGUI) {
            return;
        }

        DistortionGrid distGrid = manager.getDistortionGrid();

        // mesh size of the distortion grid
        double meshSize;
        try {
            meshSize = distortionGridMeshSizeNumberField.getNumber();

        } catch (Exception e) {
            // reset to default value if the user entered an invalid number
            meshSize = 5000.0;
            this.distortionGridMeshSizeNumberField.setNumber(meshSize);
        }
        distGrid.setMeshSize(meshSize);
        if (gridUnitComboBox.getSelectedIndex() == 0) {
            distGrid.setMeshUnit(DistortionGrid.Unit.METERS);
        } else {
            distGrid.setMeshUnit(DistortionGrid.Unit.DEGREES);
        }

        // smoothness
        double smoothness = this.distortionGridSmoothnessSlider.getValue() / 100.;
        distGrid.setSmoothness(smoothness);

        float alpha = distortionGridUncertaintyAlphaSlider.getValue() / 100.f;
        // there is not much visible change for alpha > 0.5, so just set alpha to 1
        if (distortionGridUncertaintyAlphaSlider.getValue() == distortionGridUncertaintyAlphaSlider.getMaximum()) {
            alpha = 1f;
        }
        distGrid.setUncertaintyAlpha(alpha);

        double quantile = distortionGridUncertaintyQuantileSlider.getValue() / 100d;
        distGrid.setUncertaintyQuantile(quantile);

        // clip with hull
        int clipWithHull = this.distortionGridExtensionComboBox.getSelectedIndex();
        distGrid.setClipWithHull(clipWithHull);

        // appearance of lines
        VectorSymbol symbol = distortionGridLineAppearancePanel.getVectorSymbol();
        distGrid.setVectorSymbol(symbol);

        // visiblity
        boolean visible = distortionGridVisibleCheckBox.isSelected();
        distGrid.setVisible(visible);

        // label size
        int labelSize = 0;
        switch (this.distortionGridLabelComboBox.getSelectedIndex()) {
            case 1:
                labelSize = 10;
                break;
            case 2:
                labelSize = 12;
                break;
            case 3:
                labelSize = 15;
                break;
        }
        distGrid.setLabelSize(labelSize);

        // label sequence. Read value from the menu. Menu contains number,
        // convert it to an int.
        String labelSequence = this.distortionGridLabelSequenceComboBox.getSelectedItem().toString();
        distGrid.setLabelSequence(Integer.parseInt(labelSequence));

        // exaggeration
        double exaggeration;
        try {
            distortionGridExaggerationFormattedTextField.commitEdit();
            Object v = distortionGridExaggerationFormattedTextField.getValue();
            exaggeration = ((Number) v).doubleValue();
            if (exaggeration <= 0) {
                throw new IllegalArgumentException();
            }

        } catch (Exception e) {
            // reset to default value if the user entered an invalid number
            exaggeration = 1.0;
            this.distortionGridExaggerationFormattedTextField.setValue(new Double(1));
        }
        distGrid.setExaggeration(exaggeration);

        // offset X
        double offsetX;
        try {
            distortionGridOffsetXFormattedTextField.commitEdit();
            Object dx = distortionGridOffsetXFormattedTextField.getValue();
            offsetX = ((Number) dx).doubleValue();
        } catch (Exception e) {
            // reset to default value if the user entered an invalid number
            offsetX = 0.0;
            this.distortionGridOffsetXFormattedTextField.setValue(new Double(1));
        }
        distGrid.setOffsetX(offsetX);

        // offset Y
        double offsetY;
        try {
            distortionGridOffsetYFormattedTextField.commitEdit();
            Object dy = distortionGridOffsetYFormattedTextField.getValue();
            offsetY = ((Number) dy).doubleValue();
        } catch (Exception e) {
            // reset to default value if the user entered an invalid number
            offsetY = 0.0;
            this.distortionGridOffsetYFormattedTextField.setValue(new Double(1));
        }
        distGrid.setOffsetY(offsetY);

        // show undistorted grid
        distGrid.setShowUndistorted(distortionGridShowUndistortedCheckBox.isSelected());
    }

    private void writeDistortionGridToGUI() {
        try {
            updatingGUI = true;
            if (manager == null) {
                return;
            }

            DistortionGrid distGrid = this.manager.getDistortionGrid();

            // visibility
            this.distortionGridVisibleCheckBox.setSelected(distGrid.isVisible());

            // mesh size
            double meshSize = distGrid.getMeshSize();
            this.distortionGridMeshSizeNumberField.setNumber(meshSize);

            // mesh size units
            int selID = distGrid.getMeshUnit() == DistortionGrid.Unit.METERS ? 0 : 1;
            this.gridUnitComboBox.setSelectedIndex(selID);

            // smoothness
            double smoothness = distGrid.getSmoothness();
            this.distortionGridSmoothnessSlider.setValue((int) (smoothness * 100.));

            // uncertainty alpha
            float alpha = distGrid.getUncertaintyAlpha();
            distortionGridUncertaintyAlphaSlider.setValue((int) (alpha * 100f));

            // quantile for uncertainty reference
            double quantile = distGrid.getUncertaintyQuantile();
            distortionGridUncertaintyQuantileSlider.setValue((int) (quantile * 100));

            // convex hull
            int clipWithHull = distGrid.getClipWithHull();
            this.distortionGridExtensionComboBox.setSelectedIndex(clipWithHull);

            // appearance of lines
            VectorSymbol symbol = distGrid.getVectorSymbol();
            this.distortionGridLineAppearancePanel.setVectorSymbol(symbol);

            // label size
            final int labelSize = distGrid.getLabelSize();
            int labelSizeComboBoxItem = 0;
            switch (labelSize) {
                case 10:
                    labelSizeComboBoxItem = 1;
                    break;
                case 12:
                    labelSizeComboBoxItem = 2;
                    break;
                case 15:
                    labelSizeComboBoxItem = 3;
                    break;
            }
            this.distortionGridLabelComboBox.setSelectedIndex(labelSizeComboBoxItem);

            // label sequence.
            String labelSeq = Integer.toString(distGrid.getLabelSequence());
            int nbrItems = this.distortionGridLabelSequenceComboBox.getItemCount();
            for (int i = 0; i < nbrItems; i++) {
                String found = this.distortionGridLabelSequenceComboBox.getItemAt(i).toString();
                if (labelSeq.equals(found)) {
                    this.distortionGridLabelSequenceComboBox.setSelectedIndex(i);
                    break;
                }
            }

            // exaggeration
            double v = distGrid.getExaggeration();
            this.distortionGridExaggerationFormattedTextField.setValue(v);

            // offsets
            double dx = distGrid.getOffsetX();
            this.distortionGridOffsetXFormattedTextField.setValue(dx);
            double dy = distGrid.getOffsetY();
            this.distortionGridOffsetYFormattedTextField.setValue(dy);

            // show undistorted grid
            this.distortionGridShowUndistortedCheckBox.setSelected(distGrid.isShowUndistorted());
        } finally {
            updatingGUI = true;
        }
    }

    private void readCirclesFromGUIAndRepaint() {
        readCirclesFromGUI();
        this.oldMapComponent.repaint();
        this.newMapComponent.repaint();
    }

    private void readCirclesFromGUI() {
        if (manager == null || updatingGUI) {
            return;
        }

        MekenkampCircles circles = manager.getMekenkampCircles();

        // visibility
        boolean visible = circlesVisibleCheckBox.isSelected();
        circles.setVisible(visible);

        // scale factor for circles
        final double scale = this.circleScaleSlider.getValue() / 100000.;
        circles.setCircleScale(scale);

        // appearance of lines
        VectorSymbol symbol = this.circlesLineAppearancePanel.getVectorSymbol();
        circles.setVectorSymbol(symbol);
    }

    private void writeCirclesToGUI() {
        try {
            updatingGUI = true;
            if (manager == null) {
                return;
            }

            MekenkampCircles circles = manager.getMekenkampCircles();

            // visibility
            this.circlesVisibleCheckBox.setSelected(circles.isVisible());

            // circle scale
            final int scale = (int) Math.round(circles.getCircleScale() * 10000.);
            this.circleScaleSlider.setValue(scale);

            // appearance of lines
            VectorSymbol symbol = circles.getVectorSymbol();
            this.circlesLineAppearancePanel.setVectorSymbol(symbol);
        } finally {
            updatingGUI = false;
        }
    }

    private void readDrawingFromGUIAndRepaint() {
        readDrawingFromGUI();
        this.oldMapComponent.repaint();
        this.newMapComponent.repaint();
    }

    private void readDrawingFromGUI() {
        if (manager == null || updatingGUI) {
            return;
        }

        GeometryTransformer geometryTransformer = manager.getGeometryTransformer();

        // visibility
        boolean visible = this.drawingVisibleCheckBox.isSelected();
        geometryTransformer.setVisible(visible);
    }

    private void writeDrawingToGUI() {
        try {
            updatingGUI = true;
            GeometryTransformer geometryTransformer = manager.getGeometryTransformer();

            // visibility
            this.drawingVisibleCheckBox.setSelected(geometryTransformer.isVisible());
        } finally {
            updatingGUI = false;
        }
    }

    private void readIsolinesFromGUIAndRepaint() {
        readIsolinesFromGUI();
        this.oldMapComponent.repaint();
        this.newMapComponent.repaint();
    }

    private boolean testIsolinesRadiusOfInfluence() {
        try {
            Isolines isolines = this.manager.getIsolines();
            double radius = this.isolinesRadiusNumberField.getNumber();

            // test if radius is in recommended range of possible values
            java.awt.geom.Rectangle2D ptsBounds
                    = this.manager.getLinkManager().getNewPointsGeoSet().getBounds2D();
            if (ptsBounds == null) {
                return true;    // no points in map, so any value is accepted
            }
            if (isolines.testRadiusOfInfluence(radius, ptsBounds)) {
                return true;
            }

            double[] range = isolines.getRecommendedRadiusOfInfluenceRange(ptsBounds);
            String msg = ""
                    + "The radius of influence for the isolines is outside\n"
                    + "the recommended range between "
                    + Math.round(range[0]) + " and " + Math.round(range[1]) + ".\n"
                    + "It is recommended that you enter a radius of influence\n"
                    + "within this range.\n";
            String title = "Isolines - Radius of Influika.mapanalystt of Range";
            Object[] options = {"Change Value", "Use " + radius};
            final int option = JOptionPane.showOptionDialog(this, msg, title,
                    JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, options, options[0]);
            if (option == 1) // user wants to keep entered value
            {
                return true;
            } else // user will retype a value
            {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void readIsolinesFromGUI() {
        if (manager == null || updatingGUI) {
            return;
        }

        Isolines isolines = this.manager.getIsolines();

        // visibility of scale and rotataion lines
        boolean visible = this.isolinesScaleVisibleCheckBox.isSelected();
        isolines.setShowScale(visible);
        visible = this.isolinesRotationVisibleCheckBox.isSelected();
        isolines.setShowRotation(visible);

        // interval distance for scale lines
        float interval;
        try {
            interval = (float) isolinesScaleIntervalNumberField.getNumber();

        } catch (Exception e) {
            // reset to default value if the user entered an invalid number
            interval = 10000.f;
            this.isolinesScaleIntervalNumberField.setNumber(interval);
        }
        isolines.setIsoscaleInterval(interval);

        // interval distance for rotation lines
        try {
            interval = (float) isolinesRotationIntervalNumberField.getNumber();
        } catch (Exception e) {
            // reset to default value if the user entered an invalid number
            interval = 10.f;
            this.isolinesRotationIntervalNumberField.setNumber(interval);
        }
        isolines.setIsorotationInterval(interval);

        // radius of influence
        double radius;
        try {
            radius = isolinesRadiusNumberField.getNumber();
        } catch (Exception e) {
            // reset to default value if the user entered an invalid number
            radius = 10000.;
            this.isolinesRadiusNumberField.setNumber(radius);
        }
        isolines.setRadiusOfInfluence(radius);

        // appearance of lines
        VectorSymbol symbol = this.isolinesScaleAppearancePanel.getVectorSymbol();
        isolines.setIsoscalesVectorSymbol(symbol);
        symbol = this.isolinesRotationAppearancePanel.getVectorSymbol();
        isolines.setIsorotationVectorSymbol(symbol);
    }

    private void writeIsolinesToGUI() {
        try {
            updatingGUI = true;

            if (manager == null) {
                return;
            }

            Isolines isolines = this.manager.getIsolines();

            // visibility
            this.isolinesScaleVisibleCheckBox.setSelected(isolines.isShowScale());
            this.isolinesRotationVisibleCheckBox.setSelected(isolines.isShowRotation());

            // isoscale interval
            float isoscalesInterval = isolines.getIsoscaleInterval();
            this.isolinesScaleIntervalNumberField.setNumber(isoscalesInterval);

            // isorotation interval
            float isorotationInterval = isolines.getIsorotationInterval();
            this.isolinesRotationIntervalNumberField.setNumber(isorotationInterval);

            // radius of influence
            double radius = isolines.getRadiusOfInfluence();
            this.isolinesRadiusNumberField.setNumber(radius);

            // appearance of isoscales
            VectorSymbol isoscalesSymbol = isolines.getIsoscalesVectorSymbol();
            this.isolinesScaleAppearancePanel.setVectorSymbol(isoscalesSymbol);

            // appearance of isorotations
            VectorSymbol isorotationsSymbol = isolines.getIsorotationVectorSymbol();
            this.isolinesRotationAppearancePanel.setVectorSymbol(isorotationsSymbol);
        } finally {
            updatingGUI = false;
        }
    }

    private void readTransformationFromGUI() {
        this.setHuberEstimatorParametersMenuItem.setEnabled(false);
        this.setVEstimatorParametersMenuItem.setEnabled(false);
        this.setHampelEstimatorParametersMenuItem.setEnabled(false);
        if (helmertCheckBoxMenuItem.isSelected()) {
            manager.setTransformation(new TransformationHelmert());
        } else if (affine5CheckBoxMenuItem.isSelected()) {
            manager.setTransformation(new TransformationAffine5());
        } else if (affine6CheckBoxMenuItem.isSelected()) {
            manager.setTransformation(new TransformationAffine6());
        } else {
            TransformationRobustHelmert oldToNewRobustHelmert = new TransformationRobustHelmert();
            TransformationRobustHelmert newToOldRobustHelmert = new TransformationRobustHelmert();
            manager.setTransformation(oldToNewRobustHelmert);

            if (hampelEstimatorCheckBoxMenuItem.isSelected()) {
                this.setHampelEstimatorParametersMenuItem.setEnabled(true);
                oldToNewRobustHelmert.setRobustEstimator(manager.getHampelEstimator());
                newToOldRobustHelmert.setRobustEstimator(manager.getHampelEstimator());
            } else if (vEstimatorCheckBoxMenuItem.isSelected()) {
                this.setVEstimatorParametersMenuItem.setEnabled(true);
                oldToNewRobustHelmert.setRobustEstimator(manager.getVEstimator());
                newToOldRobustHelmert.setRobustEstimator(manager.getVEstimator());
            } else if (huberEstimatorCheckBoxMenuItem.isSelected()) {
                this.setHuberEstimatorParametersMenuItem.setEnabled(true);
                oldToNewRobustHelmert.setRobustEstimator(manager.getHuberEstimator());
                newToOldRobustHelmert.setRobustEstimator(manager.getHuberEstimator());
            }
        }
    }

    private void writeTransformationToGUI() {
        Transformation trafo = manager.getTransformation();
        if (trafo instanceof TransformationHelmert) {
            helmertCheckBoxMenuItem.setSelected(true);
        } else if (trafo instanceof TransformationAffine5) {
            affine5CheckBoxMenuItem.setSelected(true);
        } else if (trafo instanceof TransformationAffine6) {
            affine6CheckBoxMenuItem.setSelected(true);
        } else if (trafo instanceof TransformationRobustHelmert) {
            TransformationRobustHelmert robustHelmert = (TransformationRobustHelmert) trafo;
            RobustEstimator estimator = robustHelmert.getRobustEstimator();
            if (estimator instanceof HampelEstimator) {
                hampelEstimatorCheckBoxMenuItem.setSelected(true);
            } else if (estimator instanceof HuberEstimator) {
                huberEstimatorCheckBoxMenuItem.setSelected(true);
            } else if (estimator instanceof VEstimator) {
                vEstimatorCheckBoxMenuItem.setSelected(true);
            }
        }
    }

    private void readShowErrorInOldMapFromGUI() {
        if (showErrorInOldMapCheckBoxMenuItem.isSelected()) {
            manager.setShowInOldMap(true);
        }
        if (showErrorInNewMapCheckBoxMenuItem.isSelected()) {
            manager.setShowInOldMap(false);
        }
    }

    private void writeShowErrorInOldMapToGUI() {
        if (manager.isShowInOldMap()) {
            showErrorInOldMapCheckBoxMenuItem.setSelected(true);
        } else {
            showErrorInNewMapCheckBoxMenuItem.setSelected(true);
        }
    }

    private void readGUI() {
        readErrorVectorFromGUI();
        readDistortionGridFromGUI();
        readCirclesFromGUI();
        readDrawingFromGUI();
        readTransformationFromGUI();
        readIsolinesFromGUI();
        readShowErrorInOldMapFromGUI();
    }

    private void writeGUI() {
        writeErrorVectorToGUI();
        writeDistortionGridToGUI();
        writeCirclesToGUI();
        writeDrawingToGUI();
        writeTransformationToGUI();
        writeIsolinesToGUI();
        writeShowErrorInOldMapToGUI();
        osmCopyrightLabel.setVisible(manager.isUsingOpenStreetMap());
    }

    public void udpateTransformationInfoGUI() {
        String str = "";
        String nl = System.getProperty("line.separator");
        Transformation transformation = manager.getTransformation();
        if (transformation == null || !manager.isTransformationInitialized()) {
            str = "Scale:\t-" + nl + "Rotation:\t-";
            this.transformationInfoTextArea.setText(str);
            return;
        }

        final boolean invert = this.manager.isShowInOldMap();
        final boolean showAdvancedTrafoInfo = true;
        if (showAdvancedTrafoInfo) {
            str += transformation.getShortReport(invert);
            str += System.getProperty("line.separator") + transformation.getName();
        } else {
            str += transformation.getSimpleShortReport(invert);
        }

        this.transformationInfoTextArea.setText(str);

        this.updateTransformationReportWindow(true);
    }

    private void clearTemporaryGUI() {
        this.transformationInfoTextArea.setText("Scale:\t-\nRotation:\t-");
        this.updateTransformationReportWindow(false);
        this.manager.clearGraphics();
        this.setDirty();
    }

    private void compute() {

        // test if there are enough linked points
        if (!this.manager.getLinkManager().hasEnoughLinkedPointsForComputation()) {
            this.notEnoughLinkedPointsErrorMessage();
            return;
        }

        try {
            // read the parameters needed for the computation from the GUI
            readGUI();

            // compute the visualizations
            this.manager.analyzeMap(
                    oldMapComponent.getCoordinateFormatter(),
                    newMapComponent.getCoordinateFormatter(),
                    this);

            // update GUI with information from the transformation
            udpateTransformationInfoGUI();

        } catch (Throwable exc) {
            Logger.getLogger(MainWindow.class.getName()).log(Level.SEVERE, null, exc);

            String errMsg = exc.getMessage() == null ? "Unknown Error" : "Error: ";

            // display error message
            if (exc.getMessage() != null && exc.getMessage().trim().length() > 0) {
                errMsg += exc.getMessage().trim();
            }
            String title = "An Error Occured";
            javax.swing.JOptionPane.showMessageDialog(this, errMsg, title,
                    javax.swing.JOptionPane.WARNING_MESSAGE, null);
        }
    }

    private void importRasterImage(boolean importToOldMap) {
        String title = importToOldMap ? "Import Old Map" : "Import New Map";
        String importFilePath = FileUtils.askFile(this, title, null, true, null,
                FileUtils.IMAGE_FILE_NAME_EXT_FILTER);
        if (importFilePath != null) {
            try {
                // make sure there is an image importer for the file format
                // before any pre-existing image is removed from the map.
                String fileExt = FileUtils.getFileExtension(importFilePath);
                if (!ika.utils.ImageUtils.canReadImageFile(fileExt)) {
                    throw new Exception("File format not supported.");
                }

                if (importToOldMap) {
                    manager.importOldRasterImage(importFilePath, this, oldMapComponent);
                } else {
                    manager.importNewRasterImage(importFilePath, this, newMapComponent);
                }

                this.setDirty();

            } catch (Exception e) {
                String msg = "The image could not be imported.\n";
                msg += "Please make sure the image is in JPEG, GIF, or PNG format.\n";
                msg += e.toString();
                JOptionPane.showMessageDialog(this, msg,
                        "Raster Image Import Error", JOptionPane.ERROR_MESSAGE, null);
            }
            this.showImages();
        }
    }

    private void showImages() {
        this.manager.setImagesVisible(true, true);
        this.oldMapComponent.repaint();
        this.newMapComponent.repaint();
        this.showOldCheckBoxMenuItem.setSelected(true);
        this.showNewCheckBoxMenuItem.setSelected(true);
    }

    private void setDirty() {
        if (!this.dirty) {
            this.dirty = true;
            this.saveProjectMenuItem.setEnabled(true);
            this.getRootPane().putClientProperty("windowModified", Boolean.TRUE);
        }
    }

    private void cleanDirty() {
        if (this.dirty) {
            this.dirty = false;
            this.saveProjectMenuItem.setEnabled(false);
            this.getRootPane().putClientProperty("windowModified", Boolean.FALSE);
        }
    }

    private void computeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_computeButtonActionPerformed
        compute();
    }//GEN-LAST:event_computeButtonActionPerformed

    private void handToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_handToggleButtonActionPerformed
        this.oldMapComponent.setMapTool(new PanTool(this.oldMapComponent));
        this.newMapComponent.setMapTool(new PanTool(this.newMapComponent));
    }//GEN-LAST:event_handToggleButtonActionPerformed

    private void zoomOutToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomOutToggleButtonActionPerformed
        this.oldMapComponent.setMapTool(new ZoomOutTool(this.oldMapComponent));
        this.newMapComponent.setMapTool(new ZoomOutTool(this.newMapComponent));
    }//GEN-LAST:event_zoomOutToggleButtonActionPerformed

    private void zoomInToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomInToggleButtonActionPerformed
        this.oldMapComponent.setMapTool(new ZoomInTool(this.oldMapComponent));
        this.newMapComponent.setMapTool(new ZoomInTool(this.newMapComponent));
    }//GEN-LAST:event_zoomInToggleButtonActionPerformed

    private void showAllNewMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showAllNewMenuItemActionPerformed
        showAllInNewMap();
    }//GEN-LAST:event_showAllNewMenuItemActionPerformed

    private void showAllOldMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showAllOldMenuItemActionPerformed
        oldMapComponent.showAll();
    }//GEN-LAST:event_showAllOldMenuItemActionPerformed

    private void importNewMapMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importNewMapMenuItemActionPerformed
        if (manager.isUsingOpenStreetMap()) {
            String msg = "The imported reference map will replace the OpenStreeMap.\n"
                    + "You can revert to the OpenStreetMap by selecting the\n"
                    + "\"Map > Use OpenStreetMap\" menu command.";
            String title = "Import New Map Image";
            JOptionPane.showMessageDialog(this, msg, title, JOptionPane.INFORMATION_MESSAGE);
        }
        osmCopyrightLabel.setVisible(false);
        importRasterImage(false);
    }//GEN-LAST:event_importNewMapMenuItemActionPerformed

    private void copyMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyMenuItemActionPerformed
        MainWindow.clipboard = this.manager.getLinkManager().serializePoints(true);
        // Inform all windows. The paste command in the edit menu must update.
        MainWindow.updateAllMenusOfAllWindows();
    }//GEN-LAST:event_copyMenuItemActionPerformed

    private void importOldMapMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importOldMapMenuItemActionPerformed
        importRasterImage(true);
    }//GEN-LAST:event_importOldMapMenuItemActionPerformed

    private void exportLinkedPointsAndVectorsInNewMapActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportLinkedPointsAndVectorsInNewMapActionPerformed
        final int nbrLinks = this.manager.getLinkManager().getNumberLinks();
        if (nbrLinks < 1) {
            this.noLinkedPointsErrorMessage();
            return;
        }

        String fileName = exportFileName("txt");
        String expPath = FileUtils.askFile(this,
                "Export Linked Points and Vectors", fileName, false, "txt", null);
        if (expPath == null) {
            return; // user canceled
        }
        // ask for file format
        int res = askFileFormat(false);
        if (res < 0) {
            return; // user canceled
        }

        try {
            this.manager.exportLinkedPointsAndVectorsToASCII(expPath, res == 0);
        } catch (Exception e) {
            this.pointExportError(e);
        }
    }//GEN-LAST:event_exportLinkedPointsAndVectorsInNewMapActionPerformed

    private void exportLinkedPointsInPixelsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportLinkedPointsInPixelsMenuItemActionPerformed
        this.exportLinkedPoints(this.manager.getOldMap());
}//GEN-LAST:event_exportLinkedPointsInPixelsMenuItemActionPerformed

    private void saveProjectMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveProjectMenuItemActionPerformed
        // ask for file path if the document has never been saved or if its
        // path is invalid.
        String ext = ika.mapanalyst.ApplicationInfo.getDocumentExtension();
        if (filePath == null || !new java.io.File(filePath).exists()) {
            filePath = FileUtils.askFile(this, "Save Project",
                    exportFileName(ext), false, ext,
                    new FileNameExtensionFilter("MapAnalyst", ext));
        }

        if (filePath == null) {
            return; // user canceled
        }
        // Don't move this to saveProject(). The path with the correct extension
        // has to be stored.
        filePath = ika.utils.FileUtils.forceFileNameExtension(filePath, ext);
        // the user canceled if filePath is null
        if (filePath != null) {
            saveProject(filePath);
            cleanDirty();
            String fileName = FileUtils.getFileNameWithoutExtension(filePath);
            setTitle(fileName + (Sys.isWindows() ? " - MapAnalyst" : ""));
            MainWindow.updateAllMenusOfAllWindows();
        }
}//GEN-LAST:event_saveProjectMenuItemActionPerformed

    private void saveProjectAsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveProjectAsMenuItemActionPerformed
        // ask for file path
        // 'save as': don't store the path to the file in this.filePath
        String ext = ApplicationInfo.getDocumentExtension();
        String path = FileUtils.askFile(this, "Save Copy of Project",
                exportFileName(ext), false, ext,
                new FileNameExtensionFilter("MapAnalyst", ext));

        if (path != null) {
            saveProject(path);
        }
}//GEN-LAST:event_saveProjectAsMenuItemActionPerformed

    private void gridOptionsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_gridOptionsButtonActionPerformed

        Object initialValue = distortionGridExaggerationFormattedTextField.getValue();
        int res = JOptionPane.showOptionDialog(splitPane,
                gridOptionsPanel,
                "Distortion Grid Options",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null, null, null);

        try {
            if (res != JOptionPane.OK_OPTION) {
                throw new Exception();
            }

            distortionGridExaggerationFormattedTextField.commitEdit();
            double f = ((Number) distortionGridExaggerationFormattedTextField.getValue()).doubleValue();
            gridExaggerationWarningLabel.setVisible(f != 1);
            this.readDistortionGridFromGUI();
            clearTemporaryGUI();
        } catch (Exception ex) {
            distortionGridExaggerationFormattedTextField.setValue(initialValue);
        }

}//GEN-LAST:event_gridOptionsButtonActionPerformed

    private void distortionGridShowUndistortedCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_distortionGridShowUndistortedCheckBoxActionPerformed
        this.readDistortionGridFromGUI();
        clearTemporaryGUI();
    }//GEN-LAST:event_distortionGridShowUndistortedCheckBoxActionPerformed

    private void gridUnitComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_gridUnitComboBoxItemStateChanged
        if (evt.getStateChange() != java.awt.event.ItemEvent.SELECTED) {
            return;
        }

        // make sure degrees are only used when OSM is used as map
        // and when distortion visualizations are generated for the old map
        if (gridUnitComboBox.getSelectedIndex() == 1) {
            if (!manager.isUsingOpenStreetMap() || !manager.isShowInOldMap()) {
                String title = "Degrees not Available";
                String msg = "Graticules of longitude / latitude lines can only\n"
                        + "be generated when OpenStreetMap is used, and when\n"
                        + "the old map is analyzed.\n";
                JOptionPane.showMessageDialog(this, msg, title,
                        JOptionPane.ERROR_MESSAGE, null);
                gridUnitComboBox.setSelectedIndex(0);
                return;
            }
        }

        this.readDistortionGridFromGUI();
        clearTemporaryGUI();
    }//GEN-LAST:event_gridUnitComboBoxItemStateChanged

    private void writeProjectionGUI(String projectionName, double lon0) {

        try {
            updatingGUI = true;

            // initialize projection selection menu
            projectionComboBox.setSelectedItem(projectionName);

            // initialize GUI for entering central longitude value
            boolean autoLon0 = manager.isAutomaticCentralLongitude();
            automaticCentralLongitudeRadioButton.setSelected(autoLon0);
            longitudeFormattedTextField.setValue(lon0);
            longitudeSlider.setValue((int) Math.round(lon0));

        } finally {
            updatingGUI = false;
        }

    }

    private void updateProjectionOptionsButton() {
        Projection p = manager.getProjection();
        boolean enable = p instanceof EquidistantCylindricalProjection;
        enable |= p instanceof CylindricalEqualAreaProjection;
        enable |= p instanceof LambertConformalConicProjection;
        // the current implementation of Mercator does not support latitude of
        // true scale
        // enable |= p instanceof TCEAProjection;

        projectionOptionsButton.setEnabled(enable);
    }

    private void readProjection() {
        if (updatingGUI) {
            return;
        }
        Projection p = (Projection) projectionComboBox.getSelectedItem();
        double lon0 = ((Number) longitudeFormattedTextField.getValue()).doubleValue();
        p.setProjectionLongitudeDegrees(lon0);
        p.setTrueScaleLatitudeDegrees(latTrueScaleDeg);
        p.setProjectionLatitudeDegrees(latTrueScaleDeg);
        p.setEllipsoid(Ellipsoid.SPHERE);
        p.initialize();
        manager.setInitializedProjection(p);
    }

    /**
     * update projectionMeanDistanceLabel in projection dialog
     */
    private void updateMeanDistanceLabel() {
        Projection p = manager.getProjection();
        double[][][] oldNewPts = this.manager.getLinkManager().getLinkedPointsCopy(null);
        double[][] oldPoints = oldNewPts[0];
        double[][] osmPoints = oldNewPts[1];
        Transformation t = manager.getTransformation();
        double d = new ProjectionEvaluator().evalProjection(p, osmPoints, oldPoints, t);
        String str = new DecimalFormat("#,###.0 mm (in old map)").format(d * 1000);
        projectionMeanDistanceLabel.setText(str);
    }

    private void updateProjectionPreview() {
        try {
            Projection p = manager.getProjection();
            URL url = Main.class.getResource(CONTINENTS_PATH);
            ArrayList<MapLine> geographicLines = UngenerateImporter.importData(url.openStream());
            ArrayList<MapLine> projectedLines = new ArrayList<>();
            LineProjector lineProjector = new LineProjector();
            lineProjector.projectLines(geographicLines, projectedLines, p);
            lineProjector.constructGraticule(projectedLines, p);
            lineProjector.constructOutline(p, projectedLines);
            mapComponent.setLines(projectedLines);
        } catch (IOException ex) {
            mapComponent.setLines(null);
            Logger.getLogger(MainWindow.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void updateProjectionInfo() {
        Projection p = manager.getProjection();
        String desc = p.getDescription();
        String history = p.getHistoryDescription();
        if (history != null && history.isEmpty() == false) {
            desc = desc + "\n" + history;
        }
        projectionDescriptionTextArea.setText(desc);
    }

    private void projectionMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_projectionMenuItemActionPerformed

        if (!manager.isUsingOpenStreetMap()) {
            String msg = "Projections are only supported if OpenStreetMap is used.";
            String title = "OpenStreetMap Required";
            ErrorDialog.showErrorDialog(msg, title, null, splitPane);
            return;
        }

        if (!this.manager.getLinkManager().hasEnoughLinkedPointsForComputation()) {
            this.notEnoughLinkedPointsErrorMessage();
            return;
        }

        // initialize the dialog
        Projection initialProjection = (Projection) manager.getProjection().clone();
        boolean initialAutomaticCentralLongitude = manager.isAutomaticCentralLongitude();

        // mean longitude of points in new map
        Double meanLon = manager.meanLongitudeOfLinkedPointsInNewMap();
        assert (meanLon != null);

        writeProjectionGUI(initialProjection.toString(), meanLon);

        // initialize projection description
        String projDesc = initialProjection.getDescription();
        projectionDescriptionTextArea.setText(projDesc);

        // initialize text fields indicating the mean longitude of the points
        // in the new map, and the mean distance between control points
        final String meanLonStr = new DecimalFormat("0.###").format(meanLon);
        projectionMeanLongitudeLabel.setText("Mean longitude: " + meanLonStr);
        updateProjectionOptionsButton();
        updateMeanDistanceLabel();

        // initialize the map in the dialog showing a preview of the projection
        updateProjectionInfo();
        updateProjectionPreview();
        updateProjectionOptionsButton();

        projectionPanel.validate();
        int res = JOptionPane.showOptionDialog(splitPane,
                projectionPanel,
                "Projection of Old Map",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null, null, null);

        if (res != JOptionPane.OK_OPTION) {
            // store the initial central longitude in the GUI for the next time
            // the dialog is opened
            double lon0 = initialProjection.getProjectionLongitudeDegrees();
            longitudeFormattedTextField.setValue(lon0);

            // reset to initial projection when the user cancels
            manager.setAutomaticCentralLongitude(initialAutomaticCentralLongitude);
            manager.setInitializedProjection(initialProjection);
            compute();
        }

    }//GEN-LAST:event_projectionMenuItemActionPerformed

    private void projectionComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_projectionComboBoxItemStateChanged

        if (evt.getStateChange() != ItemEvent.SELECTED || updatingGUI) {
            return;
        }

        readProjection();

        boolean liveUpdate = projectionLiveUpdateCheckBox.isSelected();
        if (liveUpdate) {
            compute();
            updateMeanDistanceLabel();
        }

        updateProjectionInfo();
        updateProjectionPreview();
        updateProjectionOptionsButton();
    }//GEN-LAST:event_projectionComboBoxItemStateChanged

    private void compareProjectionsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_compareProjectionsButtonActionPerformed

        // test if there are enough linked points
        if (!this.manager.getLinkManager().hasEnoughLinkedPointsForComputation()) {
            this.notEnoughLinkedPointsErrorMessage();
            return;
        }

        double[][][] oldNewPts = this.manager.getLinkManager().getLinkedPointsCopy(null);
        double[][] oldPoints = oldNewPts[0];
        double[][] newPoints = oldNewPts[1];
        Transformation transformation = manager.getTransformation();
        ProjectionEvaluator evaluator = new ProjectionEvaluator();
        Projection p = evaluator.getBestFit(newPoints, oldPoints, transformation);
        try {
            updatingGUI = true;

            projectionComboBox.setSelectedItem(p.toString());
            customCentralLongitudeRadioButton.setSelected(true);
            double lon0 = p.getProjectionLongitudeDegrees();
            longitudeFormattedTextField.setValue(lon0);
            longitudeSlider.setValue((int) Math.round(lon0));
        } finally {
            updatingGUI = false;
        }

        boolean liveUpdate = projectionLiveUpdateCheckBox.isSelected();
        if (liveUpdate) {
            readProjection();
            compute();
            updateMeanDistanceLabel();
        }

        String report = evaluator.getLastFittingReport();
        TextWindow.showSimpleTextDialog(report, "Projection Comparison", this, 50);
    }//GEN-LAST:event_compareProjectionsButtonActionPerformed

    private void longitudeSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_longitudeSliderStateChanged
        if (updatingGUI) {
            return;
        }

        try {
            updatingGUI = true;
            longitudeFormattedTextField.setValue(new Double(longitudeSlider.getValue()));
        } finally {
            updatingGUI = false;
        }

        boolean liveUpdate = projectionLiveUpdateCheckBox.isSelected();
        if (longitudeSlider.getValueIsAdjusting() == false) {
            readProjection();
            updateProjectionPreview();
            if (liveUpdate) {
                compute();
            }
            updateMeanDistanceLabel();
        }

    }//GEN-LAST:event_longitudeSliderStateChanged

    private void exportToShape(int shapeType, boolean oldMap) {
        String fileName = exportFileName("shp");
        String path = FileUtils.askFile(this, "Save ESRI Shape File", fileName, false, "shp", null);
        if (path != null) {
            exportToShape(shapeType, oldMap, path);
        }
    }

    private void exportToShape(int shapeType, boolean oldMap, String path) {
        try {
            MapComponent map = oldMap ? oldMapComponent : newMapComponent;

            final double mapScale = map.getScaleFactor();
            ShapeExporter exporter = new ShapeExporter(mapScale);
            exporter.setShapeType(shapeType);
            export(exporter, path, oldMap);

            Table table = new Table("US-ASCII");
            table.setName("table");
            table.addColumn("ID");
            final int rowCount = exporter.getFeatureCount();
            for (int i = 0; i < rowCount; i++) {
                table.addRow(new Object[]{new Double(i)});
            }
            GeoSet geoSet = map.getGeoSet();
            exporter.exportTableForGeometry(path, table, geoSet);
        } catch (IOException ex) {
            showExportError(ex);
        }
    }

    private void exportOldShapeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportOldShapeMenuItemActionPerformed
        exportToShape(ShapeGeometryExporter.POLYLINE_SHAPE_TYPE, true);
    }//GEN-LAST:event_exportOldShapeMenuItemActionPerformed

    private void exportNewShapeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportNewShapeMenuItemActionPerformed
        exportToShape(ShapeGeometryExporter.POLYLINE_SHAPE_TYPE, false);
    }//GEN-LAST:event_exportNewShapeMenuItemActionPerformed

    private void showErrorInOldMapCheckBoxMenuItemStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_showErrorInOldMapCheckBoxMenuItemStateChanged
        // TODO add your handling code here:
    }//GEN-LAST:event_showErrorInOldMapCheckBoxMenuItemStateChanged

    private void removeOSMMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeOSMMenuItemActionPerformed
        // give OSM map a chance to clean up and unregister before removing it from the map
        manager.disposeOSM();

        manager.setNewMap(null);

        // the visualization have to be recalculated when switching from/to OSM
        clearTemporaryGUI();

        // switch from spherical to Cartesian coordinates of selected point
        updateLinkGUI();

        // hide OSM copyright
        osmCopyrightLabel.setVisible(false);
    }//GEN-LAST:event_removeOSMMenuItemActionPerformed

    private void addOSMMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addOSMMenuItemActionPerformed
        manager.setNewMap(new OpenStreetMap(newMapComponent));

        // the visualization have to be recalculated when switching from/to OSM
        clearTemporaryGUI();

        // switch from Cartesian to spherical coordinates of selected point
        updateLinkGUI();

        // show OSM copyright
        osmCopyrightLabel.setVisible(true);
    }//GEN-LAST:event_addOSMMenuItemActionPerformed

    private void mapsMenuMenuSelected(javax.swing.event.MenuEvent evt) {//GEN-FIRST:event_mapsMenuMenuSelected
        this.updateMapsMenu();
    }//GEN-LAST:event_mapsMenuMenuSelected

    private void longitudeFormattedTextFieldPropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_longitudeFormattedTextFieldPropertyChange

        // treat changes to the longitude text field in the projection panel
        if (!updatingGUI && "value".equals(evt.getPropertyName())) {
            try {
                updatingGUI = true;

                // synchronize slider with this text field
                Object v = longitudeFormattedTextField.getValue();
                double d = ((Number) v).doubleValue();
                longitudeSlider.setValue((int) Math.round(d));
            } finally {
                updatingGUI = false;
            }

            // apply new value
            boolean liveUpdate = projectionLiveUpdateCheckBox.isSelected();
            if (liveUpdate) {
                readProjection();
                compute();
                updateMeanDistanceLabel();
                updateProjectionPreview();
            }

        }

    }//GEN-LAST:event_longitudeFormattedTextFieldPropertyChange

    private void projectionLiveUpdateCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_projectionLiveUpdateCheckBoxActionPerformed

        // treat clicks on check box in the projection panel for live update
        if (projectionLiveUpdateCheckBox.isSelected()) {
            readProjection();
            compute();
            updateMeanDistanceLabel();
        } else {
            manager.clearGraphics();
        }
    }//GEN-LAST:event_projectionLiveUpdateCheckBoxActionPerformed

    private void centralLongitudeButton(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_centralLongitudeButton

        // treat clicks on radio buttons switching between automatic and custom
        // central longitude
        boolean automaticCentralLongitude = automaticCentralLongitudeRadioButton.isSelected();
        longitudeSlider.setEnabled(!automaticCentralLongitude);
        longitudeFormattedTextField.setEnabled(!automaticCentralLongitude);
        if (automaticCentralLongitude) {
            Double lon0 = manager.meanLongitudeOfLinkedPointsInNewMap();
            longitudeFormattedTextField.setValue(lon0);
        }
        manager.setAutomaticCentralLongitude(automaticCentralLongitude);

        boolean liveUpdate = projectionLiveUpdateCheckBox.isSelected();
        if (liveUpdate && !updatingGUI) {
            readProjection();
            compute();
            updateMeanDistanceLabel();
        }
    }//GEN-LAST:event_centralLongitudeButton

    private void projectionOptionsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_projectionOptionsButtonActionPerformed

        final String msg;
        Projection p = manager.getProjection();
        if (p instanceof ConicProjection) {
            msg = "Standard parallel:";
        } else {
            msg = "Latitude of true scale:";
        }

        String res = JOptionPane.showInputDialog(splitPane, msg, latTrueScaleDeg);
        if (res == null) {
            return;
        }
        try {
            latTrueScaleDeg = new Double(res);
            if (latTrueScaleDeg < 0) {
                latTrueScaleDeg = new Double(0);
            }
            if (latTrueScaleDeg > 90) {
                latTrueScaleDeg = new Double(90);
            }

            readProjection();
            updateProjectionPreview();

            boolean liveUpdate = projectionLiveUpdateCheckBox.isSelected();
            if (liveUpdate) {
                compute();
                updateMeanDistanceLabel();
            }

        } catch (NumberFormatException e) {
            String errMsg = "The number entered is not valid.";
            JOptionPane.showConfirmDialog(splitPane, errMsg);
        }

    }//GEN-LAST:event_projectionOptionsButtonActionPerformed

    private void correctOSMMisalignmentBugMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_correctOSMMisalignmentBugMenuItemActionPerformed
        String title = "Correct OpenStreetMap Misalignment";
        String msg = "<html>MapAnalyst version 1.3.0 to 1.3.23 had a bug that resulted<br>"
                + "in wrong coordinates when exporting OpenStreetMap points. This<br>"
                + "bug was fixed in version 1.3.24, but OpenStreetMap points<br>"
                + "created with earlier versions of MapAnalyst will be offset in<br>"
                + "version 1.3.24 and later.<br><br>"
                + "Do you want to correct this offset?<br><br>"
                + "Note: only apply this correction once to your points, then<br>"
                + "save the project or export the points to a new file.</html>";
        String[] options = new String[]{"Correct Offset", "Cancel"};
        int response = JOptionPane.showOptionDialog(this,
                msg,
                title,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);

        if (response == JOptionPane.OK_OPTION) {

            GeoSet ptsGeoSet = manager.getLinkManager().getNewPointsGeoSet();
            final int nbrPoints = ptsGeoSet.getNumberOfChildren();

            // buggy OSM coordinate system (with wrong sphere radius)
            MercatorProjection buggyMercator = new MercatorProjection();
            buggyMercator.setMaxLatitude(OpenStreetMap.MAX_LAT);
            buggyMercator.setMinLatitude(OpenStreetMap.MIN_LAT);
            Ellipsoid buggyOSMSphere = new Ellipsoid("bug", 6371000.0, 6371000.0, 0.0, "Buggy OSM Sphere");
            buggyMercator.setEllipsoid(buggyOSMSphere);
            buggyMercator.initialize();

            // OSM coordinate system
            MercatorProjection osmMercator = ProjectionsManager.createWebMercatorProjection();

            // convert from buggy OSM to spherical coordinates, then to OSM coordinates
            for (int i = 0; i < nbrPoints; i++) {
                GeoPoint pt = (GeoPoint) (ptsGeoSet.getGeoObject(i));
                Point2D.Double lonLat = new Point2D.Double();
                buggyMercator.inverseTransformRadians(pt.getX(), pt.getY(), lonLat);
                osmMercator.transformRadians(lonLat.getX(), lonLat.getY(), lonLat);
                pt.setX(lonLat.getX());
                pt.setY(lonLat.getY());
            }

            newMapComponent.repaint();
            addUndo(title);
            clearTemporaryGUI();
        }
    }//GEN-LAST:event_correctOSMMisalignmentBugMenuItemActionPerformed

    private void showOSMGraticuleCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showOSMGraticuleCheckBoxMenuItemActionPerformed
        OpenStreetMap osm = manager.getOpenStreetMap();
        if (osm != null) {
            osm.setShowGraticule(showOSMGraticuleCheckBoxMenuItem.isSelected());
            newMapComponent.repaint();
        }
    }//GEN-LAST:event_showOSMGraticuleCheckBoxMenuItemActionPerformed

    private void showOSMTropicsCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showOSMTropicsCheckBoxMenuItemActionPerformed
        OpenStreetMap osm = manager.getOpenStreetMap();
        if (osm != null) {
            osm.setShowTropics(showOSMTropicsCheckBoxMenuItem.isSelected());
            newMapComponent.repaint();
        }
    }//GEN-LAST:event_showOSMTropicsCheckBoxMenuItemActionPerformed

    private void showOSMPolarCirclesCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showOSMPolarCirclesCheckBoxMenuItemActionPerformed
        OpenStreetMap osm = manager.getOpenStreetMap();
        if (osm != null) {
            osm.setShowPolarCircles(showOSMPolarCirclesCheckBoxMenuItem.isSelected());
            newMapComponent.repaint();
        }
    }//GEN-LAST:event_showOSMPolarCirclesCheckBoxMenuItemActionPerformed

    private String getImageSizeInfo(GeoImage img) {
        StringBuilder sb = new StringBuilder();
        if (img != null) {
            sb.append(img.getBufferedImage().getWidth());
            sb.append(" \u00D7 ");
            sb.append(img.getBufferedImage().getHeight());
            sb.append(" pixels");
        }
        return sb.toString();
    }

    /**
     * String to inform the user if the project file contains a reference to an
     * image file and the file could not be loaded.
     *
     * @param img
     * @return info string or an empty string if the GeoImage does not contain a
     * path to an image file
     */
    private String couldNotFindImageInfo(GeoImage img) {
        if (img == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String imageFilePath = filePath(img);
        if (imageFilePath.isEmpty() == false) {
            sb.append("No image could be found here:");
            sb.append("<br>");
            sb.append(imageFilePath);
            sb.append("<br>");
        }
        return sb.toString();
    }

    /**
     * Returns a path to the image file of a GeoImage.
     * @param img
     * @return path or an empty string if no path exists or img is null.
     */
    private String filePath(GeoImage img) {
        if (img == null) {
            return "";
        }
        String imageFilePath = img.getFilePath();
        if (imageFilePath != null && imageFilePath.trim().isEmpty() == false) {
            return imageFilePath.trim();
        }
        return "";
    }

    /**
     * Returns a short description of the old map image in HTML format.
     *
     * @return description
     */
    private String getOldMapImageInfo() {
        StringBuilder sb = new StringBuilder();
        GeoImage img = manager.getOldMap();

        if (img != null
                && img.getBufferedImage() != null
                && img.getBounds2D() != null) {
            sb.append(filePath(img));
            sb.append("<br>");

            Rectangle2D bounds = img.getBounds2D();
            // size in cm
            sb.append(String.format("%1$,.1f", bounds.getWidth() * 100));
            sb.append(" cm \u00D7 ");
            sb.append(String.format("%1$,.1f", bounds.getHeight() * 100));
            sb.append(" cm (");
            // size in inch
            sb.append(String.format("%1$,.1f", bounds.getWidth() * 100 / 2.54));
            sb.append(" \u00D7 ");
            sb.append(String.format("%1$,.1f", bounds.getHeight() * 100 / 2.54));
            sb.append(" inches)<br>");

            // image size in pixels
            sb.append(getImageSizeInfo(img));
        } else {
            sb.append("No old map image loaded.<br>");
            String missingImageFileInfo = couldNotFindImageInfo(img);
            if (missingImageFileInfo.isEmpty()) {
                sb.append("Use File > Import Old Map Image to load an image.");
            } else {
                sb.append(missingImageFileInfo);
            }
        }
        return sb.toString();
    }

    /**
     * Returns a short description of the new map image in HTML format.
     *
     * @return description
     */
    private String getNewMapImageInfo() {
        if (manager.isUsingOpenStreetMap()) {
            return "OpenStreetMap";
        }

        StringBuilder sb = new StringBuilder();
        GeoImage img = manager.getNewMap();
        if (img != null 
                && img.getBufferedImage() != null 
                && img.getBounds2D() != null) {
            sb.append(filePath(img));
            sb.append("<br>");
            
            Rectangle2D bounds = img.getBounds2D();
            // extent in reference coordinate system
            sb.append(String.format("%1$,.1f", bounds.getWidth()));
            sb.append(" m \u00D7 ");
            sb.append(String.format("%1$,.1f", bounds.getHeight()));
            sb.append(" m<br>");

            // image size in pixels
            sb.append(getImageSizeInfo(img));
            sb.append("<br>");

            // size of pixel
            sb.append("Pixel size: ");
            sb.append(img.getPixelSizeX());
            sb.append(" m \u00D7 ");
            sb.append(img.getPixelSizeY());
            sb.append(" m");
        } else {
            sb.append("No new map image loaded.<br>");
            String missingImageFileInfo = couldNotFindImageInfo(img);
            if (missingImageFileInfo.isEmpty()) {
                sb.append("Use Maps > Add OpenStreetMap or File > Import New Map Image.");
            } else {
                sb.append(missingImageFileInfo);
            }
        }
        return sb.toString();
    }

    /**
     * Display dialog with information about the size of map images
     *
     * @param oldMap for old or new map
     */
    private void aboutTheMaps() {
        StringBuilder sb = new StringBuilder();
        String oldMapInfo = getOldMapImageInfo();
        String newMapInfo = getNewMapImageInfo();
        sb.append("<html>");

        // old map
        sb.append("<b>Old Map</b><br>");
        if (oldMapInfo != null) {
            sb.append(oldMapInfo);
        }

        // new map
        sb.append("<br><br><b>New Map</b><br>");
        if (newMapInfo != null) {
            sb.append(newMapInfo);
        }

        sb.append("</html>");
        String dlgTitle = "About the Maps";
        JOptionPane.showMessageDialog(this, sb.toString(),
                dlgTitle, JOptionPane.PLAIN_MESSAGE, null);
    }

    private void mapSizeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mapSizeMenuItemActionPerformed
        aboutTheMaps();
    }//GEN-LAST:event_mapSizeMenuItemActionPerformed

    /**
     * Set display unit used by the CoordinateInfoPanel.
     *
     * @param format formatting string for CoordinateFormatter
     * @param unit unit appended to value, such as "m" or "cm"
     * @param scaleFactor values are divided by this scale factor. For example,
     * for cm the scale factor is 100.
     */
    private void oldMapdisplayUnit(String format, String unit, double scaleFactor) {
        CoordinateFormatter cf = new CoordinateFormatter(format + " " + unit, format, scaleFactor);
        oldMapComponent.setCoordinateFormatter(cf);
        coordinateInfoPanel.repaint();
    }

    private void oldUnitCmCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_oldUnitCmCheckBoxMenuItemActionPerformed
        oldMapdisplayUnit("###,##0.0", "cm", 100);
    }//GEN-LAST:event_oldUnitCmCheckBoxMenuItemActionPerformed

    private void oldUnitPxCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_oldUnitPxCheckBoxMenuItemActionPerformed
        GeoImage img = manager.getOldMap();
        if (img != null && img.getBufferedImage() != null && img.getBounds2D() != null) {
            int pixelWidth = img.getBufferedImage().getWidth();
            double width = img.getBounds2D().getWidth();
            oldMapdisplayUnit("###,##0", "px", pixelWidth / width);
        }
    }//GEN-LAST:event_oldUnitPxCheckBoxMenuItemActionPerformed

    private void oldUnitMCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_oldUnitMCheckBoxMenuItemActionPerformed
        String format = "###,##0.000";
        GeoImage img = manager.getOldMap();
        if (img != null && img.getBounds2D() != null && img.getBounds2D().getWidth() > 10) {
            // the image is georeferenced, so display meters with one decimal
            format = "###,##0.0";
        }
        oldMapdisplayUnit(format, "m", 1);
    }//GEN-LAST:event_oldUnitMCheckBoxMenuItemActionPerformed

    private void oldUnitInchCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_oldUnitInchCheckBoxMenuItemActionPerformed
        oldMapdisplayUnit("###,##0.0", "in", 100 / 2.54);
    }//GEN-LAST:event_oldUnitInchCheckBoxMenuItemActionPerformed

    private void warpMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_warpMenuItemActionPerformed
        manager.warpMap();
    }//GEN-LAST:event_warpMenuItemActionPerformed

    private void distortionGridUncertaintyAlphaSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_distortionGridUncertaintyAlphaSliderStateChanged
        if (distortionGridUncertaintyAlphaSlider.getValueIsAdjusting() == false) {
            readDistortionGridFromGUI();
            clearTemporaryGUI();
        }
    }//GEN-LAST:event_distortionGridUncertaintyAlphaSliderStateChanged

    private void distortionGridUncertaintyQuantileSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_distortionGridUncertaintyQuantileSliderStateChanged
        if (distortionGridUncertaintyQuantileSlider.getValueIsAdjusting() == false) {
            readDistortionGridFromGUI();
            clearTemporaryGUI();
        }
    }//GEN-LAST:event_distortionGridUncertaintyQuantileSliderStateChanged

    private void enableLinkingGUIForSelectedLink(Link link) {
        this.linkToggleButton.setEnabled(true);
        this.linkToggleButton.setSelected(true);
        this.linkToggleButton.setText(linkButtonSelectedLabel);

        this.linkNameButton.setEnabled(true);
        this.linkNameLabel.setText(link.getName());

        this.writePointCoords(link.getPtOld(), link.getPtNew());
    }

    private void disableLinkingGUI() {
        this.linkToggleButton.setEnabled(false);
        this.linkToggleButton.setSelected(false);
        this.linkToggleButton.setText(linkButtonDeselectedLabel);

        this.linkNameButton.setEnabled(false);
        this.linkNameLabel.setText("");

        this.pointNewXLabel.setText(null);
        this.pointNewYLabel.setText(null);
        this.pointOldXLabel.setText(null);
        this.pointOldYLabel.setText(null);
    }

    private void enableLinkingGUIForUnlinkedPoints(GeoPoint oldPt, GeoPoint newPt) {
        this.linkToggleButton.setEnabled(true);
        this.linkToggleButton.setSelected(false);
        this.linkToggleButton.setText(linkButtonDeselectedLabel);

        this.linkNameButton.setEnabled(false);
        this.linkNameLabel.setText("");

        this.writePointCoords(oldPt, newPt);
    }

    private void writePointCoords(GeoPoint oldPt, GeoPoint newPt) {
        if (newPt != null) {
            Point2D.Double pt = new Point2D.Double(newPt.getX(), newPt.getY());
            // test whether spherical coordinates for OpenStreetMap should be displayed
            boolean osm = manager != null && manager.isUsingOpenStreetMap();
            // format coordinate strings
            String[] str = newMapComponent.coordinatesStrings(pt, osm);
            pointNewXLabel.setText(str[0]);
            pointNewYLabel.setText(str[1]);
        }
        if (oldPt != null) {
            CoordinateFormatter oldFormatter = this.oldMapComponent.getCoordinateFormatter();
            this.pointOldXLabel.setText(oldFormatter.format(oldPt.getX()));
            this.pointOldYLabel.setText(oldFormatter.format(oldPt.getY()));
        }
    }

    private void updateLinkGUI() {
        LinkManager linkManager = this.manager.getLinkManager();
        java.util.Vector selectedLinks = linkManager.getSelectedLinks();

        GeoPoint oldSingleSelectedGeoPoint = linkManager.getSingleSelectedGeoPoint(true);
        GeoPoint newSingleSelectedGeoPoint = linkManager.getSingleSelectedGeoPoint(false);

        // update enabled state of Link button
        if (selectedLinks.size() == 1) {
            Link link = (Link) (selectedLinks.get(0));

            // make sure that besides the link there are no other points selected
            if (oldSingleSelectedGeoPoint != null
                    && newSingleSelectedGeoPoint != null
                    && linkManager.getLink(oldSingleSelectedGeoPoint) == link
                    && linkManager.getLink(newSingleSelectedGeoPoint) == link) {
                enableLinkingGUIForSelectedLink(link);
                return;
            }
        }

        if (selectedLinks.size() > 1) {
            disableLinkingGUI();
            return;
        }

        // no link selected
        // test if there is exactly one selected point in the old map
        // and one selected point in the new map.
        if (oldSingleSelectedGeoPoint == null
                || newSingleSelectedGeoPoint == null) {
            disableLinkingGUI();
            writePointCoords(oldSingleSelectedGeoPoint, newSingleSelectedGeoPoint);
        } else {
            enableLinkingGUIForUnlinkedPoints(oldSingleSelectedGeoPoint,
                    newSingleSelectedGeoPoint);
        }
    }

    private void updateTransformationReportWindow(boolean valid) {
        String report;

        String nl = System.getProperty("line.separator");
        if (valid) {
            report = "Report for Last Computation" + nl
                    + "---------------------------" + nl + nl
                    + ika.mapanalyst.ApplicationInfo.getApplicationName()
                    + " Version "
                    + ika.mapanalyst.ApplicationInfo.getApplicationVersion()
                    + nl
                    + ika.mapanalyst.ApplicationInfo.getCurrentTimeAndDate();
            report += manager.getTransformationReport();
        } else {
            report = "Please press the Compute button.";
        }
        if (this.transformationReportWin == null) {
            String title = "Report of Last Computation";
            this.transformationReportWin = new ika.utils.TextWindow(
                    this, false, false, report, title);
        } else {
            this.transformationReportWin.setText(report);
        }

    }

    private void updatePointsWindowSelection() {
        if (/*updateTableSelection && */manager != null && manager.getLinkManager() != null) {

            ListSelectionModel selModel = pointsTable.getSelectionModel();
            try {
                selModel.setValueIsAdjusting(true);
                selModel.clearSelection();

                int linkCount = manager.getLinkManager().getNumberLinks();
                for (int i = 0; i < linkCount; i++) {
                    Link link = manager.getLinkManager().getLink(i);
                    if (link.isSelected()) {
                        selModel.addSelectionInterval(i, i);
                    }
                }
            } finally {
                selModel.setValueIsAdjusting(false);
            }
        }
    }

    private void updatePointCoordinatesWindow(boolean updateTableSelection) {

        if (this.pointsDialog == null) {
            pointsDialog = new JDialog(this);
            pointsDialog.setTitle("Linked Points");
            pointsDialog.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            pointsDialog.setContentPane(this.pointTablePanel);
            pointsDialog.setLocation(50, 50);
            pointsDialog.setSize(800, 600);
            pointsDialog.setResizable(true);
        }

        // this will clear the selection of the table!
        ((DefaultTableModel) this.pointsTable.getModel()).fireTableDataChanged();

        // update selection of table
        updatePointsWindowSelection();
    }

    private void showPoints() {
        this.manager.setPointsVisible(true);
        this.oldMapComponent.repaint();
        this.newMapComponent.repaint();
        this.showPointsCheckBoxMenuItem.setSelected(true);
    }

    @Override
    public void geoSetChanged(ika.geo.GeoSet geoSet) {
        manager.clearGraphics();
        this.clearTemporaryGUI();
        this.updateLinkGUI();

        this.updatePointCoordinatesWindow(true);
        this.updateAllMenus();
    }

    @Override
    public void geoSetSelectionChanged(GeoSet geoSet) {
        this.updateLinkGUI();
        this.updateAllMenus();
        this.updatePointsWindowSelection();
    }

    public void setMekenkampVisible(boolean visible) {
        boolean mekenkampIsVisible = -1
                != this.visualizationTabbedPane.indexOfComponent(this.CirclePanel);
        if (visible && !mekenkampIsVisible) {
            this.visualizationTabbedPane.addTab("Circles", this.CirclePanel);
            this.visualizationTabbedPane.setSelectedComponent(this.CirclePanel);
        } else if (!visible && mekenkampIsVisible) {
            this.visualizationTabbedPane.remove(this.CirclePanel);
            this.visualizationTabbedPane.setSelectedIndex(0);
        }
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel CirclePanel;
    private javax.swing.JPanel DistortionGridPanel;
    private javax.swing.JPanel DrawingPanel;
    private javax.swing.JPanel ErrorVectorsPanel;
    private javax.swing.JPanel IsoscalesPanel;
    private javax.swing.JMenuItem addOSMMenuItem;
    private javax.swing.JCheckBoxMenuItem affine5CheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem affine6CheckBoxMenuItem;
    private javax.swing.JMenu analysisMenu;
    private javax.swing.JRadioButton automaticCentralLongitudeRadioButton;
    private javax.swing.JPanel bottomPanel1;
    private javax.swing.JPanel bottomPanel2;
    private javax.swing.ButtonGroup centralLongitudeButtonGroup;
    private javax.swing.JPanel circlePanel;
    private javax.swing.JSlider circleScaleSlider;
    private javax.swing.JLabel circlesInfoHeader;
    private javax.swing.JTextArea circlesInfoTextArea;
    private ika.gui.LineAppearancePanel circlesLineAppearancePanel;
    private javax.swing.JLabel circlesScaleLabel;
    private javax.swing.JPanel circlesScalePanel;
    private javax.swing.JCheckBox circlesVisibleCheckBox;
    private javax.swing.JMenuItem closeProjectMenuItem;
    private javax.swing.JButton compareProjectionsButton;
    private javax.swing.JMenuItem compareTransformationsMenuItem;
    private javax.swing.JButton computeButton;
    private javax.swing.JMenuItem computeMenuItem;
    private ika.gui.CoordinateInfoPanel coordinateInfoPanel;
    private javax.swing.JMenuItem copyMenuItem;
    private javax.swing.JMenuItem correctOSMMisalignmentBugMenuItem;
    private javax.swing.JRadioButton customCentralLongitudeRadioButton;
    private javax.swing.JMenuItem cutMenuItem;
    private javax.swing.JMenu debugMenu;
    private javax.swing.JMenuItem deleteMenuItem;
    private javax.swing.JMenuItem deselectPointsMenuItem;
    private javax.swing.JToggleButton distanceToggleButton;
    private javax.swing.JPanel distortionControlSizePanel;
    private javax.swing.JFormattedTextField distortionGridExaggerationFormattedTextField;
    private javax.swing.JComboBox distortionGridExtensionComboBox;
    private javax.swing.JComboBox distortionGridLabelComboBox;
    private javax.swing.JComboBox distortionGridLabelSequenceComboBox;
    private ika.gui.LineAppearancePanel distortionGridLineAppearancePanel;
    private ika.gui.NumberField distortionGridMeshSizeNumberField;
    private javax.swing.JFormattedTextField distortionGridOffsetXFormattedTextField;
    private javax.swing.JFormattedTextField distortionGridOffsetYFormattedTextField;
    private javax.swing.JPanel distortionGridPanel;
    private javax.swing.JCheckBox distortionGridShowUndistortedCheckBox;
    private javax.swing.JSlider distortionGridSmoothnessSlider;
    private javax.swing.JSlider distortionGridUncertaintyAlphaSlider;
    private javax.swing.JSlider distortionGridUncertaintyQuantileSlider;
    private javax.swing.JCheckBox distortionGridVisibleCheckBox;
    private javax.swing.JButton drawingClearButton;
    private javax.swing.JButton drawingImportButton;
    private javax.swing.JTextArea drawingInfoTextArea;
    private javax.swing.JPanel drawingPanel;
    private javax.swing.JCheckBox drawingVisibleCheckBox;
    private javax.swing.JMenu editMenu;
    private javax.swing.JPanel errorVectorOutlierPanel;
    private javax.swing.JPanel errorVectorScalePanel;
    private javax.swing.JCheckBox errorVectorsFillCirclesCheckBox;
    private ika.gui.ColorButton errorVectorsFillColorButton;
    private ika.gui.LineAppearancePanel errorVectorsLineAppearancePanel;
    private javax.swing.JCheckBox errorVectorsOutliersCheckBox;
    private ika.gui.ColorButton errorVectorsOutliersColorButton;
    private javax.swing.JPanel errorVectorsPanel;
    private javax.swing.JLabel errorVectorsScaleLabel;
    private ika.gui.NumberField errorVectorsScaleNumberField;
    private javax.swing.JPanel errorVectorsScalePanel;
    private javax.swing.JComboBox errorVectorsShowComboBox;
    private javax.swing.JCheckBox errorVectorsVisibleCheckBox;
    private javax.swing.JPanel exaggerationPanel;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JSeparator exitMenuSeparator;
    private javax.swing.JMenuItem exportLinkedPointsAndVectorsInNewMap;
    private javax.swing.JMenuItem exportLinkedPointsInPixelsMenuItem;
    private javax.swing.JMenuItem exportLinkedPointsMenuItem;
    private javax.swing.JMenuItem exportNewDXFMenuItem;
    private javax.swing.JMenuItem exportNewJPEGMenuItem;
    private javax.swing.JMenu exportNewMapGraphicsMenu;
    private javax.swing.JMenuItem exportNewPNGMenuItem;
    private javax.swing.JMenuItem exportNewPointsMenuItem;
    private javax.swing.JMenuItem exportNewSVGMenuItem;
    private javax.swing.JMenuItem exportNewShapeMenuItem;
    private javax.swing.JMenuItem exportNewUngenerateMenuItem;
    private javax.swing.JMenuItem exportNewWMFMenuItem;
    private javax.swing.JMenuItem exportOldDXFMenuItem;
    private javax.swing.JMenuItem exportOldJPEGMenuItem;
    private javax.swing.JMenu exportOldMapGraphicsMenu;
    private javax.swing.JMenuItem exportOldPNGMenuItem;
    private javax.swing.JMenuItem exportOldPointsMenuItem;
    private javax.swing.JMenuItem exportOldSVGMenuItem;
    private javax.swing.JMenuItem exportOldShapeMenuItem;
    private javax.swing.JMenuItem exportOldUngenerateMenuItem;
    private javax.swing.JMenuItem exportOldWMFMenuItem;
    private javax.swing.JMenu exportPointsMenu;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JMenuItem findLinkMenuItem;
    private ika.gui.GeoObjectInfoPanel geoObjectInfoPanel;
    private javax.swing.JLabel gridExaggerationWarningLabel;
    private javax.swing.JButton gridOptionsButton;
    private javax.swing.JPanel gridOptionsPanel;
    private javax.swing.JComboBox gridUnitComboBox;
    private javax.swing.JLabel hampelEstimatorALabel;
    private javax.swing.JTextArea hampelEstimatorATextArea;
    private javax.swing.JTextField hampelEstimatorATextField;
    private javax.swing.JLabel hampelEstimatorBLabel;
    private javax.swing.JTextArea hampelEstimatorBTextArea;
    private javax.swing.JTextField hampelEstimatorBTextField;
    private javax.swing.JLabel hampelEstimatorCLabel;
    private javax.swing.JTextArea hampelEstimatorCTextArea;
    private javax.swing.JTextField hampelEstimatorCTextField;
    private javax.swing.JCheckBoxMenuItem hampelEstimatorCheckBoxMenuItem;
    private javax.swing.JTextArea hampelEstimatorMinMaxATextArea;
    private javax.swing.JTextArea hampelEstimatorMinMaxBTextArea;
    private javax.swing.JTextArea hampelEstimatorMinMaxCTextArea;
    private javax.swing.JPanel hampelEstimatorParametersPanel;
    private javax.swing.JToggleButton handToggleButton;
    private javax.swing.JCheckBoxMenuItem helmertCheckBoxMenuItem;
    private javax.swing.JMenu helpMenu;
    private javax.swing.JCheckBoxMenuItem huberEstimatorCheckBoxMenuItem;
    private javax.swing.JTextArea huberEstimatorKTextArea;
    private javax.swing.JTextField huberEstimatorKTextField;
    private javax.swing.JTextArea huberEstimatorMinMaxTextArea;
    private javax.swing.JPanel huberEstimatorParametersPanel;
    private javax.swing.ButtonGroup imageRenderingQualityButtonGroup;
    private javax.swing.JMenuItem importLinkedPointsMenuItem;
    private javax.swing.JMenuItem importNewMapMenuItem;
    private javax.swing.JMenuItem importNewPointsMenuItem;
    private javax.swing.JMenuItem importOldMapMenuItem;
    private javax.swing.JMenuItem importOldPointsMenuItem;
    private javax.swing.JMenu importPointsMenu;
    private javax.swing.JMenuItem infoMenuItem;
    private javax.swing.JToolBar infoToolBar;
    private ika.gui.NumberField isolinesRadiusNumberField;
    private ika.gui.LineAppearancePanel isolinesRotationAppearancePanel;
    private javax.swing.JLabel isolinesRotationIntervalLabel;
    private ika.gui.NumberField isolinesRotationIntervalNumberField;
    private javax.swing.JLabel isolinesRotationLabel;
    private javax.swing.JCheckBox isolinesRotationVisibleCheckBox;
    private ika.gui.LineAppearancePanel isolinesScaleAppearancePanel;
    private javax.swing.JLabel isolinesScaleIntervalLabel;
    private ika.gui.NumberField isolinesScaleIntervalNumberField;
    private javax.swing.JLabel isolinesScaleLabel;
    private javax.swing.JCheckBox isolinesScaleVisibleCheckBox;
    private javax.swing.JLabel isolinesSmoothnessLabel;
    private javax.swing.JPanel isoscalesPanel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSeparator jSeparator11;
    private javax.swing.JSeparator jSeparator12;
    private javax.swing.JPopupMenu.Separator jSeparator14;
    private javax.swing.JSeparator jSeparator15;
    private javax.swing.JSeparator jSeparator16;
    private javax.swing.JSeparator jSeparator18;
    private javax.swing.JToolBar.Separator jSeparator19;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator20;
    private javax.swing.JSeparator jSeparator21;
    private javax.swing.JSeparator jSeparator23;
    private javax.swing.JPopupMenu.Separator jSeparator25;
    private javax.swing.JSeparator jSeparator4;
    private javax.swing.JSeparator jSeparator5;
    private javax.swing.JSeparator jSeparator6;
    private javax.swing.JSeparator jSeparator8;
    private javax.swing.JSeparator jSeparator9;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JButton linkNameButton;
    private javax.swing.JLabel linkNameLabel;
    private javax.swing.JPanel linkNamePanel;
    private javax.swing.JToggleButton linkToggleButton;
    private javax.swing.JMenuItem linkedPointsColorMenuItem;
    private javax.swing.JPanel linksPanel;
    private ika.gui.LocalScaleRotationInfoPanel localScaleRotationInfoPanel;
    private javax.swing.JFormattedTextField longitudeFormattedTextField;
    private javax.swing.JSlider longitudeSlider;
    private ch.ethz.karto.gui.MapComponent mapComponent;
    private javax.swing.JMenuItem mapSizeMenuItem;
    private javax.swing.JMenu mapsMenu;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JLabel nameLabel;
    private javax.swing.JToolBar navigationToolBar;
    private ika.gui.MapComponent newMapComponent;
    private javax.swing.JLabel newMapCoordsTitleLabel;
    private javax.swing.JLabel newMapLabel;
    private javax.swing.JPanel newMapLabelPanel;
    private javax.swing.JPanel newMapPanel;
    private javax.swing.JMenuItem newProjectMenuItem;
    private ika.gui.MapComponent oldMapComponent;
    private javax.swing.JMenu oldMapCoordinateDisplayUnitMenu;
    private javax.swing.JLabel oldMapCoordsTitleLabel;
    private javax.swing.ButtonGroup oldMapDisplayUnitButtonGroup;
    private javax.swing.JLabel oldMapLabel;
    private javax.swing.JPanel oldMapLabelPanel;
    private javax.swing.JPanel oldMapPanel;
    private javax.swing.JCheckBoxMenuItem oldUnitCmCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem oldUnitInchCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem oldUnitMCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem oldUnitPxCheckBoxMenuItem;
    private javax.swing.JMenuItem openProjectMenuItem;
    private javax.swing.JMenu openRecentMenu;
    private javax.swing.JLabel osmCopyrightLabel;
    private javax.swing.JToggleButton panPointsToggleButton;
    private javax.swing.JMenuItem pasteMenuItem;
    private javax.swing.JToggleButton penToggleButton;
    private javax.swing.JMenuItem placePointMenuItem;
    private javax.swing.JLabel pointNewXLabel;
    private javax.swing.JLabel pointNewYLabel;
    private javax.swing.JLabel pointOldXLabel;
    private javax.swing.JLabel pointOldYLabel;
    private javax.swing.JMenuItem pointSymbol1MenuItem;
    private javax.swing.JMenuItem pointSymbol2MenuItem;
    private javax.swing.JMenuItem pointSymbol3MenuItem;
    private javax.swing.JMenuItem pointSymbol4MenuItem;
    private javax.swing.JMenuItem pointSymbol5MenuItem;
    private javax.swing.JMenuItem pointSymbol6MenuItem;
    private javax.swing.JMenu pointSymbolMenu;
    private javax.swing.JPanel pointTablePanel;
    private javax.swing.JTable pointsTable;
    private javax.swing.JToolBar pointsToolBar;
    private javax.swing.JPanel positionPanel;
    private javax.swing.JComboBox projectionComboBox;
    private javax.swing.JTextArea projectionDescriptionTextArea;
    private javax.swing.JCheckBox projectionLiveUpdateCheckBox;
    private javax.swing.JLabel projectionMeanDistanceLabel;
    private javax.swing.JLabel projectionMeanLongitudeLabel;
    private javax.swing.JMenuItem projectionMenuItem;
    private javax.swing.JButton projectionOptionsButton;
    private javax.swing.JPanel projectionPanel;
    private javax.swing.JPopupMenu.Separator projectionSeparator;
    private javax.swing.JToggleButton rectangularSelectionToggleButton;
    private javax.swing.JMenuItem redoMenuItem;
    private javax.swing.JMenuItem removeNewRasterImageMenuItem;
    private javax.swing.JMenuItem removeOSMMenuItem;
    private javax.swing.JMenuItem removeOldRasterImageMenuItem;
    private javax.swing.JMenu robustEstimatorMenu;
    private javax.swing.JMenuItem saveProjectAsMenuItem;
    private javax.swing.JMenuItem saveProjectMenuItem;
    private javax.swing.JMenuItem selectPointsMenuItem;
    private javax.swing.JMenuItem selectUnlinkedPointsMenuItem;
    private javax.swing.JMenuItem setHampelEstimatorParametersMenuItem;
    private javax.swing.JMenuItem setHuberEstimatorParametersMenuItem;
    private javax.swing.JToggleButton setPointToggleButton;
    private javax.swing.JMenuItem setVEstimatorParametersMenuItem;
    private javax.swing.JButton showAllButton;
    private javax.swing.JMenuItem showAllMenuItem;
    private javax.swing.JMenuItem showAllNewMenuItem;
    private javax.swing.JMenuItem showAllOldMenuItem;
    private javax.swing.ButtonGroup showErrorInMapButtonGroup;
    private javax.swing.JCheckBoxMenuItem showErrorInNewMapCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem showErrorInOldMapCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem showNewCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem showOSMGraticuleCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem showOSMPolarCirclesCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem showOSMTropicsCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem showOldCheckBoxMenuItem;
    private javax.swing.JMenuItem showPointListMenuItem;
    private javax.swing.JCheckBoxMenuItem showPointsCheckBoxMenuItem;
    private javax.swing.JMenuItem showReportMenuItem;
    private javax.swing.JSplitPane splitPane;
    private javax.swing.ButtonGroup toolBarButtonGroup;
    private javax.swing.JPanel topLeftPanel;
    private javax.swing.JPanel topPanel;
    private javax.swing.ButtonGroup transformationButtonGroup;
    private javax.swing.JPanel transformationInfoPanel;
    private javax.swing.JTextArea transformationInfoTextArea;
    private javax.swing.JLabel transformationLabel;
    private javax.swing.JMenu transformationMenu;
    private javax.swing.JPanel uncertaintyPanel;
    private javax.swing.JMenuItem undoMenuItem;
    private javax.swing.JMenuItem unlinkedPointsColorMenuItem;
    private javax.swing.JCheckBoxMenuItem vEstimatorCheckBoxMenuItem;
    private javax.swing.JLabel vEstimatorELabel;
    private javax.swing.JTextArea vEstimatorETextArea;
    private javax.swing.JTextField vEstimatorETextField;
    private javax.swing.JLabel vEstimatorKLabel;
    private javax.swing.JTextArea vEstimatorKTextArea;
    private javax.swing.JTextField vEstimatorKTextField;
    private javax.swing.JTextArea vEstimatorMinMaxETextArea;
    private javax.swing.JTextArea vEstimatorMinMaxKTextArea;
    private javax.swing.JPanel vEstimatorParametersPanel;
    private javax.swing.JMenu viewMenu;
    private javax.swing.JTabbedPane visualizationTabbedPane;
    private javax.swing.JMenuItem warpMenuItem;
    private javax.swing.JMenu windowMenu;
    private javax.swing.JLabel xLabel;
    private javax.swing.JLabel yLabel;
    private javax.swing.JMenuItem zoomInMenuItem;
    private javax.swing.JToggleButton zoomInToggleButton;
    private javax.swing.JMenuItem zoomOutMenuItem;
    private javax.swing.JToggleButton zoomOutToggleButton;
    // End of variables declaration//GEN-END:variables

    private void showExportError(Exception e) {
        String msg = "The data could not be exported.\n" + e.toString();
        String title = "Export Error";
        javax.swing.JOptionPane.showMessageDialog(this, msg, title,
                javax.swing.JOptionPane.ERROR_MESSAGE, null);
    }

    /**
     * Listener for selection change events in the points table. Adjusts the
     * selection status of the points in the map.
     */
    private class PointsTableSelectionListener implements ListSelectionListener {

        private boolean updating = false;

        public void valueChanged(ListSelectionEvent e) {

            if (e.getValueIsAdjusting() || updating) {
                return;
            }

            if (manager == null || manager.getLinkManager() == null) {
                return;
            }

            // do not update the table with linked points while a map tool
            // is doing something to the points. Reason: if a tools moves the selected
            // points, MainWindow.geoSetChanged method is called, which calls
            // MainWindow.updatePointCoordinatesWindow that fires a tableChanged
            // event on the JTable, which deselects all currently selected rows.
            // As a result this valueChanged handler is called that applies the
            // selection of the table to the GeoObjects, which are in turn
            // deselected. As a result, the dragging by the map tool stops,
            // as there is no more selected object in the map.
            if (oldMapComponent.getMapTool().isChangingGeoObject()
                    || newMapComponent.getMapTool().isChangingGeoObject()) {
                return;
            }

            try {
                updating = true;
                LinkManager linkManager = manager.getLinkManager();
                ListSelectionModel lsm = (ListSelectionModel) e.getSource();

                // Find out which indexes are selected.
                int linkCount = linkManager.getNumberLinks();
                int firstSelected = -1;
                for (int i = 0; i <= linkCount; i++) {
                    boolean selected = lsm.isSelectedIndex(i);
                    if (selected && firstSelected == -1) {
                        firstSelected = i;
                    }
                    linkManager.setLinkSelection(i, selected);
                }

                // make sure at least on selected point is visible in the table
                if (firstSelected >= 0) {
                    pointsTable.scrollRectToVisible(pointsTable.getCellRect(firstSelected, 0, false));
                }

                MainWindow.this.updateLinkGUI();
                MainWindow.this.updateAllMenus();

                MainWindow.this.oldMapComponent.repaint();
                MainWindow.this.newMapComponent.repaint();

            } finally {
                updating = false;
            }
        }
    }

    /**
     * custom table model for the points table. Makes the link between the
     * LinkManager and the JTable displaying the points.
     */
    private class PointsTableModel extends javax.swing.table.DefaultTableModel {

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public int getRowCount() {

            if (manager == null || manager.getLinkManager() == null) {
                return 0;
            }
            return manager.getLinkManager().getNumberLinks();
        }

        @Override
        public int getColumnCount() {
            return 5;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {

            final int NUMBER_LENGTH = 20;
            final int NBR_DECIMALS = 6;

            if (manager == null || manager.getLinkManager() == null) {
                return null;
            }

            Link link = manager.getLinkManager().getLink(rowIndex);
            if (link == null) {
                return null;
            }

            switch (columnIndex) {
                case 0:
                    return link.getName();
                case 1:
                    double x = link.getPtOld().getX();
                    return NumberFormatter.format(x, NUMBER_LENGTH, NBR_DECIMALS);
                case 2:
                    double y = link.getPtOld().getY();
                    return NumberFormatter.format(y, NUMBER_LENGTH, NBR_DECIMALS);
                case 3:
                    x = link.getPtNew().getX();
                    return NumberFormatter.format(x, NUMBER_LENGTH, NBR_DECIMALS);
                case 4:
                    y = link.getPtNew().getY();
                    return NumberFormatter.format(y, NUMBER_LENGTH, NBR_DECIMALS);
                default:
                    return null;
            }

        }

        @Override
        public String getColumnName(int column) {
            switch (column) {
                case 0:
                    return "Link Name";
                case 1:
                    return "X Old Map [m]";
                case 2:
                    return "Y Old Map [m]";
                case 3:
                    return "X New Map [m]";
                case 4:
                    return "Y New Map [m]";
                default:
                    return null;
            }
        }
    }
}
