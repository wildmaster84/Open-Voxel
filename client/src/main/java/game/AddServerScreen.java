package game;

import engine.ui.UIManager;
import engine.ui.widgets.Button;
import engine.ui.widgets.Label;
import engine.ui.widgets.Screen;
import engine.ui.widgets.TextField;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Add-server form opened by the + button: name, IP, port. */
public class AddServerScreen extends Screen {
    public AddServerScreen(int width, int height) {
        super(width, height);
    }

    @Override
    protected void build(float w, float h) {
        float cx = w / 2f;
        add(new Label(cx - 120, 60, "Add Server"));

        TextField nameField = add(new TextField(cx - 200, 140, 400, 34, "Server name"));
        TextField ipField   = add(new TextField(cx - 200, 190, 280, 34, "IP address"));
        TextField portField = add(new TextField(cx + 100, 190, 100, 34, "Port"));
        portField.setText("25565");

        add(new Button(cx - 200, 260, 190, 36, "Add Server", b -> {
            String name = nameField.getText().trim();
            String ip = ipField.getText().trim();
            if (name.isEmpty() || ip.isEmpty()) return;
            String port = portField.getText().trim();
            String address = ip + (port.isEmpty() ? "" : ":" + port);

            List<ServerList.Entry> servers = new ArrayList<>(ServerList.load());
            servers.add(new ServerList.Entry(name, address));
            ServerList.save(servers);

            UIManager.get().closeAllGUIs();
            UIManager.get().openGUI(new ServerSelectScreen(windowWidth, windowHeight));
        }));

        add(new Button(cx + 10, 260, 190, 36, "Cancel", b -> {
            UIManager.get().closeAllGUIs();
            UIManager.get().openGUI(new ServerSelectScreen(windowWidth, windowHeight));
        }));
    }

    @Override
    public void onKeyPress(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            UIManager.get().closeAllGUIs();
            UIManager.get().openGUI(new ServerSelectScreen(windowWidth, windowHeight));
            return;
        }
        super.onKeyPress(key);
    }
}
