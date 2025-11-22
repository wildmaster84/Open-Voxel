package engine.client.adapter;

import engine.net.Connection;
import engine.net.packet.PlayerMoveRequestPacket;
import engine.net.packet.PlayerMoveResultPacket;

import java.io.IOException;
import java.net.Socket;

/**
 * Client-side adapter for connecting to a remote authoritative server.
 * Sends movement requests and processes server responses.
 */
public class RemoteWorldAdapter {
	private Connection connection;
	private String serverAddress;
	private int serverPort;
	private boolean connected = false;
	
	public RemoteWorldAdapter(String serverAddress, int serverPort) {
		this.serverAddress = serverAddress;
		this.serverPort = serverPort;
	}
	
	/**
	 * Connect to the remote server.
	 * @return true if connection successful, false otherwise
	 */
	public boolean connect() {
		try {
			Socket socket = new Socket(serverAddress, serverPort);
			connection = new Connection(socket);
			connected = true;
			System.out.println("Connected to server at " + serverAddress + ":" + serverPort);
			return true;
		} catch (IOException e) {
			System.err.println("Failed to connect to server: " + e.getMessage());
			return false;
		}
	}
	
	/**
	 * Request a player movement from the server.
	 * @param x Desired X coordinate
	 * @param y Desired Y coordinate
	 * @param z Desired Z coordinate
	 * @return The server's response, or null if request failed
	 */
	public PlayerMoveResultPacket requestMove(float x, float y, float z) {
		if (!connected || connection == null) {
			System.err.println("Not connected to server");
			return null;
		}
		
		try {
			// Send the move request
			PlayerMoveRequestPacket request = new PlayerMoveRequestPacket(x, y, z);
			connection.sendPacket(request);
			
			// Wait for and receive the response
			// TODO: This blocks the calling thread. In production, use asynchronous packet 
			// handling with callbacks or CompletableFuture to prevent UI freezing
			var response = connection.receivePacket();
			if (response instanceof PlayerMoveResultPacket) {
				return (PlayerMoveResultPacket) response;
			} else {
				System.err.println("Received unexpected packet type: " + 
					(response != null ? response.getClass().getName() : "null"));
				return null;
			}
		} catch (IOException e) {
			System.err.println("Error during move request: " + e.getMessage());
			disconnect();
			return null;
		}
	}
	
	/**
	 * Disconnect from the server.
	 */
	public void disconnect() {
		if (connection != null) {
			connection.close();
			connected = false;
			System.out.println("Disconnected from server");
		}
	}
	
	/**
	 * Check if currently connected to the server.
	 * @return true if connected, false otherwise
	 */
	public boolean isConnected() {
		return connected && connection != null && connection.isConnected();
	}
	
	/**
	 * Get the underlying connection.
	 * @return The connection object
	 */
	public Connection getConnection() {
		return connection;
	}
}
