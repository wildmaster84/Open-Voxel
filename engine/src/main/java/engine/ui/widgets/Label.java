package engine.ui.widgets;

import engine.ui.GLUIRenderer;

/** Text label with alignment. */
public class Label extends Widget {
    private String text;
    private float r = 1f, g = 1f, b = 1f, a = 1f;
    private boolean center;

    public Label(float x, float y, String text) {
        super(x, y, 0, GLUIRenderer.getFontHeight());
        this.text = text;
    }

    public Label center(float containerWidth) {
        this.center = true;
        this.width = containerWidth;
        return this;
    }

    public Label color(float r, float g, float b, float a) {
        this.r = r; this.g = g; this.b = b; this.a = a;
        return this;
    }

    public void setText(String text) { this.text = text; }

    @Override
    protected void renderWidget() {
        if (text == null) return;
        float textX = center ? x + (width - GLUIRenderer.getTextWidth(text)) / 2 : x;
        GLUIRenderer.drawText(text, textX, y, r, g, b, a);
    }
}
