package net.senbler.sentils.client.lab;

import static net.senbler.sentils.client.lab.Fixtures.MINUTE;
import static net.senbler.sentils.client.lab.Fixtures.START;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.senbler.sentils.client.lab.analysis.ItemTotals;
import net.senbler.sentils.client.lab.core.InventoryDelta;
import net.senbler.sentils.client.lab.core.ItemChange;
import net.senbler.sentils.client.lab.core.SessionRecorder;
import net.senbler.sentils.client.lab.core.SessionTimes;
import net.senbler.sentils.client.lab.model.Activity;
import net.senbler.sentils.client.lab.model.Session;
import net.senbler.sentils.client.lab.model.Session.Kind;
import net.senbler.sentils.client.lab.model.Session.Source;

class SessionRecorderTest {

    private AtomicLong clock;
    private SessionRecorder recorder;

    @BeforeEach
    void setUp() {
        this.clock = new AtomicLong(START);
        this.recorder = new SessionRecorder(this.clock::get);
    }

    private ItemTotals.Total only(Session session) {
        List<ItemTotals.Total> totals = ItemTotals.of(session, -1, null);
        assertEquals(1, totals.size());
        return totals.getFirst();
    }

    @Test
    void startAndStopGiveTheElapsedTime() {
        Session session = this.recorder.start(Activity.FARMING, "  morning  ");
        assertTrue(this.recorder.isRecording());
        assertEquals("morning", session.name);
        assertTrue(session.isRunning());

        this.clock.addAndGet(10 * MINUTE);
        Session stopped = this.recorder.stop();

        assertFalse(this.recorder.isActive());
        assertFalse(stopped.isRunning());
        assertEquals(10 * MINUTE, SessionTimes.elapsed(stopped, 0));
        assertEquals(0, SessionTimes.paused(stopped, 0));
        assertEquals(10 * MINUTE, SessionTimes.tracked(stopped, 0));
    }

    @Test
    void aSecondSessionCannotStartWhileOneIsRunning() {
        this.recorder.start(Activity.MINING, "");
        assertThrows(IllegalStateException.class, () -> this.recorder.start(Activity.MINING, ""));
    }

    @Test
    void stoppingWithNothingRunningGivesNothing() {
        assertNull(this.recorder.stop());
        assertFalse(this.recorder.pause("x"));
        assertFalse(this.recorder.resume());
    }

    @Test
    void pausedTimeIsLeftOutOfTrackedTime() {
        this.recorder.start(Activity.FARMING, "");
        this.clock.addAndGet(10 * MINUTE);
        assertTrue(this.recorder.pause("break"));
        assertFalse(this.recorder.pause("again"), "already paused");
        this.clock.addAndGet(4 * MINUTE);
        assertTrue(this.recorder.resume());
        this.clock.addAndGet(6 * MINUTE);
        this.recorder.pause("another");
        this.clock.addAndGet(1 * MINUTE);
        Session session = this.recorder.stop(); // stopping while paused closes the pause

        assertEquals(21 * MINUTE, SessionTimes.elapsed(session, 0));
        assertEquals(5 * MINUTE, SessionTimes.paused(session, 0));
        assertEquals(16 * MINUTE, SessionTimes.tracked(session, 0));
        assertEquals(session.endMillis, session.pauses.getLast().endMillis);
    }

    @Test
    void aRunningSessionIsTimedUpToNow() {
        Session session = this.recorder.start(Activity.FARMING, "");
        this.clock.addAndGet(3 * MINUTE);
        this.recorder.pause("");
        long now = this.clock.addAndGet(2 * MINUTE);

        assertEquals(5 * MINUTE, SessionTimes.elapsed(session, now));
        assertEquals(2 * MINUTE, SessionTimes.paused(session, now));
        assertEquals(3 * MINUTE, SessionTimes.tracked(session, now));
    }

    @Test
    void nothingIsRecordedWhilePaused() {
        Session session = this.recorder.start(Activity.FARMING, "");
        this.recorder.pause("");
        assertFalse(this.recorder.record(Source.SACK, Kind.GAIN, "Wheat", null, 5));
        assertFalse(this.recorder.addXp("Farming", 10, true));
        assertFalse(this.recorder.addPurseChange(100));
        this.recorder.resume();
        assertTrue(this.recorder.record(Source.SACK, Kind.GAIN, "Wheat", null, 5));

        assertEquals(5, only(session).gained);
        assertTrue(session.xp.isEmpty());
        assertNull(session.purseChange);
    }

    @Test
    void gainsInTheSameMinuteAreAddedIntoOneBucket() {
        Session session = this.recorder.start(Activity.FARMING, "");
        this.recorder.record(Source.SACK, Kind.GAIN, "Wheat", null, 5);
        this.recorder.record(Source.SACK, Kind.GAIN, "Wheat", null, 7);
        this.clock.addAndGet(MINUTE);
        this.recorder.record(Source.SACK, Kind.GAIN, "Wheat", null, 1);

        assertEquals(2, session.buckets.size());
        assertEquals(12, session.buckets.getFirst().amount);
        assertEquals(13, only(session).gained);
    }

    @Test
    void theSameSackMessageTwiceIsCountedOnce() {
        Session session = this.recorder.start(Activity.FARMING, "");
        List<ItemChange> changes = List.of(new ItemChange("Wheat", null, 64));
        assertTrue(this.recorder.recordSackMessage("[Sacks] +64 items. (Last 5s.)", changes));
        this.clock.addAndGet(200);
        assertFalse(this.recorder.recordSackMessage("[Sacks] +64 items. (Last 5s.)", changes));
        // The same text a good while later is a new message.
        this.clock.addAndGet(30_000);
        assertTrue(this.recorder.recordSackMessage("[Sacks] +64 items. (Last 5s.)", changes));

        assertEquals(128, only(session).gained);
    }

    @Test
    void sackGainsAndLossesAreKeptApart() {
        Session session = this.recorder.start(Activity.FARMING, "");
        this.recorder.recordSackMessage("a", List.of(new ItemChange("Wheat", null, 100), new ItemChange("Wheat", null, -30)));

        ItemTotals.Total wheat = only(session);
        assertEquals(100, wheat.gained);
        assertEquals(30, wheat.lost);
        assertEquals(70, wheat.net());
    }

    @Test
    void inventoryChangesWithAMenuOpenAreNotCounted() {
        Session session = this.recorder.start(Activity.FARMING, "");
        this.recorder.recordInventoryChanges(List.of(new ItemChange("Diamond", "DIAMOND", 64)), true);

        ItemTotals.Total diamond = only(session);
        assertEquals(0, diamond.gained);
        assertEquals(64, diamond.uncertain);
    }

    @Test
    void itemsPutInTheSacksByHandAreAMoveNotAGain() {
        Session session = this.recorder.start(Activity.FARMING, "");
        // 64 wheat left the inventory with the sack menu open, then the sacks reported 100 more wheat.
        this.recorder.recordInventoryChanges(List.of(new ItemChange("Wheat", "WHEAT", -64)), true);
        this.clock.addAndGet(5_000);
        this.recorder.recordSackMessage("a", List.of(new ItemChange("Wheat", null, 100)));

        ItemTotals.Total wheat = only(session);
        assertEquals(36, wheat.gained, "only what did not come from the inventory is a gain");
        assertEquals(64, wheat.transferred);
        assertEquals(0, wheat.lost);
    }

    @Test
    void itemsTakenFromTheSacksByCommandAreAMoveWhicheverHalfIsSeenFirst() {
        Session session = this.recorder.start(Activity.FARMING, "");
        // The inventory is seen to gain first, then the sack message arrives.
        this.recorder.recordInventoryChanges(List.of(new ItemChange("Wheat", "WHEAT", 32)), false);
        this.recorder.recordSackMessage("a", List.of(new ItemChange("Wheat", null, -32)));
        ItemTotals.Total wheat = only(session);
        assertEquals(0, wheat.gained);
        assertEquals(0, wheat.lost);

        // The other way round, and for more than was taken: the rest is a real gain.
        this.clock.addAndGet(2 * MINUTE);
        this.recorder.recordSackMessage("b", List.of(new ItemChange("Wheat", null, -10)));
        this.recorder.recordInventoryChanges(List.of(new ItemChange("Wheat", "WHEAT", 15)), false);
        wheat = only(session);
        assertEquals(5, wheat.gained);
        assertEquals(0, wheat.lost);
    }

    @Test
    void anItemOnItsWayToTheSacksAsTheSessionEndsIsNotLeftAsALoss() {
        Session session = this.recorder.start(Activity.FORAGING, "");
        // A compactor's enchanted log lands in the inventory and leaves it for a sack...
        this.recorder.recordInventoryChanges(List.of(new ItemChange("Enchanted Helix Log", "ENCHANTED_HELIX_LOG", 1)), false);
        this.recorder.recordInventoryChanges(List.of(new ItemChange("Enchanted Helix Log", "ENCHANTED_HELIX_LOG", -1)), false);
        this.clock.addAndGet(2_000);
        this.recorder.stop(); // ...but the session ends before the sack message says it arrived.
        assertEquals(0, only(session).net());

        // A second log that left the inventory with nothing yet seen to come in for it.
        Session.Bucket left = new Session.Bucket();
        left.minute = session.endMillis / MINUTE;
        left.source = Source.INVENTORY.name();
        left.kind = Kind.LOSS.name();
        left.item = "Enchanted Helix Log";
        left.itemId = "ENCHANTED_HELIX_LOG";
        left.amount = 1;
        session.buckets.add(left);
        assertEquals(-1, only(session).net());

        // The sack message that covers the end of the session arrives after it: the log is in the sack.
        assertTrue(SessionRecorder.addLateSackMessage(session, List.of(new ItemChange("Enchanted Helix Log", null, 1))));
        assertEquals(0, only(session).net());
    }

    @Test
    void theSackMessageAfterASessionEndsIsCountedTowardsIt() {
        // Ended before the first sack message ever came: everything gained is in that message.
        Session session = this.recorder.start(Activity.FORAGING, "");
        this.clock.addAndGet(10_000);
        this.recorder.stop();
        assertTrue(session.buckets.isEmpty());

        assertTrue(SessionRecorder.addLateSackMessage(session,
                List.of(new ItemChange("Helix Log", null, 200), new ItemChange("Seeds", null, -3))));
        List<ItemTotals.Total> totals = ItemTotals.of(session, -1, null);
        assertEquals(200, totals.getFirst().gained);
        assertEquals(3, totals.get(1).lost);
        assertEquals(session.endMillis / MINUTE, session.buckets.getFirst().minute, "as of the session's end");
        assertFalse(SessionRecorder.addLateSackMessage(session, List.of()));
    }

    @Test
    void theFirstSackMessageAfterAPauseIsCountedAndNoOther() {
        Session session = this.recorder.start(Activity.FORAGING, "");
        this.clock.addAndGet(10_000);
        this.recorder.pause("");
        long pausedAt = this.clock.get();

        // Five seconds into the pause, a message covering the last 30: mostly from before it.
        this.clock.addAndGet(5_000);
        assertTrue(this.recorder.recordSackMessageWhilePaused("a", List.of(new ItemChange("Helix Log", null, 200)), 30_000));
        assertEquals(200, only(session).gained);
        assertEquals(pausedAt / MINUTE, session.buckets.getFirst().minute, "as of the moment of pausing");

        // The next one is about the pause itself.
        this.clock.addAndGet(30_000);
        assertFalse(this.recorder.recordSackMessageWhilePaused("b", List.of(new ItemChange("Helix Log", null, 50)), 30_000));
        assertEquals(200, only(session).gained);

        // After resuming and pausing again, the same goes for the new pause.
        this.recorder.resume();
        this.clock.addAndGet(60_000);
        this.recorder.pause("");
        this.clock.addAndGet(2_000);
        assertTrue(this.recorder.recordSackMessageWhilePaused("c", List.of(new ItemChange("Helix Log", null, 30)), 30_000));
        assertEquals(230, only(session).gained);
    }

    @Test
    void aSackMessageOnlyAboutThePauseIsNotCounted() {
        Session session = this.recorder.start(Activity.FORAGING, "");
        this.recorder.pause("");
        // A minute into the pause, a message covering the last 30 seconds: nothing of it is the session's.
        this.clock.addAndGet(60_000);
        assertFalse(this.recorder.recordSackMessageWhilePaused("a", List.of(new ItemChange("Helix Log", null, 200)), 30_000));
        assertTrue(session.buckets.isEmpty());
        // And not while recording, or with nothing running: those have their own ways in.
        this.recorder.resume();
        assertFalse(this.recorder.recordSackMessageWhilePaused("b", List.of(new ItemChange("Helix Log", null, 1)), 30_000));
    }

    @Test
    void anOldMoveIsNotMatchedToALaterSackMessage() {
        Session session = this.recorder.start(Activity.FARMING, "");
        this.recorder.recordInventoryChanges(List.of(new ItemChange("Wheat", "WHEAT", -64)), true);
        this.clock.addAndGet(5 * MINUTE);
        this.recorder.recordSackMessage("a", List.of(new ItemChange("Wheat", null, 64)));

        assertEquals(64, only(session).gained);
    }

    @Test
    void inventoryDeltaFindsGainsLossesAndLeavesTheRestOut() {
        InventoryDelta.Snapshot before = new InventoryDelta.Snapshot();
        before.add("Wheat", "WHEAT", 10);
        before.add("Wheat", "WHEAT", 5); // a second stack of the same item
        before.add("Seeds", "SEEDS", 3);
        before.add("Hoe", "HOE", 1);
        InventoryDelta.Snapshot after = new InventoryDelta.Snapshot();
        after.add("Wheat", "WHEAT", 40);
        after.add("Hoe", "HOE", 1);
        after.add("Carrot", "CARROT_ITEM", 2);

        List<ItemChange> changes = InventoryDelta.between(before, after);

        assertEquals(3, changes.size());
        assertTrue(changes.contains(new ItemChange("Wheat", "WHEAT", 25)));
        assertTrue(changes.contains(new ItemChange("Carrot", "CARROT_ITEM", 2)));
        assertTrue(changes.contains(new ItemChange("Seeds", "SEEDS", -3)));
        assertTrue(InventoryDelta.between(after, after).isEmpty());
    }

    @Test
    void aSackItemAndAnInventoryItemOfTheSameNameAreOneLine() {
        Session session = this.recorder.start(Activity.FARMING, "");
        this.recorder.record(Source.SACK, Kind.GAIN, "Wheat", null, 10);
        this.recorder.record(Source.INVENTORY, Kind.GAIN, "Wheat", "WHEAT", 5);

        ItemTotals.Total wheat = only(session);
        assertEquals("WHEAT", wheat.id);
        assertEquals(15, wheat.gained);
    }

    @Test
    void segmentsSplitTheSessionAndItsItems() {
        Session session = this.recorder.start(Activity.FARMING, "");
        this.recorder.record(Source.SACK, Kind.GAIN, "Wheat", null, 10);
        this.clock.addAndGet(10 * MINUTE);
        assertTrue(this.recorder.split("new hoe"));
        this.recorder.record(Source.SACK, Kind.GAIN, "Wheat", null, 30);
        this.clock.addAndGet(20 * MINUTE);
        this.recorder.stop();

        assertEquals(2, session.segments.size());
        assertEquals("new hoe", session.segments.get(1).label);
        assertEquals(10 * MINUTE, SessionTimes.segmentTracked(session, 0, 0));
        assertEquals(20 * MINUTE, SessionTimes.segmentTracked(session, 1, 0));
        assertEquals(10, ItemTotals.of(session, 0, null).getFirst().gained);
        assertEquals(30, ItemTotals.of(session, 1, null).getFirst().gained);
        assertEquals(40, only(session).gained);
    }

    @Test
    void xpAndPurseAreAddedUp() {
        Session session = this.recorder.start(Activity.FARMING, "");
        assertNull(session.purseChange, "unknown until the purse has been read");
        this.recorder.purseSeen();
        assertEquals(0.0, session.purseChange);
        this.recorder.addPurseChange(150);
        this.recorder.addPurseChange(-50);
        this.recorder.addXp("Farming", 12.5, true);
        this.recorder.addXp("Farming", 7.5, false);

        assertEquals(100.0, session.purseChange);
        assertEquals(20.0, session.xp.get("Farming").amount);
        assertFalse(session.xp.get("Farming").exact);
    }

    @Test
    void estimatedActiveTimeCountsTheMinutesWithGainsAndNeverMoreThanTracked() {
        Session session = this.recorder.start(Activity.FARMING, "");
        this.recorder.record(Source.SACK, Kind.GAIN, "Wheat", null, 1);
        this.clock.addAndGet(5 * MINUTE);
        this.recorder.record(Source.SACK, Kind.GAIN, "Wheat", null, 1);
        this.clock.addAndGet(25 * MINUTE);
        this.recorder.stop();
        assertEquals(2 * MINUTE, SessionTimes.estimatedActive(session, 0));

        Session brief = Fixtures.wheatSession(10_000, 5);
        assertEquals(10_000, SessionTimes.estimatedActive(brief, 0));
    }
}
