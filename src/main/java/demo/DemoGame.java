package demo;

import engine.VoxelEngine;

public class DemoGame {
    public static void main(String[] args) {
    	java.util.Map<String, Integer> params = parseArgs(args);
    	int vsync = params.getOrDefault("vsync", 1);
    	int renderDistance = params.getOrDefault("render-distance", 10);
        VoxelEngine engine = new VoxelEngine(vsync, renderDistance);
        
        engine.start();
        
    }
    
    private static java.util.Map<String, Integer> parseArgs(String[] args) {
        java.util.Map<String, Integer> map = new java.util.HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                String key = arg.substring(2);
                String value = "true";
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    value = args[++i];
                }
                map.put(key, Integer.valueOf(value));
            }
        }
        return map;
    }
}