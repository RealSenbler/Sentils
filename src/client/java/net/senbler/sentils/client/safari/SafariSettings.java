package net.senbler.sentils.client.safari;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.senbler.sentils.client.Log;
import net.senbler.sentils.client.config.ConfigFiles;

public final class SafariSettings {

    private SafariSettings() {}

    public static boolean sparklingAlert = true;
    public static boolean shardTracker = false;
    public static boolean shardTrackerSafariOnly = true; // when off, the tracker shows on every island
    // Count every visit rather than only this one. Switched with the button on the tracker itself.
    public static boolean shardTrackerAllTime = false;
    public static boolean announceHotspot = false; // only ever sends while in a party

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = ConfigFiles.resolve("safari.json", "sentils_safari.json");

    private static class Data {
        boolean sparklingAlert = true;
        boolean shardTracker = false;
        boolean shardTrackerSafariOnly = true;
        boolean shardTrackerAllTime = false;
        boolean announceHotspot = false;
    }

    /** Call once from your ClientModInitializer. */
    public static void load() {
        if (!Files.exists(FILE)) return;
        try {
            Data d = GSON.fromJson(Files.readString(FILE), Data.class);
            if (d != null) {
                sparklingAlert = d.sparklingAlert;
                shardTracker = d.shardTracker;
                shardTrackerSafariOnly = d.shardTrackerSafariOnly;
                shardTrackerAllTime = d.shardTrackerAllTime;
                announceHotspot = d.announceHotspot;
            }
        } catch (IOException | RuntimeException e) {
            Log.warn("Failed to load safari settings: " + e.getMessage());
        }
    }

    public static void save() {
        Data d = new Data();
        d.sparklingAlert = sparklingAlert;
        d.shardTracker = shardTracker;
        d.shardTrackerSafariOnly = shardTrackerSafariOnly;
        d.shardTrackerAllTime = shardTrackerAllTime;
        d.announceHotspot = announceHotspot;
        try {
            Files.writeString(FILE, GSON.toJson(d));
        } catch (IOException e) {
            Log.warn("Failed to save safari settings: " + e.getMessage());
        }
    }
}
