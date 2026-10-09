package net.senbler.sentils.client.lab.price;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Reads Hypixel's two public price lists into a snapshot.
 * <p>
 * The Bazaar list gives, for each product, its standing orders on both sides. Going by the
 * API's documentation and the example in it, "sell_summary" is the buy orders, highest first:
 * what a player selling instantly is paid. "buy_summary" is the sell offers, lowest first: what
 * a player buying instantly pays, and so what a new sell order has to undercut. The names are
 * from the point of view of the player clicking the button, which is easy to get backwards.
 */
public final class PriceParser {

    private PriceParser() {}

    /** A list that is not what the API documents, which is better refused than half understood. */
    public static final class BadResponse extends Exception {
        public BadResponse(String message) {
            super(message);
        }
    }

    /**
     * Adds the Bazaar's prices to the snapshot. Items already in it keep their name and NPC price.
     */
    public static void readBazaar(String json, PriceSnapshot into) throws BadResponse {
        JsonObject root = object(json, "The Bazaar list");
        if (!(root.get("products") instanceof JsonObject products)) {
            throw new BadResponse("The Bazaar list has no products in it");
        }
        // Old Bazaar prices must not linger on an item that has since left the Bazaar.
        for (PriceSnapshot.Entry entry : into.items.values()) {
            entry.instantSell = null;
            entry.sellOrder = null;
        }
        for (var product : products.entrySet()) {
            if (!(product.getValue() instanceof JsonObject data)) continue;
            PriceSnapshot.Entry entry = entry(into, product.getKey());
            entry.instantSell = topPrice(data.get("sell_summary"));
            entry.sellOrder = topPrice(data.get("buy_summary"));
        }
    }

    /** Adds every item's name and NPC sell price to the snapshot. */
    public static void readItems(String json, PriceSnapshot into) throws BadResponse {
        JsonObject root = object(json, "The item list");
        if (!(root.get("items") instanceof JsonArray items)) throw new BadResponse("The item list has no items in it");
        for (JsonElement element : items) {
            if (!(element instanceof JsonObject item) || !isText(item.get("id"))) continue;
            PriceSnapshot.Entry entry = entry(into, item.get("id").getAsString());
            if (isText(item.get("name"))) entry.name = item.get("name").getAsString();
            entry.npc = number(item.get("npc_sell_price"));
        }
    }

    private static JsonObject object(String json, String what) throws BadResponse {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            throw new BadResponse(what + " could not be read");
        }
        if (!(parsed instanceof JsonObject root)) throw new BadResponse(what + " could not be read");
        JsonElement success = root.get("success");
        if (success != null && success.isJsonPrimitive() && !success.getAsBoolean()) {
            throw new BadResponse(what + " came back as a failure");
        }
        return root;
    }

    private static PriceSnapshot.Entry entry(PriceSnapshot snapshot, String id) {
        return snapshot.items.computeIfAbsent(id, key -> {
            PriceSnapshot.Entry entry = new PriceSnapshot.Entry();
            entry.id = key;
            entry.name = key;
            return entry;
        });
    }

    /** The price of the first order in a summary, which is the best one, or null if there are none. */
    private static Double topPrice(JsonElement summary) {
        if (!(summary instanceof JsonArray orders) || orders.isEmpty()) return null;
        if (!(orders.get(0) instanceof JsonObject best)) return null;
        return number(best.get("pricePerUnit"));
    }

    private static Double number(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return null;
        double value = element.getAsDouble();
        return Double.isFinite(value) && value >= 0 ? value : null;
    }

    private static boolean isText(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
    }
}
