/*
 * IconLoader.java
 *
 * Created on April 14, 2005, 11:24 AM
 */

package ika.icons;

import javax.swing.*;

/**
 * From the SUN Tutorial "How to Use Icons"<br>
 *
 * The first argument to the ImageIcon constructor is relative to the location of
 * the class, and will be resolved to an absolute URL. The description argument is
 * a string that allows assistive technologies to help a visually impaired user
 * understand what information the icon conveys.<br>
 *
 * Generally, applications provide their own set of images used as part of the
 * application, as is the case with the images used by many of our demos. You
 * should use the Class getResource method to obtain the path to the image.
 * This allows the application to verify that the image is available and to provide
 * sensible error handling if it is not. When the image is not part of the
 * application, getResource should not be used and the ImageIcon constructor is
 * used directly. For example: <br>
 * <code>
 * ImageIcon icon = new ImageIcon("/home/sharonz/images/middle.gif",
 *                               "a pretty but meaningless splat");
 * </code>
 * <br>
 * When you specify a filename or URL to an ImageIcon constructor, processing
 * is blocked until after the image data is completely loaded or the data location
 * has proven to be invalid. If the data location is invalid (but non-null), an
 * ImageIcon is still successfully created; it just has no size and, therefore,
 * paints nothing. As we showed in the createImageIcon method, it's wise to first
 * verify that the URL points to an existing file before passing it to the
 * ImageIcon constructor.
 */
public class IconLoader {
    
    /**
     * Returns an ImageIcon, or null if the path was invalid.
     * @param path Path to the file relative to this class!
     * @param description String that allows assistive technologies to help a visually impaired user
     * understand what information the icon conveys
     * @return The loaded ImageIcon or null if the icon cannot be loaded.
     */
    public static ImageIcon createImageIcon(String path,
            String description) {
        
        try {
            java.net.URL imgURL = IconLoader.class.getResource(path);
            if (imgURL != null) {
                ImageIcon imageIcon = new ImageIcon(imgURL, description);
                if (imageIcon.getIconWidth() == 0 
                        || imageIcon.getIconHeight() == 0)
                    imageIcon = null;
                return imageIcon;
            } else {
                System.err.println("Couldn't find file: " + path);
                return null;
            }
        } catch (Exception exc) {
            return null;
        }
    }
    
    
}
