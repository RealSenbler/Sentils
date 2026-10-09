package net.senbler.sentils.client.lab.price;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Prices as they stood at one moment, by SkyBlock item id. A session keeps the snapshot it was
 * valued with, cut down to its own items, so its report comes out the same however prices move.
 */
public class PriceSnapshot {

    public long retrievedMillis;
    public String source = "";
    public Map<String, Entry> items = new LinkedHashMap<>();

    // Item ids by name in lower case, built when first needed and not saved with the prices.
    private transient Map<String, String> idsByName;
    private transient int indexedCount;

    public static class Entry {
        public String id;
        public String name;
        /**
         * The highest standing buy order on the Bazaar: what selling one instantly would pay,
         * before tax and while that order lasts. Null if the item is not on the Bazaar.
         */
        public Double instantSell;
        /**
         * The lowest standing sell offer on the Bazaar: about what a sell order has to ask to
         * be next in line, before tax. Nothing says such an order will be filled.
         */
        public Double sellOrder;
        /** What an NPC shop pays for one, or null if it cannot be sold to one. */
        public Double npc;
    }

    public Entry get(String id) {
        return id == null ? null : this.items.get(id);
    }

    /** The id of the item with this name, whatever its case, or null if no item or more than one has it. */
    public String idForName(String name) {
        if (name == null) return null;
        // Looked up for every observation of every session on screen, against thousands of
        // items, so the names are indexed once rather than gone through each time.
        if (this.idsByName == null || this.indexedCount != this.items.size()) {
            Map<String, String> index = new HashMap<>();
            for (Entry entry : this.items.values()) {
                if (entry.name == null) continue;
                // Two items sharing a name leave it pointing at neither.
                index.merge(entry.name.toLowerCase(Locale.ROOT), entry.id, (first, second) -> "");
            }
            this.idsByName = index;
            this.indexedCount = this.items.size();
        }
        String id = this.idsByName.get(name.trim().toLowerCase(Locale.ROOT));
        return id == null || id.isEmpty() ? null : id;
    }

    /** A snapshot with only the given items, from the same source at the same time. */
    public PriceSnapshot subset(Collection<String> ids) {
        PriceSnapshot subset = new PriceSnapshot();
        subset.retrievedMillis = this.retrievedMillis;
        subset.source = this.source;
        for (String id : ids) {
            Entry entry = this.items.get(id);
            if (entry != null) subset.items.put(id, entry);
        }
        return subset;
    }

    public boolean isOlderThan(long millis, long now) {
        return now - this.retrievedMillis > millis;
    }
}
