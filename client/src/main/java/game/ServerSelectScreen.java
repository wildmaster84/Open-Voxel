package game;

import engine.ui.UIManager;
import engine.ui.widgets.Button;
import engine.ui.widgets.Label;
import engine.ui.widgets.Screen;
import engine.ui.widgets.TextField;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Minecraft-style multiplayer screen: server rows with ping + player count,
 * single-click select (highlight), double-click join, +/delete buttons,
 * and a direct-connect bar at the bottom.
 */
public class ServerSelectScreen extends Screen {
    private final List<ServerList.Entry> servers = new ArrayList<>();
    private final List<ServerPinger.Result> pings = new ArrayList<>();
    private int selected = -1;
    private long lastClickTime = 0;
    private int lastClickIndex = -1;

    public ServerSelectScreen(int width, int height) {
        super(width, height);
        servers.addAll(ServerList.load());
        for (int i = 0; i < servers.size(); i++) {
            pings.add(ServerPinger.ping(servers.get(i).address));
        }
    }

    @Override
    protected void build(float w, float h) {
        float cx = w / 2f;
        add(new Label(cx - 140, 40, "Multiplayer"));

        // ---- server rows (Minecraft-style: name, address, players, ping) ----
        float rowY = 90;
        float rowH = 56;
        for (int i = 0; i < servers.size(); i++) {
            ServerList.Entry e = servers.get(i);
            ServerPinger.Result p = pings.get(i);
            add(new ServerRow(cx - 250, rowY, 500, rowH, i, e, p, i == selected));
            rowY += rowH + 6;
        }

        // ---- control buttons row ----
        float btnY = h - 140;
        Button addBtn = new Button(cx - 250, btnY, 36, 36, "+", b -> {
            UIManager.get().closeAllGUIs();
            UIManager.get().openGUI(new AddServerScreen(windowWidth, windowHeight));
        });
        addBtn.bg(0.30f, 0.30f, 0.30f, 1f).hover(0.45f, 0.45f, 0.45f, 1f);
        add(addBtn);

        add(new Button(cx - 204, btnY, 90, 36, "Delete", b -> {
            if (selected < 0 || selected >= servers.size()) return;
            servers.remove(selected);
            if (selected < pings.size()) pings.remove(selected);
            selected = -1;
            ServerList.save(servers);
            rebuild();
        }));

        add(new Button(cx - 104, btnY, 90, 36, "Join", b -> joinSelected()));

        // ---- direct connect bar (bottom) ----
        float dcY = h - 90;
        TextField ipField = add(new TextField(cx - 250, dcY, 300, 34, "IP address"));
        TextField portField = add(new TextField(cx + 60, dcY, 90, 34, "Port"));
        portField.setText("25565");
        add(new Button(cx + 160, dcY, 90, 34, "Connect", b -> {
            String ip = ipField.getText().trim();
            String port = portField.getText().trim();
            if (ip.isEmpty()) return;
            String address = ip + (port.isEmpty() ? "" : ":" + port);
            ClientMain.onJoinServer(new ServerList.Entry("Direct: " + address, address));
        }));

        add(new Button(cx + 110, btnY, 140, 36, "Back", b -> {
            UIManager.get().closeAllGUIs();
            UIManager.get().openGUI(new MainMenuScreen(windowWidth, windowHeight));
        }));
    }

    private void joinSelected() {
        if (selected < 0 || selected >= servers.size()) return;
        ClientMain.onJoinServer(servers.get(selected));
    }

    /** Single click = select + highlight (in place); double click = join. */
    private void rowClicked(int idx) {
        long now = System.nanoTime() / 1_000_000;
        if (idx == lastClickIndex && now - lastClickTime < 450) {
            selected = idx;
            joinSelected();
            return;
        }
        lastClickTime = now;
        lastClickIndex = idx;
        selected = idx;
        for (ServerRow r : widgetsOf(ServerRow.class)) {
            r.setSelected(r.index == selected);
        }
    }

    private void rebuild() {
        UIManager.get().closeAllGUIs();
        UIManager.get().openGUI(new ServerSelectScreen(windowWidth, windowHeight));
    }

    @Override
    public void onKeyPress(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            UIManager.get().closeAllGUIs();
            UIManager.get().openGUI(new MainMenuScreen(windowWidth, windowHeight));
            return;
        }
        super.onKeyPress(key);
    }

    /** One server row: name (white), address (gray), players + ping bars (right). */
    private class ServerRow extends engine.ui.widgets.Widget {
        private final int index;
        private final ServerList.Entry entry;
        private final ServerPinger.Result ping;

        ServerRow(float x, float y, float w, float h, int index,
                   ServerList.Entry e, ServerPinger.Result p, boolean selected) {
            super(x, y, w, h);
            this.index = index;
            this.entry = e;
            this.ping = p;
            setSelected(selected);
        }

        @Override
        protected void renderWidget() {
            
            // Background: selected = bright highlight, hover = subtle, else dark.
            if (selected) {
                engine.ui.GLUIRenderer.drawPanel(x, y, width, height, 0.35f, 0.45f, 0.35f, 0.9f);
            } else if (hovered) {
                engine.ui.GLUIRenderer.drawPanel(x, y, width, height, 0.28f, 0.28f, 0.28f, 0.9f);
            } else {
                engine.ui.GLUIRenderer.drawPanel(x, y, width, height, 0.18f, 0.18f, 0.18f, 0.9f);
            }
            engine.ui.GLUIRenderer.drawBorder(x, y, width, height, 1.5f, 0.4f, 0.4f, 0.4f, 1f);

            // Name + address, vertically centered in the row.
            float fh = engine.ui.GLUIRenderer.getFontHeight();
            float textTop = y + (height - fh * 2f - 2f) / 2f;
            float baseline = textTop + fh * 0.75f;
            engine.ui.GLUIRenderer.drawText(entry.name, x + 10, baseline, 1f, 1f, 1f, 1f);
            engine.ui.GLUIRenderer.drawText(entry.address, x + 10, baseline + fh + 2f, 0.6f, 0.6f, 0.6f, 1f);

            // Right side: players + ping bars
            String players;
            float pr, pg, pb;
            if (ping.state == ServerPinger.STATE_OK) {
                players = ping.online + "/" + ping.max;
                pr = 0.6f; pg = 0.6f; pb = 0.6f;
            } else if (ping.state == ServerPinger.STATE_PENDING) {
                players = "...";
                pr = 0.5f; pg = 0.5f; pb = 0.5f;
            } else {
                players = ""; // unreachable shows red bars only
                pr = 0.8f; pg = 0.25f; pb = 0.25f;
            }
            float pw = engine.ui.GLUIRenderer.getTextWidth(players);
            engine.ui.GLUIRenderer.drawText(players, x + width - 60 - pw, baseline + fh + 2f, pr, pg, pb, 1f);

            drawPingBars(x + width - 14, y + height / 2f - 7f, ping);
        }

        /** 5 stacked bars (Minecraft style), colored by latency. */
        private void drawPingBars(float bx, float by, ServerPinger.Result p) {
            int bars;
            float br, bg_, bb;
            if (p.state == ServerPinger.STATE_PENDING)      { bars = 0; br = 0.5f; bg_ = 0.5f; bb = 0.5f; }
            else if (p.state == ServerPinger.STATE_UNREACHABLE) { bars = 5; br = 0.8f; bg_ = 0.25f; bb = 0.25f; }
            else if (p.pingMs < 60)  { bars = 5; br = 0.2f; bg_ = 0.8f; bb = 0.2f; }
            else if (p.pingMs < 120) { bars = 4; br = 0.2f; bg_ = 0.8f; bb = 0.2f; }
            else if (p.pingMs < 220) { bars = 3; br = 0.8f; bg_ = 0.8f; bb = 0.2f; }
            else if (p.pingMs < 400) { bars = 2; br = 0.9f; bg_ = 0.5f; bb = 0.2f; }
            else                     { bars = 1; br = 0.85f; bg_ = 0.25f; bb = 0.25f; }

            for (int i = 0; i < 5; i++) {
                float bh = 3 + i * 2.5f;
                float barX = bx - (4 - i) * 5;
                if (i < bars) engine.ui.GLUIRenderer.drawRect(barX, by + 12 - bh, 4, bh, br, bg_, bb, 1f);
                else engine.ui.GLUIRenderer.drawRect(barX, by + 12 - bh, 4, bh, 0.15f, 0.15f, 0.15f, 1f);
            }
        }

        @Override
        public void onClick(float mouseX, float mouseY, int button) {
            if (button == 0) rowClicked(index);
        }
    }
}
