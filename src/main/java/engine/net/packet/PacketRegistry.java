package engine.net.packet;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Registry for mapping packet IDs to packet constructors.
 * Allows dynamic packet instantiation during deserialization.
 */
public class PacketRegistry {
    
    private final Map<Integer, Supplier<Packet>> packetFactories = new HashMap<>();
    
    /**
     * Register a packet type with its ID.
     * @param packetId unique packet ID
     * @param factory supplier that creates new packet instances
     */
    public void register(int packetId, Supplier<Packet> factory) {
        if (packetFactories.containsKey(packetId)) {
            throw new IllegalArgumentException("Packet ID " + packetId + " is already registered");
        }
        packetFactories.put(packetId, factory);
    }
    
    /**
     * Create a new packet instance for the given ID.
     * @param packetId packet ID
     * @return new packet instance
     * @throws IllegalArgumentException if packet ID is not registered
     */
    public Packet createPacket(int packetId) {
        Supplier<Packet> factory = packetFactories.get(packetId);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown packet ID: " + packetId);
        }
        return factory.get();
    }
    
    /**
     * Check if a packet ID is registered.
     * @param packetId packet ID to check
     * @return true if registered
     */
    public boolean isRegistered(int packetId) {
        return packetFactories.containsKey(packetId);
    }
}
