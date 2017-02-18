package ika.mapanalyst;

import java.io.*;
import ika.geo.*;

public class Link implements Serializable {
    
    private static final long serialVersionUID = -4482453568064537680L;
    
    private GeoPoint ptOld;
    
    private GeoPoint ptNew;
    
    private String name;
    
    /**
     * Constructs a link with name and the specified points for the old and
     * the new map.
     */
    public Link(GeoPoint ptOld, GeoPoint ptNew, String name) {
        this.ptOld = ptOld;
        this.ptNew = ptNew;
        this.setName(name);
    }
    
    /**
     * Returns the linked control point from the old map.
     * @return the linked control point from the old map
     */
    public GeoPoint getPtOld(){
        return this.ptOld;
    }
    
    /**
     * Returns the linked control point form the new map.
     * @return the linked control point from the new map
     */
    public GeoPoint getPtNew(){
        return this.ptNew;
    }
    
    /**
     * Sets the linkname.
     * @param name the linkname
     */
    public void setName(String name) {
        this.name = name;
        if (this.ptOld != null)
            this.ptOld.setName(name);
        if (this.ptNew != null)
            this.ptNew.setName(name);
    }
    
    public String getName() {
        return this.name;
    }
    
    public String toString() {
        
        StringBuffer str = new StringBuffer(128);
        str.append("name: ");
        str.append(this.name);
        str.append("\n");
        str.append("old point: \n");
        str.append("x: " + this.ptOld.getX() + "\n");
        str.append("y: " + this.ptOld.getY() + "\n");
        str.append("new point: \n");
        str.append("x: " + this.ptNew.getX() + "\n");
        str.append("y: " + this.ptNew.getY() + "\n");
        return str.toString();
    }
    
    public void setSelected(boolean selected) {
        this.ptOld.setSelected(selected);
        this.ptNew.setSelected(selected);
    }
    
    public boolean isSelected() {
        return this.ptOld.isSelected()
        || this.ptNew.isSelected();
    }
    
    public void setPointSymbol(PointSymbol pointSymbol) {
        ptOld.setPointSymbol(pointSymbol);
        ptNew.setPointSymbol(pointSymbol);
    }
}