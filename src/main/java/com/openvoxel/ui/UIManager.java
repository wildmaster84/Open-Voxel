package com.openvoxel.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Singleton manager for UI and GUI elements in the Open-Voxel engine.
 * 
 * <p>The UIManager maintains stacks of UI and GUI elements and provides
 * methods for rendering them and handling input. UIs and GUIs are rendered
 * in the order they were added, with the most recently added elements on top.</p>
 * 
 * <p>The manager uses a singleton pattern to ensure only one instance exists
 * throughout the application lifecycle.</p>
 */
public class UIManager {
    
    private static UIManager instance;
    
    private final List<UI> uiStack;
    private final List<GUI> guiStack;
    private final GLUIRenderer renderer;
    
    /**
     * Private constructor for singleton pattern.
     */
    private UIManager() {
        this.uiStack = new ArrayList<>();
        this.guiStack = new ArrayList<>();
        this.renderer = new GLUIRenderer();
    }
    
    /**
     * Gets the singleton instance of UIManager.
     * 
     * @return The UIManager instance
     */
    public static UIManager getInstance() {
        if (instance == null) {
            instance = new UIManager();
        }
        return instance;
    }
    
    /**
     * Adds a UI element to the top of the UI stack.
     * 
     * @param ui The UI element to add
     */
    public void pushUI(UI ui) {
        if (ui != null && !uiStack.contains(ui)) {
            uiStack.add(ui);
        }
    }
    
    /**
     * Removes a UI element from the UI stack.
     * 
     * @param ui The UI element to remove
     */
    public void popUI(UI ui) {
        uiStack.remove(ui);
    }
    
    /**
     * Removes the topmost UI element from the UI stack.
     * 
     * @return The removed UI element, or null if the stack was empty
     */
    public UI popUI() {
        if (!uiStack.isEmpty()) {
            return uiStack.remove(uiStack.size() - 1);
        }
        return null;
    }
    
    /**
     * Adds a GUI element to the top of the GUI stack.
     * 
     * @param gui The GUI element to add
     */
    public void pushGUI(GUI gui) {
        if (gui != null && !guiStack.contains(gui)) {
            guiStack.add(gui);
        }
    }
    
    /**
     * Removes a GUI element from the GUI stack.
     * 
     * @param gui The GUI element to remove
     */
    public void popGUI(GUI gui) {
        guiStack.remove(gui);
    }
    
    /**
     * Removes the topmost GUI element from the GUI stack.
     * 
     * @return The removed GUI element, or null if the stack was empty
     */
    public GUI popGUI() {
        if (!guiStack.isEmpty()) {
            return guiStack.remove(guiStack.size() - 1);
        }
        return null;
    }
    
    /**
     * Clears all UI elements from the UI stack.
     */
    public void clearUIs() {
        uiStack.clear();
    }
    
    /**
     * Clears all GUI elements from the GUI stack.
     */
    public void clearGUIs() {
        guiStack.clear();
    }
    
    /**
     * Clears all UI and GUI elements.
     */
    public void clearAll() {
        clearUIs();
        clearGUIs();
    }
    
    /**
     * Renders all UI and GUI elements.
     * 
     * <p>Elements are rendered in the order they were added, with UIs rendered
     * first, followed by GUIs. The renderer is automatically set up for 2D
     * rendering before rendering begins and restored afterwards.</p>
     * 
     * @param windowWidth The width of the window in pixels
     * @param windowHeight The height of the window in pixels
     */
    public void render(int windowWidth, int windowHeight) {
        if (uiStack.isEmpty() && guiStack.isEmpty()) {
            return;
        }
        
        renderer.begin2D(windowWidth, windowHeight);
        
        // Render UIs first (bottom layer)
        for (UI ui : uiStack) {
            ui.render(renderer, windowWidth, windowHeight);
        }
        
        // Render GUIs on top
        for (GUI gui : guiStack) {
            gui.render(renderer, windowWidth, windowHeight);
        }
        
        renderer.end2D();
    }
    
    /**
     * Handles mouse click events by forwarding them to the topmost UI or GUI.
     * 
     * <p>The click is first offered to the topmost GUI, then to the topmost UI
     * if no GUI handled it. If a UI/GUI handles the click and blocks input,
     * the click is not forwarded to elements beneath it.</p>
     * 
     * @param x The x-coordinate of the mouse click in window space (0 = left edge)
     * @param y The y-coordinate of the mouse click in window space (0 = top edge)
     * @param button The mouse button that was clicked (GLFW button constants)
     * @param windowWidth The width of the window in pixels
     * @param windowHeight The height of the window in pixels
     * @return true if the click was handled by a UI or GUI
     */
    public boolean handleMouseClick(int x, int y, int button, int windowWidth, int windowHeight) {
        // Check GUIs first (top layer), starting from the top of the stack
        for (int i = guiStack.size() - 1; i >= 0; i--) {
            GUI gui = guiStack.get(i);
            if (gui.onMouseClick(x, y, button, windowWidth, windowHeight)) {
                if (gui.blocksInput()) {
                    return true;
                }
            }
        }
        
        // Check UIs if no GUI handled the click
        for (int i = uiStack.size() - 1; i >= 0; i--) {
            UI ui = uiStack.get(i);
            if (ui.onMouseClick(x, y, button, windowWidth, windowHeight)) {
                if (ui.blocksInput()) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Returns whether any UI or GUI elements are currently displayed.
     * 
     * @return true if there are any UI or GUI elements in the stacks
     */
    public boolean hasActiveElements() {
        return !uiStack.isEmpty() || !guiStack.isEmpty();
    }
    
    /**
     * Gets the renderer used by this UIManager.
     * 
     * @return The GLUIRenderer instance
     */
    public GLUIRenderer getRenderer() {
        return renderer;
    }
}
