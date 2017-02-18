/*
 * FileUtil.java
 *
 * Created on April 1, 2005, 11:31 AM
 */

package ika.utils;

import java.io.*;
import java.awt.*;

/**
 * FileUtils - file related utility methods.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class FileUtils {
    
    /**
     * Makes sure that a given name of a file has a certain file extension.<br>
     * Existing file extension that are different from the required one, are not
     * removed, nor is the file name altered in any other way.
     * @param fileName The name of the file.
     * @param ext The extension of the file that will be appended if necessary.
     * @return The new file name with the required extension.
     */
    public static String forceFileNameExtension(String fileName, String ext) {
        String fileNameLower = fileName.toLowerCase();
        String extLower = ext.toLowerCase();
        
        // test if the fileName has the required extension
        if(!fileNameLower.endsWith("." + extLower)) {
            
            // fileName has wrong extension: add an extension
            
            if (!fileNameLower.endsWith("."))  // add separating dot if required
                fileName = fileName.concat(".");
            fileName = fileName.concat(ext);   // add extension
            
            // the fileName was just changed: test if there exists a file with the same name
            if (new File(fileName).exists()) {
                javax.swing.Icon icon = ika.mapanalyst.ApplicationInfo.getApplicationIcon();
                String msg = "The file \"" + fileName + "\" already exists.\n" +
                        "Please try again and add the extension \"." + ext + "\".";
                String title = "File Already Exists";
                javax.swing.JOptionPane.showMessageDialog(null, msg, title,
                        javax.swing.JOptionPane.ERROR_MESSAGE, icon);
                return null;
            }
        } else
            fileName = new String(fileName);
        return fileName;
    }
    
    public static String getFileExtension(String fileName) {
        final int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1)
            return new String();
        return fileName.substring(dotIndex + 1);
    }
    
    public static String replaceExtension(String fileName, String extension) {
        final int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1)
            return fileName + "." + extension;
        return fileName.substring(0, dotIndex + 1) + extension;
    }
    
    /**
     * Removes the path to the parent folder and also the extension of a file path.
     * @return The name of the file without the path to its parent folder and
     * without the file extension.
     */
    public static String cutParentPathAndExtension(String fileName) {
        
        // cut the extension
        final int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex != -1)
            fileName = fileName.substring(0, dotIndex);
        
        // cut the path to the parent folder
        String pathSeparator = System.getProperty("file.separator");
        final int pathSeparatorIndex = fileName.lastIndexOf(pathSeparator);
        if (pathSeparatorIndex != -1)
            fileName = fileName.substring(pathSeparatorIndex + 1, fileName.length());
        
        return fileName;
    }
    
    /**
     * Ask the user for a file to load or to write to. Uses the awt FileDialog and not
     * the swing FileChoser, which should not be used, since it does not integrate
     * into the look and feel of the platform (independently of the operating system).
     * @param frame A Frame for which to display the dialog. Cannot be null.
     * @param message A message that will be displayed in the dialog.
     * @param load Pass true if an existing file for reading should be selected. Pass false if a new
     * file for writing should be specified.
     * @return A path to the file, including the file name.
     */
    public static String askFile(java.awt.Frame frame, String message, boolean load) {
        return askFile(frame, null, message, load);
    }

    public static String askFile(java.awt.Frame frame, Dialog dialog, String message, boolean load) {
        final int flag = load ? FileDialog.LOAD : FileDialog.SAVE;

        // build dummy Frame if needed.
        if (frame == null)
            frame = new Frame();

        FileDialog fd;
        if (frame != null) {
            fd = new FileDialog(frame, message, flag);
        } else {
            fd = new FileDialog(dialog, message, flag);
        }
        
        //fd.setDirectory(defDir);
        fd.setVisible(true);
        String fileName = fd.getFile();
        String directory = fd.getDirectory();
        if (fileName == null || directory == null)
            return null;
        return directory + fileName;
    }
}
