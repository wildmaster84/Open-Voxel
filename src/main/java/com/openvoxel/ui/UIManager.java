package com.openvoxel.ui;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Singleton manager for handling UI and GUI elements in the game.
 * The UIManager maintains separate stacks for UIs (render-only) and GUIs (on-tick).
 * 
 * <p>The UIManager must be integrated with the game loop:
 * <ul>
 *   <li>Call {@link #setWindow(long)} once during initialization to enable cursor management</li>
 *   <li>Call {@link #tick(long)} from the main game tick to update all active GUIs</li>
 *   <li>Call {@link #render()} from the render loop to draw all active UIs and GUIs</li>
 *   <li>Forward mouse clicks to {@link #onMouseClick(int, int, int)}</li>
 *   <li>Forward key presses to {@link #onKeyPress(int)}</li>
 * </ul>
 * 
 * <p>Cursor Management:
 * The UIManager automatically shows/hides the cursor when UIs or GUIs are opened/closed.
 * It also saves the last mouse position before showing the cursor to prevent camera snap
 * when the cursor is hidden again.
 * 
 * <p>Example integration:
 * <pre>
 * // During initialization:
 * UIManager.get().setWindow(window);
 * 
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
    
    private long window = 0;
    private double savedMouseX = 0;
    private double savedMouseY = 0;
    private boolean cursorWasDisabled = false;
    
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
     * Sets the GLFW window handle for cursor management.
     * This must be called during initialization to enable automatic cursor show/hide.
     * 
     * @param window The GLFW window handle
     */
    public void setWindow(long window) {
        this.window = window;
    }
    
    /**
     * Opens and adds a UI to the active UI stack.
     * The UI's {@link UI#onOpen()} method will be called immediately.
     * Automatically shows the cursor if this is the first UI/GUI opened.
     * 
     * @param ui The UI to open
     */
    public void openUI(UI ui) {
        if (ui == null) {
            throw new IllegalArgumentException("UI cannot be null");
        }
        
        boolean wasEmpty = activeUIs.isEmpty() && activeGUIs.isEmpty();
        activeUIs.add(ui);
        ui.onOpen();
        
        if (wasEmpty) {
            showCursor();
        }
    }
    
    /**
     * Opens and adds a GUI to the active GUI stack.
     * The GUI's {@link GUI#onOpen()} method will be called immediately.
     * Automatically shows the cursor if this is the first UI/GUI opened.
     * 
     * @param gui The GUI to open
     */
    public void openGUI(GUI gui) {
        if (gui == null) {
            throw new IllegalArgumentException("GUI cannot be null");
        }
        
        boolean wasEmpty = activeUIs.isEmpty() && activeGUIs.isEmpty();
        activeGUIs.add(gui);
        gui.onOpen();
        
        if (wasEmpty) {
            showCursor();
        }
    }
    
    /**
     * Closes and removes the topmost UI from the stack.
     * The UI's {@link UI#onClose()} method will be called before removal.
     * If there are no active UIs, this method does nothing.
     * Automatically hides the cursor if this was the last UI/GUI.
     */
    public void closeTopUI() {
        if (!activeUIs.isEmpty()) {
            UI ui = activeUIs.remove(activeUIs.size() - 1);
            ui.onClose();
            
            if (activeUIs.isEmpty() && activeGUIs.isEmpty()) {
                hideCursor();
            }
        }
    }
    
    /**
     * Closes and removes the topmost GUI from the stack.
     * The GUI's {@link GUI#onClose()} method will be called before removal.
     * If there are no active GUIs, this method does nothing.
     * Automatically hides the cursor if this was the last UI/GUI.
     */
    public void closeTopGUI() {
        if (!activeGUIs.isEmpty()) {
            GUI gui = activeGUIs.remove(activeGUIs.size() - 1);
            gui.onClose();
            
            if (activeUIs.isEmpty() && activeGUIs.isEmpty()) {
                hideCursor();
            }
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
    
    /**
     * Shows the cursor and saves the current mouse position.
     * This prevents camera snap when the cursor is hidden later.
     */
    private void showCursor() {
        if (window == 0) {
            return;
        }
        
        // Check if cursor is currently disabled
        int currentMode = GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR);
        cursorWasDisabled = (currentMode == GLFW.GLFW_CURSOR_DISABLED);
        
        if (cursorWasDisabled) {
            // Save current mouse position before showing cursor
            double[] xpos = new double[1];
            double[] ypos = new double[1];
            GLFW.glfwGetCursorPos(window, xpos, ypos);
            savedMouseX = xpos[0];
            savedMouseY = ypos[0];
        }
        
        // Show the cursor
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
    }
    
    /**
     * Hides the cursor and restores the saved mouse position.
     * This prevents camera snap by resetting the cursor to where it was.
     */
    private void hideCursor() {
        if (window == 0) {
            return;
        }
        
        if (cursorWasDisabled) {
            // Restore the saved mouse position before hiding cursor
            // This prevents camera snap
            GLFW.glfwSetCursorPos(window, savedMouseX, savedMouseY);
            
            // Hide the cursor
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        }
    }
}
