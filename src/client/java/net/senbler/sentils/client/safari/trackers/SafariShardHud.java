package net.senbler.sentils.client.safari.trackers;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import net.senbler.sentils.client.gui.Theme;
import net.senbler.sentils.client.hud.HudWidget;
import net.senbler.sentils.client.safari.SafariSettings;

/**
 * Lists how many of each shard has been caught in the Critter Safari. A button in the top row,
 * which can be clicked while an inventory is open, switches between this visit's shards and
 * those of every visit. Only the most caught are listed; hovering over the list in an
 * inventory shows all of them.
 */
public final class SafariShardHud extends HudWidget {

    private static final int PAD = 4;
    private static final int LINE_H = 10;
    private static final int TITLE_H = 12; // the top row is taller, to fit the button
    private static final int MAX_SHOWN = 10; // shards listed without hovering
    private static final int ICON_SIZE = 8; // shard icons are drawn at half an item's size
    private static final int ICON_WIDTH = ICON_SIZE + 2; // an icon and the gap before the shard's name
    private static final int NAME_GAP = 8; // between a shard's name and its count
    private static final int COLUMN_GAP = 12; // between the columns of the full list
    private static final int BUTTON_GAP = 6; // between the title and the button
    private static final int BUTTON_PAD = 3;
    private static final String TITLE = "Shards: ";
    private static final String VISIT_LABEL = "This visit";
    private static final String ALL_TIME_LABEL = "All time";
    private static final String EMPTY = "None caught yet";

    public SafariShardHud() {
        super("safari_shards", "Safari shards", 0.008f, 0.2f);
    }

    @Override
    public boolean isSwitchedOn() {
        return SafariSettings.shardTracker;
    }

    @Override
    public boolean isEnabled() {
        return isSwitchedOn() && (!SafariSettings.shardTrackerSafariOnly || SafariShardTracker.isInSafari());
    }

    // Drawn over inventories so that it can be hovered and clicked there; elsewhere it stays on the HUD.
    @Override
    public boolean drawnOver(Screen screen) {
        return screen instanceof AbstractContainerScreen<?>;
    }

    /** The shards listed without hovering: the most caught ones. */
    private static List<SafariShardTracker.Entry> shown() {
        List<SafariShardTracker.Entry> entries = SafariShardTracker.entries();
        return entries.size() > MAX_SHOWN ? entries.subList(0, MAX_SHOWN) : entries;
    }

    /** The line standing in for the shards that are not listed, or null if they all are. */
    private static String moreLine() {
        int hidden = SafariShardTracker.entries().size() - MAX_SHOWN;
        return hidden > 0 ? "+" + hidden + " more" : null;
    }

    private static String title() {
        return TITLE + SafariShardTracker.total();
    }

    /** What the button says: which shards are being counted. */
    private static String buttonLabel() {
        return SafariSettings.shardTrackerAllTime ? ALL_TIME_LABEL : VISIT_LABEL;
    }

    private static int titleRowWidth(Font font) {
        return font.width(title()) + BUTTON_GAP + font.width(buttonLabel()) + BUTTON_PAD * 2;
    }

    private static String count(SafariShardTracker.Entry entry) {
        return "x" + entry.count();
    }

    /** The width a column needs to fit every one of the entries. */
    private static int columnWidth(Font font, List<SafariShardTracker.Entry> entries) {
        int width = 0;
        for (SafariShardTracker.Entry entry : entries) {
            width = Math.max(width, font.width(entry.shard()) + NAME_GAP + font.width(count(entry)));
        }
        return ICON_WIDTH + width;
    }

    /** The height of a panel with the top row and the given number of lines under it. */
    private static int panelHeight(int lines) {
        return PAD + TITLE_H + lines * LINE_H - (LINE_H - 8) + PAD;
    }

    @Override
    public int width(Font font) {
        List<SafariShardTracker.Entry> shown = shown();
        int width = Math.max(titleRowWidth(font), columnWidth(font, shown));
        if (shown.isEmpty()) width = Math.max(width, font.width(EMPTY));
        String more = moreLine();
        if (more != null) width = Math.max(width, font.width(more));
        return width + PAD * 2;
    }

    @Override
    public int height(Font font) {
        // A line per shard listed, or the one saying there are none.
        return panelHeight(Math.max(1, shown().size()) + (moreLine() != null ? 1 : 0));
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, Font font) {
        int width = width(font);
        Theme.roundRect(graphics, 0, 0, width, height(font), Theme.HUD_BACKDROP);
        drawTitleRow(graphics, font, PAD, PAD, false);

        int y = PAD + TITLE_H;
        List<SafariShardTracker.Entry> shown = shown();
        if (shown.isEmpty()) {
            graphics.text(font, EMPTY, PAD, y, Theme.TEXT_MUTED, false);
            return;
        }
        for (SafariShardTracker.Entry entry : shown) {
            drawEntry(graphics, font, entry, PAD, y, width - PAD * 2);
            y += LINE_H;
        }
        String more = moreLine();
        if (more != null) graphics.text(font, more, PAD, y, Theme.TEXT_MUTED, false);
    }

    private static boolean isOverTitleRow(float mouseY) {
        return mouseY < PAD + TITLE_H;
    }

    /** Lights up the button while the mouse is on the top row; on the list, shows the full one over it. */
    @Override
    public void extractHovered(GuiGraphicsExtractor graphics, Font font, float mouseX, float mouseY) {
        if (isOverTitleRow(mouseY)) {
            drawTitleRow(graphics, font, PAD, PAD, true);
        } else if (moreLine() != null) {
            drawFullList(graphics, font);
        }
    }

    /** A click anywhere on the top row presses the button. */
    @Override
    public boolean mouseClicked(Font font, float mouseX, float mouseY, int button) {
        if (button != 0 || !isOverTitleRow(mouseY)) return false;
        SafariSettings.shardTrackerAllTime = !SafariSettings.shardTrackerAllTime;
        SafariSettings.save();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
        return true;
    }

    /** Every shard, in a panel that runs to several columns if one would not fit on screen. */
    private void drawFullList(GuiGraphicsExtractor graphics, Font font) {
        List<SafariShardTracker.Entry> entries = SafariShardTracker.entries();

        // Sizes here are in the widget's own units, so the screen is measured in them too.
        float screenW = graphics.guiWidth() / scale();
        float screenH = graphics.guiHeight() / scale();
        int maxRows = Math.max(1, (int) ((screenH - PAD * 2 - TITLE_H) / LINE_H));
        int columns = (entries.size() + maxRows - 1) / maxRows;
        int rows = (entries.size() + columns - 1) / columns;

        int columnW = columnWidth(font, entries);
        int width = Math.max(titleRowWidth(font), columns * columnW + (columns - 1) * COLUMN_GAP) + PAD * 2;
        int height = panelHeight(rows);

        // Moved up and left as far as needed to stay on screen.
        float left = pixelX(font, graphics.guiWidth()) / scale();
        float top = pixelY(font, graphics.guiHeight()) / scale();
        int x = (int) Math.floor(Math.max(-left, Math.min(0, screenW - left - width)));
        int y = (int) Math.floor(Math.max(-top, Math.min(0, screenH - top - height)));

        // Opaque, unlike the HUD backdrop, so the inventory behind doesn't show through the list.
        Theme.roundRect(graphics, x, y, width, height, Theme.PANEL, Theme.PANEL_BORDER);
        drawTitleRow(graphics, font, x + PAD, y + PAD, false);
        for (int i = 0; i < entries.size(); i++) {
            drawEntry(graphics, font, entries.get(i), x + PAD + (i / rows) * (columnW + COLUMN_GAP),
                    y + PAD + TITLE_H + (i % rows) * LINE_H, columnW);
        }
    }

    /** Draws the total, then the button saying which shards it counts. */
    private static void drawTitleRow(GuiGraphicsExtractor graphics, Font font, int x, int y, boolean hovered) {
        graphics.text(font, TITLE, x, y, Theme.TEXT_MUTED, false);
        graphics.text(font, Integer.toString(SafariShardTracker.total()), x + font.width(TITLE), y,
                Theme.ACCENT, false);

        String label = buttonLabel();
        int buttonX = x + font.width(title()) + BUTTON_GAP;
        Theme.roundRect(graphics, buttonX, y - 2, font.width(label) + BUTTON_PAD * 2, TITLE_H,
                hovered ? Theme.CONTROL_HOVER : Theme.CONTROL, hovered ? Theme.ACCENT : Theme.CONTROL_BORDER);
        graphics.text(font, label, buttonX + BUTTON_PAD, y, hovered ? Theme.TEXT : Theme.TEXT_MUTED, false);
    }

    /** Draws a shard's icon and name at the left of a column and its count at the right. */
    private static void drawEntry(GuiGraphicsExtractor graphics, Font font, SafariShardTracker.Entry entry,
                                  int x, int y, int columnW) {
        ItemStack icon = ShardIcons.icon(entry.shard());
        if (!icon.isEmpty()) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.pose().scale(ICON_SIZE / 16f, ICON_SIZE / 16f);
            graphics.item(icon, 0, 0);
            graphics.pose().popMatrix();
        }
        String count = count(entry);
        graphics.text(font, entry.shard(), x + ICON_WIDTH, y, Theme.TEXT, false);
        graphics.text(font, count, x + columnW - font.width(count), y, Theme.ACCENT, false);
    }
}
