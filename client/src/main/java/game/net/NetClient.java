package game.net;

import engine.net.Protocol;
import engine.world.Chunk;
import game.SessionInfo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.zip.Inflater;

/**
 * Client end of the Open-Voxel protocol. Connects, authenticates (HELLO),
 * receives chunks into a RemoteWorld, forwards block edits and position.
 *
 * Callbacks (onWelcome / onKicked / onDisconnected) fire on the reader thread;
 * world mutation must be handed to the render thread via the engine loop.
 */
public class NetClient {
    public interface Listener {
        void onWelcome(float spawnX, float spawnY, float spawnZ);
        void onKicked(String reason);
        void onDisconnected();
    }

    private final RemoteWorld world;
    private final Listener listener;
    private Socket socket;
    private DataOutputStream out;
    private volatile boolean running = false;

    public NetClient(RemoteWorld world, Listener listener) {
        this.world = world;
        this.listener = listener;
    }

    /** Connects and performs the HELLO handshake. Blocks until HELLO_ACK or failure. */
    public void connect(String host, int port, SessionInfo session, String serverId) throws IOException {
        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        DataInputStream in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());

        Protocol.writeFrame(out, new Protocol.PayloadWriter().id(Protocol.HELLO)
                .i32(Protocol.VERSION)
                .utf(session.username != null ? session.username : "Player")
                .utf(session.uuid != null ? session.uuid : "")
                .utf(session.token != null ? session.token : "")
                .utf(serverId)
                .toArray());

        byte[] resp = Protocol.readFrame(in);
        if (resp == null) throw new IOException("server closed during handshake");
        int id = resp[0] & 0xff;
        if (id == Protocol.KICK) {
            String reason = new Protocol.PayloadReader(resp, 1).utf();
            throw new IOException("kicked: " + reason);
        }
        if (id != Protocol.HELLO_ACK) throw new IOException("unexpected handshake response " + id);

        Protocol.PayloadReader r = new Protocol.PayloadReader(resp, 1);
        int ok = r.u8(); r.utf();
        float sx = r.f32(), sy = r.f32(), sz = r.f32();
        if (ok != 1) throw new IOException("login rejected");

        running = true;
        Thread reader = new Thread(() -> readLoop(in), "net-reader");
        reader.setDaemon(true);
        reader.start();

        listener.onWelcome(sx, sy, sz);
    }

    private void readLoop(DataInputStream in) {
        try {
            while (running) {
                byte[] frame = Protocol.readFrame(in);
                if (frame == null) break;
                Protocol.PayloadReader r = new Protocol.PayloadReader(frame, 1);
                switch (frame[0] & 0xff) {
                    case Protocol.CHUNK: {
                        int cx = r.i32(), cz = r.i32();
                        int rawLen = (int) r.u32(), compLen = (int) r.u32();
                        byte[] comp = new byte[compLen];
                        r.bytes(comp);
                        int[] states = inflateStates(comp, rawLen);
                        world.applyChunkData(cx, cz, states);
                        world.getChunk(cx, cz).bumpBlockVersion(); // remesh via version guard
                        // Schedule a rebuild if the renderer is already up (it is,
                        // post-HELLO_ACK; guard for packets racing enterWorld).
                        engine.VoxelEngine eng = engine.VoxelEngine.getEngine();
                        if (eng != null && eng.getRenderer() != null) {
                            eng.getRenderer().invalidateChunk(cx, cz, false);
                        }
                        break;
                    }
                    case Protocol.BLOCK_UPDATE: {
                        int x = r.i32(), y = r.i32(), z = r.i32(), state = r.i32();
                        world.applyBlockUpdate(x, y, z, state);
                        engine.VoxelEngine eng = engine.VoxelEngine.getEngine();
                        if (eng != null && eng.getRenderer() != null) {
                            eng.getRenderer().invalidateBlock(x, y, z);
                        }
                        break;
                    }
                    case Protocol.KICK: {
                        String reason = r.utf();
                        running = false;
                        listener.onKicked(reason);
                        return;
                    }
                    default: break;
                }
            }
        } catch (IOException ignored) {
        } finally {
            running = false;
            listener.onDisconnected();
        }
    }

    private static int[] inflateStates(byte[] comp, int rawLen) throws IOException {
        byte[] raw = new byte[rawLen];
        try {
            Inflater inf = new Inflater();
            inf.setInput(comp);
            int got = inf.inflate(raw);
            inf.end();
            if (got != rawLen) throw new IOException("short chunk inflate: " + got + "/" + rawLen);
        } catch (java.util.zip.DataFormatException e) {
            throw new IOException("bad chunk data: " + e.getMessage());
        }
        int[] states = new int[Chunk.SIZE * Chunk.SIZE * Chunk.HEIGHT];
        for (int i = 0, p = 0; i < states.length; i++, p += 4) {
            states[i] = ((raw[p] & 0xff) << 24) | ((raw[p + 1] & 0xff) << 16)
                    | ((raw[p + 2] & 0xff) << 8) | (raw[p + 3] & 0xff);
        }
        return states;
    }

    public void sendSetBlock(int x, int y, int z, int state) {
        send(new Protocol.PayloadWriter().id(Protocol.SET_BLOCK)
                .i32(x).i32(y).i32(z).i32(state).toArray());
    }

    public void sendPlayerPos(float x, float y, float z) {
        send(new Protocol.PayloadWriter().id(Protocol.PLAYER_POS)
                .f32(x).f32(y).f32(z).toArray());
    }

    private void send(byte[] frame) {
        if (!running) return;
        try { Protocol.writeFrame(out, frame); } catch (IOException ignored) {}
    }

    public void disconnect() {
        running = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    public boolean isRunning() { return running; }
}
