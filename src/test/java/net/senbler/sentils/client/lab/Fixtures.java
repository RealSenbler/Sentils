package net.senbler.sentils.client.lab;

import java.util.concurrent.atomic.AtomicLong;

import net.senbler.sentils.client.lab.core.SessionRecorder;
import net.senbler.sentils.client.lab.model.Activity;
import net.senbler.sentils.client.lab.model.Session;
import net.senbler.sentils.client.lab.model.Session.Kind;
import net.senbler.sentils.client.lab.model.Session.Source;
import net.senbler.sentils.client.lab.price.PriceSnapshot;

/** Sessions and prices for the tests to work on. */
public final class Fixtures {

    private Fixtures() {}

    public static final long START = 1_700_000_000_000L;
    public static final long MINUTE = 60_000L;
    public static final long HOUR = 3_600_000L;

    /** A finished farming session of the given length in which the given amount of wheat was gained. */
    public static Session wheatSession(long trackedMillis, long wheat) {
        AtomicLong clock = new AtomicLong(START);
        SessionRecorder recorder = new SessionRecorder(clock::get);
        recorder.start(Activity.FARMING, "wheat");
        if (wheat > 0) recorder.record(Source.SACK, Kind.GAIN, "Wheat", null, wheat);
        clock.addAndGet(trackedMillis);
        return recorder.stop();
    }

    /** Prices for wheat (on the Bazaar and sold by NPCs) and a hoe (NPC only). */
    public static PriceSnapshot prices() {
        PriceSnapshot prices = new PriceSnapshot();
        prices.retrievedMillis = START;
        prices.source = "test";
        prices.items.put("WHEAT", entry("WHEAT", "Wheat", 4.0, 5.0, 2.0));
        prices.items.put("HOE", entry("HOE", "Hoe", null, null, 10.0));
        return prices;
    }

    public static PriceSnapshot.Entry entry(String id, String name, Double instantSell, Double sellOrder, Double npc) {
        PriceSnapshot.Entry entry = new PriceSnapshot.Entry();
        entry.id = id;
        entry.name = name;
        entry.instantSell = instantSell;
        entry.sellOrder = sellOrder;
        entry.npc = npc;
        return entry;
    }
}
