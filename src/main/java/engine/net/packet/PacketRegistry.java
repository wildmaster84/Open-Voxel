package engine.net.packet;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Registry for packet types. Maps packet IDs to packet constructors.
 */
public class PacketRegistry {
	private static final Map<Integer, Supplier<Packet>> registry = new HashMap<>();
	
	/**
	 * Register a packet type with its ID.
	 * @param id The unique packet ID
	 * @param constructor A supplier that creates new instances of the packet
	 */
	public static void register(int id, Supplier<Packet> constructor) {
		if (registry.containsKey(id)) {
			System.err.println("Warning: Packet ID " + id + " already registered. Overwriting.");
		}
		registry.put(id, constructor);
	}
	
	/**
	 * Create a packet instance from a data stream.
	 * @param in The input stream to read from
	 * @return A new packet instance, or null if the packet type is unknown
	 * @throws IOException If reading fails
	 */
	public static Packet createPacket(DataInputStream in) throws IOException {
		int id = in.readInt();
		Supplier<Packet> constructor = registry.get(id);
		if (constructor == null) {
			System.err.println("Unknown packet ID: " + id);
			return null;
		}
		Packet packet = constructor.get();
		packet.read(in);
		return packet;
	}
	
	/**
	 * Check if a packet ID is registered.
	 * @param id The packet ID to check
	 * @return true if registered, false otherwise
	 */
	public static boolean isRegistered(int id) {
		return registry.containsKey(id);
	}
}
