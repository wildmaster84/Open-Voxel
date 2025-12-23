package engine.server.handlers;

import engine.net.Connection;
import engine.net.packet.PlayerMoveRequestPacket;
import engine.net.packet.PlayerMoveResultPacket;
import engine.server.authoritative.WorldManager;

import java.io.IOException;

/**
 * Handles player movement requests from clients.
 * Validates movements against the authoritative world state and responds accordingly.
 */
public class PlayerMoveHandler {
	private WorldManager worldManager;
	
	public PlayerMoveHandler(WorldManager worldManager) {
		this.worldManager = worldManager;
	}
	
	/**
	 * Handle a player movement request.
	 * @param connection The connection from the client
	 * @param packet The movement request packet
	 * @param playerId The player's unique identifier
	 */
	public void handleMoveRequest(Connection connection, PlayerMoveRequestPacket packet, String playerId) {
		float requestedX = packet.getX();
		float requestedY = packet.getY();
		float requestedZ = packet.getZ();
		
		// Validate the movement
		boolean valid = worldManager.validateMovement(playerId, requestedX, requestedY, requestedZ);
		
		PlayerMoveResultPacket response;
		if (valid) {
			// Accept the movement
			worldManager.updatePlayer(playerId, requestedX, requestedY, requestedZ);
			response = new PlayerMoveResultPacket(requestedX, requestedY, requestedZ, true);
			System.out.println("Accepted move for player " + playerId + " to (" + 
				requestedX + ", " + requestedY + ", " + requestedZ + ")");
		} else {
			// Reject the movement, send back the current position
			WorldManager.PlayerState state = worldManager.getPlayerState(playerId);
			if (state != null) {
				response = new PlayerMoveResultPacket(state.getX(), state.getY(), state.getZ(), false);
				System.out.println("Rejected move for player " + playerId + ", sending correction");
			} else {
				// Player not found, accept as initial position
				worldManager.updatePlayer(playerId, requestedX, requestedY, requestedZ);
				response = new PlayerMoveResultPacket(requestedX, requestedY, requestedZ, true);
			}
		}
		
		// Send the response
		try {
			connection.sendPacket(response);
		} catch (IOException e) {
			System.err.println("Failed to send move result to player " + playerId + ": " + e.getMessage());
		}
	}
}
