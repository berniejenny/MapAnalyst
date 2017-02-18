package ika.geoexport;

import java.awt.*;
import java.awt.geom.*;
import java.io.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import ika.utils.*;
import ika.geo.*;
import ika.mapanalyst.ApplicationInfo;

/**
 * Exporter for the SVG file format.
 */
public class SVGExporter extends GeoSetExporter {
    
    private double west, north;
    private double scale = 1;
    
    private static int NBR_AFTER_COMA_DECIMALS = 3;
    private static double AFTER_COMA_ROUNDER =
            Math.pow(10, NBR_AFTER_COMA_DECIMALS);
    
    private static String svgNamespace = "http://www.w3.org/2000/svg";
    private static String svgIdentifier = "-//W3C//DTD SVG 1.0//EN";
    private static String svgDTD = "http://www.w3.org/TR/2001/REC-SVG-20010904/DTD/svg10.dtd";
    private static String xlinkNamespace = "http://www.w3.org/1999/xlink";
    
    public SVGExporter(double mapScale){
        super(mapScale);
    }
    
    /**
     * Exports a GeoSet to a new SVG file.
     * @param geoSet The GeoSet to export.
     * @param filePath The path to the file that will be used to export to.
     * @throws java.io.IOException Throws an exception if export is not possible.
     */
    private void exportSVG(GeoSet geoSet, String filePath)
    throws IOException {
        try {
            
            Rectangle2D bounds = geoSet.getBounds2D();
            this.west = bounds.getMinX();
            this.north = bounds.getMaxY();
            
            // create a document
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.newDocument();
            
            // create the main svg element
            Element svg = (Element)document.createElement("svg");
            
            // specify a namespace prefix on the 'svg' element, which means that
            // SVG is the default namespace for all elements within the scope of
            // the svg element with the xmlns attribute:
            // See http://www.w3.org/TR/SVG11/struct.html#SVGElement
            svg.setAttribute("xmlns", svgNamespace);
            svg.setAttribute("xmlns:xlink", xlinkNamespace);
            svg.setAttribute("version", "1.0");
            svg.setAttribute("preserveAspectRatio", "xMinYMin");
            svg.setAttribute("width", Double.toString(transDim(bounds.getWidth())));
            svg.setAttribute("height", Double.toString(transDim(bounds.getHeight())));
            String viewBoxStr = "0 0 " + transDim(bounds.getWidth()) + " "
                    + transDim(bounds.getHeight());
            svg.setAttribute("viewBox", viewBoxStr);
            document.appendChild(svg);
            
            // add a description element
            Element desc = (Element)document.createElement("desc");
            svg.appendChild(desc);
            desc.appendChild(document.createTextNode(buildDescription()));
            
            // convert GeoSet to SVG DOM
            this.writeGeoSet(geoSet, svg, document);
            
            // write the DOM to a file
            filePath = FileUtils.forceFileNameExtension(filePath,  "svg");
            
            Source source = new DOMSource(document);
            
            // Prepare the output file
            File file = new File(filePath);
            Result result = new StreamResult(file);
            
            // Write the DOM document to the file
            Transformer xformer = TransformerFactory.newInstance().newTransformer();
            xformer.setOutputProperty(OutputKeys.INDENT, "yes");
            xformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, svgIdentifier);
            xformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, svgDTD);
            xformer.transform(source, result);
            
            // XMLDOMPrinter.printXML(document, System.out);
        } catch (Exception e) {
            throw new IOException("Export to SVG not possible. " + e.toString());
        }
    }
    
    private double round(double d){
        return Math.round(AFTER_COMA_ROUNDER * d) / AFTER_COMA_ROUNDER;
    }
    private double transX(double x) {
        return this.round((x - west) * scale);
    }
    
    private double transY(double y) {
        return this.round((north - y) * scale);
    }
    
    private double transDim(double w) {
        return this.round(w * scale);
    }
    
    private void trans(double[] coords) {
        coords[0] = transX(coords[0]);
        coords[1] = transY(coords[1]);
        coords[2] = transX(coords[2]);
        coords[3] = transY(coords[3]);
        coords[4] = transX(coords[4]);
        coords[5] = transY(coords[5]);
    }
    
    /**
     * Converts a GeoSet to a SVG DOM.
     * @param geoSet The GeoSet to convert.
     * @param parent The parent element that will contain the passed GeoSet.
     * @param document The DOM.
     */
    private void writeGeoSet(GeoSet geoSet, Element parent, Document document) {
        
        final int nbrObj = geoSet.getNumberOfChildren();
        
        // don't write invisible or empty GeoSets
        if (geoSet.isVisible() == false || nbrObj < 1)
            return;
        
        Element g = (Element)document.createElement("g");
        parent.appendChild(g);
        
        for (int i = 0; i < nbrObj; i++) {
            GeoObject obj = geoSet.getGeoObject(i);
            
            // only write visible elements
            if (obj.isVisible() == false)
                continue;
            
            if (obj instanceof GeoSet) {
                writeGeoSet((GeoSet)obj, g, document);
            } else if (obj instanceof GeoPath) {
                writePath((GeoPath)obj, parent, document);
            } else if (obj instanceof GeoImage) {
                writeImage((GeoImage)obj, parent, document);
            } else if (obj instanceof GeoPoint) {
                writePoint((GeoPoint)obj, parent, document);
            }
        }
    }
    
    private void writeImage(GeoImage geoImage, Element parent, Document document) {
        Rectangle2D bounds = geoImage.getBounds2D();
        String xStr = Double.toString(transX(bounds.getMinX()));
        String yStr = Double.toString(transY(bounds.getMaxY()));
        String wStr = Double.toString(transDim(bounds.getWidth()));
        String hStr = Double.toString(transDim(bounds.getHeight()));
        
        Element image = (Element)document.createElement("image");
        image.setAttribute("x", xStr);
        image.setAttribute("y", yStr);
        image.setAttribute("width", wStr);
        image.setAttribute("height", hStr);
        
        // construct path to image
        String imagePath;
        try {
            java.net.URI uri = new java.net.URI("file", "", geoImage.getFilePath(), null);
            imagePath = uri.toString();
        } catch (Exception exc){
            imagePath = "file://" + geoImage.getFilePath();
        }
        image.setAttributeNS("xlink", "xlink:href", imagePath);
        parent.appendChild(image);
        
        // add rectangle of the size of the image
        Element rect = (Element)document.createElement("rect");
        rect.setAttribute("x", xStr);
        rect.setAttribute("y", yStr);
        rect.setAttribute("width", wStr);
        rect.setAttribute("height", hStr);
        rect.setAttribute("fill", "none");
        rect.setAttribute("stroke", "blue");
        rect.setAttribute("stroke-width", "1");
        parent.appendChild(rect);
    }
    
    private void writePoint(GeoPoint geoPoint, Element parent, Document document) {
        
        // Unfortunately Illustrator CS does not support symbols correctly.
        
        PointSymbol pointSymbol = geoPoint.getPointSymbol();
        Shape shape = pointSymbol.getPointSymbol(this.mapScale,
                geoPoint.getX(), geoPoint.getY());
        PathIterator pi = shape.getPathIterator(null);
        this.writePathIterator(pi, pointSymbol, parent, document);
    }
    
    /**
     * Converts a GeoPath to a SVG path.
     * @param geoPath The GeoPath to convert.
     * @param parent The parent element that will contain the passed GeoSet.
     * @param document The DOM.
     */
    private void writePath(GeoPath geoPath, Element parent, Document document) {
        PathIterator pi = geoPath.getPathIterator(null);
        this.writePathIterator(pi, geoPath.getVectorSymbol(), parent, document);
    }
    
    private void writePathIterator(PathIterator pi, VectorSymbol vectorSymbol,
            Element parent, Document document) {
        String svgPath = this.convertPathIteratorToSVG(pi);
        Element pathElement = (Element)document.createElement("path");
        if (vectorSymbol != null)
            pathElement.setAttribute("style", this.symbolToCSS(vectorSymbol));
        pathElement.setAttribute("d", svgPath);
        parent.appendChild(pathElement);
    }
    
    private String convertPathIteratorToSVG(PathIterator pi){
        StringBuffer str = new StringBuffer();
        double [] coords = new double [6];
        int segmentType;
        while (pi.isDone() == false) {
            segmentType = pi.currentSegment(coords);
            trans(coords);
            switch (segmentType) {
                case PathIterator.SEG_CLOSE:
                    str.append(" z");
                    break;
                    
                case PathIterator.SEG_LINETO:
                    str.append(" L");
                    str.append(coords[0]);
                    str.append(" ");
                    str.append(coords[1]);
                    break;
                    
                case PathIterator.SEG_MOVETO:
                    str.append(" M");
                    str.append(coords[0]);
                    str.append(" ");
                    str.append(coords[1]);
                    break;
                    
                case PathIterator.SEG_QUADTO:
                    str.append(" Q");
                    str.append(coords[0]);
                    str.append(" ");
                    str.append(coords[1]);
                    str.append(" ");
                    str.append(coords[2]);
                    str.append(" ");
                    str.append(coords[3]);
                    break;
                    
                case PathIterator.SEG_CUBICTO:
                    str.append(" C");
                    str.append(coords[0]);
                    str.append(" ");
                    str.append(coords[1]);
                    str.append(" ");
                    str.append(coords[2]);
                    str.append(" ");
                    str.append(coords[3]);
                    str.append(" ");
                    str.append(coords[4]);
                    str.append(" ");
                    str.append(coords[5]);
                    break;
            }
            pi.next();
        }
        return str.toString();
    }
    
    /**
     * Converts a VectorSymbol to a CSS style.
     * @param symbol The VectorSymbol to convert.
     * @return A CSS formated string.
     */
    public String symbolToCSS(VectorSymbol symbol) {
        StringBuffer str = new StringBuffer();
        
        // fill
        if (symbol.isFilled()) {
            str.append("fill:");
            str.append(ColorUtils.colorToCSSString(symbol.getFillColor()));
            str.append(";");
        } else {
            str.append("fill:none;");
        }
        
        // stroke
        if (symbol.isStroked()) {
            str.append("stroke:");
            str.append(ColorUtils.colorToCSSString(symbol.getStrokeColor()));
            str.append(";");
            str.append("stroke-width:");
            str.append(this.transDim(symbol.getScaledStrokeWidth(this.mapScale)));
            str.append(";");
            if (symbol.isDashed()) {
                str.append("stroke-dasharray:");
                str.append(this.transDim(symbol.getScaledDashLength(this.mapScale)));
                str.append(",");
                str.append(this.transDim(symbol.getScaledDashLength(this.mapScale)));
                str.append(";");
            }
        } // stroke:none is default and therefore not needed.
        
        return str.toString();
    }
    
    private String buildDescription() {
        final String appName = ApplicationInfo.getApplicationName();
        final String userName = System.getProperty("user.name");
        
        java.util.Date date= java.util.Calendar.getInstance().getTime();
        final String dateStr = java.text.DateFormat.getDateInstance().format(date);
        
        StringBuffer str = new StringBuffer();
        if (userName.length() > 0) {
            str.append("Author:");
            str.append(userName);
            str.append(" - ");
        }
        str.append("Generator:");
        str.append(appName);
        str.append(" - ");
        str.append("Date:");
        str.append(date);
        return str.toString();
    }
    
    public void export(GeoSet geoSet, String filePath)
    throws IOException {
        if (geoSet == null || filePath == null)
            throw new IllegalArgumentException();
        
        filePath = FileUtils.forceFileNameExtension(filePath, "svg");
        exportSVG(geoSet, filePath);
    }
    
    public double getScale() {
        return scale;
    }
    
    public void setScale(double scale) {
        this.scale = scale;
    }
    
    public void setScale(GeoSet geoSet, int svgWidth){
        double w = geoSet.getBounds2D().getWidth();
        this.scale = svgWidth / w;
    }
    
    public static void main(String[] args){
        try {
            java.net.URI uri = new java.net.URI("file",
                    "",
                    "/Users/jenny/Desktop/underground map.jpg",
                    null);
            System.out.println(uri);
            
        }catch (Exception e){}
        
        
    }

    @Override
    protected String getFileExtension() {
        return "svg";
    }
}