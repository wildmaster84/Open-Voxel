package engine.net.packet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Packet sent from client to request a player move.
 * Server validates and responds with PlayerMoveResultPacket.
 */
public class PlayerMoveRequestPacket extends Packet {
    
    public static final int PACKET_ID = 10;
    
    private int requestId;
    private float x;
    private float y;
    private float z;
    
    public PlayerMoveRequestPacket() {
    }
    
    public PlayerMoveRequestPacket(int requestId, float x, float y, float z) {
        this.requestId = requestId;
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
        out.writeInt(requestId);
        out.writeFloat(x);
        out.writeFloat(y);
        out.writeFloat(z);
    }
    
    @Override
    public void read(DataInputStream in) throws IOException {
        this.requestId = in.readInt();
        this.x = in.readFloat();
        this.y = in.readFloat();
        this.z = in.readFloat();
    }
    
    public int getRequestId() {
        return requestId;
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
}
