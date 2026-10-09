package net.senbler.sentils.client.lab.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import gg.essential.elementa.UIComponent;
import gg.essential.elementa.components.UIBlock;
import gg.essential.elementa.components.UIContainer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.senbler.sentils.client.gui.Theme;
import net.senbler.sentils.client.gui.screen.SettingsScreen;
import net.senbler.sentils.client.gui.elementa.E;
import net.senbler.sentils.client.gui.elementa.EButton;
import net.senbler.sentils.client.gui.elementa.EField;
import net.senbler.sentils.client.lab.Fmt;
import net.senbler.sentils.client.lab.Lab;
import net.senbler.sentils.client.lab.LabSettings;
import net.senbler.sentils.client.lab.analysis.ActivityStats;
import net.senbler.sentils.client.lab.analysis.Comparison;
import net.senbler.sentils.client.lab.analysis.Metric;
import net.senbler.sentils.client.lab.analysis.SessionReport;
import net.senbler.sentils.client.lab.core.SessionTimes;
import net.senbler.sentils.client.lab.model.Activity;
import net.senbler.sentils.client.lab.model.Experiment;
import net.senbler.sentils.client.lab.model.Session;
import net.senbler.sentils.client.lab.model.Setup;

/**
 * The Efficiency Lab's dashboard: an overview, the session being recorded, the history, the
 * comparison of whichever sessions are ticked in the history, and the experiments.
 * <p>
 * The pages lean on tiles, icons and arrows rather than sentences. What needs explaining is in
 * the tooltips of the things it is about.
 */
public class LabScreen extends LabPanelScreen {

    public enum Tab {
        OVERVIEW("Overview"), ACTIVE("Session"), HISTORY("History"), COMPARE("Compare"), EXPERIMENTS("Experiments");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private static final String[] RANGES = {"All time", "Last 7 days", "Last 30 days", "Last 90 days"};
    private static final int[] RANGE_DAYS = {0, 7, 30, 90};
    private static final String[] SORTS = {"Newest", "Oldest", "Longest", "Activity", "Best"};
    private static final String[] GROUPINGS = {"Each session", "Setup", "Activity", "Place", "Earlier vs later"};
    private static final long DAY = 24 * 3_600_000L;
    private static final int TREND_SESSIONS = 12;
    private static final int REFRESH_TICKS = 20;
    private static final int SEARCH_W = 84;
    private static final DateTimeFormatter DAY_OF_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ROOT);

    // Remembered so that coming back to the screen finds it as it was left.
    private static Tab tab = Tab.OVERVIEW;
    private static String search = "";
    private static Activity activityFilter; // null for every activity
    private static int range;
    private static int sort;
    private static final Set<String> selected = new LinkedHashSet<>(); // sessions ticked for comparing
    private static int grouping;
    private static int baseline; // which side comes first
    private static Activity startActivity = Activity.FARMING;
    private static String startName = "";
    private static String startSetup; // id, or null

    private final int[] scroll = new int[1];
    private EField searchBox;
    private boolean confirmCancel;
    private int ticksUntilRefresh = REFRESH_TICKS;
    private int bodyY;

    // The overview's two charts, worked out when the screen is built.
    private record Bar(String label, float share, String amount) {}

    private final List<Bar> bars = new ArrayList<>();
    private final List<SessionReport> trend = new ArrayList<>(); // the graph's sessions, oldest first
    private final List<Activity> trendChoices = new ArrayList<>(); // the activities the graph can be of
    private Activity trendActivity; // the one it is of, or null with no sessions at all
    private static Activity chartActivity; // the one picked for it, or null for the most recorded
    private int chartsY;
    private int chartsH;
    private String footer = ""; // a line of muted text along the bottom

    public LabScreen(Screen parent) {
        super(parent, Component.literal("Efficiency Lab"));
    }

    public static LabScreen onTab(Screen parent, Tab opened) {
        tab = opened;
        return new LabScreen(parent);
    }

    @Override
    protected String subtitle() {
        Session active = Lab.active();
        if (active == null) return "No session running";
        long now = System.currentTimeMillis();
        return (Lab.recorder().isPaused() ? "Paused: " : "Recording: ") + active.displayName() + "  "
                + Fmt.clock(SessionTimes.tracked(active, now));
    }

    private void show(Tab opened) {
        tab = opened;
        this.scroll[0] = 0;
        this.confirmCancel = false;
        this.rebuildWidgets();
    }

    @Override
    protected void build() {
        layout();
        this.footer = "";

        int right = this.left + this.panelW - PAD;
        right = addButtonLeftOf(right, this.top + 9, "Close", EButton.Style.PRIMARY, this::onClose);
        addButtonLeftOf(right, this.top + 9, "Settings", EButton.Style.SECONDARY,
                () -> open(SettingsScreen.onLab(this)));

        int x = this.contentX;
        for (Tab each : Tab.values()) {
            int w = this.font.width(each.label) + 14;
            this.add(new EButton(x, this.contentY, w, BUTTON_H, Component.literal(each.label),
                    each == tab ? EButton.Style.PRIMARY : EButton.Style.SECONDARY, () -> show(each)));
            x += w + GAP;
        }
        this.bodyY = this.contentY + BUTTON_H + 6;

        switch (tab) {
            case OVERVIEW -> buildOverview();
            case ACTIVE -> buildActive();
            case HISTORY -> buildHistory();
            case COMPARE -> buildCompare();
            case EXPERIMENTS -> buildExperiments();
        }
    }

    @Override
    public void onTick() {
        super.onTick();
        // The running session's figures move on, so its page is rebuilt now and then.
        if (tab == Tab.ACTIVE && Lab.recorder().isRecording() && --this.ticksUntilRefresh <= 0) {
            this.ticksUntilRefresh = REFRESH_TICKS;
            this.rebuildWidgets();
        }
    }

    private LabTable addTable(int y, int bottom, float... weights) {
        LabTable table = new LabTable(this.contentX, y, this.contentW, bottom - y, this.scroll, weights);
        this.add(table);
        return table;
    }

    /** Adds tiles side by side across the width at the given y, each an icon, a label, a figure and a tooltip. */
    private void addTiles(int y, Object[]... tiles) {
        int w = (this.contentW - GAP * (tiles.length - 1)) / tiles.length;
        for (int i = 0; i < tiles.length; i++) {
            this.add(new StatTile(this.contentX + i * (w + GAP), y, w, (Icons.Icon) tiles[i][0],
                    (String) tiles[i][1], (String) tiles[i][2]).tip((String) tiles[i][3]));
        }
    }

    private EButton tip(EButton button, String tooltip) {
        button.setTooltip((Component.literal(tooltip)));
        return button;
    }

    // A row of controls above a page's table, which goes onto a second row if the window is too narrow.
    private int toolX;
    private int toolY;

    private void beginTools() {
        this.toolX = this.contentX;
        this.toolY = this.bodyY;
    }

    /** Makes room for a control of the given width, moving down a row if it does not fit. Returns its x. */
    private int toolSlot(int w) {
        if (this.toolX > this.contentX && this.toolX + w > this.contentX + this.contentW) {
            this.toolX = this.contentX;
            this.toolY += BUTTON_H + GAP;
        }
        int x = this.toolX;
        this.toolX += w + GAP;
        return x;
    }

    private void tool(String label, String tooltip, EButton.Style style, Runnable action) {
        int w = this.font.width(label) + 14;
        int x = toolSlot(w);
        this.add(tip(new EButton(x, this.toolY, w, BUTTON_H, Component.literal(label), style, action),
                tooltip));
    }

    private void tool(String label, String tooltip, Runnable action) {
        tool(label, tooltip, EButton.Style.SECONDARY, action);
    }

    /** Where the page's content starts, below the controls. */
    private int toolsBottom() {
        return this.toolY + BUTTON_H + 6;
    }

    private void cycleMetric() {
        LabSettings.primaryMetric = LabSettings.metric().next().name();
        LabSettings.save();
        this.rebuildWidgets();
    }

    // --- Overview ---

    private void buildOverview() {
        List<Session> sessions = Lab.store().sessions();
        long now = System.currentTimeMillis();
        Metric metric = LabSettings.metric();

        long total = 0;
        long week = 0;
        Map<Activity, Long> byActivity = new EnumMap<>(Activity.class);
        for (Session session : sessions) {
            long tracked = SessionTimes.tracked(session, now);
            total += tracked;
            if (now - session.startMillis < 7 * DAY) week += tracked;
            byActivity.merge(session.activityType(), tracked, Long::sum);
        }
        Activity most = null;
        for (Map.Entry<Activity, Long> entry : byActivity.entrySet()) {
            if (most == null || entry.getValue() > byActivity.get(most)) most = entry.getKey();
        }

        this.bars.clear();
        List<Map.Entry<Activity, Long>> ordered = new ArrayList<>(byActivity.entrySet());
        ordered.sort(Map.Entry.<Activity, Long>comparingByValue().reversed());
        for (Map.Entry<Activity, Long> entry : ordered) {
            this.bars.add(new Bar(entry.getKey().label, total > 0 ? (float) entry.getValue() / total : 0,
                    Fmt.duration(entry.getValue())));
        }

        // The trend: the chosen metric over the latest sessions of the most recorded activity,
        // oldest first, and the later half of them set against the earlier half.
        this.trend.clear();
        this.trendChoices.clear();
        for (Map.Entry<Activity, Long> entry : ordered) this.trendChoices.add(entry.getKey());
        this.trendActivity = chartActivity != null && byActivity.containsKey(chartActivity) ? chartActivity : most;
        Comparison.GroupResult later = null;
        if (this.trendActivity != null) {
            List<SessionReport> recent = this.trend;
            for (Session session : sessions) {
                if (session.activityType() != this.trendActivity || recent.size() >= TREND_SESSIONS) continue;
                SessionReport report = Lab.report(session);
                if (report.rate(metric).isAvailable()) recent.addFirst(report);
            }
            if (recent.size() >= 2) {
                int half = recent.size() / 2;
                later = Comparison.compare(List.of(
                        new Comparison.Group("the earlier sessions", recent.subList(0, half)),
                        new Comparison.Group("The later sessions", recent.subList(half, recent.size()))), metric)
                        .groups.get(1);
            }
        }

        // Five tiles: four counts, and which way the chosen metric is heading.
        int w = (this.contentW - GAP * 4) / 5;
        Object[][] counts = {
                {Icons.Icon.BOOK, "SAVED", Integer.toString(sessions.size()), "Sessions saved"},
                {Icons.Icon.CLOCK, "TIME", Fmt.duration(total), "Time tracked across every session"},
                {Icons.Icon.CALENDAR, "7 DAYS", Fmt.duration(week), "Time tracked in the last seven days"},
                {Icons.Icon.TAG, "TOP", most != null ? most.label : "-", "The activity with the most time tracked"},
        };
        for (int i = 0; i < counts.length; i++) {
            this.add(new StatTile(this.contentX + i * (w + GAP), this.bodyY, w, (Icons.Icon) counts[i][0],
                    (String) counts[i][1], (String) counts[i][2]).tip((String) counts[i][3]));
        }
        StatTile trendTile = new StatTile(this.contentX + 4 * (w + GAP), this.bodyY, w, Icons.of(metric), "TREND",
                later == null || later.medianPercent == null ? "-" : "");
        if (later != null && later.medianPercent != null) {
            trendTile.change(later.medianDifference, String.format(Locale.ROOT, "%.0f%%", Math.abs(later.medianPercent)));
        }
        trendTile.tip(later != null && later.summary != null ? later.summary
                : "Which way " + metric.label.toLowerCase(Locale.ROOT) + " is heading. Needs a few sessions of one activity");
        this.add(trendTile);

        this.chartsY = this.bodyY + StatTile.HEIGHT + 6;
        this.chartsH = Math.clamp((this.contentBottom - this.chartsY) * 3 / 5, 40, 120);
        int tableY = this.chartsY + this.chartsH + 6;
        addCharts();

        LabTable table = addTable(tableY, this.contentBottom, 2f, 1.5f, 3f, 1.5f, 1.6f);
        table.alignRight(3, 4);
        if (sessions.isEmpty()) {
            table.text("No sessions yet. Start one on the Session tab.", Theme.TEXT_MUTED);
        } else {
            table.head("Started", "Activity", "Name", "Tracked", metric.shortLabel);
            for (Session session : sessions.subList(0, Math.min(8, sessions.size()))) addSessionRow(table, session, metric, false);
        }
        if (Lab.store().corruptCount() > 0) {
            table.note(Lab.store().corruptCount() + " unreadable session files were moved to the corrupt folder", Theme.WARN);
        }
        if (Lab.store().newerCount() > 0) {
            table.note(Lab.store().newerCount() + " session files are from a newer version and were left alone", Theme.WARN);
        }
    }

    /** A row for a session in a list. With a tick box in front if it is for the history. */
    private void addSessionRow(LabTable table, Session session, Metric metric, boolean tickBox) {
        SessionReport report = Lab.report(session);
        SessionReport.Measure rate = report.rate(metric);
        List<String> cells = new ArrayList<>();
        if (tickBox) cells.add(selected.contains(session.id) ? "[x]" : "[ ]");
        cells.add(Fmt.shortDate(session.startMillis));
        cells.add(session.activityType().label);
        cells.add(session.displayName() + (session.recovered ? " (interrupted)" : ""));
        if (tickBox) cells.add(Lab.setupName(session.setupId));
        cells.add(Fmt.duration(report.trackedMillis));
        cells.add(Fmt.measure(rate));
        LabTable.Row row = table.add(cells.toArray(String[]::new));
        if (!rate.isAvailable()) row.color(cells.size() - 1, Theme.TEXT_MUTED);
        row.selected(tickBox && selected.contains(session.id));
        row.onClick(column -> {
            if (tickBox && column == 0) {
                if (!selected.remove(session.id)) selected.add(session.id);
                this.rebuildWidgets();
            } else {
                open(new SessionScreen(this, session));
            }
        });
    }

    /** The overview's two charts: time by activity as bars, and the chosen metric session by session. */
    private void addCharts() {
        int half = (this.contentW - GAP) / 2;
        // Time by activity
        box(this.contentX, this.chartsY, half, this.chartsH, 5, Theme.CARD);
        text("TIME BY ACTIVITY", this.contentX + 7, this.chartsY + 5, Theme.TEXT_MUTED);
        int y = this.chartsY + 17;
        int labelW = 58;
        int amountW = 48;
        int barW = half - 14 - labelW - amountW;
        for (Bar bar : this.bars) {
            if (y + 9 > this.chartsY + this.chartsH - 3) break;
            text(bar.label(), this.contentX + 7, y, Theme.TEXT);
            box(this.contentX + 7 + labelW, y + 1, barW, 6, 3, Theme.CONTROL);
            box(this.contentX + 7 + labelW, y + 1, Math.max(3, barW * bar.share()), 6, 3, Theme.ACCENT);
            text(bar.amount(), this.contentX + half - 7 - this.font.width(bar.amount()), y,
                    Theme.TEXT_MUTED);
            y += 10;
        }

        addGraph(this.contentX + half + GAP, half);
    }

    /** A straight line of the graph: an axis, or one of the lines across it. */
    private void rule(float x, float y, float width, float height, int color) {
        add(new UIBlock(E.color(color)).setX(E.px(x)).setY(E.px(y)).setWidth(E.px(width)).setHeight(E.px(height)));
    }

    /** A step between the numbers up an axis that is easy to read: 1, 2 or 5, times ten to some power. */
    private static double roundStep(double rough) {
        double power = Math.pow(10, Math.floor(Math.log10(rough)));
        double digit = rough / power;
        return (digit <= 1 ? 1 : digit <= 2 ? 2 : digit <= 5 ? 5 : 10) * power;
    }

    /**
     * The graph: one bar for each of an activity's latest sessions, oldest on the left, as high
     * as the session's figure for the chosen metric. The figures are up the left side and the
     * days the sessions were on along the bottom; the two buttons choose the activity and the
     * metric.
     */
    private void addGraph(int x, int width) {
        box(x, this.chartsY, width, this.chartsH, 5, Theme.CARD);
        Metric metric = LabSettings.metric();
        Activity activity = this.trendActivity;
        if (activity == null) {
            text("No sessions to graph yet", x + 7, this.chartsY + 5, Theme.TEXT_MUTED);
            return;
        }

        // The heading is the two things the graph can be changed by, each a button.
        String what = metric == Metric.ACTIVITY_PER_HOUR ? ActivityStats.primaryLabel(activity) : metric.label;
        if (this.font.width(activity.label) + this.font.width(what) + 24 + GAP > width - 14) {
            what = ActivityStats.shortLabel(metric, activity);
        }
        int activityW = this.font.width(activity.label) + 12;
        this.add(tip(new EButton(x + 6, this.chartsY + 4, activityW, 13, Component.literal(activity.label),
                EButton.Style.SECONDARY, () -> {
                    int at = this.trendChoices.indexOf(activity);
                    chartActivity = this.trendChoices.get((at + 1) % this.trendChoices.size());
                    this.rebuildWidgets();
                }), "The activity whose sessions are graphed. Click for the next"));
        this.add(tip(new EButton(x + 6 + activityW + GAP, this.chartsY + 4, this.font.width(what) + 12, 13,
                Component.literal(what), EButton.Style.SECONDARY, this::cycleMetric),
                metric.meaning + ", for each hour tracked. Click for the next"));

        int plotTop = this.chartsY + 24;
        // The days go under the graph if there is the height for them.
        boolean days = this.chartsH >= 70;
        int plotBottom = this.chartsY + this.chartsH - (days ? 17 : 6);
        int plotH = plotBottom - plotTop;
        if (this.trend.isEmpty() || plotH < 8) {
            text(E.fit("No " + activity.label + " session has this figure yet", width - 14), x + 7, plotTop, Theme.TEXT_MUTED);
            return;
        }

        double low = 0;
        double high = 0;
        for (SessionReport report : this.trend) {
            double value = report.rate(metric).value();
            low = Math.min(low, value);
            high = Math.max(high, value);
        }
        if (high - low <= 0) high = 1;
        // The numbers up the side are round ones, and the graph runs from one of them to another.
        double step = roundStep((high - low) / Math.max(1, plotH / 16));
        low = Math.floor(low / step) * step;
        high = Math.ceil(high / step) * step;
        double span = high - low;

        int axisW = 0;
        for (double mark = low; mark <= high + step / 2; mark += step) {
            axisW = Math.max(axisW, this.font.width(Fmt.number(mark)));
        }
        int plotX = x + 7 + axisW + 4;
        int plotW = x + width - 7 - plotX;
        for (double mark = low; mark <= high + step / 2; mark += step) {
            float y = (float) (plotBottom - (mark - low) / span * plotH);
            String label = Fmt.number(Math.abs(mark) < step / 2 ? 0 : mark);
            text(label, plotX - 4 - this.font.width(label), Math.round(y) - 4, Theme.TEXT_MUTED);
            rule(plotX, y, plotW, 1, Theme.DIVIDER);
        }
        float zeroY = (float) (plotBottom - (0 - low) / span * plotH);
        rule(plotX, plotTop, 1, plotH + 1, Theme.TEXT_MUTED);
        rule(plotX, zeroY, plotW, 1, Theme.TEXT_MUTED);

        float slot = Math.min(40f, (plotW - 3f) / this.trend.size());
        String unit = ActivityStats.shortLabel(metric, activity);
        for (int i = 0; i < this.trend.size(); i++) {
            SessionReport report = this.trend.get(i);
            Session session = report.session;
            double value = report.rate(metric).value();
            float height = (float) (Math.abs(value) / span * plotH);
            float barX = plotX + 3 + i * slot;
            UIComponent bar = box(barX, value >= 0 ? zeroY - height : zeroY + 1, Math.max(2, slot - 2),
                    Math.max(1, height), 1.5f, value >= 0 ? Theme.ACCENT : Theme.DANGER);
            // The whole height of the graph over a bar tells of it and opens its session, a short bar being hard to hit.
            UIComponent column = add(new UIContainer().setX(E.px(barX)).setY(E.px(plotTop))
                    .setWidth(E.px(Math.max(2, slot - 2))).setHeight(E.px(plotH)));
            int color = value >= 0 ? Theme.ACCENT : Theme.DANGER;
            column.onMouseEnterRunnable(() -> E.fade(bar, E.color(Theme.lerpColor(color, Theme.TEXT, 0.35f))));
            column.onMouseLeaveRunnable(() -> E.fade(bar, E.color(color)));
            E.tooltip(column, session.displayName() + "\n" + Fmt.date(session.startMillis) + "\n"
                    + Fmt.number(value) + " " + unit + " over " + Fmt.duration(report.trackedMillis) + "\nClick to open");
            E.onClick(column, () -> open(new SessionScreen(this, session)));
        }

        if (!days) return;
        String first = DAY_OF_MONTH.format(Instant.ofEpochMilli(this.trend.getFirst().session.startMillis).atZone(ZoneId.systemDefault()));
        String last = DAY_OF_MONTH.format(Instant.ofEpochMilli(this.trend.getLast().session.startMillis).atZone(ZoneId.systemDefault()));
        int lastX = Math.min(Math.round(plotX + 3 + (this.trend.size() - 1) * slot), x + width - 7 - this.font.width(last));
        text(first, plotX + 3, plotBottom + 5, Theme.TEXT_MUTED);
        String caption = "each bar is a session";
        int afterFirst = plotX + 3 + this.font.width(first) + 6;
        if (this.trend.size() > 1 && lastX >= afterFirst) {
            text(last, lastX, plotBottom + 5, Theme.TEXT_MUTED);
            afterFirst = lastX + this.font.width(last) + 6;
        }
        if (afterFirst + this.font.width(caption) <= x + width - 7) {
            text(caption, x + width - 7 - this.font.width(caption), plotBottom + 5, Theme.TEXT_MUTED);
        }
    }

    // --- The session being recorded ---

    private void buildActive() {
        Session active = Lab.active();
        if (active == null) {
            buildStart();
            return;
        }

        beginTools();
        boolean paused = Lab.recorder().isPaused();
        tool(paused ? "Resume" : "Pause", "Nothing is recorded, and no time tracked, while paused", () -> {
            if (paused) Lab.resume();
            else Lab.pause("paused by hand");
            this.rebuildWidgets();
        });
        tool("Split", "Mark a change of setup or of what you are doing. The report then shows each part on its own", () -> {
            Lab.split("");
            this.rebuildWidgets();
        });
        tool("End", "End the session, save it and open its report", EButton.Style.PRIMARY, () -> {
            Session ended = Lab.stop();
            if (ended != null) open(new SessionScreen(this, ended));
        });
        tool(this.confirmCancel ? "Sure?" : "Cancel", "Throw the session away without saving it", EButton.Style.DANGER, () -> {
            if (this.confirmCancel) {
                Lab.cancel();
                this.confirmCancel = false;
            } else {
                this.confirmCancel = true;
            }
            this.rebuildWidgets();
        });

        SessionReport report = Lab.report(active);
        int y = toolsBottom();
        for (StatTile tile : SessionView.tiles(this.contentX, y, this.contentW, report)) this.add(tile);
        LabTable table = addTable(y + StatTile.HEIGHT + 6, this.contentBottom, SessionView.weights());
        table.alignRight(1, 2, 3, 4);
        SessionView.fillActivity(table, report);
        SessionView.fillItems(table, report, null);
    }

    private void buildStart() {
        int y = this.bodyY;
        int startW = this.font.width("Start") + 20;
        int activityW = this.font.width("Foraging") + 14;
        int x = this.contentX;
        this.add(tip(new EButton(x, y, activityW, BUTTON_H, Component.literal(startActivity.label),
                EButton.Style.SECONDARY, () -> {
                    startActivity = Activity.values()[(startActivity.ordinal() + 1) % Activity.values().length];
                    this.rebuildWidgets();
                }), "What the session is of, which sets the skill whose XP is its own. Click for the next"));
        x += activityW + GAP;
        String setup = Lab.setupName(startSetup);
        int setupW = Math.min(90, this.font.width(setup.isEmpty() ? "No setup" : setup) + 14);
        this.add(tip(new EButton(x, y, setupW, BUTTON_H,
                Component.literal(setup.isEmpty() ? "No setup" : setup), EButton.Style.SECONDARY, () -> {
                    startSetup = nextSetup(startSetup);
                    this.rebuildWidgets();
                }), "The setup you are using, for comparing setups later. Setups are made on the Experiments tab"));
        x += setupW + GAP;

        EField name = new EField(this.font, x, y, Math.max(40, this.contentX + this.contentW - x - startW - GAP),
                BUTTON_H, Component.literal("Session name"));
        name.setHint(Component.literal("Name"));
        name.setMaxLength(60);
        name.setValue(startName);
        name.setResponder(text -> startName = text);
        this.add(name);
        this.add(new EButton(this.contentX + this.contentW - startW, y, startW, BUTTON_H,
                Component.literal("Start"), EButton.Style.PRIMARY, () -> {
                    Lab.say(Lab.start(startActivity, startName, startSetup));
                    startName = "";
                    this.rebuildWidgets();
                }));

        // What a session records, as four tiles; the fine print is in their tooltips.
        y += BUTTON_H + 6;
        addTiles(y,
                new Object[] {Icons.Icon.CLOCK, "TIME", "Tracked",
                        "From start to end, less any time paused. The session pauses itself if you disconnect"},
                new Object[] {Icons.of(Metric.ITEMS_PER_HOUR), "ITEMS", "Sacks, bag",
                        "What the [Sacks] chat message says went in and out of your sacks, and what changes in your "
                                + "inventory while no chest or menu is open.\n\nItems that go straight to your sacks "
                                + "are only seen if the sack message is on in the SkyBlock settings. Changes with a "
                                + "menu open are not counted"},
                new Object[] {Icons.of(Metric.PURSE_PER_HOUR), "COINS", "Purse",
                        "How the purse on the scoreboard changes"},
                new Object[] {Icons.of(Metric.XP_PER_HOUR), "XP", "Skills",
                        "Skill XP, from the line above the hotbar. If it shows none, /lab debug says why"});

        LabTable table = addTable(y + StatTile.HEIGHT + 6, this.contentBottom, 2f, 1.5f, 3f, 1.5f, 1.6f);
        table.alignRight(3, 4);
        List<Session> sessions = Lab.store().sessions();
        Metric metric = LabSettings.metric();
        if (sessions.isEmpty()) {
            table.text("Pick an activity and press Start.", Theme.TEXT_MUTED);
        } else {
            table.head("Started", "Activity", "Name", "Tracked", metric.shortLabel);
            for (Session session : sessions.subList(0, Math.min(6, sessions.size()))) addSessionRow(table, session, metric, false);
        }
    }

    /** The setup after the given one in the list, then none, then the first again. */
    static String nextSetup(String id) {
        List<Setup> setups = Lab.store().setups();
        if (setups.isEmpty()) return null;
        if (id == null) return setups.getFirst().id;
        for (int i = 0; i < setups.size(); i++) {
            if (setups.get(i).id.equals(id)) return i + 1 < setups.size() ? setups.get(i + 1).id : null;
        }
        return null;
    }

    // --- History ---

    private List<Session> filtered() {
        long now = System.currentTimeMillis();
        String[] words = search.trim().toLowerCase(Locale.ROOT).split("\\s+");
        List<Session> shown = new ArrayList<>();
        for (Session session : Lab.store().sessions()) {
            if (activityFilter != null && session.activityType() != activityFilter) continue;
            if (RANGE_DAYS[range] > 0 && now - session.startMillis > RANGE_DAYS[range] * DAY) continue;
            String text = (session.displayName() + " " + session.notes + " " + String.join(" ", session.tags) + " "
                    + Lab.setupName(session.setupId) + " " + session.location + " " + session.activityType().label)
                    .toLowerCase(Locale.ROOT);
            boolean matches = true;
            for (String word : words) matches &= text.contains(word);
            if (matches) shown.add(session);
        }

        Metric metric = LabSettings.metric();
        switch (sort) {
            case 1 -> shown.sort(Comparator.comparingLong(session -> session.startMillis));
            case 2 -> shown.sort(Comparator.comparingLong((Session session) -> SessionTimes.tracked(session, now)).reversed());
            case 3 -> shown.sort(Comparator.comparing(session -> session.activityType().label));
            case 4 -> {
                // Best first; sessions the metric is unavailable for go last.
                Map<Session, Double> rates = new LinkedHashMap<>();
                for (Session session : shown) {
                    Double rate = Lab.report(session).rate(metric).value();
                    rates.put(session, rate != null ? rate : Double.NEGATIVE_INFINITY);
                }
                shown.sort(Comparator.comparingDouble((Session session) -> rates.get(session)).reversed());
            }
            default -> { } // newest first, as stored
        }
        return shown;
    }

    private void buildHistory() {
        Metric metric = LabSettings.metric();
        beginTools();

        // The box is kept across rebuilds so it holds on to its text and cursor while the list changes.
        if (this.searchBox == null) {
            this.searchBox = new EField(this.font, 0, 0, SEARCH_W, BUTTON_H, Component.literal("Search sessions"));
            this.searchBox.setHint(Component.literal("Search"));
            this.searchBox.setTooltip((Component.literal(
                    "Search the sessions' names, notes, tags, setups and places")));
            this.searchBox.setMaxLength(40);
            this.searchBox.setValue(search);
            this.searchBox.setResponder(text -> {
                if (text.equals(search)) return;
                search = text;
                this.scroll[0] = 0;
                boolean focused = this.searchBox.hasKeyboard();
                this.rebuildWidgets();
                if (focused) this.searchBox.takeKeyboard();
            });
        }
        this.searchBox.setX(toolSlot(SEARCH_W));
        this.searchBox.setY(this.toolY);
        this.add(this.searchBox);

        tool(activityFilter == null ? "All activities" : activityFilter.label, "Show only one activity", () -> {
            Activity[] all = Activity.values();
            activityFilter = activityFilter == null ? all[0]
                    : activityFilter.ordinal() + 1 < all.length ? all[activityFilter.ordinal() + 1] : null;
            this.scroll[0] = 0;
            this.rebuildWidgets();
        });
        tool(RANGES[range], "Show only sessions started this recently", () -> {
            range = (range + 1) % RANGES.length;
            this.scroll[0] = 0;
            this.rebuildWidgets();
        });
        tool(SORTS[sort], "The order of the list. \"Best\" is by the metric shown", () -> {
            sort = (sort + 1) % SORTS.length;
            this.rebuildWidgets();
        });
        tool(metric.shortLabel, metric.label + ": " + metric.meaning.toLowerCase(Locale.ROOT) + ". Click for the next metric",
                this::cycleMetric);

        List<Session> shown = filtered();
        int footerY = this.contentBottom - BUTTON_H;
        LabTable table = addTable(toolsBottom(), footerY - 6, 0.6f, 2.3f, 1.5f, 2.3f, 1.4f, 1.5f, 1.5f);
        table.alignRight(5, 6);
        if (shown.isEmpty()) {
            table.text(Lab.store().sessions().isEmpty() ? "No sessions yet." : "Nothing matches.", Theme.TEXT_MUTED);
        } else {
            table.head("", "Started", "Activity", "Name", "Setup", "Tracked", metric.shortLabel);
            for (Session session : shown) addSessionRow(table, session, metric, true);
        }

        selected.removeIf(id -> Lab.store().session(id) == null);
        int right = this.contentX + this.contentW;
        int compareW = this.font.width("Compare " + selected.size()) + 14;
        this.add(tip(new EButton(right - compareW, footerY, compareW, BUTTON_H,
                Component.literal("Compare " + selected.size()), EButton.Style.PRIMARY, () -> show(Tab.COMPARE)),
                "Compare the ticked sessions. Tick a session with the box at the start of its row"));
        right -= compareW + GAP;
        right = addButtonLeftOf(right, footerY, "Tick all", EButton.Style.SECONDARY, () -> {
            for (Session session : shown) selected.add(session.id);
            this.rebuildWidgets();
        });
        addButtonLeftOf(right, footerY, "Clear", EButton.Style.SECONDARY, () -> {
            selected.clear();
            this.rebuildWidgets();
        });
        this.footer = shown.size() + " shown";
        text(this.footer, this.contentX + 1, this.contentBottom - BUTTON_H + 4, Theme.TEXT_MUTED);
    }

    // --- Compare ---

    private List<Comparison.Group> groups() {
        List<Session> sessions = new ArrayList<>();
        for (String id : selected) {
            Session session = Lab.store().session(id);
            if (session != null) sessions.add(session);
        }
        sessions.sort(Comparator.comparingLong(session -> session.startMillis));

        Map<String, List<SessionReport>> byName = new LinkedHashMap<>();
        for (int i = 0; i < sessions.size(); i++) {
            Session session = sessions.get(i);
            String setup = Lab.setupName(session.setupId);
            String name = switch (grouping) {
                case 1 -> setup.isEmpty() ? "No setup" : setup;
                case 2 -> session.activityType().label;
                case 3 -> session.location == null ? "Unknown" : session.location;
                case 4 -> i < sessions.size() / 2 ? "Earlier" : "Later";
                default -> session.displayName();
            };
            byName.computeIfAbsent(name, key -> new ArrayList<>()).add(Lab.report(session));
        }
        List<Comparison.Group> groups = new ArrayList<>();
        byName.forEach((name, reports) -> groups.add(new Comparison.Group(name, reports)));
        // Whichever side is put first is the baseline the rest are measured against.
        if (!groups.isEmpty()) Collections.rotate(groups, -(baseline % groups.size()));
        return groups;
    }

    private void buildCompare() {
        Metric metric = LabSettings.metric();
        beginTools();
        tool("By: " + GROUPINGS[grouping], "How the ticked sessions are put into sides to compare", () -> {
            grouping = (grouping + 1) % GROUPINGS.length;
            baseline = 0;
            this.rebuildWidgets();
        });
        tool(LabSettings.latestPrices ? "Latest prices" : "Recorded prices",
                "Recorded: each session at the prices from when it ended. Latest: every session at the same, "
                        + "newest prices, which takes price changes out of the comparison",
                () -> {
                    LabSettings.latestPrices = !LabSettings.latestPrices;
                    LabSettings.save();
                    this.rebuildWidgets();
                });
        tool("Swap", "Make the next side the baseline that the others are measured against", () -> {
            baseline++;
            this.rebuildWidgets();
        });
        tool(metric.shortLabel, "The metric picked out, and the one the list of sessions goes by: "
                + metric.label.toLowerCase(Locale.ROOT) + ". Click for the next", this::cycleMetric);

        List<Comparison.Group> groups = groups();
        LabTable table = addTable(toolsBottom(), this.contentBottom, ComparisonView.weights());
        if (groups.isEmpty()) {
            table.text("Tick sessions on the History tab to compare them.", Theme.TEXT_MUTED);
        } else {
            ComparisonView.fill(table, groups, metric);
        }
    }

    // --- Experiments ---

    private void buildExperiments() {
        beginTools();
        tool("New experiment", "Set two or more ways of doing something against each other", EButton.Style.PRIMARY,
                () -> open(new ExperimentScreen(this, Lab.newExperiment())));
        tool("Setups (" + Lab.store().setups().size() + ")",
                "Name the setups you use, so that sessions can be labelled and compared by them",
                () -> open(new SetupsScreen(this)));

        LabTable table = addTable(toolsBottom(), this.contentBottom, 2.6f, 1.8f, 2.2f, 1f, 1.6f, 2.1f);
        table.alignRight(3, 4);
        List<Experiment> experiments = Lab.store().experiments();
        if (experiments.isEmpty()) {
            table.text("Does one setup beat another? Make an experiment to find out.", Theme.TEXT_MUTED);
            return;
        }
        table.head("Experiment", "Metric", "Variants", "Runs", "Change", "Reading");
        for (Experiment experiment : experiments) {
            Comparison.Result result = ExperimentScreen.result(experiment);
            int sessions = 0;
            List<String> names = new ArrayList<>();
            for (Experiment.Variant variant : experiment.variants) {
                sessions += variant.sessionIds.size();
                names.add(variant.name);
            }
            Comparison.GroupResult second = result.groups.size() > 1 ? result.groups.get(1) : null;
            boolean measured = second != null && second.medianPercent != null;
            LabTable.Row row = table.add(experiment.name, result.metric.shortLabel, String.join(" / ", names),
                    Integer.toString(sessions),
                    measured ? String.format(Locale.ROOT, "%.1f%%", Math.abs(second.medianPercent)) : "-",
                    second != null && second.verdict != null ? second.verdict.label : "-");
            row.icon(1, Icons.of(result.metric));
            if (measured) row.arrow(4, second.medianDifference);
            if (second != null && second.verdict != null) row.dot(5, Icons.color(second.verdict));
            row.color(5, Theme.TEXT_MUTED).onClick(column -> open(new ExperimentScreen(this, experiment)));
        }
    }

}
