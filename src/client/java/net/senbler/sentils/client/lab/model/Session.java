package net.senbler.sentils.client.lab.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.senbler.sentils.client.lab.price.Assumptions;
import net.senbler.sentils.client.lab.price.PriceSnapshot;

/**
 * One recorded stretch of play, as it is saved. It holds what was observed, not what was made
 * of it: values, rates and comparisons are worked out from this by the analysis classes, so a
 * saved session can always be gone back over.
 * <p>
 * The fields are public and plain because this is the shape of the JSON file.
 */
public class Session {

    public static final int CURRENT_VERSION = 1;

    public int formatVersion = CURRENT_VERSION;
    public String id;
    public String name = "";
    public String activity = Activity.OTHER.name();
    public long startMillis;
    public long endMillis; // 0 while the session is still going
    public List<Pause> pauses = new ArrayList<>();
    public List<Segment> segments = new ArrayList<>();
    public List<String> tags = new ArrayList<>();
    public String notes = "";
    public String setupId; // null if no setup was chosen
    public String location; // the island it started on, or null if that was not known
    public boolean recovered; // the game closed mid-session and this is what had been saved

    /** What was seen to go in and out, added up by the minute. */
    public List<Bucket> buckets = new ArrayList<>();
    /** How the purse changed while the session was running, or null if the purse was never readable. */
    public Double purseChange;
    /** XP gained in each skill. A skill that is not here was not seen to gain any. */
    public Map<String, XpGain> xp = new LinkedHashMap<>();
    /** What was counted of the activity's own: sea creatures, slayer bosses and so on. See Counters for the names. */
    public Map<String, Double> counters = new LinkedHashMap<>();
    public List<Cost> costs = new ArrayList<>();

    /** The prices of this session's items when it ended, or null if there were none to be had. */
    public PriceSnapshot prices;
    /** Newer prices it was later recalculated with; the ones above are kept as they were. */
    public PriceSnapshot repriced;
    /** How items were being valued when it ended. */
    public Assumptions assumptions;

    /** Every change made by hand after the fact, oldest first. */
    public List<Edit> edits = new ArrayList<>();
    /** Things that limit how far the numbers can be trusted, such as the purse not being visible. */
    public List<String> warnings = new ArrayList<>();

    public static class Pause {
        public long startMillis;
        public long endMillis; // 0 while still paused
        public String reason = "";
    }

    /** A part of the session, begun when the player marked a change of setup or of what they were doing. */
    public static class Segment {
        public long startMillis;
        public String label = "";
    }

    /** An amount of one item, from one source, of one kind, in one minute of one segment. */
    public static class Bucket {
        public long minute; // minutes since 1970, so buckets sort and the timeline can be rebuilt
        public int segment;
        public String source; // Source name
        public String kind; // Kind name
        public String item; // the item's name as shown in game
        public String itemId; // its SkyBlock id, or null where only the name was seen
        public long amount; // always positive; the kind says which way it went
    }

    public enum Source { SACK, INVENTORY, MANUAL }

    public enum Kind {
        /** Came to the player while the session was running. */
        GAIN,
        /** Left the player while the session was running. */
        LOSS,
        /** Moved between the inventory and somewhere else of the player's own: neither gained nor lost. */
        TRANSFER,
        /** Changed while a menu was open, where there is no telling what it was. Not counted. */
        UNCERTAIN
    }

    public static class XpGain {
        public double amount;
        /** False when repeats of the same message could not be told from new gains for certain. */
        public boolean exact = true;
    }

    public static class Cost {
        public String label = "";
        public double amount;
    }

    public static class Edit {
        public long millis;
        public String field;
        public String before;
        public String after;
    }

    public Activity activityType() {
        return Activity.parse(this.activity);
    }

    public boolean isRunning() {
        return this.endMillis == 0;
    }

    /** The name to show: the one given, or the activity if none was. */
    public String displayName() {
        return this.name == null || this.name.isBlank() ? activityType().label + " session" : this.name;
    }
}
