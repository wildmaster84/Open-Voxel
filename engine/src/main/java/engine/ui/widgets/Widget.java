package engine.ui.widgets;

import engine.ui.GLUIRenderer;

/**
 * Base for all UI widgets. Owns a rectangle, handles hover/click routing.
 * Subclasses implement renderWidget() and onClick().
 */
public abstract class Widget {
    protected float x, y, width, height;
    protected boolean hovered;
    protected boolean visible = true;
    protected boolean enabled = true;
    /** Screen-controlled (e.g. server-list selection). No behavior in Widget. */
    protected boolean selected = false;

    protected Widget(float x, float y, float width, float height) {
        this.x = x; this.y = y;
        this.width = width; this.height = height;
    }

    public void setBounds(float x, float y, float width, float height) {
        this.x = x; this.y = y;
        this.width = width; this.height = height;
    }

    public boolean contains(float px, float py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    /** Called every frame before render; updates hover state. */
    public void update(float mouseX, float mouseY) {
        hovered = visible && enabled && contains(mouseX, mouseY);
    }

    public final void render() {
        if (!visible) return;
        renderWidget();
    }

    protected abstract void renderWidget();

    /** Mouse click inside this widget (already hit-tested). */
    public void onClick(float mouseX, float mouseY, int button) {}

    // convenience passthroughs
    public boolean isHovered() { return hovered; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean v) { visible = v; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean s) { selected = s; }

    protected static boolean pointInRect(float px, float py, float rx, float ry, float rw, float rh) {
        return GLUIRenderer.isPointInRect(px, py, rx, ry, rw, rh);
    }
}
