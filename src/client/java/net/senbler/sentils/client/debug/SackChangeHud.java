package net.senbler.sentils.client.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import net.senbler.sentils.client.gui.Theme;
import net.senbler.sentils.client.hud.HudWidget;

/**
 * Debug overlay showing what the last sack message was about. Hypixel reports items going into
 * and out of sacks with a line such as "[Sacks] +351 items. (Last 23s.)", and lists the items
 * themselves only in the text shown when the line is hovered over in chat. This shows the
 * items that text says were added; the removed items and everything else in it are left out.
 */
public final class SackChangeHud extends HudWidget {

    private static final int PAD = 4;
    private static final int LINE_H = 9;
    private static final int MAX_LINES = 30; // of the breakdown; a longer one is cut short
    private static final String PREFIX = "[Sacks]";
    private static final Pattern FORMATTING_CODES = Pattern.compile("§.");
    // An added item's line: "+64 Enchanted Cobblestone (Mining Sack)".
    private static final Pattern ADDED = Pattern.compile("^(\\+[\\d,]+)( .*)$");

    /** One line of text: an amount, drawn in the accent color, then the rest. The amount is empty for a note. */
    private record Line(String amount, String text) {}

    private static final String PLACEHOLDER_TITLE = "Sack changes";
    private static final List<Line> PLACEHOLDER = List.of(new Line("", "No [Sacks] message seen yet"));

    private static String title = PLACEHOLDER_TITLE; // the chat line itself
    private static List<Line> lines = PLACEHOLDER;

    public SackChangeHud() {
        super("sack_changes", "Sack changes", 0.008f, 0.4f);
    }

    /** Call once from your ClientModInitializer. */
    public static void init() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) onMessage(message);
        });
    }

    private static String plain(String text) {
        return FORMATTING_CODES.matcher(text).replaceAll("");
    }

    private static void onMessage(Component message) {
        String text = plain(message.getString()).trim();
        // At the very start, so that a player quoting the line in chat is not taken for it.
        if (!text.startsWith(PREFIX)) return;

        // The added and the removed items are each in the hover text of their own part of the line.
        List<String> hoverTexts = new ArrayList<>();
        message.visit((style, part) -> {
            if (style.getHoverEvent() instanceof HoverEvent.ShowText(Component value)) {
                String hover = plain(value.getString());
                if (!hoverTexts.contains(hover)) hoverTexts.add(hover);
            }
            return Optional.empty();
        }, Style.EMPTY);

        // The hover text also has headings, a note about settings, and prices if another mod adds them.
        List<Line> breakdown = new ArrayList<>();
        for (String hover : hoverTexts) {
            for (String line : hover.split("\n")) {
                Matcher m = ADDED.matcher(line.strip());
                if (m.matches()) breakdown.add(new Line(m.group(1), m.group(2)));
            }
        }
        if (breakdown.isEmpty()) breakdown.add(new Line("", "No items were added"));

        title = text;
        lines = breakdown;
    }

    @Override
    public boolean isSwitchedOn() {
        return DebugSettings.showSackChanges;
    }

    @Override
    public boolean isEnabled() {
        return isSwitchedOn();
    }

    /** How many of the breakdown's lines are drawn; one less than the limit if a "more" line is needed. */
    private static int shownLines() {
        return lines.size() > MAX_LINES ? MAX_LINES - 1 : lines.size();
    }

    private static String moreLine() {
        int hidden = lines.size() - shownLines();
        return hidden > 0 ? "... " + hidden + " more lines" : null;
    }

    @Override
    public int width(Font font) {
        int width = font.width(title);
        for (int i = 0; i < shownLines(); i++) {
            Line line = lines.get(i);
            width = Math.max(width, font.width(line.amount() + line.text()));
        }
        String more = moreLine();
        if (more != null) width = Math.max(width, font.width(more));
        return width + PAD * 2;
    }

    @Override
    public int height(Font font) {
        int count = 1 + shownLines() + (moreLine() != null ? 1 : 0);
        return count * LINE_H - 1 + PAD * 2;
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, Font font) {
        Theme.roundRect(graphics, 0, 0, width(font), height(font), Theme.HUD_BACKDROP);
        graphics.text(font, title, PAD, PAD, Theme.ACCENT, false);

        int y = PAD + LINE_H;
        for (int i = 0; i < shownLines(); i++) {
            Line line = lines.get(i);
            if (line.amount().isEmpty()) {
                graphics.text(font, line.text(), PAD, y, Theme.TEXT_MUTED, false);
            } else {
                graphics.text(font, line.amount(), PAD, y, Theme.ACCENT, false);
                graphics.text(font, line.text(), PAD + font.width(line.amount()), y, Theme.TEXT, false);
            }
            y += LINE_H;
        }
        String more = moreLine();
        if (more != null) graphics.text(font, more, PAD, y, Theme.TEXT_MUTED, false);
    }
}
