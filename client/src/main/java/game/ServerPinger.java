package game;

import engine.net.Protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Server-list probe: opens a TCP connection, sends PING as the first frame,
 * reads PONG (online/max), reports RTT + player count. Runs off-thread;
 * the screen polls the result fields on the render thread.
 */
public final class ServerPinger {
    public static final int STATE_PENDING = 0;
    public static final int STATE_OK = 1;
    public static final int STATE_UNREACHABLE = 2;

    /** Live result for one server entry. */
    public static final class Result {
        public volatile int state = STATE_PENDING;
        public volatile long pingMs = -1;
        public volatile int online = -1;
        public volatile int max = -1;
        /** Bumped on every completed probe so the screen knows to redraw. */
        public volatile int version;
    }

    public interface Listener { void onResult(Result r); }

    private static final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public static void addListener(Listener l) { listeners.add(l); }
    public static void removeListener(Listener l) { listeners.remove(l); }

    /** Probes host:port asynchronously. Returns the live Result immediately. */
    public static Result ping(String address) {
        Result r = new Result();
        Thread t = new Thread(() -> probe(address, r), "server-ping");
        t.setDaemon(true);
        t.start();
        return r;
    }

    private static void probe(String address, Result r) {
        String host = address;
        int port = server.GameServer.DEFAULT_PORT;
        int colon = address.lastIndexOf(':');
        if (colon > 0) {
            host = address.substring(0, colon);
            try { port = Integer.parseInt(address.substring(colon + 1)); }
            catch (NumberFormatException ignored) {}
        }
        try (Socket sock = new Socket()) {
            long start = System.nanoTime();
            sock.connect(new InetSocketAddress(host, port), 2000);
            long connectMs = (System.nanoTime() - start) / 1_000_000;

            DataOutputStream out = new DataOutputStream(sock.getOutputStream());
            DataInputStream in = new DataInputStream(sock.getInputStream());
            Protocol.writeFrame(out, new Protocol.PayloadWriter().id(Protocol.PING).toArray());

            byte[] frame = Protocol.readFrame(in);
            long rtt = (System.nanoTime() - start) / 1_000_000;
            if (frame == null || (frame[0] & 0xff) != Protocol.PONG) {
                r.state = STATE_UNREACHABLE;
            } else {
                Protocol.PayloadReader pr = new Protocol.PayloadReader(frame, 1);
                r.online = (int) pr.u32();
                r.max = (int) pr.u32();
                r.pingMs = connectMs > 0 ? connectMs : rtt;
                r.state = STATE_OK;
            }
        } catch (Exception e) {
            r.state = STATE_UNREACHABLE;
        }
        r.version++;
        for (Listener l : listeners) l.onResult(r);
    }

    private ServerPinger() {}
}
