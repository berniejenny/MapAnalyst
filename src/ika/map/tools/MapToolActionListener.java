/*
 * MapToolActionListener.java
 *
 * Created on August 28, 2005, 3:49 PM
 *
 */

package ika.map.tools;
import ika.gui.*;

/**
 *
 * @author jenny
 */
public interface MapToolActionListener {
    
    public void mapToolActionPerformed (
            MapTool mapTool, 
            MapComponent mapComponent,
            String description);
}
