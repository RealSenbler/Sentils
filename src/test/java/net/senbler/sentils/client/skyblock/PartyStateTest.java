package net.senbler.sentils.client.skyblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

/** Following a party from what the server writes in chat. */
class PartyStateTest {

    private static final String ME = "Senbler";

    private static PartyState after(String... lines) {
        PartyState party = new PartyState();
        for (String line : lines) party.accept(line, ME);
        return party;
    }

    @Test
    void joiningSomeonesPartyListsThemAndTheOthers() {
        PartyState party = after("§eYou have joined §b[MVP§c+§b] Steve's §eparty!",
                "§eYou'll be partying with: §a[VIP] Alex§e, §7Sam");
        assertTrue(party.isInParty());
        assertEquals("Steve", party.leader());
        assertEquals(Set.of("Steve", "Alex", "Sam"), party.members());
    }

    @Test
    void someoneJoiningThePlayersOwnPartyMakesThePlayerItsLeader() {
        PartyState party = after("[VIP] Alex joined the party.");
        assertTrue(party.isInParty());
        assertEquals(ME, party.leader());
        assertEquals(Set.of("Alex"), party.members());
    }

    @Test
    void membersGoingAreDroppedHoweverTheyWent() {
        PartyState party = after("A joined the party.", "B joined the party.", "C joined the party.", "D joined the party.",
                "A has left the party.", "[MVP+] B has been removed from the party.",
                "Kicked C because they were offline.", "D was removed from your party because they disconnected.");
        assertTrue(party.members().isEmpty());
        assertTrue(party.isInParty(), "only the server saying the party is over ends it");
    }

    @Test
    void everyWayAPartyEndsIsSeen() {
        for (String end : new String[] {
                "[MVP+] Steve has disbanded the party!",
                "You have been kicked from the party by [MVP+] Steve",
                "You left the party.",
                "The party was disbanded because all invites expired and the party was empty.",
                "The party was disbanded because the party leader disconnected.",
                "You are not currently in a party.",
                "You are not in a party right now."}) {
            PartyState party = after("You have joined Steve's party!", end);
            assertFalse(party.isInParty(), end);
            assertTrue(party.members().isEmpty(), end);
            assertNull(party.leader(), end);
        }
    }

    @Test
    void aTransferChangesTheLeader() {
        PartyState handed = after("You have joined Steve's party!", "Alex joined the party.",
                "The party was transferred to [VIP] Alex by [MVP+] Steve");
        assertEquals("Alex", handed.leader());
        assertEquals(Set.of("Steve", "Alex"), handed.members());

        PartyState left = after("You have joined Steve's party!", "Alex joined the party.",
                "The party was transferred to [VIP] Alex because [MVP+] Steve left");
        assertEquals("Alex", left.leader());
        assertEquals(Set.of("Alex"), left.members());
    }

    @Test
    void thePartyListReplacesWhatWasKnown() {
        PartyState party = after("Gone joined the party.",
                "Party Members (3)",
                "Party Leader: [MVP+] Steve ●",
                "Party Members: [VIP] Alex ● Senbler ●");
        assertEquals("Steve", party.leader());
        assertEquals(Set.of("Steve", "Alex"), party.members());
    }

    @Test
    void partyChatAndThePartyFinderShowAPartyNotKnownAbout() {
        assertTrue(after("Party > [VIP] Alex: hello").isInParty());
        assertTrue(after("Party > Senbler: hello").isInParty());
        assertEquals(Set.of("Alex"), after("Party Finder > Alex joined the dungeon group! (Mage Level 30)").members());
        assertEquals(Set.of("Alex"), after("Party Finder > Alex joined the group! (Combat Level 40)").members());
    }

    @Test
    void playersSayingTheseLinesChangeNothing() {
        assertFalse(after("Guild > [MVP+] Steve: Alex joined the party.").isInParty());
        assertFalse(after("[MVP+] Steve: You have joined Alex's party!").isInParty());
        assertFalse(after("Co-op > Steve: Party > Alex: hi").isInParty());
        PartyState party = after("You have joined Steve's party!", "Party > [VIP] Alex: You left the party.",
                "Party > Alex: Steve has disbanded the party!");
        assertTrue(party.isInParty());
    }
}
