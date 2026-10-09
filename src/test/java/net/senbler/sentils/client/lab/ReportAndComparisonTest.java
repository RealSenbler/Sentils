package net.senbler.sentils.client.lab;

import static net.senbler.sentils.client.lab.Fixtures.HOUR;
import static net.senbler.sentils.client.lab.Fixtures.MINUTE;
import static net.senbler.sentils.client.lab.Fixtures.wheatSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.senbler.sentils.client.lab.analysis.Comparison;
import net.senbler.sentils.client.lab.analysis.Metric;
import net.senbler.sentils.client.lab.analysis.SessionReport;
import net.senbler.sentils.client.lab.analysis.Stats;
import net.senbler.sentils.client.lab.model.Provenance;
import net.senbler.sentils.client.lab.model.Session;
import net.senbler.sentils.client.lab.price.Assumptions;
import net.senbler.sentils.client.lab.price.PriceSnapshot;

class ReportAndComparisonTest {

    private static final double EXACT = 1e-9;

    private static Assumptions untaxed(Assumptions.Mode mode) {
        Assumptions assumptions = new Assumptions();
        assumptions.mode = mode.name();
        assumptions.bazaarTaxPercent = 0;
        return assumptions;
    }

    private static SessionReport report(Session session, Assumptions assumptions) {
        return new SessionReport(session, Fixtures.prices(), assumptions, 0);
    }

    // --- Valuation ---

    @Test
    void grossCostsNetAndHourlyRateFollowTheFormula() {
        Session session = wheatSession(30 * MINUTE, 1000);
        Session.Cost cost = new Session.Cost();
        cost.label = "fuel";
        cost.amount = 500;
        session.costs.add(cost);

        SessionReport report = report(session, untaxed(Assumptions.Mode.INSTANT_SELL));

        assertEquals(4000.0, report.grossValue().value(), EXACT);
        assertEquals(Provenance.ESTIMATED, report.grossValue().provenance());
        assertEquals(500.0, report.manualCosts, EXACT);
        assertEquals(3500.0, report.net().value(), EXACT);
        assertEquals(7000.0, report.rate(Metric.NET_PER_HOUR).value(), EXACT);
        assertEquals(2000.0, report.rate(Metric.ITEMS_PER_HOUR).value(), EXACT);
    }

    @Test
    void eachValuationModeUsesItsOwnPrice() {
        Session session = wheatSession(HOUR, 100);
        assertEquals(400.0, report(session, untaxed(Assumptions.Mode.INSTANT_SELL)).grossValue().value(), EXACT);
        assertEquals(500.0, report(session, untaxed(Assumptions.Mode.SELL_ORDER)).grossValue().value(), EXACT);
        assertEquals(200.0, report(session, untaxed(Assumptions.Mode.NPC)).grossValue().value(), EXACT);
    }

    @Test
    void bazaarTaxComesOffBazaarPricesOnly() {
        Session session = wheatSession(HOUR, 100);
        Assumptions taxed = new Assumptions(); // 1.25% by default
        assertEquals(395.0, report(session, taxed).grossValue().value(), EXACT);
        taxed.mode = Assumptions.Mode.NPC.name();
        assertEquals(200.0, report(session, taxed).grossValue().value(), EXACT);
    }

    @Test
    void aManualPriceReplacesAnyOtherAndIsMarkedManual() {
        Session session = wheatSession(HOUR, 100);
        Assumptions assumptions = untaxed(Assumptions.Mode.INSTANT_SELL);
        assumptions.manualPrices.put("wheat", 9.0);

        SessionReport report = report(session, assumptions);
        assertEquals(900.0, report.grossValue().value(), EXACT);
        assertEquals(Provenance.MANUAL, report.lines.getFirst().price().provenance());
        assertTrue(report.usesManualPrices());
    }

    @Test
    void anExcludedItemIsLeftOutOfTheValueButStillListed() {
        Session session = wheatSession(HOUR, 100);
        Assumptions assumptions = untaxed(Assumptions.Mode.INSTANT_SELL);
        assumptions.excluded.add("wheat");

        SessionReport report = report(session, assumptions);
        assertEquals(1, report.lines.size());
        assertEquals(100, report.itemsGained);
        assertEquals(0, report.unpriced);
        assertEquals(0.0, report.grossValue().value(), EXACT);
    }

    @Test
    void missingPricesMakeTheValueUnavailableNotZero() {
        Session session = wheatSession(HOUR, 100);
        SessionReport report = new SessionReport(session, null, new Assumptions(), 0);

        assertNull(report.grossValue().value());
        assertEquals(Provenance.UNAVAILABLE, report.grossValue().provenance());
        assertFalse(report.net().isAvailable());
        assertFalse(report.rate(Metric.NET_PER_HOUR).isAvailable());
        assertEquals(1, report.unpriced);
        // What was observed is still there.
        assertEquals(100.0, report.rate(Metric.ITEMS_PER_HOUR).value(), EXACT);
    }

    @Test
    void anItemWithNoPriceIsCountedAsUnpricedAndTheRestStillValued() {
        Session session = wheatSession(HOUR, 100);
        Session.Bucket mystery = new Session.Bucket();
        mystery.minute = session.startMillis / MINUTE;
        mystery.source = Session.Source.INVENTORY.name();
        mystery.kind = Session.Kind.GAIN.name();
        mystery.item = "Mystery Thing";
        mystery.itemId = "MYSTERY";
        mystery.amount = 3;
        session.buckets.add(mystery);

        SessionReport report = report(session, untaxed(Assumptions.Mode.INSTANT_SELL));
        assertEquals(1, report.unpriced);
        assertEquals(400.0, report.grossValue().value(), EXACT);
        assertFalse(report.grossValue().note().isEmpty(), "the gap is pointed out");
    }

    @Test
    void anItemOffTheBazaarFallsBackToItsNpcPrice() {
        Session session = wheatSession(HOUR, 0);
        Session.Bucket hoe = new Session.Bucket();
        hoe.minute = session.startMillis / MINUTE;
        hoe.source = Session.Source.INVENTORY.name();
        hoe.kind = Session.Kind.GAIN.name();
        hoe.item = "Hoe";
        hoe.itemId = "HOE";
        hoe.amount = 2;
        session.buckets.add(hoe);

        SessionReport report = report(session, untaxed(Assumptions.Mode.INSTANT_SELL));
        assertEquals(20.0, report.grossValue().value(), EXACT);
        assertTrue(report.lines.getFirst().price().source().contains("NPC"));
    }

    @Test
    void lossesAreValuedAndTakenOffTheNet() {
        Session session = wheatSession(HOUR, 100);
        Session.Bucket lost = new Session.Bucket();
        lost.minute = session.startMillis / MINUTE;
        lost.source = Session.Source.SACK.name();
        lost.kind = Session.Kind.LOSS.name();
        lost.item = "Wheat";
        lost.amount = 150;
        session.buckets.add(lost);

        // 100 came in and 150 went out: 50 fewer at the end than at the start.
        SessionReport report = report(session, untaxed(Assumptions.Mode.INSTANT_SELL));
        assertEquals(0, report.itemsGained);
        assertEquals(50, report.itemsLost);
        assertEquals(0.0, report.grossValue().value(), EXACT);
        assertEquals(200.0, report.lossValue().value(), EXACT);
        assertEquals(-200.0, report.net().value(), EXACT, "a negative result is reported as it is");
    }

    private static void flow(Session session, Session.Source source, Session.Kind kind, String item, String id, long amount) {
        Session.Bucket bucket = new Session.Bucket();
        bucket.minute = session.startMillis / MINUTE;
        bucket.source = source.name();
        bucket.kind = kind.name();
        bucket.item = item;
        bucket.itemId = id;
        bucket.amount = amount;
        session.buckets.add(bucket);
    }

    @Test
    void whatACompactorUsesUpAndMakesIsNotCountedAsGainedAndLost() {
        // 320 logs land in the inventory, a personal compactor turns them into 2 enchanted logs,
        // and those go on into a sack. All that is left to show for it is 2 enchanted logs.
        Session session = wheatSession(HOUR, 0);
        flow(session, Session.Source.INVENTORY, Session.Kind.GAIN, "Helix Log", "HELIX_LOG", 320);
        flow(session, Session.Source.INVENTORY, Session.Kind.LOSS, "Helix Log", "HELIX_LOG", 320);
        flow(session, Session.Source.INVENTORY, Session.Kind.GAIN, "Enchanted Helix Log", "ENCHANTED_HELIX_LOG", 2);
        flow(session, Session.Source.INVENTORY, Session.Kind.LOSS, "Enchanted Helix Log", "ENCHANTED_HELIX_LOG", 2);
        flow(session, Session.Source.SACK, Session.Kind.GAIN, "Enchanted Helix Log", null, 2);

        PriceSnapshot prices = Fixtures.prices();
        prices.items.put("HELIX_LOG", Fixtures.entry("HELIX_LOG", "Helix Log", 10.0, 11.0, 1.0));
        prices.items.put("ENCHANTED_HELIX_LOG", Fixtures.entry("ENCHANTED_HELIX_LOG", "Enchanted Helix Log", 1700.0, 1800.0, 160.0));
        SessionReport report = new SessionReport(session, prices, untaxed(Assumptions.Mode.INSTANT_SELL), 0);

        assertEquals(2, report.itemsGained, "not 324");
        assertEquals(0, report.itemsLost);
        assertEquals(3400.0, report.grossValue().value(), EXACT);
        assertEquals(0.0, report.lossValue().value(), EXACT);
        assertEquals(3400.0, report.net().value(), EXACT);
        assertEquals(1, report.passedThrough, "the logs came and went");
        assertEquals(2.0, report.rate(Metric.ITEMS_PER_HOUR).value(), EXACT);
    }

    @Test
    void compactingItemsHeldBeforeTheSessionShowsAsThoseItemsGoing() {
        // 100 logs were already in the inventory; 60 more came in and all 160 became one enchanted log.
        Session session = wheatSession(HOUR, 0);
        flow(session, Session.Source.INVENTORY, Session.Kind.GAIN, "Helix Log", "HELIX_LOG", 60);
        flow(session, Session.Source.INVENTORY, Session.Kind.LOSS, "Helix Log", "HELIX_LOG", 160);
        flow(session, Session.Source.INVENTORY, Session.Kind.GAIN, "Enchanted Helix Log", "ENCHANTED_HELIX_LOG", 1);

        PriceSnapshot prices = Fixtures.prices();
        prices.items.put("HELIX_LOG", Fixtures.entry("HELIX_LOG", "Helix Log", 10.0, 11.0, 1.0));
        prices.items.put("ENCHANTED_HELIX_LOG", Fixtures.entry("ENCHANTED_HELIX_LOG", "Enchanted Helix Log", 1700.0, 1800.0, 160.0));
        SessionReport report = new SessionReport(session, prices, untaxed(Assumptions.Mode.INSTANT_SELL), 0);

        assertEquals(1, report.itemsGained);
        assertEquals(100, report.itemsLost);
        assertEquals(700.0, report.net().value(), EXACT, "one enchanted log, less the 100 logs that were there already");
    }

    @Test
    void aSessionWithNoSackMessageSaysSo() {
        Session inventoryOnly = wheatSession(HOUR, 0);
        flow(inventoryOnly, Session.Source.INVENTORY, Session.Kind.GAIN, "Wheat", "WHEAT", 5);
        assertFalse(report(inventoryOnly, new Assumptions()).sackMessagesSeen);
        assertTrue(report(wheatSession(HOUR, 5), new Assumptions()).sackMessagesSeen);
    }

    @Test
    void aSessionWithNoTimeTrackedHasNoHourlyRate() {
        Session session = wheatSession(0, 100);
        SessionReport report = report(session, untaxed(Assumptions.Mode.INSTANT_SELL));

        assertEquals(400.0, report.grossValue().value(), EXACT);
        assertFalse(report.rate(Metric.NET_PER_HOUR).isAvailable());
        assertFalse(report.rate(Metric.ITEMS_PER_HOUR).isAvailable());
    }

    @Test
    void aSessionWithNoGainsIsWorthZeroNotUnavailable() {
        SessionReport report = report(wheatSession(HOUR, 0), untaxed(Assumptions.Mode.INSTANT_SELL));
        assertEquals(0.0, report.grossValue().value(), EXACT);
        assertEquals(Provenance.CALCULATED, report.grossValue().provenance());
        assertEquals(0.0, report.net().value(), EXACT);
    }

    @Test
    void thePurseChangeIsKeptApartFromTheItemValue() {
        Session session = wheatSession(HOUR, 100);
        SessionReport without = report(session, untaxed(Assumptions.Mode.INSTANT_SELL));
        assertFalse(without.purseChange().isAvailable());
        assertFalse(without.rate(Metric.PURSE_PER_HOUR).isAvailable());

        session.purseChange = 5000.0;
        SessionReport with = report(session, untaxed(Assumptions.Mode.INSTANT_SELL));
        assertEquals(5000.0, with.purseChange().value(), EXACT);
        assertEquals(Provenance.OBSERVED, with.purseChange().provenance());
        assertEquals(400.0, with.net().value(), EXACT, "coins in the purse are not added to the item value");
    }

    @Test
    void skillXpIsOfTheActivitysSkillAndUnavailableIfNoneWasSeen() {
        Session session = wheatSession(HOUR, 0);
        assertFalse(report(session, new Assumptions()).skillXp().isAvailable());

        Session.XpGain farming = new Session.XpGain();
        farming.amount = 300;
        Session.XpGain combat = new Session.XpGain();
        combat.amount = 1000;
        combat.exact = false;
        session.xp.put("Farming", farming);
        session.xp.put("Combat", combat);

        SessionReport report = report(session, new Assumptions());
        assertEquals(300.0, report.skillXp().value(), EXACT);
        assertEquals(Provenance.CALCULATED, report.skillXp().provenance());
        assertEquals(300.0, report.rate(Metric.XP_PER_HOUR).value(), EXACT);
    }

    @Test
    void aSessionKeepsItsOwnPricesWhenRecalculatedWithNewerOnes() {
        Session session = wheatSession(HOUR, 100);
        session.prices = Fixtures.prices().subset(List.of("WHEAT"));
        session.assumptions = untaxed(Assumptions.Mode.INSTANT_SELL);

        PriceSnapshot newer = Fixtures.prices();
        newer.retrievedMillis += 48 * HOUR;
        newer.items.get("WHEAT").instantSell = 10.0;
        session.repriced = newer.subset(List.of("WHEAT"));

        SessionReport original = new SessionReport(session, session.prices, session.assumptions, 0);
        SessionReport recalculated = new SessionReport(session, session.repriced, session.assumptions, 0);
        assertEquals(400.0, original.grossValue().value(), EXACT);
        assertEquals(1000.0, recalculated.grossValue().value(), EXACT);
        assertEquals(4.0, session.prices.get("WHEAT").instantSell, EXACT, "the original snapshot is untouched");
        assertFalse(original.pricesAreStale(0));
        assertTrue(recalculated.pricesAreStale(0), "prices from two days after the session are marked");
    }

    // --- Statistics ---

    @Test
    void medianMeanAndPercentChange() {
        assertEquals(2.0, Stats.median(List.of(3.0, 1.0, 2.0)), EXACT);
        assertEquals(2.5, Stats.median(List.of(4.0, 1.0, 2.0, 3.0)), EXACT);
        assertEquals(2.5, Stats.mean(List.of(4.0, 1.0, 2.0, 3.0)), EXACT);
        assertEquals(0.0, Stats.deviation(List.of(7.0)), EXACT);
        assertEquals(50.0, Stats.percentChange(100, 150), EXACT);
        assertEquals(50.0, Stats.percentChange(-100, -50), EXACT, "measured against the size of the baseline");
        assertNull(Stats.percentChange(0, 5));
    }

    @Test
    void anOutlierNeedsEnoughValuesToStandOutFrom() {
        List<Double> values = List.of(100.0, 102.0, 98.0, 101.0, 99.0, 500.0);
        assertTrue(Stats.isOutlier(500.0, values));
        assertFalse(Stats.isOutlier(102.0, values));
        assertFalse(Stats.isOutlier(500.0, List.of(100.0, 101.0, 500.0)), "too few to judge");
    }

    // --- Comparison ---

    private static Comparison.Group group(String name, long[][] sessions) {
        List<SessionReport> reports = new ArrayList<>();
        for (long[] session : sessions) {
            reports.add(report(wheatSession(session[0], session[1]), untaxed(Assumptions.Mode.INSTANT_SELL)));
        }
        return new Comparison.Group(name, reports);
    }

    @Test
    void sessionsOfDifferentLengthsAreComparedPerHour() {
        // 500 wheat in half an hour is the same rate as 1000 in an hour.
        Comparison.Result result = Comparison.compare(List.of(
                group("A", new long[][] {{30 * MINUTE, 500}}),
                group("B", new long[][] {{60 * MINUTE, 1000}})), Metric.ITEMS_PER_HOUR);

        Comparison.GroupResult a = result.groups.get(0);
        Comparison.GroupResult b = result.groups.get(1);
        assertEquals(1000.0, a.pooledRate, EXACT);
        assertEquals(1000.0, b.pooledRate, EXACT);
        assertEquals(0.0, b.medianDifference, EXACT);
        assertEquals(0.0, b.medianPercent, EXACT);
        assertEquals(500.0, a.total, EXACT);
        assertEquals(1000.0, b.total, EXACT);
    }

    @Test
    void differencesAreGivenAbsolutelyAndAsAPercentageOfTheBaseline() {
        Comparison.Result result = Comparison.compare(List.of(
                group("A", new long[][] {{HOUR, 1000}}),
                group("B", new long[][] {{HOUR, 1250}})), Metric.GROSS_PER_HOUR);

        Comparison.GroupResult b = result.groups.get(1);
        assertEquals(1000.0, b.medianDifference, EXACT); // 5000 against 4000 coins an hour
        assertEquals(25.0, b.medianPercent, EXACT);
        assertEquals(25.0, b.pooledPercent, EXACT);
        assertNull(result.groups.get(0).verdict, "the baseline is not compared with itself");
    }

    @Test
    void pooledRateWeighsSessionsByTheirLength() {
        // An hour at 1000/h and three hours at 2000/h: 7000 in 4 hours.
        Comparison.Result result = Comparison.compare(List.of(
                group("A", new long[][] {{HOUR, 1000}, {3 * HOUR, 6000}})), Metric.ITEMS_PER_HOUR);

        Comparison.GroupResult a = result.groups.getFirst();
        assertEquals(1750.0, a.pooledRate, EXACT);
        assertEquals(1500.0, a.mean, EXACT);
        assertEquals(1500.0, a.median, EXACT);
        assertEquals(4 * HOUR, a.trackedMillis);
    }

    @Test
    void fewSessionsMakeTheResultPreliminary() {
        Comparison.Result result = Comparison.compare(List.of(
                group("A", new long[][] {{HOUR, 1000}, {HOUR, 1010}}),
                group("B", new long[][] {{HOUR, 2000}, {HOUR, 2010}})), Metric.ITEMS_PER_HOUR);

        Comparison.GroupResult b = result.groups.get(1);
        assertEquals(Comparison.Verdict.PRELIMINARY, b.verdict);
        assertTrue(b.summary.contains("preliminary"));
        assertTrue(b.summary.contains("higher"));
    }

    @Test
    void overlappingSessionsAreInconclusive() {
        Comparison.Result result = Comparison.compare(List.of(
                group("A", new long[][] {{HOUR, 1000}, {HOUR, 1200}, {HOUR, 1100}}),
                group("B", new long[][] {{HOUR, 1150}, {HOUR, 1300}, {HOUR, 1250}})), Metric.ITEMS_PER_HOUR);

        assertEquals(Comparison.Verdict.INCONCLUSIVE, result.groups.get(1).verdict);
    }

    @Test
    void sessionsThatNeverOverlapAreConsistentSoFar() {
        Comparison.Result result = Comparison.compare(List.of(
                group("A", new long[][] {{HOUR, 1000}, {HOUR, 1200}, {HOUR, 1100}}),
                group("B", new long[][] {{HOUR, 1500}, {HOUR, 1600}, {HOUR, 1550}})), Metric.ITEMS_PER_HOUR);

        Comparison.GroupResult b = result.groups.get(1);
        assertEquals(Comparison.Verdict.CONSISTENT, b.verdict);
        assertTrue(b.summary.contains("More comparable sessions"), "it stops short of calling it settled");
    }

    @Test
    void oneLuckySessionIsFlaggedAndDoesNotDecideTheMedian() {
        Comparison.Result result = Comparison.compare(List.of(
                group("A", new long[][] {{HOUR, 1000}, {HOUR, 1010}, {HOUR, 990}, {HOUR, 1005}, {HOUR, 995}}),
                group("B", new long[][] {{HOUR, 1000}, {HOUR, 1010}, {HOUR, 990}, {HOUR, 1005}, {HOUR, 9000}})),
                Metric.ITEMS_PER_HOUR);

        Comparison.GroupResult b = result.groups.get(1);
        assertEquals(List.of(false, false, false, false, true), b.outliers);
        assertTrue(b.mean > 2000, "the mean is dragged up by the one session");
        assertEquals(1005.0, b.median, EXACT);
        assertEquals(Comparison.Verdict.INCONCLUSIVE, b.verdict);
        assertTrue(result.warnings.stream().anyMatch(warning -> warning.contains("far apart")));
    }

    @Test
    void sessionsWithoutTheMetricAreLeftOutAndSaidToBe() {
        List<SessionReport> reports = new ArrayList<>();
        reports.add(report(wheatSession(HOUR, 1000), untaxed(Assumptions.Mode.INSTANT_SELL)));
        reports.add(new SessionReport(wheatSession(HOUR, 1000), null, new Assumptions(), 0)); // no prices
        Comparison.Result result = Comparison.compare(
                List.of(new Comparison.Group("A", reports)), Metric.GROSS_PER_HOUR);

        Comparison.GroupResult a = result.groups.getFirst();
        assertEquals(2, a.sessions);
        assertEquals(1, a.used.size());
        assertEquals(4000.0, a.pooledRate, EXACT);
        assertTrue(result.warnings.stream().anyMatch(warning -> warning.contains("left out")));
    }

    @Test
    void aSideWithNoDataGetsNoNumbers() {
        Comparison.Result result = Comparison.compare(List.of(
                group("A", new long[][] {{HOUR, 1000}}),
                new Comparison.Group("B", List.of())), Metric.ITEMS_PER_HOUR);

        Comparison.GroupResult b = result.groups.get(1);
        assertEquals(Comparison.Verdict.NO_DATA, b.verdict);
        assertNull(b.pooledRate);
        assertNull(b.medianPercent);
        assertNotNull(b.summary);
    }

    @Test
    void veryDifferentSessionLengthsAreWarnedAbout() {
        Comparison.Result result = Comparison.compare(List.of(
                group("A", new long[][] {{10 * MINUTE, 100}}),
                group("B", new long[][] {{2 * HOUR, 1000}})), Metric.ITEMS_PER_HOUR);

        assertTrue(result.warnings.stream().anyMatch(warning -> warning.contains("length")));
    }
}
