package com.openvoxel.ui;

/**
 * Base class for GUI elements that tick with the game loop.
 * GUIs receive regular tick updates and can process input events such as
 * mouse clicks and keyboard input. They are suitable for interactive menus,
 * inventories, and other game interfaces that require logic updates.
 * 
 * <p>Lifecycle methods:
 * <ul>
 *   <li>{@link #onOpen()} - Called once when the GUI is opened via UIManager</li>
 *   <li>{@link #onTick(long)} - Called every game tick to update GUI logic</li>
 *   <li>{@link #render()} - Called every frame from the render loop</li>
 *   <li>{@link #onMouseClick(int, int, int)} - Called when mouse is clicked while GUI is active</li>
 *   <li>{@link #onKeyPress(int)} - Called when a key is pressed while GUI is active</li>
 *   <li>{@link #onClose()} - Called once when the GUI is closed</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>
 * public class MyInventory extends GUI {
 *     &#64;Override
 *     public void onTick(long tickDelta) {
 *         // Update inventory logic
 *     }
 *     
 *     &#64;Override
 *     public void render() {
 *         GLUIRenderer.drawPanel(50, 50, 300, 400, 0.1f, 0.1f, 0.1f, 0.9f);
 *     }
 *     
 *     &#64;Override
 *     public void onMouseClick(int x, int y, int button) {
 *         // Handle item clicks
 *     }
 * }
 * 
 * // Open the GUI
 * UIManager.get().openGUI(new MyInventory());
 * </pre>
 */
public abstract class GUI {
    
    /**
     * Called when this GUI is opened and added to the UIManager.
     * Override this to initialize resources or state.
     */
    public void onOpen() {
        // Default implementation does nothing
    }
    
    /**
     * Called every game tick to update this GUI's logic.
     * This is where you should handle time-based updates and game state changes.
     * 
     * @param tickDelta The time elapsed since the last tick in milliseconds
     */
    public abstract void onTick(long tickDelta);
    
    /**
     * Called every frame from the render loop to draw this GUI.
     * This method should contain all rendering logic for the GUI.
     * Use GLUIRenderer utilities or direct OpenGL calls to draw elements.
     */
    public abstract void render();
    
    /**
     * Called when a mouse button is clicked while this GUI is the topmost GUI.
     * 
     * @param x The x-coordinate of the mouse cursor in screen space
     * @param y The y-coordinate of the mouse cursor in screen space
     * @param button The mouse button that was clicked (0=left, 1=right, 2=middle)
     */
    public void onMouseClick(int x, int y, int button) {
        // Default implementation does nothing
    }
    
    /**
     * Called when a key is pressed while this GUI is the topmost GUI.
     * Commonly used to handle ESC key to close the GUI.
     * 
     * @param key The GLFW key code of the pressed key
     */
    public void onKeyPress(int key) {
        // Default implementation does nothing
    }
    
    /**
     * Called when this GUI is closed and removed from the UIManager.
     * Override this to clean up resources or state.
     */
    public void onClose() {
        // Default implementation does nothing
    }
}
