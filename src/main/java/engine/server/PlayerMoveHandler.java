package engine.server;

import engine.net.Connection;
import engine.net.packet.PlayerMoveRequestPacket;
import engine.net.packet.PlayerMoveResultPacket;

import java.io.IOException;

/**
 * Server-side handler for player move requests.
 * Validates moves with the WorldManager and sends responses.
 */
public class PlayerMoveHandler {
    
    private final WorldManager worldManager;
    private final String playerId;
    
    public PlayerMoveHandler(WorldManager worldManager, String playerId) {
        this.worldManager = worldManager;
        this.playerId = playerId;
    }
    
    /**
     * Handle a player move request.
     * @param connection connection to send response on
     * @param packet the move request packet
     */
    public void handleMoveRequest(Connection connection, PlayerMoveRequestPacket packet) {
        // Validate the move with the world manager
        WorldManager.MoveValidationResult result = worldManager.validateMove(
                playerId, 
                packet.getX(), 
                packet.getY(), 
                packet.getZ()
        );
        
        // Send response packet
        PlayerMoveResultPacket response = new PlayerMoveResultPacket(
                packet.getRequestId(),
                result.accepted,
                result.x,
                result.y,
                result.z,
                result.reason
        );
        
        try {
            connection.sendPacket(response);
        } catch (IOException e) {
            System.err.println("Failed to send move result to player " + playerId + ": " + e.getMessage());
        }
    }
}
