package net.senbler.sentils.client.farming;

import java.util.List;

/**
 * What the fortune script hands back, read straight from its JSON; the field names here match
 * the ones written by tools/fortune/adapter.js.
 */
public final class FortuneData {

    private FortuneData() {}

    /** What is known about a profile before any crop is looked at. */
    public static final class Summary {
        public List<CropName> crops = List.of();
        // False when the player has that part of their Hypixel API switched off.
        public boolean hasGarden;
        public boolean hasInventory;
        public boolean hasSkills;
    }

    public static final class CropName {
        public String id = "";
        public String name = "";
    }

    /** Everything shown for one crop. */
    public static final class View {
        public String crop = "";
        public String cropName = "";
        public String cropStatName = "";
        public double fortune;
        public List<Row> breakdown = List.of();
        public List<Row> cropBreakdown = List.of();
        public int gardenLevel;
        public int plots;
        public int farmingLevel;
        public int milestone;
        public int cropUpgrade;
        public Item tool; // null when the player has no tool for the crop
        public Pet pet; // null when the player has no farming pet
        public List<Gear> gear = List.of();
        public List<Progress> cropProgress = List.of();
        public List<Progress> gearProgress = List.of();
        public List<Progress> generalProgress = List.of();
        public List<Progress> petProgress = List.of();
        public Rates rates = new Rates();
        public List<Upgrade> upgrades = List.of();
    }

    /** A named amount, such as one line of a fortune breakdown. */
    public static final class Row {
        public String name = "";
        public double value;
        public String stat = "";
    }

    public static final class Item {
        public String name = "";
        public double fortune;
    }

    public static final class Pet {
        public String name = "";
        public String rarity = "";
        public int level;
        public double fortune;
    }

    public static final class Gear {
        public String slot = "";
        public String name = "";
        public double fortune;
    }

    /** How far along one fortune source is, possibly made up of smaller sources. */
    public static final class Progress {
        public String name = "";
        public String source = "";
        public double current;
        public double max;
        public double ratio;
        public List<StatProgress> stats = List.of();
        public boolean api = true; // false for sources Hypixel's API does not report
        public boolean active = true;
        public String note = "";
        public List<Progress> children = List.of();
    }

    public static final class StatProgress {
        public String name = "";
        public double current;
        public double max;
        public double ratio;
    }

    public static final class Upgrade {
        public String title = "";
        public String onto = "";
        public double increase; // fortune gained for the crop being viewed
        public List<StatAmount> stats = List.of();
        public String action = "";
        public String category = "";
        public String cost = "";
        public boolean api = true;
        public int repeatable;
    }

    public static final class StatAmount {
        public String name = "";
        public double amount;
    }

    /** Expected gains from an hour of farming the crop. */
    public static final class Rates {
        public double blocksPerSecond;
        public long npcCoins;
        public List<Row> coinSources = List.of();
        public long collection;
        public List<Row> otherCollection = List.of();
        public double weight;
        public List<Effect> effects = List.of();
    }

    public static final class Effect {
        public String source = "";
        public String value = "";
        public String detail = "";
    }
}
