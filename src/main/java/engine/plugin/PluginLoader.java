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
 * Loads plugins from JAR files in the plugins/ directory.
 * Each plugin JAR must contain META-INF/plugin.properties with a plugin-class property.
 */
public class PluginLoader {
    
    private final File pluginsDirectory;
    private final List<Plugin> loadedPlugins;
    
    public PluginLoader(File pluginsDirectory) {
        this.pluginsDirectory = pluginsDirectory;
        this.loadedPlugins = new ArrayList<>();
    }
    
    /**
     * Load all plugins from the plugins directory.
     * @return number of plugins loaded
     */
    public int loadPlugins() {
        if (!pluginsDirectory.exists()) {
            pluginsDirectory.mkdirs();
            System.out.println("Created plugins directory: " + pluginsDirectory.getAbsolutePath());
            return 0;
        }
        
        if (!pluginsDirectory.isDirectory()) {
            System.err.println("Plugins path is not a directory: " + pluginsDirectory);
            return 0;
        }
        
        File[] files = pluginsDirectory.listFiles((dir, name) -> name.endsWith(".jar"));
        if (files == null || files.length == 0) {
            System.out.println("No plugin JARs found in " + pluginsDirectory.getAbsolutePath());
            return 0;
        }
        
        int loaded = 0;
        for (File jarFile : files) {
            try {
                Plugin plugin = loadPlugin(jarFile);
                if (plugin != null) {
                    loadedPlugins.add(plugin);
                    plugin.onLoad();
                    System.out.println("Loaded plugin: " + plugin.getName() + " v" + plugin.getVersion());
                    loaded++;
                }
            } catch (Exception e) {
                System.err.println("Failed to load plugin from " + jarFile.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        return loaded;
    }
    
    /**
     * Load a single plugin from a JAR file.
     * @param jarFile JAR file containing the plugin
     * @return loaded plugin instance, or null if failed
     */
    private Plugin loadPlugin(File jarFile) throws IOException, ClassNotFoundException, 
            InstantiationException, IllegalAccessException {
        
        JarFile jar = new JarFile(jarFile);
        ZipEntry propertiesEntry = jar.getEntry("META-INF/plugin.properties");
        
        if (propertiesEntry == null) {
            jar.close();
            System.err.println("No META-INF/plugin.properties found in " + jarFile.getName());
            return null;
        }
        
        // Read plugin.properties
        Properties props = new Properties();
        try (InputStream in = jar.getInputStream(propertiesEntry)) {
            props.load(in);
        }
        
        String pluginClassName = props.getProperty("plugin-class");
        if (pluginClassName == null || pluginClassName.isEmpty()) {
            jar.close();
            System.err.println("No plugin-class property in " + jarFile.getName());
            return null;
        }
        
        jar.close();
        
        // Load the plugin class
        URL[] urls = new URL[] { jarFile.toURI().toURL() };
        URLClassLoader classLoader = new URLClassLoader(urls, getClass().getClassLoader());
        
        Class<?> pluginClass = classLoader.loadClass(pluginClassName);
        
        if (!Plugin.class.isAssignableFrom(pluginClass)) {
            System.err.println("Class " + pluginClassName + " does not implement Plugin interface");
            return null;
        }
        
        @SuppressWarnings("unchecked")
        Class<? extends Plugin> typedPluginClass = (Class<? extends Plugin>) pluginClass;
        
        return typedPluginClass.newInstance();
    }
    
    /**
     * Unload all plugins.
     */
    public void unloadPlugins() {
        for (Plugin plugin : loadedPlugins) {
            try {
                plugin.onUnload();
                System.out.println("Unloaded plugin: " + plugin.getName());
            } catch (Exception e) {
                System.err.println("Error unloading plugin " + plugin.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        loadedPlugins.clear();
    }
    
    /**
     * Get all loaded plugins.
     * @return list of loaded plugins
     */
    public List<Plugin> getLoadedPlugins() {
        return new ArrayList<>(loadedPlugins);
    }
}
