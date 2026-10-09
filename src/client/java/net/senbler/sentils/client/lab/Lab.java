package net.senbler.sentils.client.lab;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import net.senbler.sentils.client.Log;
import net.senbler.sentils.client.config.ConfigFiles;
import net.senbler.sentils.client.lab.analysis.ItemTotals;
import net.senbler.sentils.client.lab.analysis.SessionReport;
import net.senbler.sentils.client.lab.core.ItemChange;
import net.senbler.sentils.client.lab.core.SessionRecorder;
import net.senbler.sentils.client.lab.core.SessionTimes;
import net.senbler.sentils.client.lab.export.Exports;
import net.senbler.sentils.client.lab.model.Activity;
import net.senbler.sentils.client.lab.model.Experiment;
import net.senbler.sentils.client.lab.model.Session;
import net.senbler.sentils.client.lab.model.Setup;
import net.senbler.sentils.client.lab.price.HttpPriceTransport;
import net.senbler.sentils.client.lab.price.PriceService;
import net.senbler.sentils.client.lab.price.PriceSnapshot;
import net.senbler.sentils.client.lab.store.LabStore;
import net.senbler.sentils.client.lab.ui.LabPanelScreen;
import net.senbler.sentils.client.skyblock.SkyblockLocation;

/**
 * The Efficiency Lab: records what a stretch of play brought in, keeps every session on this
 * computer, and compares them. This class ties the parts together for the commands, the HUD
 * and the screens.
 * <p>
 * Everything it records is read from what the client is shown anyway. It sends nothing to the
 * server and makes no web request, with one exception: fetching the public price lists, when
 * the player asks for it and, unless they switch it off, once as each session ends.
 */
public final class Lab {

    private Lab() {}

    private static LabStore store;
    private static SessionRecorder recorder;
    private static PriceService prices;
    private static Session lastEnded; // until the sack message that covers its end has come
    private static volatile boolean refreshing;
    private static String priceStatus = ""; // how the last price refresh went

    /** Call once from your ClientModInitializer. */
    public static void init() {
        Path dir = ConfigFiles.resolve("lab/settings.json").getParent();
        LabSettings.load(dir);
        store = new LabStore(dir);
        store.load();
        recorder = new SessionRecorder(System::currentTimeMillis);
        prices = new PriceService(new HttpPriceTransport(), System::currentTimeMillis, store.loadPrices());
        recoverInterrupted();
        LabCollectors.init();
        LabActivityCollectors.init();
    }

    public static LabStore store() {
        return store;
    }

    public static SessionRecorder recorder() {
        return recorder;
    }

    /** The newest prices there are, or null if none have ever been fetched. */
    public static PriceSnapshot prices() {
        return prices.current();
    }

    /** The session being recorded, or null. */
    public static Session active() {
        return recorder.active();
    }

    /** Shows a line in chat, to this player only. */
    public static void say(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("Sentils Lab: ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE)));
    }

    // --- The session being recorded ---

    /** Starts a session. Returns what to tell the player. */
    public static String start(Activity activity, String name, String setupId) {
        if (recorder.isActive()) return "A session is already running. End it with /lab stop first";
        Session session = recorder.start(activity, name);
        session.setupId = setupId;
        session.location = SkyblockLocation.getIsland();
        LabCollectors.reset();
        store.saveActive(session);
        return "Started a " + activity.label + " session" + (session.name.isEmpty() ? "" : " \"" + session.name + "\"");
    }

    public static String pause(String reason) {
        if (!recorder.isActive()) return "There is no session running";
        if (!recorder.pause(reason)) return "The session is already paused";
        store.saveActive(recorder.active());
        return "Paused. Nothing is recorded until /lab resume";
    }

    public static String resume() {
        if (!recorder.isActive()) return "There is no session running";
        if (!recorder.resume()) return "The session is not paused";
        LabCollectors.reset();
        store.saveActive(recorder.active());
        return "Resumed";
    }

    /** Ends the session and saves it. Returns it, or null if there was none. */
    public static Session stop() {
        Session session = recorder.stop();
        if (session == null) return null;
        finish(session);
        store.save(session);
        store.clearActive();
        lastEnded = session;
        if (LabSettings.refreshPricesOnEnd) priceAtEnd(session);
        return session;
    }

    /**
     * Fetches the prices as a session ends, so that it is valued at what its items are worth
     * then and not at whatever was fetched last. The session has already been saved with the
     * prices there were; if newer ones arrive, in a second or two, they take their place.
     * Prices fetched within the last few minutes are new enough, and nothing is fetched again.
     */
    private static void priceAtEnd(Session session) {
        fetchPrices(result -> {
            if (result.outcome() == PriceService.Outcome.UPDATED) {
                PriceSnapshot fresh = prices.current();
                session.prices = fresh.subset(itemIds(session, fresh));
                store.save(session);
                // A report of it that is already open is worked out again.
                if (Minecraft.getInstance().gui.screen() instanceof LabPanelScreen screen) screen.refresh();
            } else if (result.outcome() != PriceService.Outcome.TOO_SOON && session.prices == null) {
                say("Prices could not be fetched for that session: " + result.message());
            }
        });
    }

    /**
     * Takes a sack message that came with no session running. A sack message reports on the
     * stretch of time just gone, so the first one after a session ends is about the end of that
     * session, and is counted towards it. It is given how long a stretch the message covers:
     * one that covers only time since the session ended is not the session's.
     */
    static void lateSackMessage(List<ItemChange> changes, long coversMillis) {
        Session session = lastEnded;
        if (session == null || System.currentTimeMillis() - coversMillis >= session.endMillis) return;
        lastEnded = null; // only the first one
        if (!SessionRecorder.addLateSackMessage(session, changes)) return;
        // It may have brought items the session had not seen before, which need their prices too.
        PriceSnapshot current = prices.current();
        if (current != null) session.prices = current.subset(itemIds(session, current));
        store.save(session);
        if (Minecraft.getInstance().gui.screen() instanceof LabPanelScreen screen) screen.refresh();
    }

    public static String cancel() {
        if (recorder.cancel() == null) return "There is no session running";
        store.clearActive();
        return "Session cancelled. Nothing was saved";
    }

    public static String split(String label) {
        if (!recorder.split(label)) return "There is no session running";
        store.saveActive(recorder.active());
        return "Started segment " + recorder.active().segments.size() + (label.isBlank() ? "" : " \"" + label.trim() + "\"");
    }

    /** Saves the running session to disk, so that little is lost if the game closes without warning. */
    public static void autosave() {
        if (recorder.isActive()) store.saveActive(recorder.active());
    }

    /** Stamps a session that has just ended with the prices and assumptions it will be reported with. */
    private static void finish(Session session) {
        PriceSnapshot current = prices.current();
        if (current != null) session.prices = current.subset(itemIds(session, current));
        session.assumptions = LabSettings.assumptions.copy();
    }

    /**
     * A session that was running when the game last closed is ended at the moment it was last
     * saved, which is the last time anything is known about it, and goes into the history.
     */
    private static void recoverInterrupted() {
        Session session = store.loadActive();
        if (session == null) return;
        long end = session.startMillis;
        try {
            end = Math.max(end, Files.getLastModifiedTime(store.dir().resolve("active.json")).toMillis());
        } catch (IOException ignored) {
        }
        if (!session.pauses.isEmpty() && session.pauses.getLast().endMillis == 0) {
            session.pauses.getLast().endMillis = Math.max(end, session.pauses.getLast().startMillis);
        }
        session.endMillis = end;
        session.recovered = true;
        session.warnings.add("The game closed while this session was running. It ends at its last save, "
                + "and up to half a minute before that may be missing");
        finish(session);
        store.save(session);
        store.clearActive();
    }

    private static Set<String> itemIds(Session session, PriceSnapshot catalog) {
        Set<String> ids = new LinkedHashSet<>();
        for (ItemTotals.Total total : ItemTotals.of(session, -1, catalog)) {
            if (total.id != null) ids.add(total.id);
        }
        return ids;
    }

    // --- Reports ---

    /**
     * The session's report as the player has chosen to see it: valued by their current
     * assumptions, with either its own prices or the latest ones.
     */
    public static SessionReport report(Session session) {
        PriceSnapshot latest = prices.current();
        PriceSnapshot basis = session.prices;
        if (basis == null || (LabSettings.latestPrices && latest != null)) basis = latest;
        return new SessionReport(session, basis, LabSettings.assumptions, System.currentTimeMillis());
    }

    /** The report exactly as it stood when the session ended: its own prices and its own assumptions. */
    public static SessionReport originalReport(Session session) {
        return new SessionReport(session, session.prices, session.assumptions, System.currentTimeMillis());
    }

    public static List<SessionReport> reports(List<Session> sessions) {
        List<SessionReport> reports = new ArrayList<>();
        for (Session session : sessions) reports.add(report(session));
        return reports;
    }

    /** Keeps the latest prices with the session, beside the ones it was recorded with. */
    public static String reprice(Session session) {
        PriceSnapshot current = prices.current();
        if (current == null) return "There are no prices yet. Refresh them in Settings, under Efficiency Lab";
        session.repriced = current.subset(itemIds(session, current));
        store.save(session);
        return "Recalculated with prices from " + Fmt.date(current.retrievedMillis);
    }

    // --- Changes made by hand, each noted in the session's list of edits ---

    public static void edit(Session session, String field, String before, String after) {
        if (before.equals(after)) return;
        Session.Edit edit = new Session.Edit();
        edit.millis = System.currentTimeMillis();
        edit.field = field;
        edit.before = before;
        edit.after = after;
        session.edits.add(edit);
    }

    /** Adds or takes away an amount of an item by hand. It is kept as a manual observation. */
    public static void adjustItem(Session session, String item, long amount) {
        if (amount == 0 || item.isBlank()) return;
        Session.Bucket bucket = new Session.Bucket();
        bucket.minute = SessionTimes.end(session, System.currentTimeMillis()) / 60_000L;
        bucket.segment = session.segments.size() - 1;
        bucket.source = Session.Source.MANUAL.name();
        bucket.kind = (amount > 0 ? Session.Kind.GAIN : Session.Kind.LOSS).name();
        bucket.item = item.trim();
        bucket.amount = Math.abs(amount);
        session.buckets.add(bucket);
        edit(session, "item " + item.trim(), "", (amount > 0 ? "+" : "") + amount);
    }

    // --- Setups and experiments ---

    public static Setup newSetup() {
        Setup setup = new Setup();
        setup.id = UUID.randomUUID().toString();
        store.setups().add(setup);
        return setup;
    }

    public static Experiment newExperiment() {
        Experiment experiment = new Experiment();
        experiment.id = UUID.randomUUID().toString();
        experiment.name = "New experiment";
        experiment.metric = LabSettings.primaryMetric;
        experiment.createdMillis = System.currentTimeMillis();
        for (String name : List.of("A", "B")) {
            Experiment.Variant variant = new Experiment.Variant();
            variant.name = name;
            experiment.variants.add(variant);
        }
        store.experiments().add(experiment);
        store.saveExperiments();
        return experiment;
    }

    public static String setupName(String id) {
        Setup setup = store.setup(id);
        return setup != null && !setup.name.isBlank() ? setup.name : "";
    }

    // --- Prices ---

    public static boolean isRefreshingPrices() {
        return refreshing;
    }

    /** A line on where the prices are from and how old they are, for the settings. */
    public static String priceSummary() {
        PriceSnapshot current = prices.current();
        if (refreshing) return "fetching...";
        if (current == null) return "none fetched yet";
        return current.items.size() + " items, " + Fmt.ago(current.retrievedMillis, System.currentTimeMillis());
    }

    public static String priceStatus() {
        return priceStatus;
    }

    /**
     * Fetches the price lists on another thread and calls back on the game's thread with what
     * to tell the player.
     */
    public static void refreshPrices(Consumer<String> done) {
        fetchPrices(result -> done.accept(result.message()));
    }

    private static void fetchPrices(Consumer<PriceService.Result> done) {
        if (refreshing) return;
        refreshing = true;
        Thread thread = new Thread(() -> {
            PriceService.Result result = prices.refresh();
            if (result.outcome() == PriceService.Outcome.UPDATED) store.savePrices(prices.current());
            Minecraft.getInstance().execute(() -> {
                refreshing = false;
                priceStatus = result.message();
                done.accept(result);
            });
        }, "Sentils price refresh");
        thread.setDaemon(true);
        thread.start();
    }

    // --- Export and import: files on this computer only ---

    /** Writes two spreadsheets, one of sessions and one of their items. Returns what to tell the player. */
    public static String exportCsv() {
        List<SessionReport> reports = reports(store.sessions());
        if (reports.isEmpty()) return "There are no sessions to export";
        String stamp = Fmt.fileDate(System.currentTimeMillis());
        Path sessions = store.exportsDir().resolve("sessions_" + stamp + ".csv");
        Path items = store.exportsDir().resolve("items_" + stamp + ".csv");
        boolean written = LabStore.write(sessions, Exports.sessionsCsv(reports, store::setup))
                && LabStore.write(items, Exports.itemsCsv(reports));
        return written ? "Exported " + reports.size() + " sessions to " + store.exportsDir()
                : "The export could not be written. See the game log";
    }

    public static String exportJson() {
        List<Session> sessions = store.sessions();
        if (sessions.isEmpty()) return "There are no sessions to export";
        Path file = store.exportsDir().resolve("lab_" + Fmt.fileDate(System.currentTimeMillis()) + ".json");
        String json = Exports.bundleJson(sessions, store.setups(), store.experiments(), System.currentTimeMillis());
        return LabStore.write(file, json) ? "Exported " + sessions.size() + " sessions to " + file
                : "The export could not be written. See the game log";
    }

    /**
     * Brings in every export file found in the import folder. Records already here, going by
     * their ids, are left as they are. Files that were read are moved into "done".
     */
    public static String importAll() {
        List<Path> files;
        try (Stream<Path> listed = Files.list(store.importsDir())) {
            files = listed.filter(path -> path.getFileName().toString().endsWith(".json")).toList();
        } catch (IOException e) {
            return "The import folder could not be read";
        }
        if (files.isEmpty()) return "Nothing to import. Put exported .json files in " + store.importsDir();

        int added = 0;
        int known = 0;
        int problems = 0;
        for (Path file : files) {
            Exports.Imported imported;
            try {
                imported = Exports.readBundle(Files.readString(file, StandardCharsets.UTF_8));
            } catch (IOException e) {
                problems++;
                continue;
            }
            problems += imported.problems.size();
            for (String problem : imported.problems) {
                Log.warn("Import of " + file.getFileName() + ": " + problem);
            }
            for (Session session : imported.sessions) {
                if (store.session(session.id) != null) {
                    known++;
                } else if (store.save(session)) {
                    added++;
                }
            }
            for (Setup setup : imported.setups) {
                if (store.setup(setup.id) == null) store.setups().add(setup);
            }
            for (Experiment experiment : imported.experiments) {
                if (store.experiments().stream().noneMatch(held -> held.id.equals(experiment.id))) {
                    store.experiments().add(experiment);
                }
            }
            try {
                Path done = store.importsDir().resolve("done");
                Files.createDirectories(done);
                Files.move(file, done.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Log.warn("Could not move " + file.getFileName() + " to done: " + e.getMessage());
            }
        }
        store.saveSetups();
        store.saveExperiments();
        return "Imported " + added + " sessions" + (known > 0 ? ", " + known + " already here" : "")
                + (problems > 0 ? ", " + problems + " problems (see the game log)" : "");
    }
}
