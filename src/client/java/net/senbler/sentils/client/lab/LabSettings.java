package net.senbler.sentils.client.lab;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import net.senbler.sentils.client.Log;
import net.senbler.sentils.client.lab.analysis.Metric;
import net.senbler.sentils.client.lab.price.Assumptions;
import net.senbler.sentils.client.lab.store.LabStore;

public final class LabSettings {

    private LabSettings() {}

    public static boolean showHud = true;
    /** The HUD folded down to its clock alone, with the button on it. */
    public static boolean hudMinimized = false;
    public static boolean pauseOnDisconnect = true;
    /** The metric shown in the history, on the HUD and in trends. */
    public static String primaryMetric = Metric.NET_PER_HOUR.name();
    /** Value every session with the latest prices rather than each with the prices it was recorded with. */
    public static boolean latestPrices = false;
    /** Fetch the prices as each session ends, so that it is valued at what its items are worth then. */
    public static boolean refreshPricesOnEnd = true;
    /** The activity last started, which the start key and a bare /lab start use again. */
    public static String lastActivity = "FARMING";
    /** How items are valued: the mode, the tax, your own prices and the items you leave out. */
    public static Assumptions assumptions = new Assumptions();

    private static Path file;

    private static class Data {
        boolean showHud = true;
        boolean hudMinimized = false;
        boolean pauseOnDisconnect = true;
        String primaryMetric = Metric.NET_PER_HOUR.name();
        boolean latestPrices = false;
        boolean refreshPricesOnEnd = true;
        String lastActivity = "FARMING";
        Assumptions assumptions = new Assumptions();
    }

    public static Metric metric() {
        return Metric.parse(primaryMetric);
    }

    /** Call once, with the Lab's folder, before anything reads the settings. */
    public static void load(Path dir) {
        file = dir.resolve("settings.json");
        if (!Files.exists(file)) return;
        try {
            Data d = LabStore.gson().fromJson(Files.readString(file, StandardCharsets.UTF_8), Data.class);
            if (d != null) {
                showHud = d.showHud;
                hudMinimized = d.hudMinimized;
                pauseOnDisconnect = d.pauseOnDisconnect;
                primaryMetric = Metric.parse(d.primaryMetric).name();
                latestPrices = d.latestPrices;
                refreshPricesOnEnd = d.refreshPricesOnEnd;
                if (d.lastActivity != null) lastActivity = d.lastActivity;
                if (d.assumptions != null && d.assumptions.manualPrices != null && d.assumptions.excluded != null) {
                    assumptions = d.assumptions;
                    assumptions.mode = assumptions.modeType().name();
                    if (!Double.isFinite(assumptions.bazaarTaxPercent)) assumptions.bazaarTaxPercent = 1.25;
                }
            }
        } catch (IOException | RuntimeException e) {
            Log.warn("Failed to load Efficiency Lab settings: " + e.getMessage());
        }
    }

    public static void save() {
        if (file == null) return;
        Data d = new Data();
        d.showHud = showHud;
        d.hudMinimized = hudMinimized;
        d.pauseOnDisconnect = pauseOnDisconnect;
        d.primaryMetric = primaryMetric;
        d.latestPrices = latestPrices;
        d.refreshPricesOnEnd = refreshPricesOnEnd;
        d.lastActivity = lastActivity;
        d.assumptions = assumptions;
        LabStore.write(file, LabStore.gson().toJson(d));
    }
}
