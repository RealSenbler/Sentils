package net.senbler.sentils.client.safari.trackers;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.senbler.sentils.client.Log;
import net.senbler.sentils.client.config.ConfigFiles;

/** The file keeping how many of each shard has been caught in the Critter Safari over all visits. */
public final class SafariShardTotals {

    private SafariShardTotals() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = ConfigFiles.resolve("safari_shards.json");
    private static final Type TYPE = new TypeToken<Map<String, Integer>>() {}.getType();

    /** Returns the saved counts by shard name; empty if there are none yet. */
    public static Map<String, Integer> load() {
        Map<String, Integer> counts = new HashMap<>();
        if (!Files.exists(FILE)) return counts;
        try {
            Map<String, Integer> saved = GSON.fromJson(Files.readString(FILE), TYPE);
            if (saved != null) {
                saved.forEach((shard, count) -> {
                    if (shard != null && count != null && count > 0) counts.put(shard, count);
                });
            }
        } catch (IOException | RuntimeException e) {
            Log.warn("Failed to load safari shard totals: " + e.getMessage());
        }
        return counts;
    }

    public static void save(Map<String, Integer> counts) {
        try {
            Files.writeString(FILE, GSON.toJson(counts, TYPE));
        } catch (IOException e) {
            Log.warn("Failed to save safari shard totals: " + e.getMessage());
        }
    }
}
