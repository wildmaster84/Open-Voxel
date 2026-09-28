package engine.ui;

import org.lwjgl.glfw.GLFW;

import engine.gui.GUI;

import java.util.ArrayList;
import java.util.List;

public class UIManager {
    
    private static final UIManager INSTANCE = new UIManager();
    
    private final List<GUI> activeGUIs = new ArrayList<>();
    
    private long window = 0;
    private double savedMouseX = 0;
    private double savedMouseY = 0;
    private boolean cursorWasDisabled = false;
    
    private UIManager() {}
    
    public static UIManager get() {
        return INSTANCE;
    }

    public void setWindow(long window) {
        this.window = window;
        // Route unicode text input to the top GUI (for TextField widgets).
        GLFW.glfwSetCharCallback(window, (w, codepoint) -> onCharTyped(codepoint));
    }

    public long getWindow() {
        return window;
    }
    
    public void openGUI(GUI gui) {
        if (gui == null) {
            throw new IllegalArgumentException("GUI cannot be null");
        }
        
        boolean wasEmpty = activeGUIs.isEmpty();
        activeGUIs.add(gui);
        gui.onOpen();
        
        if (wasEmpty) {
            showCursor();
            lastOpenNanos = System.nanoTime();
        }
    }

    public void closeTopGUI() {
        if (!activeGUIs.isEmpty()) {
            GUI gui = activeGUIs.remove(activeGUIs.size() - 1);
            gui.onClose();
            
            if (activeGUIs.isEmpty()) {
                hideCursor();
                lastCloseNanos = System.nanoTime();
            }
        }
    }

    private long lastCloseNanos = 0;
    private long lastOpenNanos = 0;

    /** Timestamp (System.nanoTime) of the most recent GUI close. */
    public long getLastCloseNanos() { return lastCloseNanos; }

    /** Timestamp (System.nanoTime) of the most recent open-from-empty. */
    public long getLastOpenNanos() { return lastOpenNanos; }

    public void closeAllGUIs() {
        while (!activeGUIs.isEmpty()) {
            closeTopGUI();
        }
    }

    public void closeAll() {
        closeAllGUIs();
    }

    public void tick(long tickDelta) {
        // Tick all GUIs in order (defensive copy to avoid concurrent modification)
        List<GUI> guisCopy = new ArrayList<>(activeGUIs);
        for (GUI gui : guisCopy) {
            gui.onTick(tickDelta);
        }
    }
    
    public void render() {
        for (GUI gui : activeGUIs) {
            gui.render();
        }
    }

    public void onMouseClick(int x, int y, int button) {
        if (!activeGUIs.isEmpty()) {
            GUI topGUI = activeGUIs.get(activeGUIs.size() - 1);
            topGUI.onMouseClick(x, y, button);
        }
    }

    public void onKeyPress(int key) {
        if (!activeGUIs.isEmpty()) {
            // Mirror of InputHandler's close-suppression: the ESC PRESS event
            // that opened this GUI (queued before handleClicks saw the key)
            // can arrive here right after the open and instantly close it.
            // Swallow ESC within a short window of the open.
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
                    && System.nanoTime() - lastOpenNanos < 200_000_000L) {
                return;
            }
            GUI topGUI = activeGUIs.get(activeGUIs.size() - 1);
            topGUI.onKeyPress(key);
        }
    }

    public void onCharTyped(int codepoint) {
        if (!activeGUIs.isEmpty()) {
            GUI topGUI = activeGUIs.get(activeGUIs.size() - 1);
            topGUI.onCharTyped(codepoint);
        }
    }
    
    public boolean hasActiveGUIs() {
        return !activeGUIs.isEmpty();
    }

    private void showCursor() {
        if (window == 0) {
            return;
        }
        
        int currentMode = GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR);
        cursorWasDisabled = (currentMode == GLFW.GLFW_CURSOR_DISABLED);
        
        if (cursorWasDisabled) {
            double[] xpos = new double[1];
            double[] ypos = new double[1];
            GLFW.glfwGetCursorPos(window, xpos, ypos);
            savedMouseX = xpos[0];
            savedMouseY = ypos[0];
        }
        
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
    }

    private void hideCursor() {
        if (window == 0) {
            return;
        }
        
        if (cursorWasDisabled) {
            GLFW.glfwSetCursorPos(window, savedMouseX, savedMouseY);
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        }
    }
}
