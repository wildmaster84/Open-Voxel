package com.openvoxel.ui;

import org.lwjgl.opengl.GL11;
import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * Helper class providing OpenGL utilities for rendering UI elements.
 * This class uses immediate mode OpenGL (GL11) for simplicity and compatibility
 * with the existing LWJGL-based renderer in the repository.
 * 
 * <p>Features a built-in bitmap font system that renders text without requiring
 * external font files. The font is rendered at initialization using STB TrueType.
 * 
 * <p>Common usage:
 * <pre>
 * // Setup 2D rendering mode before drawing UI
 * GLUIRenderer.setup2DRendering(windowWidth, windowHeight);
 * 
 * // Draw a semi-transparent panel
 * GLUIRenderer.drawPanel(100, 100, 300, 200, 0.2f, 0.2f, 0.2f, 0.8f);
 * 
 * // Draw a button background
 * GLUIRenderer.drawRect(120, 250, 150, 40, 0.4f, 0.4f, 0.4f, 1.0f);
 * 
 * // Draw text
 * GLUIRenderer.drawText("Resume", 145, 265);
 * 
 * // Restore 3D rendering mode
 * GLUIRenderer.restore3DRendering();
 * </pre>
 */
public class GLUIRenderer {
    
    private static boolean initialized = false;
    private static int fontTextureId = -1;
    private static final int BITMAP_W = 512;
    private static final int BITMAP_H = 512;
    private static final float FONT_SIZE = 18.0f;
    private static STBTTBakedChar.Buffer cdata;
    private static final int CHAR_START = 32;  // Space character
    private static final int CHAR_COUNT = 96;  // ASCII printable characters
    
    /**
     * Initializes the font rendering system.
     * Loads ARIAL.TTF from resources and creates a bitmap font texture.
     * Called automatically on first use.
     */
    private static void initializeFont() {
        if (initialized) {
            return;
        }
        
        try {
            // Load TrueType font from resources or file system
            ByteBuffer ttf = createDefaultFontData();
            
            if (ttf == null || ttf.capacity() == 0) {
                throw new RuntimeException("Failed to load font data");
            }
            
            // Create bitmap buffer for font atlas
            ByteBuffer bitmap = MemoryUtil.memAlloc(BITMAP_W * BITMAP_H);
            cdata = STBTTBakedChar.malloc(CHAR_COUNT);
            
            // Bake font into bitmap
            int result = STBTruetype.stbtt_BakeFontBitmap(ttf, FONT_SIZE, bitmap, BITMAP_W, BITMAP_H, CHAR_START, cdata);
            
            if (result <= 0) {
                throw new RuntimeException("Failed to bake font bitmap");
            }
            
            // Create OpenGL texture from bitmap
            fontTextureId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, fontTextureId);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_ALPHA, BITMAP_W, BITMAP_H, 0, 
                            GL11.GL_ALPHA, GL11.GL_UNSIGNED_BYTE, bitmap);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            
            // Clean up temporary buffers
            MemoryUtil.memFree(bitmap);
            MemoryUtil.memFree(ttf);
            
            initialized = true;
            System.out.println("Font system initialized successfully");
        } catch (Exception e) {
            System.err.println("CRITICAL: Failed to initialize font rendering: " + e.getMessage());
            e.printStackTrace();
            System.err.println("Please ensure ARIAL.TTF is present in ./resources/ or src/main/resources/");
            // Mark as initialized to prevent repeated attempts
            initialized = true;
        }
    }
    
    /**
     * Loads font data from resources or system paths.
     * Priority: 1) Game resources, 2) System fonts, 3) Fallback
     */
    private static ByteBuffer createDefaultFontData() {
        // First, try to load from game resources
        try {
            java.io.InputStream resourceStream = GLUIRenderer.class.getResourceAsStream("/ARIAL.TTF");
            if (resourceStream == null) {
                resourceStream = GLUIRenderer.class.getResourceAsStream("/arial.ttf");
            }
            
            if (resourceStream != null) {
                byte[] fontData = resourceStream.readAllBytes();
                resourceStream.close();
                
                if (fontData.length > 0) {
                    ByteBuffer buffer = MemoryUtil.memAlloc(fontData.length);
                    buffer.put(fontData);
                    buffer.flip();
                    return buffer;
                }
            }
        } catch (Exception e) {
            System.err.println("Could not load font from resources: " + e.getMessage());
        }
        
        // Try to load from file system path relative to working directory
        String[] resourcePaths = {
            "./resources/ARIAL.TTF",
            "./resources/arial.ttf",
            "resources/ARIAL.TTF",
            "resources/arial.ttf"
        };
        
        for (String path : resourcePaths) {
            try {
                java.io.File fontFile = new java.io.File(path);
                if (fontFile.exists()) {
                    java.io.FileInputStream fis = new java.io.FileInputStream(fontFile);
                    byte[] fontData = fis.readAllBytes();
                    fis.close();
                    
                    if (fontData.length > 0) {
                        ByteBuffer buffer = MemoryUtil.memAlloc(fontData.length);
                        buffer.put(fontData);
                        buffer.flip();
                        return buffer;
                    }
                }
            } catch (Exception e) {
                // Continue to next path
            }
        }
        
        // Try to load from common system font paths
        String[] systemFontPaths = {
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/System/Library/Fonts/Helvetica.ttc",
            "C:\\Windows\\Fonts\\arial.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"
        };
        
        for (String path : systemFontPaths) {
            try {
                java.io.File fontFile = new java.io.File(path);
                if (fontFile.exists()) {
                    java.io.FileInputStream fis = new java.io.FileInputStream(fontFile);
                    byte[] fontData = fis.readAllBytes();
                    fis.close();
                    
                    if (fontData.length > 0) {
                        ByteBuffer buffer = MemoryUtil.memAlloc(fontData.length);
                        buffer.put(fontData);
                        buffer.flip();
                        return buffer;
                    }
                }
            } catch (Exception e) {
                // Continue to next font
            }
        }
        
        // If no font found, throw an error - we need a real font
        throw new RuntimeException("Could not load any TrueType font. Please place ARIAL.TTF in ./resources/ directory or src/main/resources/");
    }
    
    /**
     * Sets up 2D orthographic projection for UI rendering.
     * Call this before rendering any UI elements, and call {@link #restore3DRendering()}
     * when done to restore the previous state.
     * 
     * @param windowWidth The width of the window in pixels
     * @param windowHeight The height of the window in pixels
     */
    public static void setup2DRendering(int windowWidth, int windowHeight) {
        // Initialize font system if needed
        if (!initialized) {
            initializeFont();
        }
        
        // Save current matrices
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        
        // Set up orthographic projection (0,0 at top-left)
        GL11.glOrtho(0, windowWidth, windowHeight, 0, -1, 1);
        
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        
        // Disable depth testing for UI
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        
        // Enable blending for transparency
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }
    
    /**
     * Restores 3D rendering mode after UI rendering.
     * Call this after rendering all UI elements to restore the previous OpenGL state.
     */
    public static void restore3DRendering() {
        // Restore previous state
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        
        // Restore matrices
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
    }
    
    /**
     * Draws a filled rectangle (panel) with the specified color and alpha.
     * 
     * @param x The x-coordinate of the top-left corner
     * @param y The y-coordinate of the top-left corner
     * @param width The width of the rectangle
     * @param height The height of the rectangle
     * @param r The red component (0.0 to 1.0)
     * @param g The green component (0.0 to 1.0)
     * @param b The blue component (0.0 to 1.0)
     * @param a The alpha component (0.0 to 1.0, where 1.0 is fully opaque)
     */
    public static void drawPanel(float x, float y, float width, float height, 
                                   float r, float g, float b, float a) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(r, g, b, a);
        
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + width, y);
        GL11.glVertex2f(x + width, y + height);
        GL11.glVertex2f(x, y + height);
        GL11.glEnd();
        
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
    
    /**
     * Draws a filled rectangle with the specified color.
     * This is an alias for {@link #drawPanel} for better API clarity.
     * 
     * @param x The x-coordinate of the top-left corner
     * @param y The y-coordinate of the top-left corner
     * @param width The width of the rectangle
     * @param height The height of the rectangle
     * @param r The red component (0.0 to 1.0)
     * @param g The green component (0.0 to 1.0)
     * @param b The blue component (0.0 to 1.0)
     * @param a The alpha component (0.0 to 1.0)
     */
    public static void drawRect(float x, float y, float width, float height,
                                 float r, float g, float b, float a) {
        drawPanel(x, y, width, height, r, g, b, a);
    }
    
    /**
     * Draws a rectangle outline (border) with the specified color.
     * 
     * @param x The x-coordinate of the top-left corner
     * @param y The y-coordinate of the top-left corner
     * @param width The width of the rectangle
     * @param height The height of the rectangle
     * @param lineWidth The width of the border line
     * @param r The red component (0.0 to 1.0)
     * @param g The green component (0.0 to 1.0)
     * @param b The blue component (0.0 to 1.0)
     * @param a The alpha component (0.0 to 1.0)
     */
    public static void drawBorder(float x, float y, float width, float height,
                                    float lineWidth, float r, float g, float b, float a) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(r, g, b, a);
        GL11.glLineWidth(lineWidth);
        
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + width, y);
        GL11.glVertex2f(x + width, y + height);
        GL11.glVertex2f(x, y + height);
        GL11.glEnd();
        
        GL11.glLineWidth(1.0f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
    
    /**
     * Draws text using the bitmap font system.
     * Renders crisp, readable text at any position on screen.
     * 
     * @param text The text to render
     * @param x The x-coordinate of the text baseline
     * @param y The y-coordinate of the text baseline
     */
    public static void drawText(String text, float x, float y) {
        drawText(text, x, y, 1.0f, 1.0f, 1.0f, 1.0f);
    }
    
    /**
     * Draws text with a specified color using the bitmap font system.
     * 
     * @param text The text to render
     * @param x The x-coordinate of the text baseline
     * @param y The y-coordinate of the text baseline
     * @param r The red component (0.0 to 1.0)
     * @param g The green component (0.0 to 1.0)
     * @param b The blue component (0.0 to 1.0)
     * @param a The alpha component (0.0 to 1.0)
     */
    public static void drawText(String text, float x, float y, float r, float g, float b, float a) {
        if (text == null || text.isEmpty()) {
            return;
        }
        
        if (!initialized || fontTextureId == -1 || cdata == null) {
            // Fallback to simple rendering if font system failed
            drawSimpleText(text, x, y, r, g, b, a);
            return;
        }
        
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, fontTextureId);
        
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        
        float xOffset = x;
        
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pCodepoint = stack.mallocInt(1);
            
            for (int i = 0; i < text.length(); ) {
                i += getCodepoint(text, i, pCodepoint);
                int codepoint = pCodepoint.get(0);
                
                if (codepoint == '\n') {
                    // Handle newlines if needed
                    continue;
                }
                
                if (codepoint < CHAR_START || codepoint >= CHAR_START + CHAR_COUNT) {
                    continue;
                }
                
                STBTTBakedChar charData = cdata.get(codepoint - CHAR_START);
                
                float x0 = xOffset + charData.xoff();
                float y0 = y + charData.yoff();
                float x1 = x0 + charData.x1() - charData.x0();
                float y1 = y0 + charData.y1() - charData.y0();
                
                float s0 = charData.x0() / (float) BITMAP_W;
                float t0 = charData.y0() / (float) BITMAP_H;
                float s1 = charData.x1() / (float) BITMAP_W;
                float t1 = charData.y1() / (float) BITMAP_H;
                
                GL11.glTexCoord2f(s0, t0);
                GL11.glVertex2f(x0, y0);
                
                GL11.glTexCoord2f(s1, t0);
                GL11.glVertex2f(x1, y0);
                
                GL11.glTexCoord2f(s1, t1);
                GL11.glVertex2f(x1, y1);
                
                GL11.glTexCoord2f(s0, t1);
                GL11.glVertex2f(x0, y1);
                
                xOffset += charData.xadvance();
            }
        }
        
        GL11.glEnd();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }
    
    /**
     * Simple fallback text rendering using filled rectangles.
     * Used when the bitmap font system fails to initialize.
     */
    private static void drawSimpleText(String text, float x, float y, float r, float g, float b, float a) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(r, g, b, a);
        
        float charWidth = 8;
        float charHeight = 12;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                continue;
            }
            
            float cx = x + i * charWidth;
            
            // Draw a filled rectangle for each character
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(cx, y);
            GL11.glVertex2f(cx + charWidth - 2, y);
            GL11.glVertex2f(cx + charWidth - 2, y + charHeight);
            GL11.glVertex2f(cx, y + charHeight);
            GL11.glEnd();
        }
        
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
    
    /**
     * Gets the next codepoint from a string.
     * Supports UTF-8 encoding.
     */
    private static int getCodepoint(String text, int index, IntBuffer cpOut) {
        char c = text.charAt(index);
        cpOut.put(0, c);
        return 1;
    }
    
    /**
     * Measures the width of a text string in pixels.
     * Useful for centering text or calculating layout.
     * 
     * @param text The text to measure
     * @return The width of the text in pixels
     */
    public static float getTextWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        if (!initialized || fontTextureId == -1 || cdata == null) {
            return text.length() * 8; // Fallback estimate
        }
        
        float width = 0;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < CHAR_START || c >= CHAR_START + CHAR_COUNT) {
                continue;
            }
            
            STBTTBakedChar charData = cdata.get(c - CHAR_START);
            width += charData.xadvance();
        }
        
        return width;
    }
    
    /**
     * Gets the height of the font in pixels.
     * 
     * @return The font height in pixels
     */
    public static float getFontHeight() {
        return FONT_SIZE;
    }
    
    /**
     * Draws a button with background and text.
     * This is a convenience method that combines panel and text rendering.
     * 
     * @param x The x-coordinate of the button
     * @param y The y-coordinate of the button
     * @param width The width of the button
     * @param height The height of the button
     * @param text The text to display on the button
     * @param bgR The background red component
     * @param bgG The background green component
     * @param bgB The background blue component
     * @param bgA The background alpha component
     */
    public static void drawButton(float x, float y, float width, float height, String text,
                                    float bgR, float bgG, float bgB, float bgA) {
        // Draw button background
        drawPanel(x, y, width, height, bgR, bgG, bgB, bgA);
        
        // Draw button border
        drawBorder(x, y, width, height, 2.0f, 0.8f, 0.8f, 0.8f, 1.0f);
        
        // Draw text centered using accurate text width measurement
        float textWidth = getTextWidth(text);
        float textHeight = getFontHeight();
        float textX = x + (width - textWidth) / 2;
        float textY = y + (height - textHeight) / 2 + textHeight * 0.75f; // Adjust for baseline
        drawText(text, textX, textY);
    }
    
    /**
     * Checks if a point is within a rectangle (useful for button hit detection).
     * 
     * @param pointX The x-coordinate of the point
     * @param pointY The y-coordinate of the point
     * @param rectX The x-coordinate of the rectangle
     * @param rectY The y-coordinate of the rectangle
     * @param rectWidth The width of the rectangle
     * @param rectHeight The height of the rectangle
     * @return true if the point is within the rectangle, false otherwise
     */
    public static boolean isPointInRect(float pointX, float pointY,
                                        float rectX, float rectY,
                                        float rectWidth, float rectHeight) {
        return pointX >= rectX && pointX <= rectX + rectWidth &&
               pointY >= rectY && pointY <= rectY + rectHeight;
    }
    
    /**
     * Cleans up resources used by the renderer.
     * Call this when shutting down the application.
     */
    public static void cleanup() {
        if (fontTextureId != -1) {
            GL11.glDeleteTextures(fontTextureId);
            fontTextureId = -1;
        }
        if (cdata != null) {
            cdata.free();
            cdata = null;
        }
        initialized = false;
    }
}
