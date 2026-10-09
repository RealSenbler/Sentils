package net.senbler.sentils.client.gui;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Shared colors and drawing helpers for the mod's screens and HUD. */
public final class Theme {

    private Theme() {}

    /** The two colors the player can change, as RGB; every other color is derived from them. */
    public static final int DEFAULT_ACCENT = 0x2DD4BF;
    public static final int DEFAULT_BACKGROUND = 0x14141B;

    // Not final: apply() recalculates all of these whenever the theme colors change.
    public static int PANEL;
    public static int PANEL_BORDER;
    public static int SIDEBAR;
    public static int CARD;
    public static int DIVIDER;

    public static int CONTROL;
    public static int CONTROL_HOVER;
    public static int CONTROL_BORDER;
    public static int FIELD;

    public static int ACCENT;
    public static int ACCENT_HOVER;
    public static int ON_ACCENT;
    public static int DANGER = 0xFFF87171;
    /** For a figure that is better than what it is set against, and for one that calls for care. */
    public static final int GOOD = 0xFF4ADE80;
    public static final int WARN = 0xFFFBBF24;
    public static int DANGER_FILL;

    public static int TEXT;
    public static int TEXT_MUTED;
    public static int KNOB = 0xFFF4F4F7;

    /** Translucent backing for HUD elements drawn over the game. */
    public static int HUD_BACKDROP;

    static {
        apply(DEFAULT_ACCENT, DEFAULT_BACKGROUND);
    }

    /** Rebuilds the whole palette from an accent and a background color (both RGB). */
    public static void apply(int accent, int background) {
        int base = 0xFF000000 | background;
        // Shades step towards "fore" to stand out from the background and towards "back" to
        // sink into it, so a light background gets dark text and the other way round.
        boolean dark = luminance(base) < 0.5f;
        int fore = dark ? 0xFFFFFFFF : 0xFF000000;
        int back = dark ? 0xFF000000 : 0xFFFFFFFF;

        PANEL = base;
        PANEL_BORDER = lerpColor(base, fore, 0.11f);
        SIDEBAR = lerpColor(base, back, 0.2f);
        CARD = lerpColor(base, fore, 0.04f);
        DIVIDER = lerpColor(base, fore, 0.09f);

        CONTROL = lerpColor(base, fore, 0.085f);
        CONTROL_HOVER = lerpColor(base, fore, 0.14f);
        CONTROL_BORDER = lerpColor(base, fore, 0.18f);
        FIELD = lerpColor(base, back, 0.25f);

        ACCENT = 0xFF000000 | accent;
        ACCENT_HOVER = lerpColor(ACCENT, 0xFFFFFFFF, 0.3f);
        ON_ACCENT = luminance(ACCENT) > 0.5f
                ? lerpColor(ACCENT, 0xFF000000, 0.85f)
                : lerpColor(ACCENT, 0xFFFFFFFF, 0.9f);
        DANGER_FILL = lerpColor(base, DANGER, 0.17f);

        TEXT = lerpColor(base, fore, 0.93f);
        TEXT_MUTED = lerpColor(base, fore, 0.52f);

        HUD_BACKDROP = (SIDEBAR & 0x00FFFFFF) | 0xB0000000;
    }

    /** Perceived brightness of a color, from 0 (black) to 1 (white). */
    private static float luminance(int color) {
        return (0.299f * ((color >> 16) & 0xFF) + 0.587f * ((color >> 8) & 0xFF) + 0.114f * (color & 0xFF)) / 255f;
    }

    /** Formats an RGB color as "#RRGGBB". */
    public static String hex(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    /** Parses "#RRGGBB" (the # is optional) into an RGB color, or returns -1 if it isn't one. */
    public static int parseHex(String text) {
        String digits = text.trim();
        if (digits.startsWith("#")) digits = digits.substring(1);
        if (!digits.matches("[0-9a-fA-F]{6}")) return -1;
        return Integer.parseInt(digits, 16);
    }

    /** Corner radius of panels, cards and HUD backdrops, and of buttons, fields and other controls. */
    public static final int RADIUS = 3;
    public static final int CONTROL_RADIUS = 4;

    private static final int MAX_CORNER = 128; // in window pixels; nothing asks for a bigger corner
    private static final Map<Integer, Identifier> DISCS = new HashMap<>();
    private static final int SHADOW_LAYERS = 5;
    private static final int SHADOW_LAYER_COLOR = 0x10000000;

    /** Filled rectangle with rounded corners. */
    public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        rounded(g, x, y, w, h, RADIUS, color);
    }

    /** Rounded rectangle with a thin border. */
    public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
        rounded(g, x, y, w, h, RADIUS, fill, border);
    }

    /**
     * Filled rectangle with smooth corners of the given radius. It is drawn pixel by pixel of the
     * window rather than of the GUI scale, which is what lets the corners be curves and lets the
     * position and size be fractions. A radius of half the height makes a pill, or a circle.
     */
    public static void rounded(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int color) {
        int s = begin(g);
        int px = Math.round(x * s);
        int py = Math.round(y * s);
        fillRounded(g, px, py, Math.round((x + w) * s) - px, Math.round((y + h) * s) - py, Math.round(radius * s),
                color);
        g.pose().popMatrix();
    }

    /** Smooth rounded rectangle with a thin border. */
    public static void rounded(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius,
                               int fill, int border) {
        int s = begin(g);
        int px = Math.round(x * s);
        int py = Math.round(y * s);
        int pw = Math.round((x + w) * s) - px;
        int ph = Math.round((y + h) * s) - py;
        int pr = Math.round(radius * s);
        int thickness = Math.max(1, s / 2);
        fillRounded(g, px, py, pw, ph, pr, border);
        fillRounded(g, px + thickness, py + thickness, pw - thickness * 2, ph - thickness * 2, pr - thickness, fill);
        g.pose().popMatrix();
    }

    /** A soft shadow around and a little below a rounded rectangle; draw it before the rectangle. */
    public static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius) {
        int s = begin(g);
        for (int layer = SHADOW_LAYERS; layer >= 1; layer--) {
            int grow = layer * s;
            fillRounded(g, x * s - grow, y * s - grow + s * 2, w * s + grow * 2, h * s + grow * 2,
                    radius * s + grow, SHADOW_LAYER_COLOR);
        }
        g.pose().popMatrix();
    }

    /**
     * A small filled triangle pointing up or down, fitted into a square of the given size with
     * its top-left corner at (x, y). For marking a figure as higher or lower than another.
     */
    public static void arrow(GuiGraphicsExtractor g, float x, float y, float size, boolean up, int color) {
        int s = begin(g);
        int rows = Math.max(2, Math.round(size * s * 0.75f));
        int top = Math.round(y * s) + (Math.round(size * s) - rows) / 2;
        float middle = (x + size / 2f) * s;
        for (int row = 0; row < rows; row++) {
            // Widest at the base, which is the bottom row of one pointing up.
            float reach = (up ? row + 1 : rows - row) / (float) rows * size * s / 2f;
            g.fill(Math.round(middle - reach), top + row, Math.round(middle + reach), top + row + 1, color);
        }
        g.pose().popMatrix();
    }

    /** The color with its opacity multiplied by alpha, from 0 to 1. */
    public static int withAlpha(int color, float alpha) {
        int a = Math.round((color >>> 24) * Math.clamp(alpha, 0f, 1f));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /** Switches drawing to window pixels, until the matching pose().popMatrix(). Returns the GUI scale. */
    private static int begin(GuiGraphicsExtractor g) {
        int s = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        g.pose().pushMatrix();
        g.pose().scale(1f / s, 1f / s);
        return s;
    }

    /**
     * A rounded rectangle in window pixels. Each corner is a quarter of a ready-made picture of
     * a disc, tinted to the color, and the rest is three plain rectangles: seven things to draw
     * however big the corners are. Drawing the curves a row of pixels at a time, as this once
     * did, came to thousands of rectangles a frame on a busy screen and made the menus lag.
     */
    private static void fillRounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        int r = Math.clamp(radius, 0, Math.min(Math.min(w, h) / 2, MAX_CORNER));
        if (r == 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }

        Identifier disc = disc(r);
        int d = r * 2;
        g.blit(RenderPipelines.GUI_TEXTURED, disc, x, y, 0, 0, r, r, d, d, color);
        g.blit(RenderPipelines.GUI_TEXTURED, disc, x + w - r, y, r, 0, r, r, d, d, color);
        g.blit(RenderPipelines.GUI_TEXTURED, disc, x, y + h - r, 0, r, r, r, d, d, color);
        g.blit(RenderPipelines.GUI_TEXTURED, disc, x + w - r, y + h - r, r, r, r, r, d, d, color);
        if (w > d) {
            g.fill(x + r, y, x + w - r, y + r, color);
            g.fill(x + r, y + h - r, x + w - r, y + h, color);
        }
        if (h > d) {
            g.fill(x, y + r, x + w, y + h - r, color);
        }
    }

    /**
     * The picture of a white disc of the given radius in pixels, made the first time it is asked
     * for and kept. Its edge is blended, so corners cut from it are smooth; it is drawn at its
     * own size, a pixel for a pixel, so a disc is kept for each radius in use, which is not many.
     */
    private static Identifier disc(int radius) {
        return DISCS.computeIfAbsent(radius, r -> {
            int size = r * 2;
            NativeImage image = new NativeImage(size, size, true);
            for (int py = 0; py < size; py++) {
                for (int px = 0; px < size; px++) {
                    double distance = Math.hypot(px + 0.5 - r, py + 0.5 - r);
                    int alpha = (int) Math.round(Math.clamp(r - distance + 0.5, 0.0, 1.0) * 255);
                    image.setPixel(px, py, (alpha << 24) | 0x00FFFFFF);
                }
            }
            Identifier id = Identifier.fromNamespaceAndPath("sentils", "disc/" + r);
            DynamicTexture texture = new DynamicTexture(() -> "sentils disc " + r, image);
            texture.upload();
            Minecraft.getInstance().getTextureManager().register(id, texture);
            return id;
        });
    }

    /** A 1px rectangular outline. */
    public static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    public static int lerpColor(int from, int to, float t) {
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = (from >> shift) & 0xFF;
            int b = (to >> shift) & 0xFF;
            result |= (Math.round(a + (b - a) * t) & 0xFF) << shift;
        }
        return result;
    }
}
