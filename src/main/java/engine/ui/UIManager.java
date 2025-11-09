package engine.ui;

import org.lwjgl.glfw.GLFW;

import engine.gui.GUI;

import java.util.ArrayList;
import java.util.List;

public class UIManager {
    
    private static final UIManager INSTANCE = new UIManager();
    
    private final List<UI> activeUIs = new ArrayList<>();
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
    }
    
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

    public void closeTopUI() {
        if (!activeUIs.isEmpty()) {
            UI ui = activeUIs.remove(activeUIs.size() - 1);
            ui.onClose();
            
            if (activeUIs.isEmpty() && activeGUIs.isEmpty()) {
                hideCursor();
            }
        }
    }
    
    public void closeTopGUI() {
        if (!activeGUIs.isEmpty()) {
            GUI gui = activeGUIs.remove(activeGUIs.size() - 1);
            gui.onClose();
            
            if (activeUIs.isEmpty() && activeGUIs.isEmpty()) {
                hideCursor();
            }
        }
    }

    public void closeAllUIs() {
        while (!activeUIs.isEmpty()) {
            closeTopUI();
        }
    }
    
    public void closeAllGUIs() {
        while (!activeGUIs.isEmpty()) {
            closeTopGUI();
        }
    }

    public void closeAll() {
        closeAllUIs();
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
        for (UI ui : activeUIs) {
            ui.render();
        }
        
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
            GUI topGUI = activeGUIs.get(activeGUIs.size() - 1);
            topGUI.onKeyPress(key);
        }
    }
    
    public boolean hasActiveUIs() {
        return !activeUIs.isEmpty();
    }
    
    public boolean hasActiveGUIs() {
        return !activeGUIs.isEmpty();
    }
    
    public int getActiveUICount() {
        return activeUIs.size();
    }
    
    public int getActiveGUICount() {
        return activeGUIs.size();
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