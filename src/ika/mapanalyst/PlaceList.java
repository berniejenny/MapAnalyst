/*
 * LexicalHashMap.java
 *
 * Created on June 21, 2005, 12:46 PM
 *
 */

package ika.mapanalyst;

import java.util.regex.*;
import ternarysearchtree.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

/**
 *
 * @author jenny
 */
public class PlaceList implements Serializable {
    
    static final long serialVersionUID = -872868114859498356L;
    
    private TernarySearchTree tst;
    
    /**
     * The maximum number of place names that the user can select from, if he
     * enters only a short sequence of the name. If more than
     * MAX_INCOMPLETE_PLACE_NAMES different names are found, no selection dialog
     * will be presented.
     **/
    private int MAX_INCOMPLETE_PLACE_NAMES = 100;
    
    protected class Point implements java.io.Serializable {
        static final long serialVersionUID = -1189960479387534605L;
        
        public double x;
        public double y;
        public Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }
    
    /** Creates a new instance of LexicalHashMap */
    public PlaceList() {
        // tst is null, while not initialized by reading from a file
        this.tst = null;
    }
    
    /**
     * search matches with same initial characters
     */
    private Object[] getSimilarStarts(String key) {
        tst.setNumReturnValues(this.MAX_INCOMPLETE_PLACE_NAMES);
        DoublyLinkedList list = tst.matchPrefix(key);
        String[] names = new String[list.size()];
        Point[] pts = new Point[list.size()];
        
        DoublyLinkedList.DLLIterator iterator = list.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            final String name = (String)iterator.next();
            names[i] = name;
            pts[i] = (Point)tst.get(name);
            i++;
        }
        return new Object[] {names, pts};
    }
    
    public TernarySearchTree readFile(String filePath) throws IOException {
        try {
            this.tst = new TernarySearchTree();
            
            BufferedReader in = new BufferedReader(new FileReader(filePath));
            String str;
            while ((str = in.readLine()) != null) {
                StringTokenizer tokenizer = new StringTokenizer(str, "\t,");
                String name = (String)tokenizer.nextToken();
                name = name.trim(); // remove leading and trailing white spaces
                double x = Double.parseDouble((String)tokenizer.nextToken());
                double y = Double.parseDouble((String)tokenizer.nextToken());
                tst.put(name, new Point(x, y));
            }
            in.close();
            return tst;
        } catch (IOException e) {
            this.tst = null;
            throw e;
        }
    }
    
    private String askUserToSelectSingleName(String[] names) {
        final Icon icon = ika.mapanalyst.ApplicationInfo.getApplicationIcon();
        Object selectedValue = JOptionPane.showInputDialog(null,
                "Several places meet your entry.\nPlease select one:",
                "Select Place",
                JOptionPane.INFORMATION_MESSAGE, icon,
                names, names[0]);
        return (String)selectedValue;
    }
    
    public void askUserForFile() {
        try {
            String filePath = ika.utils.FileUtils.askFile(null,
                    "ASCII Table with Name - X - Y (CSV)", true);
            if (filePath == null)
                return; // user cancelled
            
            this.readFile(filePath);
        }catch (Exception e) {
            final Icon icon = ika.mapanalyst.ApplicationInfo.getApplicationIcon();
            JOptionPane.showMessageDialog(null,
                    "Could not read the file.", "Invalid File",
                    JOptionPane.ERROR_MESSAGE, icon);
            this.tst = null;
        }
    }
    
    public Object[] askUserForPlace() {
        if (!this.isInitialized())
            throw new IllegalStateException("Place list not initialized");
        
        // ask user for name
        String msg = "Enter the name of the location to add:";
        String title = "Place Name";
        final Icon icon = ika.mapanalyst.ApplicationInfo.getApplicationIcon();
        Object key = JOptionPane.showInputDialog(null, msg, title, 
                JOptionPane.QUESTION_MESSAGE, icon, null, null);
        if (key == null || "".equals((String)key))    // user cancelled
            return null;
        
        // search all place names which start with the entered string.
        Object[] obj = this.getSimilarStarts((String)key);
        String[] names = (String[])obj[0];
        Point[] pts = (Point[])obj[1];
        
        // test if no place was found
        if (names.length == 0) {
            // no place found with specified name
            msg = "Could not find any place name starting with \"" + key + "\".";
            title = "No Place Found";
            JOptionPane.showMessageDialog(null, msg, title, 
                    JOptionPane.ERROR_MESSAGE, icon);
            return null;
        }
        
        // test if too many places were found
        if (names.length >= MAX_INCOMPLETE_PLACE_NAMES ) {
            
            // if first place name is an exact match, take the first one.
            if (names[0].equals((String)key))
                return new Object[] {names[0], pts[0]};
                
            // List contains too many entries. User must enter more characters.
            msg = "Too many places starting with \"" + key + "\" were found.\n";
            msg += "Please enter more of the place name.";
            title = "No Place Found";
            JOptionPane.showMessageDialog(null, msg, title, 
                    JOptionPane.ERROR_MESSAGE, icon);
            return null;
        }
        
        // test if more than one place was found
        if (names.length > 1) {
            key = this.askUserToSelectSingleName(names);
            return new Object[] {key, tst.get((String)key)};
        }
        
        // return single found place
        return new Object[] {names[0], pts[0]};
    }
    
    public boolean isInitialized() {
        return this.tst != null;
    }
}
