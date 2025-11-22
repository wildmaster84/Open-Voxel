# Open-Voxel
Open Source Voxel Game Engine


Demo


https://github.com/user-attachments/assets/0fb6b2d2-06d3-4a7b-b3bb-652b94100486

## Building the Project

To build the project, run:
```bash
mvn -T1C -DskipTests package
```

This will create a shaded JAR file in the `target/` directory.

## Server-Side Networking

The engine now includes server-side authoritative networking capabilities and a plugin system.

### Running the Server Integration Example

To run the example server:
```bash
java -cp target/client-0.0.7-Debug.jar engine.server.ServerIntegrationExample
```

The server will:
- Start on port 25565
- Register packet handlers for movement validation
- Load plugins from the `plugins/` directory
- Validate all player movements authoritatively

### Plugin System

Plugins are loaded from the `plugins/` directory. Each plugin JAR must include:

**META-INF/plugin.properties** with the following property:
```properties
plugin-class=com.example.MyPlugin
```

Your plugin class must implement the `engine.plugin.Plugin` interface:
```java
package com.example;

import engine.plugin.Plugin;

public class MyPlugin implements Plugin {
    @Override
    public void onLoad() {
        System.out.println("Plugin loaded!");
    }
    
    @Override
    public void onUnload() {
        System.out.println("Plugin unloaded!");
    }
    
    @Override
    public String getName() {
        return "MyPlugin";
    }
    
    @Override
    public String getVersion() {
        return "1.0.0";
    }
}
```

### Packet System

The networking system uses a packet registry with the following built-in packets:
- **ExampleHelloPacket** (ID 1): Simple greeting packet
- **PlayerMoveRequestPacket** (ID 10): Client requests movement
- **PlayerMoveResultPacket** (ID 11): Server confirms or corrects movement

All packets implement the `engine.net.packet.Packet` interface and are registered in the `PacketRegistry`.
