package engine.plugin;

/**
 * Interface that all plugins must implement.
 * Plugins are loaded from JAR files in the plugins/ directory.
 */
public interface Plugin {
    
    /**
     * Called when the plugin is loaded.
     * Use this to initialize the plugin and register handlers.
     */
    void onLoad();
    
    /**
     * Called when the plugin is unloaded.
     * Use this to clean up resources.
     */
    void onUnload();
    
    /**
     * Get the plugin name.
     * @return plugin name
     */
    String getName();
    
    /**
     * Get the plugin version.
     * @return plugin version
     */
    String getVersion();
}
