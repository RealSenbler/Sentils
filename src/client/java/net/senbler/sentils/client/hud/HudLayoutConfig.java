package net.senbler.sentils.client.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import net.senbler.sentils.client.Log;
import net.senbler.sentils.client.config.ConfigFiles;

/** Where each HUD widget has been placed, keyed by widget id. */
public final class HudLayoutConfig {

    private HudLayoutConfig() {}

    public static class Placement {
        public float x; // fraction of the screen width
        public float y; // fraction of the screen height
        public float scale = 1f;

        public Placement() {}

        public Placement(float x, float y, float scale) {
            this.x = x;
            this.y = y;
            this.scale = scale;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = ConfigFiles.resolve("hud.json");

    public static Map<String, Placement> load() {
        if (!Files.exists(FILE)) return new HashMap<>();
        try {
            Type type = new TypeToken<HashMap<String, Placement>>() {}.getType();
            Map<String, Placement> loaded = GSON.fromJson(Files.readString(FILE), type);
            if (loaded != null) {
                loaded.values().removeIf(placement -> placement == null);
                return loaded;
            }
        } catch (IOException | RuntimeException e) {
            Log.warn("Failed to load HUD layout: " + e.getMessage());
        }
        return new HashMap<>();
    }

    public static void save(Map<String, Placement> placements) {
        try {
            Files.writeString(FILE, GSON.toJson(placements));
        } catch (IOException e) {
            Log.warn("Failed to save HUD layout: " + e.getMessage());
        }
    }
}
