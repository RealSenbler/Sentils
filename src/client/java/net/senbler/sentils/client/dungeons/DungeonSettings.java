package net.senbler.sentils.client.dungeons;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.senbler.sentils.client.Log;
import net.senbler.sentils.client.config.ConfigFiles;

public final class DungeonSettings {

    private DungeonSettings() {}

    public static boolean highlightCrackedBricks = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = ConfigFiles.resolve("dungeons.json");

    private static class Data {
        boolean highlightCrackedBricks = false;
    }

    /** Call once from your ClientModInitializer. */
    public static void load() {
        if (!Files.exists(FILE)) return;
        try {
            Data d = GSON.fromJson(Files.readString(FILE), Data.class);
            if (d != null) {
                highlightCrackedBricks = d.highlightCrackedBricks;
            }
        } catch (IOException | RuntimeException e) {
            Log.warn("Failed to load dungeon settings: " + e.getMessage());
        }
    }

    public static void save() {
        Data d = new Data();
        d.highlightCrackedBricks = highlightCrackedBricks;
        try {
            Files.writeString(FILE, GSON.toJson(d));
        } catch (IOException e) {
            Log.warn("Failed to save dungeon settings: " + e.getMessage());
        }
    }
}
