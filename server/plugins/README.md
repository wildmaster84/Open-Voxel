# Server Plugins Directory

Place plugin JAR files in this directory to have them automatically loaded by the server.

## Plugin Requirements

Each plugin JAR must:

1. Contain a class that implements `com.openvoxel.plugin.Plugin` interface
2. Include a `META-INF/plugin.properties` file with the following property:
   ```
   plugin-class=your.full.package.PluginClassName
   ```

## Example Plugin

Here's a minimal plugin example:

```java
package com.example.myplugin;

import com.openvoxel.plugin.Plugin;

public class MyPlugin implements Plugin {
    
    @Override
    public void onEnable(PluginContext context) {
        System.out.println("MyPlugin enabled!");
        // Access packet registry, register custom packets, etc.
    }
    
    @Override
    public void onDisable() {
        System.out.println("MyPlugin disabled!");
    }
    
    @Override
    public String getName() {
        return "MyPlugin";
    }
}
```

And the corresponding `META-INF/plugin.properties`:
```
plugin-class=com.example.myplugin.MyPlugin
```

## Building a Plugin

1. Create a Maven project that depends on the common module
2. Implement the Plugin interface
3. Create META-INF/plugin.properties
4. Build the plugin JAR: `mvn package`
5. Copy the JAR to this directory
6. Restart the server to load the plugin
