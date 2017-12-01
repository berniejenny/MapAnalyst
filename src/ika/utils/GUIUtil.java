/*
 * GUIUtil.java
 *
 * Created on September 11, 2007, 10:54 PM
 *
 */
package ika.utils;

import java.awt.Component;
import java.awt.Frame;
import java.awt.im.InputContext;
import java.util.Locale;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/**
 *
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich
 */
public class GUIUtil {

    /**
     * Adds support for plus and minus menu commands, typically for zooming in
     * and out. This is designed for menu items with key accelerators using
     * KeyEvent.VK_ADD and KeyEvent.VK_SUBTRACT (which are typically on the
     * numerical key pad). It adds support for KeyEvent.VK_MINUS and
     * KeyEvent.VK_PLUS, and KeyEvent.VK_EQUALS for keyboard layouts with the
     * plus sign as secondary key for the equals.
     *
     * @param zoomInAction action to call when the + key and the menu shortcut
     * key are pressed
     * @param zoomOutAction action to call when the - key and the menu shortcut
     * key are pressed
     * @param component component that will receive events and store actions
     */
    public static void zoomMenuCommands(Action zoomInAction, Action zoomOutAction, JComponent component) {
        InputMap inputMap = component.getInputMap();
        ActionMap actionMap = component.getActionMap();
        zoomMenuCommands(inputMap, actionMap, zoomInAction, zoomOutAction);
    }

    /**
     * Adds support for plus and minus menu commands, typically for zooming in
     * and out. This is designed for menu items with key accelerators using
     * KeyEvent.VK_ADD and KeyEvent.VK_SUBTRACT (which are typically on the
     * numerical key pad). It adds support for KeyEvent.VK_MINUS and
     * KeyEvent.VK_PLUS, and KeyEvent.VK_EQUALS for keyboard layouts with the
     * plus sign as secondary key for the equals.
     *
     * @param inputMap add key event to this InputMap
     * @param actionMap add action to this ActionMap
     * @param zoomInAction action to call when the + key and the menu shortcut
     * key are pressed
     * @param zoomOutAction action to call when the - key and the menu shortcut
     * key are pressed
     */
    public static void zoomMenuCommands(InputMap inputMap, ActionMap actionMap, Action zoomInAction, Action zoomOutAction) {
        int menuKeyMask = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

        // add support for minus key
        KeyStroke minusMenueKeyStroke = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_MINUS, menuKeyMask);
        inputMap.put(minusMenueKeyStroke, "zoomOutWithMinusKey");
        actionMap.put("zoomOutWithMinusKey", zoomOutAction);

        // add support for plus key to zoom in. This only works if the keyboard 
        // layout allows access to the plus character without pressing the shift 
        // key, which is not the case for US and UK keyboards.
        KeyStroke plusMenuKeyStroke = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_PLUS, menuKeyMask);
        inputMap.put(plusMenuKeyStroke, "zoomInWithPlusKey");
        actionMap.put("zoomInWithPlusKey", zoomInAction);

        // add support for cases where the plus character is the secondary 
        // character for the equal sign key. That is, plus is accessed by pressing
        // the shift key and the equal key. This is the case for US and UK 
        // keyboard layouts, which are also used in Ireland, India, Australia, Canada,
        // Hong Kong, New Zealand, South Africa, Malaysia, Singapore and Philippines.
        // See https://stackoverflow.com/questions/15605109/java-keybinding-plus-key
        // and https://en.wikipedia.org/wiki/QWERTY
        // The French Canadian keyboard also has = and + on the same key.
        Locale locale = InputContext.getInstance().getLocale();
        String isoCountry = locale.getISO3Country();
        // https://en.wikipedia.org/wiki/ISO_3166-1_alpha-3
        if ("USA".equals(isoCountry)
                || "GBR".equals(isoCountry)
                || "IRL".equals(isoCountry)
                || "IND".equals(isoCountry)
                || "AUS".equals(isoCountry)
                || "CAN".equals(isoCountry)
                || "HKG".equals(isoCountry)
                || "NZL".equals(isoCountry)
                || "ZAF".equals(isoCountry)
                || "MYS".equals(isoCountry)
                || "SGP".equals(isoCountry)
                || "PHL".equals(isoCountry)) {
            KeyStroke euqalsMenuKeyStroke = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_EQUALS, menuKeyMask);
            inputMap.put(euqalsMenuKeyStroke, "zoomInWithEqualsKey");
            actionMap.put("zoomInWithEqualsKey", zoomInAction);
        }
    }
    
    public static Frame getOwnerFrame(Component component) {
        while ((component != null) && !(component instanceof Frame)) {
            component = component.getParent();
        }
        return ((Frame) component);
    }

    /**
     * Returns the front most Frame. If possible, the Frame that currently has
     * the focus and is visible is returned.
     * @return The frontmost Frame or null if no Frame can be found.
     */
    public static Frame getFrontMostFrame() {

        // search visible window which is focused
        Frame[] frames = Frame.getFrames();
        for (int i = 0; i < frames.length; i++) {
            Frame frame = frames[i];
            if (frame != null && frame.isVisible() && frame.isFocused()) {
                return frame;
            }
        }

        // search visible window
        frames = Frame.getFrames();
        for (int i = 0; i < frames.length; i++) {
            Frame frame = frames[i];
            if (frame != null && frame.isVisible()) {
                return frame;
            }
        }

        // search window
        frames = Frame.getFrames();
        for (int i = 0; i < frames.length; i++) {
            Frame frame = frames[i];
            if (frame != null) {
                return frame;
            }
        }

        return null;
    }
}