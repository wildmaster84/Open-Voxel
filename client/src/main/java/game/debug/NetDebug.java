package game.debug;

import game.SessionInfo;
import game.net.NetClient;
import game.net.RemoteWorld;
import server.GameServer;

/**
 * Headless protocol harness. Two modes:
 *  - default: starts an offline in-process GameServer, connects, verifies
 *    handshake, chunk streaming, and block-edit echo.
 *  - --server HOST --port N: connect to an external server (e.g. a dedicated
 *    server with plugins) and run the same checks against it.
 * Optional: --online --server-id X --auth-server http://host:8370
 *           --username U --uuid I --token T   (forwarded to SessionInfo)
 */
public class NetDebug {
    public static void main(String[] args) throws Exception {
        boolean online = false;
        String remoteHost = null;
        int remotePort = 0;
        String serverId = "test";
        String clientServerId = null; // default: same as serverId
        String authServer = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--online": online = true; break;
                case "--server-id": serverId = args[++i]; if ("auto".equals(serverId)) serverId = null; break;
                case "--client-server-id": clientServerId = args[++i]; break;
                case "--auth-server": authServer = args[++i]; break;
                case "--server": remoteHost = args[++i]; break;
                case "--port": remotePort = Integer.parseInt(args[++i]); break;
            }
        }
        if (clientServerId == null) clientServerId = serverId;
        System.out.println("mode: " + (online ? "online (server-id=" + serverId + ")" : "offline"));

        GameServer server = null;
        int port;
        if (remoteHost != null) {
            port = remotePort;
            System.out.println("connecting to external server " + remoteHost + ":" + port);
        } else {
            server = new GameServer(2025L, online, authServer, serverId);
            server.start(0);
            port = server.actualPort();
            System.out.println("server up on :" + port);
        }

        RemoteWorld world = new RemoteWorld();
        final boolean[] welcomed = {false};
        final float[] spawn = new float[3];
        NetClient client = new NetClient(world, new NetClient.Listener() {
            @Override public void onWelcome(float x, float y, float z) {
                welcomed[0] = true; spawn[0] = x; spawn[1] = y; spawn[2] = z;
            }
            @Override public void onKicked(String reason) { System.out.println("KICKED: " + reason); }
            @Override public void onDisconnected() { System.out.println("disconnected"); }
        });

        client.connect(remoteHost != null ? remoteHost : "127.0.0.1", port, SessionInfo.fromArgs(args), clientServerId);
        System.out.println("welcomed: " + welcomed[0] + " spawn=" + spawn[0] + "," + spawn[1] + "," + spawn[2]);

        // Wait for chunk streaming (view distance 8 => 17x17 = 289 chunks).
        long deadline = System.currentTimeMillis() + 15000;
        while (world.getChunks().size() < 289 && System.currentTimeMillis() < deadline) Thread.sleep(50);
        System.out.println("chunks streamed: " + world.getChunks().size() + " (expected 289)");

        // Verify terrain actually arrived (not all air).
        int nonAir = 0;
        engine.world.Chunk chunk = world.getChunkIfLoaded(0, 0);
        for (int y = 0; y < engine.world.Chunk.HEIGHT && nonAir == 0; y++)
            if (chunk.getState(8, y, 8) != 0) nonAir = y;
        System.out.println("chunk(0,0) first solid at column(8,8): y=" + nonAir + (nonAir > 0 ? "  OK" : "  FAIL"));

        // Block edit echo: send SET_BLOCK, expect authoritative BLOCK_UPDATE back.
        int before = world.getChunkIfLoaded(0, 0).getState(5, 100, 5);
        client.sendSetBlock(5, 100, 5, 7);
        int after = before;
        deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            after = world.getChunkIfLoaded(0, 0).getState(5, 100, 5);
            if (after == 7) break;
            Thread.sleep(20);
        }
        System.out.println("setblock echo: " + before + " -> " + after + (after == 7 ? "  OK" : "  FAIL (denied or no echo)"));

        client.disconnect();
        Thread.sleep(200);
        if (server != null) server.stop();
        System.out.println("done");
        System.exit(0);
    }
}