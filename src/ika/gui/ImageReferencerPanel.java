/*
 * ImageReferencerPanel.java
 *
 * Created on July 1, 2005, 11:47 AM
 */

package ika.gui;

import ika.geo.*;
import ika.map.tools.*;
import ika.utils.*;
import ika.transformation.*;
import java.awt.geom.AffineTransform;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 *
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class ImageReferencerPanel extends javax.swing.JPanel
        implements GeoSetChangeListener,
        GeoSetSelectionChangeController,
        GeoSetSelectionChangeListener {
    
    private GeoSet imageGeoSet;
    private GeoSet pointGeoSet;
    private GeoSet destinationGeoSet;
    private Transformation transformation;
    
    /** Creates new form ImageReferencerPanel */
    public ImageReferencerPanel() {
        initComponents();
        
        this.imageGeoSet = new GeoSet();
        this.pointGeoSet = new GeoSet();
        this.pointGeoSet.addGeoSetChangeListener(this);
        this.pointGeoSet.addGeoSetSelectionChangeListener(this);
        this.pointGeoSet.addGeoSetSelectionChangeController(this);
        
        mapComponent.addGeoObject(this.imageGeoSet);
        mapComponent.addGeoObject(this.pointGeoSet);
        
        MapTool mapTool = new SingleSelectionPointSetterTool(mapComponent);
        mapTool.setDestinationGeoSet (pointGeoSet);
        this.mapComponent.setMapTool(mapTool);
        
        this.coordinateInfoPanel.registerWithMapComponent(this.mapComponent);
        
        enableTextFields(false);
        updateDeleteButton();
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        toolBarButtonGroup = new javax.swing.ButtonGroup();
        topPanel = new javax.swing.JPanel();
        pointsToolBar = new javax.swing.JToolBar();
        setPointToggleButton = new javax.swing.JToggleButton();
        selectPointToggleButton = new javax.swing.JToggleButton();
        panPointsToggleButton = new javax.swing.JToggleButton();
        navigationToolBar = new javax.swing.JToolBar();
        zoomInToggleButton = new javax.swing.JToggleButton();
        zoomOutToggleButton = new javax.swing.JToggleButton();
        handToggleButton = new javax.swing.JToggleButton();
        distanceToggleButton = new javax.swing.JToggleButton();
        showAllButton = new javax.swing.JButton();
        infoToolBar = new javax.swing.JToolBar();
        coordinateInfoPanel = new ika.gui.CoordinateInfoPanel();
        southPanel = new javax.swing.JPanel();
        coordinatesPanel = new javax.swing.JPanel();
        xLabel = new javax.swing.JLabel();
        yLabel = new javax.swing.JLabel();
        xCoordTextField = new javax.swing.JTextField();
        yCoordTextField = new javax.swing.JTextField();
        deleteButton = new javax.swing.JButton();
        finishButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();
        mapComponent = new ika.gui.MapComponent();

        setLayout(new java.awt.BorderLayout());

        topPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

        toolBarButtonGroup.add(setPointToggleButton);
        setPointToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/SetPoint16x16.gif")));
        setPointToggleButton.setSelected(true);
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

        toolBarButtonGroup.add(selectPointToggleButton);
        selectPointToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/SelectPoints16x16.gif")));
        selectPointToggleButton.setToolTipText("Select Points");
        selectPointToggleButton.setMaximumSize(new java.awt.Dimension(27, 27));
        selectPointToggleButton.setMinimumSize(new java.awt.Dimension(27, 27));
        selectPointToggleButton.setPreferredSize(new java.awt.Dimension(27, 27));
        selectPointToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectPointToggleButtonActionPerformed(evt);
            }
        });

        pointsToolBar.add(selectPointToggleButton);

        toolBarButtonGroup.add(panPointsToggleButton);
        panPointsToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/MovePoint16x16.gif")));
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

        topPanel.add(pointsToolBar);

        navigationToolBar.setMaximumSize(null);
        navigationToolBar.setMinimumSize(null);
        navigationToolBar.setPreferredSize(null);
        toolBarButtonGroup.add(zoomInToggleButton);
        zoomInToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/ZoomIn16x16.gif")));
        zoomInToggleButton.setPreferredSize(new java.awt.Dimension(24, 24));
        zoomInToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomInToggleButtonActionPerformed(evt);
            }
        });

        navigationToolBar.add(zoomInToggleButton);

        toolBarButtonGroup.add(zoomOutToggleButton);
        zoomOutToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/ZoomOut16x16.gif")));
        zoomOutToggleButton.setPreferredSize(new java.awt.Dimension(24, 24));
        zoomOutToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomOutToggleButtonActionPerformed(evt);
            }
        });

        navigationToolBar.add(zoomOutToggleButton);

        toolBarButtonGroup.add(handToggleButton);
        handToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/Hand16x16.gif")));
        handToggleButton.setPreferredSize(new java.awt.Dimension(24, 24));
        handToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                handToggleButtonActionPerformed(evt);
            }
        });

        navigationToolBar.add(handToggleButton);

        toolBarButtonGroup.add(distanceToggleButton);
        distanceToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/Ruler16x16.gif")));
        distanceToggleButton.setPreferredSize(new java.awt.Dimension(24, 24));
        distanceToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                distanceToggleButtonActionPerformed(evt);
            }
        });

        navigationToolBar.add(distanceToggleButton);

        showAllButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/ShowAll16x16.gif")));
        showAllButton.setToolTipText("Show All");
        showAllButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showAllButtonActionPerformed(evt);
            }
        });

        navigationToolBar.add(showAllButton);

        topPanel.add(navigationToolBar);

        infoToolBar.add(coordinateInfoPanel);

        topPanel.add(infoToolBar);

        add(topPanel, java.awt.BorderLayout.NORTH);

        southPanel.setLayout(new java.awt.GridBagLayout());

        coordinatesPanel.setLayout(new java.awt.GridBagLayout());

        coordinatesPanel.setFocusCycleRoot(true);
        xLabel.setText("Horizontal:");
        xLabel.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        coordinatesPanel.add(xLabel, gridBagConstraints);

        yLabel.setText("Vertical:");
        yLabel.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        coordinatesPanel.add(yLabel, gridBagConstraints);

        xCoordTextField.setPreferredSize(new java.awt.Dimension(120, 22));
        coordinatesPanel.add(xCoordTextField, new java.awt.GridBagConstraints());

        yCoordTextField.setPreferredSize(new java.awt.Dimension(120, 22));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        coordinatesPanel.add(yCoordTextField, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(16, 19, 0, 19);
        southPanel.add(coordinatesPanel, gridBagConstraints);

        deleteButton.setText("Delete Point");
        deleteButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteButtonActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.SOUTH;
        southPanel.add(deleteButton, gridBagConstraints);

        finishButton.setText("Finish");
        finishButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                finishButtonActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(8, 16, 8, 16);
        southPanel.add(finishButton, gridBagConstraints);

        cancelButton.setText("Cancel");
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(8, 40, 8, 0);
        southPanel.add(cancelButton, gridBagConstraints);

        add(southPanel, java.awt.BorderLayout.SOUTH);

        add(mapComponent, java.awt.BorderLayout.CENTER);

    }
    // </editor-fold>//GEN-END:initComponents
    
    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        if (this.askUserForAborting()) {
            Component comp = (Component)SwingUtilities.getRoot(mapComponent);
            comp.setVisible(false);
        }
    }//GEN-LAST:event_cancelButtonActionPerformed
    
    private void finishButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_finishButtonActionPerformed
        
        Component comp = (Component)SwingUtilities.getRoot(mapComponent);
        comp.setVisible(false);
        
        if (this.destinationGeoSet != null) {
            GeoImage geoImage = (GeoImage)this.imageGeoSet.getGeoObject(0);
            
            GeoPath testPath = new GeoPath();
            testPath.rectangle(geoImage.getBounds2D());
            
            
            geoImage.setSelectable(true);
            java.awt.image.BufferedImage image = geoImage.getBufferedImage();
            AffineTransform affineTransform = new AffineTransform();
            //this.transformation.getRotation()
            
            //affineTransform.scale (2, 2);
            //affineTransform.translate(500, -200);
            affineTransform.rotate(Math.PI / 4, image.getWidth()/2, image.getHeight()/2);
            //affineTransform.translate(0, -image.getHeight());
            //affineTransform.scale(1, -1);
            
            geoImage.transform(affineTransform);
            //geoImage.transform(transformation.getAffineTransform());
            
            this.destinationGeoSet.addGeoObject(geoImage);
            this.destinationGeoSet.addGeoObject(testPath);
        }
    }//GEN-LAST:event_finishButtonActionPerformed
    
    private void deleteButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteButtonActionPerformed
        this.mapComponent.removeSelectedGeoObjects();
    }//GEN-LAST:event_deleteButtonActionPerformed
    
    private void showAllButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showAllButtonActionPerformed
        this.mapComponent.showAll();
    }//GEN-LAST:event_showAllButtonActionPerformed
    
    private void distanceToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_distanceToggleButtonActionPerformed
        MeasureTool measureTool = new MeasureTool(mapComponent, null); // FIXME null parameter
        measureTool.addMeasureToolListener(this.coordinateInfoPanel);
        this.mapComponent.setMapTool(measureTool);
    }//GEN-LAST:event_distanceToggleButtonActionPerformed
    
    private void handToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_handToggleButtonActionPerformed
        this.mapComponent.setMapTool(new PanTool(this.mapComponent));
    }//GEN-LAST:event_handToggleButtonActionPerformed
    
    private void zoomOutToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomOutToggleButtonActionPerformed
        this.mapComponent.setMapTool(new ZoomOutTool(this.mapComponent));
    }//GEN-LAST:event_zoomOutToggleButtonActionPerformed
    
    private void zoomInToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomInToggleButtonActionPerformed
        this.mapComponent.setMapTool(new ZoomInTool(this.mapComponent));
    }//GEN-LAST:event_zoomInToggleButtonActionPerformed
    
    private void panPointsToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_panPointsToggleButtonActionPerformed
        this.mapComponent.setMapTool(new MoverTool(this.mapComponent));
    }//GEN-LAST:event_panPointsToggleButtonActionPerformed
    
    private void selectPointToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectPointToggleButtonActionPerformed
        this.mapComponent.setMapTool(new SelectionTool(this.mapComponent));
    }//GEN-LAST:event_selectPointToggleButtonActionPerformed
    
    private void setPointToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setPointToggleButtonActionPerformed
        MapTool mapTool = new SingleSelectionPointSetterTool(this.mapComponent, null);
        mapTool.setDestinationGeoSet(this.pointGeoSet);
        this.mapComponent.setMapTool(mapTool);
    }//GEN-LAST:event_setPointToggleButtonActionPerformed
    
    public boolean askUserForAborting() {
        Object[] options = { "Continue", "Abort" };
        int res = javax.swing.JOptionPane.showOptionDialog(null,
                "Are you sure to abort image referencing?",
                "Aborting Image Referencing",
                javax.swing.JOptionPane.DEFAULT_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE,
                null, options, options[0]);
        return res == 1;
    }
    
    private void computeTransformation() {
        
        this.transformation = new TransformationHelmert();
        
        int numberOfPoints = this.pointGeoSet.getNumberOfChildren();
        double[][] destSet = new double [numberOfPoints][2];
        double[][] sourceSet = new double [numberOfPoints][2];
        
        int numberOfValidPoints = 0;
        for (int i = 0; i < numberOfPoints; i++) {
            GeoObject obj = pointGeoSet.getGeoObject(i);
            if (obj instanceof GeoControlPoint) {
                GeoControlPoint pt = (GeoControlPoint)obj;
                
                double srcX = pt.getX();
                double srcY = pt.getY();
                double dstX = pt.getDestX();
                double dstY = pt.getDestY();
                if (isValid(srcX) && isValid(srcY)
                && isValid(dstX) && isValid(dstY)) {
                    destSet[i][0] = dstX;
                    destSet[i][1] = dstY;
                    sourceSet[i][0] = srcX;
                    sourceSet[i][1] = srcY;
                    numberOfValidPoints++;
                }
            }
        }
        if (numberOfValidPoints < numberOfPoints) {
            int nbrRowsToRemove = numberOfPoints - numberOfValidPoints;
            destSet = MatrixUtils.removeRows(destSet, nbrRowsToRemove);
            sourceSet = MatrixUtils.removeRows(sourceSet, nbrRowsToRemove);
        }
        
        this.transformation.init(destSet, sourceSet);
        
        System.out.println(this.transformation.getReport(false));
    }
    
    private boolean isValid(double d) {
        return !Double.isInfinite(d) && !Double.isNaN(d);
    }
    
    private GeoControlPoint getSingleSelectedGeoControlPoint() {
        GeoObject singleSelectedObj = pointGeoSet.getSingleSelectedGeoObject();
        if (singleSelectedObj != null && singleSelectedObj instanceof GeoControlPoint)
            return (GeoControlPoint)singleSelectedObj;
        return null;
    }
    
    private boolean updatePointFromTextFields() {
        GeoControlPoint pt = getSingleSelectedGeoControlPoint();
        if (pt != null) {
            double x = Double.NaN;
            double y = Double.NaN;
            
            // parseDouble throws an exception if text is not a valid number.
            try {
                x = Double.parseDouble(this.xCoordTextField.getText());
            } catch (Exception e) {
                x = Double.NaN;
            }
            try {
                y = Double.parseDouble(this.yCoordTextField.getText());
            } catch (Exception e) {
                y = Double.NaN;
            }
            
            pt.setDestX(x);
            pt.setDestY(y);
            return isValid(x) && isValid(y);
        }
        return false;
    }
    
    public void updateTextFieldsFromPoint() {
        GeoControlPoint pt = getSingleSelectedGeoControlPoint();
        if (pt != null) {
            enableTextFields(true);
            
            double x = pt.getDestX();
            double y = pt.getDestY();
            if (isValid(x) && isValid(y)) {
                this.xCoordTextField.setText(Double.toString(x));
                this.yCoordTextField.setText(Double.toString(y));
                return;
            }
        } else {
            enableTextFields(false);
        }
        this.xCoordTextField.setText(null);
        this.yCoordTextField.setText(null);
    }
    
    private void enableTextFields(boolean enabled) {
        
        this.xLabel.setEnabled(enabled);
        this.yLabel.setEnabled(enabled);
        this.xCoordTextField.setEnabled(enabled);
        this.yCoordTextField.setEnabled(enabled);
        if (enabled) {
            this.xCoordTextField.setFocusable(true);
            this.yCoordTextField.setFocusable(true);
            this.xCoordTextField.requestFocus();
        } else {
            this.xCoordTextField.setFocusable(false);
            this.yCoordTextField.setFocusable(false);
            this.xCoordTextField.setText("");
            this.yCoordTextField.setText("");
        }
    }
    
    public void geoSetChanged(GeoSet geoSet) {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                updateTextFieldsFromPoint();
                updateDeleteButton();
                computeTransformation();
            }
        });
    }
    
    public void geoSetSelectionChanged(GeoSet geoSet) {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                updateTextFieldsFromPoint();
                updateDeleteButton();
                computeTransformation();
            }
        });
    }
    
    private void updateDeleteButton() {
        if (pointGeoSet != null) {
            this.finishButton.setEnabled(pointGeoSet.hasSelectedGeoObjects());
        }
    }
    
    public boolean allowSelectionChange(GeoSet geoSet) {
        GeoControlPoint pt = getSingleSelectedGeoControlPoint();
        if (pt != null) {
            final boolean validPoint = updatePointFromTextFields();
            return validPoint;
        }
        // no point selected, a point can be selected.
        return true;
    }
    
    public void setImage(GeoImage geoImage) {
        if (geoImage == null)
            throw new IllegalArgumentException();
        
        geoImage.resetGeoreference();
        this.imageGeoSet.addGeoObject(geoImage);
        this.mapComponent.showAll();
    }
    
    public void setDestinationGeoSet(GeoSet destinationGeoSet) {
        this.destinationGeoSet = destinationGeoSet;
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private ika.gui.CoordinateInfoPanel coordinateInfoPanel;
    private javax.swing.JPanel coordinatesPanel;
    private javax.swing.JButton deleteButton;
    private javax.swing.JToggleButton distanceToggleButton;
    private javax.swing.JButton finishButton;
    private javax.swing.JToggleButton handToggleButton;
    private javax.swing.JToolBar infoToolBar;
    private ika.gui.MapComponent mapComponent;
    private javax.swing.JToolBar navigationToolBar;
    private javax.swing.JToggleButton panPointsToggleButton;
    private javax.swing.JToolBar pointsToolBar;
    private javax.swing.JToggleButton selectPointToggleButton;
    private javax.swing.JToggleButton setPointToggleButton;
    private javax.swing.JButton showAllButton;
    private javax.swing.JPanel southPanel;
    private javax.swing.ButtonGroup toolBarButtonGroup;
    private javax.swing.JPanel topPanel;
    private javax.swing.JTextField xCoordTextField;
    private javax.swing.JLabel xLabel;
    private javax.swing.JTextField yCoordTextField;
    private javax.swing.JLabel yLabel;
    private javax.swing.JToggleButton zoomInToggleButton;
    private javax.swing.JToggleButton zoomOutToggleButton;
    // End of variables declaration//GEN-END:variables
    
}
