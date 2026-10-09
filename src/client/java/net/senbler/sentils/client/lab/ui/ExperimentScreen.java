package net.senbler.sentils.client.lab.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.senbler.sentils.client.gui.Theme;
import net.senbler.sentils.client.gui.elementa.EButton;
import net.senbler.sentils.client.gui.elementa.EField;
import net.senbler.sentils.client.lab.Fmt;
import net.senbler.sentils.client.lab.Lab;
import net.senbler.sentils.client.lab.analysis.Comparison;
import net.senbler.sentils.client.lab.analysis.Metric;
import net.senbler.sentils.client.lab.analysis.SessionReport;
import net.senbler.sentils.client.lab.model.Experiment;
import net.senbler.sentils.client.lab.model.Session;

/**
 * One experiment: its variants, the sessions recorded for each, and what they show. The first
 * variant is the baseline the others are measured against.
 */
public class ExperimentScreen extends LabPanelScreen {

    private static final int MAX_VARIANTS = 6;

    private final Experiment experiment;
    private final int[] scroll = new int[1];
    private EField variantsBox;
    private boolean confirmDelete;
    private boolean deleted;

    public ExperimentScreen(Screen parent, Experiment experiment) {
        super(parent, Component.literal("Experiment"));
        this.experiment = experiment;
    }

    /** Compares the experiment's variants on its metric, using the sessions assigned to each. */
    static Comparison.Result result(Experiment experiment) {
        return Comparison.compare(groups(experiment), Metric.parse(experiment.metric));
    }

    /** The experiment's variants as sides to compare, the first of them the baseline. */
    private static List<Comparison.Group> groups(Experiment experiment) {
        List<Comparison.Group> groups = new ArrayList<>();
        for (Experiment.Variant variant : experiment.variants) {
            List<SessionReport> reports = new ArrayList<>();
            for (String id : variant.sessionIds) {
                Session session = Lab.store().session(id);
                if (session != null) reports.add(Lab.report(session));
            }
            groups.add(new Comparison.Group(variant.name.isBlank() ? "(unnamed)" : variant.name, reports));
        }
        return groups;
    }

    @Override
    protected String subtitle() {
        return "Created " + Fmt.date(this.experiment.createdMillis);
    }

    private String variantNames() {
        List<String> names = new ArrayList<>();
        for (Experiment.Variant variant : this.experiment.variants) names.add(variant.name);
        return String.join(", ", names);
    }

    /** Makes the variants match the names typed: renamed in place, added at the end, dropped off the end. */
    private void setVariants(String text) {
        List<String> names = new ArrayList<>();
        for (String name : text.split(",")) {
            if (!name.isBlank() && names.size() < MAX_VARIANTS) names.add(name.trim());
        }
        List<Experiment.Variant> variants = this.experiment.variants;
        for (int i = 0; i < names.size(); i++) {
            if (i < variants.size()) {
                variants.get(i).name = names.get(i);
            } else {
                Experiment.Variant variant = new Experiment.Variant();
                variant.name = names.get(i);
                variants.add(variant);
            }
        }
        while (variants.size() > Math.max(1, names.size())) variants.removeLast();
    }

    /** Moves the session on to the next variant, and after the last to none. */
    private void cycle(Session session) {
        List<Experiment.Variant> variants = this.experiment.variants;
        Experiment.Variant current = this.experiment.variantOf(session.id);
        int next = current == null ? 0 : variants.indexOf(current) + 1;
        if (current != null) current.sessionIds.remove(session.id);
        if (next < variants.size()) variants.get(next).sessionIds.add(session.id);
        this.rebuildWidgets();
    }

    @Override
    protected void build() {
        layout();
        int right = addButtonLeftOf(this.left + this.panelW - PAD, this.top + 9, "Back", EButton.Style.PRIMARY,
                this::onClose);
        addButtonLeftOf(right, this.top + 9, this.confirmDelete ? "Really delete?" : "Delete", EButton.Style.DANGER, () -> {
            if (!this.confirmDelete) {
                this.confirmDelete = true;
                this.rebuildWidgets();
                return;
            }
            Lab.store().experiments().remove(this.experiment);
            this.deleted = true;
            onClose();
        });

        int y = this.contentY;
        Metric metric = Metric.parse(this.experiment.metric);
        int metricW = this.font.width(metric.shortLabel) + 14;
        int nameW = (this.contentW - metricW - GAP * 2) / 2;

        EField name = new EField(this.font, this.contentX, y, nameW, BUTTON_H, Component.literal("Experiment name"));
        name.setHint(Component.literal("Name"));
        name.setTooltip((Component.literal("The question being asked, such as: does hoe B beat hoe A?")));
        name.setMaxLength(60);
        name.setValue(this.experiment.name);
        name.setResponder(text -> this.experiment.name = text.trim());
        this.add(name);

        // Kept across rebuilds so it holds on to its text and cursor while the table changes.
        if (this.variantsBox == null) {
            this.variantsBox = new EField(this.font, 0, 0, nameW, BUTTON_H, Component.literal("Variants"));
            this.variantsBox.setHint(Component.literal("A, B"));
            this.variantsBox.setMaxLength(100);
            this.variantsBox.setValue(variantNames());
            this.variantsBox.setTooltip((Component.literal(
                    "The things being compared, separated by commas. The first is the baseline")));
            this.variantsBox.setResponder(text -> {
                setVariants(text);
                boolean focused = this.variantsBox.hasKeyboard();
                this.rebuildWidgets();
                if (focused) this.variantsBox.takeKeyboard();
            });
        }
        this.variantsBox.setX(this.contentX + nameW + GAP);
        this.variantsBox.setY(y);
        this.variantsBox.setWidth(nameW);
        this.add(this.variantsBox);

        EButton metricButton = new EButton(this.contentX + this.contentW - metricW, y, metricW, BUTTON_H,
                Component.literal(metric.shortLabel), EButton.Style.SECONDARY, () -> {
                    this.experiment.metric = metric.next().name();
                    this.rebuildWidgets();
                });
        metricButton.setTooltip((Component.literal(
                "The metric picked out: " + metric.label.toLowerCase() + ". Click for the next")));
        this.add(metricButton);

        y += BUTTON_H + GAP;
        EField description = new EField(this.font, this.contentX, y, this.contentW, BUTTON_H,
                Component.literal("What was changed"));
        description.setHint(Component.literal("What was changed"));
        description.setTooltip((Component.literal(
                "What differs between the variants, and anything else worth noting")));
        description.setMaxLength(200);
        description.setValue(this.experiment.description);
        description.setResponder(text -> this.experiment.description = text);
        this.add(description);

        int tableY = y + BUTTON_H + 6;
        LabTable table = new LabTable(this.contentX, tableY, this.contentW, this.contentBottom - 11 - tableY, this.scroll,
                ComparisonView.weights());
        this.add(table);
        text(ComparisonView.priceBasis(), this.contentX + 1, this.contentBottom - 8, Theme.TEXT_MUTED);
        fill(table, metric);
    }

    private void fill(LabTable table, Metric metric) {
        boolean anyAssigned = false;
        for (Experiment.Variant variant : this.experiment.variants) anyAssigned |= !variant.sessionIds.isEmpty();
        if (anyAssigned) {
            ComparisonView.fill(table, groups(this.experiment), metric);
        } else {
            table.alignRight(1, 2, 3, 5);
        }

        table.section("ASSIGN  (click a session to change its variant)");
        List<Session> sessions = Lab.store().sessions();
        if (sessions.isEmpty()) {
            table.text("No sessions yet.", Theme.TEXT_MUTED);
            return;
        }
        table.head("Session", "", "", "", metric.shortLabel, "Time", "Variant");
        for (Session session : sessions) {
            Experiment.Variant variant = this.experiment.variantOf(session.id);
            SessionReport report = Lab.report(session);
            table.add(Fmt.shortDate(session.startMillis) + "  " + session.displayName(), "", "", "",
                            Fmt.measure(report.rate(metric)), Fmt.duration(report.trackedMillis),
                            variant == null ? "-" : variant.name)
                    .color(6, variant == null ? Theme.TEXT_MUTED : Theme.TEXT)
                    .selected(variant != null)
                    .onClick(column -> cycle(session));
        }
    }

    @Override
    public void onClose() {
        if (!this.deleted && this.experiment.name.isBlank()) this.experiment.name = "Unnamed experiment";
        Lab.store().saveExperiments();
        super.onClose();
    }
}
