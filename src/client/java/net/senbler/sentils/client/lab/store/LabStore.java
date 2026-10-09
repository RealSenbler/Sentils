package net.senbler.sentils.client.lab.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import net.senbler.sentils.client.Log;
import net.senbler.sentils.client.lab.model.Experiment;
import net.senbler.sentils.client.lab.model.Session;
import net.senbler.sentils.client.lab.model.Setup;
import net.senbler.sentils.client.lab.price.PriceSnapshot;

/**
 * Keeps the Efficiency Lab's records in a folder on this computer, and nowhere else. Each
 * session is a JSON file of its own, so that one damaged file costs one session at most.
 * <p>
 * A file is written beside the one it replaces and then swapped in, so a crash in the middle of
 * saving leaves the old file whole. A file that cannot be read is moved aside into "corrupt"
 * rather than deleted, and deleting a session moves it into "trash".
 */
public final class LabStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

    private final Path dir;
    private final Path sessionsDir;
    private final Map<String, Session> sessions = new LinkedHashMap<>();
    private List<Setup> setups = new ArrayList<>();
    private List<Experiment> experiments = new ArrayList<>();

    private int corrupt; // session files that could not be read on the last load
    private int newer; // session files from a newer version of the mod, left alone

    public LabStore(Path dir) {
        this.dir = dir;
        this.sessionsDir = dir.resolve("sessions");
    }

    public Path dir() {
        return this.dir;
    }

    public Path exportsDir() {
        return this.dir.resolve("exports");
    }

    public Path importsDir() {
        return this.dir.resolve("import");
    }

    /** Reads everything from disk. Safe to call again to pick up files changed by hand. */
    public void load() {
        this.sessions.clear();
        this.corrupt = 0;
        this.newer = 0;
        try {
            Files.createDirectories(this.sessionsDir);
            Files.createDirectories(importsDir());
        } catch (IOException e) {
            Log.warn("Could not create the Efficiency Lab folder: " + e.getMessage());
        }

        try (Stream<Path> files = Files.list(this.sessionsDir)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                loadSessionFile(file);
            }
        } catch (IOException e) {
            Log.warn("Could not list saved sessions: " + e.getMessage());
        }

        this.setups = readList(this.dir.resolve("setups.json"), new TypeToken<List<Setup>>() {});
        this.experiments = readList(this.dir.resolve("experiments.json"), new TypeToken<List<Experiment>>() {});
        this.setups.removeIf(setup -> setup == null || setup.id == null);
        this.experiments.removeIf(experiment -> experiment == null || experiment.id == null);
        for (Experiment experiment : this.experiments) {
            if (experiment.variants == null) experiment.variants = new ArrayList<>();
            experiment.variants.removeIf(variant -> variant == null);
            for (Experiment.Variant variant : experiment.variants) {
                if (variant.sessionIds == null) variant.sessionIds = new ArrayList<>();
                if (variant.name == null) variant.name = "";
            }
        }
    }

    private void loadSessionFile(Path file) {
        try {
            Session session = parseSession(Files.readString(file, StandardCharsets.UTF_8));
            if (session == null) {
                this.newer++;
            } else {
                this.sessions.put(session.id, session);
            }
        } catch (IOException | RuntimeException e) {
            this.corrupt++;
            Log.warn("Could not read session file " + file.getFileName() + ": " + e.getMessage());
            moveAside(file, "corrupt");
        }
    }

    /**
     * Turns the text of a session file into a session, upgrading it if it is from an older
     * version. Returns null if it is from a newer one. Throws if it is not a session at all.
     */
    public static Session parseSession(String json) {
        JsonElement parsed = JsonParser.parseString(json);
        if (!(parsed instanceof JsonObject object)) throw new IllegalArgumentException("not a JSON object");
        if (!Migrations.upgrade(object)) return null;
        Session session = GSON.fromJson(object, Session.class);
        String problem = validate(session);
        if (problem != null) throw new IllegalArgumentException(problem);
        return session;
    }

    /** Fills in anything left empty and returns what is wrong with the session, or null if nothing is. */
    public static String validate(Session session) {
        if (session == null) return "empty record";
        if (session.id == null || !session.id.matches("[A-Za-z0-9_-]{1,64}")) return "missing or unusable id";
        if (session.startMillis <= 0) return "no start time";
        if (session.endMillis != 0 && session.endMillis < session.startMillis) return "ends before it starts";

        if (session.name == null) session.name = "";
        if (session.notes == null) session.notes = "";
        if (session.activity == null) session.activity = "OTHER";
        if (session.pauses == null) session.pauses = new ArrayList<>();
        if (session.segments == null) session.segments = new ArrayList<>();
        if (session.tags == null) session.tags = new ArrayList<>();
        if (session.buckets == null) session.buckets = new ArrayList<>();
        if (session.xp == null) session.xp = new LinkedHashMap<>();
        if (session.counters == null) session.counters = new LinkedHashMap<>();
        session.counters.values().removeIf(count -> count == null || !Double.isFinite(count));
        if (session.costs == null) session.costs = new ArrayList<>();
        if (session.edits == null) session.edits = new ArrayList<>();
        if (session.warnings == null) session.warnings = new ArrayList<>();

        session.pauses.removeIf(pause -> pause == null);
        session.segments.removeIf(segment -> segment == null);
        session.tags.removeIf(tag -> tag == null || tag.isBlank());
        session.costs.removeIf(cost -> cost == null || !Double.isFinite(cost.amount));
        session.edits.removeIf(edit -> edit == null);
        session.xp.values().removeIf(gain -> gain == null || !Double.isFinite(gain.amount));
        // An observation without an item, a kind or a sensible amount cannot be counted or shown.
        session.buckets.removeIf(bucket -> bucket == null || bucket.item == null || bucket.item.isBlank()
                || bucket.kind == null || bucket.source == null || bucket.amount <= 0);
        if (session.purseChange != null && !Double.isFinite(session.purseChange)) session.purseChange = null;
        if (session.segments.isEmpty()) {
            Session.Segment whole = new Session.Segment();
            whole.startMillis = session.startMillis;
            session.segments.add(whole);
        }
        for (Session.Bucket bucket : session.buckets) {
            bucket.segment = Math.clamp(bucket.segment, 0, session.segments.size() - 1);
        }
        return null;
    }

    public static String toJson(Session session) {
        return GSON.toJson(session);
    }

    public static Gson gson() {
        return GSON;
    }

    /** Every saved session, newest first. */
    public List<Session> sessions() {
        List<Session> list = new ArrayList<>(this.sessions.values());
        list.sort(Comparator.comparingLong((Session session) -> session.startMillis).reversed());
        return list;
    }

    public Session session(String id) {
        return this.sessions.get(id);
    }

    public int corruptCount() {
        return this.corrupt;
    }

    public int newerCount() {
        return this.newer;
    }

    /** Saves a session, new or changed. Returns whether it reached the disk. */
    public boolean save(Session session) {
        this.sessions.put(session.id, session);
        return write(this.sessionsDir.resolve(session.id + ".json"), toJson(session));
    }

    /** Takes a session out of the history. Its file is kept in the trash folder. */
    public void delete(String id) {
        if (this.sessions.remove(id) == null) return;
        moveAside(this.sessionsDir.resolve(id + ".json"), "trash");
        for (Experiment experiment : this.experiments) {
            for (Experiment.Variant variant : experiment.variants) variant.sessionIds.remove(id);
        }
        saveExperiments();
    }

    // --- The session being recorded, saved now and then in case the game closes without warning ---

    public void saveActive(Session session) {
        write(this.dir.resolve("active.json"), toJson(session));
    }

    public void clearActive() {
        try {
            Files.deleteIfExists(this.dir.resolve("active.json"));
        } catch (IOException e) {
            Log.warn("Could not remove the active session file: " + e.getMessage());
        }
    }

    /** The session that was being recorded when the game last closed, or null if it closed cleanly. */
    public Session loadActive() {
        Path file = this.dir.resolve("active.json");
        if (!Files.exists(file)) return null;
        try {
            return parseSession(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            Log.warn("Could not read the interrupted session: " + e.getMessage());
            moveAside(file, "corrupt");
            return null;
        }
    }

    // --- Setups and experiments ---

    public List<Setup> setups() {
        return this.setups;
    }

    public Setup setup(String id) {
        if (id == null) return null;
        for (Setup setup : this.setups) {
            if (id.equals(setup.id)) return setup;
        }
        return null;
    }

    public void saveSetups() {
        write(this.dir.resolve("setups.json"), GSON.toJson(this.setups));
    }

    public List<Experiment> experiments() {
        return this.experiments;
    }

    public void saveExperiments() {
        write(this.dir.resolve("experiments.json"), GSON.toJson(this.experiments));
    }

    // --- Prices ---

    public PriceSnapshot loadPrices() {
        Path file = this.dir.resolve("prices.json");
        if (!Files.exists(file)) return null;
        try {
            PriceSnapshot prices = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), PriceSnapshot.class);
            if (prices == null || prices.items == null) return null;
            prices.items.values().removeIf(entry -> entry == null || entry.id == null);
            return prices;
        } catch (IOException | RuntimeException e) {
            Log.warn("Could not read saved prices: " + e.getMessage());
            return null;
        }
    }

    public void savePrices(PriceSnapshot prices) {
        write(this.dir.resolve("prices.json"), new Gson().toJson(prices));
    }

    // --- Files ---

    private <T> List<T> readList(Path file, TypeToken<List<T>> type) {
        if (!Files.exists(file)) return new ArrayList<>();
        try {
            List<T> list = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), type.getType());
            return list != null ? new ArrayList<>(list) : new ArrayList<>();
        } catch (IOException | RuntimeException e) {
            Log.warn("Could not read " + file.getFileName() + ": " + e.getMessage());
            moveAside(file, "corrupt");
            return new ArrayList<>();
        }
    }

    /** Writes the text beside the file and then swaps it in, so the file is never left half written. */
    public static boolean write(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            Log.warn("Could not save " + file.getFileName() + ": " + e.getMessage());
            return false;
        }
    }

    private void moveAside(Path file, String folder) {
        try {
            Path target = this.dir.resolve(folder);
            Files.createDirectories(target);
            Files.move(file, target.resolve(System.currentTimeMillis() + "-" + file.getFileName()),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Log.warn("Could not move " + file.getFileName() + " aside: " + e.getMessage());
        }
    }
}
