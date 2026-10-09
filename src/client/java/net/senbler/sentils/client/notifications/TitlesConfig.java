package net.senbler.sentils.client.notifications;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import net.senbler.sentils.client.Log;
import net.senbler.sentils.client.config.ConfigFiles;

public class TitlesConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            ConfigFiles.resolve("titles.json", "sentils_titles.json", "chatnotifications.json");

    public static Map<String, String> load() {
        if (!Files.exists(CONFIG_PATH)) {
            return new HashMap<>();
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<HashMap<String, String>>() {}.getType();
            Map<String, String> loaded = GSON.fromJson(reader, type);
            return loaded != null ? loaded : new HashMap<>();
        } catch (IOException e) {
            Log.warn("Failed to load config: " + e.getMessage());
            return new HashMap<>();
        }
    }

    public static void save(Map<String, String> titles) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(titles, writer);
            }
        } catch (IOException e) {
            Log.warn("Failed to save config: " + e.getMessage());
        }
    }
}