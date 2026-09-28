package engine.ui.widgets;

import engine.ui.GLUIRenderer;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Single-line text input. Focused by clicking (Screen manages focus).
 * Receives unicode chars via charTyped() and editing keys via keyPress().
 */
public class TextField extends Widget {
    private String text = "";
    private final String placeholder;
    private int maxLength = 64;
    private boolean focused = false;
    private Consumer<TextField> onSubmit;

    public TextField(float x, float y, float width, float height, String placeholder) {
        super(x, y, width, height);
        this.placeholder = placeholder;
    }

    public String getText() { return text; }
    public void setText(String t) { text = t == null ? "" : t; }
    public boolean isFocused() { return focused; }
    public void setFocused(boolean f) { focused = f; }
    public TextField maxLength(int n) { maxLength = n; return this; }
    public TextField onSubmit(Consumer<TextField> cb) { onSubmit = cb; return this; }

    /** Unicode char from GLFW char callback (routed by Screen). */
    public void charTyped(int codepoint) {
        if (!focused) return;
        if (codepoint < 32 || codepoint == 127) return;
        if (text.length() >= maxLength) return;
        text += (char) codepoint;
    }

    /** @return true if the key was consumed (Screen should stop handling it). */
    public boolean keyPress(int key) {
        if (!focused) return false;
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!text.isEmpty()) text = text.substring(0, text.length() - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (onSubmit != null) onSubmit.accept(this);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            focused = false;
            return true;
        }
        return true; // focused field swallows other keys (no hotkeys while typing)
    }

    @Override
    public void onClick(float mouseX, float mouseY, int button) {
        // Focus is assigned by Screen.onMouseClick (click-inside test).
    }

    @Override
    protected void renderWidget() {
        GLUIRenderer.drawPanel(x, y, width, height, 0.12f, 0.12f, 0.14f, 1.0f);
        if (focused) {
            GLUIRenderer.drawBorder(x, y, width, height, 2.0f, 0.55f, 0.85f, 0.45f, 1.0f);
        } else {
            GLUIRenderer.drawBorder(x, y, width, height, 2.0f, 0.45f, 0.45f, 0.45f, 1.0f);
        }

        float fontH = GLUIRenderer.getFontHeight();
        float textY = y + (height - fontH) / 2 + fontH * 0.75f;
        float pad = 8f;

        if (text.isEmpty() && !focused) {
            GLUIRenderer.drawText(placeholder, x + pad, textY, 0.55f, 0.55f, 0.55f, 1.0f);
            return;
        }
        GLUIRenderer.drawText(text, x + pad, textY, 1f, 1f, 1f, 1f);

        // Blinking caret at end of text while focused.
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            float caretX = x + pad + GLUIRenderer.getTextWidth(text) + 2f;
            GLUIRenderer.drawRect(caretX, y + 6f, 2f, height - 12f, 1f, 1f, 1f, 1f);
        }
    }
}
