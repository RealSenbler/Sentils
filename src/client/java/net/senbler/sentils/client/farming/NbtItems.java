package net.senbler.sentils.client.farming;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import net.senbler.sentils.client.Log;

/**
 * Reads the inventories in Hypixel's API, which are lists of items in Minecraft's NBT format,
 * gzipped and base64 encoded, and describes each item the way the fortune library expects.
 * The format is read by hand rather than with the game's own classes so that it keeps working
 * across game versions: Hypixel sends these in the layout of an old version regardless.
 */
public final class NbtItems {

    private NbtItems() {}

    private static final int END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6,
            BYTE_ARRAY = 7, STRING = 8, LIST = 9, COMPOUND = 10, INT_ARRAY = 11, LONG_ARRAY = 12;

    // Parts of an item's SkyBlock data that get their own place in the description.
    private static final Set<String> OWN_FIELDS = Set.of("id", "uuid", "enchantments", "gems", "attributes");

    /** Adds every SkyBlock item in an encoded inventory to the list; a broken inventory adds nothing. */
    public static void read(String encoded, JsonArray into) {
        if (encoded == null || encoded.isEmpty()) return;
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            Map<String, Object> root;
            try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes)))) {
                if (in.readByte() != COMPOUND) return;
                in.readUTF();
                root = readCompound(in);
            }
            if (root.get("i") instanceof List<?> items) {
                for (Object entry : items) {
                    JsonObject item = entry instanceof Map<?, ?> tag ? toItem(tag) : null;
                    if (item != null) into.add(item);
                }
            } else if (root.get("tag") instanceof Map<?, ?>) {
                // A single item on its own rather than in a list.
                JsonObject item = toItem(root);
                if (item != null) into.add(item);
            } else if (root.get("id") instanceof String) {
                // A tool in the garden's farming toolkit is stored as its SkyBlock data alone,
                // with no name or lore; the fortune script fills those in.
                into.add(describe(Map.of(), Map.of(), root));
            }
        } catch (IOException | RuntimeException e) {
            Log.warn("Failed to read an inventory: " + e);
        }
    }

    private static JsonObject toItem(Map<?, ?> tag) {
        if (!(tag.get("tag") instanceof Map<?, ?> data)) return null;
        if (!(data.get("ExtraAttributes") instanceof Map<?, ?> extra)) return null;
        if (!(extra.get("id") instanceof String)) return null;
        return describe(tag, data.get("display") instanceof Map<?, ?> display ? display : Map.of(), extra);
    }

    /** Describes an item from its stack, its display tag and its SkyBlock data. */
    private static JsonObject describe(Map<?, ?> tag, Map<?, ?> display, Map<?, ?> extra) {
        JsonObject item = new JsonObject();
        item.addProperty("id", tag.get("id") instanceof Number id ? id.intValue() : 0);
        item.addProperty("count", tag.get("Count") instanceof Number count ? count.intValue() : 0);
        item.addProperty("damage", tag.get("Damage") instanceof Number damage ? damage.intValue() : 0);
        item.addProperty("skyblockId", String.valueOf(extra.get("id")));
        if (extra.get("uuid") instanceof String uuid) item.addProperty("uuid", uuid);

        if (display.get("Name") instanceof String name) item.addProperty("name", name);
        if (display.get("Lore") instanceof List<?> lines) {
            JsonArray lore = new JsonArray();
            for (Object line : lines) {
                if (line instanceof String text) lore.add(text);
            }
            item.add("lore", lore);
        }

        if (extra.get("enchantments") instanceof Map<?, ?> enchantments) {
            JsonObject levels = new JsonObject();
            enchantments.forEach((name, level) -> {
                if (level instanceof Number number && number.intValue() > 0) {
                    levels.addProperty(String.valueOf(name), number.intValue());
                }
            });
            item.add("enchantments", levels);
        }

        JsonObject attributes = new JsonObject();
        extra.forEach((key, value) -> {
            if (OWN_FIELDS.contains(key)) return;
            if (value instanceof Double || value instanceof Float) {
                // Written out in full: the script reads these as text and does not expect "1.2E7".
                BigDecimal number = BigDecimal.valueOf(((Number) value).doubleValue());
                attributes.addProperty(String.valueOf(key), number.stripTrailingZeros().toPlainString());
            } else if (value instanceof String || value instanceof Number) {
                attributes.addProperty(String.valueOf(key), String.valueOf(value));
            }
        });
        item.add("attributes", attributes);

        if (extra.get("gems") instanceof Map<?, ?> gems) {
            JsonObject slots = new JsonObject();
            gems.forEach((slot, gem) -> {
                if ("unlocked_slots".equals(slot)) return;
                if (gem instanceof String quality) {
                    slots.addProperty(String.valueOf(slot), quality);
                } else if (gem instanceof Map<?, ?> details && details.get("quality") instanceof String quality) {
                    slots.addProperty(String.valueOf(slot), quality);
                }
            });
            // Slots that are unlocked but have no gem in them yet.
            if (gems.get("unlocked_slots") instanceof List<?> unlocked) {
                for (Object slot : unlocked) {
                    if (slot instanceof String name && !slots.has(name)) slots.add(name, JsonNull.INSTANCE);
                }
            }
            if (!slots.isEmpty()) item.add("gems", slots);
        }
        return item;
    }

    private static Map<String, Object> readCompound(DataInputStream in) throws IOException {
        Map<String, Object> compound = new LinkedHashMap<>();
        while (true) {
            int type = in.readByte();
            if (type == END) return compound;
            String name = in.readUTF();
            compound.put(name, readValue(in, type));
        }
    }

    private static Object readValue(DataInputStream in, int type) throws IOException {
        switch (type) {
            case BYTE: return in.readByte();
            case SHORT: return in.readShort();
            case INT: return in.readInt();
            case LONG: return in.readLong();
            case FLOAT: return in.readFloat();
            case DOUBLE: return in.readDouble();
            case BYTE_ARRAY: {
                byte[] bytes = new byte[in.readInt()];
                in.readFully(bytes);
                return bytes;
            }
            case STRING: return in.readUTF();
            case LIST: {
                int elementType = in.readByte();
                int length = in.readInt();
                List<Object> list = new ArrayList<>(Math.max(0, Math.min(length, 1024)));
                for (int i = 0; i < length; i++) {
                    list.add(readValue(in, elementType));
                }
                return list;
            }
            case COMPOUND: return readCompound(in);
            case INT_ARRAY: {
                int[] ints = new int[in.readInt()];
                for (int i = 0; i < ints.length; i++) ints[i] = in.readInt();
                return ints;
            }
            case LONG_ARRAY: {
                long[] longs = new long[in.readInt()];
                for (int i = 0; i < longs.length; i++) longs[i] = in.readLong();
                return longs;
            }
            default: throw new IOException("Unknown NBT tag type " + type);
        }
    }
}
