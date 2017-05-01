/*
 * GeoGrid.java
 *
 * Created on August 14, 2005, 3:54 PM
 *
 */

package ika.geo;

import java.awt.geom.*;

/**
 *
 * @author Bernhard Jenny
 */
public class GeoGrid extends GeoObject {
    
    private int cols;
    private int rows;
    private double meshSize;
    private double west;
    private double north;
    private float[][] grid;
    
    /** Creates a new instance of GeoGrid */
    public GeoGrid(int cols, int rows, double meshSize) {
        this.initGrid(cols, rows, meshSize);
    }
    
    public GeoGrid(int cols, int rows, double meshSize, float initialValue) {
        this.initGrid(cols, rows, meshSize);
        for (int r = 0; r < rows; ++r)
            java.util.Arrays.fill(this.grid[r], initialValue);
    }
    
    private void initGrid(int cols, int rows, double meshSize) {
        this.cols = cols;
        this.rows = rows;
        this.meshSize = meshSize;
        this.grid = new float[rows][cols];
    }
    
    public void draw(java.awt.Graphics2D g2d, double scale, boolean drawSelectionState) {
        Rectangle2D.Double bounds = (Rectangle2D.Double)this.getBounds2D();
        g2d.draw(bounds);
    }
    
    public java.awt.geom.Rectangle2D getBounds2D() {
        final double width = this.meshSize * (this.cols - 1);
        final double height = this.meshSize * (this.cols - 1);
        final double x = this.west;
        final double y = this.north - height;
        return new Rectangle2D.Double(x, y,  width, height);
    }
    
    public java.awt.geom.PathIterator getPathIterator(java.awt.geom.AffineTransform affineTransform) {
        return null;
    }
    
    public boolean isIntersectedByRectangle(java.awt.geom.Rectangle2D rect, double scale) {
        // Test if if the passed rectangle and the bounding box of this object
        // intersect.
        // Use GeometryUtils.rectanglesIntersect and not Rectangle2D.intersects!
        final Rectangle2D bounds = this.getBounds2D();
        return ika.utils.GeometryUtils.rectanglesIntersect(rect, bounds);
    }
    
    public boolean isPointOnSymbol(java.awt.geom.Point2D point, double tolDist, double scale) {
        Rectangle2D bounds = this.getBounds2D();
        ika.utils.GeometryUtils.enlargeRectangle(bounds, tolDist);
        return bounds.contains(point);
    }
    
    public void move(double dx, double dy) {
        this.west += dx;
        this.north += dy;
    }
    
    public final float getValue(int col, int row) {
        return grid[row][col];
    }
    
    public final float getNearestNeighbor (double x, double y) {
        // round to nearest neighbor
        int col = (int)((x - this.west) / this.meshSize + 0.5);
        int row = (int)((this.north - y) / this.meshSize + 0.5);
        if (col < 0 || col >= this.cols || row < 0 || row >= this.rows)
            return Float.NaN;
        return grid[row][col];
    }
    
    
   /**
    * Bilinear interpolation.
    * See http://www.geovista.psu.edu/sites/geocomp99/Gc99/082/gc_082.htm
    * "What's the point? Interpolation and extrapolation with a regular grid DEM"
   */     
    public final float getBilinearInterpol (double x, double y) {
        float h1, h2, h3, h4;
        // column and row of the top left corner
        int col = (int)((x - this.west) / this.meshSize);
        int row = (int)((this.north - y) / this.meshSize);
        
        if (col < 0 || col >= this.cols || row < 0 || row >= this.rows)
            return Float.NaN;
        
        // relative coordinates in the square formed by the four points, scaled to 0..1.
        // The origin is in the lower left corner.
        double relX = (x - this.west) / this.meshSize - col;
        final double south = this.getSouth();
        double relY = (y - south) / this.meshSize - this.rows + row + 2;
        
        if (row + 1 < this.rows) {
            // value at bottom left corner
            h1 = this.getValue(col, row + 1);
            // value at bottom right corner
            h2 = col + 1 < this.cols ? this.getValue(col + 1, row + 1) : Float.NaN;
        } else {
            h1 = Float.NaN;
            h2 = Float.NaN;;
        }
        
        // value at top left corner
        h3 = this.getValue(col, row);
        
        // value at top right corner
        h4 = col + 1 < this.cols ? this.getValue(col + 1, row) : Float.NaN;
        
        // start with the optimistic case: all values are valid
        return GeoGrid.bilinearInterpolation(h1, h2, h3, h4, relX, relY);
    }
    
    /**
     * compute a bilinear interpolation from
     * h1: value bottom left
     * h2: value bottom right
     * h3: value top left
     * h4: value top right
     * relX: relative horizontal coordinate (0 .. 1) counted from left to right
     * relY: relative vertical coordinate (0 .. 1) counted from bottom to top
     */
    private static final float bilinearInterpolation(float h1, float h2, float h3, float h4, double relX, double relY) {
        return (float)(h1 + (h2 - h1) * relX + (h3 - h1) * relY + (h1 - h2 - h3 + h4) * relX * relY);
    }
    
    public void setValue(float value, int col, int row) {
        grid[row][col] = value;
    }
    
    public float[] getMinMax(){
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (int r = 0; r < rows; ++r) {
            for (int c = 0; c < cols; ++c) {
                float v = grid[r][c];
                if (v < min)
                    min = v;
                if (v > max)
                    max = v;
            }
        }
        return new float[]{min, max};
    }
    
    public int getCols() {
        return cols;
    }
    
    public int getRows() {
        return rows;
    }
    
    public double getMeshSize() {
        return meshSize;
    }
    
    public void setMeshSize(double meshSize) {
        this.meshSize = meshSize;
    }
    
    public double getWest() {
        return west;
    }
    
    public void setWest(double west) {
        this.west = west;
    }
    
    public double getNorth() {
        return north;
    }
    
    public void setNorth(double north) {
        this.north = north;
    }
    
    public double getSouth() {
        return this.north - (this.rows - 1) * this.meshSize;
    }
    
    public float[][] getGrid() {
        return grid;
    }
}
