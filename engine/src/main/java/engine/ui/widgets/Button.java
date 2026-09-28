package engine.ui.widgets;

import engine.ui.GLUIRenderer;

import java.util.function.Consumer;

/** Clickable button with hover highlight and a click callback. */
public class Button extends Widget {
    private String text;
    private Consumer<Button> onClick;

    private float bgR = 0.35f, bgG = 0.35f, bgB = 0.35f, bgA = 1.0f;
    private float hoverR = 0.5f, hoverG = 0.5f, hoverB = 0.5f, hoverA = 1.0f;

    public Button(float x, float y, float width, float height, String text, Consumer<Button> onClick) {
        super(x, y, width, height);
        this.text = text;
        this.onClick = onClick;
    }

    public Button bg(float r, float g, float b, float a) {
        this.bgR = r; this.bgG = g; this.bgB = b; this.bgA = a;
        return this;
    }

    public Button hover(float r, float g, float b, float a) {
        this.hoverR = r; this.hoverG = g; this.hoverB = b; this.hoverA = a;
        return this;
    }

    public void setText(String text) { this.text = text; }

    @Override
    protected void renderWidget() {
        float r = hovered ? hoverR : bgR;
        float g = hovered ? hoverG : bgG;
        float b = hovered ? hoverB : bgB;
        float a = hovered ? hoverA : bgA;

        GLUIRenderer.drawPanel(x, y, width, height, r, g, b, a);
        GLUIRenderer.drawBorder(x, y, width, height, 2.0f, 0.8f, 0.8f, 0.8f, 1.0f);

        float textWidth = GLUIRenderer.getTextWidth(text);
        float textHeight = GLUIRenderer.getFontHeight();
        float textX = x + (width - textWidth) / 2;
        float textY = y + (height - textHeight) / 2 + textHeight * 0.75f;
        GLUIRenderer.drawText(text, textX, textY, 1f, 1f, 1f, 1f);
    }

    @Override
    public void onClick(float mouseX, float mouseY, int button) {
        if (button == 0 && onClick != null) onClick.accept(this);
    }
}
