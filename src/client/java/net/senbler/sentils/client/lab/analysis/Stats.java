package net.senbler.sentils.client.lab.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The few summary statistics the comparisons use. Every one needs at least one value. */
public final class Stats {

    private Stats() {}

    // Scales the median absolute deviation to match a standard deviation for bell-shaped data.
    private static final double MAD_SCALE = 1.4826;
    private static final double OUTLIER_DEVIATIONS = 3.0;
    private static final int OUTLIER_MIN_VALUES = 4;

    public static double mean(List<Double> values) {
        double sum = 0;
        for (double value : values) sum += value;
        return sum / values.size();
    }

    public static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    public static double min(List<Double> values) {
        return Collections.min(values);
    }

    public static double max(List<Double> values) {
        return Collections.max(values);
    }

    /** The sample standard deviation, or 0 for a single value. */
    public static double deviation(List<Double> values) {
        if (values.size() < 2) return 0;
        double mean = mean(values);
        double squares = 0;
        for (double value : values) squares += (value - mean) * (value - mean);
        return Math.sqrt(squares / (values.size() - 1));
    }

    /**
     * Whether the value stands far apart from the rest: more than three robust deviations from
     * the median. With fewer than four values nothing is called an outlier, as there is too
     * little to say what is usual.
     */
    public static boolean isOutlier(double value, List<Double> values) {
        if (values.size() < OUTLIER_MIN_VALUES) return false;
        double median = median(values);
        List<Double> distances = new ArrayList<>();
        for (double other : values) distances.add(Math.abs(other - median));
        double spread = median(distances) * MAD_SCALE;
        return spread > 0 && Math.abs(value - median) > OUTLIER_DEVIATIONS * spread;
    }

    /** How much bigger b is than a, as a percentage of a, or null if a is zero. */
    public static Double percentChange(double a, double b) {
        return a == 0 ? null : (b - a) / Math.abs(a) * 100.0;
    }
}
