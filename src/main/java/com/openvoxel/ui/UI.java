package com.openvoxel.ui;

/**
 * Base class for UI elements that render off the game tick.
 * UIs are render-only components that draw directly in the render loop
 * without receiving tick updates. They are suitable for overlays, HUDs,
 * and non-interactive visual elements that don't require game logic updates.
 * 
 * <p>Lifecycle methods:
 * <ul>
 *   <li>{@link #onOpen()} - Called once when the UI is opened via UIManager</li>
 *   <li>{@link #render()} - Called every frame from the render loop</li>
 *   <li>{@link #onClose()} - Called once when the UI is closed</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>
 * public class MyOverlay extends UI {
 *     &#64;Override
 *     public void render() {
 *         GLUIRenderer.drawPanel(10, 10, 200, 100, 0.2f, 0.2f, 0.2f, 0.8f);
 *     }
 * }
 * 
 * // Open the UI
 * UIManager.get().openUI(new MyOverlay());
 * </pre>
 */
public abstract class UI {
    
    /**
     * Called when this UI is opened and added to the UIManager.
     * Override this to initialize resources or state.
     */
    public void onOpen() {
        // Default implementation does nothing
    }
    
    /**
     * Called every frame from the render loop to draw this UI.
     * This method should contain all rendering logic for the UI.
     * Use GLUIRenderer utilities or direct OpenGL calls to draw elements.
     */
    public abstract void render();
    
    /**
     * Called when this UI is closed and removed from the UIManager.
     * Override this to clean up resources or state.
     */
    public void onClose() {
        // Default implementation does nothing
    }
}
