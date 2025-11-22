# Plugins Directory

Place plugin JAR files in this directory.

Each plugin JAR must contain `META-INF/plugin.properties` with the following property:
```
plugin-class=com.example.YourPlugin
```

The specified class must implement `engine.plugin.Plugin`.

