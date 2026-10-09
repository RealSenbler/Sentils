package net.senbler.sentils.client.safari.trackers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;

import net.senbler.sentils.client.safari.SafariSettings;
import net.senbler.sentils.client.skyblock.SkyblockLocation;

/**
 * Counts the shards caught during a visit to the Critter Safari, read from chat. The player's
 * own catches and everyone else's both count, since a catch gives its shard to the whole party,
 * and so do shards the player picks up off the ground. A second count, over every visit, is
 * kept in a file. Lines counted:
 * <pre>
 * FLOOR DROP! You found Treefrog Shard on the ground!
 * CAPTURE! You caught a Foxtrot and gained a Foxtrot Shard!
 * CAPTURE! You caught an Areita and gained 2x Areita Shard!
 * CAPTURE! You found Hideyho, and as a reward he gave you a Hideyho Shard!
 * CAPTURE! You caught a SPARKLING Rockmite and received a Rainbow Feather and 3x Rockmite Shard!
 * LOOT SHARE! You received a Foxtrot Shard from Steve catching a Foxtrot!
 * LOOT SHARE! You received 2x Hideyho Shard from Steve finding Hideyho!
 * LOOT SHARE! You received a Rainbow Feather and 3x Rockmite Shard from Steve catching a SPARKLING Rockmite!
 * </pre>
 */
public final class SafariShardTracker {

    private SafariShardTracker() {}

    /** A kind of shard and how many of it have been caught. */
    public record Entry(String shard, int count) {}

    private static final String ISLAND = "safari";
    private static final Pattern FORMATTING_CODES = Pattern.compile("§.");
    // Anchored on the prefix so that a player quoting one of these lines in chat is not counted.
    // Group 1 is the amount when there is more than one shard, group 2 the shard's name.
    private static final Pattern CATCH = Pattern.compile(
            "^(?:CAPTURE|LOOT SHARE)! .*?\\b(?:gained|gave you|received) (?:a Rainbow Feather and )?"
                    + "(?:an? |(\\d[\\d,]*)x )?(.+?) Shard\\b");
    // Floor drops can be other things too; only the ones that are a shard match. Same groups as CATCH.
    private static final Pattern FLOOR_DROP = Pattern.compile(
            "^FLOOR DROP! You found (?:an? |(\\d[\\d,]*)x )?(.+?) Shard\\b");
    private static final int CHECK_TICKS = 20;

    /** Counts by shard name, with the sorted list and total the HUD reads kept ready. */
    private static final class Tally {
        final Map<String, Integer> counts = new HashMap<>();
        List<Entry> entries = List.of(); // counts, most caught first
        int total;

        void refresh() {
            List<Entry> sorted = new ArrayList<>();
            int sum = 0;
            for (Map.Entry<String, Integer> count : this.counts.entrySet()) {
                sorted.add(new Entry(count.getKey(), count.getValue()));
                sum += count.getValue();
            }
            sorted.sort(Comparator.comparingInt(Entry::count).reversed().thenComparing(Entry::shard));
            this.entries = sorted;
            this.total = sum;
        }
    }

    private static final Tally visit = new Tally(); // this visit to the Safari
    private static final Tally allTime = new Tally(); // every visit, kept in a file
    private static boolean inSafari;
    private static boolean visiting; // false once the player has been seen on some other island
    private static int ticksUntilCheck;

    /** Call once from your ClientModInitializer. */
    public static void init() {
        allTime.counts.putAll(SafariShardTotals.load());
        allTime.refresh();
        ClientTickEvents.END_CLIENT_TICK.register(SafariShardTracker::tick);
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) onMessage(message.getString());
        });
    }

    public static boolean isInSafari() {
        return inSafari;
    }

    /** The tally the player has chosen to see. */
    private static Tally chosen() {
        return SafariSettings.shardTrackerAllTime ? allTime : visit;
    }

    /** The shards caught, this visit or over all of them as the setting says, most caught first. */
    public static List<Entry> entries() {
        return chosen().entries;
    }

    /** How many shards of any kind have been caught, this visit or over all of them. */
    public static int total() {
        return chosen().total;
    }

    private static void tick(Minecraft mc) {
        if (--ticksUntilCheck > 0) return;
        ticksUntilCheck = CHECK_TICKS;

        String island = SkyblockLocation.getIsland();
        inSafari = island != null && island.toLowerCase(Locale.ROOT).contains(ISLAND);
        // The island is unknown for a moment while changing servers, so only a named island that
        // is not the Safari ends a visit. The visit's counts are kept until the next one starts.
        if (inSafari && !visiting) {
            visiting = true;
            visit.counts.clear();
            visit.refresh();
        } else if (island != null && !inSafari) {
            visiting = false;
        }
    }

    private static void onMessage(String message) {
        if (!inSafari) return;
        String line = FORMATTING_CODES.matcher(message).replaceAll("").trim();
        Matcher m = CATCH.matcher(line);
        if (!m.find()) {
            m = FLOOR_DROP.matcher(line);
            if (!m.find()) return;
        }

        int amount = 1;
        if (m.group(1) != null) {
            try {
                amount = Integer.parseInt(m.group(1).replace(",", ""));
            } catch (NumberFormatException ignored) {
            }
        }
        for (Tally tally : List.of(visit, allTime)) {
            tally.counts.merge(m.group(2), amount, Integer::sum);
            tally.refresh();
        }
        SafariShardTotals.save(allTime.counts);
    }
}
