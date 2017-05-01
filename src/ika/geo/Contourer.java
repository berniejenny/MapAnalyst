/*
 * Contourer.java
 *
 * Created on August 15, 2005, 12:06 PM
 *
 */

package ika.geo;

import java.util.ArrayList;

/**
 *
 * @author jenny
 */
public class Contourer {
    
    private GeoGrid geoGrid;
    boolean[][] flags;
    private float interval;
    /**
     * Flag for special treatement for degrees containing degree values 0..360
     * Which cause problems for cells that have values over high-noon.
     */
    private boolean treatDegreeJump = false;
    
    /** Creates a new instance of Contourer */
    public Contourer() {
    }

    public static void toPaths(ArrayList<ArrayList<double[][]>> contours, GeoSet geoSet) {

        for (ArrayList<double[][]> levelCountours : contours) {
            GeoSet levelGeoSet = new GeoSet();
            for (double[][] contour : levelCountours) {
                GeoPath path = new GeoPath();
                path.straightLines(contour, 0, contour.length);
                levelGeoSet.addGeoObject(path);
            }
            geoSet.addGeoObject(levelGeoSet);
        }

    }

    public ArrayList< ArrayList <double[][]> > contour() {

        ArrayList< ArrayList <double[][]> > contours = new ArrayList< ArrayList <double[][]> >();
        float[] minMax = geoGrid.getMinMax();
        float firstContourLevel = (float)Math.ceil(minMax[0]/interval)*interval;
        float lastContourLevel = (float)Math.floor(minMax[1]/interval)*interval;
        int nbrIntervals = (int)((lastContourLevel - firstContourLevel) / interval + 1);
        
        if (treatDegreeJump) {
            ArrayList<double[][]> levelContours = contour(0.f);
            if (levelContours.size() > 1) {
                contours.add(levelContours);
            }
        }
        
        for (int i = 0; i < nbrIntervals; ++i) {
            float contourLevel = firstContourLevel + i * this.interval;
            System.out.println(contourLevel);
            ArrayList<double[][]> levelContours = contour(contourLevel);
            if (levelContours.size() > 1) {
                contours.add(levelContours);
            }
        }

        return contours;

    }
    
    public ArrayList<double[][]> contour(float level) {
        
        final int nbrCellsX = this.geoGrid.getCols() - 1;
        final int nbrCellsY = this.geoGrid.getRows() - 1;
        float[][] grid = this.geoGrid.getGrid();
        double west = this.geoGrid.getWest();
        double north = this.geoGrid.getNorth();
        float cellSize = (float)this.geoGrid.getMeshSize();
        
        for (int i = 0; i < this.flags.length; i++)
            java.util.Arrays.fill(this.flags[i], false);

        ArrayList< double[][] > contours = new ArrayList< double[][] >();
        for (int y = 0; y < nbrCellsY; y++) {
            boolean[] flag_row = flags[y];
            for (int x = 0; x < nbrCellsX; x++) {
                if (flag_row[x] == false) {
                    double[][] contour = this.traceContour(grid, new int[]{x, y}, level, west, north, cellSize);
                    if (contour.length > 1) {
                        contours.add(contour);
                    }
                }
            }
        }

        return contours;

    }
    
    private double[][] traceContour(float[][] grid, int[]xy, float level,
            double west, double north, float cellSize) {
        
        float[] pt = new float[2];
        int[]xy_copy = new int[]{xy[0], xy[1]};
        final int cols = grid[0].length;
        final int rows = grid.length;
        ArrayList<double[]> contour = new ArrayList<double[]>();

        // first trace contour in backward direction
        while (xy_copy[0] >= 0 &&
                xy_copy[0] < cols - 1 &&
                xy_copy[1] >= 0 &&
                xy_copy[1] < rows - 1 &&
                this.next(false, grid, xy_copy, level, west, north, cellSize, pt)) {

            contour.add(0, new double[]{pt[0], pt[1]});
        }
        
        // reset the flag for the passed point xy
        flags[xy[1]][xy[0]] = false;
        
        // then trace contour in forward direction
        while (xy[0] >= 0 &&
                xy[0] < cols - 1 &&
                xy[1] >= 0 &&
                xy[1] < rows - 1 &&
                this.next(true, grid, xy, level, west, north, cellSize, pt)) {
            contour.add(new double[]{pt[0], pt[1]});
        }

        return contour.toArray(new double[][]{});
    }
    
    private boolean next(boolean forward, float[][] grid, int[]xy, float level,
            double west, double north, float cellSize, float[] pt) {
        
        final int x = xy[0];
        final int y = xy[1];
        
        if (this.flags[y][x] == true)
            return false;
        this.flags[y][x] = true;
        
        float v0 = grid[y+1][x];  // lower left
        if (Float.isNaN(v0))
            return false;
        float v1 = grid[y+1][x+1];// lower right
        if (Float.isNaN(v1))
            return false;
        float v2 = grid[y][x];    // upper left
        if (Float.isNaN(v2))
            return false;
        float v3 = grid[y][x+1];  // upper right
        if (Float.isNaN(v3))
            return false;
        
        if (this.treatDegreeJump) {

            float v0d = v0 - 180;
            float v1d = v1 - 180;
            float v2d = v2 - 180;
            float v3d = v3 - 180;
            
            final boolean adjustCell =
                    (v0d > 0 && v1d < 0 && v0d - v1d > 90)
                    || (v0d < 0 && v1d > 0 && v1d - v0d > 90)
                    || (v0d > 0 && v2d < 0 && v0d - v2d > 90)
                    || (v0d < 0 && v2d > 0 && v2d - v0d > 90)
                    || (v0d > 0 && v3d < 0 && v0d - v3d > 90)
                    || (v0d < 0 && v3d > 0 && v3d - v0d > 90)
                    
                    || (v1d > 0 && v2d < 0 && v1d - v2d > 90)
                    || (v1d < 0 && v2d > 0 && v2d - v1d > 90)
                    || (v1d > 0 && v3d < 0 && v1d - v3d > 90)
                    || (v1d < 0 && v3d > 0 && v3d - v1d > 90)
                    
                    || (v2d > 0 && v3d < 0 && v2d - v3d > 90)
                    || (v2d < 0 && v3d > 0 && v3d - v2d > 90);
            
            if (adjustCell) {
                
                if (v0 > 180)
                    v0 -= 360;
                if (v1 > 180)
                    v1 -= 360;
                if (v2 > 180)
                    v2 -= 360;
                if (v3 > 180)
                    v3 -= 360;
            }
        }
        
        if (!forward) {
            v0 = -v0;
            v1 = -v1;
            v2 = -v2;
            v3 = -v3;
            level = -level;
        }
        
        int code = 0;
        if (v0 > level)
            code ^= 1;
        if (v1 > level)
            code ^= 2;
        if (v2 > level)
            code ^= 4;
        if (v3 > level)
            code ^= 8;
        if (code == 0 || code == 15)
            return false;
        
        pt[0] = (float)(west + xy[0] * cellSize);
        pt[1] = (float)(north - xy[1] * cellSize);
        
        switch (code) {
            case 1:
                pt[1] -= interpol(level, v2, v0) * cellSize;;
                xy[0]--;
                break;
                
            case 2:
                pt[0] += interpol(level, v0, v1) * cellSize;
                pt[1] -= cellSize;
                xy[1]++;
                break;
                
            case 3:
                pt[1] -= interpol(level, v2, v0) * cellSize;
                xy[0]--;
                break;
                
            case 4:
                pt[0] += interpol(level, v2, v3) * cellSize;
                xy[1]--;
                break;
                
            case 5:
                pt[0] += interpol(level, v2, v3) * cellSize;
                xy[1]--;
                break;
                
            case 7:
                pt[0] += interpol(level, v2, v3) * cellSize;
                xy[1]--;
                break;
                
            case 8:
                pt[0] += cellSize;
                pt[1] -= interpol(level, v3, v1) * cellSize;
                xy[0]++;
                break;
                
            case 10:
                pt[0] += interpol(level, v0, v1) * cellSize;
                pt[1] -= cellSize;
                xy[1]++;
                break;
                
            case 11:
                pt[1] -= interpol(level, v2, v0) * cellSize;
                xy[0]--;
                break;
                
            case 12:
                pt[0] += cellSize;
                pt[1] -= interpol(level, v3, v1) * cellSize;
                xy[0]++;
                break;
                
            case 13:
                pt[0] += cellSize;
                pt[1] -= interpol(level, v3, v1) * cellSize;
                xy[0]++;
                break;
                
            case 14:
                pt[0] += interpol(level, v0, v1) * cellSize;
                pt[1] -= cellSize;
                xy[1]++;
                break;
                
            /*
            case 6:
            case 9:
             */
                
        }
        return true;
    }
    
    static final private float interpol(float level, float v0, float v1) {
        return (level - v0) / (v1 - v0);
    }
    
    public GeoGrid getGeoGrid() {
        return geoGrid;
    }
    
    public void setGeoGrid(GeoGrid geoGrid) {
        this.geoGrid = geoGrid;
        this.flags = new boolean[geoGrid.getRows()][geoGrid.getCols()];
    }
   
    public float getInterval() {
        return interval;
    }
    
    public void setInterval(float interval) {
        this.interval = interval;
    }
    
    public boolean isTreatDegreeJump() {
        return treatDegreeJump;
    }
    
    public void setTreatDegreeJump(boolean treatDegreeJump) {
        this.treatDegreeJump = treatDegreeJump;
    }
    
    /*
    public static void main(String[] params) {
        GeoGrid geoGrid = new GeoGrid(3, 3, 1);
        geoGrid.setWest(0);
        geoGrid.setNorth(2);
        geoGrid.setMeshSize(1);
        float[][] grid = geoGrid.getGrid();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                grid[i][j] = 0.999999f;
            }
        }
        grid[1][1] = 0.000001f;
     
     
        grid[0][0] = 0;
        grid[0][1] = 1;
        grid[0][2] = 2;
        grid[1][0] = 1;
        grid[1][1] = 2;
        grid[1][2] = 3;
        grid[2][0] = 2;
        grid[2][1] = 3;
        grid[2][2] = 4;
     
        Contourer contourer = new Contourer();
        contourer.setGeoGrid(geoGrid);
        contourer.setInterval(0.5f);
        contourer.contour();
     
        ika.geoexport.GeoSetExporter exporter = new ika.geoexport.SVGExporter();
        try {
            exporter.export(contourer.getGeoSet(), "/Users/jenny/Desktop/contours.svg");
        } catch (Exception exc) {
            exc.printStackTrace();
        }
    }
     */
}