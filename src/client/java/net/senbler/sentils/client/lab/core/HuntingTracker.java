package net.senbler.sentils.client.lab.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.senbler.sentils.client.lab.model.Counters;

/**
 * Follows hunting from chat: the shards gained and how each was come by. Shards go to the
 * Hunting Box, never through the inventory or the sacks, so chat is the only place they show.
 * Lines read:
 * <pre>
 * You caught a Birries Shard!
 * You caught x2 Voracious Spider Shards!
 * GOOD CATCH! You caught Water Snake Shard x3!                      (fished up, with an icon in front)
 * LOOT SHARE You received 2 Mossybit Shards for assisting Steve!
 * LOOT SHARE! You received 2x Parakeet Shard from Steve catching a Parakeet!
 * CHARM! You charmed the Haggard and received 3 Haggard Shards!
 * CAPTURE! You caught a Solsnatcher and gained 2x Solsnatcher Shard!
 * CAPTURE! You found Hideyho, and as a reward he gave you a Hideyho Shard!
 * FLOOR DROP! You found Litterbug Shard on the ground!
 * SHARD! Your contribution earned you the End Stone Protector Shard!
 * FUSION! You obtained Bolt Shard x2!
 * </pre>
 * A fusion is counted apart from the rest: it makes shards out of others rather than gaining any.
 */
public final class HuntingTracker {

    private static final String AMOUNT = "([\\d,]+)";
    private static final Pattern CAUGHT = Pattern.compile("^You caught (?:an? |x" + AMOUNT + " )?(.+?) Shards?!");
    private static final Pattern FISHED = Pattern.compile(
            "^\\P{L}*[A-Z]+ CATCH! You caught (?:an? )?(.+?) Shard(?: x" + AMOUNT + ")?!");
    private static final Pattern LOOT_SHARE = Pattern.compile(
            "^LOOT SHARE!? You received (?:a Rainbow Feather and )?(?:an? |" + AMOUNT + "x? )(.+?) Shards? (?:for assisting|from) ");
    private static final Pattern CHARMED = Pattern.compile(
            "^CHARM! You charmed .+? and received " + AMOUNT + " (.+?) Shards?!");
    private static final Pattern CAPTURED = Pattern.compile(
            "^CAPTURE! You .+?(?:gained|gave you|received) (?:a Rainbow Feather and )?(?:an? |" + AMOUNT + "x )(.+?) Shard\\b");
    private static final Pattern FLOOR_DROP = Pattern.compile(
            "^FLOOR DROP! You found (?:an? |" + AMOUNT + "x )?(.+?) Shard\\b");
    private static final Pattern BOSS = Pattern.compile("^SHARD! Your contribution earned you (?:the |an? )?(.+?) Shard!");
    private static final Pattern FUSED = Pattern.compile("^FUSION! You obtained (?:an? )?(.+?) Shard(?: x" + AMOUNT + ")?!");

    /** A shard's name as the Bazaar knows it: "Voracious Spider" is SHARD_VORACIOUS_SPIDER. */
    public static String itemId(String shard) {
        return "SHARD_" + shard.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
    }

    private static double number(String text) {
        if (text == null) return 1;
        try {
            return Double.parseDouble(text.replace(",", ""));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** Takes a line of chat from the server, without formatting codes. */
    public List<Count> chat(String text) {
        String line = text.trim();
        Matcher m;
        // The amount comes before the name in some lines and after it in others.
        if ((m = FISHED.matcher(line)).find()) return gained("Fished up", m.group(1), m.group(2));
        if ((m = CAUGHT.matcher(line)).find()) return gained("Caught", m.group(2), m.group(1));
        if ((m = LOOT_SHARE.matcher(line)).find()) return gained("Loot share", m.group(2), m.group(1));
        if ((m = CHARMED.matcher(line)).find()) return gained("Charmed", m.group(2), m.group(1));
        if ((m = CAPTURED.matcher(line)).find()) return gained("Safari capture", m.group(2), m.group(1));
        if ((m = FLOOR_DROP.matcher(line)).find()) return gained("Floor drop", m.group(2), m.group(1));
        if ((m = BOSS.matcher(line)).find()) return gained("Boss", m.group(1), null);
        if ((m = FUSED.matcher(line)).find()) {
            return List.of(Count.one(Counters.HUNTING_FUSIONS),
                    new Count(Counters.HUNTING_FUSED + m.group(1), number(m.group(2))));
        }
        return List.of();
    }

    private static List<Count> gained(String how, String shard, String amount) {
        double shards = number(amount);
        List<Count> counts = new ArrayList<>();
        counts.add(Count.one(Counters.HUNTING_CATCHES));
        counts.add(Count.one(Counters.HUNTING_SOURCE + how));
        counts.add(new Count(Counters.HUNTING_SHARDS, shards));
        counts.add(new Count(Counters.HUNTING_SHARD + shard, shards));
        return counts;
    }
}
