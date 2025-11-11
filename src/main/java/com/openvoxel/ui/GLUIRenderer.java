package com.openvoxel.ui;

import org.lwjgl.opengl.GL11;

/**
 * Helper class providing minimal OpenGL utilities for rendering UI elements.
 * This class uses immediate mode OpenGL (GL11) for simplicity and compatibility
 * with the existing LWJGL-based renderer in the repository.
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
 * // Draw placeholder text
 * GLUIRenderer.drawText("Resume", 145, 265);
 * 
 * // Restore 3D rendering mode
 * GLUIRenderer.restore3DRendering();
 * </pre>
 * 
 * <p>Note: This is a minimal implementation. For production use, consider using
 * a proper font rendering library and modern OpenGL techniques.
 */
public class GLUIRenderer {
    
    /**
     * Sets up 2D orthographic projection for UI rendering.
     * Call this before rendering any UI elements, and call {@link #restore3DRendering()}
     * when done to restore the previous state.
     * 
     * @param windowWidth The width of the window in pixels
     * @param windowHeight The height of the window in pixels
     */
    public static void setup2DRendering(int windowWidth, int windowHeight) {
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
     * Draws placeholder text using simple line-based rendering.
     * This is a basic implementation for demonstration purposes.
     * For production use, integrate a proper font rendering library like STB TrueType.
     * 
     * <p>Note: This implementation draws very simple block text and should be
     * replaced with proper font rendering in a production environment.
     * 
     * @param text The text to render
     * @param x The x-coordinate of the text baseline
     * @param y The y-coordinate of the text baseline
     */
    public static void drawText(String text, float x, float y) {
        // This is a placeholder implementation that draws simple blocks for each character
        // In production, use a proper font rendering library (e.g., STB TrueType via LWJGL)
        
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        
        float charWidth = 8;
        float charHeight = 12;
        
        for (int i = 0; i < text.length(); i++) {
            float cx = x + i * charWidth;
            
            // Draw a simple rectangle for each character (placeholder)
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex2f(cx, y);
            GL11.glVertex2f(cx + charWidth - 2, y);
            GL11.glVertex2f(cx + charWidth - 2, y + charHeight);
            GL11.glVertex2f(cx, y + charHeight);
            GL11.glEnd();
        }
        
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
    
    /**
     * Draws placeholder text with a specified color.
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
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(r, g, b, a);
        
        float charWidth = 8;
        float charHeight = 12;
        
        for (int i = 0; i < text.length(); i++) {
            float cx = x + i * charWidth;
            
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex2f(cx, y);
            GL11.glVertex2f(cx + charWidth - 2, y);
            GL11.glVertex2f(cx + charWidth - 2, y + charHeight);
            GL11.glVertex2f(cx, y + charHeight);
            GL11.glEnd();
        }
        
        GL11.glEnable(GL11.GL_TEXTURE_2D);
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
        
        // Draw text centered (approximate centering)
        float textX = x + (width - text.length() * 8) / 2;
        float textY = y + (height - 12) / 2;
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
}
