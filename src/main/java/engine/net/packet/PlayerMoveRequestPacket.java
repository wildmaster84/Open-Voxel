package engine.net.packet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Client-to-server packet requesting a player movement.
 * The server validates and responds with a PlayerMoveResultPacket.
 */
public class PlayerMoveRequestPacket implements Packet {
	public static final int PACKET_ID = 10;
	
	private float x;
	private float y;
	private float z;
	
	public PlayerMoveRequestPacket() {
		this(0, 0, 0);
	}
	
	public PlayerMoveRequestPacket(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
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
	}
	
	@Override
	public void read(DataInputStream in) throws IOException {
		this.x = in.readFloat();
		this.y = in.readFloat();
		this.z = in.readFloat();
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
	
	public void setX(float x) {
		this.x = x;
	}
	
	public void setY(float y) {
		this.y = y;
	}
	
	public void setZ(float z) {
		this.z = z;
	}
}
