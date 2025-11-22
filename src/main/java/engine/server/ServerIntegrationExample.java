package engine.server;

import engine.net.Connection;
import engine.net.packet.*;
import engine.plugin.PluginLoader;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Example demonstrating server-side integration of networking and plugins.
 * Shows how to register packets, handlers, and load plugins without replacing existing code.
 * 
 * This is a standalone example and doesn't interfere with the existing VoxelEngine.
 */
public class ServerIntegrationExample {
    
    private final int port;
    private final PacketRegistry registry;
    private final WorldManager worldManager;
    private final PluginLoader pluginLoader;
    private ServerSocket serverSocket;
    private volatile boolean running;
    
    public ServerIntegrationExample(int port) {
        this.port = port;
        this.registry = new PacketRegistry();
        this.worldManager = new WorldManager();
        this.pluginLoader = new PluginLoader(new File("plugins"));
        this.running = false;
    }
    
    /**
     * Initialize the server by registering packets and loading plugins.
     */
    public void initialize() {
        // Register packet types
        registry.register(ExampleHelloPacket.PACKET_ID, ExampleHelloPacket::new);
        registry.register(PlayerMoveRequestPacket.PACKET_ID, PlayerMoveRequestPacket::new);
        registry.register(PlayerMoveResultPacket.PACKET_ID, PlayerMoveResultPacket::new);
        
        System.out.println("Registered packets: Hello(1), PlayerMoveRequest(10), PlayerMoveResult(11)");
        
        // Load plugins
        int pluginsLoaded = pluginLoader.loadPlugins();
        System.out.println("Loaded " + pluginsLoaded + " plugin(s)");
    }
    
    /**
     * Start the server and begin accepting connections.
     */
    public void start() throws IOException {
        initialize();
        
        serverSocket = new ServerSocket(port);
        running = true;
        
        System.out.println("Server listening on port " + port);
        
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());
                
                // Create connection for this client
                Connection connection = new Connection(clientSocket, registry);
                
                // Generate a unique player ID (in production, use proper authentication)
                String playerId = "player_" + System.currentTimeMillis();
                
                // Create handler for this player
                PlayerMoveHandler moveHandler = new PlayerMoveHandler(worldManager, playerId);
                
                // Register packet handlers
                connection.registerHandler(ExampleHelloPacket.class, packet -> {
                    System.out.println("Received hello from " + playerId + ": " + packet.getMessage());
                });
                
                connection.registerHandler(PlayerMoveRequestPacket.class, packet -> {
                    System.out.println("Player " + playerId + " requests move to (" 
                            + packet.getX() + ", " + packet.getY() + ", " + packet.getZ() + ")");
                    moveHandler.handleMoveRequest(connection, packet);
                });
                
                // Start receiving packets
                connection.startReceiving();
                
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error accepting client: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Stop the server and unload plugins.
     */
    public void stop() {
        running = false;
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server socket: " + e.getMessage());
        }
        
        pluginLoader.unloadPlugins();
        System.out.println("Server stopped");
    }
    
    /**
     * Main method to run the example server.
     * This does not interfere with the existing VoxelEngine demo.
     */
    public static void main(String[] args) {
        int port = 25565; // Default Minecraft port for familiarity
        
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0]);
                return;
            }
        }
        
        ServerIntegrationExample server = new ServerIntegrationExample(port);
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        
        try {
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
