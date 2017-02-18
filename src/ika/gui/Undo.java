/*
 * Undo.java
 *
 * Created on August 28, 2005, 10:28 AM
 *
 */

package ika.gui;

import java.util.*;
import javax.swing.*;

/**
 *
 * @author jenny
 */
public class Undo {
    
    private LinkedList list = new LinkedList();
    
    private int undoID = -1;
    
    private JMenuItem undoMenuItem = null;
    
    private JMenuItem redoMenuItem = null;
    
    private class UndoItem {
        public String name;
        public Object obj;
        
        public UndoItem(String name, Object obj) {
            this.name = name;
            this.obj = obj;
        }
    }
    
    /** Creates a new instance of Undo */
    public Undo() {
    }
    
    public void setUndoMenuItems(JMenuItem undoMenuItem, JMenuItem redoMenuItem) {
        this.undoMenuItem = undoMenuItem;
        this.redoMenuItem = redoMenuItem;
    }
    
    public void add(String name, Object undoItem) {
        
        // cut off all undoItems after undoID
        final int nbrItemsToRemove = this.list.size() - this.undoID - 1;
        for (int i = 0; i < nbrItemsToRemove; ++i) {
            this.list.removeLast();
        }
        
        // add undoItem to list
        this.list.add(new UndoItem(name, undoItem));
        
        // undoID points at the last item in list.
        this.undoID = list.size() - 1;
        
        this.updateMenuItems();
    }
    
    private void updateMenuItems() {
        final int nbrItems = list.size();
        
        // update undo menu
        if (this.undoMenuItem != null) {
            final boolean canUndo= nbrItems > 0  && this.undoID > 0;
            this.undoMenuItem.setEnabled(canUndo);
            String str = "Undo";
            if (canUndo) {
                UndoItem undoItem = (UndoItem)list.get(this.undoID);
                if (undoItem != null && undoItem.name != null)
                    str += " " + undoItem.name;
            }
            this.undoMenuItem.setText(str);
        }
        // update redo menu
        if (this.redoMenuItem != null) {
            final boolean canRedo = nbrItems > 0  && this.undoID < nbrItems - 1;
            this.redoMenuItem.setEnabled(canRedo);
            String str = "Redo";
            if (canRedo) {
               UndoItem redoItem = (UndoItem)list.get(this.undoID + 1);
               if (redoItem != null && redoItem.name != null)
                    str += " " + redoItem.name;
            }
            this.redoMenuItem.setText(str);
        }
    }
    
    public void reset() {
        this.list.clear();
        this.undoID = -1;
    }
    
    public Object getUndo() {
        if (this.list.size() > 0 && this.undoID > 0) {
            UndoItem undoItem = (UndoItem)this.list.get(--this.undoID);
            this.updateMenuItems();
            return undoItem.obj;
        } else
            return null;
    }
    
    public Object getRedo() {
        if (this.list.size() > 0
                && this.undoID >= -1
                && this.undoID < this.list.size() - 1) {
            UndoItem undoItem = (UndoItem)this.list.get(++this.undoID);
            this.updateMenuItems();
            return undoItem.obj;
        } else {
            return null;
        }
    }
    
    public String toString(UndoItem undoItem) {
        if (undoItem == null)
            return "";
        
        String str = " Name : " + undoItem.name;
        str += " Value : " + undoItem.obj;
        return str;
    }
    
    public String toString(Object obj) {
        if (obj != null)
            return "Value : " + obj.toString();
        return "";
    }
    
    public String toString() {
        StringBuffer str = new StringBuffer();
        final int nbrItems = this.list.size();
        for (int i = 0; i < nbrItems; ++i) {
            str.append("#" + i);
            str.append(this.toString((UndoItem)list.get(i)));
            str.append("\n");
        }
        return str.toString();
    }
    
    public static void main(String [] str){
        Undo undo = new Undo();
        System.out.println("undo: " + undo.toString(undo.getUndo()));
        System.out.println("redo: " + undo.toString(undo.getRedo()));
        undo.add("1", new Integer(1));
        undo.add("2", new Integer(2));
        undo.add("3", new Integer(3));
        undo.add("4", new Integer(4));
        undo.add("5", new Integer(5));
        undo.add("6", new Integer(6));
        System.out.println(undo);
        System.out.println("undo: " + undo.toString(undo.getUndo()));
        System.out.println("undo: " + undo.toString(undo.getUndo()));
        System.out.println("undo: " + undo.toString(undo.getUndo()));
        System.out.println("redo: " + undo.toString(undo.getRedo()));
        System.out.println("redo: " + undo.toString(undo.getRedo()));
        undo.add("7", new Integer(7));
        System.out.println(undo);
        System.out.println("redo: " + undo.toString(undo.getRedo()));
        System.out.println("redo: " + undo.toString(undo.getRedo()));
        System.out.println("undo: " + undo.toString(undo.getUndo()));
        undo.add("8", new Integer(8));
        System.out.println(undo);
        
        undo.reset();
        System.out.println("Reset\n" + undo);
        System.out.println("redo: " + undo.toString(undo.getRedo()));
        System.out.println("undo: " + undo.toString(undo.getUndo()));
        undo.add("1", new Integer(1));
        undo.add("2", new Integer(2));
        System.out.println(undo);
        System.out.println("undo: " + undo.toString(undo.getUndo()));
        System.out.println("undo: " + undo.toString(undo.getUndo()));
        System.out.println("undo: " + undo.toString(undo.getUndo()));
        System.out.println("redo: " + undo.toString(undo.getRedo()));
        undo.add("3", new Integer(3));
        System.out.println(undo);
    }
}
