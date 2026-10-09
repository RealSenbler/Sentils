package net.senbler.sentils.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import net.senbler.sentils.client.gui.Fade;
import net.senbler.sentils.client.gui.Theme;

public class FlatButton extends AbstractButton {

    public enum Style { PRIMARY, SECONDARY, DANGER }

    private final Style style;
    private final Runnable action;
    private final Fade hover = new Fade(120f, false);
    private boolean highlighted;

    public FlatButton(int x, int y, int width, int height, Component message, Style style, Runnable action) {
        super(x, y, width, height, message);
        this.style = style;
        this.action = action;
    }

    /** Draws a secondary button in the accent color, to show it is waiting for input. */
    public void setHighlighted(boolean highlighted) {
        this.highlighted = highlighted;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.action.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float lit = this.hover.step(this.isHoveredOrFocused());
        int fill;
        int border;
        int text;
        switch (this.style) {
            case PRIMARY -> {
                fill = Theme.lerpColor(Theme.ACCENT, Theme.ACCENT_HOVER, lit);
                border = fill;
                text = Theme.ON_ACCENT;
                // A soft halo in the accent color as it lights up
                Theme.rounded(graphics, this.getX() - 1, this.getY() - 1, this.width + 2, this.height + 2,
                        Theme.CONTROL_RADIUS + 1, Theme.withAlpha(Theme.ACCENT, 0.35f * lit));
            }
            case DANGER -> {
                fill = Theme.lerpColor(Theme.FIELD, Theme.DANGER_FILL, lit);
                border = Theme.lerpColor(Theme.CONTROL_BORDER, Theme.DANGER, lit);
                text = Theme.lerpColor(Theme.TEXT_MUTED, Theme.DANGER, lit);
            }
            default -> {
                fill = Theme.lerpColor(Theme.CONTROL, Theme.CONTROL_HOVER, lit);
                border = this.highlighted || this.isFocused() ? Theme.ACCENT
                        : Theme.lerpColor(Theme.CONTROL_BORDER, Theme.TEXT_MUTED, 0.5f * lit);
                text = this.highlighted ? Theme.ACCENT : Theme.TEXT;
            }
        }
        Theme.rounded(graphics, this.getX(), this.getY(), this.width, this.height, Theme.CONTROL_RADIUS, fill, border);

        Font font = Minecraft.getInstance().font;
        String label = this.getMessage().getString();
        graphics.text(font, label,
                this.getX() + (this.width - font.width(label) + 1) / 2,
                this.getY() + (this.height - 8) / 2,
                text, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
