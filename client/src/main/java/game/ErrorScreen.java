package game;

import engine.ui.UIManager;
import engine.ui.widgets.Button;
import engine.ui.widgets.Label;
import engine.ui.widgets.Panel;
import engine.ui.widgets.Screen;
import org.lwjgl.glfw.GLFW;

/** Modal error dialog: title + wrapped message + Back to menu. */
public class ErrorScreen extends Screen {
    private final String title;
    private final String message;

    public ErrorScreen(int width, int height, String title, String message) {
        super(width, height);
        this.title = title;
        this.message = message == null ? "" : message;
    }

    @Override
    protected void build(float w, float h) {
        final float PANEL_WIDTH = 460;
        final float PANEL_HEIGHT = 260;
        float panelX = (w - PANEL_WIDTH) / 2;
        float panelY = (h - PANEL_HEIGHT) / 2;

        add(new Panel(0, 0, w, h, 0.0f, 0.0f, 0.0f, 0.5f));
        Panel panel = add(new Panel(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0.15f, 0.12f, 0.12f, 0.95f));
        panel.withBorder(2.0f, 0.75f, 0.3f, 0.3f, 1.0f);

        add(new Label(panelX, panelY + 28, title).center(PANEL_WIDTH));

        // Wrap the message to the panel width (~55 chars at default scale).
        float ty = panelY + 70;
        for (String line : wrap(message, 55)) {
            add(new Label(panelX + 20, ty, line).color(0.85f, 0.85f, 0.85f, 1.0f));
            ty += 18;
        }

        add(new Button(panelX + (PANEL_WIDTH - 150) / 2, panelY + PANEL_HEIGHT - 60, 150, 40,
                "Back to menu", b -> back()));
    }

    private void back() {
        UIManager.get().closeAllGUIs();
        UIManager.get().openGUI(new MainMenuScreen(windowWidth, windowHeight));
    }

    @Override
    public void onKeyPress(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
            back();
            return;
        }
        super.onKeyPress(key);
    }

    private static java.util.List<String> wrap(String text, int maxChars) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (line.length() > 0 && line.length() + word.length() + 1 > maxChars) {
                    lines.add(line.toString());
                    line = new StringBuilder();
                }
                if (line.length() > 0) line.append(' ');
                line.append(word);
            }
            lines.add(line.toString());
        }
        return lines;
    }
}
