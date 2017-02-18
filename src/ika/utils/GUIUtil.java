/*
 * GUIUtil.java
 *
 * Created on September 11, 2007, 10:54 PM
 *
 */
package ika.utils;

import java.awt.Component;
import java.awt.Frame;

/**
 *
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich
 */
public class GUIUtil {

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