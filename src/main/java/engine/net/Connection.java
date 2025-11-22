package engine.net;

import engine.net.packet.Packet;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Represents a network connection that can send and receive packets.
 */
public class Connection {
	private Socket socket;
	private DataInputStream input;
	private DataOutputStream output;
	private boolean connected;
	
	public Connection(Socket socket) throws IOException {
		this.socket = socket;
		this.input = new DataInputStream(socket.getInputStream());
		this.output = new DataOutputStream(socket.getOutputStream());
		this.connected = true;
	}
	
	/**
	 * Send a packet over this connection.
	 * @param packet The packet to send
	 * @throws IOException If sending fails
	 */
	public void sendPacket(Packet packet) throws IOException {
		if (!connected) {
			throw new IOException("Connection is closed");
		}
		synchronized (output) {
			output.writeInt(packet.getPacketId());
			packet.write(output);
			output.flush();
		}
	}
	
	/**
	 * Receive a packet from this connection.
	 * This method blocks until a packet is received.
	 * @return The received packet, or null if connection is closed
	 * @throws IOException If receiving fails
	 */
	public Packet receivePacket() throws IOException {
		if (!connected) {
			return null;
		}
		try {
			return engine.net.packet.PacketRegistry.createPacket(input);
		} catch (IOException e) {
			close();
			throw e;
		}
	}
	
	/**
	 * Close this connection.
	 */
	public void close() {
		if (!connected) {
			return;
		}
		connected = false;
		try {
			if (socket != null && !socket.isClosed()) {
				socket.close();
			}
		} catch (IOException e) {
			System.err.println("Error closing connection: " + e.getMessage());
		}
	}
	
	/**
	 * Check if this connection is still active.
	 * @return true if connected, false otherwise
	 */
	public boolean isConnected() {
		return connected && socket != null && !socket.isClosed();
	}
	
	/**
	 * Get the remote address of this connection.
	 * @return The remote address as a string
	 */
	public String getRemoteAddress() {
		if (socket != null) {
			return socket.getInetAddress().getHostAddress();
		}
		return "unknown";
	}
}
