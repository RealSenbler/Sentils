package net.senbler.sentils.client.gui.elementa;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import gg.essential.elementa.UIComponent;
import gg.essential.elementa.components.UIBlock;
import gg.essential.elementa.components.UIContainer;
import gg.essential.elementa.components.UIRoundedRectangle;
import gg.essential.elementa.components.UIText;
import gg.essential.elementa.components.Window;
import gg.essential.elementa.constraints.CenterConstraint;
import gg.essential.elementa.constraints.ConstantColorConstraint;
import gg.essential.elementa.constraints.PixelConstraint;
import gg.essential.elementa.constraints.animation.AnimatingConstraints;
import gg.essential.elementa.constraints.animation.Animations;
import gg.essential.universal.USound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

import net.senbler.sentils.client.gui.Theme;

/** The small things every screen drawn with Elementa needs: colors, sizes, fades, clicks and tooltips. */
public final class E {

    private E() {}

    public static final Color CLEAR = new Color(0, 0, 0, 0);
    private static final float FADE_SECONDS = 0.12f;
    private static final int TIP_MAX_W = 230;
    private static final int TIP_PAD = 5;
    private static final String CUT = "...";

    /** A theme color, which is kept as a number, as Elementa wants it. One with no alpha is solid. */
    public static Color color(int argb) {
        return new Color((argb >>> 24) == 0 ? argb | 0xFF000000 : argb, true);
    }

    public static Color withAlpha(int argb, int alpha) {
        return new Color(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, alpha);
    }

    public static PixelConstraint px(float value) {
        return new PixelConstraint(value);
    }

    /** Measured from the right or the bottom instead. */
    public static PixelConstraint pxEnd(float value) {
        return new PixelConstraint(value, true);
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    /** The text, cut short with dots if it is wider than the space. */
    public static String fit(String text, int space) {
        if (text == null) return "";
        Font font = font();
        if (font.width(text) <= space) return text;
        return font.plainSubstrByWidth(text, Math.max(0, space - font.width(CUT))) + CUT;
    }

    /** Eases the component's color over to another. */
    public static void fade(UIComponent component, Color to) {
        AnimatingConstraints animation = component.makeAnimation();
        animation.setColorAnimation(Animations.OUT_QUAD, FADE_SECONDS, new ConstantColorConstraint(to));
        component.animateTo(animation);
    }

    /** Lights the component up while the mouse is on it. */
    public static void hover(UIComponent component, Color normal, Color hovered) {
        component.onMouseEnterRunnable(() -> fade(component, hovered));
        component.onMouseLeaveRunnable(() -> fade(component, normal));
    }

    public static void clickSound() {
        USound.INSTANCE.playButtonPress(0.25f);
    }

    /**
     * Runs something once the game is next between frames. A screen must not be changed, closed or
     * swapped for another while it is being drawn or while its own parts are being gone through for
     * the mouse: Elementa refuses the one, and closing a screen in the middle of its drawing has it
     * drawn with what it draws on already thrown away.
     */
    public static void later(Runnable action) {
        Minecraft.getInstance().schedule(action);
    }

    /**
     * Runs the action when the component is clicked with the left button. It is run once the
     * click has been dealt with, not in the middle of it, since most actions build the screen
     * again, and that would pull the component out from under the click.
     */
    public static void onClick(UIComponent component, Runnable action) {
        component.onMouseClickConsumer(event -> {
            if (event.getMouseButton() != 0) return;
            clickSound();
            later(action);
        });
    }

    /** An arrow up or down, of three bars each wider than the last. */
    public static UIComponent arrow(boolean up, Color color) {
        UIComponent arrow = new UIContainer().setWidth(px(6)).setHeight(px(6));
        for (int i = 0; i < 3; i++) {
            int width = up ? 2 + i * 2 : 6 - i * 2;
            new UIBlock(color).setX(new CenterConstraint()).setY(px(i * 2)).setWidth(px(width)).setHeight(px(2))
                    .setChildOf(arrow);
        }
        return arrow;
    }

    /**
     * Shows the text in a small box under the component while the mouse is on it. Lines are
     * separated by "\n", and long ones are wrapped.
     */
    public static void tooltip(UIComponent anchor, String text) {
        if (text == null || text.isEmpty()) return;
        UIComponent[] shown = new UIComponent[1];
        boolean[] hovered = new boolean[1];
        anchor.onMouseEnterRunnable(() -> {
            hovered[0] = true;
            later(() -> showTip(anchor, text, shown, hovered));
        });
        anchor.onMouseLeaveRunnable(() -> {
            hovered[0] = false;
            later(() -> hideTip(shown));
        });
    }

    private static void showTip(UIComponent anchor, String text, UIComponent[] shown, boolean[] hovered) {
        {
            hideTip(shown);
            if (!hovered[0]) return;
            Window window;
            try {
                window = Window.Companion.of(anchor);
            } catch (IllegalStateException gone) {
                return; // the screen was built again in the meantime, without this on it
            }
            Font font = font();
            List<String> lines = new ArrayList<>();
            for (String line : text.split("\n")) lines.addAll(wrap(line, TIP_MAX_W));
            int width = 0;
            for (String line : lines) width = Math.max(width, font.width(line));
            width += TIP_PAD * 2;
            int height = lines.size() * 10 - 1 + TIP_PAD * 2;

            // Under the component, or over it if there is no room under; never off the window's edge.
            float x = Math.max(2, Math.min(anchor.getLeft(), window.getWidth() - width - 2));
            float y = anchor.getBottom() + 4;
            if (y + height > window.getHeight() - 2) y = Math.max(2, anchor.getTop() - height - 4);

            UIComponent edge = new UIRoundedRectangle(6f).setX(px(x - 1)).setY(px(y - 1)).setWidth(px(width + 2))
                    .setHeight(px(height + 2)).setColor(color(Theme.CONTROL_BORDER));
            UIComponent box = new UIRoundedRectangle(5f).setX(px(1)).setY(px(1)).setWidth(px(width)).setHeight(px(height))
                    .setColor(color(Theme.SIDEBAR)).setChildOf(edge);
            for (int i = 0; i < lines.size(); i++) {
                new UIText(lines.get(i), false).setX(px(TIP_PAD)).setY(px(TIP_PAD + i * 10))
                        .setColor(color(Theme.TEXT)).setChildOf(box);
            }
            edge.setChildOf(window);
            // Drawn over everything else, whatever was added to the window after it.
            edge.setFloating(true);
            shown[0] = edge;
        }
    }

    private static void hideTip(UIComponent[] shown) {
        if (shown[0] == null) return;
        try {
            shown[0].setFloating(false);
            shown[0].hide(true);
        } catch (RuntimeException gone) {
            // The screen it was on has been built again or closed, and took it along.
        }
        shown[0] = null;
    }

    /**
     * Breaks text into lines no wider than the width, at spaces where it can. Spaces at the
     * start of the text are an indent, which every line it is broken into keeps.
     */
    public static List<String> wrap(String text, int width) {
        Font font = font();
        String body = text.stripLeading();
        String indent = text.substring(0, text.length() - body.length());
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : body.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (line.isEmpty() || font.width(indent + candidate) <= width) {
                line.setLength(0);
                line.append(candidate);
            } else {
                lines.add(indent + line);
                line.setLength(0);
                line.append(word);
            }
        }
        lines.add(indent + line);
        return lines;
    }
}
