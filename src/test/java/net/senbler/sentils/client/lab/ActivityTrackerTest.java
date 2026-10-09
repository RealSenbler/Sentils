package net.senbler.sentils.client.lab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import net.senbler.sentils.client.lab.analysis.ActivityStats;
import net.senbler.sentils.client.lab.analysis.Metric;
import net.senbler.sentils.client.lab.analysis.SessionReport;
import net.senbler.sentils.client.lab.core.Count;
import net.senbler.sentils.client.lab.core.DungeonTracker;
import net.senbler.sentils.client.lab.core.FarmingTracker;
import net.senbler.sentils.client.lab.core.FishingTracker;
import net.senbler.sentils.client.lab.core.ForagingTracker;
import net.senbler.sentils.client.lab.core.MiningTracker;
import net.senbler.sentils.client.lab.core.SecretTracker;
import net.senbler.sentils.client.lab.core.SessionRecorder;
import net.senbler.sentils.client.lab.core.SlayerTracker;
import net.senbler.sentils.client.lab.model.Activity;
import net.senbler.sentils.client.lab.model.Counters;
import net.senbler.sentils.client.lab.model.Session;

/** What is counted of an activity itself: sea creatures fished up, slayer bosses killed. */
class ActivityTrackerTest {

    private static final long T = Fixtures.START;

    /** Everything a run of lines and readings added up to, by counter. */
    private static Map<String, Double> sum(List<List<Count>> all) {
        Map<String, Double> totals = new HashMap<>();
        for (List<Count> counts : all) {
            for (Count count : counts) totals.merge(count.key(), count.amount(), Double::sum);
        }
        return totals;
    }

    @Test
    void aSeaCreatureIsKnownByItsNameInTheServersLine() {
        // Lines as the Hypixel SkyBlock Wiki gives them for each creature.
        assertEquals("Squid", FishingTracker.seaCreature("A Squid appeared."));
        assertEquals("Sea Walker", FishingTracker.seaCreature("You caught a Sea Walker."));
        assertEquals("Sea Witch", FishingTracker.seaCreature(
                "It looks like you've disrupted the Sea Witch's brewing session. Watch out, she's furious!"));
        assertEquals("Catfish", FishingTracker.seaCreature("Huh? A Catfish!"));
        assertEquals("Guardian Defender", FishingTracker.seaCreature("You've discovered a Guardian Defender of the sea."));
        assertEquals("Thunder", FishingTracker.seaCreature("You hear a massive rumble as Thunder emerges."));
        assertEquals("Lord Jawbus", FishingTracker.seaCreature("You have angered a legendary creature... Lord Jawbus has arrived."));
        assertEquals("Loch Emperor", FishingTracker.seaCreature("The Loch Emperor arises from the depths."));
        // The longer name wins where one name is inside another.
        assertEquals("Night Squid", FishingTracker.seaCreature("Pitch darkness reveals a Night Squid."));
        assertEquals("Poisoned Water Worm", FishingTracker.seaCreature("A Poisoned Water Worm surfaces!"));
    }

    @Test
    void linesThatOnlyNameACreatureAreNotCatches() {
        assertNull(FishingTracker.seaCreature("Party > [MVP+] Steve: A Squid appeared."));
        assertNull(FishingTracker.seaCreature("RARE DROP! Squid Boots"));
        assertNull(FishingTracker.seaCreature("BESTIARY Sea Walker 4➜5"));
        assertNull(FishingTracker.seaCreature("Your Squid leveled up to level 50!"));
        assertNull(FishingTracker.seaCreature("Thunderlord struck the enemy."));
        assertNull(FishingTracker.seaCreature("You caught a fish."));
    }

    @Test
    void seaCreaturesAreCountedByKindAndADoubleHookCountsTwo() {
        FishingTracker fishing = new FishingTracker();
        Map<String, Double> totals = sum(List.of(
                fishing.chat("A Squid appeared.", T, true),
                fishing.chat("It's a Double Hook! Woot woot!", T + 5000, true),
                fishing.chat("You caught a Sea Walker.", T + 5100, true),
                fishing.chat("A Squid appeared.", T + 9000, true)));

        assertEquals(3, totals.get(Counters.FISHING_CATCHES), "a double hook is one catch");
        assertEquals(4, totals.get(Counters.FISHING_SEA_CREATURES));
        assertEquals(2, totals.get(Counters.FISHING_CREATURE + "Squid"));
        assertEquals(2, totals.get(Counters.FISHING_CREATURE + "Sea Walker"));
        assertEquals(1, totals.get(Counters.FISHING_DOUBLE_HOOKS));
    }

    @Test
    void aCreaturesLineCountsOnlyWithARodInHandAndAnOldDoubleHookIsForgotten() {
        FishingTracker fishing = new FishingTracker();
        assertTrue(fishing.chat("A Squid appeared.", T, false).isEmpty());

        fishing.chat("It's a Double Hook!", T, true);
        Map<String, Double> late = sum(List.of(fishing.chat("A Squid appeared.", T + 60_000, true)));
        assertEquals(1, late.get(Counters.FISHING_SEA_CREATURES));
        assertNull(late.get(Counters.FISHING_DOUBLE_HOOKS));
    }

    @Test
    void trophyFishAndTreasureAreReadWithOrWithoutAnIcon() {
        FishingTracker fishing = new FishingTracker();
        Map<String, Double> totals = sum(List.of(
                fishing.chat(" TROPHY FISH! You caught Blobfish BRONZE!", T, true),
                fishing.chat("TROPHY FISH! You caught a Lavahorse SILVER!", T, true),
                fishing.chat("⛃ GOOD CATCH! You caught 36,064 Coins!", T, true),
                fishing.chat("GREAT CATCH! You found a Fish Bait.", T, true),
                fishing.chat("[MVP+] Steve: TROPHY FISH! You caught Blobfish BRONZE!", T, true)));

        assertEquals(2, totals.get(Counters.FISHING_TROPHY_FISH));
        assertEquals(1, totals.get(Counters.FISHING_TROPHY_TIER + "BRONZE"));
        assertEquals(1, totals.get(Counters.FISHING_TROPHY_TIER + "SILVER"));
        assertEquals(2, totals.get(Counters.FISHING_TREASURES));
        assertEquals(36_064, totals.get(Counters.FISHING_TREASURE_COINS));
    }

    @Test
    void aFishIsACatchWhenTheSoundAndTheBobberComingInFallTogether() {
        FishingTracker fishing = new FishingTracker();
        // Either may be seen first.
        assertTrue(fishing.sound(T).isEmpty());
        assertEquals(List.of(Count.one(Counters.FISHING_CATCHES)), fishing.reeled(T + 100));
        assertTrue(fishing.reeled(T + 10_000).isEmpty());
        assertEquals(List.of(Count.one(Counters.FISHING_CATCHES)), fishing.sound(T + 10_050));

        // The same sound long after reeling in an empty line is something else.
        assertTrue(fishing.reeled(T + 20_000).isEmpty());
        assertTrue(fishing.sound(T + 30_000).isEmpty());
        assertTrue(fishing.sound(T + 30_500).isEmpty(), "one reel is not matched twice");
    }

    private static List<String> board(String state) {
        return List.of("SKYBLOCK", " Late Summer 3rd", " ⏣ Graveyard", "Purse: 1,000",
                "Slayer Quest🍫", "Revenant Horror V", state, "www.hypixel.net");
    }

    @Test
    void aSlayerQuestIsFollowedFromStartToKill() {
        SlayerTracker slayer = new SlayerTracker();
        Map<String, Double> totals = sum(List.of(
                slayer.chat("  SLAYER QUEST STARTED!", T),
                slayer.scoreboard(board(" (1,200/2,400) Combat XP"), T + 1000),
                slayer.scoreboard(board("Slay the boss!"), T + 60_000),
                slayer.scoreboard(board("Slay the boss!"), T + 60_500),
                slayer.scoreboard(board("Boss slain!"), T + 75_000),
                slayer.chat("  SLAYER QUEST COMPLETE!", T + 75_200)));

        assertEquals(1, totals.get(Counters.SLAYER_STARTED));
        assertEquals(1, totals.get(Counters.SLAYER_COMPLETED));
        assertEquals(1, totals.get(Counters.SLAYER_BOSS + "Revenant Horror V"));
        assertEquals(60_000, totals.get(Counters.SLAYER_SPAWN_MILLIS));
        assertEquals(15_000, totals.get(Counters.SLAYER_KILL_MILLIS));
        assertEquals(1, totals.get(Counters.SLAYER_KILLS_TIMED), "the kill is timed once, not by chat as well");
    }

    @Test
    void chatSayingTheBossIsDeadFirstDoesNotStartItAgain() {
        SlayerTracker slayer = new SlayerTracker();
        Map<String, Double> totals = sum(List.of(
                slayer.chat("SLAYER QUEST STARTED!", T),
                slayer.scoreboard(board("Slay the boss!"), T + 30_000),
                slayer.chat("SLAYER QUEST COMPLETE!", T + 40_000),
                // Auto-slayer starts the next one while the scoreboard still shows the last.
                slayer.chat("SLAYER QUEST STARTED!", T + 40_100),
                slayer.scoreboard(board("Slay the boss!"), T + 40_500),
                slayer.scoreboard(board("(0/2,400) Combat XP"), T + 41_000),
                slayer.scoreboard(board("Slay the boss!"), T + 100_100)));

        assertEquals(10_000, totals.get(Counters.SLAYER_KILL_MILLIS));
        assertEquals(1, totals.get(Counters.SLAYER_KILLS_TIMED));
        assertEquals(2, totals.get(Counters.SLAYER_SPAWNS_TIMED));
        assertEquals(30_000 + 60_000, totals.get(Counters.SLAYER_SPAWN_MILLIS));
    }

    @Test
    void slayerXpIsTheTiersUsualXpUntilTwoLevelLinesGiveItExactly() {
        SlayerTracker slayer = new SlayerTracker();
        slayer.scoreboard(board("Slay the boss!"), T);
        Map<String, Double> totals = sum(List.of(
                // The first quest: nothing to take its level line from, so the tier's 1,500.
                slayer.chat("SLAYER QUEST COMPLETE!", T + 1000),
                slayer.chat("   Zombie Slayer LVL 8 - Next LVL in 100,000 XP!", T + 1001),
                // The second, with a quarter more from a bonus, is taken from the two lines.
                slayer.chat("SLAYER QUEST COMPLETE!", T + 60_000),
                slayer.chat("   Zombie Slayer LVL 8 - Next LVL in 98,125 XP!", T + 60_001),
                // The third reaches the next level, where the lines cannot be compared.
                slayer.chat("SLAYER QUEST COMPLETE!", T + 120_000),
                slayer.chat("   Zombie Slayer LVL 9 - Next LVL in 500,000 XP!", T + 120_001)));
        assertEquals(1500 + 1875 + 1500, totals.get(Counters.SLAYER_XP));
    }

    @Test
    void aQuestIsFollowedFromChatAloneAsTheGameWritesIt() {
        // Lines as captured in game, with the scoreboard never showing the boss.
        SlayerTracker slayer = new SlayerTracker();
        List<String> grinding = List.of("Slayer Quest", "Revenant Horror IV", " 4/51 Kills");
        Map<String, Double> totals = sum(List.of(
                slayer.chat("   RNG Meter - 1,275,585 Stored XP", T - 1000),
                slayer.chat("  SLAYER QUEST STARTED!", T),
                slayer.chat("   » Slay 5,000 Combat XP worth of Zombies.", T),
                slayer.scoreboard(grinding, T + 500),
                slayer.chat("SLAYER MINIBOSS! The Revenant Champion spawned!", T + 5000),
                slayer.chat("SLAYER MINIBOSS! The Revenant Champion spawned!", T + 9000),
                slayer.chat("SLAYER BOSS! The Revenant Horror IV spawned!", T + 20_000),
                slayer.chat("  SLAYER QUEST COMPLETE!", T + 28_000),
                slayer.chat("   Zombie Slayer LVL 8 - Next LVL in 191,850 XP!", T + 28_000),
                slayer.chat("   RNG Meter - 1,276,210 Stored XP", T + 28_000),
                slayer.chat("  SLAYER QUEST STARTED!", T + 28_100)));

        assertEquals(2, totals.get(Counters.SLAYER_MINIBOSSES));
        assertEquals(20_000, totals.get(Counters.SLAYER_SPAWN_MILLIS));
        assertEquals(8_000, totals.get(Counters.SLAYER_KILL_MILLIS));
        assertEquals(1, totals.get(Counters.SLAYER_BOSS + "Revenant Horror IV"));
        assertEquals(500, totals.get(Counters.SLAYER_XP));
        assertEquals(625, totals.get(Counters.SLAYER_RNG_METER_XP));
    }

    @Test
    void slayerXpIsStillCountedWhereNoLevelLineFollows() {
        SlayerTracker slayer = new SlayerTracker();
        slayer.scoreboard(board("Slay the boss!"), T);
        Map<String, Double> totals = sum(List.of(
                slayer.chat("SLAYER QUEST COMPLETE!", T + 1000),
                slayer.chat("   Zombie Slayer LVL 9 - LVL MAXED OUT!", T + 1001),
                slayer.scoreboard(board("Boss slain!"), T + 1500),
                slayer.scoreboard(board("Boss slain!"), T + 4000),
                slayer.scoreboard(board("Boss slain!"), T + 4500)));
        assertEquals(1500, totals.get(Counters.SLAYER_XP));
    }

    @Test
    void failedQuestsAndBankPaymentsAreCountedAndQuotesAreNot() {
        SlayerTracker slayer = new SlayerTracker();
        Map<String, Double> totals = sum(List.of(
                slayer.chat("SLAYER QUEST STARTED!", T),
                slayer.chat("  SLAYER QUEST FAILED!", T + 1000),
                slayer.chat("Took 1.9k coins from your bank for auto-slayer...", T + 2000),
                slayer.chat("Party > Steve: SLAYER QUEST COMPLETE!", T + 3000)));

        assertEquals(1, totals.get(Counters.SLAYER_FAILED));
        assertEquals(1900, totals.get(Counters.SLAYER_BANK_COINS), 1e-6);
        assertNull(totals.get(Counters.SLAYER_COMPLETED));
    }

    @Test
    void aTreeGiftIsReadWithItsRewardsItsBonusAndWhatFellOutOfTheTree() {
        ForagingTracker foraging = new ForagingTracker();
        String bar = "▬".repeat(64);
        List<String> none = List.of();
        Map<String, Double> totals = sum(List.of(
                foraging.chat(bar, none, T),
                foraging.chat("                                TREE GIFT", none, T),
                foraging.chat("                 You helped cut 100% of the Fig Tree.", none, T),
                foraging.chat("                       +5 rewards gained! (hover)", List.of(
                        "Forest Essence x4\nForest Whispers x40\nForaging Experience x1,000\nHOTF Experience x10\n"
                                + "Tender Wood x0-2\nSignal Enhancer (0.4%)"), T),
                foraging.chat("                                BONUS GIFT", none, T),
                foraging.chat("                          Stretching Sticks (20%)", none, T),
                foraging.chat("          Enchanted Book (First Impression I) (0.4%)", none, T),
                foraging.chat("                     A Phanflare fell from the Tree!", none, T),
                foraging.chat(bar, none, T),
                // A second tree, shared with others, and nothing more from it.
                foraging.chat("You helped cut 15.2% of the Mangrove Tree.", none, T + 9000),
                foraging.chat("+0 rewards gained!", none, T + 9000)));

        assertEquals(2, totals.get(Counters.FORAGING_TREES));
        assertEquals(1, totals.get(Counters.FORAGING_TREE + "Fig"));
        assertEquals(1, totals.get(Counters.FORAGING_TREE + "Mangrove"));
        assertEquals(115.2, totals.get(Counters.FORAGING_SHARE_PERCENT), 1e-9);
        assertEquals(5, totals.get(Counters.FORAGING_REWARDS));
        assertEquals(40, totals.get(Counters.FORAGING_REWARD + "Forest Whispers"));
        assertEquals(1000, totals.get(Counters.FORAGING_REWARD + "Foraging Experience"));
        assertEquals(10, totals.get(Counters.FORAGING_REWARD + "HOTF Experience"));
        assertEquals(2, totals.get(Counters.FORAGING_REWARD + "Tender Wood"));
        assertEquals(1, totals.get(Counters.FORAGING_REWARD + "Signal Enhancer"));
        assertEquals(2, totals.get(Counters.FORAGING_BONUS_GIFTS));
        assertEquals(1, totals.get(Counters.FORAGING_BONUS + "Stretching Sticks"));
        assertEquals(1, totals.get(Counters.FORAGING_MOB + "Phanflare"));
    }

    @Test
    void aTreeGiftIsReadAsCapturedInTheTorrhusCanyon() {
        ForagingTracker foraging = new ForagingTracker();
        List<String> none = List.of();
        Map<String, Double> totals = sum(List.of(
                foraging.chat("                                 TREE GIFT", none, T),
                foraging.chat("                You helped cut 100% of the Helix Tree.", none, T),
                foraging.chat("                       +3 rewards gained! (hover)",
                        List.of("Forest Essence x24\nForaging Experience x9,000\nDesert Whispers x920"), T),
                foraging.chat("                      A Firefox fell from the Tree!", none, T)));

        assertEquals(1, totals.get(Counters.FORAGING_TREE + "Helix"));
        assertEquals(24, totals.get(Counters.FORAGING_REWARD + "Forest Essence"));
        assertEquals(920, totals.get(Counters.FORAGING_REWARD + "Desert Whispers"));
        assertEquals(9000, totals.get(Counters.FORAGING_REWARD + "Foraging Experience"));
        assertEquals(1, totals.get(Counters.FORAGING_MOB + "Firefox"));
    }

    @Test
    void whispersAreWhatTheTotalOnTheScoreboardRisesBy() {
        ForagingTracker foraging = new ForagingTracker();
        Map<String, Double> totals = sum(List.of(
                foraging.scoreboard(List.of("  Torrhus Canyon", "Whispers: 328.2k (+39)", "Bits: 91,627")),
                // The purse takes the line's place between gains.
                foraging.scoreboard(List.of("  Torrhus Canyon", "Purse: 8,553,877", "Bits: 91,627")),
                foraging.scoreboard(List.of("Whispers: 328.2k (+39)")),
                foraging.scoreboard(List.of("Whispers: 336.1k (+35)")),
                // Spending them is not a gain undone.
                foraging.scoreboard(List.of("Whispers: 300k")),
                foraging.scoreboard(List.of("Whispers: 300.5k (+35)"))));
        assertEquals(7900 + 500, totals.get(Counters.FORAGING_WHISPERS));

        // Below the thousands the total is given in full.
        ForagingTracker small = new ForagingTracker();
        small.scoreboard(List.of("Whispers: 141 (+1)"));
        assertEquals(List.of(new Count(Counters.FORAGING_WHISPERS, 9)), small.scoreboard(List.of("Whispers: 150 (+9)")));
        small.reset();
        assertTrue(small.scoreboard(List.of("Whispers: 900 (+1)")).isEmpty(), "after a reset it is a starting point again");
    }

    @Test
    void linesThatOnlyLookLikeATreeGiftAreLeftAlone() {
        ForagingTracker foraging = new ForagingTracker();
        List<String> none = List.of();
        assertTrue(foraging.chat("Party > Steve: You helped cut 100% of the Fig Tree.", none, T).isEmpty());
        // A chance in brackets only means a bonus gift straight after its heading.
        assertTrue(foraging.chat("Stretching Sticks (20%)", none, T).isEmpty());
        foraging.chat("BONUS GIFT", none, T);
        assertTrue(foraging.chat("Critical Chance (20%)", none, T + 60_000).isEmpty());
    }

    @Test
    void foragingFiguresShowTheUsualShareOfATree() {
        AtomicLong clock = new AtomicLong(T);
        SessionRecorder recorder = new SessionRecorder(clock::get);
        recorder.start(Activity.FORAGING, "");
        recorder.count(Counters.FORAGING_TREES, 4);
        recorder.count(Counters.FORAGING_SHARE_PERCENT, 300);
        clock.addAndGet(Fixtures.HOUR);
        Session session = recorder.stop();

        assertEquals(4.0, new SessionReport(session, null, null, clock.get()).rate(Metric.ACTIVITY_PER_HOUR).value(), 1e-9);
        ActivityStats.Row share = ActivityStats.of(session).getFirst().rows().get(1);
        assertEquals("Share of each tree", share.label());
        assertEquals(75.0, share.total(), 1e-9);
        assertFalse(share.hasRate());
    }

    @Test
    void miningLinesCountCommissionsProcsChestsAndNucleusRuns() {
        MiningTracker mining = new MiningTracker();
        Map<String, Double> totals = sum(List.of(
                mining.chat("MITHRIL MINER Commission Complete! Visit the King to claim your rewards!"),
                mining.chat("GOBLIN SLAYER Commission Complete! Visit the King to claim your rewards!"),
                mining.chat("Party > Steve: MITHRIL MINER Commission Complete! Visit the King"),
                mining.chat("Your Pickobulus destroyed 140 blocks!"),
                mining.chat("COMPACT! You found an Enchanted Hard Stone!"),
                mining.chat("PRISTINE! You found ❤ Flawed Ruby Gemstone x12!"),
                mining.chat("  CHEST LOCKPICKED "),
                mining.chat("    Gemstone Powder x537"),
                mining.chat("  CRYSTAL NUCLEUS LOOT BUNDLE")));

        assertEquals(2, totals.get(Counters.MINING_COMMISSIONS));
        assertEquals(1, totals.get(Counters.MINING_COMMISSION + "Mithril Miner"));
        assertEquals(1, totals.get(Counters.MINING_PICKOBULUS));
        assertEquals(140, totals.get(Counters.MINING_PICKOBULUS_BLOCKS));
        assertEquals(1, totals.get(Counters.MINING_COMPACT_ITEM + "Enchanted Hard Stone"));
        assertEquals(1, totals.get(Counters.MINING_PRISTINE));
        assertEquals(12, totals.get(Counters.MINING_PRISTINE_ITEM + "Flawed Ruby Gemstone"));
        assertEquals(1, totals.get(Counters.MINING_CHESTS));
        assertEquals(1, totals.get(Counters.MINING_NUCLEUS_RUNS));
    }

    @Test
    void powderGainedIsWhatTheTabListRisesByAndSpendingIsNotAGainUndone() {
        MiningTracker mining = new MiningTracker();
        Map<String, Double> totals = sum(List.of(
                mining.tab(List.of("Powders:", " ᠅ Mithril: 35,448", " ᠅ Gemstone: 36,758", " Mithril Miner: 45%")),
                mining.tab(List.of(" ᠅ Mithril: 35,600", " ᠅ Gemstone: 37,295")),
                // Spent on the Heart of the Mountain
                mining.tab(List.of(" ᠅ Mithril: 600", " ᠅ Gemstone: 37,295")),
                mining.tab(List.of(" ᠅ Mithril: 648", " ᠅ Glacite Powder: 10")),
                mining.tool("pick", 1_000),
                mining.tool("pick", 1_250),
                mining.tool("other", 90_000),
                mining.tool("other", 90_010)));

        assertEquals(200, totals.get(Counters.MINING_POWDER_KIND + "Mithril"));
        assertEquals(537, totals.get(Counters.MINING_POWDER_KIND + "Gemstone"));
        assertEquals(737, totals.get(Counters.MINING_POWDER));
        assertNull(totals.get(Counters.MINING_POWDER_KIND + "Glacite"));
        assertEquals(260, totals.get(Counters.MINING_BLOCKS));
    }

    @Test
    void farmingLinesCountPestsRareCropsAndVisitors() {
        FarmingTracker farming = new FarmingTracker();
        Map<String, Double> totals = sum(List.of(
                farming.chat("You received 7x Enchanted Potato for killing a Locust!", List.of()),
                farming.chat("You received 64x Enchanted Sugar for killing a Mosquito!", List.of()),
                farming.chat("You received 6x Enchanted Cocoa Beans for killing a Moth!", List.of()),
                farming.chat("You received 3x Enchanted Potato for killing a Locust!", List.of()),
                farming.chat("RARE CROP! Cropie (+97)", List.of()),
                farming.chat("RARE CROP! Seasoning (+115) (automatically donated)", List.of()),
                farming.chat("VERY RARE CROP! Burrowing Spores", List.of()),
                farming.chat("OFFER ACCEPTED with Jacob (UNCOMMON)", List.of()),
                farming.chat("Co-op > Steve: RARE CROP! Cropie (+97)", List.of())));

        assertEquals(4, totals.get(Counters.FARMING_PESTS));
        assertEquals(2, totals.get(Counters.FARMING_PEST + "Locust"));
        assertEquals(3, totals.get(Counters.FARMING_RARE_CROPS));
        assertEquals(1, totals.get(Counters.FARMING_RARE_CROP + "Seasoning"));
        assertEquals(1, totals.get(Counters.FARMING_RARE_CROP + "Burrowing Spores"));
        assertEquals(1, totals.get(Counters.FARMING_VISITORS));
    }

    @Test
    void pestDropsVisitorRewardsAndCopperAreReadAsCapturedInTheGarden() {
        FarmingTracker farming = new FarmingTracker();
        List<String> pestHover = List.of("When killing a  Pest on your\nGarden, your  Farming Fortune\nboosts crop gain");
        Map<String, Double> totals = sum(List.of(
                farming.scoreboard(List.of("Purse: 8,554,190", "Copper: 921", "Sowdust: 2,528,468")),
                farming.chat("You received 23x Enchanted Pumpkin for killing a Rat!", pestHover),
                farming.chat("RARE DROP! Polished Pumpkin x3 (+73.2)", pestHover),
                // A rare drop from something else reads much the same, but for what its hover says.
                farming.chat("RARE DROP! Foul Flesh x3 (+127% Magic Find)", List.of()),
                farming.chat("RARE DROP! (3x Foul Flesh) (+127%  Magic Find)", pestHover),
                farming.chat("OFFER ACCEPTED with Fear Mongerer (UNCOMMON)", List.of()),
                farming.chat("    +15 Garden Experience", List.of()),
                farming.chat("    +5k Farming XP", List.of()),
                farming.chat("    +17 Copper", List.of()),
                farming.scoreboard(List.of("Copper: 938", "Sowdust: 2,528,468")),
                // Spending copper is not a gain undone.
                farming.scoreboard(List.of("Copper: 100", "Sowdust: 2,528,500"))));

        assertEquals(1, totals.get(Counters.FARMING_PEST + "Rat"));
        assertEquals(1, totals.get(Counters.FARMING_PEST_DROPS));
        assertEquals(3, totals.get(Counters.FARMING_PEST_DROP + "Polished Pumpkin"));
        assertEquals(1, totals.get(Counters.FARMING_VISITORS));
        assertEquals(15, totals.get(Counters.FARMING_GARDEN_XP));
        assertEquals(17, totals.get(Counters.FARMING_COPPER));
        assertEquals(32, totals.get(Counters.FARMING_SOWDUST));
    }

    @Test
    void pestsSpawningAreCountedAndAnAmountCutShortIsNotMeasuredFrom() {
        FarmingTracker farming = new FarmingTracker();
        Map<String, Double> totals = sum(List.of(
                farming.chat("GROSS! A  Pest has appeared in Plot - 8!", List.of()),
                farming.chat("EWW! 2  Pests have spawned in Plot - 3!", List.of()),
                farming.chat("  CLICK HERE to teleport to the plot!", List.of()),
                farming.scoreboard(List.of("Copper: 938", "Sowdust: 2,528,468")),
                farming.scoreboard(List.of("Copper: 938", "Sowdust: 2.6M (+6.3k)")),
                farming.scoreboard(List.of("Copper: 938", "Sowdust: 2,541,068"))));

        assertEquals(3, totals.get(Counters.FARMING_PESTS_SPAWNED));
        assertEquals(12_600, totals.get(Counters.FARMING_SOWDUST));
        assertNull(totals.get(Counters.FARMING_COPPER));
    }

    @Test
    void cropsHarvestedAreWhatTheToolsOwnCountRisesBy() {
        FarmingTracker farming = new FarmingTracker();
        assertTrue(farming.tool("hoe", 1_000_000).isEmpty(), "the first sight of a tool is a starting point");
        assertEquals(List.of(new Count(Counters.FARMING_CROPS, 250)), farming.tool("hoe", 1_000_250));
        assertTrue(farming.tool("hoe", 1_000_250).isEmpty());
        // Another tool has a count of its own, and so does the first when it is picked up again.
        assertTrue(farming.tool("axe", 40).isEmpty());
        assertTrue(farming.tool(null, -1).isEmpty());
        assertTrue(farming.tool("hoe", 1_000_900).isEmpty(), "what it did while out of hand was not seen");
        assertEquals(List.of(new Count(Counters.FARMING_CROPS, 100)), farming.tool("hoe", 1_001_000));
    }

    @Test
    void farmingFiguresGiveBlocksByTheSecond() {
        AtomicLong clock = new AtomicLong(T);
        SessionRecorder recorder = new SessionRecorder(clock::get);
        recorder.start(Activity.FARMING, "");
        recorder.count(Counters.FARMING_BLOCKS, 36_000);
        clock.addAndGet(Fixtures.HOUR / 2);
        Session session = recorder.stop();

        SessionReport report = new SessionReport(session, null, null, clock.get());
        assertEquals(72_000.0, report.rate(Metric.ACTIVITY_PER_HOUR).value(), 1e-9);
        ActivityStats.Row perSecond = ActivityStats.of(session, report.trackedMillis).getFirst().rows().get(1);
        assertEquals("Blocks per second", perSecond.label());
        assertEquals(20.0, perSecond.total(), 1e-9);
        assertFalse(perSecond.hasRate());
    }

    @Test
    void aDungeonRunsEndingIsReadForItsFloorScoreTimeAndXp() {
        DungeonTracker dungeons = new DungeonTracker();
        Map<String, Double> totals = sum(List.of(
                dungeons.chat(" ☠ You were killed by Lost Adventurer and became a ghost."),
                dungeons.chat(" ☠ Steve fell into a deep hole and became a ghost."),
                dungeons.chat(" ☠ Steve disconnected from the Dungeon and became a ghost."),
                dungeons.chat("                    Master Mode The Catacombs - Floor V"),
                dungeons.chat("                          Team Score: 305 (S+)"),
                dungeons.chat("                    ☠ Defeated Livid in 05m 12s"),
                dungeons.chat("                       +123,456.5 Catacombs Experience"),
                dungeons.chat("                         +41,500 Mage Experience"),
                dungeons.chat("                         The Catacombs - Floor VII"),
                dungeons.chat("                          Team Score: 271 (S)"),
                dungeons.chat("                    ☠ Defeated Necron in 48s"),
                dungeons.chat("                         The Catacombs - Entrance"),
                dungeons.chat("Party > Steve: The Catacombs - Floor VII"),
                dungeons.chat("Steve: gg and became a ghost.")));

        assertEquals(3, totals.get(Counters.DUNGEONS_RUNS));
        assertEquals(1, totals.get(Counters.DUNGEONS_FLOOR + "M5"));
        assertEquals(1, totals.get(Counters.DUNGEONS_FLOOR + "F7"));
        assertEquals(1, totals.get(Counters.DUNGEONS_FLOOR + "Entrance"));
        assertEquals(576, totals.get(Counters.DUNGEONS_SCORE));
        assertEquals(1, totals.get(Counters.DUNGEONS_GRADE + "S+"));
        assertEquals(312_000 + 48_000, totals.get(Counters.DUNGEONS_RUN_MILLIS));
        assertEquals(123_456.5, totals.get(Counters.DUNGEONS_CATACOMBS_XP));
        assertEquals(41_500, totals.get(Counters.DUNGEONS_CLASS_XP + "Mage"));
        assertEquals(1, totals.get(Counters.DUNGEONS_DEATHS));
        assertEquals(2, totals.get(Counters.DUNGEONS_TEAM_DEATHS));
    }

    @Test
    void aDungeonRunsEndingIsReadAsCapturedInGame() {
        DungeonTracker dungeons = new DungeonTracker();
        Map<String, Double> totals = sum(List.of(
                dungeons.chat("[BOSS] Bonzo: Alright, maybe I'm just weak after all.."),
                dungeons.chat("                         The Catacombs - Floor I"),
                dungeons.chat("                            Team Score: 207 (B)"),
                dungeons.chat("                      ☠ Defeated Bonzo in 02m 08s"),
                dungeons.chat("                             > EXTRA STATS <"),
                dungeons.chat("                      +179.3 Catacombs Experience"),
                dungeons.chat("                       +110.5 Berserk Experience"),
                dungeons.chat("      Click HERE to re-queue into The Catacombs!")));

        assertEquals(1, totals.get(Counters.DUNGEONS_RUNS));
        assertEquals(1, totals.get(Counters.DUNGEONS_FLOOR + "F1"));
        assertEquals(207, totals.get(Counters.DUNGEONS_SCORE));
        assertEquals(1, totals.get(Counters.DUNGEONS_GRADE + "B"));
        assertEquals(128_000, totals.get(Counters.DUNGEONS_RUN_MILLIS));
        assertEquals(179.3, totals.get(Counters.DUNGEONS_CATACOMBS_XP));
        assertEquals(110.5, totals.get(Counters.DUNGEONS_CLASS_XP + "Berserk"));
    }

    private static List<String> tab(int secrets) {
        return List.of("Dungeon Stats", " Opened Rooms: 12", " Completed Rooms: 9", " Secrets Found: " + secrets, " Crypts: 3");
    }

    @Test
    void secretsAreTheServersCountAndTheOnesJustAfterThePlayersOwnDoingAreTheirs() {
        SecretTracker secrets = new SecretTracker();
        // Joined part way through a run: what was found before is not counted.
        assertTrue(secrets.tab(tab(3), T).isEmpty());

        secrets.action(T + 1000); // a chest opened
        Map<String, Double> totals = sum(List.of(
                secrets.tab(tab(4), T + 1500),
                // A teammate's, with nothing done by the player near it in time.
                secrets.tab(tab(5), T + 20_000)));
        secrets.action(T + 30_000); // an opened chest clicked again, which finds nothing
        secrets.action(T + 60_000); // two items picked up one after the other
        secrets.action(T + 60_400);
        totals = sum(List.of(List.of(new Count(Counters.DUNGEONS_SECRETS, totals.get(Counters.DUNGEONS_SECRETS)),
                        new Count(Counters.DUNGEONS_OWN_SECRETS, totals.get(Counters.DUNGEONS_OWN_SECRETS))),
                secrets.tab(tab(8), T + 61_000)));

        assertEquals(5, totals.get(Counters.DUNGEONS_SECRETS));
        assertEquals(3, totals.get(Counters.DUNGEONS_OWN_SECRETS), "one, then two of the three that came together");
    }

    @Test
    void aNewRunStartsTheSecretCountAgainFromNone() {
        SecretTracker secrets = new SecretTracker();
        secrets.tab(tab(0), T);
        Map<String, Double> totals = sum(List.of(
                secrets.tab(tab(7), T + 100_000),
                secrets.tab(List.of("Skills:", " Farming 45: 7.3%"), T + 200_000),
                secrets.tab(tab(2), T + 300_000),
                secrets.tab(tab(2), T + 300_500)));
        assertEquals(9, totals.get(Counters.DUNGEONS_SECRETS));
        assertNull(totals.get(Counters.DUNGEONS_OWN_SECRETS));
    }

    @Test
    void aSessionsCountersGiveTheActivitysOwnRateAndItsFigures() {
        AtomicLong clock = new AtomicLong(T);
        SessionRecorder recorder = new SessionRecorder(clock::get);
        recorder.start(Activity.FISHING, "");
        recorder.count(Counters.FISHING_CATCHES, 30);
        recorder.count(Counters.FISHING_SEA_CREATURES, 12);
        recorder.count(Counters.FISHING_CREATURE + "Squid", 8);
        recorder.count(Counters.FISHING_CREATURE + "Sea Walker", 4);
        recorder.count(Counters.FISHING_TROPHY_TIER + "BRONZE", 1);
        clock.addAndGet(Fixtures.HOUR / 2);
        Session session = recorder.stop();
        assertFalse(recorder.count(Counters.FISHING_CATCHES, 1), "nothing is counted once it has ended");

        SessionReport report = new SessionReport(session, null, null, clock.get());
        assertEquals(24.0, report.rate(Metric.ACTIVITY_PER_HOUR).value(), 1e-9);
        assertEquals("Creatures/h", ActivityStats.shortLabel(Metric.ACTIVITY_PER_HOUR, Activity.FISHING));

        List<ActivityStats.Row> rows = ActivityStats.of(session).getFirst().rows();
        assertEquals(List.of("Catches", "Sea creatures", "Squid", "Sea Walker", "Bronze"),
                rows.stream().map(ActivityStats.Row::label).toList());
        assertTrue(rows.get(2).part());
    }

    @Test
    void anActivityWithNothingOfItsOwnCountedHasNoRateOfItsOwn() {
        Session session = Fixtures.wheatSession(Fixtures.HOUR, 100);
        session.activity = Activity.OTHER.name();
        SessionReport report = new SessionReport(session, null, null, session.endMillis);
        assertFalse(report.rate(Metric.ACTIVITY_PER_HOUR).isAvailable());
        assertTrue(ActivityStats.of(session).isEmpty());
    }

    @Test
    void xpInOtherSkillsOnlyIsNotShownAsNoneInTheActivitysOwn() {
        AtomicLong clock = new AtomicLong(T);
        SessionRecorder recorder = new SessionRecorder(clock::get);
        recorder.start(Activity.DUNGEONS, "");
        recorder.addXp("Combat", 500, true);
        clock.addAndGet(Fixtures.HOUR);
        Session session = recorder.stop();
        assertFalse(new SessionReport(session, null, null, clock.get()).skillXp().isAvailable());
    }
}
