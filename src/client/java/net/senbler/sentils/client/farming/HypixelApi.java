package net.senbler.sentils.client.farming;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** The web requests behind the fortune checker: a player's id from Mojang, their SkyBlock data from Hypixel. */
public final class HypixelApi {

    private HypixelApi() {}

    private static final String MOJANG = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String HYPIXEL = "https://api.hypixel.net/v2/skyblock/";

    private static HttpClient http;

    /** A request that failed in a way worth telling the player about; the message is shown as is. */
    public static final class ApiException extends IOException {
        public ApiException(String message) {
            super(message);
        }
    }

    /** Returns Mojang's record for a player name, with "id" (no dashes) and "name" (correct capitals). */
    public static JsonObject account(String name) throws IOException, InterruptedException {
        HttpResponse<String> response = send(MOJANG + URLEncoder.encode(name, StandardCharsets.UTF_8), null);
        // Mojang answers 404 (or an empty 204) for a name nobody has, and 400 for one nobody could have.
        if (response.statusCode() == 404 || response.statusCode() == 204 || response.statusCode() == 400) {
            throw new ApiException("There is no player named " + name);
        }
        if (response.statusCode() == 429) throw new ApiException("Too many name lookups, try again in a minute");
        if (response.statusCode() != 200) throw new ApiException("Mojang's name lookup failed (HTTP " + response.statusCode() + ")");
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    /** Every SkyBlock profile the player is on. */
    public static JsonObject profiles(String uuid, String apiKey) throws IOException, InterruptedException {
        return hypixel("profiles?uuid=" + uuid, apiKey);
    }

    /** The garden of a profile; "garden" is missing from the answer if the profile has none. */
    public static JsonObject garden(String profileId, String apiKey) throws IOException, InterruptedException {
        return hypixel("garden?profile=" + profileId, apiKey);
    }

    private static JsonObject hypixel(String path, String apiKey) throws IOException, InterruptedException {
        HttpResponse<String> response = send(HYPIXEL + path, apiKey);
        switch (response.statusCode()) {
            case 200:
                return JsonParser.parseString(response.body()).getAsJsonObject();
            case 403:
                throw new ApiException("Hypixel rejected the API key. Developer keys expire after a few days");
            case 404:
                // The garden endpoint answers this for a profile that has never visited the garden.
                return new JsonObject();
            case 429:
                throw new ApiException("Hypixel's API limit was reached, try again in a few minutes");
            default:
                throw new ApiException("Hypixel's API failed (HTTP " + response.statusCode() + cause(response.body()) + ")");
        }
    }

    /** Hypixel's own explanation of an error, if the answer has one. */
    private static String cause(String body) {
        try {
            JsonElement cause = JsonParser.parseString(body).getAsJsonObject().get("cause");
            return cause != null ? ": " + cause.getAsString() : "";
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static HttpResponse<String> send(String url, String apiKey) throws IOException, InterruptedException {
        if (http == null) http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30));
        if (apiKey != null) request.header("API-Key", apiKey);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
