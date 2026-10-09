package net.senbler.sentils.client.gui.elementa;

import java.awt.Color;

import gg.essential.elementa.components.UIRoundedRectangle;
import gg.essential.elementa.components.UIText;
import gg.essential.elementa.constraints.CenterConstraint;
import net.minecraft.network.chat.Component;

import net.senbler.sentils.client.gui.Theme;

/** A button: a rounded shape with its label in the middle, which lights up under the mouse. */
public class EButton extends UIRoundedRectangle {

    public enum Style { PRIMARY, SECONDARY, DANGER }

    private final int width;
    private final UIText text;
    private final Color plainFill;
    private final Color plainLit;
    private Color fill;
    private Color lit;

    /** Placed at the given spot in whatever it is put in. */
    public EButton(int x, int y, int width, int height, Component label, Style style, Runnable action) {
        super(5f);
        this.width = width;
        this.plainFill = switch (style) {
            case PRIMARY -> E.color(Theme.ACCENT);
            case SECONDARY -> E.color(Theme.CONTROL);
            case DANGER -> E.color(Theme.DANGER_FILL);
        };
        this.plainLit = switch (style) {
            case PRIMARY -> E.color(Theme.ACCENT_HOVER);
            case SECONDARY -> E.color(Theme.CONTROL_HOVER);
            case DANGER -> E.color(Theme.lerpColor(Theme.DANGER_FILL, Theme.DANGER, 0.35f));
        };
        int textColor = switch (style) {
            case PRIMARY -> Theme.ON_ACCENT;
            case SECONDARY -> Theme.TEXT;
            case DANGER -> Theme.DANGER;
        };
        this.fill = this.plainFill;
        this.lit = this.plainLit;
        this.setX(E.px(x)).setY(E.px(y)).setWidth(E.px(width)).setHeight(E.px(height)).setColor(this.fill);
        this.text = new UIText(E.fit(label.getString(), width - 6), false);
        this.text.setX(new CenterConstraint()).setY(new CenterConstraint()).setColor(E.color(textColor)).setChildOf(this);
        this.onMouseEnterRunnable(() -> E.fade(this, this.lit));
        this.onMouseLeaveRunnable(() -> E.fade(this, this.fill));
        E.onClick(this, action);
    }

    /** Sets what is shown while the mouse is over the button. */
    public EButton setTooltip(Component text) {
        E.tooltip(this, text.getString());
        return this;
    }

    public void setLabel(String label) {
        this.text.setText(E.fit(label, this.width - 6));
    }

    /** Draws the button in the accent color, for one that is waiting on the player. */
    public void setHighlighted(boolean highlighted) {
        this.fill = highlighted ? E.withAlpha(Theme.ACCENT, 110) : this.plainFill;
        this.lit = highlighted ? E.withAlpha(Theme.ACCENT, 150) : this.plainLit;
        E.fade(this, this.fill);
    }
}
