package net.senbler.sentils.client.theme;

import java.util.function.IntConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.senbler.sentils.client.gui.Theme;
import net.senbler.sentils.client.gui.widget.ColorPicker;
import net.senbler.sentils.client.gui.widget.FlatButton;
import net.senbler.sentils.client.gui.widget.FlatEditBox;

/**
 * Picks one RGB color, by dragging in the picker or typing a hex code. The color is previewed
 * live while it changes; Cancel puts the original back, Done (or Escape) keeps the new one.
 */
public class ColorPickerScreen extends Screen {

    private static final int PANEL_W = 228;
    private static final int PANEL_H = 186;
    private static final int PAD = 12;
    private static final int HEADER_H = 38;
    private static final int PICKER_W = 136;
    private static final int PICKER_H = 100;
    private static final int GAP = 10;
    private static final int SWATCH_H = 26;

    private final Screen parent; // may be null (closes to the game)
    private final String subtitle;
    private final int original;
    private final IntConsumer preview; // called with every color the player tries
    private final Runnable commit;     // called when the player keeps the new color

    private int color;
    private ColorPicker picker;
    private FlatEditBox hexField;

    private int left;
    private int top;
    private int contentTop;
    private int sideX; // the column to the right of the picker
    private int sideW;

    public ColorPickerScreen(Screen parent, Component title, String subtitle, int color,
                             IntConsumer preview, Runnable commit) {
        super(title);
        this.parent = parent;
        this.subtitle = subtitle;
        this.original = color;
        this.color = color;
        this.preview = preview;
        this.commit = commit;
    }

    @Override
    protected void init() {
        this.left = (this.width - PANEL_W) / 2;
        this.top = Math.max(4, (this.height - PANEL_H) / 2);
        this.contentTop = this.top + HEADER_H + 10;
        this.sideX = this.left + PAD + PICKER_W + GAP;
        this.sideW = this.left + PANEL_W - PAD - this.sideX;

        this.picker = new ColorPicker(this.left + PAD, this.contentTop, PICKER_W, PICKER_H,
                Component.literal("Color picker"), this.color, rgb -> {
                    this.color = rgb;
                    this.hexField.setValue(Theme.hex(rgb));
                    this.preview.accept(rgb);
                });
        this.addRenderableWidget(this.picker);

        this.hexField = new FlatEditBox(this.font, this.sideX, this.contentTop + 57, this.sideW, 16,
                Component.literal("Hex color code"));
        this.hexField.setMaxLength(7);
        this.hexField.setValue(Theme.hex(this.color));
        this.hexField.setResponder(text -> {
            int rgb = Theme.parseHex(text);
            this.hexField.setInvalid(rgb < 0);
            if (rgb >= 0 && rgb != this.color) {
                this.color = rgb;
                this.picker.setColor(rgb);
                this.preview.accept(rgb);
            }
        });
        this.addRenderableWidget(this.hexField);

        int footerY = this.top + PANEL_H - PAD - 18;
        int right = this.left + PANEL_W - PAD;
        this.addRenderableWidget(new FlatButton(right - 64 - 6 - 64, footerY, 64, 18,
                Component.literal("Cancel"), FlatButton.Style.SECONDARY, () -> {
                    this.preview.accept(this.original);
                    Minecraft.getInstance().gui.setScreen(this.parent);
                }));
        this.addRenderableWidget(new FlatButton(right - 64, footerY, 64, 18,
                Component.literal("Done"), FlatButton.Style.PRIMARY, this::onClose));
    }

    @Override
    public void onClose() {
        this.commit.run();
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Theme.roundRect(graphics, this.left, this.top, PANEL_W, PANEL_H, Theme.PANEL, Theme.PANEL_BORDER);

        // Header
        graphics.fill(this.left + PAD, this.top + 11, this.left + PAD + 2, this.top + 29, Theme.ACCENT);
        graphics.text(this.font, this.title.getString(), this.left + PAD + 8, this.top + 11, Theme.TEXT, false);
        graphics.text(this.font, this.subtitle, this.left + PAD + 8, this.top + 21, Theme.TEXT_MUTED, false);
        graphics.fill(this.left + 1, this.top + HEADER_H, this.left + PANEL_W - 1, this.top + HEADER_H + 1, Theme.DIVIDER);

        // Original color on the left, new color on the right
        graphics.text(this.font, "OLD / NEW", this.sideX + 1, this.contentTop, Theme.TEXT_MUTED, false);
        int swatchY = this.contentTop + 11;
        int middle = this.sideX + this.sideW / 2;
        Theme.roundRect(graphics, this.sideX, swatchY, this.sideW, SWATCH_H, Theme.CONTROL_BORDER);
        graphics.fill(this.sideX + 1, swatchY + 1, middle, swatchY + SWATCH_H - 1, 0xFF000000 | this.original);
        graphics.fill(middle, swatchY + 1, this.sideX + this.sideW - 1, swatchY + SWATCH_H - 1, 0xFF000000 | this.color);

        graphics.text(this.font, "HEX", this.sideX + 1, this.contentTop + 46, Theme.TEXT_MUTED, false);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
}
