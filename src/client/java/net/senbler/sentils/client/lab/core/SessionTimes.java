package net.senbler.sentils.client.lab.core;

import java.util.HashSet;
import java.util.Set;

import net.senbler.sentils.client.lab.model.Session;

/**
 * The three lengths of a session, kept apart on purpose. Elapsed is start to end by the clock.
 * Tracked is that less the time spent paused, and is what rates are worked out over. Active is
 * a guess at how much of the tracked time anything was actually coming in.
 */
public final class SessionTimes {

    private SessionTimes() {}

    private static final long MINUTE = 60_000L;

    /** When the session ended, or now if it has not. */
    public static long end(Session session, long now) {
        return session.endMillis != 0 ? session.endMillis : Math.max(now, session.startMillis);
    }

    public static long elapsed(Session session, long now) {
        return Math.max(0, end(session, now) - session.startMillis);
    }

    public static long paused(Session session, long now) {
        return pausedBetween(session, session.startMillis, end(session, now), now);
    }

    public static long tracked(Session session, long now) {
        return Math.max(0, elapsed(session, now) - paused(session, now));
    }

    /** The tracked time of one segment, which runs from its start to the next segment's, or to the end. */
    public static long segmentTracked(Session session, int index, long now) {
        if (index < 0 || index >= session.segments.size()) return 0;
        long from = session.segments.get(index).startMillis;
        long to = index + 1 < session.segments.size() ? session.segments.get(index + 1).startMillis : end(session, now);
        return Math.max(0, to - from - pausedBetween(session, from, to, now));
    }

    /**
     * A guess at the time spent actually doing the activity: the minutes in which something was
     * seen to come in or go out. It is an estimate, and never more than the tracked time.
     */
    public static long estimatedActive(Session session, long now) {
        Set<Long> minutes = new HashSet<>();
        for (Session.Bucket bucket : session.buckets) {
            if (Session.Kind.GAIN.name().equals(bucket.kind) || Session.Kind.LOSS.name().equals(bucket.kind)) {
                minutes.add(bucket.minute);
            }
        }
        return Math.min(tracked(session, now), minutes.size() * MINUTE);
    }

    /** How much of the stretch from one moment to another was spent paused. */
    private static long pausedBetween(Session session, long from, long to, long now) {
        long paused = 0;
        for (Session.Pause pause : session.pauses) {
            long start = Math.max(pause.startMillis, from);
            long stop = Math.min(pause.endMillis != 0 ? pause.endMillis : end(session, now), to);
            if (stop > start) paused += stop - start;
        }
        return paused;
    }

    /** Hours as a fraction, for rates. */
    public static double hours(long millis) {
        return millis / 3_600_000.0;
    }
}
