package net.senbler.sentils.client.lab.analysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.senbler.sentils.client.lab.core.SessionTimes;
import net.senbler.sentils.client.lab.model.Provenance;

/**
 * Compares groups of sessions on one metric. The first group is the baseline the others are
 * measured against. It reports what the recorded sessions show and how far that can be leaned
 * on; it does not pick a winner, and makes no claim of statistical significance.
 */
public final class Comparison {

    private Comparison() {}

    private static final int MIN_SESSIONS_FOR_A_VERDICT = 3;
    private static final double DURATION_RATIO_WARNING = 2.0;
    private static final long PRICE_GAP_WARNING_MILLIS = 24 * 3_600_000L;

    /** Sessions to be taken together: those of one setup, one variant, or a single session. */
    public record Group(String name, List<SessionReport> reports) {}

    public enum Verdict {
        /** One side has no session with this metric. */
        NO_DATA("No data"),
        /** Too few sessions on a side for the difference to mean much. */
        PRELIMINARY("Preliminary"),
        /** The two sides' sessions overlap: the difference is within their session-to-session variation. */
        INCONCLUSIVE("Inconclusive"),
        /** Every session on one side beat every session on the other. */
        CONSISTENT("Consistent");

        public final String label;

        Verdict(String label) {
            this.label = label;
        }
    }

    public static final class GroupResult {
        public String name;
        public int sessions; // how many were given
        public List<SessionReport> used = new ArrayList<>(); // the ones the metric is available for
        public List<Double> rates = new ArrayList<>(); // each used session's rate, in the same order
        public List<Boolean> outliers = new ArrayList<>();
        public long trackedMillis; // of the used sessions
        public long medianTrackedMillis;
        public Provenance provenance = Provenance.UNAVAILABLE;

        /** All the used sessions as one: their total over their total time. Null if none could be used. */
        public Double pooledRate;
        public Double total;
        public Double mean;
        public Double median;
        public Double min;
        public Double max;
        public Double deviation;

        // Against the baseline; all null for the baseline itself or where either side has no data.
        public Double medianDifference;
        public Double medianPercent;
        public Double pooledDifference;
        public Double pooledPercent;
        public Verdict verdict;
        public String summary;
    }

    public static final class Result {
        public Metric metric;
        public List<GroupResult> groups = new ArrayList<>();
        public List<String> warnings = new ArrayList<>();
    }

    public static Result compare(List<Group> groups, Metric metric) {
        Result result = new Result();
        result.metric = metric;
        for (Group group : groups) result.groups.add(summarize(group, metric));

        GroupResult baseline = result.groups.isEmpty() ? null : result.groups.getFirst();
        for (int i = 1; i < result.groups.size(); i++) against(baseline, result.groups.get(i), metric);
        warn(result, groups);
        return result;
    }

    private static GroupResult summarize(Group group, Metric metric) {
        GroupResult result = new GroupResult();
        result.name = group.name();
        result.sessions = group.reports().size();

        double total = 0;
        List<Double> durations = new ArrayList<>();
        Provenance provenance = Provenance.OBSERVED;
        for (SessionReport report : group.reports()) {
            SessionReport.Measure rate = report.rate(metric);
            if (!rate.isAvailable()) continue;
            result.used.add(report);
            result.rates.add(rate.value());
            result.trackedMillis += report.trackedMillis;
            durations.add((double) report.trackedMillis);
            total += report.total(metric).value();
            provenance = provenance.and(rate.provenance());
        }
        if (result.used.isEmpty()) return result;

        result.provenance = provenance;
        result.total = total;
        result.pooledRate = total / SessionTimes.hours(result.trackedMillis);
        result.mean = Stats.mean(result.rates);
        result.median = Stats.median(result.rates);
        result.min = Stats.min(result.rates);
        result.max = Stats.max(result.rates);
        result.deviation = Stats.deviation(result.rates);
        result.medianTrackedMillis = Math.round(Stats.median(durations));
        for (double rate : result.rates) result.outliers.add(Stats.isOutlier(rate, result.rates));
        return result;
    }

    private static void against(GroupResult baseline, GroupResult other, Metric metric) {
        if (baseline.used.isEmpty() || other.used.isEmpty()) {
            other.verdict = Verdict.NO_DATA;
            other.summary = "There is no session with " + metric.label.toLowerCase() + " on one side, so nothing can be said.";
            return;
        }
        other.medianDifference = other.median - baseline.median;
        other.medianPercent = Stats.percentChange(baseline.median, other.median);
        other.pooledDifference = other.pooledRate - baseline.pooledRate;
        other.pooledPercent = Stats.percentChange(baseline.pooledRate, other.pooledRate);

        if (Math.min(baseline.used.size(), other.used.size()) < MIN_SESSIONS_FOR_A_VERDICT) {
            other.verdict = Verdict.PRELIMINARY;
        } else if (other.min > baseline.max || other.max < baseline.min) {
            other.verdict = Verdict.CONSISTENT;
        } else {
            other.verdict = Verdict.INCONCLUSIVE;
        }
        other.summary = sentence(baseline, other, metric);
    }

    private static String sentence(GroupResult baseline, GroupResult other, Metric metric) {
        String what = "median " + metric.label.toLowerCase();
        String counts = " across " + other.used.size() + " and " + baseline.used.size() + " recorded sessions. ";
        String finding;
        if (other.medianDifference == 0) {
            finding = other.name + " and " + baseline.name + " had the same " + what + counts;
        } else if (other.medianPercent == null) {
            finding = other.name + " had a " + (other.medianDifference > 0 ? "higher " : "lower ") + what
                    + " than " + baseline.name + counts;
        } else {
            finding = other.name + " had " + String.format("%.1f", Math.abs(other.medianPercent)) + "% "
                    + (other.medianDifference > 0 ? "higher " : "lower ") + what + " than " + baseline.name + counts;
        }
        return finding + switch (other.verdict) {
            case PRELIMINARY -> "With fewer than " + MIN_SESSIONS_FOR_A_VERDICT
                    + " sessions on a side this is preliminary: it may not hold up.";
            case INCONCLUSIVE -> "The two sets of sessions overlap, so the difference is within their "
                    + "session-to-session variation.";
            case CONSISTENT -> "Every session on the better side beat every session on the other. More "
                    + "comparable sessions would show whether that keeps up.";
            case NO_DATA -> "";
        };
    }

    /** Notes whatever makes the groups less than like for like. */
    private static void warn(Result result, List<Group> groups) {
        Set<String> activities = new HashSet<>();
        int leftOut = 0;
        int outliers = 0;
        long earliestPrices = Long.MAX_VALUE;
        long latestPrices = Long.MIN_VALUE;
        long shortest = Long.MAX_VALUE;
        long longest = 0;
        boolean estimated = false;

        for (GroupResult group : result.groups) {
            leftOut += group.sessions - group.used.size();
            for (boolean outlier : group.outliers) if (outlier) outliers++;
            if (group.used.isEmpty()) continue;
            shortest = Math.min(shortest, group.medianTrackedMillis);
            longest = Math.max(longest, group.medianTrackedMillis);
            estimated |= group.provenance == Provenance.ESTIMATED;
            for (SessionReport report : group.used) {
                activities.add(report.session.activity);
                if (report.prices != null) {
                    earliestPrices = Math.min(earliestPrices, report.prices.retrievedMillis);
                    latestPrices = Math.max(latestPrices, report.prices.retrievedMillis);
                }
            }
        }

        // Kept short: these are shown as notes under a comparison.
        if (leftOut > 0) {
            result.warnings.add(leftOut + (leftOut == 1 ? " session" : " sessions") + " left out: no "
                    + result.metric.label.toLowerCase());
        }
        if (activities.size() > 1) result.warnings.add("Different activities are mixed");
        if (longest > 0 && shortest > 0 && (double) longest / shortest > DURATION_RATIO_WARNING) {
            result.warnings.add("Session lengths differ a lot between sides");
        }
        boolean priced = result.metric == Metric.NET_PER_HOUR || result.metric == Metric.GROSS_PER_HOUR;
        if (priced && latestPrices - earliestPrices > PRICE_GAP_WARNING_MILLIS) {
            result.warnings.add("Prices are from different days: switch to latest prices");
        }
        if (outliers > 0) {
            result.warnings.add(outliers + (outliers == 1 ? " session is" : " sessions are")
                    + " far apart from the rest (*)");
        }
        if (estimated) result.warnings.add("Estimates: they rest on prices or inexact XP");
    }
}
