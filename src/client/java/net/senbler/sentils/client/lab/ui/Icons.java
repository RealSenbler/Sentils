package net.senbler.sentils.client.lab.ui;

import gg.essential.elementa.UIComponent;
import gg.essential.elementa.components.UIContainer;
import gg.essential.elementa.components.UIRoundedRectangle;

import net.senbler.sentils.client.gui.elementa.E;

import net.senbler.sentils.client.gui.Theme;
import net.senbler.sentils.client.lab.analysis.Comparison;
import net.senbler.sentils.client.lab.analysis.Metric;
import net.senbler.sentils.client.lab.model.Provenance;

/**
 * The pictures and colors the Lab's screens use in place of words. The pictures are drawn from
 * a few plain shapes rather than taken from the game's items, which cannot be drawn until a
 * world has been joined, and the Lab can be opened from the main menu.
 */
final class Icons {

    private Icons() {}

    private static final int GOLD = 0xFFFBBF24;
    private static final int GOLD_DARK = 0xFFB45309;
    private static final int WOOD = 0xFFA16207;
    private static final int WOOD_DARK = 0xFF713F12;
    private static final int GREEN = 0xFF84CC16;
    private static final int GREEN_LIGHT = 0xFFD9F99D;
    private static final int WHEAT = 0xFFEAB308;
    private static final int PAPER = 0xFFE5E7EB;
    private static final int INK = 0xFF6B7280;
    private static final int VIOLET = 0xFF818CF8;
    private static final int RED = 0xFFF87171;
    private static final int MANUAL_BLUE = 0xFF60A5FA;

    /** A small picture, drawn to fit a square of whatever size is asked for. */
    enum Icon {
        CLOCK, COIN, COINS, CHEST, ORB, ITEMS, BOOK, CALENDAR, TAG;

        /** The picture, as something to put on a screen, in a square of the given size. */
        UIComponent component(float size) {
            UIComponent c = new UIContainer().setWidth(E.px(size)).setHeight(E.px(size));
            float x = 0;
            float y = 0;
            float u = size / 10f; // the pictures are worked out on a 10 by 10 grid
            switch (this) {
                case CLOCK -> {
                    r(c, x, y, size, size, size / 2, PAPER);
                    r(c, x + u * 1.5f, y + u * 1.5f, u * 7, u * 7, u * 3.5f, Theme.CARD);
                    r(c, x + u * 4.5f, y + u * 2.5f, u, u * 3, u / 2, PAPER); // the long hand, up
                    r(c, x + u * 4.5f, y + u * 4.5f, u * 3, u, u / 2, PAPER); // the short hand, right
                }
                case COIN -> coin(c, x, y, size);
                case COINS -> {
                    coin(c, x, y + u * 3, u * 7);
                    coin(c, x + u * 3, y, u * 7);
                }
                case CHEST -> {
                    r(c, x, y + u, size, u * 8, u * 1.5f, WOOD);
                    r(c, x, y + u * 4, size, u, 0, WOOD_DARK); // where the lid meets the box
                    r(c, x + u * 4, y + u * 3.5f, u * 2, u * 2.5f, u / 2, GOLD); // the latch
                }
                case ORB -> {
                    r(c, x + u, y + u, u * 8, u * 8, u * 4, GREEN);
                    r(c, x + u * 2.5f, y + u * 2.5f, u * 3, u * 3, u * 1.5f, GREEN_LIGHT);
                }
                case ITEMS -> {
                    // Four of something, two by two
                    for (int i = 0; i < 4; i++) {
                        r(c, x + u * (0.5f + i % 2 * 4.75f), y + u * (0.5f + i / 2 * 4.75f),
                                u * 4.25f, u * 4.25f, u, WHEAT);
                    }
                }
                case BOOK -> {
                    r(c, x + u, y, u * 8, size, u * 1.5f, VIOLET);
                    r(c, x + u * 2.5f, y + u * 2.5f, u * 5, u, 0, PAPER);
                    r(c, x + u * 2.5f, y + u * 4.5f, u * 5, u, 0, PAPER);
                }
                case CALENDAR -> {
                    r(c, x, y + u, size, u * 9, u * 1.5f, PAPER);
                    r(c, x, y + u, size, u * 3, u * 1.5f, RED);
                    r(c, x + u * 2, y + u * 5.5f, u * 2, u * 2, u / 2, INK);
                    r(c, x + u * 6, y + u * 5.5f, u * 2, u * 2, u / 2, INK);
                }
                case TAG -> {
                    r(c, x, y + u * 2, size, u * 6, u * 2, MANUAL_BLUE);
                    r(c, x + u * 1.5f, y + u * 4, u * 2, u * 2, u, Theme.CARD); // the hole for the string
                }
            }
            return c;
        }

        private static void coin(UIComponent c, float x, float y, float size) {
            float u = size / 10f;
            r(c, x, y, size, size, size / 2, GOLD_DARK);
            r(c, x + u, y + u, u * 8, u * 8, u * 4, GOLD);
            r(c, x + u * 4.25f, y + u * 3, u * 1.5f, u * 4, u / 2, GOLD_DARK); // the mark on its face
        }
    }

    /** Adds a rounded shape to a picture. A radius of nothing makes it square. */
    private static void r(UIComponent c, float x, float y, float width, float height, float radius, int color) {
        new UIRoundedRectangle(radius).setX(E.px(x)).setY(E.px(y)).setWidth(E.px(width)).setHeight(E.px(height))
                .setColor(E.color(color)).setChildOf(c);
    }

    static Icon of(Metric metric) {
        return switch (metric) {
            case NET_PER_HOUR -> Icon.COIN;
            case GROSS_PER_HOUR -> Icon.CHEST;
            case PURSE_PER_HOUR -> Icon.COINS;
            case ITEMS_PER_HOUR -> Icon.ITEMS;
            case XP_PER_HOUR -> Icon.ORB;
            case ACTIVITY_PER_HOUR -> Icon.TAG;
        };
    }

    /** Green for seen directly, amber for an estimate, blue for typed in, grey for not known. */
    static int color(Provenance provenance) {
        return switch (provenance) {
            case OBSERVED -> Theme.GOOD;
            case CALCULATED -> Theme.TEXT;
            case ESTIMATED -> Theme.WARN;
            case MANUAL -> MANUAL_BLUE;
            case UNAVAILABLE -> Theme.TEXT_MUTED;
        };
    }

    /** Green for a difference that held in every session, amber for one that did not, grey for too few to say. */
    static int color(Comparison.Verdict verdict) {
        return switch (verdict) {
            case CONSISTENT -> Theme.GOOD;
            case INCONCLUSIVE -> Theme.WARN;
            case PRELIMINARY, NO_DATA -> Theme.TEXT_MUTED;
        };
    }
}
