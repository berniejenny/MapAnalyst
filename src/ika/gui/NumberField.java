/*
 * NumberField.java
 *
 * Created on September 15, 2005, 10:44 AM
 *
 */

package ika.gui;

import java.awt.event.FocusAdapter;
import javax.swing.*;
import java.text.*;
import java.awt.event.*;
import javax.swing.text.DefaultFormatter;
import javax.swing.text.DefaultFormatterFactory;

/**
 * A field to enter a floating point number. The user is forced to enter only
 * characters that can be interpreded as floating point number. The focus is kept
 * if the current entry is not a valid number.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class NumberField extends JFormattedTextField {
    
    /** An optional NumberFieldChecker that can accept or reject a value.
     * Useful if the possible entries have to respect certain conditions, 
     * e.g. values must be within an interval
     */
    private NumberFieldChecker numberFieldChecker;
    
    /** Creates a new instance of NumberField */
    public NumberField() {
        
        // define a custom input verifier
        this.setInputVerifier(new NumberVerifier());
        
        // create a formatter for displaying and editing
        DefaultFormatter formatter = new DefaultFormatter();
        
        // allow the user to completely delete all text
        formatter.setAllowsInvalid(true);
        
        // typing should insert new characters and not overwrite old ones
        formatter.setOverwriteMode(false);
        
        // commit on edit, otherwise a property change event is generated
        // when the field loses the focus and the value changed since it gained
        // the focus.
        formatter.setCommitsOnValidEdit(true);
        
        // getValue should return a Double object
        formatter.setValueClass(java.lang.Double.class);
        
        // the kind of formatter getFormatter should return
        this.setFormatterFactory(new DefaultFormatterFactory(formatter));
        
        // default value is 0
        this.setValue(new Double(0));
    }
    
    /** Set the optional NumberFieldChecker */
    public void setNumberFieldChecker(NumberFieldChecker numberFieldChecker) {
        this.numberFieldChecker = numberFieldChecker;
    }
    
    /** Get the optional NumberFieldChecker. Can return null. */
    public NumberFieldChecker getNumberFieldChecker() {
        return this.numberFieldChecker;
    }
    
    /** Returns the current number. This is the preferred way to retrieve the value. */
    public double getNumber() throws java.text.ParseException {
        javax.swing.JFormattedTextField.AbstractFormatter formatter = this.getFormatter();
        if (formatter != null) {
            this.commitEdit();
            String text = this.getText();
            Double nbr = (Double)formatter.stringToValue(text);
            return nbr.doubleValue();
        }
        throw new java.text.ParseException("no formatter found", 0);
    }
    
    /** The the new value */
    public void setNumber(double number) {
        this.setValue(new Double(number));
    }
    
    /** A private InputVerifier to make sure the user enters only valid floating
     * point numbers.
     */
    private class NumberVerifier extends javax.swing.InputVerifier {
        
        public boolean verify(JComponent input) {
            if (input instanceof JFormattedTextField) {
                NumberField numberField = (NumberField)input;
                javax.swing.JFormattedTextField.AbstractFormatter formatter =
                        numberField.getFormatter();
                if (formatter != null) {
                    String text = numberField.getText();
                    try {
                        // stringToValue throws an exception if entry is invalid
                        formatter.stringToValue(text);
                        
                        // ask the optional NumberFieldChecker if the entered value is ok.
                        if (numberFieldChecker != null) {
                            try {
                                // suspend this NumberVerifier
                                // Otherwise this method would be called if the 
                                // numberFieldChecker changes the current focus,
                                // e.g. by generating a dialog.
                                numberField.setInputVerifier(null);
                                return numberFieldChecker.testValue(numberField);
                            } finally {
                                // reset this NumberVerifier
                                numberField.setInputVerifier(this);
                            }
                        }
                    } catch (ParseException pe) {
                        return false;
                    }
                }
            }
            return true;
        }
        public boolean shouldYieldFocus(JComponent input) {
            return verify(input);
        }
    }
}
