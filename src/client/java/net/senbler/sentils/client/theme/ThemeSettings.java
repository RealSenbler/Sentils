package net.senbler.sentils.client.theme;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.senbler.sentils.client.Log;
import net.senbler.sentils.client.config.ConfigFiles;
import net.senbler.sentils.client.gui.Theme;

/** The colors the menus and HUD are drawn in. Change a color, then call apply() to use it. */
public final class ThemeSettings {

    private ThemeSettings() {}

    public static int accent = Theme.DEFAULT_ACCENT;
    public static int background = Theme.DEFAULT_BACKGROUND;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = ConfigFiles.resolve("theme.json");

    private static class Data {
        String accent;
        String background;
    }

    /** Call once from your ClientModInitializer. */
    public static void load() {
        if (!Files.exists(FILE)) return;
        try {
            Data d = GSON.fromJson(Files.readString(FILE), Data.class);
            if (d != null) {
                accent = parse(d.accent, accent);
                background = parse(d.background, background);
            }
        } catch (IOException | RuntimeException e) {
            Log.warn("Failed to load theme settings: " + e.getMessage());
        }
        apply();
    }

    private static int parse(String hex, int fallback) {
        int color = hex != null ? Theme.parseHex(hex) : -1;
        return color >= 0 ? color : fallback;
    }

    /** Recolors the menus and HUD with the current settings. */
    public static void apply() {
        Theme.apply(accent, background);
    }

    public static void reset() {
        accent = Theme.DEFAULT_ACCENT;
        background = Theme.DEFAULT_BACKGROUND;
        apply();
    }

    public static void save() {
        Data d = new Data();
        d.accent = Theme.hex(accent);
        d.background = Theme.hex(background);
        try {
            Files.writeString(FILE, GSON.toJson(d));
        } catch (IOException e) {
            Log.warn("Failed to save theme settings: " + e.getMessage());
        }
    }
}
