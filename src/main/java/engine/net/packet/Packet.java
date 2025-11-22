package engine.net.packet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Base interface for all network packets.
 * Each packet type must have a unique ID and implement serialization methods.
 */
public interface Packet {
	/**
	 * Get the unique identifier for this packet type.
	 * @return The packet ID
	 */
	int getPacketId();
	
	/**
	 * Write packet data to the output stream.
	 * @param out The output stream to write to
	 * @throws IOException If writing fails
	 */
	void write(DataOutputStream out) throws IOException;
	
	/**
	 * Read packet data from the input stream.
	 * @param in The input stream to read from
	 * @throws IOException If reading fails
	 */
	void read(DataInputStream in) throws IOException;
}
