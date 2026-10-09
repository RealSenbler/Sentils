package net.senbler.sentils.client.stats;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.senbler.sentils.client.Log;
import net.senbler.sentils.client.config.ConfigFiles;

public final class ColdSettings {

    private ColdSettings() {}

    public static boolean coldTimer = true;
    public static boolean hideFrostOverlay = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = ConfigFiles.resolve("cold.json");

    private static class Data {
        boolean coldTimer = true;
        boolean hideFrostOverlay = false;
    }

    /** Call once from your ClientModInitializer. */
    public static void load() {
        if (!Files.exists(FILE)) return;
        try {
            Data d = GSON.fromJson(Files.readString(FILE), Data.class);
            if (d != null) {
                coldTimer = d.coldTimer;
                hideFrostOverlay = d.hideFrostOverlay;
            }
        } catch (IOException | RuntimeException e) {
            Log.warn("Failed to load cold settings: " + e.getMessage());
        }
    }

    public static void save() {
        Data d = new Data();
        d.coldTimer = coldTimer;
        d.hideFrostOverlay = hideFrostOverlay;
        try {
            Files.writeString(FILE, GSON.toJson(d));
        } catch (IOException e) {
            Log.warn("Failed to save cold settings: " + e.getMessage());
        }
    }
}
