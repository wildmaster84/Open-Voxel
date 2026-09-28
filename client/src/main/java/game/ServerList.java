package game;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Saved multiplayer servers, persisted as flat "name|address" lines in
 * servers.txt (working dir). Simple to hand-edit; no JSON dependency.
 */
public class ServerList {
    public static class Entry {
        public String name;
        public String address; // host or host:port
        public Entry(String name, String address) { this.name = name; this.address = address; }
    }

    private static final Path FILE = Paths.get("servers.txt");

    public static List<Entry> load() {
        List<Entry> out = new ArrayList<>();
        try {
            if (!Files.exists(FILE)) return out;
            for (String line : Files.readAllLines(FILE)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int sep = line.indexOf('|');
                if (sep <= 0) continue;
                out.add(new Entry(line.substring(0, sep).trim(), line.substring(sep + 1).trim()));
            }
        } catch (IOException ignored) {}
        return out;
    }

    public static void save(List<Entry> entries) {
        StringBuilder sb = new StringBuilder("# Open-Voxel servers: name|address\n");
        for (Entry e : entries) sb.append(e.name).append('|').append(e.address).append('\n');
        try { Files.write(FILE, sb.toString().getBytes("UTF-8")); }
        catch (IOException ignored) {}
    }
}
