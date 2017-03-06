/*
 * Created on July 2, 2005, 10:24 AM
 */

package ika.geoexport;

import ika.geo.*;
import java.io.*;
import java.awt.geom.*;
import java.util.*;

/**
 *
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class WMFExporter extends GeoSetExporter{
    
    /*
     * Stream to write to.
     */
    private java.io.ByteArrayOutputStream stream = null;
    
    /*
     * Store bounding box around GeoObjects.
     */
    private Rectangle2D bounds = null;
    
    /*
     * Scale GeoObjects by this factor.
     * Automatically assigned, no getter or setter.
     */
    private double scale = 1;
    
    /*
     * Keep track of the number of objects written to the WMF file.
     * This includes pen and brush records.
     */ 
    private int nbrObjects = 0;
    
    /*
     * Keep track of the largest record.
     */
    private int sizeOfLargestRecord = 0;
    
    /*
     * Vector holding all VectorSymbols used to draw the GeoObjects.
     */
    private Vector vectorSymbols = new Vector();
    
    /*
     * Index into vectorSymbols on the VectorSymbol that is currently used for 
     * drawing.
     */
    private int currentVectorSymbolID = 0;
    
    /* 
     * The largest possible coordinate in a WMF file.
     */
    private static final int MAX_COORD = 16384;
    
    public WMFExporter(double mapScale) {
        super(mapScale);
    }
    
    public void export(GeoSet geoSet, String filePath) throws IOException {
        
        // compute scale
        this.bounds = geoSet.getBounds2D();
        this.scale = MAX_COORD / Math.max(bounds.getWidth(), bounds.getHeight());
        
        this.stream = new java.io.ByteArrayOutputStream();
        
        this.writeHeader();
        this.writeMapModeRecord();
        this.writeWindowOriginRecord((short)0, (short)0);
        final short w = this.transformDim((float)bounds.getWidth());
        final short h = this.transformDim((float)bounds.getHeight());
        this.writeWindowExtensionRecord(w, h);
        
        // write a default VectorSymbol used to draw bounding boxes for GeoImages
        VectorSymbol defaultVectorSymbol = new VectorSymbol();     
        this.vectorSymbols.add(defaultVectorSymbol);
        
        // extract all VectorSymbols and write them to the file
        this.extractVectorSymbols(geoSet);
       
        // IMPORTANT: write the symbols before any other record that increments
        // this.nbrObjects
        this.writeVectorSymbols();
        
        this.writeGeoSet(geoSet);

        this.writeFooterRecord();
        byte[] bytes = this.updateHeader();
        this.writeBytesToFile(bytes, filePath);
    }
    
    private void writeGeoSet(GeoSet geoSet) {
        if (geoSet.isVisible() == false)
            return;
        
        final int numberOfChildren = geoSet.getNumberOfChildren();
        for (int i = 0; i < numberOfChildren; i++) {
            GeoObject geoObject = geoSet.getGeoObject(i);
            
            // only write visible objects
            if (geoObject.isVisible() == false)
                continue;
            
            if (geoObject instanceof GeoPath) {
                writeGeoPathAsPolyLine((GeoPath)geoObject);
            } else if (geoObject instanceof GeoPoint) {
                writeGeoPoint((GeoPoint)geoObject);
            } else if (geoObject instanceof GeoImage) {
                writeGeoImage((GeoImage)geoObject);
            } else if (geoObject instanceof GeoSet) {
                writeGeoSet((GeoSet)geoObject);
            }
        }
    }

    @Override
    public String getFileExtension() {
        return "wmf";
    }
    
    
    /* Polygon - private class that holds pairs of points and a closed flag. */
    private class Polygon extends java.util.Vector{
        public boolean closed = false;
    }
    
    private void writePathIterator(PathIterator pi, VectorSymbol symbol){
        
        float [] coords = new float [6];
        
        // vector to hold Polygon objects
        java.util.Vector polygons = new java.util.Vector();
        Polygon polygon = new Polygon();
        
        // convert the GeoPath to a series of Polygon objects
        while (!pi.isDone()) {
            final int type = pi.currentSegment(coords);
            int iCoords[] = this.transform(coords);
            switch (type) {
                
                case PathIterator.SEG_MOVETO:{
                    if (polygon.size() > 1)
                        polygons.add(polygon);
                    polygon = new Polygon();
                    polygon.add(new java.awt.Point(iCoords[0], iCoords[1]));
                    break;
                }
                
                case PathIterator.SEG_CLOSE: {
                    polygon.closed = true;
                    if (polygon.size() > 1)
                        polygons.add(polygon);
                    polygon = new Polygon();
                    break;
                }
                case PathIterator.SEG_LINETO:{
                    polygon.add(new java.awt.Point(iCoords[0], iCoords[1]));
                    break;
                }
            }
            pi.next();
        }
        
        // add polygon to polygons if this has not yet been done
        if (polygons.contains(polygon) == false && polygon.size() > 1) {
            polygons.add(polygon);
        }
        
        // write each Polygon in the polygons vector
        for (int i = 0; i < polygons.size(); i++){
            
            this.selectVectorSymbol(symbol);
            
            polygon = (Polygon)polygons.get(i);
            int nbrPoints = polygon.size();
            if (nbrPoints > Short.MAX_VALUE)
                nbrPoints = Short.MAX_VALUE;
            
            // allocate an array to hold the points
            short points[] = new short[nbrPoints * 2 + 1];
            
            // write the number of points to the array
            points[0] = (short)nbrPoints;
            
            // copy the points
            for (int j = 0; j < nbrPoints; j++){
                java.awt.Point pt = (java.awt.Point)polygon.get(j);
                points[j*2+1] = (short)pt.x;
                points[j*2+2] = (short)pt.y;
            }
            
            // write a polyline or a polygon
            if (polygon.closed){
                /*
                The polygon is closed automatically by drawing a line from the
                last vertex to the first.
                The current position is neither used nor updated by the Polygon
                function.
                 */
                writeRecord(0x0324, points);
            } else {
                /*
                The lines are drawn from the first point through subsequent
                points by using the current pen. Unlike the LineTo or PolylineTo
                functions, the Polyline function neither uses nor updates the
                current position.
                 */
                writeRecord(0x0325, points);
            }
            ++this.nbrObjects;
        }
    }
    
    private void writeGeoPoint(GeoPoint geoPoint) {
        PointSymbol pointSymbol = geoPoint.getPointSymbol();
        java.awt.Shape shape = pointSymbol.getPointSymbol(this.mapScale,
                geoPoint.getX(), geoPoint.getY());
        PathIterator pi = shape.getPathIterator(null, this.flatness);
        this.writePathIterator(pi, pointSymbol);
    }
    
    private void writeGeoImage(GeoImage geoImage) {
        Rectangle2D bounds = geoImage.getBounds2D();
        this.selectVectorSymbol(0); // draw with default VectorSymbol
        this.writeRectangle(bounds);
    }
    
    private void writeGeoPathAsPolyLine(GeoPath geoPath) {
        PathIterator pi = geoPath.getPathIterator(null, this.flatness);
        this.writePathIterator(pi, geoPath.getVectorSymbol());
    }
    
    private int[] transform(float[] fCoords) {
        int iCoords[] = new int [fCoords.length];
        for (int i = 0; i < fCoords.length / 2; ++i) {
            iCoords[i*2] = transformX(fCoords[i*2]);
            iCoords[i*2+1] = transformY(fCoords[i*2+1]);
        }
        return iCoords;
    }
    
    private short transformX(float x) {
        return (short)Math.round((x - this.bounds.getMinX()) * this.scale);
    }
    
    private short transformY(float y) {
        return (short)Math.round((this.bounds.getMaxY() - y) * this.scale);
    }
    
    private short transformDim(float dim) {
        return (short)Math.round(dim * this.scale);
    }
    
    private void writeLine(int x1, int y1, int x2, int y2) {
        this.writeMoveToRecord(x1, y1);
        this.writeLineToRecord(x2, y2);
        ++this.nbrObjects;
    }
    
    private void writeRectangle(Rectangle2D rect) {
        final short x = this.transformX((float)rect.getMinX());
        final short y = this.transformY((float)rect.getMaxY());
        final short w = this.transformDim((float)rect.getWidth());
        final short h = this.transformDim((float)rect.getHeight());
        this.writeRectangleRecord(x, y, w, h);
    }
    
    private void writeCircle(double cx, double cy, double radius) {
        final short x = this.transformX((float)cx);
        final short y = this.transformY((float)cy);
        final short r = this.transformDim((float)radius);
        this.writeCircleRecord(x, y, r);
    }
    
    private void writeBytesToFile(byte[] bytes, String filePath)
    throws java.io.FileNotFoundException, java.io.IOException {
        java.io.FileOutputStream fileStream = new java.io.FileOutputStream(filePath);
        fileStream.write(bytes);
        fileStream.flush();
        fileStream.close();
    }
    
    private void writeHeader() {
        
        final short w = this.transformDim((float)bounds.getWidth());
        final short h = this.transformDim((float)bounds.getHeight());
        
        // Placeable Meta File Header
        // DWORD   key;
        this.writeInt(0x9AC6CDD7);
        // HANDLE  hmf;
        this.writeShort((short)0);
        // RECT    bbox;
        // left
        this.writeShort((short)0);
        // top
        this.writeShort((short)0);
        // right
        this.writeShort(w);
        // bottom
        this.writeShort(h);
        // WORD    inch;
        this.writeShort((short)1440);
        // DWORD   reserved;
        this.writeInt(0);
        // WORD    checksum;
        short checksum = 0;
        byte[] b = stream.toByteArray();
        for (int i = 0; i < b.length; ++i)
            checksum ^= b[i];
        this.writeShort(checksum);
        
        // Standard Meta File Header
        // WORD  FileType;       // Type of metafile (0=memory, 1=disk)
        this.writeShort((short)1);
        // WORD  HeaderSize;     // Size of header in WORDS (always 9)
        this.writeShort((short)9);
        // WORD  Version;        // Version of Microsoft Windows used
        this.writeShort((short)0x0300);
        // DWORD FileSize;       // Total size of the metafile in WORDs
        this.writeInt(0);
        // WORD  NumOfObjects;   // Number of objects in the file
        this.writeShort((short)1);
        // DWORD MaxRecordSize;  // The size of largest record in WORDs
        this.writeInt(0);
        // WORD  NumOfParams;    // Not Used (always 0)
        this.writeShort((short)0);
    }
    
    private byte[] updateHeader() throws java.io.IOException {
        
        final int headerOffset = 22; // length of placeable meta file header
        
        byte[] bytes = stream.toByteArray();
        // update total size of the metafile in WORDs
        byte[] b = this.intToBytes(bytes.length / 2-11);
        for (int i = 0; i < 4; ++i)
            bytes[i+6+headerOffset] = b[3-i];
        
        // update number of objects in the file
        if (this.nbrObjects > Short.MAX_VALUE)
            throw new java.io.IOException("Too many objects in WMF file");
        bytes[10+headerOffset] = this.right((short)this.nbrObjects);
        bytes[11+headerOffset] = this.left((short)this.nbrObjects);
        
        // update size of largest record in WORDs
        b = this.intToBytes(this.sizeOfLargestRecord);
        for (int i = 0; i < 4; ++i)
            bytes[i+12+headerOffset] = b[3-i];
        
        return bytes;
    }
    
    private void writeRecord(int fctNumber, short [] params) {
        // write size of this record in 32-bit words
        final int recordSize = params != null ? params.length + 3 : 3;
        this.writeInt(recordSize);
        this.writeShort((short)fctNumber);
        if (params!= null) {
            for (int i = 0; i < params.length; ++i)
                this.writeShort(params[i]);
        }
        this.updateSizeOfLargestRecord(recordSize);
    }
    
    private void writeFooterRecord() {
        writeRecord(0x0000, null);
    }
    
    private void writeMapModeRecord() {
        writeRecord(0x0103, new short[]{8});
    }
    
    private void writeWindowOriginRecord(short x, short y) {
        writeRecord(0x020B, new short[]{y, x});
    }
    
    private void writeWindowExtensionRecord(short widht, short height) {
        writeRecord(0x020C, new short[]{height, widht});
    }
    
    private void writeMoveToRecord(short x, short y) {
        writeRecord(0x0214, new short[]{y, x});
    }
    
    private void writeMoveToRecord(float x, float y) {
        this.writeMoveToRecord(Math.round(x), Math.round(y));
    }
    
    private void writeLineToRecord(short x, short y) {
        writeRecord(0x0213, new short[]{y, x});
    }
    
    private void writeLineToRecord(float x, float y) {
        this.writeLineToRecord(Math.round(x), Math.round(y));
    }
    
    private void writeSelectObjectRecord(short objID) {
        writeRecord(0x012D, new short[]{objID});
    }
    
    private void writeRectangleRecord(short x, short y, short width, short height) {
        // bottom|right|top|left|
        writeRecord(0x041B, new short[]{(short)(y + height), (short)(x + width), y, x});
        ++this.nbrObjects;
    }
    
    private void writeCircleRecord(short x, short y, short r) {
        // bottom|right|top|left|
        writeRecord(0x0418, new short[]{(short)(y + r), (short)(x + r),
                (short)(y - r), (short)(x - r)});
                ++this.nbrObjects;
    }
    
    /*
     * Returns the position of a VectorSymbol in this.vectorSymbols.
     * If this.vectorSymbols does not contain the VectorSymbol, -1 is returned.
     */
    private int findVectorSymbol(VectorSymbol symbol){
        final int nbrSymbols = this.vectorSymbols.size();
        for (int i = 0; i < nbrSymbols; i++){
            VectorSymbol s = (VectorSymbol)(this.vectorSymbols.get(i));
            if (symbol.equals(s))
                return i;
        }
        return -1;
    }
    
    /*
     * Selects a VectorSymbol, i.e. following drawing occurs with the passed
     * VectorSymbol.
     */
    private void selectVectorSymbol(VectorSymbol symbol){
        final int symbolID = this.findVectorSymbol(symbol);
        this.selectVectorSymbol(symbolID);
    }
    
    /*
     * Selects a VectorSymbol, i.e. following drawing occurs with the passed
     * VectorSymbol.
     */
    private void selectVectorSymbol(int symbolID){
        if (symbolID < 0)
            throw new IllegalArgumentException("problem writing symbols for WMF file.");
        
        if (this.currentVectorSymbolID != symbolID){
            // select pen for stroking
            this.writeSelectObjectRecord((short)symbolID);
            
            // select brush for filling
            this.writeSelectObjectRecord((short)(symbolID + this.vectorSymbols.size()));
            this.currentVectorSymbolID = symbolID;
        }
    }
    
    /*
     * Find all VectorSymbols used in a GeoSet and add the found VectorSymbols
     * to this.vectorSymbols.
     */ 
    private void extractVectorSymbols(GeoSet geoSet){
        if (geoSet.isVisible() == false)
            return;
        
        final int numberOfChildren = geoSet.getNumberOfChildren();
        for (int i = 0; i < numberOfChildren; i++) {
            GeoObject geoObject = geoSet.getGeoObject(i);
            
            // only extract symbols from visible objects
            if (geoObject.isVisible() == false)
                continue;
            
            if (geoObject instanceof GeoPath) {
                GeoPath geoPath = (GeoPath)geoObject;
                VectorSymbol symbol = geoPath.getVectorSymbol();
                if (this.findVectorSymbol(symbol) < 0)
                    this.vectorSymbols.add(symbol);
            } else if (geoObject instanceof GeoPoint) {
                GeoPoint geoPoint = (GeoPoint)geoObject;
                PointSymbol pointSymbol = geoPoint.getPointSymbol();
                if (this.findVectorSymbol(pointSymbol) < 0)
                    this.vectorSymbols.add(pointSymbol);
                
            } else if (geoObject instanceof GeoSet) {
                extractVectorSymbols((GeoSet)geoObject);
            }
        }
    }
    
    /*
     * Write the VectorSymbols in this.vectorSymbols to the WMF file.
     */ 
    private void writeVectorSymbols(){
        // first write the pens for stroking
        Iterator iterator = this.vectorSymbols.iterator();
        while (iterator.hasNext()){
            VectorSymbol symbol = (VectorSymbol)iterator.next();
            this.writePenRecord(symbol);
        }
        
        // then write the brushes for filling
        iterator = this.vectorSymbols.iterator();
        while (iterator.hasNext()){
            VectorSymbol symbol = (VectorSymbol)iterator.next();
            this.writeBrushRecord(symbol);
        }
    }
    
    /*
     * Writes the stroking information of a VectorSymbol to a WMF file.
     */
    private void writePenRecord(VectorSymbol symbol) {
        
        /* When specifying an explicit RGB color, the COLORREF value has the
        following hexadecimal form: 0x00bbggrr */
        
        // stroke style: solid = 0; dashed = 1; null = 5. There are some more...
        short style = (short)(symbol.isDashed() ? 1 : 0);
        if (symbol.isStroked() == false)
            style = 5;
        
        // stroke width
        final float w = symbol.getScaledStrokeWidth(this.mapScale);
        final short strokeWidth = this.transformDim(w);
        
        // stroke color
        final int strokeColor = symbol.getStrokeColor().getRGB();
        final short r = (short)((strokeColor & 0x00ff0000) >> 16);
        final short g = (short)((strokeColor & 0x0000ff00) >> 8);
        final short b = (short) (strokeColor & 0x000000ff);
        final short gr = (short)(g << 8 | r);
        
        // 02FA CreatePenIndirect
        writeRecord(0x02FA, new short[]{style, strokeWidth, strokeWidth, gr, b});
        
        // CreatePenIndirect adds an entry for itself in the object list.
        ++this.nbrObjects;
    }
    
    /*
     * Writes the filling information of a VectorSymbol to a WMF file.
     */
    private void writeBrushRecord(VectorSymbol symbol) {
        // brush style: solid = 0; hollow = 1; hatched = 2;
        final short brushStyle = (short)(symbol.isFilled() ? 0 : 1);
        
        // fill color
        final int fillColor = symbol.getFillColor().getRGB();
        final short r = (short)((fillColor & 0x00ff0000) >> 16);
        final short g = (short)((fillColor & 0x0000ff00) >> 8);
        final short b = (short) (fillColor & 0x000000ff);
        final short gr = (short)(g << 8 | r);
        
        // hatch only used if brushStyle == 2
        final int brushHatch = 0;
        
        // CreateBrushIndirect
        writeRecord(0x02FC, new short[]{brushStyle, gr, b, brushHatch});
        
        // CreateBrushIndirect adds an entry for itself in the object list.
        ++this.nbrObjects;
    }
    
    private void updateSizeOfLargestRecord(int recordSizeInWords) {
        if (recordSizeInWords > this.sizeOfLargestRecord)
            this.sizeOfLargestRecord = recordSizeInWords;
    }
    
    /**
     * Shift a short left 8
     * @param s the short to shift left 8
     * @return the byte result of the shift
     */
    byte left(short s) {
        return ((byte) ((s & (short) 0xFF00) >> 8));
    }
    
    /**
     * Lower Byte of a short
     * @param s the short
     * @return byte with lower byte of short
     */
    byte right(short s) {
        return ((byte) ((s & (short) 0x00FF)));
    }
    
    byte[] intToBytes(int i) {
        byte[] b = new byte[4];
        b[0] = ((byte) ((i & 0xFF000000) >> 24));
        b[1] = ((byte) ((i & 0x00FF0000) >> 16));
        b[2] = ((byte) ((i & 0x0000FF00) >> 8));
        b[3] = ((byte) ((i & 0x000000FF)));
        return b;
    }
    
    /**
     * Write a short to the byte output stream
     * @param s the short
     */
    void writeShort(short s) {
        this.stream.write(right(s));
        this.stream.write(left(s));
    }
    
    /**
     * Write a short to the byte output stream
     * @param s the short
     */
    void writeInt(int i) {
        byte[] b = this.intToBytes(i);
        for (int k = 3; k >= 0; --k)
            this.stream.write(b[k]);
    }
    
    void writeByte(byte b) {
        this.stream.write(b);
    }
}
