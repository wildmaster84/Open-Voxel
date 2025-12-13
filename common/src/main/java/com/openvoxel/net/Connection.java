package com.openvoxel.net;

import com.openvoxel.net.packet.Packet;
import com.openvoxel.net.packet.PacketRegistry;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Represents a network connection that can send and receive packets.
 * Uses length-prefixed framing for packet transmission.
 */
public class Connection {
    
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final PacketRegistry registry;
    private final Map<Class<? extends Packet>, Consumer<Packet>> handlers = new HashMap<>();
    private volatile boolean running = false;
    private Thread readerThread;
    
    public Connection(Socket socket, PacketRegistry registry) throws IOException {
        this.socket = socket;
        this.registry = registry;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
    }
    
    /**
     * Register a handler for a specific packet type.
     * @param packetClass the packet class to handle
     * @param handler the handler to invoke when a packet of this type is received
     */
    @SuppressWarnings("unchecked")
    public <T extends Packet> void registerHandler(Class<T> packetClass, Consumer<T> handler) {
        // This cast is safe because we only invoke handlers for the exact packet type
        // they are registered for (see readLoop method where handlers are invoked)
        handlers.put(packetClass, (Consumer<Packet>) handler);
    }
    
    /**
     * Send a packet over this connection.
     * @param packet the packet to send
     * @throws IOException if an I/O error occurs
     */
    public synchronized void sendPacket(Packet packet) throws IOException {
        Integer id = registry.getId(packet.getClass());
        if (id == null) {
            throw new IllegalArgumentException("Packet type not registered: " + packet.getClass().getName());
        }
        
        // Write packet ID
        out.writeInt(id);
        // Write packet data
        packet.write(out);
        out.flush();
    }
    
    /**
     * Start the reader thread that processes incoming packets.
     */
    public void startReader() {
        if (running) {
            return;
        }
        running = true;
        readerThread = new Thread(this::readLoop, "Connection-Reader");
        readerThread.start();
    }
    
    /**
     * Main loop that reads and processes incoming packets.
     */
    private void readLoop() {
        try {
            while (running && !socket.isClosed()) {
                // Read packet ID
                int packetId = in.readInt();
                
                // Create packet instance
                Packet packet = registry.createPacket(packetId);
                if (packet == null) {
                    System.err.println("Unknown packet ID: " + packetId);
                    close();
                    break;
                }
                
                // Read packet data
                packet.read(in);
                
                // Invoke handler if registered
                Consumer<Packet> handler = handlers.get(packet.getClass());
                if (handler != null) {
                    handler.accept(packet);
                }
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Connection error: " + e.getMessage());
            }
        } finally {
            close();
        }
    }
    
    /**
     * Close this connection and stop the reader thread.
     */
    public void close() {
        if (!running) {
            return;
        }
        running = false;
        try {
            socket.close();
        } catch (IOException e) {
            // Ignore
        }
    }
    
    public boolean isConnected() {
        return running && !socket.isClosed();
    }
}
