package game;

import engine.VoxelEngine;
import engine.ui.UIManager;
import game.net.NetClient;
import game.net.RemoteWorld;
import org.joml.Vector3f;
import server.GameServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Open-Voxel game client. Boots into the main menu.
 *
 * Singleplayer runs an internal (integrated) server on loopback and joins it
 * through the exact same netcode as multiplayer — one world path, no special
 * casing. Multiplayer joins a remote server from the server list.
 */
public class ClientMain {
    private static final long SEED = 2025L;

    private static SessionInfo session;
    private static GameServer integratedServer; // singleplayer only
    private static NetClient net;
    /** Set by net callbacks (reader thread), consumed by the game tick (render thread). */
    private static final java.util.concurrent.atomic.AtomicReference<String> pendingLeave =
            new java.util.concurrent.atomic.AtomicReference<>();

    public static void main(String[] args) {
        session = SessionInfo.fromArgs(args);

        VoxelEngine engine = new VoxelEngine(session.vsync, session.renderDistance);
        String version = ClientMain.class.getPackage().getImplementationVersion();
        engine.setVersion(version == null ? "0.0.0-Debug" : version);
        engine.setExitToMenuHandler(ClientMain::backToMenu);

        // Menu screens need the window to exist, so open the menu from inside
        // the first frame: UIManager window is set during engine.start().
        new Thread(() -> {
            while (UIManager.get().getWindow() == 0) {
                try { Thread.sleep(16); } catch (InterruptedException ignored) {}
            }
            UIManager.get().openGUI(new MainMenuScreen(1280, 720));
        }, "menu-open").start();

        engine.start();
    }

    public static SessionInfo getSession() { return session; }
    public static NetClient getNet() { return net; }

    /** Singleplayer: start the integrated loopback server and join it. */
    public static void startSingleplayer() {
        disconnect();
        try {
            integratedServer = new GameServer(SEED, false, null, "singleplayer");
            integratedServer.start(0); // ephemeral loopback port
            join("127.0.0.1", integratedServer.actualPort(), "singleplayer");
        } catch (IOException e) {
            System.out.println("[singleplayer] failed to start: " + e.getMessage());
            integratedServer = null;
        }
    }

    /**
     * Multiplayer: register the join-intent with the auth server (hasJoined
     * flow), then connect. serverId must match the server's configured id.
     */
    public static void onJoinServer(ServerList.Entry entry) {
        disconnect();
        String[] hp = splitHostPort(entry.address);
        String serverId = hp[0] + ":" + hp[1];
        if (session.isLoggedIn()) {
            String err = registerJoinIntent(serverId);
            if (err != null) {
                System.out.println("[multiplayer] /session/join failed: " + err);
            }
        }
        join(hp[0], Integer.parseInt(hp[1]), serverId);
    }

    private static void join(String host, int port, String serverId) {
        pendingLeave.set(null);
        RemoteWorld world = new RemoteWorld();
        net = new NetClient(world, new NetClient.Listener() {
            @Override
            public void onWelcome(float x, float y, float z) {
                VoxelEngine engine = VoxelEngine.getEngine();
                UIManager.get().closeAllGUIs();
                engine.enterWorld(world);
                engine.getCamera().setPosition(new Vector3f(x, y, z));

                // Block edits go to the server; it echoes them back authoritatively.
                NetClient conn = net;
                engine.setBlockEditSink((bx, by, bz, block) -> {
                    if (conn == null || !conn.isRunning()) return false;
                    conn.sendSetBlock(bx, by, bz, block.getState());
                    return true;
                });
                // Stream position at game-tick rate so the server can send new chunks.
                engine.setWorldTickHook(() -> {
                    // A drop/kick lands here on the render thread, where world
                    // teardown (GL calls) is safe.
                    String leave = pendingLeave.getAndSet(null);
                    if (leave != null) {
                        backToMenuWithError("Disconnected", leave);
                        return;
                    }
                    if (conn == null || !conn.isRunning()) return;
                    Vector3f pos = engine.getCamera().getPosition();
                    conn.sendPlayerPos(pos.x, pos.y, pos.z);
                });

                System.out.println("[net] joined " + host + ":" + port);
            }
            @Override
            public void onKicked(String reason) {
                System.out.println("[net] kicked: " + reason);
                pendingLeave.compareAndSet(null, "Kicked: " + reason);
            }
            @Override
            public void onDisconnected() {
                System.out.println("[net] disconnected");
                // User-initiated leaves null out net first; only report real drops.
                if (net != null) pendingLeave.compareAndSet(null, "Connection lost");
            }
        });
        try {
            net.connect(host, port, session, serverId);
        } catch (IOException e) {
            System.out.println("[net] connect failed: " + e.getMessage());
            net = null;
            UIManager.get().closeAllGUIs();
            UIManager.get().openGUI(new ErrorScreen(1280, 720, "Couldn't connect", e.getMessage()));
        }
    }

    /** Back to the menu; drops the connection (and integrated server, if any). */
    public static void disconnect() {
        // Null BEFORE closing so the reader thread's onDisconnected sees this
        // as user-initiated and doesn't queue a "Connection lost".
        NetClient c = net;
        net = null;
        if (c != null) c.disconnect();
        if (integratedServer != null) { integratedServer.stop(); integratedServer = null; }
        VoxelEngine engine = VoxelEngine.getEngine();
        if (engine != null && engine.isWorldActive()) engine.exitWorld();
    }

    public static void backToMenu() {
        disconnect();
        UIManager.get().closeAllGUIs();
        UIManager.get().openGUI(new MainMenuScreen(1280, 720));
    }

    /** Drop everything and show the error dialog (kicks, drops, failures). */
    public static void backToMenuWithError(String title, String message) {
        disconnect();
        UIManager.get().closeAllGUIs();
        UIManager.get().openGUI(new ErrorScreen(1280, 720, title, message));
    }

    private static String[] splitHostPort(String address) {
        int colon = address.lastIndexOf(':');
        if (colon > 0) return new String[]{address.substring(0, colon), address.substring(colon + 1)};
        return new String[]{address, String.valueOf(GameServer.DEFAULT_PORT)};
    }

    /** POST /session/join { serverId } with the launcher token. Returns error or null. */
    private static String registerJoinIntent(String serverId) {
        try {
            HttpURLConnection http = (HttpURLConnection)
                    new URL(session.authServer + "/session/join").openConnection();
            http.setRequestMethod("POST");
            http.setRequestProperty("Authorization", "Bearer " + session.token);
            http.setRequestProperty("Content-Type", "application/json");
            http.setDoOutput(true);
            byte[] body = ("{\"serverId\":\"" + serverId + "\"}").getBytes("UTF-8");
            try (OutputStream os = http.getOutputStream()) { os.write(body); }
            int code = http.getResponseCode();
            return code == 200 ? null : "HTTP " + code;
        } catch (Exception e) {
            return e.getMessage();
        }
    }
}
