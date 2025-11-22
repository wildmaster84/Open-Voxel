package engine.server;

import engine.net.Connection;
import engine.net.packet.ExampleHelloPacket;
import engine.net.packet.Packet;
import engine.net.packet.PacketRegistry;
import engine.net.packet.PlayerMoveRequestPacket;
import engine.net.packet.PlayerMoveResultPacket;
import engine.plugin.PluginLoader;
import engine.server.authoritative.WorldManager;
import engine.server.handlers.PlayerMoveHandler;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Example server integration demonstrating packet handling and plugin loading.
 * This shows how to set up a basic authoritative server with networking.
 */
public class ServerIntegrationExample {
	private static final int PORT = 25565;
	private WorldManager worldManager;
	private PlayerMoveHandler moveHandler;
	private PluginLoader pluginLoader;
	private boolean running = false;
	
	public ServerIntegrationExample() {
		this.worldManager = new WorldManager();
		this.moveHandler = new PlayerMoveHandler(worldManager);
		this.pluginLoader = new PluginLoader();
	}
	
	/**
	 * Initialize the server: register packets and load plugins.
	 */
	public void initialize() {
		System.out.println("Initializing server...");
		
		// Register all packet types
		PacketRegistry.register(ExampleHelloPacket.PACKET_ID, ExampleHelloPacket::new);
		PacketRegistry.register(PlayerMoveRequestPacket.PACKET_ID, PlayerMoveRequestPacket::new);
		PacketRegistry.register(PlayerMoveResultPacket.PACKET_ID, PlayerMoveResultPacket::new);
		
		System.out.println("Registered " + 3 + " packet types");
		
		// Load plugins
		int pluginCount = pluginLoader.loadPlugins();
		System.out.println("Loaded " + pluginCount + " plugin(s)");
	}
	
	/**
	 * Start the server and listen for connections.
	 */
	public void start() {
		running = true;
		System.out.println("Starting server on port " + PORT + "...");
		
		try (ServerSocket serverSocket = new ServerSocket(PORT)) {
			System.out.println("Server started! Listening for connections...");
			
			while (running) {
				try {
					Socket clientSocket = serverSocket.accept();
					System.out.println("New connection from " + clientSocket.getInetAddress().getHostAddress());
					
					// Handle each client in a separate thread
					new Thread(() -> handleClient(clientSocket)).start();
				} catch (IOException e) {
					if (running) {
						System.err.println("Error accepting connection: " + e.getMessage());
					}
				}
			}
		} catch (IOException e) {
			System.err.println("Failed to start server: " + e.getMessage());
			e.printStackTrace();
		}
		
		shutdown();
	}
	
	/**
	 * Handle a client connection.
	 */
	private void handleClient(Socket socket) {
		// TODO: Use secure session-based authentication instead of IP address for player ID
		// IP addresses can be spoofed and are not suitable for production use
		String clientId = socket.getInetAddress().getHostAddress();
		Connection connection = null;
		
		try {
			connection = new Connection(socket);
			System.out.println("Client " + clientId + " connected");
			
			// Handle packets from this client
			while (connection.isConnected()) {
				Packet packet = connection.receivePacket();
				if (packet == null) {
					break;
				}
				
				handlePacket(connection, packet, clientId);
			}
		} catch (IOException e) {
			System.err.println("Error with client " + clientId + ": " + e.getMessage());
		} finally {
			if (connection != null) {
				connection.close();
			}
			worldManager.removePlayer(clientId);
			System.out.println("Client " + clientId + " disconnected");
		}
	}
	
	/**
	 * Handle a received packet.
	 */
	private void handlePacket(Connection connection, Packet packet, String playerId) {
		if (packet instanceof ExampleHelloPacket) {
			ExampleHelloPacket hello = (ExampleHelloPacket) packet;
			System.out.println("Received hello from " + playerId + ": " + hello.getMessage());
			
			// Send a response
			try {
				ExampleHelloPacket response = new ExampleHelloPacket("Hello from server!");
				connection.sendPacket(response);
			} catch (IOException e) {
				System.err.println("Failed to send hello response: " + e.getMessage());
			}
		} else if (packet instanceof PlayerMoveRequestPacket) {
			PlayerMoveRequestPacket moveRequest = (PlayerMoveRequestPacket) packet;
			moveHandler.handleMoveRequest(connection, moveRequest, playerId);
		} else {
			System.out.println("Received unknown packet type: " + packet.getClass().getName());
		}
	}
	
	/**
	 * Stop the server.
	 */
	public void stop() {
		System.out.println("Stopping server...");
		running = false;
	}
	
	/**
	 * Shutdown and cleanup.
	 */
	private void shutdown() {
		System.out.println("Shutting down server...");
		pluginLoader.unloadAll();
		System.out.println("Server stopped");
	}
	
	/**
	 * Main entry point for running the server standalone.
	 */
	public static void main(String[] args) {
		ServerIntegrationExample server = new ServerIntegrationExample();
		server.initialize();
		
		// Add shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
		
		server.start();
	}
}
