package net.senbler.sentils.client.lab.analysis;

import java.util.ArrayList;
import java.util.List;

import net.senbler.sentils.client.lab.core.SessionTimes;
import net.senbler.sentils.client.lab.model.Activity;
import net.senbler.sentils.client.lab.model.Provenance;
import net.senbler.sentils.client.lab.model.Session;
import net.senbler.sentils.client.lab.price.Assumptions;
import net.senbler.sentils.client.lab.price.PriceSnapshot;
import net.senbler.sentils.client.lab.price.Valuation;

/**
 * Everything worked out from one session: its lengths, its items with a value put on each, and
 * its totals. It is made fresh from the saved session each time, with whichever prices and
 * assumptions are asked for, and changes nothing in the session.
 * <p>
 * Three sums of money are kept apart. The purse change is coins actually gained or spent. The
 * gross value is what the items gained would be worth. The net is that less the items lost and
 * the costs entered. The purse change is never added to the others: an item sold shows up as an
 * item lost and as coins in the purse, and counting both would count it twice.
 */
public final class SessionReport {

    private static final long STALE_MILLIS = 24 * 3_600_000L;

    /** A number with how it was come by. The value is null exactly when it is unavailable. */
    public record Measure(Double value, Provenance provenance, String note) {
        public static Measure unavailable(String why) {
            return new Measure(null, Provenance.UNAVAILABLE, why);
        }

        public boolean isAvailable() {
            return this.value != null;
        }
    }

    /** One item of the session with its price. */
    public record Line(ItemTotals.Total total, Valuation.Price price) {
        /** The value of how many more of it there were at the end, or null if the item has no price. */
        public Double gainValue() {
            return this.price.isKnown() ? this.total.netGain() * this.price.unit() : null;
        }

        /** The value of how many fewer of it there were at the end. */
        public Double lossValue() {
            return this.price.isKnown() ? this.total.netLoss() * this.price.unit() : null;
        }
    }

    public final Session session;
    public final PriceSnapshot prices; // may be null
    public final Assumptions assumptions;

    public final long elapsedMillis;
    public final long pausedMillis;
    public final long trackedMillis;
    public final long estimatedActiveMillis;

    public final List<Line> lines = new ArrayList<>();
    /**
     * How many more items there were at the end, and how many fewer, adding up each kind of
     * item's own difference. Items that came in and went out again are in neither.
     */
    public final long itemsGained;
    public final long itemsLost;
    /** Kinds of item that only came in and went out again, such as what a compactor used up. */
    public final int passedThrough;
    /** Whether any sack message was seen. Without one, items that go straight to the sacks are missed. */
    public final boolean sackMessagesSeen;
    /** Kinds of item that were gained or lost but have no price, and so are in none of the values. */
    public final int unpriced;
    public final double manualCosts;

    private final double gainValue;
    private final double lossValue;
    private final boolean anyPriced;
    private final boolean anyManualPrice;
    // Whether anything was gained or lost that is not left out of the value by the player.
    private final boolean anyGainCounted;
    private final boolean anyLossCounted;

    public SessionReport(Session session, PriceSnapshot prices, Assumptions assumptions, long now) {
        this.session = session;
        this.prices = prices;
        this.assumptions = assumptions != null ? assumptions : new Assumptions();

        this.elapsedMillis = SessionTimes.elapsed(session, now);
        this.pausedMillis = SessionTimes.paused(session, now);
        this.trackedMillis = SessionTimes.tracked(session, now);
        this.estimatedActiveMillis = SessionTimes.estimatedActive(session, now);

        long gained = 0;
        long lost = 0;
        int passedThrough = 0;
        int unpriced = 0;
        double gainValue = 0;
        double lossValue = 0;
        boolean anyPriced = false;
        boolean anyManual = false;
        boolean anyGainCounted = false;
        boolean anyLossCounted = false;
        for (ItemTotals.Total total : ItemTotals.of(session, -1, prices)) {
            Valuation.Price price = Valuation.price(total.name, total.id, prices, this.assumptions);
            Line line = new Line(total, price);
            this.lines.add(line);
            gained += total.netGain();
            lost += total.netLoss();
            if (total.net() == 0 && total.passedThrough() > 0) passedThrough++;
            if (price.excluded() || total.net() == 0) continue;
            anyGainCounted |= total.netGain() > 0;
            anyLossCounted |= total.netLoss() > 0;
            if (price.isKnown()) {
                gainValue += line.gainValue();
                lossValue += line.lossValue();
                anyPriced = true;
                anyManual |= price.provenance() == Provenance.MANUAL;
            } else {
                unpriced++;
            }
        }
        this.itemsGained = gained;
        this.itemsLost = lost;
        this.passedThrough = passedThrough;
        boolean sacks = false;
        for (Session.Bucket bucket : session.buckets) sacks |= Session.Source.SACK.name().equals(bucket.source);
        this.sackMessagesSeen = sacks;
        this.unpriced = unpriced;
        this.gainValue = gainValue;
        this.lossValue = lossValue;
        this.anyPriced = anyPriced;
        this.anyManualPrice = anyManual;
        this.anyGainCounted = anyGainCounted;
        this.anyLossCounted = anyLossCounted;

        double costs = 0;
        for (Session.Cost cost : session.costs) costs += cost.amount;
        this.manualCosts = costs;
    }

    public Activity activity() {
        return this.session.activityType();
    }

    /** Whether the prices were fetched more than a day away from when the session ended. */
    public boolean pricesAreStale(long now) {
        if (this.prices == null) return false;
        return Math.abs(SessionTimes.end(this.session, now) - this.prices.retrievedMillis) > STALE_MILLIS;
    }

    /** Estimated value of the items gained, before anything is taken off. */
    public Measure grossValue() {
        if (this.itemsGained == 0) {
            return new Measure(0.0, Provenance.CALCULATED, "No items were seen to be gained");
        }
        if (!this.anyGainCounted) {
            return new Measure(0.0, Provenance.CALCULATED, "Every item gained is one you have left out of the value");
        }
        if (!this.anyPriced) return Measure.unavailable(noPriceReason());
        return new Measure(this.gainValue, Provenance.ESTIMATED, unpricedNote());
    }

    /** Estimated value of the items lost. */
    public Measure lossValue() {
        if (!this.anyLossCounted) return new Measure(0.0, Provenance.CALCULATED, "No items were seen to be lost");
        if (!this.anyPriced) return Measure.unavailable(noPriceReason());
        return new Measure(this.lossValue, Provenance.ESTIMATED, unpricedNote());
    }

    /** Estimated net profit: the value of the gains, less the value of the losses and the costs entered. */
    public Measure net() {
        Measure gross = grossValue();
        if (!gross.isAvailable()) return gross;
        Measure loss = lossValue();
        if (!loss.isAvailable()) return loss;
        Provenance provenance = this.itemsGained == 0 && this.itemsLost == 0 && this.manualCosts != 0
                ? Provenance.MANUAL : gross.provenance().and(loss.provenance());
        return new Measure(gross.value() - loss.value() - this.manualCosts, provenance, unpricedNote());
    }

    /** Coins actually gained or spent while recording. */
    public Measure purseChange() {
        if (this.session.purseChange == null) return Measure.unavailable("The purse was never readable on the scoreboard");
        return new Measure(this.session.purseChange, Provenance.OBSERVED, "");
    }

    /** XP in the activity's skill, or in every skill together if the activity has none. */
    public Measure skillXp() {
        if (this.session.xp.isEmpty()) {
            return Measure.unavailable("No skill XP was seen above the hotbar or in the tab list. Try /lab debug");
        }
        String skill = activity().skill;
        double amount = 0;
        boolean exact = true;
        boolean any = false;
        for (var entry : this.session.xp.entrySet()) {
            if (skill != null && !skill.equalsIgnoreCase(entry.getKey())) continue;
            amount += entry.getValue().amount;
            exact &= entry.getValue().exact;
            any = true;
        }
        // Other skills gained XP, but none was seen for this one: that is not known to be none.
        if (!any) return Measure.unavailable("No " + skill + " XP was seen, only XP in other skills");
        return new Measure(amount, exact ? Provenance.CALCULATED : Provenance.ESTIMATED,
                exact ? "" : "Repeated XP messages could not all be told from new gains");
    }

    /** The metric's amount over the whole session, before it is divided by the time. */
    public Measure total(Metric metric) {
        return switch (metric) {
            case NET_PER_HOUR -> net();
            case GROSS_PER_HOUR -> grossValue();
            case PURSE_PER_HOUR -> purseChange();
            case ITEMS_PER_HOUR -> new Measure((double) this.itemsGained, Provenance.CALCULATED, "");
            case XP_PER_HOUR -> skillXp();
            case ACTIVITY_PER_HOUR -> activityCount();
        };
    }

    /** The count the activity is scored in: sea creatures for fishing, bosses for slayer. */
    public Measure activityCount() {
        String key = ActivityStats.primaryKey(activity());
        if (key == null) return Measure.unavailable("Nothing of its own is counted for " + activity().label.toLowerCase() + " yet");
        return new Measure(ActivityStats.count(this.session, key), Provenance.OBSERVED, "");
    }

    /** The metric per tracked hour. Unavailable if the total is, or if no time was tracked. */
    public Measure rate(Metric metric) {
        Measure total = total(metric);
        if (!total.isAvailable()) return total;
        if (this.trackedMillis <= 0) return Measure.unavailable("No time was tracked");
        Provenance provenance = total.provenance().and(Provenance.CALCULATED);
        return new Measure(total.value() / SessionTimes.hours(this.trackedMillis), provenance, total.note());
    }

    public boolean usesManualPrices() {
        return this.anyManualPrice;
    }

    private String noPriceReason() {
        return this.prices == null && this.assumptions.manualPrices.isEmpty()
                ? "No prices: refresh them in Settings or set your own"
                : "None of the items has a price";
    }

    private String unpricedNote() {
        return this.unpriced == 0 ? "" : this.unpriced + (this.unpriced == 1 ? " item has" : " items have")
                + " no price and " + (this.unpriced == 1 ? "is" : "are") + " not included";
    }
}
