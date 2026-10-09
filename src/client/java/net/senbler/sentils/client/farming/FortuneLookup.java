package net.senbler.sentils.client.farming;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.senbler.sentils.client.farming.HypixelApi.ApiException;

/** Fetches a player's SkyBlock profile and works out their farming fortune from it. */
public final class FortuneLookup {

    private FortuneLookup() {}

    private static final Gson GSON = new Gson();

    // The inventories that are searched for farming tools, armor and equipment.
    private static final String[] INVENTORIES = {
            "inv_contents", "inv_armor", "equipment_contents", "wardrobe_contents",
            "ender_chest_contents", "personal_vault_contents"};

    // The script holds one profile at a time; this counts loads, so a view asked for by a screen
    // showing an earlier profile can be told apart.
    private static final AtomicInteger loads = new AtomicInteger();

    /** A profile that is loaded into the fortune script and ready to be viewed crop by crop. */
    public record Profile(int load, String playerName, String profileName, List<String> profileNames,
                          FortuneData.Summary summary) {}

    /**
     * Loads a player's profile. The uuid may be null, in which case it is looked up from the name;
     * the profile name may be null for the profile the player last played on.
     */
    public static CompletableFuture<Profile> load(String name, String uuid, String profileName, String apiKey) {
        return FortuneEngine.submit(() -> {
            String playerName = name;
            String id = uuid;
            if (id == null) {
                JsonObject account = HypixelApi.account(name);
                id = account.get("id").getAsString();
                playerName = account.get("name").getAsString();
            }
            id = id.replace("-", "");

            JsonElement list = HypixelApi.profiles(id, apiKey).get("profiles");
            if (list == null || !list.isJsonArray() || list.getAsJsonArray().isEmpty()) {
                throw new ApiException(playerName + " has no SkyBlock profiles");
            }

            List<String> profileNames = new ArrayList<>();
            JsonObject profile = null;
            for (JsonElement element : list.getAsJsonArray()) {
                JsonObject candidate = element.getAsJsonObject();
                String cuteName = text(candidate, "cute_name");
                profileNames.add(cuteName);
                boolean wanted = profileName != null
                        ? cuteName.equalsIgnoreCase(profileName)
                        : candidate.has("selected") && candidate.get("selected").getAsBoolean();
                if (wanted) profile = candidate;
            }
            if (profile == null) {
                if (profileName != null) {
                    throw new ApiException(playerName + " has no profile named " + profileName
                            + " (they have " + String.join(", ", profileNames) + ")");
                }
                profile = list.getAsJsonArray().get(0).getAsJsonObject();
            }

            JsonObject members = profile.getAsJsonObject("members");
            JsonObject member = members != null ? members.getAsJsonObject(id) : null;
            if (member == null) throw new ApiException(playerName + " is not on that profile");

            JsonObject input = new JsonObject();
            input.add("member", member);
            JsonElement garden = HypixelApi.garden(text(profile, "profile_id"), apiKey).get("garden");
            if (garden != null && garden.isJsonObject()) input.add("garden", garden);
            input.add("items", items(member));
            input.add("accessories", accessories(member));

            FortuneData.Summary summary =
                    GSON.fromJson(FortuneEngine.call("load", GSON.toJson(input)), FortuneData.Summary.class);
            return new Profile(loads.incrementAndGet(), playerName, text(profile, "cute_name"), profileNames, summary);
        });
    }

    /** Works out everything shown for one crop of a loaded profile. */
    public static CompletableFuture<FortuneData.View> view(Profile profile, String cropId) {
        return FortuneEngine.submit(() -> {
            if (profile.load() != loads.get()) throw new ApiException("Another player was looked up since");
            return GSON.fromJson(FortuneEngine.call("view", cropId), FortuneData.View.class);
        });
    }

    /** Every item the player could be farming with, wherever it is stored. */
    private static JsonArray items(JsonObject member) {
        JsonArray items = new JsonArray();
        JsonObject inventory = object(member, "inventory");
        for (String name : INVENTORIES) {
            NbtItems.read(data(object(inventory, name)), items);
        }
        for (Map.Entry<String, JsonElement> backpack : object(inventory, "backpack_contents").entrySet()) {
            if (backpack.getValue().isJsonObject()) NbtItems.read(data(backpack.getValue().getAsJsonObject()), items);
        }
        // The wardrobe's armor and equipment sets, and the tools put away in the garden's farming
        // toolkit, are each stored one item at a time, nested by set or by crop.
        readNested(object(member, "loadout"), items);
        readNested(object(object(member, "garden_player_data"), "farming_toolkit"), items);
        return items;
    }

    /** Reads every encoded item found anywhere inside a part of the API's answer. */
    private static void readNested(JsonElement element, JsonArray items) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) readNested(child, items);
        } else if (element.isJsonObject()) {
            String data = data(element.getAsJsonObject());
            if (data != null) {
                NbtItems.read(data, items);
                return;
            }
            for (Map.Entry<String, JsonElement> child : element.getAsJsonObject().entrySet()) {
                readNested(child.getValue(), items);
            }
        }
    }

    /** Accessories only count from the accessory bag or the inventory itself. */
    private static JsonArray accessories(JsonObject member) {
        JsonArray items = new JsonArray();
        JsonObject inventory = object(member, "inventory");
        NbtItems.read(data(object(object(inventory, "bag_contents"), "talisman_bag")), items);
        NbtItems.read(data(object(inventory, "inv_contents")), items);
        return items;
    }

    /** The named object inside another, or an empty one if it is not there. */
    private static JsonObject object(JsonObject parent, String name) {
        JsonElement child = parent.get(name);
        return child != null && child.isJsonObject() ? child.getAsJsonObject() : new JsonObject();
    }

    /** The encoded contents of an inventory, or null if the API left it out. */
    private static String data(JsonObject inventory) {
        JsonElement data = inventory.get("data");
        return data != null && data.isJsonPrimitive() ? data.getAsString() : null;
    }

    private static String text(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }
}
