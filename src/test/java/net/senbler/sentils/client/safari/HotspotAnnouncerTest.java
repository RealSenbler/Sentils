package net.senbler.sentils.client.safari;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import net.senbler.sentils.client.safari.party.HotspotAnnouncer;

/** Reading the biome out of the game's hotspot line. */
class HotspotAnnouncerTest {

    @Test
    void theBiomeIsReadFromTheHotspotLine() {
        assertEquals("Forest", HotspotAnnouncer.biome("HOTSPOT! Your Hunting Hotspot is the Forest Biome!"));
        assertEquals("Forest", HotspotAnnouncer.biome("§6§lHOTSPOT! §eYour Hunting Hotspot is the §aForest Biome§e!"));
        assertEquals("Deep Jungle", HotspotAnnouncer.biome("HOTSPOT! Your Hunting Hotspot is the Deep Jungle Biome!"));
    }

    @Test
    void aPlayerQuotingTheLineIsNotAnnounced() {
        assertNull(HotspotAnnouncer.biome("Party > [MVP+] Steve: HOTSPOT! Your Hunting Hotspot is the Forest Biome!"));
        assertNull(HotspotAnnouncer.biome("My biome is Forest"));
    }
}
