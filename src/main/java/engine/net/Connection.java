package engine.net;

import engine.net.packet.Packet;
import engine.net.packet.PacketRegistry;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Represents a network connection that can send and receive packets.
 * Uses length-prefixed framing for reliable packet boundaries.
 */
public class Connection {
    
    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final PacketRegistry registry;
    private final Map<Class<? extends Packet>, Consumer<Packet>> handlers;
    private volatile boolean running;
    private Thread receiveThread;
    
    public Connection(Socket socket, PacketRegistry registry) throws IOException {
        this.socket = socket;
        this.input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        this.registry = registry;
        this.handlers = new HashMap<>();
        this.running = false;
    }
    
    /**
     * Register a handler for a specific packet type.
     * @param packetClass packet class
     * @param handler callback to handle received packets
     */
    public <T extends Packet> void registerHandler(Class<T> packetClass, Consumer<T> handler) {
        @SuppressWarnings("unchecked")
        Consumer<Packet> wrappedHandler = (Consumer<Packet>) handler;
        handlers.put(packetClass, wrappedHandler);
    }
    
    /**
     * Send a packet over the connection.
     * Uses length-prefixed framing: [length (4 bytes)][packet ID (4 bytes)][data]
     * @param packet packet to send
     * @throws IOException if send fails
     */
    public synchronized void sendPacket(Packet packet) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream packetOut = new DataOutputStream(baos);
        
        // Write packet ID and data to buffer
        packetOut.writeInt(packet.getPacketId());
        packet.write(packetOut);
        packetOut.flush();
        
        byte[] data = baos.toByteArray();
        
        // Write length prefix followed by data
        output.writeInt(data.length);
        output.write(data);
        output.flush();
    }
    
    /**
     * Start receiving packets in a background thread.
     */
    public void startReceiving() {
        if (running) {
            return;
        }
        running = true;
        receiveThread = new Thread(this::receiveLoop, "Connection-Receiver");
        receiveThread.setDaemon(true);
        receiveThread.start();
    }
    
    /**
     * Main receive loop that reads packets from the input stream.
     */
    private void receiveLoop() {
        try {
            while (running && !socket.isClosed()) {
                // Read length prefix
                int length = input.readInt();
                if (length <= 0 || length > 1024 * 1024) { // 1MB max
                    throw new IOException("Invalid packet length: " + length);
                }
                
                // Read packet data
                byte[] data = new byte[length];
                input.readFully(data);
                
                // Parse packet
                ByteArrayInputStream bais = new ByteArrayInputStream(data);
                DataInputStream packetIn = new DataInputStream(bais);
                
                int packetId = packetIn.readInt();
                Packet packet = registry.createPacket(packetId);
                packet.read(packetIn);
                
                // Dispatch to handler
                Consumer<Packet> handler = handlers.get(packet.getClass());
                if (handler != null) {
                    handler.accept(packet);
                }
            }
        } catch (EOFException e) {
            // Connection closed normally
        } catch (IOException e) {
            if (running) {
                e.printStackTrace();
            }
        } finally {
            close();
        }
    }
    
    /**
     * Close the connection and stop receiving.
     */
    public void close() {
        running = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public boolean isConnected() {
        return socket != null && !socket.isClosed() && running;
    }
}
