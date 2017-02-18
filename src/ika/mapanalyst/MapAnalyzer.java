package ika.mapanalyst;

import java.io.*;
import ika.geo.*;

public abstract class MapAnalyzer implements Serializable {
    
    private static final long serialVersionUID = 3190984104408150328L;
    
    /* The resulting graphics for the old map has to be added to oldGeoSet */
    protected GeoSet oldGeoSet;
    
    /* The resulting graphics for the new map has to be added to newGeoSet */
    protected GeoSet newGeoSet;

    protected transient VisualizationParameters params;

    public MapAnalyzer() {
        this.oldGeoSet = new GeoSet();
        this.oldGeoSet.setName(this.getName() + " old");
        this.newGeoSet = new GeoSet();
        this.newGeoSet.setName(this.getName() + " new");
    }
    
    abstract public String getName();

    /**
     * Analyze a map and store the visual result.
     * Derived classes should throw a MapAnalyzerException, if the visualization
     * cannot be generated.
     * @param params
     */
    abstract public void analyzeMap ()
            throws MapAnalyzerException;

    public boolean isVisible() {
        return this.oldGeoSet.isVisible();
    }
    
    public void setVisible(boolean visible) {
        this.oldGeoSet.setVisible(visible);
        this.newGeoSet.setVisible(visible);
    }
    
    public void clearAll(){
        this.newGeoSet.removeAllGeoObjects();
        this.oldGeoSet.removeAllGeoObjects();
    }
    
    public GeoSet getOldGeoSet() {
        return oldGeoSet;
    }
    
    public GeoSet getNewGeoSet() {
        return newGeoSet;
    }

    /**
     * @param params the params to set
     */
    public void setVisualizationParameters(VisualizationParameters params) {
        this.params = params;
    }

    public class MapAnalyzerException extends Exception {
        public final String mapAnalyzerName;
        public MapAnalyzerException (String msg, MapAnalyzer mapAnalyzer) {
            super(msg);
            this.mapAnalyzerName = mapAnalyzer.getName();
        }

        public MapAnalyzerException (String msg) {
            super(msg);
            mapAnalyzerName = null;
        }
    }
}