package net.senbler.sentils.client.lab.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Works out what changed between two counts of an inventory. */
public final class InventoryDelta {

    private InventoryDelta() {}

    /** How many of one kind of item an inventory holds. */
    public record Count(String name, String id, long amount) {}

    /** A count of an inventory, by a key that is the same for two stacks of the same item. */
    public static final class Snapshot {
        private final Map<String, Count> counts = new LinkedHashMap<>();

        public void add(String name, String id, long amount) {
            if (amount <= 0 || name == null || name.isBlank()) return;
            String key = id != null ? "id:" + id : "name:" + name;
            Count held = this.counts.get(key);
            this.counts.put(key, new Count(name, id, (held != null ? held.amount() : 0) + amount));
        }

        public boolean isEmpty() {
            return this.counts.isEmpty();
        }
    }

    /** Every item there is now more or less of than before. An item with the same count is left out. */
    public static List<ItemChange> between(Snapshot before, Snapshot after) {
        List<ItemChange> changes = new ArrayList<>();
        for (Map.Entry<String, Count> entry : after.counts.entrySet()) {
            Count was = before.counts.get(entry.getKey());
            long change = entry.getValue().amount() - (was != null ? was.amount() : 0);
            if (change != 0) changes.add(new ItemChange(entry.getValue().name(), entry.getValue().id(), change));
        }
        for (Map.Entry<String, Count> entry : before.counts.entrySet()) {
            if (!after.counts.containsKey(entry.getKey())) {
                Count was = entry.getValue();
                changes.add(new ItemChange(was.name(), was.id(), -was.amount()));
            }
        }
        return changes;
    }
}
