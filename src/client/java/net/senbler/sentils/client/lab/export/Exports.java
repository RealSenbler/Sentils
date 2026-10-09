package net.senbler.sentils.client.lab.export;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.senbler.sentils.client.lab.analysis.Metric;
import net.senbler.sentils.client.lab.analysis.SessionReport;
import net.senbler.sentils.client.lab.model.Experiment;
import net.senbler.sentils.client.lab.model.Session;
import net.senbler.sentils.client.lab.model.Setup;
import net.senbler.sentils.client.lab.store.LabStore;

/**
 * Turns records into text to be written to a file, and reads them back. CSV is for looking at
 * in a spreadsheet and holds worked-out figures. JSON holds the records themselves, complete,
 * and is what can be imported again. Neither contains anything but what the Lab recorded.
 */
public final class Exports {

    private Exports() {}

    public static final int BUNDLE_VERSION = 1;

    private static final List<String> SESSION_COLUMNS = List.of(
            "session_id", "name", "activity", "start", "end", "elapsed_seconds", "paused_seconds",
            "tracked_seconds", "estimated_active_seconds", "setup", "tags", "location",
            "items_gained", "items_lost", "est_item_value", "est_loss_value", "costs", "est_net_value",
            "est_net_per_hour", "purse_change", "purse_change_per_hour", "skill_xp", "skill_xp_per_hour",
            // activity_count is what the activity is scored in; counters is everything counted, as name=amount.
            "activity_count", "activity_count_per_hour", "counters",
            "valuation", "price_source", "price_time", "items_without_price", "interrupted", "notes");

    private static final List<String> ITEM_COLUMNS = List.of(
            // gained and lost are the difference between start and end; came_in and went_out are
            // every arrival and departure seen, including what was compacted or moved on.
            "session_id", "session_name", "item", "item_id", "gained", "lost", "came_in", "went_out", "moved", "uncertain",
            "unit_price", "price_source", "est_gain_value");

    /** One row per session. A figure that is unavailable is an empty cell. */
    public static String sessionsCsv(List<SessionReport> reports, Function<String, Setup> setups) {
        StringBuilder csv = new StringBuilder(Csv.BYTE_ORDER_MARK).append(Csv.row(SESSION_COLUMNS));
        for (SessionReport report : reports) {
            Session session = report.session;
            Setup setup = setups.apply(session.setupId);
            List<String> row = new ArrayList<>();
            row.add(Csv.text(session.id));
            row.add(Csv.text(session.name));
            row.add(Csv.text(session.activityType().label));
            row.add(Csv.text(Instant.ofEpochMilli(session.startMillis).toString()));
            row.add(Csv.text(session.endMillis != 0 ? Instant.ofEpochMilli(session.endMillis).toString() : ""));
            row.add(Long.toString(report.elapsedMillis / 1000));
            row.add(Long.toString(report.pausedMillis / 1000));
            row.add(Long.toString(report.trackedMillis / 1000));
            row.add(Long.toString(report.estimatedActiveMillis / 1000));
            row.add(Csv.text(setup != null ? setup.name : ""));
            row.add(Csv.text(String.join("; ", session.tags)));
            row.add(Csv.text(session.location));
            row.add(Long.toString(report.itemsGained));
            row.add(Long.toString(report.itemsLost));
            row.add(Csv.number(report.grossValue().value()));
            row.add(Csv.number(report.lossValue().value()));
            row.add(Csv.number(report.manualCosts));
            row.add(Csv.number(report.net().value()));
            row.add(Csv.number(report.rate(Metric.NET_PER_HOUR).value()));
            row.add(Csv.number(report.purseChange().value()));
            row.add(Csv.number(report.rate(Metric.PURSE_PER_HOUR).value()));
            row.add(Csv.number(report.skillXp().value()));
            row.add(Csv.number(report.rate(Metric.XP_PER_HOUR).value()));
            row.add(Csv.number(report.activityCount().value()));
            row.add(Csv.number(report.rate(Metric.ACTIVITY_PER_HOUR).value()));
            List<String> counters = new ArrayList<>();
            session.counters.forEach((name, amount) -> counters.add(name + "=" + Csv.number(amount)));
            row.add(Csv.text(String.join("; ", counters)));
            row.add(Csv.text(report.assumptions.modeType().label));
            row.add(Csv.text(report.prices != null ? report.prices.source : ""));
            row.add(Csv.text(report.prices != null ? Instant.ofEpochMilli(report.prices.retrievedMillis).toString() : ""));
            row.add(Integer.toString(report.unpriced));
            row.add(session.recovered ? "yes" : "no");
            row.add(Csv.text(session.notes));
            csv.append(Csv.row(row));
        }
        return csv.toString();
    }

    /** One row per item per session. */
    public static String itemsCsv(List<SessionReport> reports) {
        StringBuilder csv = new StringBuilder(Csv.BYTE_ORDER_MARK).append(Csv.row(ITEM_COLUMNS));
        for (SessionReport report : reports) {
            for (SessionReport.Line line : report.lines) {
                List<String> row = new ArrayList<>();
                row.add(Csv.text(report.session.id));
                row.add(Csv.text(report.session.name));
                row.add(Csv.text(line.total().name));
                row.add(Csv.text(line.total().id));
                row.add(Long.toString(line.total().netGain()));
                row.add(Long.toString(line.total().netLoss()));
                row.add(Long.toString(line.total().gained));
                row.add(Long.toString(line.total().lost));
                row.add(Long.toString(line.total().transferred));
                row.add(Long.toString(line.total().uncertain));
                row.add(Csv.number(line.price().unit()));
                row.add(Csv.text(line.price().source()));
                row.add(Csv.number(line.gainValue()));
                csv.append(Csv.row(row));
            }
        }
        return csv.toString();
    }

    /** Everything, complete: sessions with their observations and price snapshots, setups and experiments. */
    public static String bundleJson(List<Session> sessions, List<Setup> setups, List<Experiment> experiments,
                                    long now) {
        JsonObject bundle = new JsonObject();
        bundle.addProperty("format", "sentils-efficiency-lab");
        bundle.addProperty("bundleVersion", BUNDLE_VERSION);
        bundle.addProperty("exportedMillis", now);
        bundle.add("sessions", LabStore.gson().toJsonTree(sessions));
        bundle.add("setups", LabStore.gson().toJsonTree(setups));
        bundle.add("experiments", LabStore.gson().toJsonTree(experiments));
        return LabStore.gson().toJson(bundle);
    }

    /** What an exported file turned out to hold, and what in it could not be used. */
    public static final class Imported {
        public final List<Session> sessions = new ArrayList<>();
        public final List<Setup> setups = new ArrayList<>();
        public final List<Experiment> experiments = new ArrayList<>();
        public final List<String> problems = new ArrayList<>();
    }

    /**
     * Reads a file made by bundleJson, or a single session file. Anything in it that is not a
     * usable record is left out and listed among the problems; the rest is still read.
     */
    public static Imported readBundle(String json) {
        Imported imported = new Imported();
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            imported.problems.add("The file is not valid JSON");
            return imported;
        }
        if (!(parsed instanceof JsonObject root)) {
            imported.problems.add("The file is not an Efficiency Lab export");
            return imported;
        }

        if (!root.has("sessions")) {
            // A lone session file, as kept in the sessions folder.
            readSession(root, imported);
            return imported;
        }
        if (root.get("sessions") instanceof JsonArray sessions) {
            for (JsonElement element : sessions) readSession(element, imported);
        } else {
            imported.problems.add("\"sessions\" is not a list");
        }
        if (root.get("setups") instanceof JsonArray setups) {
            for (JsonElement element : setups) {
                try {
                    Setup setup = LabStore.gson().fromJson(element, Setup.class);
                    if (setup != null && setup.id != null && setup.name != null) imported.setups.add(setup);
                } catch (RuntimeException e) {
                    imported.problems.add("A setup could not be read");
                }
            }
        }
        if (root.get("experiments") instanceof JsonArray experiments) {
            for (JsonElement element : experiments) {
                try {
                    Experiment experiment = LabStore.gson().fromJson(element, Experiment.class);
                    if (experiment == null || experiment.id == null) continue;
                    if (experiment.variants == null) experiment.variants = new ArrayList<>();
                    experiment.variants.removeIf(variant -> variant == null || variant.sessionIds == null);
                    imported.experiments.add(experiment);
                } catch (RuntimeException e) {
                    imported.problems.add("An experiment could not be read");
                }
            }
        }
        return imported;
    }

    private static void readSession(JsonElement element, Imported imported) {
        try {
            Session session = LabStore.parseSession(element.toString());
            if (session == null) {
                imported.problems.add("A session is from a newer version of the mod and was left out");
            } else if (session.isRunning()) {
                imported.problems.add("Session " + session.id + " never ended and was left out");
            } else {
                imported.sessions.add(session);
            }
        } catch (RuntimeException e) {
            imported.problems.add("A session could not be read: " + e.getMessage());
        }
    }
}
