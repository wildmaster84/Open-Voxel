package engine.plugin;

/**
 * Base interface that all plugins must implement.
 * Plugins are loaded from JAR files in the plugins/ directory.
 */
public interface Plugin {
	/**
	 * Called when the plugin is loaded and initialized.
	 * Use this to set up your plugin and register handlers.
	 */
	void onLoad();
	
	/**
	 * Called when the plugin is being disabled/unloaded.
	 * Use this to clean up resources.
	 */
	void onUnload();
	
	/**
	 * Get the name of this plugin.
	 * @return The plugin name
	 */
	String getName();
	
	/**
	 * Get the version of this plugin.
	 * @return The plugin version
	 */
	String getVersion();
}
