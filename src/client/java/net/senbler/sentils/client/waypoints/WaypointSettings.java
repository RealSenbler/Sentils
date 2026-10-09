package net.senbler.sentils.client.waypoints;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.senbler.sentils.client.Log;
import net.senbler.sentils.client.config.ConfigFiles;

public final class WaypointSettings {

    private WaypointSettings() {}

    public static final int MIN_DURATION_SECONDS = 1;
    public static final int MAX_DURATION_SECONDS = 3600;

    public static boolean renderWaypoints = true;
    public static boolean renderTracer = false;
    public static int durationSeconds = 10; // how long a waypoint stays visible
    public static boolean fromAllChat = true;
    public static boolean fromPartyChat = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE =
            ConfigFiles.resolve("waypoints.json", "sentils_waypoints.json", "chatnotificationsmod_waypoints.json");

    private static class Data {
        boolean renderWaypoints = true;
        boolean renderTracer = false;
        int durationSeconds = 10;
        boolean fromAllChat = true;
        boolean fromPartyChat = true;
    }

    /** Call once from your ClientModInitializer. */
    public static void load() {
        if (!Files.exists(FILE)) return;
        try {
            Data d = GSON.fromJson(Files.readString(FILE), Data.class);
            if (d != null) {
                renderWaypoints = d.renderWaypoints;
                renderTracer = d.renderTracer;
                durationSeconds = Math.clamp(d.durationSeconds, MIN_DURATION_SECONDS, MAX_DURATION_SECONDS);
                fromAllChat = d.fromAllChat;
                fromPartyChat = d.fromPartyChat;
            }
        } catch (IOException | RuntimeException e) {
            Log.warn("Failed to load waypoint settings: " + e.getMessage());
        }
    }

    public static void save() {
        Data d = new Data();
        d.renderWaypoints = renderWaypoints;
        d.renderTracer = renderTracer;
        d.durationSeconds = durationSeconds;
        d.fromAllChat = fromAllChat;
        d.fromPartyChat = fromPartyChat;
        try {
            Files.writeString(FILE, GSON.toJson(d));
        } catch (IOException e) {
            Log.warn("Failed to save waypoint settings: " + e.getMessage());
        }
    }
}
