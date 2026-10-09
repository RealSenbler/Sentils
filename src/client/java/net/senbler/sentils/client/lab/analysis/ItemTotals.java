package net.senbler.sentils.client.lab.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.senbler.sentils.client.lab.model.Session;
import net.senbler.sentils.client.lab.model.Session.Kind;
import net.senbler.sentils.client.lab.model.Session.Source;
import net.senbler.sentils.client.lab.price.PriceSnapshot;

/** Adds a session's observations up into one line per item. */
public final class ItemTotals {

    private ItemTotals() {}

    /**
     * Everything seen of one item over a session, or over one segment of it.
     * <p>
     * What counts is how much more or less of the item the player ended up with, in the
     * inventory and the sacks together. Plenty comes in only to go out again: logs that a
     * personal compactor turns into enchanted logs, an item that lands in the inventory and is
     * then moved to a sack. Those show up here as the same amount in and out and cancel, so what
     * is left is the enchanted logs in the sack and nothing else.
     */
    public static final class Total {
        public String id; // null if the item's SkyBlock id never came to light
        public String name;
        public long gained; // every arrival seen, in the inventory or the sacks
        public long lost; // every departure seen
        public long transferred; // moved between inventory and sacks: neither gained nor lost
        public long uncertain; // changed with a menu open: not counted either way
        public boolean manual; // some of the gain or loss was entered by hand

        public long net() {
            return this.gained - this.lost;
        }

        /** How many more of the item there were at the end than at the start, or 0 if there were fewer. */
        public long netGain() {
            return Math.max(0, net());
        }

        /** How many fewer of the item there were at the end than at the start, or 0 if there were more. */
        public long netLoss() {
            return Math.max(0, -net());
        }

        /** How much of the item came in only to go out again: compacted, used up or moved on. */
        public long passedThrough() {
            return Math.min(this.gained, this.lost);
        }
    }

    /** One line per item, biggest gain first. A segment of -1 means the whole session. */
    public static List<Total> of(Session session, int segment, PriceSnapshot catalog) {
        // A sack message names an item without its id. If the same name was also seen with an id,
        // in the inventory, the two are the same item; failing that, the price list may know the name.
        Map<String, String> idByName = new HashMap<>();
        for (Session.Bucket bucket : session.buckets) {
            if (bucket.itemId == null) continue;
            String name = bucket.item.toLowerCase(Locale.ROOT);
            String known = idByName.putIfAbsent(name, bucket.itemId);
            if (known != null && !known.equals(bucket.itemId)) idByName.put(name, ""); // two items, one name
        }

        Map<String, Total> totals = new LinkedHashMap<>();
        for (Session.Bucket bucket : session.buckets) {
            if (segment >= 0 && bucket.segment != segment) continue;
            String lower = bucket.item.toLowerCase(Locale.ROOT);
            String id = bucket.itemId;
            if (id == null) {
                id = idByName.get(lower);
                if (id != null && id.isEmpty()) id = null;
                if (id == null && !idByName.containsKey(lower) && catalog != null) id = catalog.idForName(bucket.item);
            }
            String key = id != null ? "id:" + id : "name:" + lower;
            Total total = totals.get(key);
            if (total == null) {
                total = new Total();
                total.id = id;
                total.name = bucket.item;
                totals.put(key, total);
            }
            switch (kind(bucket)) {
                case GAIN -> total.gained += bucket.amount;
                case LOSS -> total.lost += bucket.amount;
                case TRANSFER -> total.transferred += bucket.amount;
                case UNCERTAIN -> total.uncertain += bucket.amount;
            }
            if (Source.MANUAL.name().equals(bucket.source)) total.manual = true;
        }

        List<Total> list = new ArrayList<>(totals.values());
        list.sort(Comparator.comparingLong(Total::netGain).reversed()
                .thenComparing(total -> total.name));
        return list;
    }

    /** A kind this version does not know is treated as uncertain, so it is shown but not counted. */
    private static Kind kind(Session.Bucket bucket) {
        try {
            return Kind.valueOf(bucket.kind);
        } catch (RuntimeException e) {
            return Kind.UNCERTAIN;
        }
    }
}
