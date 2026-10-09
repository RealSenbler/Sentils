package net.senbler.sentils.client.lab.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


import net.senbler.sentils.client.gui.Theme;
import net.senbler.sentils.client.lab.Fmt;
import net.senbler.sentils.client.lab.Lab;
import net.senbler.sentils.client.lab.LabSettings;
import net.senbler.sentils.client.lab.analysis.Comparison;
import net.senbler.sentils.client.lab.analysis.Metric;
import net.senbler.sentils.client.lab.analysis.SessionReport;
import net.senbler.sentils.client.lab.core.SessionTimes;

/**
 * Lays a comparison out as a scorecard: one row for each metric, with the baseline's figure,
 * each other side's, and an arrow for whether that side is higher or lower. Under it are the
 * sessions behind the figures and the items each side brought in.
 */
final class ComparisonView {

    private ComparisonView() {}

    private static final int COLUMNS = 7;
    private static final int MAX_SIDES = 3; // the baseline and two others is what there are columns for
    private static final int TOP_ITEMS = 6;

    static float[] weights() {
        return new float[] {2.3f, 1.4f, 1.4f, 1.7f, 1.4f, 1.7f, 1.9f};
    }

    private static String[] cells(String first) {
        String[] cells = new String[COLUMNS];
        Arrays.fill(cells, "");
        cells[0] = first;
        return cells;
    }

    /** The column a side's figure goes in: the baseline's, then each other side's with its change after it. */
    private static int column(int side) {
        return side == 0 ? 1 : side * 2;
    }

    /**
     * Fills the table from the groups, the first of which is the baseline. The primary metric
     * is the one picked out, and the one the list of sessions goes by.
     */
    static void fill(LabTable table, List<Comparison.Group> groups, Metric primary) {
        int sides = Math.min(groups.size(), MAX_SIDES);
        // With two sides the reading sits straight after the change, where it has room to be read in full.
        int readingColumn = sides > 2 ? 6 : 4;
        if (sides > 2) table.alignRight(1, 2, 3, 4, 5);
        else table.alignRight(1, 2, 3, 5);

        String[] head = cells("");
        for (int side = 0; side < sides; side++) head[column(side)] = groups.get(side).name();
        if (sides > 1) head[readingColumn] = "Reading";
        table.head(head);

        Comparison.Result main = null;
        for (Metric metric : Metric.values()) {
            Comparison.Result result = Comparison.compare(groups, metric);
            if (metric == primary) main = result;

            String[] cells = cells(metric.shortLabel);
            LabTable.Row row = table.add(cells).icon(0, Icons.of(metric)).selected(metric == primary);
            Comparison.Verdict reading = null;
            for (int side = 0; side < sides; side++) {
                Comparison.GroupResult group = result.groups.get(side);
                cells[column(side)] = Fmt.number(group.median);
                if (group.median == null) row.color(column(side), Theme.TEXT_MUTED);
                if (side == 0) continue;
                // The most cautious reading of the sides shown is the one given.
                if (reading == null || group.verdict.ordinal() < reading.ordinal()) reading = group.verdict;
                if (group.medianDifference == null) continue;
                cells[column(side) + 1] = group.medianPercent == null ? "" : String.format(Locale.ROOT, "%.1f%%",
                        Math.abs(group.medianPercent));
                if (group.medianDifference == 0) cells[column(side) + 1] = "same";
                row.arrow(column(side) + 1, group.medianDifference);
            }
            if (reading != null) {
                cells[readingColumn] = reading.label;
                row.dot(readingColumn, Icons.color(reading)).color(readingColumn, Theme.TEXT_MUTED);
            }
        }

        if (groups.size() > MAX_SIDES) {
            table.note("Showing " + MAX_SIDES + " of " + groups.size() + " sides. Next baseline brings the others round.",
                    Theme.TEXT_MUTED);
        }
        if (main == null) return;
        for (String warning : main.warnings) table.note(warning, Theme.WARN);

        table.section("SESSIONS");
        for (int side = 0; side < sides; side++) {
            Comparison.GroupResult group = main.groups.get(side);
            String[] title = cells(group.name);
            title[5] = "Time";
            title[6] = primary.shortLabel;
            table.head(title);
            for (int i = 0; i < group.used.size(); i++) {
                SessionReport report = group.used.get(i);
                boolean outlier = group.outliers.get(i);
                String[] cells = cells(Fmt.shortDate(report.session.startMillis) + "  " + report.session.displayName());
                cells[5] = Fmt.duration(report.trackedMillis);
                cells[6] = Fmt.number(group.rates.get(i)) + (outlier ? " *" : "");
                LabTable.Row row = table.add(cells);
                if (outlier) row.color(6, Theme.WARN);
            }
            if (group.used.isEmpty()) table.text("  none with " + primary.label.toLowerCase(Locale.ROOT), Theme.TEXT_MUTED);
        }

        List<Map.Entry<String, double[]>> items = itemRates(main, sides);
        if (!items.isEmpty()) {
            table.section("ITEMS PER HOUR");
            String[] itemHead = cells("Item");
            for (int side = 0; side < sides; side++) itemHead[column(side)] = main.groups.get(side).name;
            table.head(itemHead);
            for (Map.Entry<String, double[]> item : items) {
                String[] cells = cells(item.getKey());
                LabTable.Row row = table.add(cells);
                double base = item.getValue()[0];
                for (int side = 0; side < sides; side++) {
                    double rate = item.getValue()[side];
                    cells[column(side)] = rate == 0 ? "-" : Fmt.number(rate);
                    if (side > 0 && rate != base) row.arrow(column(side), rate - base);
                }
            }
        }

        if (sides > 1) {
            table.section("READING");
            table.note("Too few sessions on a side to say", Icons.color(Comparison.Verdict.PRELIMINARY));
            table.note("The sides' sessions overlap", Icons.color(Comparison.Verdict.INCONCLUSIVE));
            table.note("Held in every session so far", Icons.color(Comparison.Verdict.CONSISTENT));
        }
    }

    /** The most gained items across the sides, each with its hourly rate on every side. */
    private static List<Map.Entry<String, double[]>> itemRates(Comparison.Result result, int sides) {
        Map<String, double[]> gained = new LinkedHashMap<>();
        for (int i = 0; i < sides; i++) {
            Comparison.GroupResult group = result.groups.get(i);
            double hours = SessionTimes.hours(group.trackedMillis);
            if (hours <= 0) continue;
            for (SessionReport report : group.used) {
                for (SessionReport.Line line : report.lines) {
                    if (line.total().netGain() == 0) continue;
                    gained.computeIfAbsent(line.total().name, name -> new double[sides])[i] += line.total().netGain() / hours;
                }
            }
        }
        List<Map.Entry<String, double[]>> items = new ArrayList<>(gained.entrySet());
        items.sort(Comparator.comparingDouble((Map.Entry<String, double[]> item) -> {
            double most = 0;
            for (double rate : item.getValue()) most = Math.max(most, rate);
            return most;
        }).reversed());
        return items.subList(0, Math.min(TOP_ITEMS, items.size()));
    }

    /** A few words on which prices the figures rest on, for under a comparison. */
    static String priceBasis() {
        return LabSettings.latestPrices && Lab.prices() != null
                ? "Latest prices, " + Fmt.shortDate(Lab.prices().retrievedMillis)
                : "Prices as recorded";
    }
}
