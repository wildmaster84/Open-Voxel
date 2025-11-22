package engine.server;

import java.util.HashMap;
import java.util.Map;

/**
 * Server-side world manager that maintains authoritative state.
 * Validates player movements and other world interactions.
 */
public class WorldManager {
    
    private final Map<String, PlayerState> playerStates;
    
    public WorldManager() {
        this.playerStates = new HashMap<>();
    }
    
    /**
     * Get or create a player state.
     * @param playerId player identifier
     * @return player state
     */
    public PlayerState getPlayerState(String playerId) {
        return playerStates.computeIfAbsent(playerId, id -> new PlayerState(id));
    }
    
    /**
     * Validate a player move request.
     * @param playerId player identifier
     * @param x target x position
     * @param y target y position
     * @param z target z position
     * @return result of the validation
     */
    public MoveValidationResult validateMove(String playerId, float x, float y, float z) {
        PlayerState state = getPlayerState(playerId);
        
        // Calculate distance from current position
        float dx = x - state.x;
        float dy = y - state.y;
        float dz = z - state.z;
        float distanceSq = dx * dx + dy * dy + dz * dz;
        
        // Simple validation: check if move is within reasonable bounds
        final float MAX_MOVE_DISTANCE_SQ = 100.0f; // Max 10 units per move
        
        if (distanceSq > MAX_MOVE_DISTANCE_SQ) {
            return new MoveValidationResult(false, state.x, state.y, state.z, 
                    "Move too far (distance: " + Math.sqrt(distanceSq) + ")");
        }
        
        // Additional checks could be added here:
        // - Collision detection
        // - Anti-cheat validation
        // - World boundaries
        
        // Accept the move
        state.x = x;
        state.y = y;
        state.z = z;
        state.lastMoveTime = System.currentTimeMillis();
        
        return new MoveValidationResult(true, x, y, z, "");
    }
    
    /**
     * Remove a player from tracking.
     * @param playerId player identifier
     */
    public void removePlayer(String playerId) {
        playerStates.remove(playerId);
    }
    
    /**
     * Represents the server-side state of a player.
     */
    public static class PlayerState {
        public final String playerId;
        public float x;
        public float y;
        public float z;
        public long lastMoveTime;
        
        public PlayerState(String playerId) {
            this.playerId = playerId;
            this.x = 0;
            this.y = 64;
            this.z = 0;
            this.lastMoveTime = System.currentTimeMillis();
        }
    }
    
    /**
     * Result of a move validation.
     */
    public static class MoveValidationResult {
        public final boolean accepted;
        public final float x;
        public final float y;
        public final float z;
        public final String reason;
        
        public MoveValidationResult(boolean accepted, float x, float y, float z, String reason) {
            this.accepted = accepted;
            this.x = x;
            this.y = y;
            this.z = z;
            this.reason = reason;
        }
    }
}
