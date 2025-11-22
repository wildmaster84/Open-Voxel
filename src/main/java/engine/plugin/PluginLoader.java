package engine.plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Loads plugin JAR files from the plugins/ directory.
 * Each plugin JAR must contain META-INF/plugin.properties with a 'plugin-class' property.
 */
public class PluginLoader {
	private static final String PLUGINS_DIR = "plugins";
	private static final String PLUGIN_PROPERTIES = "META-INF/plugin.properties";
	private static final String PLUGIN_CLASS_KEY = "plugin-class";
	
	private List<Plugin> loadedPlugins = new ArrayList<>();
	private List<URLClassLoader> classLoaders = new ArrayList<>();
	
	/**
	 * Load all plugins from the plugins/ directory.
	 * @return The number of plugins successfully loaded
	 */
	public int loadPlugins() {
		File pluginsDir = new File(PLUGINS_DIR);
		if (!pluginsDir.exists()) {
			System.out.println("Plugins directory does not exist. Creating: " + PLUGINS_DIR);
			pluginsDir.mkdirs();
			return 0;
		}
		
		if (!pluginsDir.isDirectory()) {
			System.err.println("Error: " + PLUGINS_DIR + " is not a directory");
			return 0;
		}
		
		File[] jarFiles = pluginsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
		if (jarFiles == null || jarFiles.length == 0) {
			System.out.println("No plugin JAR files found in " + PLUGINS_DIR);
			return 0;
		}
		
		int loadedCount = 0;
		for (File jarFile : jarFiles) {
			try {
				Plugin plugin = loadPlugin(jarFile);
				if (plugin != null) {
					loadedPlugins.add(plugin);
					plugin.onLoad();
					System.out.println("Loaded plugin: " + plugin.getName() + " v" + plugin.getVersion());
					loadedCount++;
				}
			} catch (Exception e) {
				System.err.println("Failed to load plugin from " + jarFile.getName() + ": " + e.getMessage());
				e.printStackTrace();
			}
		}
		
		return loadedCount;
	}
	
	/**
	 * Load a single plugin from a JAR file.
	 * @param jarFile The JAR file to load
	 * @return The loaded plugin instance, or null if loading failed
	 * @throws Exception If loading fails
	 */
	private Plugin loadPlugin(File jarFile) throws Exception {
		// Read plugin.properties from the JAR
		String pluginClassName;
		try (JarFile jar = new JarFile(jarFile)) {
			ZipEntry propertiesEntry = jar.getEntry(PLUGIN_PROPERTIES);
			if (propertiesEntry == null) {
				System.err.println("Error: " + jarFile.getName() + " does not contain " + PLUGIN_PROPERTIES);
				return null;
			}
			
			Properties props = new Properties();
			try (InputStream in = jar.getInputStream(propertiesEntry)) {
				props.load(in);
			}
			
			pluginClassName = props.getProperty(PLUGIN_CLASS_KEY);
			if (pluginClassName == null || pluginClassName.trim().isEmpty()) {
				System.err.println("Error: " + PLUGIN_PROPERTIES + " in " + jarFile.getName() + 
					" does not contain '" + PLUGIN_CLASS_KEY + "' property");
				return null;
			}
		}
		
		// Load the plugin class
		URLClassLoader classLoader = new URLClassLoader(
			new URL[] { jarFile.toURI().toURL() },
			getClass().getClassLoader()
		);
		
		try {
			Class<?> pluginClass = classLoader.loadClass(pluginClassName);
			if (!Plugin.class.isAssignableFrom(pluginClass)) {
				System.err.println("Error: " + pluginClassName + " does not implement Plugin interface");
				classLoader.close();
				return null;
			}
			
			Plugin plugin = (Plugin) pluginClass.getDeclaredConstructor().newInstance();
			// Store classloader for cleanup during unload
			classLoaders.add(classLoader);
			return plugin;
		} catch (Exception e) {
			classLoader.close();
			throw e;
		}
	}
	
	/**
	 * Unload all loaded plugins and close their classloaders.
	 */
	public void unloadAll() {
		for (Plugin plugin : loadedPlugins) {
			try {
				plugin.onUnload();
				System.out.println("Unloaded plugin: " + plugin.getName());
			} catch (Exception e) {
				System.err.println("Error unloading plugin " + plugin.getName() + ": " + e.getMessage());
			}
		}
		loadedPlugins.clear();
		
		// Close all classloaders to free resources
		for (URLClassLoader classLoader : classLoaders) {
			try {
				classLoader.close();
			} catch (Exception e) {
				System.err.println("Error closing plugin classloader: " + e.getMessage());
			}
		}
		classLoaders.clear();
	}
	
	/**
	 * Get all loaded plugins.
	 * @return List of loaded plugins
	 */
	public List<Plugin> getLoadedPlugins() {
		return new ArrayList<>(loadedPlugins);
	}
}
