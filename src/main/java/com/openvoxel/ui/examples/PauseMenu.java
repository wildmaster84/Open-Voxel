package com.openvoxel.ui.examples;

import com.openvoxel.ui.GLUIRenderer;
import com.openvoxel.ui.GUI;
import com.openvoxel.ui.UIManager;
import org.lwjgl.glfw.GLFW;

/**
 * Example pause menu GUI that demonstrates how to implement an interactive menu
 * using the GUI framework. This GUI displays a translucent panel with a clickable
 * "Resume" button.
 * 
 * <p>This is an interactive GUI (extends {@link GUI}), meaning it receives
 * tick updates and can handle mouse clicks and keyboard input.
 * 
 * <p>Example usage:
 * <pre>
 * // Open the pause menu
 * UIManager.get().openGUI(new PauseMenu(1280, 720));
 * </pre>
 * 
 * <p>Features:
 * <ul>
 *   <li>Clickable Resume button to close the menu</li>
 *   <li>ESC key support to close the menu</li>
 *   <li>Automatic cursor display when opened</li>
 * </ul>
 */
public class PauseMenu extends GUI {
    
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
    public void onTick(long tickDelta) {
        // Pause menu doesn't need tick updates
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
    public void onMouseClick(int x, int y, int button) {
        // Only handle left mouse button
        if (button != 0) {
            return;
        }
        
        // Calculate button position
        float panelX = (windowWidth - PANEL_WIDTH) / 2;
        float panelY = (windowHeight - PANEL_HEIGHT) / 2;
        float buttonX = panelX + (PANEL_WIDTH - BUTTON_WIDTH) / 2;
        float buttonY = panelY + 80;
        
        // Check if Resume button was clicked
        if (GLUIRenderer.isPointInRect(x, y, buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            System.out.println("Resume button clicked");
            close();
        }
    }
    
    @Override
    public void onKeyPress(int key) {
        // Handle ESC key to close menu
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
        }
    }
    
    @Override
    public void onClose() {
        System.out.println("Pause menu closed");
    }
    
    /**
     * Closes this pause menu.
     */
    public void close() {
        UIManager.get().closeTopGUI();
    }
}
