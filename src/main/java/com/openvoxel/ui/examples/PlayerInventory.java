package com.openvoxel.ui.examples;

import com.openvoxel.ui.GUI;
import com.openvoxel.ui.GLUIRenderer;

/**
 * Example player inventory GUI implementation.
 * 
 * <p>This GUI displays a simple grid-based inventory with labeled slots.
 * It demonstrates how to render text labels and handle click detection
 * on individual inventory slots using window coordinates.</p>
 */
public class PlayerInventory implements GUI {
    
    private static final int INVENTORY_WIDTH = 400;
    private static final int INVENTORY_HEIGHT = 300;
    private static final int SLOT_SIZE = 40;
    private static final int SLOT_SPACING = 5;
    private static final int GRID_COLS = 8;
    private static final int GRID_ROWS = 4;
    
    private int selectedSlot = -1;
    private SlotClickListener slotClickListener;
    
    /**
     * Listener interface for slot click events.
     */
    public interface SlotClickListener {
        /**
         * Called when an inventory slot is clicked.
         * 
         * @param slotIndex The index of the clicked slot (0-31)
         */
        void onSlotClicked(int slotIndex);
    }
    
    /**
     * Creates a new player inventory GUI.
     */
    public PlayerInventory() {
        this.selectedSlot = -1;
    }
    
    /**
     * Sets the listener for slot click events.
     * 
     * @param listener The listener to set
     */
    public void setSlotClickListener(SlotClickListener listener) {
        this.slotClickListener = listener;
    }
    
    /**
     * Sets the currently selected slot.
     * 
     * @param slotIndex The index of the slot to select (0-31), or -1 for no selection
     */
    public void setSelectedSlot(int slotIndex) {
        this.selectedSlot = slotIndex;
    }
    
    /**
     * Gets the currently selected slot.
     * 
     * @return The index of the selected slot, or -1 if no slot is selected
     */
    public int getSelectedSlot() {
        return selectedSlot;
    }
    
    @Override
    public void render(GLUIRenderer renderer, int windowWidth, int windowHeight) {
        // Calculate centered position
        float invX = (windowWidth - INVENTORY_WIDTH) / 2.0f;
        float invY = (windowHeight - INVENTORY_HEIGHT) / 2.0f;
        
        // Draw semi-transparent overlay
        renderer.drawFilledRect(0, 0, windowWidth, windowHeight, 0.0f, 0.0f, 0.0f, 0.3f);
        
        // Draw inventory panel
        renderer.drawPanel(invX, invY, INVENTORY_WIDTH, INVENTORY_HEIGHT,
                         0.15f, 0.15f, 0.15f, 0.95f,  // Very dark gray background
                         0.5f, 0.5f, 0.5f, 1.0f);      // Gray border
        
        // Draw title
        String title = "Inventory";
        float titleScale = 1.5f;
        float titleWidth = title.length() * 8.0f * titleScale;
        float titleX = invX + (INVENTORY_WIDTH - titleWidth) / 2.0f;
        float titleY = invY + 15;
        renderer.drawText(title, titleX, titleY, titleScale, 1.0f, 1.0f, 1.0f, 1.0f);
        
        // Calculate grid start position
        float gridWidth = GRID_COLS * (SLOT_SIZE + SLOT_SPACING) - SLOT_SPACING;
        float gridHeight = GRID_ROWS * (SLOT_SIZE + SLOT_SPACING) - SLOT_SPACING;
        float gridStartX = invX + (INVENTORY_WIDTH - gridWidth) / 2.0f;
        float gridStartY = invY + 50;
        
        // Draw inventory slots
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int slotIndex = row * GRID_COLS + col;
                float slotX = gridStartX + col * (SLOT_SIZE + SLOT_SPACING);
                float slotY = gridStartY + row * (SLOT_SIZE + SLOT_SPACING);
                
                // Highlight selected slot
                boolean isSelected = (slotIndex == selectedSlot);
                float bgR = isSelected ? 0.4f : 0.25f;
                float bgG = isSelected ? 0.4f : 0.25f;
                float bgB = isSelected ? 0.5f : 0.25f;
                float borderR = isSelected ? 0.8f : 0.5f;
                float borderG = isSelected ? 0.8f : 0.5f;
                float borderB = isSelected ? 1.0f : 0.5f;
                
                renderer.drawPanel(slotX, slotY, SLOT_SIZE, SLOT_SIZE,
                                 bgR, bgG, bgB, 1.0f,
                                 borderR, borderG, borderB, 1.0f);
                
                // Draw slot number
                String slotNum = String.valueOf(slotIndex);
                float numScale = 0.8f;
                float numX = slotX + 3;
                float numY = slotY + 3;
                renderer.drawText(slotNum, numX, numY, numScale, 
                                0.7f, 0.7f, 0.7f, 1.0f);
            }
        }
        
        // Draw instructions
        String instructions = "Click a slot to select";
        float instrScale = 1.0f;
        float instrWidth = instructions.length() * 8.0f * instrScale;
        float instrX = invX + (INVENTORY_WIDTH - instrWidth) / 2.0f;
        float instrY = invY + INVENTORY_HEIGHT - 25;
        renderer.drawText(instructions, instrX, instrY, instrScale, 
                        0.6f, 0.6f, 0.6f, 1.0f);
    }
    
    @Override
    public boolean onMouseClick(int x, int y, int button, int windowWidth, int windowHeight) {
        // Calculate inventory bounds
        float invX = (windowWidth - INVENTORY_WIDTH) / 2.0f;
        float invY = (windowHeight - INVENTORY_HEIGHT) / 2.0f;
        
        // Check if click is within inventory panel
        if (x < invX || x > invX + INVENTORY_WIDTH ||
            y < invY || y > invY + INVENTORY_HEIGHT) {
            return false;
        }
        
        // Calculate grid start position
        float gridWidth = GRID_COLS * (SLOT_SIZE + SLOT_SPACING) - SLOT_SPACING;
        float gridStartX = invX + (INVENTORY_WIDTH - gridWidth) / 2.0f;
        float gridStartY = invY + 50;
        
        // Check which slot was clicked
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int slotIndex = row * GRID_COLS + col;
                float slotX = gridStartX + col * (SLOT_SIZE + SLOT_SPACING);
                float slotY = gridStartY + row * (SLOT_SIZE + SLOT_SPACING);
                
                if (x >= slotX && x <= slotX + SLOT_SIZE &&
                    y >= slotY && y <= slotY + SLOT_SIZE) {
                    selectedSlot = slotIndex;
                    if (slotClickListener != null) {
                        slotClickListener.onSlotClicked(slotIndex);
                    }
                    return true;
                }
            }
        }
        
        // Click was within inventory but not on a slot
        return true;
    }
    
    @Override
    public boolean blocksInput() {
        return true;
    }
}
