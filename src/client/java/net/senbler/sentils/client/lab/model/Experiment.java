package net.senbler.sentils.client.lab.model;

import java.util.ArrayList;
import java.util.List;

/** A question put to the records: which of some variants does better on a chosen metric. */
public class Experiment {
    public String id;
    public String name = "";
    public String description = "";
    public String metric; // Metric name
    public long createdMillis;
    public List<Variant> variants = new ArrayList<>();

    /** One of the things being compared, and the sessions recorded while doing it. */
    public static class Variant {
        public String name = "";
        public List<String> sessionIds = new ArrayList<>();
    }

    /** The variant the session is assigned to, or null. */
    public Variant variantOf(String sessionId) {
        for (Variant variant : this.variants) {
            if (variant.sessionIds.contains(sessionId)) return variant;
        }
        return null;
    }
}
