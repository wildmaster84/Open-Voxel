package engine.server.authoritative;

import java.util.HashMap;
import java.util.Map;

/**
 * Server-side authoritative world state manager.
 * Maintains the canonical state of the game world and validates all changes.
 */
public class WorldManager {
	private Map<String, PlayerState> players = new HashMap<>();
	
	/**
	 * Represents a player's state on the server.
	 */
	public static class PlayerState {
		private String playerId;
		private float x;
		private float y;
		private float z;
		private long lastUpdateTime;
		
		public PlayerState(String playerId, float x, float y, float z) {
			this.playerId = playerId;
			this.x = x;
			this.y = y;
			this.z = z;
			this.lastUpdateTime = System.currentTimeMillis();
		}
		
		public String getPlayerId() {
			return playerId;
		}
		
		public float getX() {
			return x;
		}
		
		public float getY() {
			return y;
		}
		
		public float getZ() {
			return z;
		}
		
		public void setPosition(float x, float y, float z) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.lastUpdateTime = System.currentTimeMillis();
		}
		
		public long getLastUpdateTime() {
			return lastUpdateTime;
		}
	}
	
	/**
	 * Add or update a player in the world.
	 * @param playerId The player's unique identifier
	 * @param x X coordinate
	 * @param y Y coordinate
	 * @param z Z coordinate
	 */
	public void updatePlayer(String playerId, float x, float y, float z) {
		PlayerState state = players.get(playerId);
		if (state == null) {
			state = new PlayerState(playerId, x, y, z);
			players.put(playerId, state);
			System.out.println("Added player " + playerId + " at (" + x + ", " + y + ", " + z + ")");
		} else {
			state.setPosition(x, y, z);
		}
	}
	
	/**
	 * Validate a proposed player movement.
	 * @param playerId The player's unique identifier
	 * @param newX Proposed X coordinate
	 * @param newY Proposed Y coordinate
	 * @param newZ Proposed Z coordinate
	 * @return true if the movement is valid, false otherwise
	 */
	public boolean validateMovement(String playerId, float newX, float newY, float newZ) {
		PlayerState state = players.get(playerId);
		if (state == null) {
			// New player, accept the initial position
			return true;
		}
		
		// Calculate distance moved
		float dx = newX - state.getX();
		float dy = newY - state.getY();
		float dz = newZ - state.getZ();
		float distanceSq = dx * dx + dy * dy + dz * dz;
		
		// Simple validation: limit movement speed
		// Allow up to 10 units per tick (can be adjusted)
		float maxDistanceSq = 100.0f;
		
		if (distanceSq > maxDistanceSq) {
			System.out.println("Rejected movement for player " + playerId + ": too fast");
			return false;
		}
		
		// Additional validation could check for collisions, invalid positions, etc.
		
		return true;
	}
	
	/**
	 * Get a player's current state.
	 * @param playerId The player's unique identifier
	 * @return The player's state, or null if not found
	 */
	public PlayerState getPlayerState(String playerId) {
		return players.get(playerId);
	}
	
	/**
	 * Remove a player from the world.
	 * @param playerId The player's unique identifier
	 */
	public void removePlayer(String playerId) {
		players.remove(playerId);
		System.out.println("Removed player " + playerId);
	}
	
	/**
	 * Get all player states.
	 * @return Map of player IDs to their states
	 */
	public Map<String, PlayerState> getAllPlayers() {
		return new HashMap<>(players);
	}
}
