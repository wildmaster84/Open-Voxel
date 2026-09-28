package game;

import java.util.HashMap;
import java.util.Map;

/**
 * Launcher-passed identity + engine args. The launcher starts the jar with:
 *   --username X --uuid Y --token T --authserver https://open-voxel.net
 * plus engine args: --vsync N --render-distance N
 */
public class SessionInfo {
    public final String username;   // null = offline mode (no launcher login)
    public final String uuid;
    public final String token;
    public final String authServer;
    public final int vsync;
    public final int renderDistance;

    private SessionInfo(Map<String, String> args) {
        this.username = args.get("username");
        this.uuid = args.get("uuid");
        this.token = args.get("token");
        this.authServer = args.getOrDefault("authserver", "https://open-voxel.net");
        this.vsync = parseInt(args.get("vsync"), 1);
        this.renderDistance = parseInt(args.get("render-distance"), 12);
    }

    public boolean isLoggedIn() { return username != null && token != null; }

    public static SessionInfo fromArgs(String[] argv) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < argv.length; i++) {
            String arg = argv[i];
            if (arg.startsWith("--")) {
                String key = arg.substring(2);
                String value = "true";
                if (i + 1 < argv.length && !argv[i + 1].startsWith("--")) {
                    value = argv[++i];
                }
                map.put(key, value);
            }
        }
        return new SessionInfo(map);
    }

    private static int parseInt(String s, int def) {
        try { return s == null ? def : Integer.parseInt(s); }
        catch (NumberFormatException e) { return def; }
    }
}
