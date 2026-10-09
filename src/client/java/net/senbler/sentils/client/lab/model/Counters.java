package net.senbler.sentils.client.lab.model;

/**
 * The names of the things counted for an activity, as they are saved in a session. A name ending
 * in a dot is the start of a family: one count for each sea creature, say, with the creature's
 * name after the dot.
 */
public final class Counters {

    private Counters() {}

    /** Everything reeled in: fish, treasure and sea creatures alike. */
    public static final String FISHING_CATCHES = "fishing.catches";
    public static final String FISHING_SEA_CREATURES = "fishing.sea_creatures";
    public static final String FISHING_DOUBLE_HOOKS = "fishing.double_hooks";
    public static final String FISHING_CREATURE = "fishing.creature.";
    public static final String FISHING_TROPHY_FISH = "fishing.trophy_fish";
    public static final String FISHING_TROPHY_TIER = "fishing.trophy.";
    public static final String FISHING_TREASURES = "fishing.treasures";
    public static final String FISHING_TREASURE_COINS = "fishing.treasure_coins";

    public static final String DUNGEONS_RUNS = "dungeons.runs";
    /** Runs by floor: "F7", "M3", "Entrance". */
    public static final String DUNGEONS_FLOOR = "dungeons.floor.";
    /** Team scores added up, and how many there were, for the usual score. */
    public static final String DUNGEONS_SCORE = "dungeons.score";
    public static final String DUNGEONS_SCORES = "dungeons.scores";
    public static final String DUNGEONS_GRADE = "dungeons.grade.";
    /** Run times added up, and how many there were, for the usual time. */
    public static final String DUNGEONS_RUN_MILLIS = "dungeons.run_millis";
    public static final String DUNGEONS_RUNS_TIMED = "dungeons.runs_timed";
    public static final String DUNGEONS_CATACOMBS_XP = "dungeons.catacombs_xp";
    public static final String DUNGEONS_CLASS_XP = "dungeons.class_xp.";
    public static final String DUNGEONS_DEATHS = "dungeons.deaths";
    public static final String DUNGEONS_TEAM_DEATHS = "dungeons.team_deaths";
    /** Secrets the whole team found, as the server counts them, and those of them put down to the player. */
    public static final String DUNGEONS_SECRETS = "dungeons.secrets";
    public static final String DUNGEONS_OWN_SECRETS = "dungeons.own_secrets";

    /** Blocks the player broke during a farming session. */
    public static final String FARMING_BLOCKS = "farming.blocks";
    /** Crops harvested, going by the count the farming tool keeps. */
    public static final String FARMING_CROPS = "farming.crops";
    public static final String FARMING_PESTS_SPAWNED = "farming.pests_spawned";
    public static final String FARMING_PESTS = "farming.pests";
    public static final String FARMING_PEST = "farming.pest.";
    public static final String FARMING_RARE_CROPS = "farming.rare_crops";
    public static final String FARMING_RARE_CROP = "farming.rare_crop.";
    public static final String FARMING_PEST_DROPS = "farming.pest_drops";
    public static final String FARMING_PEST_DROP = "farming.pest_drop.";
    public static final String FARMING_VISITORS = "farming.visitors";
    public static final String FARMING_GARDEN_XP = "farming.garden_xp";
    /** Gained, going by what the scoreboard shows held. */
    public static final String FARMING_COPPER = "farming.copper";
    public static final String FARMING_SOWDUST = "farming.sowdust";

    /** Powder gained of every kind, going by what the tab list shows held, and of each kind. */
    public static final String MINING_POWDER = "mining.powder";
    public static final String MINING_POWDER_KIND = "mining.powder_kind.";
    /** Blocks mined, going by the count a tool with Compact keeps. */
    public static final String MINING_BLOCKS = "mining.blocks";
    public static final String MINING_COMMISSIONS = "mining.commissions";
    public static final String MINING_COMMISSION = "mining.commission.";
    public static final String MINING_CHESTS = "mining.chests";
    public static final String MINING_PICKOBULUS = "mining.pickobulus";
    public static final String MINING_PICKOBULUS_BLOCKS = "mining.pickobulus_blocks";
    public static final String MINING_COMPACT = "mining.compact";
    public static final String MINING_COMPACT_ITEM = "mining.compact_item.";
    public static final String MINING_PRISTINE = "mining.pristine";
    /** How many of each gemstone Pristine gave. */
    public static final String MINING_PRISTINE_ITEM = "mining.pristine_item.";
    public static final String MINING_NUCLEUS_RUNS = "mining.nucleus_runs";
    public static final String MINING_MINESHAFTS = "mining.mineshafts";

    /** Trees cut down that gave a gift, and each kind of tree. */
    public static final String FORAGING_TREES = "foraging.trees";
    public static final String FORAGING_TREE = "foraging.tree.";
    /** The player's share of each tree, as a percentage, added up: divide by the trees for the usual share. */
    public static final String FORAGING_SHARE_PERCENT = "foraging.share_percent";
    /** Whispers gained in any way, going by the total held on the scoreboard. */
    public static final String FORAGING_WHISPERS = "foraging.whispers";
    public static final String FORAGING_REWARDS = "foraging.rewards";
    /** How much of each reward the gifts held, by its name: whispers, XP, essence and the rest. */
    public static final String FORAGING_REWARD = "foraging.reward.";
    public static final String FORAGING_BONUS_GIFTS = "foraging.bonus_gifts";
    public static final String FORAGING_BONUS = "foraging.bonus.";
    public static final String FORAGING_MOBS = "foraging.mobs";
    public static final String FORAGING_MOB = "foraging.mob.";

    public static final String SLAYER_STARTED = "slayer.started";
    public static final String SLAYER_COMPLETED = "slayer.completed";
    public static final String SLAYER_FAILED = "slayer.failed";
    public static final String SLAYER_BOSS = "slayer.boss.";
    public static final String SLAYER_XP = "slayer.xp";
    public static final String SLAYER_RNG_METER_XP = "slayer.rng_meter_xp";
    public static final String SLAYER_MINIBOSSES = "slayer.minibosses";
    /** Time from starting a quest to its boss appearing, added up, and how many times that was timed. */
    public static final String SLAYER_SPAWN_MILLIS = "slayer.spawn_millis";
    public static final String SLAYER_SPAWNS_TIMED = "slayer.spawns_timed";
    /** Time from a boss appearing to its being killed, added up, and how many times that was timed. */
    public static final String SLAYER_KILL_MILLIS = "slayer.kill_millis";
    public static final String SLAYER_KILLS_TIMED = "slayer.kills_timed";
    /** Coins auto-slayer took from the bank, which the purse never shows. */
    public static final String SLAYER_BANK_COINS = "slayer.bank_coins";
}
