package net.senbler.sentils.client.lab.core;

/** Something to add to one of a session's counters; see Counters for the names. */
public record Count(String key, double amount) {

    public static Count one(String key) {
        return new Count(key, 1);
    }
}
