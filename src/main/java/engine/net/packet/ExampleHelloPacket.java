package engine.net.packet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Example packet for testing basic networking.
 * Sends a simple hello message.
 */
public class ExampleHelloPacket extends Packet {
    
    public static final int PACKET_ID = 1;
    
    private String message;
    
    public ExampleHelloPacket() {
        this.message = "";
    }
    
    public ExampleHelloPacket(String message) {
        this.message = message;
    }
    
    @Override
    public int getPacketId() {
        return PACKET_ID;
    }
    
    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeUTF(message);
    }
    
    @Override
    public void read(DataInputStream in) throws IOException {
        this.message = in.readUTF();
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
}
