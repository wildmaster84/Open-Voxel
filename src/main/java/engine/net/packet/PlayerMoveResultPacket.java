package engine.net.packet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Packet sent from server to respond to a player move request.
 * Contains the request ID for correlation and whether the move was accepted.
 */
public class PlayerMoveResultPacket extends Packet {
    
    public static final int PACKET_ID = 11;
    
    private int requestId;
    private boolean accepted;
    private float x;
    private float y;
    private float z;
    private String reason;
    
    public PlayerMoveResultPacket() {
        this.reason = "";
    }
    
    public PlayerMoveResultPacket(int requestId, boolean accepted, float x, float y, float z, String reason) {
        this.requestId = requestId;
        this.accepted = accepted;
        this.x = x;
        this.y = y;
        this.z = z;
        this.reason = reason != null ? reason : "";
    }
    
    @Override
    public int getPacketId() {
        return PACKET_ID;
    }
    
    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeInt(requestId);
        out.writeBoolean(accepted);
        out.writeFloat(x);
        out.writeFloat(y);
        out.writeFloat(z);
        out.writeUTF(reason);
    }
    
    @Override
    public void read(DataInputStream in) throws IOException {
        this.requestId = in.readInt();
        this.accepted = in.readBoolean();
        this.x = in.readFloat();
        this.y = in.readFloat();
        this.z = in.readFloat();
        this.reason = in.readUTF();
    }
    
    public int getRequestId() {
        return requestId;
    }
    
    public boolean isAccepted() {
        return accepted;
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
    
    public String getReason() {
        return reason;
    }
}
