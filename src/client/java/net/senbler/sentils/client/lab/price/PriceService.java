package net.senbler.sentils.client.lab.price;

import java.io.IOException;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Fetches prices when told to, and never of its own accord. These are the only requests the
 * Efficiency Lab makes: two public lists from Hypixel's API that need no key and say nothing
 * about the player. Nothing is sent but the request itself.
 * <p>
 * It holds back from asking too often: a wait after each success, a longer one after each
 * failure in a row, and however long Hypixel says when it reports a rate limit.
 */
public final class PriceService {

    public static final String BAZAAR_URL = "https://api.hypixel.net/v2/skyblock/bazaar";
    public static final String ITEMS_URL = "https://api.hypixel.net/v2/resources/skyblock/items";
    public static final String SOURCE = "Hypixel API: Bazaar and item list";

    private static final long SUCCESS_WAIT_MILLIS = 5 * 60_000L;
    private static final long FIRST_FAILURE_WAIT_MILLIS = 60_000L;
    private static final long MAX_FAILURE_WAIT_MILLIS = 30 * 60_000L;
    private static final long RATE_LIMIT_WAIT_MILLIS = 5 * 60_000L;

    /** What a request came back with. Header names are in lower case. */
    public record Response(int status, String body, Map<String, String> headers) {}

    /** The one way out to the network, so that tests can stand in for it. */
    public interface Transport {
        Response get(String url) throws IOException, InterruptedException;
    }

    public enum Outcome { UPDATED, TOO_SOON, RATE_LIMITED, FAILED }

    /** How a refresh went, with a line to show the player. */
    public record Result(Outcome outcome, String message) {}

    private final Transport transport;
    private final LongSupplier clock;
    // Read by the game's thread every frame while a refresh may be under way on another, so these
    // two are never read under a lock: a refresh holds its lock for as long as the network takes.
    private volatile PriceSnapshot current; // null until prices have been fetched or loaded
    private volatile long nextAllowedMillis;
    private int failures;

    public PriceService(Transport transport, LongSupplier clock, PriceSnapshot cached) {
        this.transport = transport;
        this.clock = clock;
        this.current = cached;
    }

    /** The newest prices there are, or null if none have ever been fetched. */
    public PriceSnapshot current() {
        return this.current;
    }

    /** How long until a refresh will be attempted again, or 0 if one can be now. */
    public long millisUntilAllowed() {
        return Math.max(0, this.nextAllowedMillis - this.clock.getAsLong());
    }

    /**
     * Fetches both lists and, if the Bazaar one is good, replaces the current prices. This waits
     * for the network, so it must not be called on the game's own thread. Only one refresh runs
     * at a time; reading the current prices meanwhile does not wait for it.
     */
    public synchronized Result refresh() {
        long now = this.clock.getAsLong();
        if (now < this.nextAllowedMillis) {
            return new Result(Outcome.TOO_SOON, "Prices were asked for a moment ago. Try again in "
                    + (this.nextAllowedMillis - now + 999) / 1000 + "s");
        }

        // Starts from the last snapshot so that names and NPC prices survive the item list failing.
        PriceSnapshot fresh = new PriceSnapshot();
        if (this.current != null) {
            for (PriceSnapshot.Entry old : this.current.items.values()) {
                PriceSnapshot.Entry copy = new PriceSnapshot.Entry();
                copy.id = old.id;
                copy.name = old.name;
                copy.npc = old.npc;
                fresh.items.put(copy.id, copy);
            }
        }

        try {
            Response bazaar = this.transport.get(BAZAAR_URL);
            if (bazaar.status() == 429) return rateLimited(bazaar, now);
            if (bazaar.status() != 200) return failed("Hypixel's Bazaar list failed (HTTP " + bazaar.status() + ")", now);
            PriceParser.readBazaar(bazaar.body(), fresh);
        } catch (PriceParser.BadResponse e) {
            return failed(e.getMessage(), now);
        } catch (IOException e) {
            return failed("Could not reach Hypixel's API: " + describe(e), now);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failed("The price refresh was interrupted", now);
        }

        // The item list only adds names and NPC prices, so the Bazaar prices stand without it.
        String itemsProblem = null;
        try {
            Response items = this.transport.get(ITEMS_URL);
            if (items.status() == 200) {
                PriceParser.readItems(items.body(), fresh);
            } else {
                itemsProblem = "HTTP " + items.status();
            }
        } catch (PriceParser.BadResponse | IOException e) {
            itemsProblem = e instanceof IOException ? describe(e) : e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            itemsProblem = "interrupted";
        }

        fresh.retrievedMillis = now;
        fresh.source = SOURCE;
        this.current = fresh;
        this.failures = 0;
        this.nextAllowedMillis = now + SUCCESS_WAIT_MILLIS;
        return new Result(Outcome.UPDATED, itemsProblem == null
                ? "Prices updated for " + fresh.items.size() + " items"
                : "Bazaar prices updated, but the item list failed (" + itemsProblem + "): NPC prices and "
                        + "item names are as they were");
    }

    private Result rateLimited(Response response, long now) {
        long wait = RATE_LIMIT_WAIT_MILLIS;
        String header = response.headers().get("retry-after");
        if (header == null) header = response.headers().get("ratelimit-reset");
        try {
            if (header != null) wait = Math.max(wait, Long.parseLong(header.trim()) * 1000L);
        } catch (NumberFormatException ignored) {
        }
        this.nextAllowedMillis = now + wait;
        return new Result(Outcome.RATE_LIMITED, "Hypixel's API limit was reached. Try again in "
                + (wait + 59_999) / 60_000 + " min");
    }

    private Result failed(String message, long now) {
        long wait = Math.min(MAX_FAILURE_WAIT_MILLIS, FIRST_FAILURE_WAIT_MILLIS << Math.min(this.failures, 10));
        this.failures++;
        this.nextAllowedMillis = now + wait;
        return new Result(Outcome.FAILED, message);
    }

    private static String describe(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
