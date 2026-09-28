package engine.gui;

import org.lwjgl.glfw.GLFW;

import engine.rendering.Camera;
import engine.rendering.Texture;
import engine.ui.GLUIRenderer;
import engine.ui.UIManager;
import engine.world.AbstractBlock;
import engine.world.block.BlockType;
import engine.input.InputHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Creative-style inventory: lists every block type (except AIR) in pages and lets the player
 * equip a block into their hand by clicking it. Shows block side texture in each slot.
 */
public class PlayerInventory extends GUI {

    private static final int ROWS = 4;
    private static final int COLS = 9;
    private static final int SLOTS_PER_PAGE = ROWS * COLS;

    private final List<BlockType> allBlocks = new ArrayList<>();
    private int page = 0;

    private static final float PANEL_WIDTH = 720;
    private static final float PANEL_HEIGHT = 420;
    private static final float SLOT_SIZE = 48;
    private static final float SLOT_PADDING = 8;

    private final Camera camera;

    private long totalTicks = 0;

    private int windowWidth;
    private int windowHeight;

    public PlayerInventory(Camera camera) {
        this.camera = camera;
        this.windowWidth = camera.getAspect()[0];
        this.windowHeight = camera.getAspect()[1];

        // populate list with all blocks (skip AIR)
        for (BlockType bt : BlockType.values()) {
            if (bt == null) continue;
            if (bt == BlockType.AIR) continue;
            allBlocks.add(bt);
        }
    }

    @Override
    public void onOpen() {
        camera.getInputHandler().paused = true;
    }

    @Override
    public void onTick(long tickDelta) {
        totalTicks += tickDelta;
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

        GLUIRenderer.drawPanel(0, 0, windowWidth, windowHeight, 0.0f, 0.0f, 0.0f, 0.45f);

        GLUIRenderer.drawPanel(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT,
                0.12f, 0.12f, 0.12f, 0.98f);

        GLUIRenderer.drawBorder(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT,
                3.0f, 0.5f, 0.5f, 0.5f, 1.0f);

        String title = "Creative Inventory";
        float titleX = panelX + (PANEL_WIDTH - title.length() * 8) / 2;
        float titleY = panelY + 16;
        GLUIRenderer.drawText(title, titleX, titleY, 1.0f, 1.0f, 1.0f, 1.0f);

        float startX = panelX + (PANEL_WIDTH - (COLS * (SLOT_SIZE + SLOT_PADDING) - SLOT_PADDING)) / 2;
        float startY = panelY + 60;

        // compute page indices
        int totalPages = Math.max(1, (allBlocks.size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;
        int offset = page * SLOTS_PER_PAGE;

        // draw slots and block icons/text
        BlockType held = camera.getBlockInHand().getType();

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int slotIndex = row * COLS + col;
                int globalIndex = offset + slotIndex;
                float slotX = startX + col * (SLOT_SIZE + SLOT_PADDING);
                float slotY = startY + row * (SLOT_SIZE + SLOT_PADDING);

                float r, g, b, a;
                if (globalIndex < allBlocks.size()) {
                    BlockType bt = allBlocks.get(globalIndex);
                    // highlight if it's the held block
                    if (bt == held) {
                        float pulse = (float) Math.abs(Math.sin(totalTicks * 0.004));
                        r = 0.2f + pulse * 0.25f;
                        g = 0.45f + pulse * 0.3f;
                        b = 0.2f + pulse * 0.25f;
                        a = 1.0f;
                    } else {
                        r = 0.18f; g = 0.18f; b = 0.18f; a = 1.0f;
                    }
                } else {
                    // empty slot
                    r = 0.08f; g = 0.08f; b = 0.08f; a = 1.0f;
                }

                GLUIRenderer.drawRect(slotX, slotY, SLOT_SIZE, SLOT_SIZE, r, g, b, a);

                GLUIRenderer.drawBorder(slotX, slotY, SLOT_SIZE, SLOT_SIZE,
                        1.5f, 0.35f, 0.35f, 0.35f, 1.0f);

                if (globalIndex < allBlocks.size()) {
                    BlockType bt = allBlocks.get(globalIndex);
                    // prefer side texture (face index 2); fall back to top (0) if null
                    Texture tex = bt.getTextureForFace(2);
                    if (tex == null) tex = bt.getTextureForFace(0);
                    if (tex != null) {
                        float iconSize = SLOT_SIZE - 12;
                        float ix = slotX + (SLOT_SIZE - iconSize) / 2f;
                        float iy = slotY + (SLOT_SIZE - iconSize) / 2f;
                        GLUIRenderer.drawTexture(tex, ix, iy, iconSize, iconSize);
                    } else {
                        String label = bt.name();
                        String shortName = label.length() > 12 ? label.substring(0, 12) + "…" : label;
                        GLUIRenderer.drawText(shortName, slotX + 6, slotY + SLOT_SIZE / 2f - 6,
                                0.85f, 0.85f, 0.85f, 1.0f);
                    }
                }
            }
        }

        // Pagination / controls
        final float btnW = 36, btnH = 24;
        float prevX = panelX + 16;
        float prevY = panelY + PANEL_HEIGHT - 40;
        float nextX = panelX + PANEL_WIDTH - 16 - btnW;
        float pageTextX = panelX + (PANEL_WIDTH - 40) / 2f;
        String pageText = String.format("Page %d/%d", page + 1, totalPages);

        GLUIRenderer.drawButton(prevX, prevY, btnW, btnH, "<",
                0.18f, 0.18f, 0.18f, 1.0f);
        GLUIRenderer.drawButton(nextX, prevY, btnW, btnH, ">",
                0.18f, 0.18f, 0.18f, 1.0f);

        GLUIRenderer.drawText(pageText, pageTextX, prevY + 4, 0.8f, 0.8f, 0.8f, 1.0f);

        // Info line
        String info = "Left-click a block to equip it to your hand | ESC to close";
        float infoX = panelX + (PANEL_WIDTH - info.length() * 8) / 2;
        float infoY = panelY + PANEL_HEIGHT - 16;
        GLUIRenderer.drawText(info, infoX, infoY, 0.6f, 0.6f, 0.6f, 1.0f);

        GLUIRenderer.restore3DRendering();
    }

    @Override
    public void onMouseClick(int mouseX, int mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;

        float panelX = (windowWidth - PANEL_WIDTH) / 2;
        float panelY = (windowHeight - PANEL_HEIGHT) / 2;
        float startX = panelX + (PANEL_WIDTH - (COLS * (SLOT_SIZE + SLOT_PADDING) - SLOT_PADDING)) / 2;
        float startY = panelY + 60;

        // check slots
        int offset = page * SLOTS_PER_PAGE;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int slotIndex = row * COLS + col;
                int globalIndex = offset + slotIndex;
                float slotX = startX + col * (SLOT_SIZE + SLOT_PADDING);
                float slotY = startY + row * (SLOT_SIZE + SLOT_PADDING);

                if (GLUIRenderer.isPointInRect(mouseX, mouseY, slotX, slotY, SLOT_SIZE, SLOT_SIZE)) {
                    if (globalIndex < allBlocks.size()) {
                        BlockType selected = allBlocks.get(globalIndex);
                        AbstractBlock ih = camera.getBlockInHand();
                        if (ih != null) {
                        	camera.setBlockInHand(new AbstractBlock(selected));
                        }
                        // keep GUI open — if you prefer to auto-close, uncomment the next line:
                        // close();
                    }
                    return;
                }
            }
        }

        // check previous / next buttons
        final float btnW = 36, btnH = 24;
        float prevX = panelX + 16;
        float prevY = panelY + PANEL_HEIGHT - 40;
        float nextX = panelX + PANEL_WIDTH - 16 - btnW;
        float nextY = prevY;

        if (GLUIRenderer.isPointInRect(mouseX, mouseY, prevX, prevY, btnW, btnH)) {
            if (page > 0) page--;
            return;
        }
        if (GLUIRenderer.isPointInRect(mouseX, mouseY, nextX, nextY, btnW, btnH)) {
            int totalPages = Math.max(1, (allBlocks.size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);
            if (page < totalPages - 1) page++;
            return;
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