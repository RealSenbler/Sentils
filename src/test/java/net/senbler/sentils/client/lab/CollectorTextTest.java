package net.senbler.sentils.client.lab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import net.senbler.sentils.client.lab.core.ItemChange;
import net.senbler.sentils.client.lab.core.SackText;
import net.senbler.sentils.client.lab.core.TabSkillTracker;
import net.senbler.sentils.client.lab.core.XpTracker;

/** Reading what the game shows the player: the text behind a sack message and the XP line. */
class CollectorTextTest {

    @Test
    void sackLinesGiveItemsAddedAndRemoved() {
        List<ItemChange> changes = SackText.parse(List.of(
                "Added items:\n  +64 Enchanted Cobblestone (Mining Sack)\n  +1,250 Wheat (Agronomy Sack)\n\n"
                        + "This message can be disabled in the settings.",
                "Removed items:\n  -2 Enchanted Bread (Agronomy Sack)"));

        assertEquals(List.of(
                new ItemChange("Enchanted Cobblestone", null, 64),
                new ItemChange("Wheat", null, 1250),
                new ItemChange("Enchanted Bread", null, -2)), changes);
    }

    @Test
    void sackLinesSurviveBracketsInNamesAndExtrasFromOtherMods() {
        List<ItemChange> changes = SackText.parse(List.of(
                "+3 Rough Jade Gemstone (Gemstones Sack) (1.2k coins)\n+5 Odd (Thing) Item (Mining Sack)\n+7 No Sack Named"));

        assertEquals("Rough Jade Gemstone", changes.get(0).name());
        assertEquals("Odd (Thing) Item", changes.get(1).name());
        assertEquals(new ItemChange("No Sack Named", null, 7), changes.get(2));
    }

    @Test
    void theSackLineSaysHowLongAStretchItCovers() {
        assertEquals(23_000, SackText.coversMillis("[Sacks] +351 items. (Last 23s.)"));
        assertEquals(65_000, SackText.coversMillis("[Sacks] +351 items, -2 items. (Last 1m 5s.)"));
        assertEquals(120_000, SackText.coversMillis("[Sacks] +1 item. (Last 2m.)"));
        assertEquals(30_000, SackText.coversMillis("[Sacks] +351 items."), "the usual, where it does not say");
    }

    @Test
    void textThatIsNotAnItemLineIsIgnored() {
        assertTrue(SackText.parse(List.of("Added items:", "", "+ nothing", "+0 Wheat (Agronomy Sack)", "64 Wheat")).isEmpty());
    }

    @Test
    void xpIsMeasuredFromTheProgressFigureSoRepeatsCountOnce() {
        XpTracker tracker = new XpTracker();
        assertNull(tracker.accept("+12.5 Farming (1,000/50,000)"), "the first message only sets the starting point");
        assertNull(tracker.accept("+12.5 Farming (1,000/50,000)"), "the same message again is no gain");

        XpTracker.Gain gain = tracker.accept("§a+12.5 Farming (1,025/50,000)".replace("§a", ""));
        assertEquals("Farming", gain.skill());
        assertEquals(25.0, gain.amount(), 1e-9, "two gains of 12.5, though only one message was seen");
        assertTrue(gain.exact());
        assertNull(tracker.accept("+12.5 Farming (1,025/50,000)"));
    }

    @Test
    void xpIsFoundAmongTheRestOfTheActionBar() {
        XpTracker tracker = new XpTracker();
        tracker.accept("1,500/1,500❤     +5 Mining (10/100)     300/300✎ Mana");
        XpTracker.Gain gain = tracker.accept("1,500/1,500❤     +5 Mining (15/100)     300/300✎ Mana");
        assertEquals("Mining", gain.skill());
        assertEquals(5.0, gain.amount(), 1e-9);
        assertNull(tracker.accept("1,500/1,500❤     300 Defense     300/300✎ Mana"));
    }

    @Test
    void skillsAreFollowedSeparately() {
        XpTracker tracker = new XpTracker();
        tracker.accept("+1 Farming (100/1,000)");
        assertNull(tracker.accept("+1 Combat (500/1,000)"), "a first message for another skill");
        assertEquals(10.0, tracker.accept("+1 Farming (110/1,000)").amount(), 1e-9);
        assertEquals(1.0, tracker.accept("+1 Combat (501/1,000)").amount(), 1e-9);
    }

    @Test
    void aLevelUpFallsBackOnTheShownGainAndSaysItIsInexact() {
        XpTracker tracker = new XpTracker();
        tracker.accept("+50 Farming (49,980/50,000)");
        XpTracker.Gain gain = tracker.accept("+50 Farming (30/75,000)");
        assertEquals(50.0, gain.amount(), 1e-9);
        assertFalse(gain.exact());
    }

    @Test
    void percentDisplayCountsChangedMessagesAndSaysItIsInexact() {
        XpTracker tracker = new XpTracker();
        assertNull(tracker.accept("+12.5 Farming (24.69%)"));
        assertNull(tracker.accept("+12.5 Farming (24.69%)"));
        XpTracker.Gain gain = tracker.accept("+12.5 Farming (24.71%)");
        assertEquals(12.5, gain.amount(), 1e-9);
        assertFalse(gain.exact());
    }

    @Test
    void aPercentageIsFollowedOnceTheTabListGivesTheSkillsLevel() {
        // The text as captured while farming at 20 blocks a second, where few of the gains get a message.
        XpTracker tracker = new XpTracker();
        tracker.levels(List.of(" Farming Fortune: 1087", " Farming 45: 7.2%", " Mining 60: MAX"));
        assertNull(tracker.accept("4,187/3,812     +6.9 Farming (7.17%)     392/392     109/109     "));
        XpTracker.Gain gain = tracker.accept("4,187/3,812     +6.9 Farming (7.28%)     392/392     109/109     ");
        // From level 45 to 46 takes 2.9M, and 0.11% of that was covered.
        assertEquals(3190.0, gain.amount(), 1e-6);
        assertFalse(gain.exact(), "a hundredth of a percent is as close as it gets");
        assertNull(tracker.accept("4,187/3,812     +6.9 Farming (7.28%)     392/392     109/109     "));

        // A level up starts the percentage again, and only the shown gain is left to go by.
        tracker.accept("+6.9 Farming (99.99%)");
        assertEquals(6.9, tracker.accept("+6.9 Farming (0.01%)").amount(), 1e-9);
    }

    @Test
    void theXpEachLevelTakesAddsUpToTheKnownTotals() {
        // 55,172,425 XP to reach level 50 and 111,672,425 to reach 60, as the game's skill menu has it.
        AtomicLong to50 = new AtomicLong();
        AtomicLong to60 = new AtomicLong();
        for (int level = 1; level <= 60; level++) {
            if (level <= 50) to50.addAndGet(XpTracker.xpForLevel(level));
            to60.addAndGet(XpTracker.xpForLevel(level));
        }
        assertEquals(55_172_425L, to50.get());
        assertEquals(111_672_425L, to60.get());
    }

    @Test
    void xpAtMaxLevelIsFollowedToo() {
        // At the top level the game shows the total so far over 0.
        XpTracker tracker = new XpTracker();
        assertNull(tracker.accept("+207.2 Hunting (5,183,244/0)"));
        XpTracker.Gain gain = tracker.accept("+207.2 Hunting (5,183,658.4/0)");
        assertEquals(414.4, gain.amount(), 1e-6);
        assertTrue(gain.exact());
    }

    @Test
    void xpIsFoundWithAnIconOrXpInTheMessage() {
        // An icon between the number and the skill, as newer servers draw one.
        XpTracker tracker = new XpTracker();
        assertTrue(XpTracker.hasSkillLine("+12.5  Foraging (100/750)"));
        tracker.accept("+12.5  Foraging (100/750)");
        XpTracker.Gain gain = tracker.accept("+12.5  Foraging (125/750)");
        assertEquals("Foraging", gain.skill());
        assertEquals(25.0, gain.amount(), 1e-9);

        XpTracker withXp = new XpTracker();
        withXp.accept("+6.3 Foraging XP (24/750)");
        assertEquals(6.3, withXp.accept("+6.3 Foraging XP (30.3/750)").amount(), 1e-9);

        XpTracker lowerCase = new XpTracker();
        lowerCase.accept("+1 FORAGING (10/20)");
        assertEquals("Foraging", lowerCase.accept("+1 FORAGING (11/20)").skill());
    }

    @Test
    void xpIsFoundWithATotalOrNothingInBrackets() {
        XpTracker total = new XpTracker();
        total.accept("+50 Foraging (1,200,000)");
        XpTracker.Gain exact = total.accept("+50 Foraging (1,200,150)");
        assertEquals(150.0, exact.amount(), 1e-9);
        assertTrue(exact.exact());

        // With no figure at all, a changed message is all there is to go on.
        XpTracker bare = new XpTracker();
        assertNull(bare.accept("1,500/1,500❤     +40 Foraging     300/300 Mana"));
        assertNull(bare.accept("1,500/1,500❤     +40 Foraging     300/300 Mana"));
        XpTracker.Gain guessed = bare.accept("1,500/1,500❤     +45 Foraging     300/300 Mana");
        assertEquals(45.0, guessed.amount(), 1e-9);
        assertFalse(guessed.exact());
    }

    @Test
    void textThatOnlyLooksLikeXpIsLeftAlone() {
        assertFalse(XpTracker.hasSkillLine("+50 Mining Speed"));
        assertFalse(XpTracker.hasSkillLine("-50 Mana (Wither Impact)"));
        assertFalse(XpTracker.hasSkillLine("1,500/1,500❤+50▆     300 Defense     300/300 Mana"));
        assertFalse(XpTracker.hasSkillLine("+5 Kills (Combat)"));
        assertFalse(XpTracker.hasSkillLine("Foraging 35: 12.4%"));
    }

    @Test
    void tabListSkillLinesGiveXpFromTheNumberMoving() {
        TabSkillTracker tracker = new TabSkillTracker();
        assertTrue(tracker.accept(List.of("Skills:", " Mining 14: 22,922/75k", " Combat 49: 7,678/4M")).isEmpty(),
                "the first reading only sets the starting point");

        List<XpTracker.Gain> gains = tracker.accept(List.of(" Mining 14: 23,100/75k", " Combat 49: 7,678/4M"));
        assertEquals(1, gains.size());
        assertEquals("Mining", gains.getFirst().skill());
        assertEquals(178.0, gains.getFirst().amount(), 1e-9);
        assertFalse(gains.getFirst().exact(), "the widget lags, so it is reported as inexact");
    }

    @Test
    void tabListLinesThatCannotBeFollowedGiveNothing() {
        TabSkillTracker tracker = new TabSkillTracker();
        List<String> lines = List.of(" Farming 35: 12.4%", " Farming 60: MAX", " Wheat 9: 1,000/25k", " Mining 14: 100/75k");
        tracker.accept(lines);
        // A percentage or MAX has no number to follow, and a collection is not a skill.
        assertTrue(tracker.accept(List.of(" Farming 35: 12.9%", " Farming 60: MAX", " Wheat 9: 5,000/25k")).isEmpty());
        assertFalse(TabSkillTracker.isSkillLine(" Wheat 9: 1,000/25k"));
        assertFalse(TabSkillTracker.isSkillLine(" Farming 35: 12.4%"));
        assertTrue(TabSkillTracker.isSkillLine(" Mining 14: 100/75k"));
    }

    @Test
    void aLevelUpInTheTabListOnlySetsANewStartingPoint() {
        TabSkillTracker tracker = new TabSkillTracker();
        tracker.accept(List.of(" Mining 14: 74,900/75k"));
        assertTrue(tracker.accept(List.of(" Mining 15: 300/100k")).isEmpty());
        assertEquals(50.0, tracker.accept(List.of(" Mining 15: 350/100k")).getFirst().amount(), 1e-9);
    }

    @Test
    void afterAResetTheNextMessageIsAStartingPointAgain() {
        XpTracker tracker = new XpTracker();
        tracker.accept("+1 Farming (100/1,000)");
        tracker.reset();
        assertNull(tracker.accept("+1 Farming (900/1,000)"), "XP gained while not recording is not counted");
    }
}
