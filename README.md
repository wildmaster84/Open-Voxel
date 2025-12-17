# Open-Voxel
Open Source Voxel Game Engine


Demo


https://github.com/user-attachments/assets/0fb6b2d2-06d3-4a7b-b3bb-652b94100486

## Building

Build the project with Maven:
```bash
mvn -T1C -DskipTests package
```

This will create a shaded JAR with all dependencies in the `target/` directory.

## Running

### Client Demo
Run the existing voxel engine demo:
```bash
java -jar target/client-0.0.7-Debug.jar
```

Or with Maven:
```bash
mvn exec:java
```

### Server Example
Run the server integration example:
```bash
java -cp target/client-0.0.7-Debug.jar engine.server.ServerIntegrationExample [port]
```

Default port is 25565. The server demonstrates:
- Packet-based networking with length-prefixed framing
- Authoritative server-side player move validation
- Plugin loading from the `plugins/` directory

## Plugin Development

Plugins extend the engine with custom functionality. To create a plugin:

1. Create a class that implements `engine.plugin.Plugin`
2. Package it as a JAR file
3. Include `META-INF/plugin.properties` with:
   ```properties
   plugin-class=com.example.MyPlugin
   ```
4. Place the JAR in the `plugins/` directory
5. The plugin will be loaded automatically on server startup

Example plugin structure:
```
my-plugin.jar
├── com/example/MyPlugin.class
└── META-INF/plugin.properties
```

