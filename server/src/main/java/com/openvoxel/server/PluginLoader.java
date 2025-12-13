package com.openvoxel.server;

import com.openvoxel.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Loads plugins from JAR files in the plugins directory.
 * Each plugin JAR must contain META-INF/plugin.properties with a "plugin-class" property.
 */
public class PluginLoader {
    
    private static final String PLUGIN_PROPERTIES = "META-INF/plugin.properties";
    private static final String PLUGIN_CLASS_KEY = "plugin-class";
    
    /**
     * Load all plugins from the given directory.
     * @param pluginsDir the directory containing plugin JAR files
     * @return a list of loaded plugin instances
     */
    public List<Plugin> loadPlugins(File pluginsDir) {
        List<Plugin> plugins = new ArrayList<>();
        
        if (!pluginsDir.exists() || !pluginsDir.isDirectory()) {
            System.out.println("Plugins directory not found: " + pluginsDir.getAbsolutePath());
            return plugins;
        }
        
        File[] files = pluginsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (files == null || files.length == 0) {
            System.out.println("No plugin JARs found in: " + pluginsDir.getAbsolutePath());
            return plugins;
        }
        
        for (File file : files) {
            try {
                Plugin plugin = loadPlugin(file);
                if (plugin != null) {
                    plugins.add(plugin);
                    System.out.println("Loaded plugin: " + plugin.getName() + " from " + file.getName());
                }
            } catch (Exception e) {
                System.err.println("Failed to load plugin from " + file.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        return plugins;
    }
    
    /**
     * Load a single plugin from a JAR file.
     * @param jarFile the JAR file containing the plugin
     * @return the loaded plugin instance, or null if loading failed
     */
    private Plugin loadPlugin(File jarFile) throws Exception {
        // Read plugin.properties from the JAR
        String pluginClassName = null;
        
        try (JarFile jar = new JarFile(jarFile)) {
            ZipEntry entry = jar.getEntry(PLUGIN_PROPERTIES);
            if (entry == null) {
                System.err.println("Plugin JAR missing " + PLUGIN_PROPERTIES + ": " + jarFile.getName());
                return null;
            }
            
            Properties props = new Properties();
            try (InputStream in = jar.getInputStream(entry)) {
                props.load(in);
            }
            
            pluginClassName = props.getProperty(PLUGIN_CLASS_KEY);
            if (pluginClassName == null || pluginClassName.trim().isEmpty()) {
                System.err.println("Plugin properties missing '" + PLUGIN_CLASS_KEY + "' property: " + jarFile.getName());
                return null;
            }
        }
        
        // Load the plugin class
        URL[] urls = new URL[] { jarFile.toURI().toURL() };
        // Note: We don't close the classloader here because it needs to remain open
        // for the plugin classes to remain accessible during the server's lifetime
        URLClassLoader classLoader = new URLClassLoader(urls, getClass().getClassLoader());
        
        Class<?> pluginClass = classLoader.loadClass(pluginClassName.trim());
        if (!Plugin.class.isAssignableFrom(pluginClass)) {
            System.err.println("Plugin class does not implement Plugin interface: " + pluginClassName);
            classLoader.close();
            return null;
        }
        
        // Instantiate the plugin
        return (Plugin) pluginClass.getDeclaredConstructor().newInstance();
    }
}
