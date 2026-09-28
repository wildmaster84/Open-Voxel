package engine.ui.widgets;

import engine.ui.GLUIRenderer;

/** Simple colored panel with optional border. */
public class Panel extends Widget {
    private float r, g, b, a;
    private float borderR = -1f, borderG, borderB, borderA, borderWidth;

    public Panel(float x, float y, float width, float height, float r, float g, float b, float a) {
        super(x, y, width, height);
        this.r = r; this.g = g; this.b = b; this.a = a;
    }

    public Panel withBorder(float width, float r, float g, float b, float a) {
        this.borderWidth = width;
        this.borderR = r; this.borderG = g; this.borderB = b; this.borderA = a;
        return this;
    }

    @Override
    protected void renderWidget() {
        GLUIRenderer.drawPanel(x, y, width, height, r, g, b, a);
        if (borderR >= 0f) {
            GLUIRenderer.drawBorder(x, y, width, height, borderWidth, borderR, borderG, borderB, borderA);
        }
    }
}
