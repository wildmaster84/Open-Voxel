package com.openvoxel.plugin;

import com.openvoxel.net.Connection;
import com.openvoxel.net.packet.Packet;
import com.openvoxel.net.packet.PacketRegistry;

/**
 * Base interface for all plugins.
 * Plugins can be loaded dynamically and interact with the game through this API.
 */
public interface Plugin {
    
    /**
     * Called when the plugin is enabled.
     * @param context the plugin context providing access to game APIs
     */
    void onEnable(PluginContext context);
    
    /**
     * Called when the plugin is disabled.
     */
    void onDisable();
    
    /**
     * Get the name of this plugin.
     * @return the plugin name
     */
    String getName();
    
    /**
     * Context provided to plugins, exposing game APIs.
     */
    interface PluginContext {
        
        /**
         * Get the packet registry for registering custom packets.
         * @return the packet registry
         */
        PacketRegistry getPacketRegistry();
        
        /**
         * Broadcast a packet to all connected clients.
         * @param packet the packet to broadcast
         */
        void broadcast(Packet packet);
        
        /**
         * Send a packet to a specific connection.
         * @param connection the target connection
         * @param packet the packet to send
         */
        void sendTo(Connection connection, Packet packet);
    }
}
