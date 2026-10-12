package net.senbler.sentils.client.gui.screen;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import gg.essential.elementa.ElementaVersion;
import gg.essential.elementa.UIComponent;
import gg.essential.elementa.WindowScreen;
import gg.essential.elementa.components.ScrollComponent;
import gg.essential.elementa.components.UIBlock;
import gg.essential.elementa.components.UIContainer;
import gg.essential.elementa.components.UIRoundedRectangle;
import gg.essential.elementa.components.UIText;
import gg.essential.elementa.components.UIWrappedText;
import gg.essential.elementa.components.input.UITextInput;
import gg.essential.elementa.constraints.AdditiveConstraint;
import gg.essential.elementa.constraints.CenterConstraint;
import gg.essential.elementa.constraints.ChildBasedMaxSizeConstraint;
import gg.essential.elementa.constraints.ChildBasedSizeConstraint;
import gg.essential.elementa.constraints.ConstantColorConstraint;
import gg.essential.elementa.constraints.PixelConstraint;
import gg.essential.elementa.constraints.RelativeConstraint;
import gg.essential.elementa.constraints.SiblingConstraint;
import gg.essential.elementa.constraints.SubtractiveConstraint;
import gg.essential.elementa.constraints.animation.AnimatingConstraints;
import gg.essential.elementa.constraints.animation.Animations;
import gg.essential.universal.UKeyboard;
import gg.essential.universal.USound;
import kotlin.Unit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import net.senbler.sentils.client.SentilsClient;
import net.senbler.sentils.client.debug.DebugSettings;
import net.senbler.sentils.client.dungeons.DungeonSettings;
import net.senbler.sentils.client.farming.FortuneScreen;
import net.senbler.sentils.client.gui.Theme;
import net.senbler.sentils.client.gui.elementa.E;
import net.senbler.sentils.client.hud.HudEditScreen;
import net.senbler.sentils.client.keybinds.KeybindConfigScreen;
import net.senbler.sentils.client.keybinds.KeybindsConfig;
import net.senbler.sentils.client.lab.Lab;
import net.senbler.sentils.client.lab.LabSettings;
import net.senbler.sentils.client.lab.price.Assumptions;
import net.senbler.sentils.client.lab.ui.LabScreen;
import net.senbler.sentils.client.mining.MetalDetectorSolver;
import net.senbler.sentils.client.mining.MiningSettings;
import net.senbler.sentils.client.notifications.NotificationConfigScreen;
import net.senbler.sentils.client.safari.SafariSettings;
import net.senbler.sentils.client.safari.waypoints.BellWaypoints;
import net.senbler.sentils.client.stats.ColdSettings;
import net.senbler.sentils.client.theme.ColorPickerScreen;
import net.senbler.sentils.client.theme.ThemeSettings;
import net.senbler.sentils.client.waypoints.WaypointSettings;

/**
 * The settings menu, drawn with Elementa: a header with a search box, a sidebar of categories,
 * and the chosen category's page of cards, each row of which is one setting with what it does
 * written under its name.
 * <p>
 * The frame is built once for the size of the window. The sidebar and the page are filled again
 * whenever what they show changes: a category picked, a search typed, a button that now says
 * something else.
 */
public class SettingsScreen extends WindowScreen {

    // The panel takes up this share of the screen, within these limits.
    private static final float SCREEN_SHARE_W = 0.84f;
    private static final float SCREEN_SHARE_H = 0.88f;
    private static final int MIN_W = 340;
    private static final int MIN_H = 224;
    private static final int MAX_W = 720;
    private static final int MAX_H = 440;

    private static final int SIDEBAR_W = 118;
    private static final int EDGE = 8; // between the panel's edge and what sits inside it
    private static final int PAD = 10; // between the sheet's edge and the page
    private static final int HEADER_H = 42;
    private static final int TAB_H = 20;
    private static final int TAB_GAP = 2;
    private static final int CHILD_INDENT = 12;
    private static final int ROW_PAD = 16; // above and below a row's text, together
    private static final int SECTION_GAP = 12;
    private static final int CONTROL_H = 18;
    private static final int BUTTON_MIN_W = 64;
    private static final int FIELD_W = 52; // short text fields and color swatches
    private static final int SEARCH_W = 150;
    private static final int DONE_H = 22;
    private static final int LOGO_SIZE = 22;
    private static final float FADE_SECONDS = 0.12f;

    /**
     * The sidebar entries, in display order. To add one, add it here and in buildCategory.
     * <p>
     * Giving an entry a parent makes it a sub-category, listed under that parent while the
     * parent is open. A parent is only a group: it has no page of its own, and clicking it
     * opens or closes its list of sub-categories. Sub-categories cannot have sub-categories
     * of their own.
     */
    private enum Category {
        NOTIFICATIONS("Notifications"),
        WAYPOINTS("Waypoints"),
        KEYBINDS("Keybinds"),
        SAFARI("Safari"),
        SAFARI_ALERTS("Alerts", SAFARI),
        SAFARI_TRACKERS("Trackers", SAFARI),
        SAFARI_WAYPOINTS("Waypoints", SAFARI),
        FARMING("Farming"),
        MINING("Mining"),
        MINING_NUCLEUS("Nucleus Runs", MINING),
        DUNGEONS("Dungeons"),
        LAB("Efficiency Lab"),
        STATS("Stats"),
        HUD("HUD"),
        THEME("Theme"),
        DEBUG("Debug");

        final String label;
        final Category parent; // null for a top-level entry

        Category(String label) {
            this(label, null);
        }

        Category(String label, Category parent) {
            this.label = label;
            this.parent = parent;
        }

        /** The label with the parent's in front for a sub-category, e.g. "Safari / Alerts". */
        String path() {
            return this.parent != null ? this.parent.label + " / " + this.label : this.label;
        }

        /**
         * Whether the entry is left out of the sidebar and the search. Farming has only the fortune
         * checker; Debug is for working on the mod, and shows only where SentilsClient.debugFeatures is on.
         */
        boolean hidden() {
            if (this == DEBUG) return !SentilsClient.debugFeatures;
            return this == FARMING && !SentilsClient.apiKeyFeatures;
        }

        List<Category> children() {
            List<Category> children = new ArrayList<>();
            for (Category category : values()) {
                if (category.parent == this) children.add(category);
            }
            return children;
        }
    }

    // Remembered so coming back from a sub-screen or reopening lands on the same category.
    private static Category selected = Category.NOTIFICATIONS;
    private static Category openGroup; // the group whose sub-categories are listed, or null

    private final Screen parent; // may be null (closes to the game)
    private final String name;
    // Typing in the search box swaps the selected category's page for the matching rows of every category.
    private String query = ""; // lower case and trimmed; empty when not searching

    // The parts of the frame that are filled again as things change
    private ScrollComponent sidebar;
    private int sidebarH;
    private ScrollComponent page;
    private UIText breadcrumbText;
    // Texts that say how something stands right now, each with where its words come from
    private final List<Runnable> liveTexts = new ArrayList<>();

    // Where the page being built has got to.
    private Category building; // the category whose rows are being added
    private String sectionName;
    private UIComponent card; // the current section's card, or null until its first row is added
    private int matchCount;

    public SettingsScreen(Screen parent, Component title) {
        super(ElementaVersion.V11, true, false, false, -1);
        // The newer of Elementa's two ways of drawing. The older one draws in the middle of the game working
        // out what to draw, which other mods that change how frames are drawn can break.
        setUseElementaRenderer(true);
        this.parent = parent;
        this.name = title.getString();
    }

    /** The settings, opened on the Efficiency Lab's page. */
    public static SettingsScreen onLab(Screen parent) {
        selected = Category.LAB;
        openGroup = null;
        return new SettingsScreen(parent, Component.literal("Sentils"));
    }

    private static void open(Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }

    /** A theme color, which is kept as a number, as Elementa wants it. One with no alpha is solid. */
    private static Color color(int argb) {
        return new Color((argb >>> 24) == 0 ? argb | 0xFF000000 : argb, true);
    }

    private static Color withAlpha(int argb, int alpha) {
        return new Color(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, alpha);
    }

    private static final Color CLEAR = new Color(0, 0, 0, 0);

    private static PixelConstraint px(float value) {
        return new PixelConstraint(value);
    }

    /** Measured from the right or the bottom instead. */
    private static PixelConstraint pxEnd(float value) {
        return new PixelConstraint(value, true);
    }

    /** Eases the component's color over to another. */
    private static void fade(UIComponent component, Color to) {
        AnimatingConstraints animation = component.makeAnimation();
        animation.setColorAnimation(Animations.OUT_QUAD, FADE_SECONDS, new ConstantColorConstraint(to));
        component.animateTo(animation);
    }

    /** Lights the component up while the mouse is on it. */
    private static void hover(UIComponent component, Color normal, Color hovered) {
        component.onMouseEnterRunnable(() -> fade(component, hovered));
        component.onMouseLeaveRunnable(() -> fade(component, normal));
    }

    private static void click() {
        USound.INSTANCE.playButtonPress(0.25f);
    }

    /** The entries currently listed in the sidebar: every top-level one, plus the open group's children. */
    private static List<Category> sidebarEntries() {
        List<Category> entries = new ArrayList<>();
        for (Category category : Category.values()) {
            if (category.parent != null || category.hidden()) continue;
            entries.add(category);
            if (category == openGroup) entries.addAll(category.children());
        }
        return entries;
    }

    private void select(Category category) {
        List<Category> children = category.children();
        if (children.isEmpty()) {
            selected = category;
            if (category.parent == null) openGroup = null;
        } else if (category == openGroup) {
            openGroup = null; // the page stays on the sub-category that was selected
        } else {
            openGroup = category;
            if (selected.parent != category) selected = children.getFirst();
        }
        // Picking a category leaves the search; building the frame again empties its box.
        this.query = "";
        buildFrame();
    }

    private boolean searching() {
        return !this.query.isEmpty();
    }

    private void onSearchChanged(String text) {
        String typed = text.trim().toLowerCase(Locale.ROOT);
        if (typed.equals(this.query)) return;
        this.query = typed;
        E.later(this::refresh);
    }

    /** Whether every word of the search is somewhere in the row's label, description, section or category. */
    private boolean matches(String label, String description) {
        String text = (label + " " + description + " " + this.sectionName + " " + this.building.path())
                .toLowerCase(Locale.ROOT);
        for (String word : this.query.split("\\s+")) {
            if (!text.contains(word)) return false;
        }
        return true;
    }

    /** Fills the sidebar and the page again, leaving the frame, and so the search box, as it is. */
    private void refresh() {
        if (this.page == null) return;
        this.breadcrumbText.setText(breadcrumb());
        fillSidebar();
        fillPage();
    }

    /** The line under the mod's name saying which page is open. */
    private String breadcrumb() {
        return searching() ? "Search results" : "Settings / " + selected.path();
    }

    @Override
    public void initScreen(int width, int height) {
        super.initScreen(width, height);
        buildFrame();
    }

    @Override
    public void onTick() {
        super.onTick();
        for (Runnable text : this.liveTexts) text.run();
    }

    @Override
    public void onKeyPressed(int keyCode, char typedChar, UKeyboard.Modifiers modifiers) {
        if (keyCode == UKeyboard.KEY_ESCAPE) {
            open(this.parent);
            return;
        }
        super.onKeyPressed(keyCode, typedChar, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Builds everything for the window as it is now. */
    private void buildFrame() {
        UIComponent window = getWindow();
        window.clearChildren();
        int panelW = Math.clamp(Math.round(this.width * SCREEN_SHARE_W), Math.min(MIN_W, this.width - 16), MAX_W);
        int panelH = Math.clamp(Math.round(this.height * SCREEN_SHARE_H), Math.min(MIN_H, this.height - 8), MAX_H);
        int sheetW = panelW - SIDEBAR_W - EDGE;
        int sheetH = panelH - HEADER_H - EDGE;

        new UIBlock(new Color(0, 0, 0, 110)).setWidth(new RelativeConstraint(1f)).setHeight(new RelativeConstraint(1f))
                .setChildOf(window);
        // A soft shadow, of three darker and darker shapes each a little smaller than the last
        for (int i = 3; i >= 1; i--) {
            new UIRoundedRectangle(10f + i * 2).setX(new CenterConstraint()).setY(new CenterConstraint())
                    .setWidth(px(panelW + i * 6)).setHeight(px(panelH + i * 6))
                    .setColor(new Color(0, 0, 0, 22)).setChildOf(window);
        }
        // The panel is the darker frame holding the header and sidebar, with a hairline round it.
        new UIRoundedRectangle(11f).setX(new CenterConstraint()).setY(new CenterConstraint())
                .setWidth(px(panelW + 2)).setHeight(px(panelH + 2)).setColor(color(Theme.PANEL_BORDER)).setChildOf(window);
        UIComponent panel = new UIRoundedRectangle(10f).setX(new CenterConstraint()).setY(new CenterConstraint())
                .setWidth(px(panelW)).setHeight(px(panelH)).setColor(color(Theme.SIDEBAR)).setChildOf(window);

        buildHeader(panel, sheetW);

        this.sidebar = new ScrollComponent("", 0f, Color.WHITE, false, true, false, false, 20f, 1f, null);
        this.sidebar.setX(px(EDGE)).setY(px(HEADER_H)).setWidth(px(SIDEBAR_W - EDGE * 2))
                .setHeight(px(sheetH - DONE_H - EDGE)).setChildOf(panel);
        this.sidebarH = sheetH - DONE_H - EDGE;

        UIComponent done = new UIRoundedRectangle(5f).setX(px(EDGE)).setY(pxEnd(EDGE))
                .setWidth(px(SIDEBAR_W - EDGE * 2)).setHeight(px(DONE_H)).setColor(color(Theme.ACCENT)).setChildOf(panel);
        new UIText("Done", false).setX(new CenterConstraint()).setY(new CenterConstraint())
                .setColor(color(Theme.ON_ACCENT)).setChildOf(done);
        hover(done, color(Theme.ACCENT), color(Theme.ACCENT_HOVER));
        E.onClick(done, () -> open(this.parent));

        // The page sits on a lighter sheet, to the right of the sidebar and below the header.
        UIComponent sheet = new UIRoundedRectangle(8f).setX(px(SIDEBAR_W)).setY(px(HEADER_H))
                .setWidth(px(sheetW)).setHeight(px(sheetH)).setColor(color(Theme.PANEL)).setChildOf(panel);
        this.page = new ScrollComponent("", 0f, Color.WHITE, false, true, false, false, 24f, 1f, null);
        this.page.setX(px(PAD)).setY(px(PAD)).setWidth(px(sheetW - PAD * 2)).setHeight(px(sheetH - PAD * 2))
                .setChildOf(sheet);
        UIComponent track = new UIContainer().setX(pxEnd(3)).setY(px(PAD)).setWidth(px(2))
                .setHeight(px(sheetH - PAD * 2)).setChildOf(sheet);
        UIComponent thumb = new UIRoundedRectangle(1f).setWidth(new RelativeConstraint(1f))
                .setColor(color(Theme.TEXT_MUTED)).setChildOf(track);
        this.page.setVerticalScrollBarComponent(thumb, true);

        fillSidebar();
        fillPage();
    }

    /** The header: a badge with the mod's initial, its name, where in the settings this is, and the search box. */
    private void buildHeader(UIComponent panel, int sheetW) {
        UIComponent logo = new UIRoundedRectangle(6f).setX(px(EDGE + 2)).setY(px((HEADER_H - LOGO_SIZE) / 2f))
                .setWidth(px(LOGO_SIZE)).setHeight(px(LOGO_SIZE)).setColor(color(Theme.ACCENT)).setChildOf(panel);
        new UIText(this.name.isEmpty() ? "" : this.name.substring(0, 1), false).setX(new CenterConstraint())
                .setY(new CenterConstraint()).setColor(color(Theme.ON_ACCENT)).setChildOf(logo);
        int titleX = EDGE + 2 + LOGO_SIZE + 8;
        new UIText(this.name, false).setX(px(titleX)).setY(px(HEADER_H / 2f - 10)).setColor(color(Theme.TEXT))
                .setChildOf(panel);
        this.breadcrumbText = new UIText(breadcrumb(), false);
        this.breadcrumbText.setX(px(titleX)).setY(px(HEADER_H / 2f + 1)).setColor(color(Theme.TEXT_MUTED)).setChildOf(panel);

        int searchW = Math.min(SEARCH_W, Math.max(60, sheetW - 120));
        UIComponent box = new UIRoundedRectangle(5f).setX(pxEnd(EDGE)).setY(px((HEADER_H - CONTROL_H) / 2f))
                .setWidth(px(searchW)).setHeight(px(CONTROL_H)).setColor(color(Theme.CONTROL)).setChildOf(panel);
        UITextInput search = new UITextInput("Search settings", false, withAlpha(Theme.ACCENT, 120));
        search.setX(px(7)).setY(new CenterConstraint()).setWidth(px(searchW - 14)).setHeight(px(9))
                .setColor(color(Theme.TEXT)).setChildOf(box);
        search.onUpdate(text -> {
            onSearchChanged(text);
            return Unit.INSTANCE;
        });
        box.onMouseClickConsumer(event -> search.grabWindowFocus());
        hover(box, color(Theme.CONTROL), color(Theme.CONTROL_HOVER));
    }

    private void fillSidebar() {
        this.sidebar.clearChildren();
        for (Category category : sidebarEntries()) {
            boolean child = category.parent != null;
            boolean group = !category.children().isEmpty();
            // A closed group is marked as selected while the page shown is one of its sub-categories.
            // Nothing is marked while searching, since the results come from every category.
            boolean marked = !searching()
                    && (category == selected || (category == selected.parent && category != openGroup));
            Color normal = marked ? withAlpha(Theme.ACCENT, 46) : CLEAR;

            // A sub-category's row is set in from the group's, past the line that ties it to the group, so
            // that the color behind it starts where its own words do.
            int inset = child ? CHILD_INDENT - 2 : 0;
            UIComponent slot = new UIContainer().setY(new SiblingConstraint(TAB_GAP)).setWidth(new RelativeConstraint(1f))
                    .setHeight(px(TAB_H)).setChildOf(this.sidebar);
            if (child) {
                new UIBlock(color(Theme.DIVIDER)).setX(px(5)).setY(px(-TAB_GAP)).setWidth(px(1))
                        .setHeight(px(TAB_H + TAB_GAP)).setChildOf(slot);
            }
            UIComponent tab = new UIRoundedRectangle(5f).setX(px(inset))
                    .setWidth(new SubtractiveConstraint(new RelativeConstraint(1f), px(inset))).setHeight(px(TAB_H))
                    .setColor(normal).setChildOf(slot);
            if (marked) {
                new UIRoundedRectangle(1f).setX(px(2)).setY(new CenterConstraint()).setWidth(px(2))
                        .setHeight(px(10)).setColor(color(Theme.ACCENT)).setChildOf(tab);
            }
            new UIText(category.label, false).setX(px(9)).setY(new CenterConstraint())
                    .setColor(color(marked || category == openGroup ? Theme.TEXT : Theme.TEXT_MUTED)).setChildOf(tab);
            if (group) {
                new UIText(category == openGroup ? "-" : "+", false).setX(pxEnd(7)).setY(new CenterConstraint())
                        .setColor(color(Theme.TEXT_MUTED)).setChildOf(tab);
            }
            hover(tab, normal, marked ? withAlpha(Theme.ACCENT, 70) : color(Theme.CONTROL));
            E.onClick(tab, () -> select(category));
        }
        // Scrolled so that the page shown, and as much of its group as fits, is in view.
        List<Category> entries = sidebarEntries();
        int index = entries.indexOf(selected);
        if (index < 0) index = entries.indexOf(selected.parent); // inside a closed group
        float below = (index + 1) * (TAB_H + TAB_GAP) - this.sidebarH;
        if (below > 0) this.sidebar.scrollTo(0f, -below, false);
    }

    /** Builds the selected category's page, or the search results from every category's page. */
    private void fillPage() {
        this.page.clearChildren();
        this.liveTexts.clear();
        this.matchCount = 0;
        this.card = null;

        if (!searching()) {
            buildCategory(selected);
        } else {
            for (Category category : Category.values()) {
                if (category.children().isEmpty() && !category.hidden()) buildCategory(category);
            }
            if (this.matchCount == 0) {
                new UIText("No settings match your search", false).setColor(color(Theme.TEXT_MUTED)).setChildOf(this.page);
            }
        }
        this.page.scrollToTop(false);
    }

    private void buildCategory(Category category) {
        this.building = category;
        switch (category) {
            case NOTIFICATIONS -> buildNotifications();
            case WAYPOINTS -> buildWaypoints();
            case KEYBINDS -> buildKeybinds();
            case SAFARI -> { } // a group: its settings are on its sub-categories' pages
            case SAFARI_ALERTS -> buildSafariAlerts();
            case SAFARI_TRACKERS -> buildSafariTrackers();
            case SAFARI_WAYPOINTS -> buildSafariWaypoints();
            case FARMING -> buildFarming();
            case MINING -> { } // a group, like SAFARI
            case MINING_NUCLEUS -> buildMiningNucleus();
            case DUNGEONS -> buildDungeons();
            case LAB -> buildLab();
            case STATS -> buildStats();
            case HUD -> buildHud();
            case THEME -> buildTheme();
            case DEBUG -> buildDebug();
        }
    }

    private void buildNotifications() {
        beginSection("TITLES");

        addButtonRow("Title triggers", "Choose which chat messages show a title on screen",
                () -> SentilsClient.titles.size() + " active", "Configure",
                () -> open(new NotificationConfigScreen(this, SentilsClient.titles)));
    }

    private void buildWaypoints() {
        beginSection("DISPLAY");

        addToggleRow("Render waypoints", "Show waypoints from chat coordinates",
                WaypointSettings.renderWaypoints, value -> {
                    WaypointSettings.renderWaypoints = value;
                    WaypointSettings.save();
                });

        addToggleRow("Render tracer", "Line pointing to the waypoint",
                WaypointSettings.renderTracer, value -> {
                    WaypointSettings.renderTracer = value;
                    WaypointSettings.save();
                });

        addFieldRow("Duration (seconds)",
                "How long a waypoint stays visible, from " + WaypointSettings.MIN_DURATION_SECONDS
                        + " to " + WaypointSettings.MAX_DURATION_SECONDS + " seconds",
                Integer.toString(WaypointSettings.durationSeconds), text -> {
                    int seconds = parseDuration(text);
                    if (seconds >= 0 && seconds != WaypointSettings.durationSeconds) {
                        WaypointSettings.durationSeconds = seconds;
                        WaypointSettings.save();
                    }
                    return seconds >= 0;
                });

        beginSection("CHAT SOURCES");

        addToggleRow("All chat", "Create waypoints from coordinates in all chat",
                WaypointSettings.fromAllChat, value -> {
                    WaypointSettings.fromAllChat = value;
                    WaypointSettings.save();
                });

        addToggleRow("Party chat", "Create waypoints from coordinates in party chat",
                WaypointSettings.fromPartyChat, value -> {
                    WaypointSettings.fromPartyChat = value;
                    WaypointSettings.save();
                });
    }

    /** Returns the duration in seconds, or -1 if the text is not a duration in the allowed range. */
    private static int parseDuration(String text) {
        try {
            int seconds = Integer.parseInt(text.trim());
            if (seconds >= WaypointSettings.MIN_DURATION_SECONDS && seconds <= WaypointSettings.MAX_DURATION_SECONDS) {
                return seconds;
            }
        } catch (NumberFormatException ignored) {
        }
        return -1;
    }

    private void buildKeybinds() {
        beginSection("COMMANDS");

        addButtonRow("Command keybinds", "Bind keys that send a command or chat message",
                () -> KeybindsConfig.bindings.size() + " set", "Configure",
                () -> open(new KeybindConfigScreen(this)));
    }

    private void buildFarming() {
        beginSection("FORTUNE");

        addButtonRow("Farming fortune",
                "See your farming fortune and what is left to upgrade, crop by crop. "
                        + "For another player, use /sentils ff <username>",
                null, "Open", () -> open(FortuneScreen.forPlayer(this, null, null)));
    }

    private void buildMiningNucleus() {
        beginSection("METAL DETECTOR");

        addToggleRow("Metal detector solver",
                "Mark the chest the Metal Detector is pointing at with a waypoint. Works while walking; "
                        + "needs a Keeper to have been in view once per lobby",
                MiningSettings.metalDetectorSolver, value -> {
                    MiningSettings.metalDetectorSolver = value;
                    MiningSettings.save();
                    MetalDetectorSolver.settingsChanged();
                });

        addToggleRow("Tracer to the chest", "Line pointing to the chest the metal detector solver has found",
                MiningSettings.metalDetectorTracer, value -> {
                    MiningSettings.metalDetectorTracer = value;
                    MiningSettings.save();
                    MetalDetectorSolver.settingsChanged();
                });

        beginSection("AUTOMATONS");

        addToggleRow("Highlight Automatons",
                "Give Automatons in the Crystal Hollows a glowing outline while they are in sight. "
                        + "The outline does not show through walls",
                MiningSettings.automatonGlow, value -> {
                    MiningSettings.automatonGlow = value;
                    MiningSettings.save();
                });
    }

    // What the last press of each Efficiency Lab button did, shown beside it.
    private static String labPriceNote = "";
    private static String labDataNote = "";

    private void buildLab() {
        beginSection("SESSIONS");

        addButtonRow("Efficiency Lab",
                "Record what a stretch of play brings in, keep every session, and compare them. Also /lab",
                () -> Lab.active() != null ? "recording" : Lab.store().sessions().size() + " sessions", "Open",
                () -> open(new LabScreen(this)));

        addToggleRow("Session HUD", "Show the running session's time and chosen metric on screen. "
                        + "Recording works the same with it off",
                LabSettings.showHud, value -> {
                    LabSettings.showHud = value;
                    LabSettings.save();
                });

        addToggleRow("Pause on disconnect", "Pause the running session when you leave or lose the server, "
                        + "so time away is not tracked. Resume it with /lab resume",
                LabSettings.pauseOnDisconnect, value -> {
                    LabSettings.pauseOnDisconnect = value;
                    LabSettings.save();
                });

        beginSection("ITEM VALUES");

        // The button says which is chosen, and each press moves on to the next.
        addButtonRow("Value items by", "Which price gained items are valued at. Click to change: Bazaar instant "
                        + "sell (highest buy order), Bazaar sell order (lowest sell offer), NPC sell price, or only "
                        + "prices you set. These are estimates of worth, not coins received",
                null, LabSettings.assumptions.modeType().shortLabel, () -> {
                    Assumptions.Mode[] modes = Assumptions.Mode.values();
                    LabSettings.assumptions.mode = modes[(LabSettings.assumptions.modeType().ordinal() + 1) % modes.length].name();
                    LabSettings.save();
                    refresh();
                });

        // Only offered with a Bazaar way of valuing: an NPC or your own price has no tax to take off.
        if (LabSettings.assumptions.modeType().usesBazaar()) {
            addFieldRow("Bazaar tax (%)", "Taken off Bazaar prices, which are listed before the tax a seller pays. "
                    + "The usual rate is 1.25. Not applied to NPC prices or prices you set",
                    Double.toString(LabSettings.assumptions.bazaarTaxPercent), text -> {
                        double percent = -1;
                        try {
                            percent = Double.parseDouble(text.trim());
                        } catch (NumberFormatException ignored) {
                        }
                        boolean valid = percent >= 0 && percent <= 100;
                        if (valid && percent != LabSettings.assumptions.bazaarTaxPercent) {
                            LabSettings.assumptions.bazaarTaxPercent = percent;
                            LabSettings.save();
                        }
                        return valid;
                    });
        }

        addToggleRow("Compare at latest prices", "Value every session with the newest prices instead of each "
                        + "with the prices from when it ended, so that price changes do not show up as differences",
                LabSettings.latestPrices, value -> {
                    LabSettings.latestPrices = value;
                    LabSettings.save();
                });

        beginSection("PRICE DATA");

        // The only web request the Lab makes: when this button is pressed, and as a session ends if that is left on.
        addToggleRow("Refresh when a session ends", "Fetch the prices as each session ends, so that it is "
                        + "valued at what its items are worth then. Skipped if they were fetched in the last "
                        + "five minutes. With this off, prices are only fetched when you press Refresh",
                LabSettings.refreshPricesOnEnd, value -> {
                    LabSettings.refreshPricesOnEnd = value;
                    LabSettings.save();
                });

        addButtonRow("Prices", "Fetches two public lists from the Hypixel API. " + "This can be done at most once every five minutes",
                () -> labPriceNote.isEmpty() ? Lab.priceSummary() : labPriceNote,
                "Refresh", () -> {
                    labPriceNote = "";
                    Lab.refreshPrices(message -> labPriceNote = message.length() > 34 ? Lab.priceSummary() : message);
                });

        beginSection("YOUR DATA");

        addButtonRow("Export spreadsheets", "Write every session, and every session's items, to two CSV files "
                        + "in the Lab's exports folder on this computer",
                () -> labDataNote, "CSV", () -> labDataNote = shortNote(Lab.exportCsv()));

        addButtonRow("Export everything", "Write every session complete, with its observations and prices, "
                        + "and your setups and experiments, to one JSON file. This is the file to back up or import",
                null, "JSON", () -> labDataNote = shortNote(Lab.exportJson()));

        addButtonRow("Import", "Read every exported JSON file found in the Lab's import folder. Sessions "
                        + "already here are left as they are",
                null, "Import", () -> labDataNote = shortNote(Lab.importAll()));

        addButtonRow("Data folder", "Everything the Lab records is kept in this folder and nowhere else. "
                        + "Copy it to back it up; delete files from it to remove them",
                null, "Open", () -> Util.getPlatform().openPath(Lab.store().dir()));
    }

    /** The start of a message, short enough to sit beside a row's label. */
    private static String shortNote(String message) {
        int cut = message.indexOf(" to ");
        String note = cut > 0 ? message.substring(0, cut) : message;
        return note.length() > 30 ? note.substring(0, 28) + "..." : note;
    }

    private void buildDungeons() {
        beginSection("SUPERBOOM WALLS");

        addToggleRow("Highlight cracked stone bricks",
                "Color the cracked stone bricks in dungeons blue.",
                DungeonSettings.highlightCrackedBricks, value -> {
                    DungeonSettings.highlightCrackedBricks = value;
                    DungeonSettings.save();
                });
    }

    private void buildSafariAlerts() {
        beginSection("SPARKLING CREATURES");

        addToggleRow("Sparkling creature alert",
                "Show a title when a sparkling creature comes into view on the Critter Safari",
                SafariSettings.sparklingAlert, value -> {
                    SafariSettings.sparklingAlert = value;
                    SafariSettings.save();
                });

        beginSection("PARTY");

        addToggleRow("Announce hotspot biome",
                "Send \"My biome is ...\" to party chat when the game tells you your Hunting Hotspot. Only while in a party",
                SafariSettings.announceHotspot, value -> {
                    SafariSettings.announceHotspot = value;
                    SafariSettings.save();
                });
    }

    private void buildSafariTrackers() {
        beginSection("SHARDS");

        addToggleRow("Shard tracker",
                "List how many of each shard has been caught in the Safari, by you or anyone else.",
                SafariSettings.shardTracker, value -> {
                    SafariSettings.shardTracker = value;
                    SafariSettings.save();
                });

        addToggleRow("Only show in the Safari",
                "Show the shard tracker only while on the Safari island.",
                SafariSettings.shardTrackerSafariOnly, value -> {
                    SafariSettings.shardTrackerSafariOnly = value;
                    SafariSettings.save();
                });
    }

    private void buildSafariWaypoints() {
        beginSection("BELLS");

        // Not a saved setting: the waypoints are shown by hand and go when the Safari is left.
        addButtonRow("Bell waypoints",
                "Mark all seven bells in the Safari with waypoints until you leave it. Only works on the Safari island",
                () -> BellWaypoints.isShown() ? "shown" : BellWaypoints.isInSafari() ? "" : "Safari only",
                BellWaypoints.isShown() ? "Hide" : "Show", () -> {
                    BellWaypoints.toggle();
                    refresh(); // the button now says the opposite
                });
    }

    private void buildStats() {
        beginSection("COLD");

        addToggleRow("Cold timer", "In cold areas, show your cold and an estimate of how long until it reaches 100",
                ColdSettings.coldTimer, value -> {
                    ColdSettings.coldTimer = value;
                    ColdSettings.save();
                });

        addToggleRow("Hide frost overlay", "Hide the frost that covers the edges of the screen as your cold rises",
                ColdSettings.hideFrostOverlay, value -> {
                    ColdSettings.hideFrostOverlay = value;
                    ColdSettings.save();
                });
    }

    private void buildHud() {
        beginSection("LAYOUT");

        addButtonRow("Move and resize HUD", "Drag HUD elements to move them and scroll to resize them",
                null, "Edit", () -> open(new HudEditScreen(this)));
    }

    private void buildTheme() {
        beginSection("COLORS");

        addColorRow("Accent", "Color of highlights, buttons and switches",
                () -> ThemeSettings.accent, value -> ThemeSettings.accent = value);
        addColorRow("Background", "Color of menu panels and HUD backdrops",
                () -> ThemeSettings.background, value -> ThemeSettings.background = value);

        beginSection("DEFAULTS");

        addButtonRow("Default colors", "Put both colors back to how they started", null, "Reset", () -> {
            ThemeSettings.reset();
            ThemeSettings.save();
        });
    }

    private void buildDebug() {
        beginSection("HUD");

        addToggleRow("Show SkyBlock item info",
                "In an inventory, show the rarity, reforge, stars and other SkyBlock details of the item under the mouse",
                DebugSettings.showItemData, value -> DebugSettings.showItemData = value);

        addToggleRow("Show detected island", "Show the island Sentils thinks you are on in the top-left corner",
                DebugSettings.showLocationHud, value -> DebugSettings.showLocationHud = value);

        addToggleRow("Show sack changes",
                "Show the items listed in the last [Sacks] chat message, which chat only shows when the message is hovered over",
                DebugSettings.showSackChanges, value -> DebugSettings.showSackChanges = value);
    }


    /** Starts a titled card that the following rows go into. */
    private void beginSection(String name) {
        this.sectionName = name;
        this.card = null;
    }

    /** Adds a row with an on/off switch. Clicking anywhere on the row flips it. */
    private void addToggleRow(String label, String description, boolean value, Consumer<Boolean> onChange) {
        UIComponent row = addRow(label, description, null, 30);
        if (row == null) return;
        boolean[] on = {value};
        UIComponent pill = new UIRoundedRectangle(8f).setX(pxEnd(10)).setY(new CenterConstraint()).setWidth(px(30))
                .setHeight(px(16)).setColor(color(value ? Theme.ACCENT : Theme.CONTROL_BORDER)).setChildOf(row);
        UIComponent knob = new UIRoundedRectangle(6f).setX(px(value ? 16 : 2)).setY(new CenterConstraint())
                .setWidth(px(12)).setHeight(px(12)).setColor(color(Theme.KNOB)).setChildOf(pill);
        row.onMouseClickConsumer(event -> {
            on[0] = !on[0];
            click();
            fade(pill, color(on[0] ? Theme.ACCENT : Theme.CONTROL_BORDER));
            AnimatingConstraints slide = knob.makeAnimation();
            slide.setXAnimation(Animations.OUT_CUBIC, 0.16f, px(on[0] ? 16 : 2));
            knob.animateTo(slide);
            onChange.accept(on[0]);
        });
    }

    /** Adds a row with a button. */
    private void addButtonRow(String label, String description, Supplier<String> detail, String buttonText,
                              Runnable action) {
        int width = Math.max(BUTTON_MIN_W, this.font.width(buttonText) + 18);
        UIComponent row = addRow(label, description, detail, width);
        if (row == null) return;
        UIComponent button = new UIRoundedRectangle(5f).setX(pxEnd(10)).setY(new CenterConstraint()).setWidth(px(width))
                .setHeight(px(CONTROL_H)).setColor(color(Theme.CONTROL)).setChildOf(row);
        new UIText(buttonText, false).setX(new CenterConstraint()).setY(new CenterConstraint())
                .setColor(color(Theme.TEXT)).setChildOf(button);
        hover(button, color(Theme.CONTROL), color(Theme.CONTROL_HOVER));
        E.onClick(button, action);
    }

    /**
     * Adds a row with a short box to type in. accept is given the text each time it changes, and
     * says whether it is something the setting can take, having taken it if so; the box is
     * marked while it holds something it cannot.
     */
    private void addFieldRow(String label, String description, String value, Predicate<String> accept) {
        UIComponent row = addRow(label, description, null, FIELD_W);
        if (row == null) return;
        UIComponent box = new UIRoundedRectangle(5f).setX(pxEnd(10)).setY(new CenterConstraint()).setWidth(px(FIELD_W))
                .setHeight(px(CONTROL_H)).setColor(color(Theme.FIELD)).setChildOf(row);
        UITextInput input = new UITextInput("", false, withAlpha(Theme.ACCENT, 120));
        input.setX(px(6)).setY(new CenterConstraint()).setWidth(px(FIELD_W - 12)).setHeight(px(9))
                .setColor(color(Theme.TEXT)).setChildOf(box);
        input.setText(value);
        input.onUpdate(text -> {
            fade(box, accept.test(text) ? color(Theme.FIELD) : color(Theme.DANGER_FILL));
            return Unit.INSTANCE;
        });
        box.onMouseClickConsumer(event -> input.grabWindowFocus());
    }

    /** Adds a row showing a theme color, with a swatch that opens the color picker for it. */
    private void addColorRow(String label, String description, IntSupplier getter, IntConsumer setter) {
        UIComponent row = addRow(label, description, () -> Theme.hex(getter.getAsInt()), FIELD_W);
        if (row == null) return;
        Component pickerTitle = Component.literal(label + " color");
        // A hairline round the swatch, so that a color close to the card's own can still be seen.
        UIComponent edge = new UIRoundedRectangle(5f).setX(pxEnd(10)).setY(new CenterConstraint()).setWidth(px(FIELD_W))
                .setHeight(px(CONTROL_H)).setColor(color(Theme.CONTROL_BORDER)).setChildOf(row);
        new UIRoundedRectangle(4f).setX(px(1)).setY(px(1)).setWidth(px(FIELD_W - 2)).setHeight(px(CONTROL_H - 2))
                .setColor(color(getter.getAsInt() | 0xFF000000)).setChildOf(edge);
        hover(edge, color(Theme.CONTROL_BORDER), color(Theme.ACCENT));
        E.onClick(edge, () -> open(new ColorPickerScreen(this, pickerTitle, "Theme", getter.getAsInt(), picked -> {
            setter.accept(picked);
            ThemeSettings.apply();
        }, ThemeSettings::save)));
    }

    /**
     * Adds a labelled row to the current section and returns it for its control to be put in, at
     * the right; null if a search is on and the row does not match. The description is written
     * under the label, and is what the search looks through along with it. The detail is a muted
     * note after the label that is kept up to date, or null for none. controlWidth is how much
     * room the control takes, which the text is kept clear of.
     */
    private UIComponent addRow(String label, String description, Supplier<String> detail, int controlWidth) {
        if (searching() && !matches(label, description)) return null;
        this.matchCount++;

        if (this.card == null) {
            // Search results mix categories, so each card says which one it came from.
            String title = searching()
                    ? this.building.path().toUpperCase(Locale.ROOT) + " / " + this.sectionName
                    : this.sectionName;
            new UIText(title, false).setX(px(2)).setY(new SiblingConstraint(SECTION_GAP))
                    .setColor(color(Theme.TEXT_MUTED)).setChildOf(this.page);
            this.card = new UIRoundedRectangle(7f).setY(new SiblingConstraint(5f)).setWidth(new RelativeConstraint(1f))
                    .setHeight(new ChildBasedSizeConstraint()).setColor(color(Theme.CARD)).setChildOf(this.page);
        } else {
            UIComponent line = new UIContainer().setY(new SiblingConstraint()).setWidth(new RelativeConstraint(1f))
                    .setHeight(px(1)).setChildOf(this.card);
            new UIBlock(color(Theme.DIVIDER)).setX(px(10)).setWidth(new SubtractiveConstraint(new RelativeConstraint(1f), px(20)))
                    .setHeight(px(1)).setChildOf(line);
        }

        // The row is as tall as its text needs, and lights up under the mouse.
        UIComponent row = new UIRoundedRectangle(6f).setY(new SiblingConstraint()).setWidth(new RelativeConstraint(1f))
                .setHeight(new AdditiveConstraint(new ChildBasedMaxSizeConstraint(), px(ROW_PAD))).setColor(CLEAR)
                .setChildOf(this.card);
        hover(row, CLEAR, withAlpha(Theme.CONTROL, 150));

        UIComponent text = new UIContainer().setX(px(11)).setY(new CenterConstraint())
                .setWidth(new SubtractiveConstraint(new RelativeConstraint(1f), px(controlWidth + 36)))
                .setHeight(new ChildBasedSizeConstraint(3f)).setChildOf(row);
        UIComponent heading = new UIContainer().setWidth(new RelativeConstraint(1f)).setHeight(px(9)).setChildOf(text);
        new UIText(label, false).setColor(color(Theme.TEXT)).setChildOf(heading);
        if (detail != null) {
            UIText note = new UIText("", false);
            note.setX(new SiblingConstraint(7f)).setColor(color(Theme.ACCENT)).setChildOf(heading);
            Runnable update = () -> {
                String now = detail.get();
                if (now != null && !now.equals(note.getText())) note.setText(now);
            };
            update.run();
            this.liveTexts.add(update);
        }
        if (description != null && !description.isEmpty()) {
            new UIWrappedText(description, false).setY(new SiblingConstraint(3f)).setWidth(new RelativeConstraint(1f))
                    .setColor(color(Theme.TEXT_MUTED)).setChildOf(text);
        }
        return row;
    }
}
