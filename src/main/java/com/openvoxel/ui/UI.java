package com.openvoxel.ui;

/**
 * Base interface for UI elements in the Open-Voxel engine.
 * UIs are rendered on screen and can handle mouse input.
 * 
 * <p>A UI is responsible for rendering itself using the provided renderer
 * and handling mouse clicks within its bounds. Window dimensions are provided
 * to allow proper layout calculations.</p>
 */
public interface UI {
    
    /**
     * Renders the UI element.
     * 
     * @param renderer The GLUIRenderer used to draw UI elements
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
     * @return true if the click was handled by this UI, false otherwise
     */
    boolean onMouseClick(int x, int y, int button, int windowWidth, int windowHeight);
    
    /**
     * Returns whether this UI should block input to lower layers.
     * 
     * @return true if this UI blocks input to UIs beneath it
     */
    default boolean blocksInput() {
        return true;
    }
}
