package net.senbler.sentils.client.hud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

/** Owns every HudWidget: draws them in game and loads and saves where the player put them. */
public final class HudManager {

    private HudManager() {}

    private static final List<HudWidget> widgets = new ArrayList<>();

    /** Adds a widget. Call before init. */
    public static void register(HudWidget widget) {
        widgets.add(widget);
    }

    public static List<HudWidget> widgets() {
        return Collections.unmodifiableList(widgets);
    }

    /** Call once from your ClientModInitializer, after registering the widgets. */
    public static void init() {
        Map<String, HudLayoutConfig.Placement> saved = HudLayoutConfig.load();
        for (HudWidget widget : widgets) {
            HudLayoutConfig.Placement placement = saved.get(widget.id());
            if (placement != null) widget.place(placement.x, placement.y, placement.scale);
        }
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("sentils", "hud"), HudManager::extract);
        // The in-game HUD sits underneath open screens, so widgets meant for screens are drawn
        // here, where they can also be hovered and clicked.
        ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
            if (screen instanceof HudEditScreen) return; // the editor draws the widgets itself
            ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> {
                for (HudWidget widget : widgets) {
                    if (widget.drawnOver(s) && widget.isEnabled()) drawHoverable(graphics, widget, mouseX, mouseY);
                }
            });
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                Font font = Minecraft.getInstance().font;
                for (HudWidget widget : widgets) {
                    if (!widget.drawnOver(s) || !widget.isEnabled()
                            || !widget.isOver(font, event.x(), event.y(), s.width, s.height)) continue;
                    if (widget.mouseClicked(font, widget.localX(font, event.x(), s.width),
                            widget.localY(font, event.y(), s.height), event.button())) {
                        return false; // used by the widget, so kept from the screen
                    }
                }
                return true;
            });
        });
    }

    public static void save() {
        Map<String, HudLayoutConfig.Placement> placements = new HashMap<>();
        for (HudWidget widget : widgets) {
            placements.put(widget.id(), new HudLayoutConfig.Placement(widget.x(), widget.y(), widget.scale()));
        }
        HudLayoutConfig.save(placements);
    }

    private static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        // The editor draws the widgets itself.
        if (mc.gui.hud.isHidden() || mc.gui.screen() instanceof HudEditScreen) return;

        // Widgets drawn over the open screen are left to the screen hook above.
        Screen screen = mc.gui.screen();
        for (HudWidget widget : widgets) {
            if (widget.isEnabled() && (screen == null || !widget.drawnOver(screen))) draw(graphics, widget);
        }
    }

    /** Draws one widget at its saved position and scale. */
    public static void draw(GuiGraphicsExtractor graphics, HudWidget widget) {
        // A mouse position that is never on the widget.
        drawHoverable(graphics, widget, -1, -1);
    }

    /** Draws one widget, along with what it shows while hovered if the mouse is on it. */
    private static void drawHoverable(GuiGraphicsExtractor graphics, HudWidget widget, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        graphics.pose().pushMatrix();
        graphics.pose().translate(widget.pixelX(font, screenW), widget.pixelY(font, screenH));
        graphics.pose().scale(widget.scale(), widget.scale());
        widget.extract(graphics, font);
        if (widget.isOver(font, mouseX, mouseY, screenW, screenH)) {
            widget.extractHovered(graphics, font, widget.localX(font, mouseX, screenW),
                    widget.localY(font, mouseY, screenH));
        }
        graphics.pose().popMatrix();
    }
}
