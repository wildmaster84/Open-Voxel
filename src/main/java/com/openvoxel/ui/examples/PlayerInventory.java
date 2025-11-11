package com.openvoxel.ui.examples;

import com.openvoxel.ui.GUI;
import com.openvoxel.ui.GLUIRenderer;
import com.openvoxel.ui.UIManager;
import org.lwjgl.glfw.GLFW;

/**
 * Example player inventory GUI that demonstrates how to implement an interactive
 * menu using the GUI framework. This GUI receives tick updates and handles
 * mouse clicks and keyboard input.
 * 
 * <p>This is an on-tick GUI (extends {@link GUI}), meaning it receives regular
 * tick updates and can process input events. It demonstrates:
 * <ul>
 *   <li>Rendering inventory slots in a grid layout</li>
 *   <li>Handling mouse clicks to select/move items</li>
 *   <li>Handling keyboard input (ESC to close)</li>
 *   <li>Tick-based updates for animations or state changes</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>
 * // Open the inventory
 * UIManager.get().openGUI(new PlayerInventory(1280, 720));
 * 
 * // The inventory will automatically handle:
 * // - Mouse clicks (via UIManager.onMouseClick)
 * // - Key presses (via UIManager.onKeyPress)
 * // - Tick updates (via UIManager.tick)
 * </pre>
 */
public class PlayerInventory extends GUI {
    
    private final int windowWidth;
    private final int windowHeight;
    
    // Inventory state (simplified - in production, integrate with actual inventory system)
    private static final int ROWS = 4;
    private static final int COLS = 9;
    private final String[] items = new String[ROWS * COLS];
    private int selectedSlot = -1;  // -1 means no selection
    
    // UI layout constants
    private static final float PANEL_WIDTH = 500;
    private static final float PANEL_HEIGHT = 300;
    private static final float SLOT_SIZE = 40;
    private static final float SLOT_PADDING = 5;
    
    // Animation state
    private long totalTicks = 0;
    
    /**
     * Creates a new player inventory GUI.
     * 
     * @param windowWidth The width of the game window
     * @param windowHeight The height of the game window
     */
    public PlayerInventory(int windowWidth, int windowHeight) {
        this.windowWidth = windowWidth;
        this.windowHeight = windowHeight;
        
        // Initialize with some dummy items for demonstration
        items[0] = "Stone";
        items[1] = "Wood";
        items[9] = "Iron";
        items[18] = "Gold";
    }
    
    @Override
    public void onOpen() {
        System.out.println("Player inventory opened");
        selectedSlot = -1;
    }
    
    @Override
    public void onTick(long tickDelta) {
        // Update tick counter for animations
        totalTicks += tickDelta;
        
        // Here you could update inventory state, check for item changes,
        // or handle other game logic that needs to happen every tick
    }
    
    @Override
    public void render() {
        // Setup 2D rendering
        GLUIRenderer.setup2DRendering(windowWidth, windowHeight);
        
        // Calculate centered position
        float panelX = (windowWidth - PANEL_WIDTH) / 2;
        float panelY = (windowHeight - PANEL_HEIGHT) / 2;
        
        // Draw semi-transparent dark overlay
        GLUIRenderer.drawPanel(0, 0, windowWidth, windowHeight, 0.0f, 0.0f, 0.0f, 0.4f);
        
        // Draw main inventory panel
        GLUIRenderer.drawPanel(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT,
                               0.15f, 0.15f, 0.15f, 0.95f);
        
        // Draw panel border
        GLUIRenderer.drawBorder(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT,
                                3.0f, 0.5f, 0.5f, 0.5f, 1.0f);
        
        // Draw title
        float titleX = panelX + (PANEL_WIDTH - "Inventory".length() * 8) / 2;
        float titleY = panelY + 15;
        GLUIRenderer.drawText("Inventory", titleX, titleY, 1.0f, 1.0f, 1.0f, 1.0f);
        
        // Draw inventory slots
        float startX = panelX + (PANEL_WIDTH - (COLS * (SLOT_SIZE + SLOT_PADDING))) / 2;
        float startY = panelY + 50;
        
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = row * COLS + col;
                float slotX = startX + col * (SLOT_SIZE + SLOT_PADDING);
                float slotY = startY + row * (SLOT_SIZE + SLOT_PADDING);
                
                // Determine slot color (highlight if selected)
                float r, g, b, a;
                if (index == selectedSlot) {
                    // Selected slot - highlight with animation
                    float pulse = (float) Math.abs(Math.sin(totalTicks * 0.003));
                    r = 0.3f + pulse * 0.2f;
                    g = 0.5f + pulse * 0.3f;
                    b = 0.3f + pulse * 0.2f;
                    a = 1.0f;
                } else if (items[index] != null) {
                    // Occupied slot
                    r = 0.25f;
                    g = 0.25f;
                    b = 0.25f;
                    a = 1.0f;
                } else {
                    // Empty slot
                    r = 0.1f;
                    g = 0.1f;
                    b = 0.1f;
                    a = 1.0f;
                }
                
                // Draw slot background
                GLUIRenderer.drawRect(slotX, slotY, SLOT_SIZE, SLOT_SIZE, r, g, b, a);
                
                // Draw slot border
                GLUIRenderer.drawBorder(slotX, slotY, SLOT_SIZE, SLOT_SIZE,
                                        1.5f, 0.4f, 0.4f, 0.4f, 1.0f);
                
                // Draw item name if present (simplified - in production, draw item icons)
                if (items[index] != null) {
                    // Draw first 3 characters of item name
                    String shortName = items[index].substring(0, Math.min(3, items[index].length()));
                    GLUIRenderer.drawText(shortName, slotX + 5, slotY + 15, 
                                         0.8f, 0.8f, 0.8f, 1.0f);
                }
            }
        }
        
        // Draw instructions
        float infoY = panelY + PANEL_HEIGHT - 30;
        String info = "Click slots to select/move | ESC to close";
        float infoX = panelX + (PANEL_WIDTH - info.length() * 8) / 2;
        GLUIRenderer.drawText(info, infoX, infoY, 0.6f, 0.6f, 0.6f, 1.0f);
        
        // Restore 3D rendering
        GLUIRenderer.restore3DRendering();
    }
    
    @Override
    public void onMouseClick(int x, int y, int button) {
        // Only handle left mouse button
        if (button != 0) {
            return;
        }
        
        // Calculate panel position
        float panelX = (windowWidth - PANEL_WIDTH) / 2;
        float panelY = (windowHeight - PANEL_HEIGHT) / 2;
        float startX = panelX + (PANEL_WIDTH - (COLS * (SLOT_SIZE + SLOT_PADDING))) / 2;
        float startY = panelY + 50;
        
        // Check if click is on any inventory slot
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = row * COLS + col;
                float slotX = startX + col * (SLOT_SIZE + SLOT_PADDING);
                float slotY = startY + row * (SLOT_SIZE + SLOT_PADDING);
                
                if (GLUIRenderer.isPointInRect(x, y, slotX, slotY, SLOT_SIZE, SLOT_SIZE)) {
                    handleSlotClick(index);
                    return;
                }
            }
        }
    }
    
    /**
     * Handles clicking on an inventory slot.
     * This demonstrates basic item pick/place logic.
     * 
     * @param index The index of the clicked slot
     */
    private void handleSlotClick(int index) {
        if (selectedSlot == -1) {
            // No item currently selected - pick up item from clicked slot
            if (items[index] != null) {
                selectedSlot = index;
                System.out.println("Picked up: " + items[index] + " from slot " + index);
            }
        } else if (selectedSlot == index) {
            // Clicking same slot - deselect
            selectedSlot = -1;
            System.out.println("Deselected slot " + index);
        } else {
            // Different slot - swap items
            String temp = items[selectedSlot];
            items[selectedSlot] = items[index];
            items[index] = temp;
            System.out.println("Swapped items between slots " + selectedSlot + " and " + index);
            selectedSlot = -1;
        }
    }
    
    @Override
    public void onKeyPress(int key) {
        // Handle ESC key to close inventory
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
        }
    }
    
    @Override
    public void onClose() {
        System.out.println("Player inventory closed");
    }
    
    /**
     * Closes this inventory GUI.
     */
    public void close() {
        UIManager.get().closeTopGUI();
    }
}
