package com.openvoxel.ui.examples;

import com.openvoxel.ui.GLUIRenderer;
import com.openvoxel.ui.UI;
import com.openvoxel.ui.UIManager;

/**
 * Example pause menu UI that demonstrates how to implement a simple menu
 * using the UI framework. This UI renders off-tick and displays a translucent
 * panel with a "Resume" button.
 * 
 * <p>This is a render-only UI (extends {@link UI}), meaning it doesn't receive
 * tick updates but is drawn every frame. For interactive menus that need to
 * handle clicks and process game logic, use {@link com.openvoxel.ui.GUI} instead.
 * 
 * <p>Example usage:
 * <pre>
 * // Open the pause menu
 * UIManager.get().openUI(new PauseMenu(1280, 720));
 * </pre>
 * 
 * <p>Note: This example uses simple rendering and doesn't handle actual click events.
 * For a fully interactive pause menu with button clicks, see {@link PlayerInventory}
 * which extends GUI and demonstrates event handling.
 */
public class PauseMenu extends UI {
    
    private final int windowWidth;
    private final int windowHeight;
    
    // UI layout constants
    private static final float PANEL_WIDTH = 300;
    private static final float PANEL_HEIGHT = 200;
    private static final float BUTTON_WIDTH = 150;
    private static final float BUTTON_HEIGHT = 40;
    
    /**
     * Creates a new pause menu UI.
     * 
     * @param windowWidth The width of the game window
     * @param windowHeight The height of the game window
     */
    public PauseMenu(int windowWidth, int windowHeight) {
        this.windowWidth = windowWidth;
        this.windowHeight = windowHeight;
    }
    
    @Override
    public void onOpen() {
        System.out.println("Pause menu opened");
    }
    
    @Override
    public void render() {
        // Setup 2D rendering
        GLUIRenderer.setup2DRendering(windowWidth, windowHeight);
        
        // Calculate centered position
        float panelX = (windowWidth - PANEL_WIDTH) / 2;
        float panelY = (windowHeight - PANEL_HEIGHT) / 2;
        
        // Draw semi-transparent dark overlay over the entire screen
        GLUIRenderer.drawPanel(0, 0, windowWidth, windowHeight, 0.0f, 0.0f, 0.0f, 0.5f);
        
        // Draw the main panel
        GLUIRenderer.drawPanel(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 
                               0.2f, 0.2f, 0.2f, 0.9f);
        
        // Draw panel border
        GLUIRenderer.drawBorder(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT,
                                2.0f, 0.6f, 0.6f, 0.6f, 1.0f);
        
        // Draw title
        float titleX = panelX + (PANEL_WIDTH - "Paused".length() * 8) / 2;
        float titleY = panelY + 30;
        GLUIRenderer.drawText("Paused", titleX, titleY, 1.0f, 1.0f, 1.0f, 1.0f);
        
        // Draw Resume button
        float buttonX = panelX + (PANEL_WIDTH - BUTTON_WIDTH) / 2;
        float buttonY = panelY + 80;
        GLUIRenderer.drawButton(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT, "Resume",
                                0.4f, 0.4f, 0.4f, 1.0f);
        
        // Draw hint text
        float hintX = panelX + (PANEL_WIDTH - "Press ESC to resume".length() * 8) / 2;
        float hintY = panelY + PANEL_HEIGHT - 40;
        GLUIRenderer.drawText("Press ESC to resume", hintX, hintY, 0.7f, 0.7f, 0.7f, 1.0f);
        
        // Restore 3D rendering
        GLUIRenderer.restore3DRendering();
    }
    
    @Override
    public void onClose() {
        System.out.println("Pause menu closed");
    }
    
    /**
     * Example method showing how a pause menu might be closed programmatically.
     * In practice, you would call this from an input handler or GUI event.
     */
    public void close() {
        UIManager.get().closeTopUI();
    }
}
