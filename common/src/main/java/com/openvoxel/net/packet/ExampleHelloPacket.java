package com.openvoxel.net.packet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Example packet that contains a simple text message.
 * Used to demonstrate packet communication between client and server.
 */
public class ExampleHelloPacket implements Packet {
    
    private String message;
    
    public ExampleHelloPacket() {
        this.message = "";
    }
    
    public ExampleHelloPacket(String message) {
        this.message = message;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeUTF(message);
    }
    
    @Override
    public void read(DataInputStream in) throws IOException {
        this.message = in.readUTF();
    }
}
