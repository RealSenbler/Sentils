package net.senbler.sentils.client.gui.screen;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import gg.essential.elementa.ElementaVersion;
import gg.essential.elementa.UIComponent;
import gg.essential.elementa.WindowScreen;
import gg.essential.elementa.components.ScrollComponent;
import gg.essential.elementa.components.UIBlock;
import gg.essential.elementa.components.UIContainer;
import gg.essential.elementa.components.UIRoundedRectangle;
import gg.essential.elementa.components.UIText;
import gg.essential.elementa.constraints.RelativeConstraint;
import gg.essential.elementa.constraints.SiblingConstraint;
import gg.essential.universal.UKeyboard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.senbler.sentils.client.gui.Theme;
import net.senbler.sentils.client.gui.elementa.E;
import net.senbler.sentils.client.gui.elementa.EButton;
import net.senbler.sentils.client.gui.elementa.EField;

/**
 * A panel with a scrollable list of editable rows, an add button and a Done button. Drawn with
 * Elementa. Subclasses keep their own list of entries and build one row of widgets per entry;
 * the entries are saved whenever the screen closes.
 */
public abstract class ListEditorScreen extends WindowScreen {

    private static final int PANEL_W = 400;
    private static final int PANEL_H = 280;
    private static final int PAD = 12;
    private static final int HEADER_H = 38;
    private static final int ROW_H = 22;
    private static final int SCROLLBAR_W = 6;
    protected static final int FIELD_H = 18;
    protected static final int GAP = 6;
    protected static final int REMOVE_W = 18;

    private final Screen parent; // may be null (closes to the game)
    private final String heading;
    private final String subtitle;
    private final String addLabel;
    private final List<UIComponent[]> rows = new ArrayList<>();

    private int left;
    private int top;
    private ScrollComponent list;

    /** Left edge of the row area, measured from the row's own. */
    protected int innerLeft;
    /** Right edge of the area available to a row's own widgets (the remove button sits after it). */
    protected int innerRight;

    protected ListEditorScreen(Screen parent, Component title, String subtitle, String addLabel) {
        super(ElementaVersion.V11, true, false, false, -1);
        setUseElementaRenderer(true);
        this.parent = parent;
        this.heading = title.getString();
        this.subtitle = subtitle;
        this.addLabel = addLabel;
    }

    /** Creates the widgets for every entry, calling addRow once per entry. */
    protected abstract void buildRows();

    /** Appends a blank entry to the subclass's list. */
    protected abstract void addEntry();

    protected abstract void save();

    /** Puts up the column headings, calling columnLabel once per column. */
    protected abstract void addColumnLabels();

    @Override
    public void initScreen(int width, int height) {
        super.initScreen(width, height);
        rebuild(false);
    }

    /** Builds the screen again from the entries, scrolled to where it was or to the last row. */
    private void rebuild(boolean toBottom) {
        float offset = this.list != null ? this.list.getVerticalOffset() : 0f;
        this.rows.clear();
        UIComponent window = getWindow();
        window.clearChildren();

        int panelW = Math.min(PANEL_W, this.width - 16);
        int panelH = Math.min(PANEL_H, this.height - 8);
        this.left = (this.width - panelW) / 2;
        this.top = (this.height - panelH) / 2;
        int footerY = this.top + panelH - PAD - 18;
        int listTop = this.top + HEADER_H + 20;
        int listH = Math.max(FIELD_H, footerY - 6 - listTop);
        int listW = panelW - PAD * 2 - SCROLLBAR_W;
        this.innerLeft = 0;
        this.innerRight = listW - REMOVE_W - GAP;

        new UIBlock(new Color(0, 0, 0, 110)).setWidth(new RelativeConstraint(1f)).setHeight(new RelativeConstraint(1f))
                .setChildOf(window);
        // A soft shadow, of three darker and darker shapes each a little smaller than the last
        for (int i = 3; i >= 1; i--) {
            new UIRoundedRectangle(10f + i * 2).setX(E.px(this.left - i * 3)).setY(E.px(this.top - i * 3))
                    .setWidth(E.px(panelW + i * 6)).setHeight(E.px(panelH + i * 6))
                    .setColor(new Color(0, 0, 0, 22)).setChildOf(window);
        }
        new UIRoundedRectangle(11f).setX(E.px(this.left - 1)).setY(E.px(this.top - 1)).setWidth(E.px(panelW + 2))
                .setHeight(E.px(panelH + 2)).setColor(E.color(Theme.PANEL_BORDER)).setChildOf(window);
        new UIRoundedRectangle(10f).setX(E.px(this.left)).setY(E.px(this.top)).setWidth(E.px(panelW))
                .setHeight(E.px(panelH)).setColor(E.color(Theme.PANEL)).setChildOf(window);

        // Header
        new UIRoundedRectangle(1f).setX(E.px(this.left + PAD)).setY(E.px(this.top + 11)).setWidth(E.px(2))
                .setHeight(E.px(18)).setColor(E.color(Theme.ACCENT)).setChildOf(window);
        new UIText(this.heading, false).setX(E.px(this.left + PAD + 8)).setY(E.px(this.top + 11))
                .setColor(E.color(Theme.TEXT)).setChildOf(window);
        new UIText(E.fit(this.subtitle, panelW - PAD * 2 - 8), false).setX(E.px(this.left + PAD + 8))
                .setY(E.px(this.top + 21)).setColor(E.color(Theme.TEXT_MUTED)).setChildOf(window);
        new UIBlock(E.color(Theme.DIVIDER)).setX(E.px(this.left + 1)).setY(E.px(this.top + HEADER_H))
                .setWidth(E.px(panelW - 2)).setHeight(E.px(1)).setChildOf(window);

        addColumnLabels();

        this.list = new ScrollComponent("", 0f, Color.WHITE, false, true, false, false, ROW_H, 1f, null);
        this.list.setX(E.px(this.left + PAD)).setY(E.px(listTop)).setWidth(E.px(listW)).setHeight(E.px(listH))
                .setChildOf(window);
        UIComponent track = new UIContainer().setX(E.px(this.left + panelW - PAD - 2)).setY(E.px(listTop))
                .setWidth(E.px(2)).setHeight(E.px(listH)).setChildOf(window);
        UIComponent thumb = new UIRoundedRectangle(1f).setWidth(new RelativeConstraint(1f))
                .setColor(E.color(Theme.TEXT_MUTED)).setChildOf(track);
        this.list.setVerticalScrollBarComponent(thumb, true);

        buildRows();

        int addW = this.font.width(this.addLabel) + 16;
        new EButton(this.left + PAD, footerY, addW, 18, Component.literal(this.addLabel), EButton.Style.SECONDARY, () -> {
            addEntry();
            rebuild(true);
            if (!this.rows.isEmpty() && this.rows.getLast()[0] instanceof EField field) field.takeKeyboard();
        }).setChildOf(window);
        new EButton(this.left + panelW - PAD - 64, footerY, 64, 18, Component.literal("Done"),
                EButton.Style.PRIMARY, this::onClose).setChildOf(window);

        float lowest = Math.min(0f, listH - (this.rows.size() * ROW_H - (ROW_H - FIELD_H)));
        float to = toBottom ? lowest : Math.max(offset, lowest);
        if (to < 0) this.list.scrollTo(0f, to, false);
    }

    /** Puts a column heading over the rows, with its left edge at the given x of a row. */
    protected void columnLabel(String text, int x) {
        new UIText(text, false).setX(E.px(this.left + PAD + x + 1)).setY(E.px(this.top + HEADER_H + 8))
                .setColor(E.color(Theme.TEXT_MUTED)).setChildOf(getWindow());
    }

    /**
     * Adds one row. Each widget says where across the row it goes, from innerLeft to innerRight;
     * removeEntry should delete the row's entry from the subclass's list.
     */
    protected void addRow(Runnable removeEntry, UIComponent... widgets) {
        UIComponent line = new UIContainer().setY(new SiblingConstraint(ROW_H - FIELD_H))
                .setWidth(new RelativeConstraint(1f)).setHeight(E.px(FIELD_H)).setChildOf(this.list);
        for (UIComponent widget : widgets) widget.setChildOf(line);
        new EButton(this.innerRight + GAP, 0, REMOVE_W, FIELD_H, Component.literal("×"), EButton.Style.DANGER, () -> {
            removeEntry.run();
            rebuild(false);
        }).setTooltip(Component.literal("Remove")).setChildOf(line);
        this.rows.add(widgets);
    }

    @Override
    public void onKeyPressed(int keyCode, char typedChar, UKeyboard.Modifiers modifiers) {
        if (keyCode == UKeyboard.KEY_ESCAPE) {
            onClose();
            return;
        }
        super.onKeyPressed(keyCode, typedChar, modifiers);
    }

    @Override
    public void onClose() {
        save();
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
