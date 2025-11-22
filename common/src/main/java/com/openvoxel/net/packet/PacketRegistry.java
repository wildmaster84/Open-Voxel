package com.openvoxel.net.packet;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Registry for packet types, mapping packet IDs to packet factories.
 * Allows serialization and deserialization of packets over the network.
 */
public class PacketRegistry {
    
    private final Map<Integer, Supplier<Packet>> idToFactory = new HashMap<>();
    private final Map<Class<? extends Packet>, Integer> classToId = new HashMap<>();
    
    /**
     * Register a packet type with the given ID.
     * @param id the packet ID
     * @param packetClass the packet class
     * @param factory a supplier that creates new instances of the packet
     */
    public void register(int id, Class<? extends Packet> packetClass, Supplier<Packet> factory) {
        idToFactory.put(id, factory);
        classToId.put(packetClass, id);
    }
    
    /**
     * Get the packet ID for a given packet class.
     * @param packetClass the packet class
     * @return the packet ID, or null if not registered
     */
    public Integer getId(Class<? extends Packet> packetClass) {
        return classToId.get(packetClass);
    }
    
    /**
     * Create a new packet instance from a packet ID.
     * @param id the packet ID
     * @return a new packet instance, or null if ID is not registered
     */
    public Packet createPacket(int id) {
        Supplier<Packet> factory = idToFactory.get(id);
        return factory != null ? factory.get() : null;
    }
}
