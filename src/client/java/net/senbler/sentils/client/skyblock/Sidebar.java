package net.senbler.sentils.client.skyblock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/** The scoreboard at the side of the screen, as lines of text. */
public final class Sidebar {

    private Sidebar() {}

    private static final Pattern FORMATTING_CODES = Pattern.compile("§.");

    /** The lines from the top down, without formatting codes. Empty if there is no scoreboard on show. */
    public static List<String> lines() {
        Minecraft mc = Minecraft.getInstance();
        List<String> lines = new ArrayList<>();
        if (mc.level == null) return lines;
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) return lines;
        List<PlayerScoreEntry> entries = new ArrayList<>(scoreboard.listPlayerScores(sidebar));
        // The highest score is drawn at the top.
        entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed());
        for (PlayerScoreEntry entry : entries) {
            // Hypixel keeps each line's text in the prefix and suffix of the entry's team.
            String line = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName())
                    .getString();
            lines.add(FORMATTING_CODES.matcher(line).replaceAll(""));
        }
        return lines;
    }
}
