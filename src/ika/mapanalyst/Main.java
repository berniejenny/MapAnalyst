/*
 * Main.java
 *
 * Created on November 1, 2005, 10:19 AM
 *
 */
package ika.mapanalyst;

import javax.swing.*;
import ika.gui.*;
import ika.utils.ErrorDialog;
import ika.utils.TextWindow;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main entry point for MapAnalyst.
 * 
 * @author Bernie Jenny, Faculty of Information Technology, Monash University
 */
public class Main {

    /**
     * main routine for the application.
     * @param args the command line arguments
     */
    public static void main(String args[]) {

        // on Mac OS X: take the menu bar out of the window and put it on top
        // of the main screen.
        if (ika.utils.Sys.isMacOSX()) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
        }

        try {
            // use the standard look and feel            
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Throwable ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        // set icon for JOptionPane dialogs. This is done automatically on Mac 10.5 and later
        Main.setOptionPaneIcons("logo48x48.gif");

        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                try {
                    if (ika.utils.Sys.isMacOSX()) {
                        MacWindowsManager.emptyWindow = new MacWindowsManager();
                    }
                    MainWindow.newProject();
                } catch (Exception exc) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, exc);
                    String msg = "An error occured.";
                    String title = "MapAnalyst Error";
                    ErrorDialog.showErrorDialog(msg, title, exc, null);

                    StringWriter sw = new StringWriter();
                    sw.write(title);
                    sw.write(System.getProperty("line.separator"));
                    sw.write("Please send this report to the author of MapAnalyst.");
                    sw.write(System.getProperty("line.separator"));
                    PrintWriter pw = new PrintWriter(sw);
                    exc.printStackTrace(pw);
                    pw.flush();
                    new TextWindow(null, true, true, sw.toString(), title);
                }
            }
        });
    }

    /**
     * Changes the icon displayed in JOptionPane dialogs to the passed icon.
     * Error, information, question and warning dialogs will show this icon.
     * This will also replace the icon in ProgressMonitor dialogs.
     */
    private static void setOptionPaneIcons(String iconName) {
        String iconPath = "/ika/icons/" + iconName;
        LookAndFeel lf = UIManager.getLookAndFeel();
        if (lf != null) {
            Class iconBaseClass = lf.getClass();
            Object appIcon = LookAndFeel.makeIcon(iconBaseClass, iconPath);
            UIManager.put("OptionPane.errorIcon", appIcon);
            UIManager.put("OptionPane.informationIcon", appIcon);
            UIManager.put("OptionPane.questionIcon", appIcon);
            UIManager.put("OptionPane.warningIcon", appIcon);
        }
    }
}
