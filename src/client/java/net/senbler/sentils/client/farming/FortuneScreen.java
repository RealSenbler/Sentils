package net.senbler.sentils.client.farming;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.senbler.sentils.client.Log;
import net.senbler.sentils.client.SentilsClient;
import net.senbler.sentils.client.farming.FortuneData.Progress;
import net.senbler.sentils.client.farming.FortuneData.Row;
import net.senbler.sentils.client.farming.FortuneData.StatProgress;
import net.senbler.sentils.client.farming.FortuneData.Upgrade;
import net.senbler.sentils.client.farming.FortuneData.View;
import net.senbler.sentils.client.farming.HypixelApi.ApiException;
import net.senbler.sentils.client.gui.Theme;
import net.senbler.sentils.client.gui.widget.FlatButton;
import net.senbler.sentils.client.gui.widget.ItemButton;

/**
 * A player's farming fortune, crop by crop: what eliteskyblock.com shows on its fortune page.
 * The screen opens straight away and fills in once the player's profile has been fetched.
 */
public class FortuneScreen extends Screen {

    private static final int PANEL_W = 420;
    private static final int PANEL_H = 300;
    private static final int PAD = 12;
    private static final int HEADER_H = 38;
    private static final int CROP_SIZE = 22;
    private static final int TAB_H = 16;
    private static final int LINE_H = 11;
    private static final int INDENT = 8;
    private static final int SCROLL_LINES = 3;

    private static final String FORTUNE_ICON = " ☘";

    /** The pages of the screen, in the order of their buttons. */
    private enum Tab {
        OVERVIEW("Overview"),
        FORTUNE("Fortune"),
        RATES("Rates"),
        UPGRADES("Upgrades");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private enum Kind { HEADING, TEXT, NOTE, WARNING, BAR, GAP }

    /** One line of the page: text at the left and right, or a progress bar. */
    private record Line(Kind kind, String left, String right, int indent, double ratio, boolean active) {}

    // Remembered so looking up another player lands on the same page and crop.
    private static Tab tab = Tab.OVERVIEW;
    private static String cropId = "WHEAT";

    private final Screen parent; // may be null (closes to the game)
    private final String requestedName;
    private final String requestedUuid; // null to look the name up
    private String requestedProfile; // null for the profile last played on

    private FortuneLookup.Profile profile; // null until fetched
    private final Map<String, View> views = new HashMap<>();
    private String error; // shown in place of the page
    private boolean closed;

    private final List<Line> lines = new ArrayList<>();
    private int scroll; // index of the first visible line

    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private int listTop;
    private int visibleLines;

    /**
     * The screen for a player by name, or for the player running the game if the name is null
     * or their own. The profile may be null for the one the player last played on, and the
     * parent null to close to the game.
     */
    public static FortuneScreen forPlayer(Screen parent, String name, String profileName) {
        var self = Minecraft.getInstance().getUser();
        if (name == null || name.equalsIgnoreCase(self.getName())) {
            // Their own id is already known, which saves looking the name up.
            return new FortuneScreen(parent, self.getName(), self.getProfileId().toString(), profileName);
        }
        return new FortuneScreen(parent, name, null, profileName);
    }

    private FortuneScreen(Screen parent, String name, String uuid, String profileName) {
        super(Component.literal("Farming Fortune"));
        this.parent = parent;
        this.requestedName = name;
        this.requestedUuid = uuid;
        this.requestedProfile = profileName;
        loadProfile();
    }

    private void loadProfile() {
        this.profile = null;
        this.error = null;
        this.views.clear();
        FortuneLookup.load(this.requestedName, this.requestedUuid, this.requestedProfile, SentilsClient.developmentApiKey)
                .whenComplete((loaded, failure) -> onClientThread(() -> {
                    if (failure != null) {
                        this.error = describe(failure);
                    } else {
                        this.profile = loaded;
                        loadView(cropId);
                    }
                    refresh();
                }));
    }

    private void loadView(String crop) {
        FortuneLookup.Profile current = this.profile;
        if (current == null || this.views.containsKey(crop)) return;
        FortuneLookup.view(current, crop).whenComplete((view, failure) -> onClientThread(() -> {
            if (this.profile != current) return; // another profile was picked meanwhile
            if (failure != null) {
                this.error = describe(failure);
            } else {
                this.views.put(crop, view);
            }
            refresh();
        }));
    }

    /** Results arrive on the fortune thread; the screen may only be touched from the game's. */
    private void onClientThread(Runnable task) {
        Minecraft.getInstance().execute(() -> {
            if (!this.closed) task.run();
        });
    }

    private static String describe(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof ApiException) return cause.getMessage();
        Log.warn("Fortune lookup failed: " + cause);
        return "Something went wrong: " + cause;
    }

    /** Rebuilds the widgets if the screen is the one showing; it may have been left already. */
    private void refresh() {
        if (Minecraft.getInstance().gui.screen() == this) this.rebuildWidgets();
    }

    @Override
    protected void init() {
        this.panelW = Math.min(PANEL_W, this.width - 16);
        this.panelH = Math.min(PANEL_H, this.height - 8);
        this.left = (this.width - this.panelW) / 2;
        this.top = (this.height - this.panelH) / 2;

        int footerY = this.top + this.panelH - PAD - 18;
        int y = this.top + HEADER_H + 6;

        if (this.profile != null) {
            // Crops, as a row of their items
            List<FortuneData.CropName> crops = this.profile.summary().crops;
            int gap = Math.max(1, Math.min(4, (this.panelW - 2 * PAD - crops.size() * CROP_SIZE) / Math.max(1, crops.size() - 1)));
            int x = this.left + (this.panelW - crops.size() * CROP_SIZE - (crops.size() - 1) * gap) / 2;
            for (FortuneData.CropName crop : crops) {
                this.addRenderableWidget(new ItemButton(x, y, CROP_SIZE, new ItemStack(cropItem(crop.id)),
                        Component.literal(crop.name), crop.id.equals(cropId), () -> selectCrop(crop.id)));
                x += CROP_SIZE + gap;
            }
            y += CROP_SIZE + 6;

            // Pages
            int tabW = (this.panelW - 2 * PAD - (Tab.values().length - 1) * 4) / Tab.values().length;
            x = this.left + PAD;
            for (Tab entry : Tab.values()) {
                FlatButton button = new FlatButton(x, y, tabW, TAB_H, Component.literal(entry.label),
                        FlatButton.Style.SECONDARY, () -> selectTab(entry));
                button.setHighlighted(entry == tab);
                this.addRenderableWidget(button);
                x += tabW + 4;
            }
            y += TAB_H + 6;

            // Profile switcher, for players with more than one
            List<String> names = this.profile.profileNames();
            if (names.size() > 1) {
                String label = "Profile: " + this.profile.profileName();
                this.addRenderableWidget(new FlatButton(this.left + PAD, footerY, this.font.width(label) + 16, 18,
                        Component.literal(label), FlatButton.Style.SECONDARY, this::nextProfile));
            }
        }

        this.addRenderableWidget(new FlatButton(this.left + this.panelW - PAD - 64, footerY, 64, 18,
                Component.literal("Done"), FlatButton.Style.PRIMARY, this::onClose));

        this.listTop = y;
        this.visibleLines = Math.max(1, (footerY - 6 - this.listTop) / LINE_H);
        buildLines();
    }

    private void selectCrop(String crop) {
        cropId = crop;
        this.scroll = 0;
        loadView(crop);
        this.rebuildWidgets();
    }

    private void selectTab(Tab selected) {
        tab = selected;
        this.scroll = 0;
        this.rebuildWidgets();
    }

    private void nextProfile() {
        List<String> names = this.profile.profileNames();
        int index = names.indexOf(this.profile.profileName());
        this.requestedProfile = names.get((index + 1) % names.size());
        this.scroll = 0;
        loadProfile();
        this.rebuildWidgets();
    }

    /** The vanilla item that stands for a crop. */
    private static Item cropItem(String crop) {
        return switch (crop) {
            case "CARROT_ITEM" -> Items.CARROT;
            case "POTATO_ITEM" -> Items.POTATO;
            case "PUMPKIN" -> Items.PUMPKIN;
            case "MELON" -> Items.MELON_SLICE;
            case "MUSHROOM_COLLECTION" -> Items.RED_MUSHROOM;
            case "CACTUS" -> Items.CACTUS;
            case "SUGAR_CANE" -> Items.SUGAR_CANE;
            case "NETHER_STALK" -> Items.NETHER_WART;
            case "INK_SACK:3" -> Items.COCOA_BEANS;
            case "DOUBLE_PLANT" -> Items.SUNFLOWER;
            case "MOONFLOWER" -> Items.BLUE_ORCHID;
            case "WILD_ROSE" -> Items.ROSE_BUSH;
            default -> Items.WHEAT;
        };
    }

    // ---- Page contents ----

    private void buildLines() {
        this.lines.clear();
        View view = this.views.get(cropId);
        if (this.profile == null || view == null) return;

        FortuneData.Summary summary = this.profile.summary();
        if (!summary.hasInventory) {
            warning("Inventory API is off: tools, armor and equipment are missing");
        }
        if (!summary.hasSkills) warning("Skills API is off: farming level is counted as 0");
        if (!summary.hasGarden) warning("No garden data: plots, milestones and crop upgrades are missing");
        if (!this.lines.isEmpty()) gap();

        switch (tab) {
            case OVERVIEW -> buildOverview(view);
            case FORTUNE -> buildFortune(view);
            case RATES -> buildRates(view);
            case UPGRADES -> buildUpgrades(view);
        }
        this.scroll = Math.clamp(this.scroll, 0, Math.max(0, this.lines.size() - this.visibleLines));
    }

    private void buildOverview(View view) {
        heading("STATS", "");
        text("Farming Level", number(view.farmingLevel), 0);
        text("Garden Level", number(view.gardenLevel), 0);
        text("Unlocked Plots", number(view.plots), 0);
        text(view.cropName + " Milestone", number(view.milestone), 0);
        text(view.cropName + " Crop Upgrade", number(view.cropUpgrade), 0);

        gap();
        heading("EQUIPPED", "");
        text(view.tool != null ? view.tool.name : "No " + view.cropName.toLowerCase(Locale.ROOT) + " tool",
                view.tool != null ? fortune(view.tool.fortune) : "", 0);
        text(view.pet != null ? view.pet.rarity + " " + view.pet.name + " (Lvl " + view.pet.level + ")" : "No farming pet",
                view.pet != null ? fortune(view.pet.fortune) : "", 0);
        for (FortuneData.Gear piece : view.gear) {
            text(piece.name, fortune(piece.fortune), 0);
        }

        gap();
        heading("TOTAL FARMING FORTUNE", fortune(view.fortune));
        for (Row row : view.breakdown) {
            text(row.name, number(row.value), 0);
        }

        if (!view.cropBreakdown.isEmpty()) {
            double total = 0;
            for (Row row : view.cropBreakdown) total += row.value;
            gap();
            heading(view.cropStatName.toUpperCase(Locale.ROOT) + " ONLY", number(total));
            for (Row row : view.cropBreakdown) {
                text(row.name, number(row.value), 0);
            }
        }
    }

    private void buildFortune(View view) {
        progressSection(view.cropName.toUpperCase(Locale.ROOT) + " FORTUNE", view.cropProgress);
        progressSection("GEAR FORTUNE", view.gearProgress);
        progressSection("GENERAL FORTUNE", view.generalProgress);
        progressSection("PETS", view.petProgress);
    }

    private void progressSection(String name, List<Progress> sources) {
        if (sources.isEmpty()) return;
        if (!this.lines.isEmpty()) gap();
        heading(name, "");
        for (Progress source : sources) {
            progress(source, 0);
        }
    }

    private void progress(Progress source, int indent) {
        // An item's own name heads its entry; the parts beneath it go by what they are ("Reforge Stats").
        String name = indent == 0 ? source.name : source.source;
        if (source.stats.isEmpty()) {
            // Not a stat but a plain count, such as a garden chip's level.
            boolean counted = source.max > 0;
            text(name, counted ? number(source.current) + " / " + number(source.max) : "", indent);
            if (counted) this.lines.add(new Line(Kind.BAR, "", "", indent, source.ratio, source.active));
        }
        for (int i = 0; i < source.stats.size(); i++) {
            StatProgress stat = source.stats.get(i);
            String amount = number(stat.current) + " / " + number(stat.max);
            // The first bar is the source's own; any more are its other stats, named beneath it.
            if (i == 0) {
                boolean fortune = stat.name.equals("Farming Fortune");
                text(name, fortune ? amount + FORTUNE_ICON : amount + " " + stat.name, indent);
            } else {
                note(stat.name + ": " + amount, indent + 1);
            }
            this.lines.add(new Line(Kind.BAR, "", "", indent, stat.ratio, source.active));
        }
        if (!source.api) note("Not in Hypixel's API, so this is counted as 0", indent + 1);
        if (!source.note.isEmpty()) note(source.note, indent + 1);
        for (Progress child : source.children) {
            progress(child, indent + 1);
        }
    }

    private void buildRates(View view) {
        FortuneData.Rates rates = view.rates;
        heading("ONE HOUR OF " + view.cropName.toUpperCase(Locale.ROOT) + " AT " + number(rates.blocksPerSecond) + " BLOCKS A SECOND", "");

        gap();
        heading("NPC PROFIT", number(rates.npcCoins) + " coins");
        for (Row row : rates.coinSources) {
            text(row.name.equals("Collection") ? view.cropName : row.name, number(row.value), 0);
        }

        gap();
        heading("COLLECTION GAIN", number(rates.collection));
        for (Row row : rates.otherCollection) {
            text(row.name.equals("Normal") ? view.cropName : row.name, number(row.value), 0);
        }

        gap();
        heading("FARMING WEIGHT GAIN", number(rates.weight));

        if (!rates.effects.isEmpty()) {
            gap();
            heading("DROP EFFECTS", "");
            for (FortuneData.Effect effect : rates.effects) {
                text(effect.source, effect.value, 0);
                if (!effect.detail.isEmpty()) note(effect.detail, 1);
            }
        }
    }

    private void buildUpgrades(View view) {
        heading("UPGRADES, BIGGEST FIRST", number(view.upgrades.size()));
        if (view.upgrades.isEmpty()) note("Nothing left to upgrade", 0);
        for (Upgrade upgrade : view.upgrades) {
            StringBuilder gain = new StringBuilder();
            for (FortuneData.StatAmount stat : upgrade.stats) {
                if (!gain.isEmpty()) gain.append(", ");
                gain.append(stat.amount > 0 ? "+" : "").append(number(stat.amount));
                gain.append(stat.name.equals("Farming Fortune") ? FORTUNE_ICON : " " + stat.name);
            }
            // Some titles already say what they go on ("Recombobulate Fermento Boots").
            boolean named = upgrade.onto.isEmpty() || upgrade.title.contains(upgrade.onto);
            String title = named ? upgrade.title : upgrade.title + " on " + upgrade.onto;
            text(title, gain.toString(), 0);

            String detail = upgrade.cost;
            if (upgrade.repeatable > 1) detail = (detail.isEmpty() ? "" : detail + ", ") + "up to " + upgrade.repeatable + " times";
            if (!upgrade.api) detail = (detail.isEmpty() ? "" : detail + ", ") + "not in Hypixel's API";
            if (!detail.isEmpty()) note(detail, 1);
        }
    }

    private void heading(String left, String right) {
        this.lines.add(new Line(Kind.HEADING, left, right, 0, 0, true));
    }

    private void text(String left, String right, int indent) {
        this.lines.add(new Line(Kind.TEXT, left, right, indent, 0, true));
    }

    private void note(String text, int indent) {
        this.lines.add(new Line(Kind.NOTE, text, "", indent, 0, true));
    }

    private void warning(String text) {
        this.lines.add(new Line(Kind.WARNING, text, "", 0, 0, true));
    }

    private void gap() {
        this.lines.add(new Line(Kind.GAP, "", "", 0, 0, true));
    }

    /** Formats an amount with thousands separators and up to two decimals. */
    private static String number(double value) {
        if (value == Math.rint(value)) return String.format(Locale.ROOT, "%,d", (long) value);
        String text = String.format(Locale.ROOT, "%,.2f", value);
        return text.endsWith("0") ? text.substring(0, text.length() - 1) : text;
    }

    private static String fortune(double value) {
        return number(value) + FORTUNE_ICON;
    }

    // ---- Input and drawing ----

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        if (scrollY == 0) return false;
        this.scroll = Math.clamp(this.scroll - (int) Math.signum(scrollY) * SCROLL_LINES,
                0, Math.max(0, this.lines.size() - this.visibleLines));
        return true;
    }

    @Override
    public void onClose() {
        this.closed = true;
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Theme.roundRect(graphics, this.left, this.top, this.panelW, this.panelH, Theme.PANEL, Theme.PANEL_BORDER);

        // Header
        View view = this.views.get(cropId);
        String subtitle = this.profile != null
                ? this.profile.playerName() + " on " + this.profile.profileName()
                : this.requestedName;
        graphics.fill(this.left + PAD, this.top + 11, this.left + PAD + 2, this.top + 29, Theme.ACCENT);
        graphics.text(this.font, this.title.getString(), this.left + PAD + 8, this.top + 11, Theme.TEXT, false);
        graphics.text(this.font, subtitle, this.left + PAD + 8, this.top + 21, Theme.TEXT_MUTED, false);
        if (view != null && this.error == null) {
            String total = fortune(view.fortune);
            int right = this.left + this.panelW - PAD;
            graphics.text(this.font, total, right - this.font.width(total), this.top + 11, Theme.ACCENT, false);
            graphics.text(this.font, view.cropName, right - this.font.width(view.cropName), this.top + 21, Theme.TEXT_MUTED, false);
        }
        graphics.fill(this.left + 1, this.top + HEADER_H, this.left + this.panelW - 1, this.top + HEADER_H + 1, Theme.DIVIDER);

        // A message in place of the page while there is nothing to show
        String message = this.error != null ? this.error
                : this.profile == null ? "Looking up " + this.requestedName + "..."
                : view == null ? "Working out the fortune..."
                : null;
        if (message != null) {
            int maxW = this.panelW - 2 * PAD;
            int y = this.listTop + 12;
            for (String part : wrap(message, maxW)) {
                graphics.centeredText(this.font, part, this.left + this.panelW / 2, y,
                        this.error != null ? Theme.DANGER : Theme.TEXT_MUTED);
                y += LINE_H;
            }
        } else {
            extractLines(graphics);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void extractLines(GuiGraphicsExtractor graphics) {
        boolean scrolls = this.lines.size() > this.visibleLines;
        int lineLeft = this.left + PAD;
        int lineRight = this.left + this.panelW - PAD - (scrolls ? 6 : 0);

        for (int slot = 0; slot < this.visibleLines && this.scroll + slot < this.lines.size(); slot++) {
            Line line = this.lines.get(this.scroll + slot);
            int x = lineLeft + line.indent() * INDENT;
            int y = this.listTop + slot * LINE_H;
            switch (line.kind()) {
                case HEADING -> {
                    int rightW = this.font.width(line.right());
                    graphics.text(this.font, fit(line.left(), lineRight - x - rightW - 6), x, y + 1, Theme.TEXT_MUTED, false);
                    graphics.text(this.font, line.right(), lineRight - rightW, y + 1, Theme.ACCENT, false);
                    graphics.fill(lineLeft, y + LINE_H - 1, lineRight, y + LINE_H, Theme.DIVIDER);
                }
                case TEXT -> {
                    int rightW = this.font.width(line.right());
                    graphics.text(this.font, fit(line.left(), lineRight - x - rightW - 6), x, y + 1, Theme.TEXT, false);
                    graphics.text(this.font, line.right(), lineRight - rightW, y + 1, Theme.TEXT, false);
                }
                case NOTE -> graphics.text(this.font, fit(line.left(), lineRight - x), x, y + 1, Theme.TEXT_MUTED, false);
                case WARNING -> graphics.text(this.font, fit(line.left(), lineRight - x), x, y + 1, Theme.DANGER, false);
                case BAR -> {
                    int width = lineRight - x;
                    int filled = (int) Math.round(width * Math.clamp(line.ratio(), 0, 1));
                    graphics.fill(x, y + 2, lineRight, y + 5, Theme.FIELD);
                    graphics.fill(x, y + 2, x + filled, y + 5, line.active() ? Theme.ACCENT : Theme.TEXT_MUTED);
                }
                case GAP -> { }
            }
        }

        if (scrolls) {
            int trackX = this.left + this.panelW - PAD - 2;
            int trackH = this.visibleLines * LINE_H;
            int thumbH = Math.max(8, trackH * this.visibleLines / this.lines.size());
            int thumbY = this.listTop + (trackH - thumbH) * this.scroll / (this.lines.size() - this.visibleLines);
            graphics.fill(trackX, this.listTop, trackX + 2, this.listTop + trackH, Theme.DIVIDER);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, Theme.TEXT_MUTED);
        }
    }

    /** Cuts text short with "..." if it is wider than the space it has. */
    private String fit(String text, int maxW) {
        if (this.font.width(text) <= maxW) return text;
        return this.font.plainSubstrByWidth(text, Math.max(0, maxW - this.font.width("..."))) + "...";
    }

    /** Breaks a message into lines no wider than the panel, at spaces. */
    private List<String> wrap(String text, int maxW) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            if (!current.isEmpty() && this.font.width(current + " " + word) > maxW) {
                parts.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) current.append(' ');
            current.append(word);
        }
        if (!current.isEmpty()) parts.add(current.toString());
        return parts;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
