package engine.ui;

import org.lwjgl.opengl.GL11;
import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import engine.rendering.Texture;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class GLUIRenderer {
    
    private static boolean initialized = false;
    private static int fontTextureId = -1;
    private static final int BITMAP_W = 512;
    private static final int BITMAP_H = 512;
    private static final float FONT_SIZE = 18.0f;
    private static STBTTBakedChar.Buffer cdata;
    private static final int CHAR_START = 32;
    private static final int CHAR_COUNT = 96; 
    
    private static void initializeFont() {
        if (initialized) {
            return;
        }
        
        try {
            ByteBuffer ttf = createDefaultFontData();
            
            if (ttf == null || ttf.capacity() == 0) {
                throw new RuntimeException("Failed to load font data");
            }
            
            ByteBuffer bitmap = MemoryUtil.memAlloc(BITMAP_W * BITMAP_H);
            cdata = STBTTBakedChar.malloc(CHAR_COUNT);
            
            int result = STBTruetype.stbtt_BakeFontBitmap(ttf, FONT_SIZE, bitmap, BITMAP_W, BITMAP_H, CHAR_START, cdata);
            
            if (result <= 0) {
                throw new RuntimeException("Failed to bake font bitmap");
            }
            
            fontTextureId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, fontTextureId);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_ALPHA, BITMAP_W, BITMAP_H, 0, 
                            GL11.GL_ALPHA, GL11.GL_UNSIGNED_BYTE, bitmap);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            
            MemoryUtil.memFree(bitmap);
            MemoryUtil.memFree(ttf);
            
            initialized = true;
        } catch (Exception e) {
            System.err.println("CRITICAL: Failed to initialize font rendering: " + e.getMessage());
            e.printStackTrace();
            System.err.println("Please ensure ARIAL.TTF is present in ./resources/ or src/main/resources/");
            initialized = true;
        }
    }
    
    private static ByteBuffer createDefaultFontData() {
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
            } catch (Exception e) {}
        }
        
        throw new RuntimeException("Could not load any TrueType font.");
    }
    
    public static void setup2DRendering(int windowWidth, int windowHeight) {
        // Initialize font system if needed
        if (!initialized) {
            initializeFont();
        }
        
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
    
    public static void restore3DRendering() {
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
    }

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

    public static void drawRect(float x, float y, float width, float height,
                                 float r, float g, float b, float a) {
        drawPanel(x, y, width, height, r, g, b, a);
    }

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

    public static void drawText(String text, float x, float y) {
        drawText(text, x, y, 1.0f, 1.0f, 1.0f, 1.0f);
    }
    
    public static void drawText(String text, float x, float y, float r, float g, float b, float a) {
        if (text == null || text.isEmpty()) {
            return;
        }
        
        if (!initialized || fontTextureId == -1 || cdata == null) {
        	throw new RuntimeException("Failed to draw text to screen.");
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

    private static int getCodepoint(String text, int index, IntBuffer cpOut) {
        char c = text.charAt(index);
        cpOut.put(0, c);
        return 1;
    }
    
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

    public static float getFontHeight() {
        return FONT_SIZE;
    }
    
    public static void drawButton(float x, float y, float width, float height, String text,
                                    float bgR, float bgG, float bgB, float bgA) {
        drawPanel(x, y, width, height, bgR, bgG, bgB, bgA);
        
        drawBorder(x, y, width, height, 2.0f, 0.8f, 0.8f, 0.8f, 1.0f);
        
        float textWidth = getTextWidth(text);
        float textHeight = getFontHeight();
        float textX = x + (width - textWidth) / 2;
        float textY = y + (height - textHeight) / 2 + textHeight * 0.75f;
        drawText(text, textX, textY);
    }

    public static boolean isPointInRect(float pointX, float pointY,
                                        float rectX, float rectY,
                                        float rectWidth, float rectHeight) {
        return pointX >= rectX && pointX <= rectX + rectWidth &&
               pointY >= rectY && pointY <= rectY + rectHeight;
    }
    
    /**
     * Draw a textured quad using the provided Texture object.
     * The Texture class in the renderer exposes bind()/unbind() (we use bind()); this function
     * will bind the texture and draw a quad with texture coords 0..1.
     */
    public static void drawTexture(Texture tex, float x, float y, float width, float height) {
        if (tex == null) return;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        tex.bind();
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glBegin(GL11.GL_QUADS);
        // top-left
        GL11.glTexCoord2f(0f, 1f);
        GL11.glVertex2f(x, y);
        // top-right
        GL11.glTexCoord2f(1f, 1f);
        GL11.glVertex2f(x + width, y);
        // bottom-right
        GL11.glTexCoord2f(1f, 0f);
        GL11.glVertex2f(x + width, y + height);
        // bottom-left
        GL11.glTexCoord2f(0f, 0f);
        GL11.glVertex2f(x, y + height);
        GL11.glEnd();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }
    
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