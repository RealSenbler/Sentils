package net.senbler.sentils.client.lab.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.senbler.sentils.client.lab.model.Counters;

/**
 * Works out what was fished up from what the server shows and plays. Lines read, with or without
 * an icon in front:
 * <pre>
 * A Squid appeared.
 * You caught a Sea Walker.
 * It's a Double Hook! Woot woot!
 * TROPHY FISH! You caught Blobfish BRONZE!
 * GOOD CATCH! You caught 36,064 Coins!
 * </pre>
 * Every sea creature has a line of its own wording, and the wording is changed now and then, so
 * a creature is known by its name being in a line the server wrote while a rod was in hand, not
 * by the whole line. The names are from the Hypixel SkyBlock Wiki's list of sea creatures.
 * <p>
 * A fish or a treasure gets no line. It is known by the sound the server plays for it, taken
 * together with the player's bobber coming out of the water at the same moment.
 */
public final class FishingTracker {

    private static final String[] CREATURES = {
            // Water, anywhere
            "Squid", "Sea Walker", "Sea Guardian", "Sea Witch", "Sea Archer", "Rider of the Deep", "Catfish",
            "Sea Leech", "Guardian Defender", "Deep Sea Protector", "Water Hydra", "Sea Emperor", "Loch Emperor",
            "Night Squid", "Carrot King", "Agarimoo",
            // Backwater Bayou, Lotus Atoll, Moonglade Marsh, Torrhus Canyon
            "Trash Gobbler", "Dumpster Diver", "Banshee", "Bayou Sludge", "Alligator", "Titanoboa",
            "Atoll Croaker", "Lotus Guardian", "gorF", "Drowned Captain", "Puddle Jumper", "Frog Prince",
            "Bogged", "Wetwing", "Tadgang", "Ent", "Nessie", "Stridersurfer",
            "Haggard", "Brineling", "Sprawl", "Torrid", "Silkbreeze", "Giant Isopod",
            // Lava
            "Magma Slug", "Moogma", "Lava Leech", "Pyroclastic Worm", "Lava Flame", "Fire Eel", "Taurus", "Thunder",
            "Fiery Scuttler", "Lord Jawbus", "Fried Chicken", "Volcanic Snail", "Fireproof Witch", "Magma Pillar",
            "Ragnarok", "Plhlegblast",
            // Events
            "Frozen Steve", "Frosty", "Grinch", "Nutcracker", "Yeti", "Reindrake",
            "Jumpin' Jack", "Scarecrow", "Nightmare", "Werewolf", "Phantom Fisher", "Grim Reaper",
            "Nurse Shark", "Blue Shark", "Tiger Shark", "Great White Shark",
            // Hotspots
            "Frog Man", "Inkling", "Snapping Turtle", "Manta Ray", "Blue Ringed Octopus", "Wiki Tiki",
            // Crystal Hollows, Dwarven Mines, Mushroom Desert
            "Water Worm", "Poisoned Water Worm", "Abyssal Miner", "Flaming Worm", "Lava Blaze", "Lava Pigman",
            "Mithril Grubber", "Oasis Sheep", "Oasis Rabbit",
    };

    private static final Pattern CREATURE = creaturePattern();
    private static final Pattern DOUBLE_HOOK = Pattern.compile("^\\P{L}{0,4}It's a Double Hook!");
    private static final Pattern TROPHY = Pattern.compile(
            "^\\P{L}{0,4}TROPHY FISH! You caught (?:an? )?(.+?) (BRONZE|SILVER|GOLD|DIAMOND)\\b");
    private static final Pattern TREASURE = Pattern.compile(
            "^\\P{L}{0,4}(?:GOOD|GREAT|OUTSTANDING) CATCH! You (?:caught|found) (?:([\\d,]+) Coins)?");
    // A line the server heads with words in capitals, such as RARE DROP! or BESTIARY, is about something else.
    private static final Pattern HEADED = Pattern.compile("^\\P{L}{0,4}[A-Z][A-Z' ]{3,}\\b");
    private static final int LONGEST_LINE = 120;
    private static final long DOUBLE_HOOK_MILLIS = 3000;
    private static final long CATCH_MILLIS = 1000;

    private long doubleHookMillis; // when the double hook line came, or 0
    private long soundMillis; // when the catch sound was last played and not yet matched, or 0
    private long reeledMillis; // when the bobber last came in and was not yet matched, or 0

    /** Longest name first, so that a Poisoned Water Worm is not taken for a Water Worm. */
    private static Pattern creaturePattern() {
        List<String> names = new ArrayList<>(List.of(CREATURES));
        names.sort(Comparator.comparingInt(String::length).reversed());
        StringBuilder any = new StringBuilder();
        for (String name : names) any.append(any.isEmpty() ? "" : "|").append(Pattern.quote(name));
        return Pattern.compile("(?<!\\p{L})(" + any + ")(?!\\p{L})");
    }

    /** Forgets anything half seen, as when recording stops or the world changes. */
    public void reset() {
        this.doubleHookMillis = 0;
        this.soundMillis = 0;
        this.reeledMillis = 0;
    }

    /** The sea creature a line is about, or null if it is not the server announcing one being fished up. */
    public static String seaCreature(String line) {
        if (line.length() > LONGEST_LINE || line.contains(": ") || line.contains("➜")) return null;
        if (HEADED.matcher(line).find() || line.chars().anyMatch(Character::isDigit)) return null;
        Matcher m = CREATURE.matcher(line);
        return m.find() ? m.group(1) : null;
    }

    /** Takes a line of chat from the server, without formatting codes. */
    public List<Count> chat(String text, long millis, boolean holdingRod) {
        String line = text.trim();
        List<Count> counts = new ArrayList<>();
        Matcher m;
        if (DOUBLE_HOOK.matcher(line).find()) {
            this.doubleHookMillis = millis;
        } else if ((m = TROPHY.matcher(line)).find()) {
            counts.add(Count.one(Counters.FISHING_TROPHY_FISH));
            counts.add(Count.one(Counters.FISHING_TROPHY_TIER + m.group(2)));
        } else if ((m = TREASURE.matcher(line)).find()) {
            counts.add(Count.one(Counters.FISHING_TREASURES));
            if (m.group(1) != null) {
                counts.add(new Count(Counters.FISHING_TREASURE_COINS, Double.parseDouble(m.group(1).replace(",", ""))));
            }
        } else if (holdingRod) {
            String creature = seaCreature(line);
            if (creature == null) return counts;
            // The double hook line comes just ahead of the creature's, and means two of it.
            boolean doubled = this.doubleHookMillis != 0 && millis - this.doubleHookMillis <= DOUBLE_HOOK_MILLIS;
            this.doubleHookMillis = 0;
            counts.add(Count.one(Counters.FISHING_CATCHES));
            counts.add(new Count(Counters.FISHING_SEA_CREATURES, doubled ? 2 : 1));
            counts.add(new Count(Counters.FISHING_CREATURE + creature, doubled ? 2 : 1));
            if (doubled) counts.add(Count.one(Counters.FISHING_DOUBLE_HOOKS));
        }
        return counts;
    }

    /** Takes the catch sound being played while a rod is in hand. */
    public List<Count> sound(long millis) {
        this.soundMillis = millis;
        return match();
    }

    /** Takes the player's bobber coming in. */
    public List<Count> reeled(long millis) {
        this.reeledMillis = millis;
        return match();
    }

    /** A catch, if the sound and the bobber coming in fell together, whichever was seen first. */
    private List<Count> match() {
        if (this.soundMillis == 0 || this.reeledMillis == 0) return List.of();
        boolean together = Math.abs(this.soundMillis - this.reeledMillis) <= CATCH_MILLIS;
        if (together) {
            this.soundMillis = 0;
            this.reeledMillis = 0;
            return List.of(Count.one(Counters.FISHING_CATCHES));
        }
        // The older of the two has nothing to do with the newer: keep only the newer.
        if (this.soundMillis < this.reeledMillis) this.soundMillis = 0;
        else this.reeledMillis = 0;
        return List.of();
    }
}
