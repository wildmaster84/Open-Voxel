package engine.gui;

public abstract class GUI {

    public void onOpen() {}

    public abstract void onTick(long tickDelta);

    public abstract void render();

    public void onMouseClick(int x, int y, int button) {}

    public void onKeyPress(int key) {}

    public void onClose() {}
}