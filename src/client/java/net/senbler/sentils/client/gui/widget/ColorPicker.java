package net.senbler.sentils.client.gui.widget;

import java.util.function.IntConsumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import net.senbler.sentils.client.gui.Theme;

/**
 * A saturation/brightness square with a hue bar to its right. Click or drag in either one
 * to pick a color; onChange receives the new RGB color.
 */
public class ColorPicker extends AbstractWidget {

    private static final int HUE_W = 10;
    private static final int GAP = 6;
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final float MAX_HUE = 0.999f; // a hue of exactly 1 is outside Mth.hsvToRgb's range

    private final IntConsumer onChange;
    private float hue;
    private float saturation;
    private float brightness;
    private boolean draggingHue;

    public ColorPicker(int x, int y, int width, int height, Component message, int rgb, IntConsumer onChange) {
        super(x, y, width, height, message);
        this.onChange = onChange;
        setColor(rgb);
    }

    private int squareWidth() {
        return this.width - HUE_W - GAP;
    }

    public int getColor() {
        return Mth.hsvToRgb(this.hue, this.saturation, this.brightness) & 0xFFFFFF;
    }

    /** Moves the markers to the given RGB color. Does not call onChange. */
    public void setColor(int rgb) {
        rgb &= 0xFFFFFF;
        if (rgb == getColor()) return;

        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float delta = max - Math.min(r, Math.min(g, b));

        this.brightness = max;
        this.saturation = max == 0 ? 0 : delta / max;
        // Greys have no hue of their own, so the hue bar stays where it was for them.
        if (delta > 0) {
            float sixth;
            if (max == r) sixth = (g - b) / delta;
            else if (max == g) sixth = 2 + (b - r) / delta;
            else sixth = 4 + (r - g) / delta;
            this.hue = Math.min(MAX_HUE, ((sixth / 6f) % 1f + 1f) % 1f);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        this.draggingHue = event.x() >= this.getX() + squareWidth() + GAP / 2.0;
        pick(event.x(), event.y());
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        pick(event.x(), event.y());
    }

    private void pick(double mouseX, double mouseY) {
        float down = Mth.clamp((float) (mouseY - this.getY()) / (this.height - 1), 0f, 1f);
        if (this.draggingHue) {
            this.hue = Math.min(MAX_HUE, down);
        } else {
            this.saturation = Mth.clamp((float) (mouseX - this.getX()) / (squareWidth() - 1), 0f, 1f);
            this.brightness = 1f - down;
        }
        this.onChange.accept(getColor());
    }

    // Dragging would otherwise click on every press.
    @Override
    public void playDownSound(SoundManager soundManager) {
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int x = this.getX();
        int y = this.getY();
        int squareW = squareWidth();
        int hueX = x + this.width - HUE_W;
        int bottom = y + this.height;

        Theme.outline(graphics, x - 1, y - 1, squareW + 2, this.height + 2, Theme.CONTROL_BORDER);
        Theme.outline(graphics, hueX - 1, y - 1, HUE_W + 2, this.height + 2, Theme.CONTROL_BORDER);

        // One column per saturation step, each fading from full brightness down to black.
        for (int i = 0; i < squareW; i++) {
            int top = BLACK | Mth.hsvToRgb(this.hue, i / (float) (squareW - 1), 1f);
            graphics.fillGradient(x + i, y, x + i + 1, bottom, top, BLACK);
        }

        // The hue bar: the six stretches of the color wheel, top to bottom.
        for (int i = 0; i < 6; i++) {
            graphics.fillGradient(hueX, y + this.height * i / 6, hueX + HUE_W, y + this.height * (i + 1) / 6,
                    BLACK | Mth.hsvToRgb(i / 6f, 1f, 1f),
                    BLACK | Mth.hsvToRgb((i + 1) % 6 / 6f, 1f, 1f));
        }

        int markX = x + Math.round(this.saturation * (squareW - 1));
        int markY = y + Math.round((1f - this.brightness) * (this.height - 1));
        Theme.outline(graphics, markX - 3, markY - 3, 7, 7, BLACK);
        Theme.outline(graphics, markX - 2, markY - 2, 5, 5, WHITE);

        int hueY = y + Math.round(this.hue * (this.height - 1));
        Theme.outline(graphics, hueX - 2, hueY - 2, HUE_W + 4, 5, BLACK);
        Theme.outline(graphics, hueX - 1, hueY - 1, HUE_W + 2, 3, WHITE);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
