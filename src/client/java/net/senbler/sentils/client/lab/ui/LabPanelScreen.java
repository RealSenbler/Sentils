package net.senbler.sentils.client.lab.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import gg.essential.elementa.ElementaVersion;
import gg.essential.elementa.UIComponent;
import gg.essential.elementa.WindowScreen;
import gg.essential.elementa.components.UIBlock;
import gg.essential.elementa.components.UIRoundedRectangle;
import gg.essential.elementa.components.UIText;
import gg.essential.elementa.constraints.RelativeConstraint;
import gg.essential.universal.UKeyboard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.senbler.sentils.client.gui.Theme;
import net.senbler.sentils.client.gui.elementa.E;
import net.senbler.sentils.client.gui.elementa.EButton;

/**
 * The frame the Lab's screens share: a panel taking up most of the window, with a header. Drawn
 * with Elementa.
 * <p>
 * A screen says where each thing goes in the window's own measure, working from the panel's
 * edges kept here, and puts it there with add. It is built afresh whenever what it shows
 * changes, by rebuildWidgets.
 */
public abstract class LabPanelScreen extends WindowScreen {

    private static final float SCREEN_SHARE_W = 0.88f;
    private static final float SCREEN_SHARE_H = 0.9f;
    private static final int MIN_W = 360;
    private static final int MIN_H = 230;
    private static final int MAX_W = 760;
    private static final int MAX_H = 460;

    protected static final int PAD = 10;
    protected static final int HEADER_H = 34;
    protected static final int BUTTON_H = 16;
    protected static final int GAP = 4;

    protected final Screen parent; // may be null (closes to the game)
    private final String heading;
    protected int left;
    protected int top;
    protected int panelW;
    protected int panelH;
    /** The area below the header that the screen fills. */
    protected int contentX;
    protected int contentY;
    protected int contentW;
    protected int contentBottom;

    private UIText subtitleText;
    private final List<LabTable> tables = new ArrayList<>();

    protected LabPanelScreen(Screen parent, Component title) {
        super(ElementaVersion.V11, true, false, false, -1);
        // The newer of Elementa's two ways of drawing. The older one draws in the middle of the game working
        // out what to draw, which other mods that change how frames are drawn can break.
        setUseElementaRenderer(true);
        this.parent = parent;
        this.heading = title.getString();
    }

    /** The line under the title. */
    protected abstract String subtitle();

    /** Puts everything the screen shows onto it. The first thing it does is call layout. */
    protected abstract void build();

    @Override
    public void initScreen(int width, int height) {
        super.initScreen(width, height);
        rebuildWidgets();
    }

    /** Builds the screen again from the records, for when something it shows has changed under it. */
    @Override
    protected void rebuildWidgets() {
        for (LabTable table : this.tables) table.saveScroll();
        this.tables.clear();
        getWindow().clearChildren();
        build();
        for (LabTable table : this.tables) table.finish();
    }

    public void refresh() {
        rebuildWidgets();
    }

    /** Works out where the panel goes and draws it and its header. Call first thing in build. */
    protected void layout() {
        this.panelW = Math.clamp(Math.round(this.width * SCREEN_SHARE_W), Math.min(MIN_W, this.width - 8), MAX_W);
        this.panelH = Math.clamp(Math.round(this.height * SCREEN_SHARE_H), Math.min(MIN_H, this.height - 8), MAX_H);
        this.left = (this.width - this.panelW) / 2;
        this.top = (this.height - this.panelH) / 2;
        this.contentX = this.left + PAD;
        this.contentY = this.top + HEADER_H + 6;
        this.contentW = this.panelW - PAD * 2;
        this.contentBottom = this.top + this.panelH - PAD;

        UIComponent window = getWindow();
        new UIBlock(new Color(0, 0, 0, 110)).setWidth(new RelativeConstraint(1f)).setHeight(new RelativeConstraint(1f))
                .setChildOf(window);
        // A soft shadow, of three darker and darker shapes each a little smaller than the last
        for (int i = 3; i >= 1; i--) {
            new UIRoundedRectangle(10f + i * 2).setX(E.px(this.left - i * 3)).setY(E.px(this.top - i * 3))
                    .setWidth(E.px(this.panelW + i * 6)).setHeight(E.px(this.panelH + i * 6))
                    .setColor(new Color(0, 0, 0, 22)).setChildOf(window);
        }
        new UIRoundedRectangle(11f).setX(E.px(this.left - 1)).setY(E.px(this.top - 1)).setWidth(E.px(this.panelW + 2))
                .setHeight(E.px(this.panelH + 2)).setColor(E.color(Theme.PANEL_BORDER)).setChildOf(window);
        new UIRoundedRectangle(10f).setX(E.px(this.left)).setY(E.px(this.top)).setWidth(E.px(this.panelW))
                .setHeight(E.px(this.panelH)).setColor(E.color(Theme.PANEL)).setChildOf(window);

        new UIRoundedRectangle(1f).setX(E.px(this.left + PAD)).setY(E.px(this.top + 9)).setWidth(E.px(2))
                .setHeight(E.px(18)).setColor(E.color(Theme.ACCENT)).setChildOf(window);
        text(this.heading, this.left + PAD + 8, this.top + 9, Theme.TEXT);
        this.subtitleText = text(subtitle(), this.left + PAD + 8, this.top + 19, Theme.TEXT_MUTED);
        new UIBlock(E.color(Theme.DIVIDER)).setX(E.px(this.left + 1)).setY(E.px(this.top + HEADER_H))
                .setWidth(E.px(this.panelW - 2)).setHeight(E.px(1)).setChildOf(window);
    }

    /** Puts something on the screen, where it has said it goes. */
    protected <T extends UIComponent> T add(T component) {
        component.setChildOf(getWindow());
        if (component instanceof LabTable table) this.tables.add(table);
        return component;
    }

    /** Puts a rounded shape on the screen: a card, a bar of a chart. */
    protected UIComponent box(float x, float y, float width, float height, float radius, int color) {
        return add(new UIRoundedRectangle(radius).setX(E.px(x)).setY(E.px(y)).setWidth(E.px(width))
                .setHeight(E.px(height)).setColor(E.color(color)));
    }

    /** Puts a line of text on the screen with its top-left corner at the given spot. */
    protected UIText text(String text, int x, int y, int color) {
        UIText line = new UIText(text, false);
        line.setX(E.px(x)).setY(E.px(y)).setColor(E.color(color));
        return add(line);
    }

    /** Adds a button as wide as its label needs, with its left edge at x. Returns the x just past it. */
    protected int addButton(int x, int y, String label, EButton.Style style, Runnable action) {
        int w = this.font.width(label) + 14;
        add(new EButton(x, y, w, BUTTON_H, Component.literal(label), style, action));
        return x + w + GAP;
    }

    /** Adds a button with its right edge at the given x. Returns the x just before it. */
    protected int addButtonLeftOf(int right, int y, String label, EButton.Style style, Runnable action) {
        int w = this.font.width(label) + 14;
        add(new EButton(right - w, y, w, BUTTON_H, Component.literal(label), style, action));
        return right - w - GAP;
    }

    protected static void open(Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }

    @Override
    public void onTick() {
        super.onTick();
        // The line under the title can have a running clock in it.
        if (this.subtitleText != null) {
            String now = subtitle();
            if (!now.equals(this.subtitleText.getText())) this.subtitleText.setText(now);
        }
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
        open(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
