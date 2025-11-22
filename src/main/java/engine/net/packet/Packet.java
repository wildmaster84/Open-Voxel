package engine.net.packet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Base class for all network packets.
 * Packets are serialized with a length prefix followed by packet ID and data.
 */
public abstract class Packet {
    
    /**
     * Get the unique packet ID for this packet type.
     * @return packet ID
     */
    public abstract int getPacketId();
    
    /**
     * Write packet data to output stream (excluding length prefix and packet ID).
     * @param out output stream
     * @throws IOException if write fails
     */
    public abstract void write(DataOutputStream out) throws IOException;
    
    /**
     * Read packet data from input stream (excluding length prefix and packet ID).
     * @param in input stream
     * @throws IOException if read fails
     */
    public abstract void read(DataInputStream in) throws IOException;
}
