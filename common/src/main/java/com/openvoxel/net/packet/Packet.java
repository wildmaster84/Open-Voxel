package com.openvoxel.net.packet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Base interface for all network packets.
 * Packets are serialized/deserialized for transmission over the network.
 */
public interface Packet {
    
    /**
     * Write packet data to the output stream.
     * @param out the output stream to write to
     * @throws IOException if an I/O error occurs
     */
    void write(DataOutputStream out) throws IOException;
    
    /**
     * Read packet data from the input stream.
     * @param in the input stream to read from
     * @throws IOException if an I/O error occurs
     */
    void read(DataInputStream in) throws IOException;
}
