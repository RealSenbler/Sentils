package net.senbler.sentils.client.gui.elementa;

import java.util.function.Consumer;

import gg.essential.elementa.components.UIRoundedRectangle;
import gg.essential.elementa.components.Window;
import gg.essential.elementa.components.input.UITextInput;
import gg.essential.elementa.constraints.CenterConstraint;
import gg.essential.elementa.constraints.RelativeConstraint;
import gg.essential.elementa.constraints.SubtractiveConstraint;
import kotlin.Unit;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import net.senbler.sentils.client.gui.Theme;

/**
 * A box to type a line of text in. Clicking it takes the keyboard. A screen that builds itself
 * again as the text changes can keep hold of the box and put it back, text, cursor and all.
 */
public class EField extends UIRoundedRectangle {

    private final UITextInput input;
    private Consumer<String> responder = text -> { };
    private int maxLength = Integer.MAX_VALUE;
    private boolean digitsOnly;
    private boolean settingText; // the text is being set from here, which is not the player typing

    /** Placed at the given spot in whatever it is put in. The font and name are as a vanilla box takes them. */
    public EField(Font font, int x, int y, int width, int height, Component name) {
        super(5f);
        this.setX(E.px(x)).setY(E.px(y)).setWidth(E.px(width)).setHeight(E.px(height)).setColor(E.color(Theme.FIELD));
        this.input = new UITextInput("", false, E.withAlpha(Theme.ACCENT, 120));
        this.input.setX(E.px(6)).setY(new CenterConstraint())
                .setWidth(new SubtractiveConstraint(new RelativeConstraint(1f), E.px(12))).setHeight(E.px(9))
                .setColor(E.color(Theme.TEXT)).setChildOf(this);
        this.input.onUpdate(text -> {
            if (this.settingText) return Unit.INSTANCE;
            String allowed = this.digitsOnly ? text.replaceAll("\\D", "") : text;
            if (allowed.length() > this.maxLength) allowed = allowed.substring(0, this.maxLength);
            if (!allowed.equals(text)) {
                setValue(allowed);
                text = getValue();
            }
            // Between frames: a responder may build the screen again, which must not happen mid-keystroke.
            String typed = text;
            E.later(() -> this.responder.accept(typed));
            return Unit.INSTANCE;
        });
        this.onMouseClickConsumer(event -> this.input.grabWindowFocus());
        E.hover(this, E.color(Theme.FIELD), E.color(Theme.CONTROL));
    }

    /** Muted words shown while the box is empty. */
    public void setHint(Component hint) {
        this.input.setPlaceholder(hint.getString());
    }

    /** Has the box take nothing but the digits 0 to 9. */
    public void setDigitsOnly(boolean digitsOnly) {
        this.digitsOnly = digitsOnly;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    /** Sets the text without telling the responder, which is for what the player types. */
    public void setValue(String text) {
        this.settingText = true;
        try {
            this.input.setText(text == null ? "" : text);
        } finally {
            this.settingText = false;
        }
    }

    public String getValue() {
        return this.input.getText();
    }

    /** Asks to be given the text each time the player changes it. */
    public void setResponder(Consumer<String> responder) {
        this.responder = responder;
    }

    public void setX(int x) {
        this.setX(E.px(x));
    }

    public void setY(int y) {
        this.setY(E.px(y));
    }

    public void setWidth(int width) {
        this.setWidth(E.px(width));
    }

    public EField setTooltip(Component text) {
        E.tooltip(this, text.getString());
        return this;
    }

    /** Whether the box has the keyboard. */
    public boolean hasKeyboard() {
        try {
            return Window.Companion.of(this).getFocusedComponent() == this.input;
        } catch (IllegalStateException notOnAScreen) {
            return false;
        }
    }

    /** Gives the box the keyboard. */
    public void takeKeyboard() {
        this.input.grabWindowFocus();
    }
}
