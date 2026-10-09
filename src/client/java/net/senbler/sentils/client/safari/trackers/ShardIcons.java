package net.senbler.sentils.client.safari.trackers;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import net.senbler.sentils.client.Log;
import net.senbler.sentils.client.config.ConfigFiles;

/**
 * The icons of shards, which are player heads. Neither the game nor Hypixel's API says what a
 * shard looks like before the player has one, so the head textures are looked up in the
 * community NotEnoughUpdates item repository on GitHub, one shard at a time as each is first
 * needed, and kept in the mod's cache folder so that nothing is downloaded twice.
 */
public final class ShardIcons {

    private ShardIcons() {}

    private static final String REPO = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/";
    private static final String TEXTURE_URL = "http://textures.minecraft.net/texture/";
    private static final Pattern TEXTURE_VALUE = Pattern.compile("Value:\\\\?\"([A-Za-z0-9+/=]+)");
    private static final Pattern TEXTURE_HASH = Pattern.compile("/texture/([0-9a-f]{1,64})\"");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STRING_MAP = new TypeToken<Map<String, String>>() {}.getType();
    // Shard name ("Treefrog") to the repository's name for its item file.
    private static final Path INDEX_FILE = ConfigFiles.resolve("cache/shard_index.json");
    // Shard name to the hash that names its head texture on Mojang's texture server.
    private static final Path TEXTURES_FILE = ConfigFiles.resolve("cache/shard_textures.json");

    private static final Map<String, String> textures = new ConcurrentHashMap<>();
    private static final Map<String, ItemStack> stacks = new HashMap<>(); // render thread only
    private static final Set<String> requested = new HashSet<>(); // looked up this session; render thread only

    // Only touched by the lookup thread.
    private static Map<String, String> index;
    private static boolean indexDownloaded;
    private static HttpClient http;
    private static ExecutorService lookups;

    /** Call once from your ClientModInitializer. */
    public static void load() {
        textures.putAll(readMap(TEXTURES_FILE));
    }

    /**
     * The head to draw for a shard, or an empty stack while it is not known. The first time an
     * unknown shard is asked for, it is looked up in the background; the icon appears once found.
     */
    public static ItemStack icon(String shard) {
        ItemStack stack = stacks.get(shard);
        if (stack != null) return stack;

        String hash = textures.get(shard);
        if (hash == null) {
            if (requested.add(shard)) lookUp(shard);
            return ItemStack.EMPTY;
        }
        stack = head(hash);
        stacks.put(shard, stack);
        return stack;
    }

    /** A player head with the skin the hash names. */
    private static ItemStack head(String hash) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + TEXTURE_URL + hash + "\"}}}";
        String value = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        // The same hash always gets the same id, so the game's skin cache recognises it.
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(hash.getBytes(StandardCharsets.UTF_8)), "",
                new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", value))));
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
        return stack;
    }

    private static void lookUp(String shard) {
        if (lookups == null) {
            lookups = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "Sentils shard icons");
                thread.setDaemon(true);
                return thread;
            });
        }
        lookups.execute(() -> {
            try {
                String hash = download(shard);
                if (hash == null) return;
                textures.put(shard, hash);
                Files.writeString(TEXTURES_FILE, GSON.toJson(new HashMap<>(textures), STRING_MAP));
            } catch (IOException | RuntimeException e) {
                Log.warn("Failed to get the icon for " + shard + " Shard: " + e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /** Returns the texture hash from the shard's item file in the repository, or null if it has none. */
    private static String download(String shard) throws IOException, InterruptedException {
        if (index == null) index = readMap(INDEX_FILE);
        // A shard missing from the saved index may be newer than it: fetch it again, once a session.
        if (!index.containsKey(shard) && !indexDownloaded) {
            indexDownloaded = true;
            index = downloadIndex();
            Files.writeString(INDEX_FILE, GSON.toJson(index, STRING_MAP));
        }
        String itemName = index.get(shard);
        if (itemName == null) return null;

        String file = URLEncoder.encode(itemName, StandardCharsets.UTF_8).replace("+", "%20");
        JsonObject item = JsonParser.parseString(get("items/" + file + ".json")).getAsJsonObject();
        JsonElement nbt = item.get("nbttag");
        if (nbt == null) return null;
        // Shards shown as an ordinary item rather than a head have no texture to find.
        Matcher value = TEXTURE_VALUE.matcher(nbt.getAsString());
        if (!value.find()) return null;
        String profile = new String(Base64.getDecoder().decode(value.group(1)), StandardCharsets.UTF_8);
        Matcher hash = TEXTURE_HASH.matcher(profile);
        return hash.find() ? hash.group(1) : null;
    }

    private static Map<String, String> downloadIndex() throws IOException, InterruptedException {
        Map<String, String> names = new HashMap<>();
        JsonObject root = JsonParser.parseString(get("constants/attribute_shards.json")).getAsJsonObject();
        for (JsonElement element : root.getAsJsonArray("attributes")) {
            JsonObject shard = element.getAsJsonObject();
            if (shard.has("displayName") && shard.has("internalName")) {
                names.put(shard.get("displayName").getAsString(), shard.get("internalName").getAsString());
            }
        }
        return names;
    }

    private static String get(String path) throws IOException, InterruptedException {
        if (http == null) http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(REPO + path)).timeout(Duration.ofSeconds(15)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode() + " for " + path);
        return response.body();
    }

    private static Map<String, String> readMap(Path file) {
        Map<String, String> result = new HashMap<>();
        if (!Files.exists(file)) return result;
        try {
            Map<String, String> saved = GSON.fromJson(Files.readString(file), STRING_MAP);
            if (saved != null) {
                saved.forEach((key, value) -> {
                    if (key != null && value != null) result.put(key, value);
                });
            }
        } catch (IOException | RuntimeException e) {
            Log.warn("Failed to read " + file.getFileName() + ": " + e.getMessage());
        }
        return result;
    }
}
