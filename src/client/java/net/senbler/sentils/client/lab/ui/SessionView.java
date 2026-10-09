package net.senbler.sentils.client.lab.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;


import net.senbler.sentils.client.gui.Theme;
import net.senbler.sentils.client.lab.Fmt;
import net.senbler.sentils.client.lab.analysis.ActivityStats;
import net.senbler.sentils.client.lab.analysis.Metric;
import net.senbler.sentils.client.lab.analysis.SessionReport;
import net.senbler.sentils.client.lab.core.SessionTimes;
import net.senbler.sentils.client.lab.model.Provenance;
import net.senbler.sentils.client.lab.model.Session;

/**
 * Lays a session's report out: a row of tiles with the headline figures, and a table of five
 * columns under them. Explanations are kept to the tiles' tooltips. Every figure is marked with
 * how it is known, and one that is not known is a dash, never a zero.
 */
final class SessionView {

    private SessionView() {}

    private static final int GAP = 4;
    // A session this long with no sack message at all probably has them switched off.
    private static final long NO_SACKS_AFTER_MILLIS = 2 * 60_000L;

    static float[] weights() {
        return new float[] {3f, 1.3f, 1.3f, 1.5f, 1.9f};
    }

    /** The headline figures as tiles, side by side across the width. */
    static List<StatTile> tiles(int x, int y, int width, SessionReport report) {
        List<StatTile> tiles = new ArrayList<>();
        int count = 5;
        int w = (width - GAP * (count - 1)) / count;

        tiles.add(new StatTile(x, y, w, Icons.Icon.CLOCK, "TIME", Fmt.duration(report.trackedMillis))
                .tip("Tracked time, which hourly rates are worked out over: start to end, less pauses.\n"
                        + "Start to end: " + Fmt.duration(report.elapsedMillis) + "\n"
                        + "Paused: " + Fmt.duration(report.pausedMillis) + "\n"
                        + "Active, at a guess: " + Fmt.duration(report.estimatedActiveMillis)));

        SessionReport.Measure net = report.rate(Metric.NET_PER_HOUR);
        tiles.add(itemTile(x + (w + GAP), y, w, Metric.NET_PER_HOUR, "NET/H", report)
                .tip("Estimated coins per hour: what the items gained are worth, less items lost and costs.\n"
                        + "Items gained: " + Fmt.measure(report.grossValue()) + "\n"
                        + "Items lost: " + Fmt.measure(report.lossValue()) + "\n"
                        + "Costs: " + Fmt.number(report.manualCosts) + "\n"
                        + "Net: " + Fmt.measure(report.net()) + known(net)));

        SessionReport.Measure purse = report.purseChange();
        tiles.add(tile(x + (w + GAP) * 2, y, w, Metric.PURSE_PER_HOUR, "PURSE", purse)
                .tip("Coins actually gained or spent, from the scoreboard.\n"
                        + "Per hour: " + Fmt.measure(report.rate(Metric.PURSE_PER_HOUR)) + "\n"
                        + "Kept apart from item values, so that an item you sold is not counted twice."
                        + known(purse)));

        SessionReport.Measure xp = report.rate(Metric.XP_PER_HOUR);
        StringBuilder skills = new StringBuilder();
        for (Map.Entry<String, Session.XpGain> gain : report.session.xp.entrySet()) {
            skills.append("\n").append(gain.getKey()).append(": ").append(Fmt.number(gain.getValue().amount));
        }
        tiles.add(tile(x + (w + GAP) * 3, y, w, Metric.XP_PER_HOUR, "XP/H", xp)
                .tip("Skill XP per hour, in this activity's own skill." + skills + known(xp)));

        // The last tile is the activity's own count where it has one, and items of any kind where it has not.
        SessionReport.Measure own = ActivityStats.liveRate(report);
        if (own.isAvailable() || ActivityStats.primaryKey(report.activity()) != null) {
            String label = ActivityStats.liveLabel(report.activity()).toUpperCase(Locale.ROOT);
            tiles.add(tile(x + (w + GAP) * 4, y, w, Metric.ACTIVITY_PER_HOUR, label, own)
                    .tip(Metric.ACTIVITY_PER_HOUR.meaning + ".\nPer hour: "
                            + Fmt.measure(report.rate(Metric.ACTIVITY_PER_HOUR)) + "\nTotal: "
                            + Fmt.measure(report.activityCount()) + known(own)));
            return tiles;
        }
        SessionReport.Measure items = report.rate(Metric.ITEMS_PER_HOUR);
        tiles.add(itemTile(x + (w + GAP) * 4, y, w, Metric.ITEMS_PER_HOUR, "ITEMS/H", report)
                .tip("Items gained per hour, of any kind.\nTotal: " + Fmt.number((double) report.itemsGained)
                        + known(items)));
        return tiles;
    }

    /** What was counted of the activity itself: sea creatures, bosses and the like, with the kinds of each under it. */
    static void fillActivity(LabTable table, SessionReport report) {
        double hours = SessionTimes.hours(report.trackedMillis);
        for (ActivityStats.Group group : ActivityStats.of(report.session, report.trackedMillis)) {
            table.section(group.title());
            table.head("", "", "Total", "Per hour", "");
            for (ActivityStats.Row stat : group.rows()) {
                String total = switch (stat.unit()) {
                    case DURATION -> Fmt.duration((long) stat.total());
                    case PERCENT -> Fmt.number(stat.total()) + "%";
                    case COUNT, COINS, PLAIN -> Fmt.number(stat.total());
                };
                LabTable.Row row = table.add((stat.part() ? "   " : "") + stat.label(), "", total,
                        stat.hasRate() && hours > 0 ? Fmt.number(stat.total() / hours) : "", "");
                if (stat.part()) row.color(Theme.TEXT_MUTED);
                else if (stat.unit() == ActivityStats.Unit.COINS) row.icon(0, Icons.of(Metric.PURSE_PER_HOUR));
                else row.icon(0, Icons.of(Metric.ACTIVITY_PER_HOUR));
            }
        }
    }

    private static StatTile tile(int x, int y, int w, Metric metric, String label, SessionReport.Measure measure) {
        StatTile tile = new StatTile(x, y, w, Icons.of(metric), label, Fmt.measure(measure));
        return measure.isAvailable() ? tile : tile.muted();
    }

    /**
     * A tile for a figure made from items. While a session is running and nothing has been seen
     * to come in yet, it says so: sack items are only reported every half a minute or so, and
     * until then the figure is not zero, it is not known.
     */
    private static StatTile itemTile(int x, int y, int w, Metric metric, String label, SessionReport report) {
        if (report.session.isRunning() && report.session.buckets.isEmpty()) {
            return new StatTile(x, y, w, Icons.of(metric), label, "waiting").muted();
        }
        return tile(x, y, w, metric, label, report.rate(metric));
    }

    /** A last line for a tooltip, saying how the figure is known and anything that qualifies it. */
    private static String known(SessionReport.Measure measure) {
        String note = measure.note() == null || measure.note().isEmpty() ? "" : ". " + measure.note();
        return "\n\nHow known: " + measure.provenance().label + note;
    }

    /** The session's totals, each with its hourly rate and how it is known. */
    static void fillTotals(LabTable table, SessionReport report) {
        table.section("TOTALS");
        table.head("", "", "Total", "Per hour", "Known");
        total(table, Metric.GROSS_PER_HOUR, "Items gained", report.grossValue(), report.rate(Metric.GROSS_PER_HOUR));
        if (report.itemsLost > 0) total(table, null, "Items lost", report.lossValue(), null);
        if (report.manualCosts != 0) {
            total(table, null, "Costs", new SessionReport.Measure(report.manualCosts, Provenance.MANUAL, ""), null);
        }
        total(table, Metric.NET_PER_HOUR, "Net", report.net(), report.rate(Metric.NET_PER_HOUR));
        total(table, Metric.PURSE_PER_HOUR, "Purse", report.purseChange(), report.rate(Metric.PURSE_PER_HOUR));

        double hours = SessionTimes.hours(report.trackedMillis);
        if (report.session.xp.isEmpty()) total(table, Metric.XP_PER_HOUR, "Skill XP", report.skillXp(), null);
        for (Map.Entry<String, Session.XpGain> xp : report.session.xp.entrySet()) {
            Provenance provenance = xp.getValue().exact ? Provenance.CALCULATED : Provenance.ESTIMATED;
            table.add(xp.getKey() + " XP", "", Fmt.number(xp.getValue().amount),
                            hours > 0 ? Fmt.number(xp.getValue().amount / hours) : "-", provenance.label)
                    .icon(0, Icons.of(Metric.XP_PER_HOUR))
                    .dot(4, Icons.color(provenance)).color(4, Theme.TEXT_MUTED);
        }
        if (report.unpriced > 0) {
            table.note(report.unpriced + (report.unpriced == 1 ? " item has" : " items have")
                    + " no price and is left out of the values", Theme.WARN);
        }
        fillActivity(table, report);
    }

    private static void total(LabTable table, Metric icon, String label, SessionReport.Measure total,
                              SessionReport.Measure rate) {
        LabTable.Row row = table.add(label, "", Fmt.measure(total), rate == null ? "" : Fmt.measure(rate),
                total.provenance().label);
        if (icon != null) row.icon(0, Icons.of(icon));
        row.dot(4, Icons.color(total.provenance())).color(4, Theme.TEXT_MUTED);
        if (!total.isAvailable()) row.color(2, Theme.TEXT_MUTED).color(3, Theme.TEXT_MUTED);
    }

    /**
     * The session's items, biggest gain first. With onClick, a row can be clicked, and is
     * given the item's name: the session's own screen uses that to leave an item out of the value.
     */
    static void fillItems(LabTable table, SessionReport report, Consumer<String> onClick) {
        table.section("ITEMS");
        if (report.lines.isEmpty()) {
            table.text("Nothing has come or gone yet.", Theme.TEXT_MUTED);
            return;
        }
        table.head("Item", "Gained", "Lost", "Each", "Value");
        long moved = 0;
        long unsure = 0;
        for (SessionReport.Line line : report.lines) {
            moved += line.total().transferred;
            unsure += line.total().uncertain;
            // Only what there is more or less of at the end is listed. What came in and went out
            // again, such as logs a compactor turned into enchanted logs, cancels out.
            if (line.total().net() == 0) continue;
            long gained = line.total().netGain();
            long lost = line.total().netLoss();
            Double value = lost > 0 && line.lossValue() != null ? Double.valueOf(-line.lossValue()) : line.gainValue();
            LabTable.Row row = table.add(line.total().name + (line.total().manual ? " *" : ""),
                    gained > 0 ? Fmt.number((double) gained) : "",
                    lost > 0 ? Fmt.number((double) lost) : "",
                    line.price().excluded() ? "off" : Fmt.number(line.price().unit()),
                    line.price().excluded() ? "-" : Fmt.number(value));
            if (lost > 0) row.color(2, Theme.DANGER).color(4, Theme.DANGER);
            if (line.price().excluded()) row.color(Theme.TEXT_MUTED);
            else if (!line.price().isKnown()) row.color(3, Theme.WARN).color(4, Theme.WARN);
            if (onClick != null) row.onClick(column -> onClick.accept(line.total().name));
        }
        if (report.passedThrough > 0) {
            table.note(report.passedThrough + (report.passedThrough == 1 ? " item" : " items")
                    + " came and went (compacted, used or moved): cancelled out", Theme.TEXT_MUTED);
        }
        if (moved > 0) table.note(Fmt.number((double) moved) + " moved between inventory and sacks: not counted", Theme.TEXT_MUTED);
        if (unsure > 0) table.note(Fmt.number((double) unsure) + " changed with a menu open: not counted", Theme.TEXT_MUTED);
        if (!report.sackMessagesSeen && report.trackedMillis > NO_SACKS_AFTER_MILLIS) {
            table.note("No [Sacks] message seen: items that go straight to sacks are missed. "
                    + "It is switched on in the SkyBlock settings", Theme.WARN);
        }
    }
}
