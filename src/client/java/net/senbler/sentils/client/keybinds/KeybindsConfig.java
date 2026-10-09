package net.senbler.sentils.client.keybinds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.senbler.sentils.client.Log;
import net.senbler.sentils.client.config.ConfigFiles;

public final class KeybindsConfig {

    private KeybindsConfig() {}

    public static final int UNBOUND = -1;

    /** A key that sends a command (starting with a slash) or a chat message when pressed. */
    public static class Binding {
        public int key = UNBOUND; // GLFW key code
        public String command = "";

        public Binding() {}

        public Binding(int key, String command) {
            this.key = key;
            this.command = command;
        }
    }

    public static List<Binding> bindings = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = ConfigFiles.resolve("keybinds.json", "sentils_keybinds.json");

    /** Call once from your ClientModInitializer. */
    public static void load() {
        if (!Files.exists(FILE)) return;
        try {
            Type type = new TypeToken<ArrayList<Binding>>() {}.getType();
            List<Binding> loaded = GSON.fromJson(Files.readString(FILE), type);
            if (loaded != null) {
                loaded.removeIf(binding -> binding == null || binding.command == null);
                bindings = loaded;
            }
        } catch (IOException | RuntimeException e) {
            Log.warn("Failed to load keybinds: " + e.getMessage());
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(bindings));
        } catch (IOException e) {
            Log.warn("Failed to save keybinds: " + e.getMessage());
        }
    }
}
