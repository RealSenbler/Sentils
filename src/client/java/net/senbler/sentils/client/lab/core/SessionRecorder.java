package net.senbler.sentils.client.lab.core;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

import net.senbler.sentils.client.lab.model.Activity;
import net.senbler.sentils.client.lab.model.Session;
import net.senbler.sentils.client.lab.model.Session.Kind;
import net.senbler.sentils.client.lab.model.Session.Source;

/**
 * Runs the session being recorded: starts, pauses and ends it, and writes down what the
 * collectors see while it is running. Nothing is written down while it is paused.
 * <p>
 * It knows nothing of the game. The collectors hand it what they saw and it decides what that
 * was: a gain, a loss, or an item only moving between the inventory and the sacks.
 */
public final class SessionRecorder {

    private static final long MINUTE = 60_000L;
    // A sack message says what happened over the last so many seconds, so the two sides of an
    // item moving between the inventory and the sacks can be this far apart.
    private static final long TRANSFER_WINDOW_MILLIS = 60_000L;
    // The same sack message arriving again this soon is the same message, not a second one.
    private static final long DUPLICATE_WINDOW_MILLIS = 1_500L;

    /** An amount that left or entered the inventory while a menu was open, and when. */
    private record Pending(long amount, long millis) {}

    private final LongSupplier clock;
    private Session active;
    private final Map<String, Pending> movedOut = new HashMap<>(); // by item name in lower case
    private final Map<String, Pending> movedIn = new HashMap<>();
    private String lastSackText;
    private long lastSackMillis;
    private boolean tailTaken; // a sack message has already been counted for the current pause

    public SessionRecorder(LongSupplier clock) {
        this.clock = clock;
    }

    public Session active() {
        return this.active;
    }

    public boolean isActive() {
        return this.active != null;
    }

    public boolean isPaused() {
        return this.active != null && !this.active.pauses.isEmpty()
                && this.active.pauses.getLast().endMillis == 0;
    }

    /** Whether what is seen right now is written down: a session is going and is not paused. */
    public boolean isRecording() {
        return this.active != null && !isPaused();
    }

    /** Starts a session. There must not be one going already. */
    public Session start(Activity activity, String name) {
        if (this.active != null) throw new IllegalStateException("A session is already running");
        Session session = new Session();
        session.id = UUID.randomUUID().toString();
        session.activity = activity.name();
        session.name = name == null ? "" : name.trim();
        session.startMillis = this.clock.getAsLong();
        Session.Segment first = new Session.Segment();
        first.startMillis = session.startMillis;
        session.segments.add(first);
        this.active = session;
        clearMatching();
        return session;
    }

    public boolean pause(String reason) {
        if (!isRecording()) return false;
        Session.Pause pause = new Session.Pause();
        pause.startMillis = this.clock.getAsLong();
        pause.reason = reason == null ? "" : reason;
        this.active.pauses.add(pause);
        this.tailTaken = false;
        return true;
    }

    public boolean resume() {
        if (!isPaused()) return false;
        this.active.pauses.getLast().endMillis = this.clock.getAsLong();
        clearMatching();
        return true;
    }

    /** Ends the session and hands it over to be saved, or returns null if there is none. */
    public Session stop() {
        if (this.active == null) return null;
        Session session = this.active;
        long now = Math.max(this.clock.getAsLong(), session.startMillis);
        if (isPaused()) session.pauses.getLast().endMillis = now;
        session.endMillis = now;
        this.active = null;
        return session;
    }

    /** Throws the session away. Returns it, or null if there was none. */
    public Session cancel() {
        Session session = this.active;
        this.active = null;
        return session;
    }

    /** Starts a new segment from now, for a change of setup or of what is being done. */
    public boolean split(String label) {
        if (this.active == null) return false;
        Session.Segment segment = new Session.Segment();
        segment.startMillis = this.clock.getAsLong();
        segment.label = label == null ? "" : label.trim();
        this.active.segments.add(segment);
        return true;
    }

    /** Writes down an amount of an item. Returns false, having done nothing, if not recording. */
    public boolean record(Source source, Kind kind, String name, String id, long amount) {
        if (!isRecording()) return false;
        return put(this.active, this.clock.getAsLong(), source, kind, name, id, amount);
    }

    /** Writes an amount of an item into a session as of the given moment, whatever state it is in. */
    private static boolean put(Session session, long millis, Source source, Kind kind, String name, String id,
                               long amount) {
        if (amount <= 0 || name == null || name.isBlank()) return false;
        long minute = millis / MINUTE;
        int segment = session.segments.size() - 1;
        List<Session.Bucket> buckets = session.buckets;
        for (int i = buckets.size() - 1; i >= 0 && buckets.get(i).minute == minute; i--) {
            Session.Bucket bucket = buckets.get(i);
            if (bucket.segment == segment && bucket.source.equals(source.name()) && bucket.kind.equals(kind.name())
                    && bucket.item.equals(name) && Objects.equals(bucket.itemId, id)) {
                bucket.amount += amount;
                return true;
            }
        }
        Session.Bucket bucket = new Session.Bucket();
        bucket.minute = minute;
        bucket.segment = segment;
        bucket.source = source.name();
        bucket.kind = kind.name();
        bucket.item = name;
        bucket.itemId = id;
        bucket.amount = amount;
        buckets.add(bucket);
        return true;
    }

    /**
     * Takes what a "[Sacks]" message said went into and out of the sacks. A message with the
     * same text as the one just before it is taken to be that one again and ignored. Returns
     * whether it was used.
     */
    public boolean recordSackMessage(String text, List<ItemChange> changes) {
        if (!isRecording()) return false;
        long now = this.clock.getAsLong();
        if (text != null && text.equals(this.lastSackText) && now - this.lastSackMillis < DUPLICATE_WINDOW_MILLIS) {
            return false;
        }
        this.lastSackText = text;
        this.lastSackMillis = now;
        applySackChanges(changes, now);
        return true;
    }

    /**
     * Takes a sack message that arrived while the session is paused. A sack message reports on
     * the stretch of time just gone, so the first one after a pause is mostly or wholly about
     * what was gained before it, and is counted. It is given how long a stretch the message
     * covers; one that covers only time since the pause is left out, as is any after the first.
     */
    public boolean recordSackMessageWhilePaused(String text, List<ItemChange> changes, long coversMillis) {
        if (!isPaused() || this.tailTaken) return false;
        long pausedAt = this.active.pauses.getLast().startMillis;
        if (this.clock.getAsLong() - coversMillis >= pausedAt) return false;
        this.tailTaken = true;
        this.lastSackText = text;
        this.lastSackMillis = this.clock.getAsLong();
        applySackChanges(changes, pausedAt);
        return true;
    }

    /** Writes a sack message's items into the session as of the given moment. */
    private void applySackChanges(List<ItemChange> changes, long millis) {
        for (ItemChange change : changes) {
            long amount = Math.abs(change.amount());
            if (change.amount() > 0) {
                // Into the sacks. Some of it may be what was just put there from the inventory by hand.
                long moved = take(this.movedOut, change.name(), amount, millis);
                put(this.active, millis, Source.SACK, Kind.TRANSFER, change.name(), change.id(), moved);
                put(this.active, millis, Source.SACK, Kind.GAIN, change.name(), change.id(), amount - moved);
            } else if (change.amount() < 0) {
                // Out of the sacks. Some of it may have gone to the inventory, with a menu open or without.
                long moved = take(this.movedIn, change.name(), amount, millis);
                moved += reclassify(Source.INVENTORY, Kind.GAIN, change.name(), amount - moved, millis);
                put(this.active, millis, Source.SACK, Kind.TRANSFER, change.name(), change.id(), moved);
                put(this.active, millis, Source.SACK, Kind.LOSS, change.name(), change.id(), amount - moved);
            }
        }
    }

    /**
     * Takes what changed in the inventory since it was last looked at. With a menu open there is
     * no telling a pickup from something taken out of a chest, so those changes are only noted,
     * not counted. They are remembered, though, to tell when a sack message is about the same items.
     */
    public void recordInventoryChanges(List<ItemChange> changes, boolean menuWasOpen) {
        if (!isRecording()) return;
        long now = this.clock.getAsLong();
        for (ItemChange change : changes) {
            long amount = Math.abs(change.amount());
            if (menuWasOpen) {
                record(Source.INVENTORY, Kind.UNCERTAIN, change.name(), change.id(), amount);
                add(change.amount() > 0 ? this.movedIn : this.movedOut, change.name(), amount, now);
            } else if (change.amount() > 0) {
                // Taken out of the sacks with a command: the sack message may have come first.
                long moved = reclassify(Source.SACK, Kind.LOSS, change.name(), amount, now);
                record(Source.INVENTORY, Kind.TRANSFER, change.name(), change.id(), moved);
                record(Source.INVENTORY, Kind.GAIN, change.name(), change.id(), amount - moved);
            } else {
                record(Source.INVENTORY, Kind.LOSS, change.name(), change.id(), amount);
            }
        }
    }

    /**
     * Takes the first sack message to arrive after a session ended. It reports on the stretch
     * of time just gone, which is the end of the session, so what it lists is added to the
     * session as of its end. Whether the message is recent enough to belong to the session,
     * and that it is only done once, is for the caller to see to. Returns whether anything was added.
     */
    public static boolean addLateSackMessage(Session ended, List<ItemChange> changes) {
        boolean changed = false;
        for (ItemChange change : changes) {
            changed |= put(ended, ended.endMillis, Source.SACK, change.amount() > 0 ? Kind.GAIN : Kind.LOSS,
                    change.name(), change.id(), Math.abs(change.amount()));
        }
        return changed;
    }

    /** Adds XP in a skill. exact is false if a repeat of a message could have been taken for a new gain. */
    public boolean addXp(String skill, double amount, boolean exact) {
        if (!isRecording() || amount <= 0 || skill == null || skill.isBlank()) return false;
        Session.XpGain gain = this.active.xp.computeIfAbsent(skill, key -> new Session.XpGain());
        gain.amount += amount;
        gain.exact &= exact;
        return true;
    }

    /** Adds to one of the activity's own counters; see Counters for the names. */
    public boolean count(String key, double amount) {
        if (!isRecording() || amount <= 0 || key == null || key.isBlank()) return false;
        this.active.counters.merge(key, amount, Double::sum);
        return true;
    }

    /** Adds a change in the purse, up or down, seen while recording. */
    public boolean addPurseChange(double change) {
        if (!isRecording()) return false;
        this.active.purseChange = (this.active.purseChange != null ? this.active.purseChange : 0) + change;
        return true;
    }

    /** Notes that the purse could be read, so that no change is recorded as zero rather than as unknown. */
    public void purseSeen() {
        if (isRecording() && this.active.purseChange == null) this.active.purseChange = 0.0;
    }

    public void warn(String warning) {
        if (this.active != null && !this.active.warnings.contains(warning)) this.active.warnings.add(warning);
    }

    private void clearMatching() {
        this.movedOut.clear();
        this.movedIn.clear();
        this.lastSackText = null;
    }

    private static void add(Map<String, Pending> pending, String name, long amount, long now) {
        String key = name.toLowerCase(Locale.ROOT);
        Pending held = pending.get(key);
        long carried = held != null && now - held.millis() <= TRANSFER_WINDOW_MILLIS ? held.amount() : 0;
        pending.put(key, new Pending(carried + amount, now));
    }

    /** Uses up to the amount of what is pending for the item, if it is recent enough. Returns how much. */
    private static long take(Map<String, Pending> pending, String name, long amount, long now) {
        String key = name.toLowerCase(Locale.ROOT);
        Pending held = pending.get(key);
        if (held == null || now - held.millis() > TRANSFER_WINDOW_MILLIS) {
            pending.remove(key);
            return 0;
        }
        long taken = Math.min(amount, held.amount());
        if (taken == held.amount()) {
            pending.remove(key);
        } else {
            pending.put(key, new Pending(held.amount() - taken, held.millis()));
        }
        return taken;
    }

    /**
     * Turns up to the amount of the item's recent gains or losses from one source into
     * transfers, for when the other half of a move turns up. Returns how much was turned.
     */
    private long reclassify(Source source, Kind from, String name, long amount, long now) {
        if (amount <= 0) return 0;
        long earliest = (now - TRANSFER_WINDOW_MILLIS) / MINUTE;
        long left = amount;
        List<Session.Bucket> buckets = this.active.buckets;
        for (int i = buckets.size() - 1; i >= 0 && left > 0 && buckets.get(i).minute >= earliest; i--) {
            Session.Bucket bucket = buckets.get(i);
            if (!bucket.source.equals(source.name()) || !bucket.kind.equals(from.name())
                    || !bucket.item.equalsIgnoreCase(name)) continue;
            long moved = Math.min(left, bucket.amount);
            left -= moved;
            if (moved == bucket.amount) {
                bucket.kind = Kind.TRANSFER.name();
            } else {
                bucket.amount -= moved;
                Session.Bucket transfer = new Session.Bucket();
                transfer.minute = bucket.minute;
                transfer.segment = bucket.segment;
                transfer.source = bucket.source;
                transfer.kind = Kind.TRANSFER.name();
                transfer.item = bucket.item;
                transfer.itemId = bucket.itemId;
                transfer.amount = moved;
                buckets.add(i + 1, transfer);
            }
        }
        return amount - left;
    }
}
