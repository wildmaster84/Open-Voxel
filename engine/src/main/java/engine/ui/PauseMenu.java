package engine.ui;

import engine.VoxelEngine;
import engine.rendering.Camera;
import engine.ui.widgets.Button;
import engine.ui.widgets.Label;
import engine.ui.widgets.Panel;
import engine.ui.widgets.Screen;

/**
 * Pause menu built on the widget toolkit. The whole menu is declarative —
 * compare with the old hand-computed layout + duplicated hit-testing.
 */
public class PauseMenu extends Screen {

    public PauseMenu(Camera camera, int windowWidth, int windowHeight) {
        super(camera);
    }

    @Override
    protected void build(float w, float h) {
        final float PANEL_WIDTH = 300;
        final float PANEL_HEIGHT = 400;
        final float BUTTON_WIDTH = 150;
        final float BUTTON_HEIGHT = 40;

        float panelX = (w - PANEL_WIDTH) / 2;
        float panelY = (h - PANEL_HEIGHT) / 2;

        // Dim background
        add(new Panel(0, 0, w, h, 0.0f, 0.0f, 0.0f, 0.5f));

        Panel panel = add(new Panel(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0.2f, 0.2f, 0.2f, 0.9f));
        panel.withBorder(2.0f, 0.6f, 0.6f, 0.6f, 1.0f);

        add(new Label(panelX, panelY + 30, "Paused").center(PANEL_WIDTH));

        float buttonX = panelX + (PANEL_WIDTH - BUTTON_WIDTH) / 2;
        add(new Button(buttonX, panelY + 80, BUTTON_WIDTH, BUTTON_HEIGHT, "Resume",
                btn -> close()));

        add(new Button(buttonX, panelY + 140, BUTTON_WIDTH, BUTTON_HEIGHT, "Main Menu",
                btn -> {
                    Runnable handler = VoxelEngine.getEngine().getExitToMenuHandler();
                    if (handler != null) {
                        handler.run();
                    } else {
                        // Standalone engine (no game layer): quitting is the only exit.
                        VoxelEngine.getEngine().cleanup();
                        System.exit(0);
                    }
                }));

        add(new Label(panelX, panelY + PANEL_HEIGHT - 40, "Press ESC to resume")
                .center(PANEL_WIDTH)
                .color(0.7f, 0.7f, 0.7f, 1.0f));
    }
}
