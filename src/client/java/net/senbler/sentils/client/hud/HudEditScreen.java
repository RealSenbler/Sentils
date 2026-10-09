package net.senbler.sentils.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import net.senbler.sentils.client.gui.Theme;
import net.senbler.sentils.client.gui.widget.FlatButton;

import java.util.List;

/**
 * Lets the player drag HUD widgets around and resize them with the scroll wheel.
 * Only the widgets whose setting is switched on are shown; the rest are left where they are.
 */
public class HudEditScreen extends Screen {

    private static final int DIM = 0x66000000;
    private static final float SCALE_STEP = 1.1f;

    private final Screen parent; // may be null (closes to the game)
    private HudWidget dragging;
    private double grabX; // where on the widget it was grabbed
    private double grabY;

    public HudEditScreen(Screen parent) {
        super(Component.literal("HUD Layout"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(new FlatButton(this.width / 2 - 32, this.height - 30, 64, 18,
                Component.literal("Done"), FlatButton.Style.PRIMARY, this::onClose));
    }

    private int left(HudWidget widget) {
        return widget.pixelX(this.font, this.width);
    }

    private int top(HudWidget widget) {
        return widget.pixelY(this.font, this.height);
    }

    /** The topmost widget under the mouse, or null. */
    private HudWidget widgetAt(double mouseX, double mouseY) {
        List<HudWidget> widgets = HudManager.widgets();
        for (int i = widgets.size() - 1; i >= 0; i--) {
            HudWidget widget = widgets.get(i);
            if (!widget.isSwitchedOn()) continue;
            int x = left(widget);
            int y = top(widget);
            if (mouseX >= x && mouseX < x + widget.scaledWidth(this.font)
                    && mouseY >= y && mouseY < y + widget.scaledHeight(this.font)) {
                return widget;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;

        HudWidget widget = widgetAt(event.x(), event.y());
        if (widget == null) return false;

        if (event.button() == 1) {
            widget.reset();
        } else if (event.button() == 0) {
            this.dragging = widget;
            this.grabX = event.x() - left(widget);
            this.grabY = event.y() - top(widget);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.dragging == null) return super.mouseDragged(event, dragX, dragY);
        this.dragging.moveTo(this.font, (int) Math.round(event.x() - this.grabX),
                (int) Math.round(event.y() - this.grabY), this.width, this.height);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.dragging = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        HudWidget widget = this.dragging != null ? this.dragging : widgetAt(mouseX, mouseY);
        if (widget == null || scrollY == 0) return false;

        float scale = widget.scale() * (scrollY > 0 ? SCALE_STEP : 1f / SCALE_STEP);
        widget.place(widget.x(), widget.y(), scale);
        return true;
    }

    @Override
    public void onClose() {
        HudManager.save();
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // A light dim instead of the usual blur, so the game stays visible while placing things.
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, DIM);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        HudWidget hovered = this.dragging != null ? this.dragging : widgetAt(mouseX, mouseY);

        boolean anyShown = false;
        for (HudWidget widget : HudManager.widgets()) {
            if (!widget.isSwitchedOn()) continue;
            anyShown = true;
            HudManager.draw(graphics, widget);

            int x = left(widget);
            int y = top(widget);
            int w = widget.scaledWidth(this.font);
            int h = widget.scaledHeight(this.font);
            boolean active = widget == hovered;
            Theme.outline(graphics, x - 1, y - 1, w + 2, h + 2, active ? Theme.ACCENT : Theme.TEXT_MUTED);

            if (active) {
                String label = widget.name() + "  " + Math.round(widget.scale() * 100) + "%";
                int labelY = y >= 12 ? y - 11 : y + h + 3;
                int labelX = Math.min(x, this.width - this.font.width(label) - 2);
                graphics.text(this.font, label, labelX, labelY, Theme.ACCENT, true);
            }
        }

        String hint = anyShown ? "Drag to move  ·  Scroll to resize  ·  Right-click to reset"
                : "No HUD elements are switched on. Turn one on in the settings to place it here.";
        graphics.centeredText(this.font, hint, this.width / 2, this.height - 44, Theme.TEXT);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
}
