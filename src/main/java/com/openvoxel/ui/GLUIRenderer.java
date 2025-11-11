package com.openvoxel.ui;

import org.lwjgl.opengl.GL11;

/**
 * OpenGL-based renderer for UI elements using immediate mode.
 * Provides simple drawing primitives and includes an embedded 8x8 bitmap font
 * for rendering text without external assets.
 * 
 * <p>The embedded font supports ASCII characters 32-126 and uses a compact
 * monochrome bitmap representation. Text rendering is performed using
 * GL11 immediate-mode quads for simplicity.</p>
 * 
 * <p><strong>Note:</strong> This renderer uses deprecated immediate-mode OpenGL
 * for simplicity and portability. It's suitable for simple UIs but not optimized
 * for complex scenes.</p>
 */
public class GLUIRenderer {
    
    /**
     * 8x8 bitmap font data for ASCII 32-126.
     * Each character is represented by 8 bytes (one per row).
     * Each bit represents a pixel (1 = on, 0 = off).
     * 
     * Font layout: 8x8 monochrome bitmap, left-to-right, top-to-bottom.
     */
    private static final byte[][] FONT_DATA = new byte[95][8];
    
    static {
        // Initialize font data for ASCII 32-126
        // Space (32)
        FONT_DATA[0] = new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        // ! (33)
        FONT_DATA[1] = new byte[]{0x18, 0x18, 0x18, 0x18, 0x18, 0x00, 0x18, 0x00};
        // " (34)
        FONT_DATA[2] = new byte[]{0x36, 0x36, 0x36, 0x00, 0x00, 0x00, 0x00, 0x00};
        // # (35)
        FONT_DATA[3] = new byte[]{0x36, 0x36, 0x7F, 0x36, 0x7F, 0x36, 0x36, 0x00};
        // $ (36)
        FONT_DATA[4] = new byte[]{0x0C, 0x3E, 0x03, 0x1E, 0x30, 0x1F, 0x0C, 0x00};
        // % (37)
        FONT_DATA[5] = new byte[]{0x00, 0x63, 0x33, 0x18, 0x0C, 0x66, 0x63, 0x00};
        // & (38)
        FONT_DATA[6] = new byte[]{0x1C, 0x36, 0x1C, 0x6E, 0x3B, 0x33, 0x6E, 0x00};
        // ' (39)
        FONT_DATA[7] = new byte[]{0x06, 0x06, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00};
        // ( (40)
        FONT_DATA[8] = new byte[]{0x18, 0x0C, 0x06, 0x06, 0x06, 0x0C, 0x18, 0x00};
        // ) (41)
        FONT_DATA[9] = new byte[]{0x06, 0x0C, 0x18, 0x18, 0x18, 0x0C, 0x06, 0x00};
        // * (42)
        FONT_DATA[10] = new byte[]{0x00, 0x66, 0x3C, (byte)0xFF, 0x3C, 0x66, 0x00, 0x00};
        // + (43)
        FONT_DATA[11] = new byte[]{0x00, 0x0C, 0x0C, 0x3F, 0x0C, 0x0C, 0x00, 0x00};
        // , (44)
        FONT_DATA[12] = new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x0C, 0x0C, 0x06};
        // - (45)
        FONT_DATA[13] = new byte[]{0x00, 0x00, 0x00, 0x3F, 0x00, 0x00, 0x00, 0x00};
        // . (46)
        FONT_DATA[14] = new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x0C, 0x0C, 0x00};
        // / (47)
        FONT_DATA[15] = new byte[]{0x60, 0x30, 0x18, 0x0C, 0x06, 0x03, 0x01, 0x00};
        // 0 (48)
        FONT_DATA[16] = new byte[]{0x3E, 0x63, 0x73, 0x7B, 0x6F, 0x67, 0x3E, 0x00};
        // 1 (49)
        FONT_DATA[17] = new byte[]{0x0C, 0x0E, 0x0C, 0x0C, 0x0C, 0x0C, 0x3F, 0x00};
        // 2 (50)
        FONT_DATA[18] = new byte[]{0x1E, 0x33, 0x30, 0x1C, 0x06, 0x33, 0x3F, 0x00};
        // 3 (51)
        FONT_DATA[19] = new byte[]{0x1E, 0x33, 0x30, 0x1C, 0x30, 0x33, 0x1E, 0x00};
        // 4 (52)
        FONT_DATA[20] = new byte[]{0x38, 0x3C, 0x36, 0x33, 0x7F, 0x30, 0x78, 0x00};
        // 5 (53)
        FONT_DATA[21] = new byte[]{0x3F, 0x03, 0x1F, 0x30, 0x30, 0x33, 0x1E, 0x00};
        // 6 (54)
        FONT_DATA[22] = new byte[]{0x1C, 0x06, 0x03, 0x1F, 0x33, 0x33, 0x1E, 0x00};
        // 7 (55)
        FONT_DATA[23] = new byte[]{0x3F, 0x33, 0x30, 0x18, 0x0C, 0x0C, 0x0C, 0x00};
        // 8 (56)
        FONT_DATA[24] = new byte[]{0x1E, 0x33, 0x33, 0x1E, 0x33, 0x33, 0x1E, 0x00};
        // 9 (57)
        FONT_DATA[25] = new byte[]{0x1E, 0x33, 0x33, 0x3E, 0x30, 0x18, 0x0E, 0x00};
        // : (58)
        FONT_DATA[26] = new byte[]{0x00, 0x0C, 0x0C, 0x00, 0x00, 0x0C, 0x0C, 0x00};
        // ; (59)
        FONT_DATA[27] = new byte[]{0x00, 0x0C, 0x0C, 0x00, 0x00, 0x0C, 0x0C, 0x06};
        // < (60)
        FONT_DATA[28] = new byte[]{0x18, 0x0C, 0x06, 0x03, 0x06, 0x0C, 0x18, 0x00};
        // = (61)
        FONT_DATA[29] = new byte[]{0x00, 0x00, 0x3F, 0x00, 0x00, 0x3F, 0x00, 0x00};
        // > (62)
        FONT_DATA[30] = new byte[]{0x06, 0x0C, 0x18, 0x30, 0x18, 0x0C, 0x06, 0x00};
        // ? (63)
        FONT_DATA[31] = new byte[]{0x1E, 0x33, 0x30, 0x18, 0x0C, 0x00, 0x0C, 0x00};
        // @ (64)
        FONT_DATA[32] = new byte[]{0x3E, 0x63, 0x7B, 0x7B, 0x7B, 0x03, 0x1E, 0x00};
        // A (65)
        FONT_DATA[33] = new byte[]{0x0C, 0x1E, 0x33, 0x33, 0x3F, 0x33, 0x33, 0x00};
        // B (66)
        FONT_DATA[34] = new byte[]{0x3F, 0x66, 0x66, 0x3E, 0x66, 0x66, 0x3F, 0x00};
        // C (67)
        FONT_DATA[35] = new byte[]{0x3C, 0x66, 0x03, 0x03, 0x03, 0x66, 0x3C, 0x00};
        // D (68)
        FONT_DATA[36] = new byte[]{0x1F, 0x36, 0x66, 0x66, 0x66, 0x36, 0x1F, 0x00};
        // E (69)
        FONT_DATA[37] = new byte[]{0x7F, 0x46, 0x16, 0x1E, 0x16, 0x46, 0x7F, 0x00};
        // F (70)
        FONT_DATA[38] = new byte[]{0x7F, 0x46, 0x16, 0x1E, 0x16, 0x06, 0x0F, 0x00};
        // G (71)
        FONT_DATA[39] = new byte[]{0x3C, 0x66, 0x03, 0x03, 0x73, 0x66, 0x7C, 0x00};
        // H (72)
        FONT_DATA[40] = new byte[]{0x33, 0x33, 0x33, 0x3F, 0x33, 0x33, 0x33, 0x00};
        // I (73)
        FONT_DATA[41] = new byte[]{0x1E, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x1E, 0x00};
        // J (74)
        FONT_DATA[42] = new byte[]{0x78, 0x30, 0x30, 0x30, 0x33, 0x33, 0x1E, 0x00};
        // K (75)
        FONT_DATA[43] = new byte[]{0x67, 0x66, 0x36, 0x1E, 0x36, 0x66, 0x67, 0x00};
        // L (76)
        FONT_DATA[44] = new byte[]{0x0F, 0x06, 0x06, 0x06, 0x46, 0x66, 0x7F, 0x00};
        // M (77)
        FONT_DATA[45] = new byte[]{0x63, 0x77, 0x7F, 0x7F, 0x6B, 0x63, 0x63, 0x00};
        // N (78)
        FONT_DATA[46] = new byte[]{0x63, 0x67, 0x6F, 0x7B, 0x73, 0x63, 0x63, 0x00};
        // O (79)
        FONT_DATA[47] = new byte[]{0x1C, 0x36, 0x63, 0x63, 0x63, 0x36, 0x1C, 0x00};
        // P (80)
        FONT_DATA[48] = new byte[]{0x3F, 0x66, 0x66, 0x3E, 0x06, 0x06, 0x0F, 0x00};
        // Q (81)
        FONT_DATA[49] = new byte[]{0x1E, 0x33, 0x33, 0x33, 0x3B, 0x1E, 0x38, 0x00};
        // R (82)
        FONT_DATA[50] = new byte[]{0x3F, 0x66, 0x66, 0x3E, 0x36, 0x66, 0x67, 0x00};
        // S (83)
        FONT_DATA[51] = new byte[]{0x1E, 0x33, 0x07, 0x0E, 0x38, 0x33, 0x1E, 0x00};
        // T (84)
        FONT_DATA[52] = new byte[]{0x3F, 0x2D, 0x0C, 0x0C, 0x0C, 0x0C, 0x1E, 0x00};
        // U (85)
        FONT_DATA[53] = new byte[]{0x33, 0x33, 0x33, 0x33, 0x33, 0x33, 0x3F, 0x00};
        // V (86)
        FONT_DATA[54] = new byte[]{0x33, 0x33, 0x33, 0x33, 0x33, 0x1E, 0x0C, 0x00};
        // W (87)
        FONT_DATA[55] = new byte[]{0x63, 0x63, 0x63, 0x6B, 0x7F, 0x77, 0x63, 0x00};
        // X (88)
        FONT_DATA[56] = new byte[]{0x63, 0x63, 0x36, 0x1C, 0x1C, 0x36, 0x63, 0x00};
        // Y (89)
        FONT_DATA[57] = new byte[]{0x33, 0x33, 0x33, 0x1E, 0x0C, 0x0C, 0x1E, 0x00};
        // Z (90)
        FONT_DATA[58] = new byte[]{0x7F, 0x63, 0x31, 0x18, 0x4C, 0x66, 0x7F, 0x00};
        // [ (91)
        FONT_DATA[59] = new byte[]{0x1E, 0x06, 0x06, 0x06, 0x06, 0x06, 0x1E, 0x00};
        // \ (92)
        FONT_DATA[60] = new byte[]{0x03, 0x06, 0x0C, 0x18, 0x30, 0x60, 0x40, 0x00};
        // ] (93)
        FONT_DATA[61] = new byte[]{0x1E, 0x18, 0x18, 0x18, 0x18, 0x18, 0x1E, 0x00};
        // ^ (94)
        FONT_DATA[62] = new byte[]{0x08, 0x1C, 0x36, 0x63, 0x00, 0x00, 0x00, 0x00};
        // _ (95)
        FONT_DATA[63] = new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte)0xFF};
        // ` (96)
        FONT_DATA[64] = new byte[]{0x0C, 0x0C, 0x18, 0x00, 0x00, 0x00, 0x00, 0x00};
        // a (97)
        FONT_DATA[65] = new byte[]{0x00, 0x00, 0x1E, 0x30, 0x3E, 0x33, 0x6E, 0x00};
        // b (98)
        FONT_DATA[66] = new byte[]{0x07, 0x06, 0x06, 0x3E, 0x66, 0x66, 0x3B, 0x00};
        // c (99)
        FONT_DATA[67] = new byte[]{0x00, 0x00, 0x1E, 0x33, 0x03, 0x33, 0x1E, 0x00};
        // d (100)
        FONT_DATA[68] = new byte[]{0x38, 0x30, 0x30, 0x3e, 0x33, 0x33, 0x6E, 0x00};
        // e (101)
        FONT_DATA[69] = new byte[]{0x00, 0x00, 0x1E, 0x33, 0x3f, 0x03, 0x1E, 0x00};
        // f (102)
        FONT_DATA[70] = new byte[]{0x1C, 0x36, 0x06, 0x0f, 0x06, 0x06, 0x0F, 0x00};
        // g (103)
        FONT_DATA[71] = new byte[]{0x00, 0x00, 0x6E, 0x33, 0x33, 0x3E, 0x30, 0x1F};
        // h (104)
        FONT_DATA[72] = new byte[]{0x07, 0x06, 0x36, 0x6E, 0x66, 0x66, 0x67, 0x00};
        // i (105)
        FONT_DATA[73] = new byte[]{0x0C, 0x00, 0x0E, 0x0C, 0x0C, 0x0C, 0x1E, 0x00};
        // j (106)
        FONT_DATA[74] = new byte[]{0x30, 0x00, 0x30, 0x30, 0x30, 0x33, 0x33, 0x1E};
        // k (107)
        FONT_DATA[75] = new byte[]{0x07, 0x06, 0x66, 0x36, 0x1E, 0x36, 0x67, 0x00};
        // l (108)
        FONT_DATA[76] = new byte[]{0x0E, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x1E, 0x00};
        // m (109)
        FONT_DATA[77] = new byte[]{0x00, 0x00, 0x33, 0x7F, 0x7F, 0x6B, 0x63, 0x00};
        // n (110)
        FONT_DATA[78] = new byte[]{0x00, 0x00, 0x1F, 0x33, 0x33, 0x33, 0x33, 0x00};
        // o (111)
        FONT_DATA[79] = new byte[]{0x00, 0x00, 0x1E, 0x33, 0x33, 0x33, 0x1E, 0x00};
        // p (112)
        FONT_DATA[80] = new byte[]{0x00, 0x00, 0x3B, 0x66, 0x66, 0x3E, 0x06, 0x0F};
        // q (113)
        FONT_DATA[81] = new byte[]{0x00, 0x00, 0x6E, 0x33, 0x33, 0x3E, 0x30, 0x78};
        // r (114)
        FONT_DATA[82] = new byte[]{0x00, 0x00, 0x3B, 0x6E, 0x66, 0x06, 0x0F, 0x00};
        // s (115)
        FONT_DATA[83] = new byte[]{0x00, 0x00, 0x3E, 0x03, 0x1E, 0x30, 0x1F, 0x00};
        // t (116)
        FONT_DATA[84] = new byte[]{0x08, 0x0C, 0x3E, 0x0C, 0x0C, 0x2C, 0x18, 0x00};
        // u (117)
        FONT_DATA[85] = new byte[]{0x00, 0x00, 0x33, 0x33, 0x33, 0x33, 0x6E, 0x00};
        // v (118)
        FONT_DATA[86] = new byte[]{0x00, 0x00, 0x33, 0x33, 0x33, 0x1E, 0x0C, 0x00};
        // w (119)
        FONT_DATA[87] = new byte[]{0x00, 0x00, 0x63, 0x6B, 0x7F, 0x7F, 0x36, 0x00};
        // x (120)
        FONT_DATA[88] = new byte[]{0x00, 0x00, 0x63, 0x36, 0x1C, 0x36, 0x63, 0x00};
        // y (121)
        FONT_DATA[89] = new byte[]{0x00, 0x00, 0x33, 0x33, 0x33, 0x3E, 0x30, 0x1F};
        // z (122)
        FONT_DATA[90] = new byte[]{0x00, 0x00, 0x3F, 0x19, 0x0C, 0x26, 0x3F, 0x00};
        // { (123)
        FONT_DATA[91] = new byte[]{0x38, 0x0C, 0x0C, 0x07, 0x0C, 0x0C, 0x38, 0x00};
        // | (124)
        FONT_DATA[92] = new byte[]{0x18, 0x18, 0x18, 0x00, 0x18, 0x18, 0x18, 0x00};
        // } (125)
        FONT_DATA[93] = new byte[]{0x07, 0x0C, 0x0C, 0x38, 0x0C, 0x0C, 0x07, 0x00};
        // ~ (126)
        FONT_DATA[94] = new byte[]{0x6E, 0x3B, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    }
    
    /**
     * Draws a filled rectangle.
     * 
     * @param x X-coordinate of the top-left corner
     * @param y Y-coordinate of the top-left corner
     * @param width Width of the rectangle
     * @param height Height of the rectangle
     * @param r Red component (0.0-1.0)
     * @param g Green component (0.0-1.0)
     * @param b Blue component (0.0-1.0)
     * @param a Alpha component (0.0-1.0)
     */
    public void drawFilledRect(float x, float y, float width, float height, 
                              float r, float g, float b, float a) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + width, y);
        GL11.glVertex2f(x + width, y + height);
        GL11.glVertex2f(x, y + height);
        GL11.glEnd();
    }
    
    /**
     * Draws a panel (filled rectangle with border).
     * 
     * @param x X-coordinate of the top-left corner
     * @param y Y-coordinate of the top-left corner
     * @param width Width of the panel
     * @param height Height of the panel
     * @param bgR Background red component (0.0-1.0)
     * @param bgG Background green component (0.0-1.0)
     * @param bgB Background blue component (0.0-1.0)
     * @param bgA Background alpha component (0.0-1.0)
     * @param borderR Border red component (0.0-1.0)
     * @param borderG Border green component (0.0-1.0)
     * @param borderB Border blue component (0.0-1.0)
     * @param borderA Border alpha component (0.0-1.0)
     */
    public void drawPanel(float x, float y, float width, float height,
                         float bgR, float bgG, float bgB, float bgA,
                         float borderR, float borderG, float borderB, float borderA) {
        // Draw background
        drawFilledRect(x, y, width, height, bgR, bgG, bgB, bgA);
        
        // Draw border
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(borderR, borderG, borderB, borderA);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + width, y);
        GL11.glVertex2f(x + width, y + height);
        GL11.glVertex2f(x, y + height);
        GL11.glEnd();
    }
    
    /**
     * Draws text using the embedded 8x8 bitmap font.
     * 
     * <p>The embedded font supports ASCII characters 32-126. Unsupported
     * characters will be rendered as spaces. Text is rendered using immediate
     * mode quads for each on-pixel in the bitmap.</p>
     * 
     * @param text The text to render
     * @param x X-coordinate of the top-left corner of the text
     * @param y Y-coordinate of the top-left corner of the text
     * @param scale Scale factor for the text (1.0 = 8x8 pixels per character)
     * @param r Red component (0.0-1.0)
     * @param g Green component (0.0-1.0)
     * @param b Blue component (0.0-1.0)
     * @param a Alpha component (0.0-1.0)
     */
    public void drawText(String text, float x, float y, float scale, 
                        float r, float g, float b, float a) {
        if (text == null || text.isEmpty()) {
            return;
        }
        
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(r, g, b, a);
        
        float charWidth = 8.0f * scale;
        float charHeight = 8.0f * scale;
        float pixelSize = scale;
        
        float currentX = x;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int charIndex = c - 32; // ASCII 32 is the first character in our font
            
            // Skip unsupported characters or render as space
            if (charIndex < 0 || charIndex >= FONT_DATA.length) {
                currentX += charWidth;
                continue;
            }
            
            byte[] charData = FONT_DATA[charIndex];
            
            // Draw each pixel of the character
            GL11.glBegin(GL11.GL_QUADS);
            for (int row = 0; row < 8; row++) {
                byte rowData = charData[row];
                for (int col = 0; col < 8; col++) {
                    // Check if pixel is on (bit is set)
                    if ((rowData & (1 << (7 - col))) != 0) {
                        float px = currentX + col * pixelSize;
                        float py = y + row * pixelSize;
                        
                        GL11.glVertex2f(px, py);
                        GL11.glVertex2f(px + pixelSize, py);
                        GL11.glVertex2f(px + pixelSize, py + pixelSize);
                        GL11.glVertex2f(px, py + pixelSize);
                    }
                }
            }
            GL11.glEnd();
            
            currentX += charWidth;
        }
    }
    
    /**
     * Prepares the renderer for 2D UI rendering.
     * Sets up orthographic projection and necessary OpenGL state.
     * 
     * @param windowWidth Width of the window
     * @param windowHeight Height of the window
     */
    public void begin2D(int windowWidth, int windowHeight) {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, windowWidth, windowHeight, 0, -1, 1);
        
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }
    
    /**
     * Restores the OpenGL state after 2D UI rendering.
     */
    public void end2D() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
    }
}
