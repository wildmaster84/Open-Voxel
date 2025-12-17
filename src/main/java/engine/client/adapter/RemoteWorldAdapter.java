package engine.client.adapter;

import engine.net.Connection;
import engine.net.packet.PlayerMoveRequestPacket;
import engine.net.packet.PlayerMoveResultPacket;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client-side adapter for communicating with a remote server.
 * Sends player move requests and correlates responses by request ID.
 */
public class RemoteWorldAdapter {
    
    private final Connection connection;
    private final AtomicInteger nextRequestId;
    private final Map<Integer, CompletableFuture<PlayerMoveResultPacket>> pendingRequests;
    
    public RemoteWorldAdapter(Connection connection) {
        this.connection = connection;
        this.nextRequestId = new AtomicInteger(1);
        this.pendingRequests = new HashMap<>();
        
        // Register handler for move results
        connection.registerHandler(PlayerMoveResultPacket.class, this::handleMoveResult);
    }
    
    /**
     * Request a player move from the server.
     * Returns a CompletableFuture that completes when the server responds.
     * 
     * @param x target x position
     * @param y target y position
     * @param z target z position
     * @return future that completes with the server's response
     */
    public CompletableFuture<PlayerMoveResultPacket> requestMove(float x, float y, float z) {
        int requestId = nextRequestId.getAndIncrement();
        
        CompletableFuture<PlayerMoveResultPacket> future = new CompletableFuture<>();
        
        synchronized (pendingRequests) {
            pendingRequests.put(requestId, future);
        }
        
        PlayerMoveRequestPacket request = new PlayerMoveRequestPacket(requestId, x, y, z);
        
        try {
            connection.sendPacket(request);
        } catch (IOException e) {
            // Remove from pending and complete exceptionally
            synchronized (pendingRequests) {
                pendingRequests.remove(requestId);
            }
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * Handle a move result packet from the server.
     * Completes the corresponding pending request.
     */
    private void handleMoveResult(PlayerMoveResultPacket result) {
        CompletableFuture<PlayerMoveResultPacket> future;
        
        synchronized (pendingRequests) {
            future = pendingRequests.remove(result.getRequestId());
        }
        
        if (future != null) {
            future.complete(result);
        } else {
            System.err.println("Received move result for unknown request ID: " + result.getRequestId());
        }
    }
    
    /**
     * Check if connected to the server.
     * @return true if connected
     */
    public boolean isConnected() {
        return connection.isConnected();
    }
    
    /**
     * Close the connection to the server.
     */
    public void disconnect() {
        connection.close();
        
        // Complete all pending requests exceptionally
        synchronized (pendingRequests) {
            for (CompletableFuture<PlayerMoveResultPacket> future : pendingRequests.values()) {
                future.completeExceptionally(new IOException("Connection closed"));
            }
            pendingRequests.clear();
        }
    }
}
