package com.openvoxel.ui;

/**
 * Base interface for GUI (Graphical User Interface) elements in the Open-Voxel engine.
 * GUIs are similar to UIs but typically represent more complex interactive elements
 * like menus, dialogs, or HUDs.
 * 
 * <p>A GUI is responsible for rendering itself using the provided renderer
 * and handling mouse clicks within its bounds. Window dimensions are provided
 * to allow proper layout calculations.</p>
 */
public interface GUI {
    
    /**
     * Renders the GUI element.
     * 
     * @param renderer The GLUIRenderer used to draw GUI elements
     * @param windowWidth The width of the window in pixels
     * @param windowHeight The height of the window in pixels
     */
    void render(GLUIRenderer renderer, int windowWidth, int windowHeight);
    
    /**
     * Handles mouse click events.
     * 
     * @param x The x-coordinate of the mouse click in window space (0 = left edge)
     * @param y The y-coordinate of the mouse click in window space (0 = top edge)
     * @param button The mouse button that was clicked (GLFW button constants)
     * @param windowWidth The width of the window in pixels
     * @param windowHeight The height of the window in pixels
     * @return true if the click was handled by this GUI, false otherwise
     */
    boolean onMouseClick(int x, int y, int button, int windowWidth, int windowHeight);
    
    /**
     * Returns whether this GUI should block input to lower layers.
     * 
     * @return true if this GUI blocks input to GUIs beneath it
     */
    default boolean blocksInput() {
        return true;
    }
}
