package com.openvoxel.client;

import com.openvoxel.net.Connection;
import com.openvoxel.net.packet.ExampleHelloPacket;
import com.openvoxel.net.packet.PacketRegistry;

import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

/**
 * Main entry point for the client application.
 * Connects to the server and demonstrates packet communication.
 */
public class ClientMain {
    
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 25565;
    private final PacketRegistry packetRegistry;
    private Connection connection;
    
    public ClientMain() {
        this.packetRegistry = new PacketRegistry();
        registerPackets();
    }
    
    /**
     * Register all packet types with their IDs.
     * Must match the server's packet registration!
     */
    private void registerPackets() {
        // Register ExampleHelloPacket with ID 1 (must match server)
        packetRegistry.register(1, ExampleHelloPacket.class, ExampleHelloPacket::new);
    }
    
    /**
     * Connect to the server.
     */
    public void connect(String host, int port) {
        try {
            System.out.println("Connecting to server at " + host + ":" + port + "...");
            Socket socket = new Socket(host, port);
            connection = new Connection(socket, packetRegistry);
            
            // Register packet handlers
            connection.registerHandler(ExampleHelloPacket.class, this::handleHelloPacket);
            
            // Start reading packets
            connection.startReader();
            
            System.out.println("Connected to server!");
            
        } catch (IOException e) {
            System.err.println("Failed to connect to server: " + e.getMessage());
            System.exit(1);
        }
    }
    
    /**
     * Handle an incoming ExampleHelloPacket.
     */
    private void handleHelloPacket(ExampleHelloPacket packet) {
        System.out.println("Server says: " + packet.getMessage());
    }
    
    /**
     * Send a hello message to the server.
     */
    public void sendHello(String message) {
        try {
            ExampleHelloPacket packet = new ExampleHelloPacket(message);
            connection.sendPacket(packet);
            System.out.println("Sent hello message: " + message);
        } catch (IOException e) {
            System.err.println("Failed to send packet: " + e.getMessage());
        }
    }
    
    /**
     * Run the client in interactive mode.
     */
    public void run() {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("\nClient started. Type messages to send to server (or 'quit' to exit):");
        
        while (true) {
            System.out.print("> ");
            String line = scanner.nextLine().trim();
            
            if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                break;
            }
            
            if (!line.isEmpty()) {
                sendHello(line);
            }
        }
        
        if (connection != null) {
            connection.close();
        }
        
        System.out.println("Client disconnected.");
    }
    
    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;
        
        // Parse command line arguments
        if (args.length >= 1) {
            host = args[0];
        }
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[1]);
                System.exit(1);
            }
        }
        
        ClientMain client = new ClientMain();
        client.connect(host, port);
        client.run();
    }
}
