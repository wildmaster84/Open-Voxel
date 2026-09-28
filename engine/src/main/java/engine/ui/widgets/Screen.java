package engine.ui.widgets;

import engine.rendering.Camera;
import engine.ui.GLUIRenderer;
import engine.ui.UIManager;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * A GUI built from widgets. Subclasses add widgets in build(), and this class
 * handles rendering, hover updates and click routing — no per-menu hit-test code.
 */
public abstract class Screen extends engine.gui.GUI {
    protected final Camera camera; // null for menu screens (no world loaded)
    protected int windowWidth;
    protected int windowHeight;

    private final List<Widget> widgets = new ArrayList<>();

    protected Screen(Camera camera) {
        this.camera = camera;
        this.windowWidth = camera.getAspect()[0];
        this.windowHeight = camera.getAspect()[1];
    }

    /** Menu-mode screen: no camera/world. Size tracks the framebuffer. */
    protected Screen(int width, int height) {
        this.camera = null;
        this.windowWidth = width;
        this.windowHeight = height;
    }

    private int[] currentSize() {
        if (camera != null) return camera.getAspect();
        int[] w = new int[1], h = new int[1];
        GLFW.glfwGetFramebufferSize(UIManager.get().getWindow(), w, h);
        return new int[]{w[0], h[0]};
    }

    protected <T extends Widget> T add(T widget) {
        widgets.add(widget);
        return widget;
    }

    /** Widgets of a given type, for in-place updates (e.g. selection). */
    protected <T extends Widget> java.util.List<T> widgetsOf(Class<T> type) {
        java.util.List<T> out = new java.util.ArrayList<>();
        for (Widget w : widgets) if (type.isInstance(w)) out.add(type.cast(w));
        return out;
    }

    /** Build widgets once. Called on first render (GL context ready). */
    protected abstract void build(float w, float h);

    private boolean built = false;

    @Override
    public void onOpen() {
        if (camera != null) camera.getInputHandler().paused = true;
    }

    @Override
    public void onTick(long tickDelta) {
        int[] size = currentSize();
        if (windowWidth != size[0] || windowHeight != size[1]) {
            windowWidth = size[0];
            windowHeight = size[1];
            widgets.clear();
            built = false; // rebuild layout on resize
        }
    }

    @Override
    public void render() {
        if (!built) {
            build(windowWidth, windowHeight);
            built = true;
        }

        GLUIRenderer.setup2DRendering(windowWidth, windowHeight);

        double[] mx = new double[1], my = new double[1];
        GLFW.glfwGetCursorPos(UIManager.get().getWindow(), mx, my);

        for (Widget w : widgets) {
            w.update((float) mx[0], (float) my[0]);
            w.render();
        }

        GLUIRenderer.restore3DRendering();
    }

    @Override
    public void onMouseClick(int x, int y, int button) {
        // Focus follows the click for text fields (click outside = unfocus all).
        for (Widget w : widgets) {
            if (w instanceof TextField) {
                ((TextField) w).setFocused(w.isVisible() && w.contains(x, y));
            }
        }
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget w = widgets.get(i);
            if (w.isVisible() && w.contains(x, y)) {
                w.onClick(x, y, button);
                return;
            }
        }
    }

    @Override
    public void onKeyPress(int key) {
        for (Widget w : widgets) {
            if (w instanceof TextField && ((TextField) w).keyPress(key)) return;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
        }
    }

    @Override
    public void onCharTyped(int codepoint) {
        for (Widget w : widgets) {
            if (w instanceof TextField) ((TextField) w).charTyped(codepoint);
        }
    }

    @Override
    public void onClose() {
        if (camera != null) camera.getInputHandler().paused = false;
    }

    public void close() {
        UIManager.get().closeTopGUI();
    }
}
