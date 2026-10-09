package net.senbler.sentils.client.mining;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.senbler.sentils.client.Log;
import net.senbler.sentils.client.config.ConfigFiles;

public final class MiningSettings {

    private MiningSettings() {}

    public static boolean metalDetectorSolver = true;
    public static boolean metalDetectorTracer = true;
    public static boolean automatonGlow = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = ConfigFiles.resolve("mining.json");

    private static class Data {
        boolean metalDetectorSolver = true;
        boolean metalDetectorTracer = true;
        boolean automatonGlow = false;
    }

    /** Call once from your ClientModInitializer. */
    public static void load() {
        if (!Files.exists(FILE)) return;
        try {
            Data d = GSON.fromJson(Files.readString(FILE), Data.class);
            if (d != null) {
                metalDetectorSolver = d.metalDetectorSolver;
                metalDetectorTracer = d.metalDetectorTracer;
                automatonGlow = d.automatonGlow;
            }
        } catch (IOException | RuntimeException e) {
            Log.warn("Failed to load mining settings: " + e.getMessage());
        }
    }

    public static void save() {
        Data d = new Data();
        d.metalDetectorSolver = metalDetectorSolver;
        d.metalDetectorTracer = metalDetectorTracer;
        d.automatonGlow = automatonGlow;
        try {
            Files.writeString(FILE, GSON.toJson(d));
        } catch (IOException e) {
            Log.warn("Failed to save mining settings: " + e.getMessage());
        }
    }
}
