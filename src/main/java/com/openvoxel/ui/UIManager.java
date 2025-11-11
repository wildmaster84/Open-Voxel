package com.openvoxel.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Singleton manager for handling UI and GUI elements in the game.
 * The UIManager maintains separate stacks for UIs (render-only) and GUIs (on-tick).
 * 
 * <p>The UIManager must be integrated with the game loop:
 * <ul>
 *   <li>Call {@link #tick(long)} from the main game tick to update all active GUIs</li>
 *   <li>Call {@link #render()} from the render loop to draw all active UIs and GUIs</li>
 *   <li>Forward mouse clicks to {@link #onMouseClick(int, int, int)}</li>
 *   <li>Forward key presses to {@link #onKeyPress(int)}</li>
 * </ul>
 * 
 * <p>Example integration:
 * <pre>
 * // In game tick loop:
 * UIManager.get().tick(tickDelta);
 * 
 * // In render loop (after main game rendering):
 * UIManager.get().render();
 * 
 * // In mouse event handler:
 * UIManager.get().onMouseClick(mouseX, mouseY, button);
 * 
 * // In key event handler:
 * UIManager.get().onKeyPress(keyCode);
 * </pre>
 */
public class UIManager {
    
    private static final UIManager INSTANCE = new UIManager();
    
    private final List<UI> activeUIs = new ArrayList<>();
    private final List<GUI> activeGUIs = new ArrayList<>();
    
    /**
     * Private constructor for singleton pattern.
     */
    private UIManager() {
    }
    
    /**
     * Gets the singleton instance of the UIManager.
     * 
     * @return The UIManager instance
     */
    public static UIManager get() {
        return INSTANCE;
    }
    
    /**
     * Opens and adds a UI to the active UI stack.
     * The UI's {@link UI#onOpen()} method will be called immediately.
     * 
     * @param ui The UI to open
     */
    public void openUI(UI ui) {
        if (ui == null) {
            throw new IllegalArgumentException("UI cannot be null");
        }
        activeUIs.add(ui);
        ui.onOpen();
    }
    
    /**
     * Opens and adds a GUI to the active GUI stack.
     * The GUI's {@link GUI#onOpen()} method will be called immediately.
     * 
     * @param gui The GUI to open
     */
    public void openGUI(GUI gui) {
        if (gui == null) {
            throw new IllegalArgumentException("GUI cannot be null");
        }
        activeGUIs.add(gui);
        gui.onOpen();
    }
    
    /**
     * Closes and removes the topmost UI from the stack.
     * The UI's {@link UI#onClose()} method will be called before removal.
     * If there are no active UIs, this method does nothing.
     */
    public void closeTopUI() {
        if (!activeUIs.isEmpty()) {
            UI ui = activeUIs.remove(activeUIs.size() - 1);
            ui.onClose();
        }
    }
    
    /**
     * Closes and removes the topmost GUI from the stack.
     * The GUI's {@link GUI#onClose()} method will be called before removal.
     * If there are no active GUIs, this method does nothing.
     */
    public void closeTopGUI() {
        if (!activeGUIs.isEmpty()) {
            GUI gui = activeGUIs.remove(activeGUIs.size() - 1);
            gui.onClose();
        }
    }
    
    /**
     * Closes and removes all active UIs.
     * Each UI's {@link UI#onClose()} method will be called before removal.
     */
    public void closeAllUIs() {
        while (!activeUIs.isEmpty()) {
            closeTopUI();
        }
    }
    
    /**
     * Closes and removes all active GUIs.
     * Each GUI's {@link GUI#onClose()} method will be called before removal.
     */
    public void closeAllGUIs() {
        while (!activeGUIs.isEmpty()) {
            closeTopGUI();
        }
    }
    
    /**
     * Closes all UIs and GUIs.
     * Equivalent to calling {@link #closeAllUIs()} and {@link #closeAllGUIs()}.
     */
    public void closeAll() {
        closeAllUIs();
        closeAllGUIs();
    }
    
    /**
     * Ticks all active GUIs, calling their {@link GUI#onTick(long)} methods.
     * This method should be called from the main game tick loop.
     * 
     * @param tickDelta The time elapsed since the last tick in milliseconds
     */
    public void tick(long tickDelta) {
        // Tick all GUIs in order (defensive copy to avoid concurrent modification)
        List<GUI> guisCopy = new ArrayList<>(activeGUIs);
        for (GUI gui : guisCopy) {
            gui.onTick(tickDelta);
        }
    }
    
    /**
     * Renders all active UIs and GUIs.
     * This method should be called from the render loop, after the main game rendering.
     * UIs and GUIs are rendered in the order they were added.
     */
    public void render() {
        // Render all UIs
        for (UI ui : activeUIs) {
            ui.render();
        }
        
        // Render all GUIs
        for (GUI gui : activeGUIs) {
            gui.render();
        }
    }
    
    /**
     * Dispatches a mouse click event to the topmost GUI.
     * If there are no active GUIs, this method does nothing.
     * 
     * @param x The x-coordinate of the mouse cursor in screen space
     * @param y The y-coordinate of the mouse cursor in screen space
     * @param button The mouse button that was clicked (0=left, 1=right, 2=middle)
     */
    public void onMouseClick(int x, int y, int button) {
        if (!activeGUIs.isEmpty()) {
            GUI topGUI = activeGUIs.get(activeGUIs.size() - 1);
            topGUI.onMouseClick(x, y, button);
        }
    }
    
    /**
     * Dispatches a key press event to the topmost GUI.
     * If there are no active GUIs, this method does nothing.
     * 
     * @param key The GLFW key code of the pressed key
     */
    public void onKeyPress(int key) {
        if (!activeGUIs.isEmpty()) {
            GUI topGUI = activeGUIs.get(activeGUIs.size() - 1);
            topGUI.onKeyPress(key);
        }
    }
    
    /**
     * Checks if there are any active UIs.
     * 
     * @return true if there is at least one active UI, false otherwise
     */
    public boolean hasActiveUIs() {
        return !activeUIs.isEmpty();
    }
    
    /**
     * Checks if there are any active GUIs.
     * 
     * @return true if there is at least one active GUI, false otherwise
     */
    public boolean hasActiveGUIs() {
        return !activeGUIs.isEmpty();
    }
    
    /**
     * Gets the number of active UIs.
     * 
     * @return The number of active UIs
     */
    public int getActiveUICount() {
        return activeUIs.size();
    }
    
    /**
     * Gets the number of active GUIs.
     * 
     * @return The number of active GUIs
     */
    public int getActiveGUICount() {
        return activeGUIs.size();
    }
}
