package net.senbler.sentils.client.lab.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.senbler.sentils.client.lab.model.Activity;
import net.senbler.sentils.client.lab.model.Counters;
import net.senbler.sentils.client.lab.model.Session;

/**
 * What a session's counters say about the activity itself, set out for showing: sea creatures
 * for fishing, bosses for slayer. An activity has one count picked out as the one it is scored
 * in, which is what the activity metric goes by.
 * <p>
 * A session shows the figures of whichever activities something was counted for, as well as its
 * own, since a sea creature fished up during a session of another kind was still fished up.
 */
public final class ActivityStats {

    private ActivityStats() {}

    /** PLAIN is a number that is already a rate or an average, shown as it is. */
    public enum Unit { COUNT, COINS, DURATION, PERCENT, PLAIN }

    /** One figure. A part is one of the kinds that make up the figure above it, such as one sea creature. */
    public record Row(String label, double total, Unit unit, boolean part) {
        /** Whether an hourly rate means anything for it: not for an average, of a length of time or a share. */
        public boolean hasRate() {
            return this.unit == Unit.COUNT || this.unit == Unit.COINS;
        }
    }

    public record Group(String title, List<Row> rows) {}

    /** The counter the activity is scored in, or null if nothing of its own is counted for it yet. */
    public static String primaryKey(Activity activity) {
        return switch (activity) {
            case FISHING -> Counters.FISHING_SEA_CREATURES;
            case SLAYER -> Counters.SLAYER_COMPLETED;
            case FORAGING -> Counters.FORAGING_TREES;
            case FARMING -> Counters.FARMING_BLOCKS;
            case DUNGEONS -> Counters.DUNGEONS_RUNS;
            case MINING -> Counters.MINING_POWDER;
            default -> null;
        };
    }

    /** A few letters for the activity's own rate, such as "Bosses/h"; the metric's usual ones if it has none. */
    public static String primaryLabel(Activity activity) {
        return switch (activity) {
            case FISHING -> "Creatures/h";
            case SLAYER -> "Bosses/h";
            case FORAGING -> "Trees/h";
            case FARMING -> "Blocks/h";
            case DUNGEONS -> "Runs/h";
            case MINING -> "Powder/h";
            default -> Metric.ACTIVITY_PER_HOUR.shortLabel;
        };
    }

    /**
     * The activity's own rate as it reads best on its own, on the HUD and the session's tile.
     * Blocks broken while farming are given by the second, as farmers reckon them; everything
     * else by the hour. Comparisons go by the hour throughout, where only the order matters.
     */
    public static SessionReport.Measure liveRate(SessionReport report) {
        SessionReport.Measure perHour = report.rate(Metric.ACTIVITY_PER_HOUR);
        if (report.activity() != Activity.FARMING || !perHour.isAvailable()) return perHour;
        return new SessionReport.Measure(perHour.value() / 3600, perHour.provenance(), perHour.note());
    }

    /** The few letters that go with liveRate. */
    public static String liveLabel(Activity activity) {
        return activity == Activity.FARMING ? "BPS" : primaryLabel(activity);
    }

    /** The metric's few letters as they should read for a session of this activity. */
    public static String shortLabel(Metric metric, Activity activity) {
        return metric == Metric.ACTIVITY_PER_HOUR ? primaryLabel(activity) : metric.shortLabel;
    }

    public static double count(Session session, String key) {
        Double count = session.counters.get(key);
        return count != null ? count : 0;
    }

    /** The groups of figures to show for a session, its own activity's first. */
    public static List<Group> of(Session session) {
        return of(session, 0);
    }

    /** The same, given how long the session was tracked for, which a figure by the second needs. */
    public static List<Group> of(Session session, long trackedMillis) {
        Activity activity = session.activityType();
        List<Group> groups = new ArrayList<>();
        if (activity == Activity.FARMING || any(session, "farming.")) groups.add(farming(session, trackedMillis));
        if (activity == Activity.DUNGEONS || any(session, "dungeons.")) groups.add(dungeons(session));
        if (activity == Activity.MINING || any(session, "mining.")) groups.add(mining(session));
        if (activity == Activity.FISHING || any(session, "fishing.")) groups.add(fishing(session));
        if (activity == Activity.SLAYER || any(session, "slayer.")) {
            groups.add(activity == Activity.SLAYER ? 0 : groups.size(), slayer(session));
        }
        if (activity == Activity.FORAGING || any(session, "foraging.")) {
            groups.add(activity == Activity.FORAGING ? 0 : groups.size(), foraging(session));
        }
        return groups;
    }

    private static Group farming(Session session, long trackedMillis) {
        List<Row> rows = new ArrayList<>();
        double blocks = count(session, Counters.FARMING_BLOCKS);
        rows.add(new Row("Blocks broken", blocks, Unit.COUNT, false));
        if (blocks > 0 && trackedMillis > 0) {
            rows.add(new Row("Blocks per second", blocks / (trackedMillis / 1000.0), Unit.PLAIN, false));
        }
        optional(rows, "Crops harvested", count(session, Counters.FARMING_CROPS), Unit.COUNT);
        optional(rows, "Pests spawned", count(session, Counters.FARMING_PESTS_SPAWNED), Unit.COUNT);
        optional(rows, "Pests killed", count(session, Counters.FARMING_PESTS), Unit.COUNT);
        parts(session, Counters.FARMING_PEST, rows);
        optional(rows, "Rare crops", count(session, Counters.FARMING_RARE_CROPS), Unit.COUNT);
        parts(session, Counters.FARMING_RARE_CROP, rows);
        optional(rows, "Pest rare drops", count(session, Counters.FARMING_PEST_DROPS), Unit.COUNT);
        parts(session, Counters.FARMING_PEST_DROP, rows);
        optional(rows, "Visitors served", count(session, Counters.FARMING_VISITORS), Unit.COUNT);
        optional(rows, "Copper", count(session, Counters.FARMING_COPPER), Unit.COUNT);
        optional(rows, "Garden XP", count(session, Counters.FARMING_GARDEN_XP), Unit.COUNT);
        optional(rows, "Sowdust", count(session, Counters.FARMING_SOWDUST), Unit.COUNT);
        return new Group("FARMING", rows);
    }

    private static Group mining(Session session) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Powder gained", count(session, Counters.MINING_POWDER), Unit.COUNT, false));
        parts(session, Counters.MINING_POWDER_KIND, rows);
        optional(rows, "Blocks mined (Compact tool)", count(session, Counters.MINING_BLOCKS), Unit.COUNT);
        optional(rows, "Commissions completed", count(session, Counters.MINING_COMMISSIONS), Unit.COUNT);
        parts(session, Counters.MINING_COMMISSION, rows);
        optional(rows, "Powder chests opened", count(session, Counters.MINING_CHESTS), Unit.COUNT);
        optional(rows, "Nucleus runs", count(session, Counters.MINING_NUCLEUS_RUNS), Unit.COUNT);
        optional(rows, "Mineshafts found", count(session, Counters.MINING_MINESHAFTS), Unit.COUNT);
        optional(rows, "Pristine procs", count(session, Counters.MINING_PRISTINE), Unit.COUNT);
        parts(session, Counters.MINING_PRISTINE_ITEM, rows);
        optional(rows, "Compact procs", count(session, Counters.MINING_COMPACT), Unit.COUNT);
        parts(session, Counters.MINING_COMPACT_ITEM, rows);
        optional(rows, "Pickobulus uses", count(session, Counters.MINING_PICKOBULUS), Unit.COUNT);
        optional(rows, "Pickobulus blocks", count(session, Counters.MINING_PICKOBULUS_BLOCKS), Unit.COUNT);
        return new Group("MINING", rows);
    }

    private static Group foraging(Session session) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Trees cut", count(session, Counters.FORAGING_TREES), Unit.COUNT, false));
        parts(session, Counters.FORAGING_TREE, rows);
        average(rows, "Share of each tree", session, Counters.FORAGING_SHARE_PERCENT, Counters.FORAGING_TREES, Unit.PERCENT);
        optional(rows, "Whispers", count(session, Counters.FORAGING_WHISPERS), Unit.COUNT);
        // How many rewards the gifts held, and under it how much of each: whispers, XP, essence.
        optional(rows, "Gift rewards", count(session, Counters.FORAGING_REWARDS), Unit.COUNT);
        parts(session, Counters.FORAGING_REWARD, rows);
        optional(rows, "Bonus gifts", count(session, Counters.FORAGING_BONUS_GIFTS), Unit.COUNT);
        parts(session, Counters.FORAGING_BONUS, rows);
        optional(rows, "Mobs from trees", count(session, Counters.FORAGING_MOBS), Unit.COUNT);
        parts(session, Counters.FORAGING_MOB, rows);
        return new Group("FORAGING", rows);
    }

    private static boolean any(Session session, String prefix) {
        for (String key : session.counters.keySet()) {
            if (key.startsWith(prefix)) return true;
        }
        return false;
    }

    private static Group fishing(Session session) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Catches", count(session, Counters.FISHING_CATCHES), Unit.COUNT, false));
        rows.add(new Row("Sea creatures", count(session, Counters.FISHING_SEA_CREATURES), Unit.COUNT, false));
        parts(session, Counters.FISHING_CREATURE, rows);
        optional(rows, "Double hooks", count(session, Counters.FISHING_DOUBLE_HOOKS), Unit.COUNT);
        optional(rows, "Trophy fish", count(session, Counters.FISHING_TROPHY_FISH), Unit.COUNT);
        parts(session, Counters.FISHING_TROPHY_TIER, rows);
        optional(rows, "Treasures", count(session, Counters.FISHING_TREASURES), Unit.COUNT);
        optional(rows, "Treasure coins", count(session, Counters.FISHING_TREASURE_COINS), Unit.COINS);
        return new Group("FISHING", rows);
    }

    private static Group slayer(Session session) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Bosses killed", count(session, Counters.SLAYER_COMPLETED), Unit.COUNT, false));
        parts(session, Counters.SLAYER_BOSS, rows);
        optional(rows, "Slayer XP", count(session, Counters.SLAYER_XP), Unit.COUNT);
        optional(rows, "RNG meter XP", count(session, Counters.SLAYER_RNG_METER_XP), Unit.COUNT);
        optional(rows, "Minibosses", count(session, Counters.SLAYER_MINIBOSSES), Unit.COUNT);
        optional(rows, "Quests started", count(session, Counters.SLAYER_STARTED), Unit.COUNT);
        optional(rows, "Quests failed", count(session, Counters.SLAYER_FAILED), Unit.COUNT);
        average(rows, "Time to spawn", session, Counters.SLAYER_SPAWN_MILLIS, Counters.SLAYER_SPAWNS_TIMED, Unit.DURATION);
        average(rows, "Time to kill", session, Counters.SLAYER_KILL_MILLIS, Counters.SLAYER_KILLS_TIMED, Unit.DURATION);
        optional(rows, "Paid from bank", count(session, Counters.SLAYER_BANK_COINS), Unit.COINS);
        return new Group("SLAYER", rows);
    }

    private static void optional(List<Row> rows, String label, double total, Unit unit) {
        if (total > 0) rows.add(new Row(label, total, unit, false));
    }

    private static void average(List<Row> rows, String label, Session session, String sumKey, String timesKey, Unit unit) {
        double times = count(session, timesKey);
        if (times > 0) rows.add(new Row(label, count(session, sumKey) / times, unit, false));
    }

    private static Group dungeons(Session session) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Runs completed", count(session, Counters.DUNGEONS_RUNS), Unit.COUNT, false));
        parts(session, Counters.DUNGEONS_FLOOR, rows);
        average(rows, "Run time", session, Counters.DUNGEONS_RUN_MILLIS, Counters.DUNGEONS_RUNS_TIMED, Unit.DURATION);
        average(rows, "Team score", session, Counters.DUNGEONS_SCORE, Counters.DUNGEONS_SCORES, Unit.PLAIN);
        parts(session, Counters.DUNGEONS_GRADE, rows);
        double runs = count(session, Counters.DUNGEONS_RUNS);
        double secrets = count(session, Counters.DUNGEONS_SECRETS);
        double own = count(session, Counters.DUNGEONS_OWN_SECRETS);
        // The team's secrets are the server's own count; which of them were the player's is an estimate.
        optional(rows, "Secrets found by team", secrets, Unit.COUNT);
        optional(rows, "Your secrets (est.)", own, Unit.COUNT);
        if (own > 0 && runs > 0) rows.add(new Row("Your secrets per run", own / runs, Unit.PLAIN, false));
        optional(rows, "Catacombs XP", count(session, Counters.DUNGEONS_CATACOMBS_XP), Unit.COUNT);
        double classXp = 0;
        for (Map.Entry<String, Double> counter : session.counters.entrySet()) {
            if (counter.getKey().startsWith(Counters.DUNGEONS_CLASS_XP)) classXp += counter.getValue();
        }
        optional(rows, "Class XP", classXp, Unit.COUNT);
        parts(session, Counters.DUNGEONS_CLASS_XP, rows);
        optional(rows, "Your deaths", count(session, Counters.DUNGEONS_DEATHS), Unit.COUNT);
        optional(rows, "Team deaths", count(session, Counters.DUNGEONS_TEAM_DEATHS), Unit.COUNT);
        return new Group("DUNGEONS", rows);
    }

    /** One row for each counter of a family, the most counted first. */
    private static void parts(Session session, String prefix, List<Row> rows) {
        List<Row> parts = new ArrayList<>();
        for (Map.Entry<String, Double> counter : session.counters.entrySet()) {
            if (!counter.getKey().startsWith(prefix) || counter.getValue() <= 0) continue;
            parts.add(new Row(name(counter.getKey().substring(prefix.length())), counter.getValue(), Unit.COUNT, true));
        }
        parts.sort(Comparator.comparingDouble(Row::total).reversed().thenComparing(Row::label));
        rows.addAll(parts);
    }

    /** A word the game writes all in capitals, such as a trophy fish's tier, in ordinary case. */
    private static String name(String text) {
        if (text.length() < 2 || !text.equals(text.toUpperCase())) return text;
        return text.charAt(0) + text.substring(1).toLowerCase();
    }
}
