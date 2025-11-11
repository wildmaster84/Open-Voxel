package com.openvoxel.ui.examples;

import com.openvoxel.ui.GUI;
import com.openvoxel.ui.GLUIRenderer;

/**
 * Example pause menu GUI implementation.
 * 
 * <p>This GUI displays a pause menu with a title and a "Resume" button.
 * The menu is centered on the screen and uses the embedded bitmap font
 * for text rendering.</p>
 */
public class PauseMenu implements GUI {
    
    private static final int MENU_WIDTH = 300;
    private static final int MENU_HEIGHT = 200;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 40;
    
    private Runnable onResume;
    
    /**
     * Creates a new pause menu.
     * 
     * @param onResume Callback to execute when the Resume button is clicked
     */
    public PauseMenu(Runnable onResume) {
        this.onResume = onResume;
    }
    
    @Override
    public void render(GLUIRenderer renderer, int windowWidth, int windowHeight) {
        // Calculate centered position
        float menuX = (windowWidth - MENU_WIDTH) / 2.0f;
        float menuY = (windowHeight - MENU_HEIGHT) / 2.0f;
        
        // Draw semi-transparent overlay
        renderer.drawFilledRect(0, 0, windowWidth, windowHeight, 0.0f, 0.0f, 0.0f, 0.5f);
        
        // Draw menu panel
        renderer.drawPanel(menuX, menuY, MENU_WIDTH, MENU_HEIGHT,
                         0.2f, 0.2f, 0.2f, 0.9f,  // Dark gray background
                         0.6f, 0.6f, 0.6f, 1.0f); // Light gray border
        
        // Draw title
        String title = "PAUSED";
        float titleScale = 2.0f;
        float titleWidth = title.length() * 8.0f * titleScale;
        float titleX = menuX + (MENU_WIDTH - titleWidth) / 2.0f;
        float titleY = menuY + 30;
        renderer.drawText(title, titleX, titleY, titleScale, 1.0f, 1.0f, 1.0f, 1.0f);
        
        // Draw Resume button
        float buttonX = menuX + (MENU_WIDTH - BUTTON_WIDTH) / 2.0f;
        float buttonY = menuY + 100;
        
        // Check if mouse is hovering (we'll use this in click handling)
        renderer.drawPanel(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                         0.4f, 0.4f, 0.4f, 1.0f,  // Button background
                         0.8f, 0.8f, 0.8f, 1.0f); // Button border
        
        // Draw button text
        String buttonText = "Resume";
        float buttonTextScale = 1.5f;
        float buttonTextWidth = buttonText.length() * 8.0f * buttonTextScale;
        float buttonTextX = buttonX + (BUTTON_WIDTH - buttonTextWidth) / 2.0f;
        float buttonTextY = buttonY + (BUTTON_HEIGHT - 8.0f * buttonTextScale) / 2.0f;
        renderer.drawText(buttonText, buttonTextX, buttonTextY, buttonTextScale, 
                        1.0f, 1.0f, 1.0f, 1.0f);
        
        // Draw instructions
        String instructions = "Press ESC to resume";
        float instrScale = 1.0f;
        float instrWidth = instructions.length() * 8.0f * instrScale;
        float instrX = menuX + (MENU_WIDTH - instrWidth) / 2.0f;
        float instrY = menuY + MENU_HEIGHT - 40;
        renderer.drawText(instructions, instrX, instrY, instrScale, 
                        0.7f, 0.7f, 0.7f, 1.0f);
    }
    
    @Override
    public boolean onMouseClick(int x, int y, int button, int windowWidth, int windowHeight) {
        // Calculate button bounds
        float menuX = (windowWidth - MENU_WIDTH) / 2.0f;
        float menuY = (windowHeight - MENU_HEIGHT) / 2.0f;
        float buttonX = menuX + (MENU_WIDTH - BUTTON_WIDTH) / 2.0f;
        float buttonY = menuY + 100;
        
        // Check if click is within Resume button bounds
        if (x >= buttonX && x <= buttonX + BUTTON_WIDTH &&
            y >= buttonY && y <= buttonY + BUTTON_HEIGHT) {
            if (onResume != null) {
                onResume.run();
            }
            return true; // Click was handled
        }
        
        // Click was within menu area but not on button
        if (x >= menuX && x <= menuX + MENU_WIDTH &&
            y >= menuY && y <= menuY + MENU_HEIGHT) {
            return true; // Consume click to prevent it from going through
        }
        
        return false;
    }
    
    @Override
    public boolean blocksInput() {
        return true;
    }
}
