package net.senbler.sentils.client.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.fabricmc.loader.api.FabricLoader;

import net.senbler.sentils.client.Log;

public final class ConfigFiles {

    private ConfigFiles() {}

    private static final String FOLDER = "sentils";

    /**
     * Returns the file with the given name inside the mod's config folder, creating the folder
     * if needed. If the file does not exist yet, the first of the legacy names found directly
     * in the config directory (where the mod used to keep its files) is moved over.
     */
    public static Path resolve(String name, String... legacyNames) {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path file = configDir.resolve(FOLDER).resolve(name);
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                for (String legacyName : legacyNames) {
                    Path legacy = configDir.resolve(legacyName);
                    if (Files.exists(legacy)) {
                        Files.move(legacy, file);
                        break;
                    }
                }
            }
        } catch (IOException e) {
            Log.warn("Failed to set up config file " + name + ": " + e.getMessage());
        }
        return file;
    }
}
