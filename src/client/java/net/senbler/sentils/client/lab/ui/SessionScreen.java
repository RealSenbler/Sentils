package net.senbler.sentils.client.lab.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.senbler.sentils.client.gui.Theme;
import net.senbler.sentils.client.gui.elementa.EButton;
import net.senbler.sentils.client.gui.elementa.EField;
import net.senbler.sentils.client.lab.Fmt;
import net.senbler.sentils.client.lab.Lab;
import net.senbler.sentils.client.lab.LabSettings;
import net.senbler.sentils.client.lab.analysis.ItemTotals;
import net.senbler.sentils.client.lab.analysis.Metric;
import net.senbler.sentils.client.lab.analysis.SessionReport;
import net.senbler.sentils.client.lab.core.SessionTimes;
import net.senbler.sentils.client.lab.model.Activity;
import net.senbler.sentils.client.lab.model.Provenance;
import net.senbler.sentils.client.lab.model.Session;
import net.senbler.sentils.client.lab.price.Assumptions;
import net.senbler.sentils.client.lab.price.PriceSnapshot;

/**
 * One saved session: its headline figures as tiles and the rest in a table under them. The
 * means to relabel and correct it are behind the Edit button, out of the way until wanted.
 * Whatever is changed by hand is written into the session's own list of edits.
 */
public class SessionScreen extends LabPanelScreen {

    private final Session session;
    private final int[] scroll = new int[1];

    // As they were when the screen opened, to tell what was changed.
    private final String nameBefore;
    private final String tagsBefore;
    private final String notesBefore;
    private final String activityBefore;
    private final String setupBefore;

    private String tags;
    private String itemText = "";
    private String numberText = "";
    private String message = ""; // what the last button press did
    private boolean editing;
    private boolean confirmDelete;
    private boolean deleted;

    public SessionScreen(Screen parent, Session session) {
        super(parent, Component.literal(session.displayName()));
        this.session = session;
        this.nameBefore = session.name;
        this.tagsBefore = String.join(", ", session.tags);
        this.notesBefore = session.notes;
        this.activityBefore = session.activity;
        this.setupBefore = Lab.setupName(session.setupId);
        this.tags = this.tagsBefore;
    }

    @Override
    protected String subtitle() {
        // What the last button press did takes the place of the date until the screen is left.
        if (!this.message.isEmpty()) return this.message;
        String setup = Lab.setupName(this.session.setupId);
        return this.session.activityType().label + ", " + Fmt.shortDate(this.session.startMillis)
                + (setup.isEmpty() ? "" : ", " + setup) + (this.session.recovered ? " (interrupted)" : "");
    }

    private EField box(int x, int y, int w, String hint, String tooltip, String value, int maxLength,
                            Consumer<String> onChange) {
        EField box = new EField(this.font, x, y, w, BUTTON_H, Component.literal(hint));
        box.setHint(Component.literal(hint));
        box.setTooltip((Component.literal(tooltip)));
        box.setMaxLength(maxLength);
        box.setValue(value);
        box.setResponder(onChange);
        this.add(box);
        return box;
    }

    /** Adds a button with a tooltip, as wide as its label needs or as given. Returns the x just past it. */
    private int tipButton(int x, int y, int w, String label, String tooltip, Runnable action) {
        int width = w > 0 ? w : this.font.width(label) + 14;
        EButton button = new EButton(x, y, width, BUTTON_H, Component.literal(label), EButton.Style.SECONDARY, action);
        button.setTooltip((Component.literal(tooltip)));
        this.add(button);
        return x + width + GAP;
    }

    @Override
    protected void build() {
        layout();
        int right = this.left + this.panelW - PAD;
        right = addButtonLeftOf(right, this.top + 9, "Back", EButton.Style.PRIMARY, this::onClose);
        right = addButtonLeftOf(right, this.top + 9, this.confirmDelete ? "Sure?" : "Delete",
                EButton.Style.DANGER, this::delete);
        int repriceW = this.font.width("Reprice") + 14;
        tipButton(right - repriceW, this.top + 9, repriceW, "Reprice",
                "Work this session out again with the latest prices. The prices it was recorded with are kept too",
                () -> {
                    this.message = Lab.reprice(this.session);
                    this.rebuildWidgets();
                });
        right -= repriceW + GAP;
        int editW = this.font.width(this.editing ? "Done" : "Edit") + 14;
        tipButton(right - editW, this.top + 9, editW, this.editing ? "Done" : "Edit",
                "Rename this session, change its activity, setup, tags and notes, or correct its items and costs",
                () -> {
                    this.editing = !this.editing;
                    this.rebuildWidgets();
                });

        SessionReport report = Lab.report(this.session);
        int y = this.contentY;
        for (StatTile tile : SessionView.tiles(this.contentX, y, this.contentW, report)) this.add(tile);
        y += StatTile.HEIGHT + 6;
        if (this.editing) y = addEditControls(y);

        LabTable table = new LabTable(this.contentX, y, this.contentW, this.contentBottom - y, this.scroll,
                SessionView.weights());
        table.alignRight(1, 2, 3, 4);
        this.add(table);
        fill(table, report);
    }

    /** The boxes and buttons for relabelling and correcting the session. Returns the y below them. */
    private int addEditControls(int y) {
        int half = (this.contentW - GAP * 2) / 2;
        box(this.contentX, y, half, "Name", "The session's name", this.session.name, 60,
                text -> this.session.name = text.trim());
        int x = this.contentX + half + GAP;
        String setupName = Lab.setupName(this.session.setupId);
        x = tipButton(x, y, half / 2, this.session.activityType().label,
                "What the session was of, which sets the skill whose XP is its own. Click for the next", () -> {
                    Activity[] all = Activity.values();
                    this.session.activity = all[(this.session.activityType().ordinal() + 1) % all.length].name();
                    this.rebuildWidgets();
                });
        tipButton(x, y, this.contentX + this.contentW - x, setupName.isEmpty() ? "No setup" : setupName,
                "The setup it was recorded with. Click for the next. Setups are made on the Experiments tab", () -> {
                    this.session.setupId = LabScreen.nextSetup(this.session.setupId);
                    this.rebuildWidgets();
                });

        y += BUTTON_H + GAP;
        int tagsW = this.contentW / 3;
        box(this.contentX, y, tagsW, "Tags", "Words to find and group it by, separated by commas", this.tags, 80,
                text -> this.tags = text);
        box(this.contentX + tagsW + GAP, y, this.contentW - tagsW - GAP, "Notes", "Anything worth remembering about it",
                this.session.notes, 200, text -> this.session.notes = text);

        // Corrections: one item box and one number box, used by three buttons
        y += BUTTON_H + GAP;
        String[] labels = {"+ Items", "Price", "Cost"};
        int buttonsW = 0;
        for (String label : labels) buttonsW += this.font.width(label) + 14 + GAP;
        int numberW = 54;
        int itemW = this.contentW - buttonsW - numberW - GAP;
        box(this.contentX, y, itemW, "Item", "An item's name as the game shows it, or what a cost was for",
                this.itemText, 60, text -> this.itemText = text);
        box(this.contentX + itemW + GAP, y, numberW, "Number",
                "An amount of items (negative to take away), a price for one, or a cost in coins",
                this.numberText, 14, text -> this.numberText = text);
        x = this.contentX + itemW + GAP + numberW + GAP;
        x = tipButton(x, y, 0, labels[0], "Add this many of the item to the record, or take them away with a negative number",
                this::addItems);
        x = tipButton(x, y, 0, labels[1], "Value this item at your own price each, in every session. "
                + "With the number empty, it goes back to market prices", this::setPrice);
        tipButton(x, y, 0, labels[2], "Take a cost in coins off this session's net, such as fuel or bait bought for it",
                this::addCost);
        return y + BUTTON_H + 6;
    }

    private void fill(LabTable table, SessionReport report) {
        SessionView.fillItems(table, report, this::toggleExcluded);
        SessionView.fillTotals(table, report);

        // The same session under each set of prices it has.
        Assumptions now = LabSettings.assumptions;
        table.section("PRICES");
        table.head("", "", "Net", "Per hour", "From");
        valuedWith(table, "As recorded", this.session.prices, this.session.assumptions);
        if (this.session.repriced != null) valuedWith(table, "Repriced", this.session.repriced, now);
        if (Lab.prices() != null) valuedWith(table, "Latest", Lab.prices(), now);
        if (report.prices == null) {
            table.note("No prices yet: refresh them in Settings", Theme.WARN);
        } else if (report.pricesAreStale(System.currentTimeMillis())) {
            table.note("These prices are from a different day than the session", Theme.WARN);
        }
        // The tax is the Bazaar's, so it is only mentioned when Bazaar prices are in use.
        table.note(now.modeType().label + (now.modeType().usesBazaar() ? ", " + now.bazaarTaxPercent + "% tax" : ""),
                Theme.TEXT_MUTED);

        if (this.session.segments.size() > 1) {
            table.section("SEGMENTS");
            table.head("", "", "", "Time", "Items");
            for (int i = 0; i < this.session.segments.size(); i++) {
                long gained = 0;
                for (ItemTotals.Total total : ItemTotals.of(this.session, i, report.prices)) gained += total.netGain();
                String label = this.session.segments.get(i).label;
                table.add((i + 1) + (label.isBlank() ? "" : "  " + label), "", "",
                        Fmt.duration(SessionTimes.segmentTracked(this.session, i, System.currentTimeMillis())),
                        Fmt.number((double) gained));
            }
        }

        List<String> notes = new ArrayList<>();
        if (this.session.location != null) notes.add(this.session.location);
        if (!this.session.tags.isEmpty()) notes.add("Tags: " + String.join(", ", this.session.tags));
        if (!this.session.notes.isBlank()) notes.add(this.session.notes);
        for (Session.Pause pause : this.session.pauses) {
            if (pause.endMillis != 0) {
                notes.add("Paused " + Fmt.duration(pause.endMillis - pause.startMillis)
                        + (pause.reason.isBlank() ? "" : ", " + pause.reason));
            }
        }
        for (Session.Cost cost : this.session.costs) {
            notes.add("Cost " + Fmt.number(cost.amount) + (cost.label.isBlank() ? "" : ", " + cost.label));
        }
        if (!notes.isEmpty() || !this.session.warnings.isEmpty() || !this.session.edits.isEmpty()) {
            table.section("NOTES");
            for (String note : notes) table.note(note, Theme.TEXT_MUTED);
            for (String warning : this.session.warnings) table.note(warning, Theme.WARN);
            // Everything changed by hand since it was recorded
            for (Session.Edit edit : this.session.edits) {
                table.note("Edited " + Fmt.shortDate(edit.millis) + ": " + edit.field + " "
                        + (edit.before.isEmpty() ? "" : "\"" + edit.before + "\" to ") + "\"" + edit.after + "\"",
                        Icons.color(Provenance.MANUAL));
            }
        }

        table.section("KNOWN");
        for (Provenance provenance : Provenance.values()) {
            table.note(provenance.label + ": " + provenance.meaning.toLowerCase(), Icons.color(provenance));
        }
    }

    private void valuedWith(LabTable table, String label, PriceSnapshot prices, Assumptions assumptions) {
        SessionReport report = new SessionReport(this.session, prices, assumptions, System.currentTimeMillis());
        table.add(label, "", Fmt.measure(report.net()), Fmt.measure(report.rate(Metric.NET_PER_HOUR)),
                        prices == null ? "-" : Fmt.shortDate(prices.retrievedMillis))
                .color(4, Theme.TEXT_MUTED);
    }

    private void toggleExcluded(String item) {
        String key = Assumptions.key(item);
        if (!LabSettings.assumptions.excluded.remove(key)) LabSettings.assumptions.excluded.add(key);
        LabSettings.save();
        this.message = LabSettings.assumptions.excluded.contains(key)
                ? item + " is left out of the value" : item + " is counted again";
        this.rebuildWidgets();
    }

    /** The number box as a number, or null if it does not hold one. */
    private Double number() {
        try {
            double value = Double.parseDouble(this.numberText.trim().replace(",", ""));
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void addItems() {
        Double amount = number();
        if (this.itemText.isBlank() || amount == null || amount.longValue() == 0) {
            this.message = "Type an item and a whole number first";
        } else {
            Lab.adjustItem(this.session, this.itemText, amount.longValue());
            Lab.store().save(this.session);
            this.message = (amount > 0 ? "+" : "") + amount.longValue() + " " + this.itemText.trim() + " recorded";
            this.numberText = "";
        }
        this.rebuildWidgets();
    }

    private void setPrice() {
        String key = Assumptions.key(this.itemText);
        Double price = number();
        if (key.isEmpty() || (price != null && price < 0)) {
            this.message = "Type an item and a price first";
        } else if (price == null) {
            this.message = LabSettings.assumptions.manualPrices.remove(key) != null
                    ? this.itemText.trim() + " is back on market prices" : "Type a price for " + this.itemText.trim();
        } else {
            LabSettings.assumptions.manualPrices.put(key, price);
            this.message = this.itemText.trim() + " valued at " + Fmt.number(price.doubleValue()) + " each";
        }
        LabSettings.save();
        this.rebuildWidgets();
    }

    private void addCost() {
        Double amount = number();
        if (amount == null || amount <= 0) {
            this.message = "Type the cost in the number box first";
        } else {
            Session.Cost cost = new Session.Cost();
            cost.label = this.itemText.trim();
            cost.amount = amount;
            this.session.costs.add(cost);
            Lab.edit(this.session, "cost", "", Fmt.number(amount.doubleValue()) + (cost.label.isEmpty() ? "" : " for " + cost.label));
            Lab.store().save(this.session);
            this.message = Fmt.number(amount.doubleValue()) + " taken off the net";
            this.numberText = "";
        }
        this.rebuildWidgets();
    }

    private void delete() {
        if (!this.confirmDelete) {
            this.confirmDelete = true;
            this.rebuildWidgets();
            return;
        }
        Lab.store().delete(this.session.id);
        this.deleted = true;
        onClose();
    }

    @Override
    public void onClose() {
        if (!this.deleted) {
            List<String> tagList = new ArrayList<>();
            for (String tag : this.tags.split(",")) {
                if (!tag.isBlank() && !tagList.contains(tag.trim())) tagList.add(tag.trim());
            }
            this.session.tags = tagList;
            Lab.edit(this.session, "name", this.nameBefore, this.session.name);
            Lab.edit(this.session, "tags", this.tagsBefore, String.join(", ", tagList));
            Lab.edit(this.session, "notes", this.notesBefore, this.session.notes);
            Lab.edit(this.session, "activity", this.activityBefore, this.session.activity);
            Lab.edit(this.session, "setup", this.setupBefore, Lab.setupName(this.session.setupId));
            Lab.store().save(this.session);
        }
        super.onClose();
    }
}
