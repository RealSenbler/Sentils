package net.senbler.sentils.client.lab.ui;

import gg.essential.elementa.UIComponent;
import gg.essential.elementa.components.UIRoundedRectangle;
import gg.essential.elementa.components.UIText;
import gg.essential.elementa.constraints.CenterConstraint;

import net.senbler.sentils.client.gui.Theme;
import net.senbler.sentils.client.gui.elementa.E;

/**
 * One figure on a card: a small picture, a word or two saying what it is, and the figure.
 * Anything more there is to say about it goes in its tooltip, so that the screen stays quiet.
 */
public class StatTile extends UIRoundedRectangle {

    public static final int HEIGHT = 30;
    private static final float ICON_SIZE = 12f;
    private static final int TEXT_X = 24;

    private final int width;
    private final UIText valueText;
    private final String shown;

    /** Placed at the given spot in whatever it is put in. */
    StatTile(int x, int y, int width, Icons.Icon icon, String label, String value) {
        super(6f);
        this.width = width;
        this.setX(E.px(x)).setY(E.px(y)).setWidth(E.px(width)).setHeight(E.px(HEIGHT)).setColor(E.color(Theme.CARD));
        icon.component(ICON_SIZE).setX(E.px(7)).setY(new CenterConstraint()).setChildOf(this);
        int room = width - TEXT_X - 3;
        new UIText(E.fit(label, room), false).setX(E.px(TEXT_X)).setY(E.px(6)).setColor(E.color(Theme.TEXT_MUTED))
                .setChildOf(this);
        this.shown = E.fit(value, room);
        this.valueText = new UIText(this.shown, false);
        this.valueText.setX(E.px(TEXT_X)).setY(E.px(16)).setColor(E.color(Theme.TEXT)).setChildOf(this);
        E.hover(this, E.color(Theme.CARD), E.color(Theme.CONTROL));
    }

    /** Sets what is shown when the mouse is over the tile. Lines are separated by "\n". */
    public StatTile tip(String text) {
        E.tooltip(this, text);
        return this;
    }

    /** Draws the figure muted, for one that is not available. */
    public StatTile muted() {
        this.valueText.setColor(E.color(Theme.TEXT_MUTED));
        return this;
    }

    /** Adds an arrow after the figure, up in green or down in red, with a few characters beside it. */
    public StatTile change(double difference, String text) {
        if (difference == 0) return this;
        int color = difference > 0 ? Theme.GOOD : Theme.DANGER;
        int x = TEXT_X + E.font().width(this.shown) + (this.shown.isEmpty() ? 0 : 5);
        if (x + 8 + E.font().width(text) > this.width - 3) return this; // no room for it
        UIComponent arrow = E.arrow(difference > 0, E.color(color));
        arrow.setX(E.px(x)).setY(E.px(17)).setChildOf(this);
        new UIText(text, false).setX(E.px(x + 8)).setY(E.px(16)).setColor(E.color(color)).setChildOf(this);
        return this;
    }
}
