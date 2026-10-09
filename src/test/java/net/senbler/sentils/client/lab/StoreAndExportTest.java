package net.senbler.sentils.client.lab;

import static net.senbler.sentils.client.lab.Fixtures.HOUR;
import static net.senbler.sentils.client.lab.Fixtures.wheatSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.senbler.sentils.client.lab.analysis.SessionReport;
import net.senbler.sentils.client.lab.export.Csv;
import net.senbler.sentils.client.lab.export.Exports;
import net.senbler.sentils.client.lab.model.Experiment;
import net.senbler.sentils.client.lab.model.Session;
import net.senbler.sentils.client.lab.model.Setup;
import net.senbler.sentils.client.lab.price.Assumptions;
import net.senbler.sentils.client.lab.price.PriceSnapshot;
import net.senbler.sentils.client.lab.store.LabStore;
import net.senbler.sentils.client.lab.store.Migrations;

class StoreAndExportTest {

    @TempDir
    Path dir;

    private LabStore open() {
        LabStore store = new LabStore(this.dir);
        store.load();
        return store;
    }

    private static long count(Path folder) throws IOException {
        if (!Files.exists(folder)) return 0;
        try (Stream<Path> files = Files.list(folder)) {
            return files.count();
        }
    }

    // --- Persistence ---

    @Test
    void sessionsSurviveARestart() {
        Session session = wheatSession(HOUR, 1234);
        session.tags.add("test");
        session.notes = "a note, with \"quotes\"";
        session.purseChange = 42.5;
        session.prices = Fixtures.prices().subset(List.of("WHEAT"));
        session.assumptions = new Assumptions();
        Session.XpGain xp = new Session.XpGain();
        xp.amount = 99.5;
        session.xp.put("Farming", xp);
        assertTrue(open().save(session));

        // A new store on the same folder is what the next launch of the game has.
        LabStore reopened = open();
        Session loaded = reopened.session(session.id);
        assertNotNull(loaded);
        assertEquals(1, reopened.sessions().size());
        assertEquals(session.startMillis, loaded.startMillis);
        assertEquals(session.endMillis, loaded.endMillis);
        assertEquals(List.of("test"), loaded.tags);
        assertEquals(session.notes, loaded.notes);
        assertEquals(42.5, loaded.purseChange);
        assertEquals(99.5, loaded.xp.get("Farming").amount);
        assertEquals(1234, loaded.buckets.getFirst().amount);
        assertEquals(4.0, loaded.prices.get("WHEAT").instantSell);
        assertEquals(LabStore.toJson(session), LabStore.toJson(loaded), "nothing is lost or changed on the way");
    }

    @Test
    void sessionsAreListedNewestFirst() {
        LabStore store = open();
        Session older = wheatSession(HOUR, 1);
        Session newer = wheatSession(HOUR, 2);
        newer.startMillis += 10 * HOUR;
        newer.endMillis += 10 * HOUR;
        store.save(older);
        store.save(newer);

        assertEquals(List.of(newer.id, older.id), open().sessions().stream().map(session -> session.id).toList());
    }

    @Test
    void aDamagedFileIsMovedAsideAndTheRestStillLoad() throws IOException {
        LabStore store = open();
        Session good = wheatSession(HOUR, 5);
        store.save(good);
        Files.writeString(this.dir.resolve("sessions/broken.json"), "{ \"id\": \"broken\", \"startMillis\": ");
        Files.writeString(this.dir.resolve("sessions/nostart.json"), "{ \"id\": \"nostart\" }");
        Files.writeString(this.dir.resolve("sessions/list.json"), "[1, 2, 3]");

        LabStore reopened = open();
        assertEquals(1, reopened.sessions().size());
        assertEquals(good.id, reopened.sessions().getFirst().id);
        assertEquals(3, reopened.corruptCount());
        assertEquals(3, count(this.dir.resolve("corrupt")), "kept for a look, not deleted");
        assertEquals(1, count(this.dir.resolve("sessions")));
    }

    @Test
    void aFileFromANewerVersionIsLeftWhereItIs() throws IOException {
        Session session = wheatSession(HOUR, 5);
        JsonObject json = JsonParser.parseString(LabStore.toJson(session)).getAsJsonObject();
        json.addProperty("formatVersion", Session.CURRENT_VERSION + 1);
        Files.createDirectories(this.dir.resolve("sessions"));
        Files.writeString(this.dir.resolve("sessions/" + session.id + ".json"), json.toString());

        LabStore store = open();
        assertTrue(store.sessions().isEmpty());
        assertEquals(1, store.newerCount());
        assertEquals(0, store.corruptCount());
        assertEquals(1, count(this.dir.resolve("sessions")));
    }

    @Test
    void missingPartsOfARecordAreFilledInRatherThanBreakingIt() {
        Session session = LabStore.parseSession(
                "{\"id\":\"abc\",\"startMillis\":1000,\"endMillis\":5000,\"tags\":null,"
                        + "\"buckets\":[{\"item\":\"Wheat\",\"kind\":\"GAIN\",\"source\":\"SACK\",\"amount\":3},"
                        + "{\"item\":\"\",\"kind\":\"GAIN\",\"source\":\"SACK\",\"amount\":3},"
                        + "{\"item\":\"Wheat\",\"kind\":\"GAIN\",\"source\":\"SACK\",\"amount\":-1}, null]}");

        assertNotNull(session.tags);
        assertEquals(1, session.buckets.size(), "observations that cannot be counted are dropped");
        assertEquals(1, session.segments.size());
        assertNull(session.purseChange, "what was never measured stays unknown");
        assertThrows(RuntimeException.class, () -> LabStore.parseSession("{\"id\":\"x\",\"startMillis\":9,\"endMillis\":1}"));
        assertThrows(RuntimeException.class, () -> LabStore.parseSession("{\"id\":\"../evil\",\"startMillis\":9}"));
    }

    @Test
    void olderRecordsAreUpgradedStepByStep() {
        // A made-up history: version 2 renamed "title" to "name", version 3 added a default note.
        List<Migrations.Step> steps = List.of(
                record -> record.add("name", record.remove("title")),
                record -> record.addProperty("notes", "migrated"));

        JsonObject old = JsonParser.parseString("{\"title\":\"first\"}").getAsJsonObject(); // no version: version 1
        assertTrue(Migrations.upgrade(old, steps, 3));
        assertEquals("first", old.get("name").getAsString());
        assertEquals("migrated", old.get("notes").getAsString());
        assertEquals(3, old.get("formatVersion").getAsInt());

        JsonObject middle = JsonParser.parseString("{\"formatVersion\":2,\"name\":\"second\"}").getAsJsonObject();
        assertTrue(Migrations.upgrade(middle, steps, 3));
        assertEquals("second", middle.get("name").getAsString());
        assertEquals("migrated", middle.get("notes").getAsString());

        JsonObject future = JsonParser.parseString("{\"formatVersion\":4,\"name\":\"third\"}").getAsJsonObject();
        assertFalse(Migrations.upgrade(future, steps, 3));
        assertEquals(4, future.get("formatVersion").getAsInt(), "left exactly as it was");
    }

    @Test
    void anInterruptedSessionCanBePickedUpAgain() {
        LabStore store = open();
        assertNull(store.loadActive());

        Session running = wheatSession(HOUR, 77);
        running.endMillis = 0;
        store.saveActive(running);

        Session recovered = open().loadActive();
        assertNotNull(recovered);
        assertTrue(recovered.isRunning());
        assertEquals(77, recovered.buckets.getFirst().amount);
        assertTrue(open().sessions().isEmpty(), "it is not in the history until it is ended");

        store.clearActive();
        assertNull(open().loadActive());
    }

    @Test
    void aDeletedSessionGoesToTheTrashAndOutOfExperiments() throws IOException {
        LabStore store = open();
        Session session = wheatSession(HOUR, 5);
        store.save(session);
        Experiment experiment = new Experiment();
        experiment.id = "e1";
        Experiment.Variant variant = new Experiment.Variant();
        variant.sessionIds.add(session.id);
        experiment.variants.add(variant);
        store.experiments().add(experiment);
        store.saveExperiments();

        store.delete(session.id);

        LabStore reopened = open();
        assertTrue(reopened.sessions().isEmpty());
        assertEquals(1, count(this.dir.resolve("trash")));
        assertTrue(reopened.experiments().getFirst().variants.getFirst().sessionIds.isEmpty());
    }

    @Test
    void setupsExperimentsAndPricesSurviveARestart() {
        LabStore store = open();
        Setup setup = new Setup();
        setup.id = "s1";
        setup.name = "Hoe A";
        store.setups().add(setup);
        store.saveSetups();
        store.savePrices(Fixtures.prices());

        LabStore reopened = open();
        assertEquals("Hoe A", reopened.setup("s1").name);
        assertNull(reopened.setup("nope"));
        PriceSnapshot prices = reopened.loadPrices();
        assertEquals(Fixtures.START, prices.retrievedMillis);
        assertEquals("WHEAT", prices.idForName("wheat"));
    }

    // --- CSV ---

    @Test
    void csvCellsAreQuotedOnlyWhenTheyNeedToBe() {
        assertEquals("plain", Csv.text("plain"));
        assertEquals("\"a,b\"", Csv.text("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", Csv.text("say \"hi\""));
        assertEquals("\"two\nlines\"", Csv.text("two\nlines"));
        assertEquals("", Csv.text(null));
        assertEquals("'=SUM(A1)", Csv.text("=SUM(A1)"), "not run as a formula by a spreadsheet");
        assertEquals("", Csv.number(null), "an unavailable figure is an empty cell, not 0");
        assertEquals("", Csv.number(Double.NaN));
        assertEquals("1500", Csv.number(1500.0));
        assertEquals("-2.5000", Csv.number(-2.5));
    }

    @Test
    void csvReadsBackWhatItWrote() {
        List<String> cells = List.of("plain", "a,b", "say \"hi\"", "two\nlines", "", " padded ", "ünïcödé ❤");
        String csv = Csv.BYTE_ORDER_MARK + Csv.row(cells.stream().map(Csv::text).toList())
                + Csv.row(List.of("1", "2"));

        List<List<String>> rows = Csv.parse(csv);
        assertEquals(2, rows.size());
        assertEquals(cells, rows.get(0));
        assertEquals(List.of("1", "2"), rows.get(1));
    }

    @Test
    void theSessionsCsvHasARowPerSessionAndEmptyCellsForWhatIsUnknown() {
        Session priced = wheatSession(HOUR, 100);
        priced.name = "Wheat, \"fast\" run";
        priced.notes = "line one\nline two";
        Session unpriced = wheatSession(HOUR, 100);
        Assumptions assumptions = new Assumptions();
        assumptions.bazaarTaxPercent = 0;
        List<SessionReport> reports = List.of(
                new SessionReport(priced, Fixtures.prices(), assumptions, 0),
                new SessionReport(unpriced, null, assumptions, 0));

        List<List<String>> rows = Csv.parse(Exports.sessionsCsv(reports, id -> null));
        assertEquals(3, rows.size());
        List<String> header = rows.get(0);
        for (List<String> row : rows) assertEquals(header.size(), row.size(), "every row has every column");

        assertEquals("Wheat, \"fast\" run", rows.get(1).get(header.indexOf("name")));
        assertEquals("line one\nline two", rows.get(1).get(header.indexOf("notes")));
        assertEquals("3600", rows.get(1).get(header.indexOf("tracked_seconds")));
        assertEquals("400", rows.get(1).get(header.indexOf("est_net_value")));
        assertEquals("", rows.get(2).get(header.indexOf("est_net_value")));
        assertEquals("", rows.get(2).get(header.indexOf("purse_change")));
        assertEquals("100", rows.get(2).get(header.indexOf("items_gained")));

        List<List<String>> items = Csv.parse(Exports.itemsCsv(reports));
        assertEquals(3, items.size());
        assertEquals("Wheat", items.get(1).get(items.get(0).indexOf("item")));
        assertEquals("", items.get(2).get(items.get(0).indexOf("unit_price")));
    }

    // --- JSON ---

    @Test
    void aJsonExportReadsBackComplete() {
        Session session = wheatSession(HOUR, 100);
        session.prices = Fixtures.prices().subset(List.of("WHEAT"));
        Setup setup = new Setup();
        setup.id = "s1";
        setup.name = "Hoe A";
        Experiment experiment = new Experiment();
        experiment.id = "e1";
        experiment.name = "Hoes";

        String json = Exports.bundleJson(List.of(session), List.of(setup), List.of(experiment), 123);
        Exports.Imported imported = Exports.readBundle(json);

        assertTrue(imported.problems.isEmpty());
        assertEquals(LabStore.toJson(session), LabStore.toJson(imported.sessions.getFirst()));
        assertEquals("Hoe A", imported.setups.getFirst().name);
        assertEquals("Hoes", imported.experiments.getFirst().name);
        assertFalse(json.toLowerCase().contains("api-key"));
        assertFalse(json.toLowerCase().contains("token"));
    }

    @Test
    void aLoneSessionFileCanBeImported() {
        Session session = wheatSession(HOUR, 100);
        Exports.Imported imported = Exports.readBundle(LabStore.toJson(session));
        assertEquals(1, imported.sessions.size());
        assertTrue(imported.problems.isEmpty());
    }

    @Test
    void badImportsAreRefusedPieceByPiece() {
        assertFalse(Exports.readBundle("not json at all {").problems.isEmpty());
        assertFalse(Exports.readBundle("[1,2]").problems.isEmpty());
        assertFalse(Exports.readBundle("{\"sessions\": 5}").problems.isEmpty());

        Session good = wheatSession(HOUR, 1);
        String mixed = "{\"sessions\":[" + LabStore.toJson(good) + ",{\"id\":\"bad\"},7,"
                + "{\"id\":\"running\",\"startMillis\":5}],\"setups\":[{\"name\":\"no id\"},3]}";
        Exports.Imported imported = Exports.readBundle(mixed);
        assertEquals(1, imported.sessions.size());
        assertEquals(good.id, imported.sessions.getFirst().id);
        assertEquals(4, imported.problems.size(), "three sessions and one setup");
        assertTrue(imported.setups.isEmpty());
    }
}
