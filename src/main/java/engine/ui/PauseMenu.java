package engine.ui;

import java.io.File;
import java.io.IOException;

import org.lwjgl.glfw.GLFW;

import engine.VoxelEngine;
import engine.gui.GUI;
import engine.rendering.Camera;

public class PauseMenu extends GUI {
    
    private int windowWidth;
    private int windowHeight;
    private final Camera camera;
    
    private static final float PANEL_WIDTH = 300;
    private static final float PANEL_HEIGHT = 400;
    private static final float BUTTON_WIDTH = 150;
    private static final float BUTTON_HEIGHT = 40;

    public PauseMenu(Camera camera, int windowWidth, int windowHeight) {
        this.camera = camera;
        this.windowWidth = camera.getAspect()[0];
        this.windowHeight = camera.getAspect()[1];
    }
    
    @Override
    public void onOpen() {
        camera.getInputHandler().paused = true;
    }
    
    @Override
    public void onTick(long tickDelta) {
    	if (this.windowWidth != camera.getAspect()[0] || this.windowHeight != camera.getAspect()[1]) {
    		this.windowWidth = camera.getAspect()[0];
            this.windowHeight = camera.getAspect()[1];
    	}
    	
    }
    
    @Override
    public void render() {
        GLUIRenderer.setup2DRendering(windowWidth, windowHeight);
        
        float panelX = (windowWidth - PANEL_WIDTH) / 2;
        float panelY = (windowHeight - PANEL_HEIGHT) / 2;
        
        GLUIRenderer.drawPanel(0, 0, windowWidth, windowHeight, 0.0f, 0.0f, 0.0f, 0.5f);
        
        GLUIRenderer.drawPanel(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 
                               0.2f, 0.2f, 0.2f, 0.9f);
        
        GLUIRenderer.drawBorder(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT,
                                2.0f, 0.6f, 0.6f, 0.6f, 1.0f);
        
        float titleX = panelX + (PANEL_WIDTH - "Paused".length() * 8) / 2;
        float titleY = panelY + 30;
        GLUIRenderer.drawText("Paused", titleX, titleY, 1.0f, 1.0f, 1.0f, 1.0f);
        
        float buttonX = panelX + (PANEL_WIDTH - BUTTON_WIDTH) / 2;
        float buttonY = panelY + 80;
        GLUIRenderer.drawButton(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT, "Resume",
                                0.4f, 0.4f, 0.4f, 1.0f);
        
        GLUIRenderer.drawButton(buttonX, buttonY + 60, BUTTON_WIDTH, BUTTON_HEIGHT, "Exit",
                0.4f, 0.4f, 0.4f, 1.0f);
        
        float hintX = panelX + (PANEL_WIDTH - "Press ESC to resume".length() * 8) / 2;
        float hintY = panelY + PANEL_HEIGHT - 40;
        GLUIRenderer.drawText("Press ESC to resume", hintX, hintY, 0.7f, 0.7f, 0.7f, 1.0f);
        
        GLUIRenderer.restore3DRendering();
    }
    
    @Override
    public void onMouseClick(int x, int y, int button) {
        if (button != 0) {
            return;
        }
        
        float panelX = (windowWidth - PANEL_WIDTH) / 2;
        float panelY = (windowHeight - PANEL_HEIGHT) / 2;
        float buttonX = panelX + (PANEL_WIDTH - BUTTON_WIDTH) / 2;
        float buttonY = panelY + 80;
        
        if (GLUIRenderer.isPointInRect(x, y, buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            close();
        }
        
        if (GLUIRenderer.isPointInRect(x, y, buttonX, buttonY + 60, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            VoxelEngine.getEngine().cleanup();
            System.exit(0);
        }
    }
    
    @Override
    public void onKeyPress(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
        }
    }
    
    @Override
    public void onClose() {
    	camera.getInputHandler().paused = false;
    }
    
    public void close() {
        UIManager.get().closeTopGUI();
    }
}