package net.senbler.sentils.client.gui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import net.senbler.sentils.client.gui.Theme;

/** An EditBox that draws a flat themed background instead of the vanilla sprite. */
public class FlatEditBox extends EditBox {

    private boolean digitsOnly;
    private boolean invalid;

    public FlatEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        this.setTextShadow(false);
    }

    public void setDigitsOnly(boolean digitsOnly) {
        this.digitsOnly = digitsOnly;
    }

    /** Marks the current value as rejected, shown as a red border. */
    public void setInvalid(boolean invalid) {
        this.invalid = invalid;
    }

    // EditBox only consults isBordered() for the vanilla sprite and the inner width; the text
    // padding comes from its own field, so returning false here drops the sprite and nothing else.
    @Override
    public boolean isBordered() {
        return false;
    }

    @Override
    public int getInnerWidth() {
        return this.width - 8;
    }

    @Override
    public void insertText(String text) {
        if (this.digitsOnly) {
            text = text.replaceAll("\\D", "");
            if (text.isEmpty()) return;
        }
        super.insertText(text);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int border = this.invalid ? Theme.DANGER
                : this.isFocused() ? Theme.ACCENT
                : this.isHovered() ? Theme.TEXT_MUTED
                : Theme.CONTROL_BORDER;
        // A ring around the field while it is being typed in
        if (this.isFocused()) {
            Theme.rounded(graphics, this.getX() - 1, this.getY() - 1, this.width + 2, this.height + 2,
                    Theme.CONTROL_RADIUS + 1, Theme.withAlpha(border, 0.3f));
        }
        Theme.rounded(graphics, this.getX(), this.getY(), this.width, this.height, Theme.CONTROL_RADIUS,
                Theme.FIELD, border);
        this.setTextColor(Theme.TEXT); // set every frame so it follows a theme change while open
        super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
    }
}
