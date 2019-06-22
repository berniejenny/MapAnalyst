/*
 * ApplicationInfo.java
 *
 * Created on March 29, 2005, 11:39 PM
 */

package ika.mapanalyst;

import java.text.DateFormat;
import java.util.Locale;

/**
 * Information about this application.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class ApplicationInfo {
    
    /**
     * Returns the name of this application.
     * @return The name of this application.
     */
    public static String getApplicationName() {
        return "MapAnalyst";
    }
    
    /**
     * Returns a string containing version information about this application.
     * @return The version of this application.
     */
    public static String getApplicationVersion() {
        return "1.3.35";
    }
    
    /**
     * Returns an icon for this application.
     * @return The icon of this application.
     */
    public static javax.swing.Icon getApplicationIcon() {
        return ika.icons.IconLoader.createImageIcon("logo48x48.gif", null);
    }
    
    /**
     * Returns a copyright string for this application.
     * @return The copyright description of this application.
     */
    public static String getCopyright() {
        String info = "<html><center>";
        info += "MapAnalyst is Copyright 2005-2019 by<br>" +
                "Bernhard Jenny, Monash University, Australia<br><br>";
        info += "This program is free software;<br>";
        info += "you can redistribute it and/or modify it <br>" +
                "under the terms of the GNU General Public <br>" +
                "License version 2 as published by the<br>" +
                "Free Software Foundation.<br><br>";
        info += "</center></html>";
        return info;
    }
     
    /**
     * Returns the home page (a html web address) for this application.
     * @return The home page of this application.
     */
    public static String getHomepage() {
        return "http://mapanalyst.org/";
    }
    
    /**
     * Returns the file extension for documents created by this application.
     * @return The file extension.
     */
    public static String getDocumentExtension() {
        return "pma";
    }
    
    /**
     * Returns the current time and date in a formatted string.
     * @return The string containing time and date.
     */
    public static String getCurrentTimeAndDate() {
        return java.text.DateFormat.getDateTimeInstance(
                DateFormat.LONG, DateFormat.LONG,
                Locale.US) // require US locale since the GUI is in English and 
                // to avoid issues with missing glyphs for certain scripts (e.g. cyrillic)
                .format(new java.util.Date());
    }
}
