package game;

import engine.VoxelEngine;
import engine.ui.UIManager;
import engine.ui.widgets.Button;
import engine.ui.widgets.Label;
import engine.ui.widgets.Screen;
import org.lwjgl.glfw.GLFW;

/** Title screen: logo + Singleplayer / Multiplayer / Quit. */
public class MainMenuScreen extends Screen {
    public MainMenuScreen(int width, int height) {
        super(width, height);
    }

    @Override
    protected void build(float w, float h) {
        float cx = w / 2f;
        float bw = 260f, bh = 44f, gap = 10f;
        float y = h * 0.42f;

        add(new Label(cx - 220, h * 0.14f, "O P E N - V O X E L"));

        add(new Button(cx - bw / 2, y, bw, bh, "Singleplayer", b -> {
            ClientMain.startSingleplayer();
        }));
        add(new Button(cx - bw / 2, y + (bh + gap), bw, bh, "Multiplayer", b -> {
            UIManager.get().closeAllGUIs();
            UIManager.get().openGUI(new ServerSelectScreen(windowWidth, windowHeight));
        }));
        add(new Button(cx - bw / 2, y + 2 * (bh + gap), bw, bh, "Quit", b -> {
            VoxelEngine.getEngine().stop();
        }));
    }

    @Override
    public void onKeyPress(int key) {
        // Root screen: Escape does nothing here (no closing the title screen).
        if (key == GLFW.GLFW_KEY_ESCAPE) return;
        super.onKeyPress(key);
    }
}
