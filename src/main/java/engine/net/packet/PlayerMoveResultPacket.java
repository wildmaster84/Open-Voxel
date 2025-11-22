package engine.net.packet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Server-to-client packet confirming or correcting a player movement.
 * Contains the authoritative position determined by the server.
 */
public class PlayerMoveResultPacket implements Packet {
	public static final int PACKET_ID = 11;
	
	private float x;
	private float y;
	private float z;
	private boolean accepted;
	
	public PlayerMoveResultPacket() {
		this(0, 0, 0, false);
	}
	
	public PlayerMoveResultPacket(float x, float y, float z, boolean accepted) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.accepted = accepted;
	}
	
	@Override
	public int getPacketId() {
		return PACKET_ID;
	}
	
	@Override
	public void write(DataOutputStream out) throws IOException {
		out.writeFloat(x);
		out.writeFloat(y);
		out.writeFloat(z);
		out.writeBoolean(accepted);
	}
	
	@Override
	public void read(DataInputStream in) throws IOException {
		this.x = in.readFloat();
		this.y = in.readFloat();
		this.z = in.readFloat();
		this.accepted = in.readBoolean();
	}
	
	public float getX() {
		return x;
	}
	
	public float getY() {
		return y;
	}
	
	public float getZ() {
		return z;
	}
	
	public boolean isAccepted() {
		return accepted;
	}
	
	public void setX(float x) {
		this.x = x;
	}
	
	public void setY(float y) {
		this.y = y;
	}
	
	public void setZ(float z) {
		this.z = z;
	}
	
	public void setAccepted(boolean accepted) {
		this.accepted = accepted;
	}
}
