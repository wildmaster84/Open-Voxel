package com.openvoxel.server;

import com.openvoxel.net.Connection;
import com.openvoxel.net.packet.ExampleHelloPacket;
import com.openvoxel.net.packet.Packet;
import com.openvoxel.net.packet.PacketRegistry;
import com.openvoxel.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Main entry point for the dedicated server.
 * Listens for client connections, loads plugins, and manages the game server.
 */
public class ServerMain {
    
    private static final int DEFAULT_PORT = 25565;
    private final PacketRegistry packetRegistry;
    private final List<Connection> connections = new ArrayList<>();
    private final List<Plugin> plugins = new ArrayList<>();
    private volatile boolean running = false;
    
    public ServerMain() {
        this.packetRegistry = new PacketRegistry();
        registerPackets();
    }
    
    /**
     * Register all packet types with their IDs.
     */
    private void registerPackets() {
        // Register ExampleHelloPacket with ID 1
        packetRegistry.register(1, ExampleHelloPacket.class, ExampleHelloPacket::new);
    }
    
    /**
     * Start the server.
     */
    public void start() {
        System.out.println("Starting Open-Voxel Server...");
        
        // Load plugins
        loadPlugins();
        
        // Enable all plugins
        enablePlugins();
        
        // Start listening for connections
        running = true;
        try (ServerSocket serverSocket = new ServerSocket(DEFAULT_PORT)) {
            System.out.println("Server listening on port " + DEFAULT_PORT);
            
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Client connected: " + clientSocket.getInetAddress());
                    
                    Connection connection = new Connection(clientSocket, packetRegistry);
                    connections.add(connection);
                    
                    // Register packet handlers
                    connection.registerHandler(ExampleHelloPacket.class, this::handleHelloPacket);
                    
                    // Start reading packets
                    connection.startReader();
                    
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Error accepting client connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }
    
    /**
     * Handle an incoming ExampleHelloPacket.
     */
    private void handleHelloPacket(ExampleHelloPacket packet) {
        System.out.println("Received hello message: " + packet.getMessage());
        
        // Send a response back
        ExampleHelloPacket response = new ExampleHelloPacket("Hello from server!");
        broadcast(response);
    }
    
    /**
     * Load plugins from the plugins directory.
     */
    private void loadPlugins() {
        File pluginsDir = new File("plugins");
        PluginLoader loader = new PluginLoader();
        plugins.addAll(loader.loadPlugins(pluginsDir));
    }
    
    /**
     * Enable all loaded plugins.
     */
    private void enablePlugins() {
        ServerPluginContext context = new ServerPluginContext();
        for (Plugin plugin : plugins) {
            try {
                plugin.onEnable(context);
                System.out.println("Enabled plugin: " + plugin.getName());
            } catch (Exception e) {
                System.err.println("Failed to enable plugin " + plugin.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Broadcast a packet to all connected clients.
     */
    public void broadcast(Packet packet) {
        synchronized (connections) {
            for (Connection connection : connections) {
                try {
                    connection.sendPacket(packet);
                } catch (IOException e) {
                    System.err.println("Error broadcasting packet: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Shut down the server and disable all plugins.
     */
    public void shutdown() {
        if (!running) {
            return;
        }
        
        System.out.println("Shutting down server...");
        running = false;
        
        // Close all connections
        synchronized (connections) {
            for (Connection connection : connections) {
                connection.close();
            }
            connections.clear();
        }
        
        // Disable all plugins
        for (Plugin plugin : plugins) {
            try {
                plugin.onDisable();
                System.out.println("Disabled plugin: " + plugin.getName());
            } catch (Exception e) {
                System.err.println("Error disabling plugin " + plugin.getName() + ": " + e.getMessage());
            }
        }
        
        System.out.println("Server stopped.");
    }
    
    /**
     * Plugin context implementation for the server.
     */
    private class ServerPluginContext implements Plugin.PluginContext {
        
        @Override
        public PacketRegistry getPacketRegistry() {
            return packetRegistry;
        }
        
        @Override
        public void broadcast(Packet packet) {
            ServerMain.this.broadcast(packet);
        }
        
        @Override
        public void sendTo(Connection connection, Packet packet) {
            try {
                connection.sendPacket(packet);
            } catch (IOException e) {
                System.err.println("Error sending packet to connection: " + e.getMessage());
            }
        }
    }
    
    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        ServerMain server = new ServerMain();
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));
        
        // Start server
        server.start();
    }
}
