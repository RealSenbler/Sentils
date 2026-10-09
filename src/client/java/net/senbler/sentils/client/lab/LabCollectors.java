package net.senbler.sentils.client.lab;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import net.senbler.sentils.client.lab.core.InventoryDelta;
import net.senbler.sentils.client.lab.core.ItemChange;
import net.senbler.sentils.client.lab.core.SackText;
import net.senbler.sentils.client.lab.core.SessionRecorder;
import net.senbler.sentils.client.lab.core.SessionTimes;
import net.senbler.sentils.client.lab.core.TabSkillTracker;
import net.senbler.sentils.client.lab.core.XpTracker;
import net.senbler.sentils.client.lab.model.Session;
import net.senbler.sentils.client.skyblock.ActionBar;
import net.senbler.sentils.mixin.client.BossHealthOverlayAccessor;

/**
 * Watches what the client is shown and hands it to the session recorder. There are four things
 * it reads, all of them on the player's own screen: the "[Sacks]" chat message, the skill XP
 * line above the hotbar, the purse on the scoreboard, and the player's own inventory.
 * <p>
 * It only ever reads. Nothing here presses a key, clicks, moves an item or sends a packet.
 */
public final class LabCollectors {

    private LabCollectors() {}

    private static final Pattern FORMATTING_CODES = Pattern.compile("§.");
    private static final Pattern PURSE = Pattern.compile("(?:Purse|Piggy): ([\\d,]+(?:\\.\\d+)?)");
    private static final int CHECK_TICKS = 10;
    private static final int AUTOSAVE_TICKS = 600;
    // After changing worlds the inventory is emptied and filled again, which is not gains and losses.
    private static final int SETTLE_TICKS = 60;

    private static final int TAB_CHECK_TICKS = 20;
    private static final long NO_SACKS_AFTER_MILLIS = 2 * 60_000L;
    private static boolean sackMessagesWork; // one has been seen since the game started
    private static boolean sackWarningGiven;

    private static final XpTracker xp = new XpTracker();
    private static final TabSkillTracker tabXp = new TabSkillTracker();
    // Once the line above the hotbar has shown XP, the tab list is no longer counted from as well.
    private static boolean actionBarShowsXp;
    private static int ticksUntilTabCheck;
    // Kept for /lab debug, to tell why XP is or is not being picked up.
    private static String lastActionBar = "";
    private static long lastActionBarMillis;
    private static String lastXpText = "";
    private static final int DEBUG_TEXTS = 5;
    private static final Pattern SKILL_WORD = Pattern.compile(
            "Farming|Mining|Combat|Foraging|Fishing|Enchanting|Alchemy|Carpentry|Runecrafting|Social|Taming|Hunting",
            Pattern.CASE_INSENSITIVE);
    // The last few texts to name a skill: above the hotbar, in chat, and as a title
    private static final ArrayDeque<String> skillTexts = new ArrayDeque<>();
    private static final ArrayDeque<String> chatTexts = new ArrayDeque<>();
    private static final ArrayDeque<String> titleTexts = new ArrayDeque<>();
    private static int tabSkillLines;
    private static InventoryDelta.Snapshot inventory; // as last counted, or null to start over
    private static boolean menuSinceCount; // a container was open at some point since then
    private static Double purse; // as last read, or null to start over
    private static ClientLevel level;
    private static int settleTicks;
    private static int ticksUntilCheck;
    private static int ticksUntilAutosave;

    static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(LabCollectors::tick);
        ClientReceiveMessageEvents.GAME.register(LabCollectors::onMessage);
        ActionBar.register(LabCollectors::onActionBar);
        ActionBar.registerTitles(title -> keep(titleTexts, plain(title.getString())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect());
    }

    /** Forgets every starting point, so that what happened while not recording is not counted. */
    static void reset() {
        xp.reset();
        tabXp.reset();
        LabActivityCollectors.reset();
        inventory = null;
        menuSinceCount = false;
        purse = null;
    }

    private static void onDisconnect() {
        SessionRecorder recorder = Lab.recorder();
        if (LabSettings.pauseOnDisconnect && recorder.isRecording()) recorder.pause("disconnected");
        Lab.autosave();
        reset();
    }

    private static String plain(String text) {
        return FORMATTING_CODES.matcher(text).replaceAll("");
    }

    /**
     * Keeps a text for /lab debug if it names a skill, whether or not it reads as an XP message:
     * the point is to find out where and in what form the server shows skill XP.
     */
    private static void keep(ArrayDeque<String> texts, String text) {
        if (!SKILL_WORD.matcher(text).find() || texts.contains(text)) return;
        texts.addLast(text);
        if (texts.size() > DEBUG_TEXTS) texts.removeFirst();
    }

    /** The text above the hotbar, which is where Hypixel shows skill XP as it is gained. */
    private static void onActionBar(Component message) {
        String text = plain(message.getString());
        lastActionBar = text;
        lastActionBarMillis = System.currentTimeMillis();
        keep(skillTexts, text);
        if (!XpTracker.hasSkillLine(text)) return;
        actionBarShowsXp = true;
        lastXpText = text;

        SessionRecorder recorder = Lab.recorder();
        if (!recorder.isRecording()) return;
        XpTracker.Gain gain = xp.accept(text);
        if (gain != null) recorder.addXp(gain.skill(), gain.amount(), gain.exact());
    }

    /** The tab list's lines, without formatting codes. */
    static List<String> tabLines(Minecraft mc) {
        List<String> lines = new ArrayList<>();
        if (mc.getConnection() == null) return lines;
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            Component name = info.getTabListDisplayName();
            if (name != null) lines.add(plain(name.getString()));
        }
        return lines;
    }

    /** Skill XP from the tab list's Skills widget, counted only while the action bar has shown none. */
    private static void readTabSkills(Minecraft mc, SessionRecorder recorder) {
        List<String> lines = tabLines(mc);
        tabSkillLines = (int) lines.stream().filter(TabSkillTracker::isSkillLine).count();
        xp.levels(lines);
        for (XpTracker.Gain gain : tabXp.accept(lines)) {
            if (!actionBarShowsXp) recorder.addXp(gain.skill(), gain.amount(), gain.exact());
        }
    }

    /**
     * The text with every character that is not plain ASCII written out as its code, so that an
     * icon from the server's own font shows up as something that can be read and passed on.
     */
    static String visible(String text) {
        StringBuilder shown = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= 32 && c < 127) shown.append(c);
            else shown.append(String.format("\\u%04X", (int) c));
        }
        return shown.toString();
    }

    /**
     * What the XP readers have seen, for /lab debug: every place a server can put text, and what
     * in each has named a skill. That shows where skill XP is displayed, if anywhere.
     */
    static List<String> debugLines() {
        Minecraft mc = Minecraft.getInstance();
        List<String> lines = new ArrayList<>();
        if (lastActionBar.isEmpty()) {
            lines.add("Hotbar: no text has been seen above the hotbar at all since the game started");
        } else {
            lines.add("Hotbar: last text, " + Fmt.ago(lastActionBarMillis, System.currentTimeMillis())
                    + ": \"" + visible(lastActionBar) + "\"");
        }
        if (skillTexts.isEmpty()) lines.add("Hotbar: nothing there has named a skill");
        for (String text : skillTexts) {
            lines.add((XpTracker.hasSkillLine(text) ? "Hotbar, read as XP: \"" : "Hotbar, NOT read: \"")
                    + visible(text) + "\"");
        }

        if (chatTexts.isEmpty()) lines.add("Chat: nothing has named a skill");
        for (String text : chatTexts) lines.add("Chat: \"" + visible(text) + "\"");
        if (titleTexts.isEmpty()) lines.add("Titles: nothing has named a skill");
        for (String text : titleTexts) lines.add("Title: \"" + visible(text) + "\"");

        // The boss bars and the tab list as they are right now
        int bossBars = 0;
        for (LerpingBossEvent bar : ((BossHealthOverlayAccessor) mc.gui.hud.getBossOverlay()).sentils$events().values()) {
            String text = plain(bar.getName().getString());
            if (SKILL_WORD.matcher(text).find() && bossBars++ < DEBUG_TEXTS) lines.add("Boss bar: \"" + visible(text) + "\"");
        }
        if (bossBars == 0) lines.add("Boss bars: none names a skill");
        int tabLines = 0;
        for (String text : tabLines(mc)) {
            if (!SKILL_WORD.matcher(text).find() || tabLines++ >= DEBUG_TEXTS) continue;
            lines.add((TabSkillTracker.isSkillLine(text) ? "Tab list, can be counted from: \"" : "Tab list, NOT usable: \"")
                    + visible(text) + "\"");
        }
        if (tabLines == 0) lines.add("Tab list: no line names a skill (the Skills widget is set up with /tablist)");
        if (actionBarShowsXp) lines.add("The tab list is not counted from, because the hotbar text has shown XP");
        Session active = Lab.active();
        if (active == null) {
            lines.add("No session is running, so nothing is being recorded");
        } else if (active.xp.isEmpty()) {
            lines.add("This session has no XP recorded yet. The first XP text of a session only sets a starting point");
        } else {
            StringBuilder xpSoFar = new StringBuilder("This session's XP so far:");
            active.xp.forEach((skill, gain) -> xpSoFar.append(' ').append(skill).append(' ').append(Fmt.number(gain.amount)));
            lines.add(xpSoFar.toString());
        }
        return lines;
    }

    private static void onMessage(Component message, boolean overlay) {
        SessionRecorder recorder = Lab.recorder();
        // The action bar comes by way of onActionBar, however the server sent it.
        if (overlay) return;
        String text = plain(message.getString());
        keep(chatTexts, text.trim());

        // At the very start, so that a player quoting the line in chat is not taken for it.
        String line = text.trim();
        if (!line.startsWith(SackText.PREFIX)) return;
        // The added and the removed items are each in the hover text of their own part of the line.
        List<String> hoverTexts = new ArrayList<>();
        message.visit((style, part) -> {
            if (style.getHoverEvent() instanceof HoverEvent.ShowText(Component value)) {
                String hover = plain(value.getString());
                if (!hoverTexts.contains(hover)) hoverTexts.add(hover);
            }
            return Optional.empty();
        }, Style.EMPTY);
        List<ItemChange> changes = SackText.parse(hoverTexts);
        sackMessagesWork = true;
        if (recorder.isRecording()) {
            recorder.recordSackMessage(line, changes);
            return;
        }
        // Paused or just ended: the message is about the stretch of time that led up to that.
        changes = lessWhatLeftTheInventory(changes);
        long covers = SackText.coversMillis(line);
        if (recorder.isActive()) {
            if (recorder.recordSackMessageWhilePaused(line, changes, covers)) Lab.autosave();
        } else {
            Lab.lateSackMessage(changes, covers);
        }
    }

    /**
     * Takes out of a sack message whatever is only the player moving items between their
     * inventory and their sacks since recording stopped, going by how the inventory has changed
     * since it was last counted. Those items were counted when they first came in.
     */
    private static List<ItemChange> lessWhatLeftTheInventory(List<ItemChange> changes) {
        Minecraft mc = Minecraft.getInstance();
        if (inventory == null || mc.player == null) return changes;
        List<ItemChange> moved = InventoryDelta.between(inventory, count(mc.player.getInventory()));
        List<ItemChange> rest = new ArrayList<>();
        for (ItemChange change : changes) {
            long amount = change.amount();
            for (ItemChange move : moved) {
                // Opposite ways: out of the inventory and into the sacks, or the other way about.
                if (!move.name().equalsIgnoreCase(change.name()) || Long.signum(move.amount()) == Long.signum(amount)) continue;
                long cancelled = Math.min(Math.abs(move.amount()), Math.abs(amount));
                amount -= Long.signum(amount) * cancelled;
            }
            if (amount != 0) rest.add(new ItemChange(change.name(), change.id(), amount));
        }
        return rest;
    }

    private static void tick(Minecraft mc) {
        // Followed whether or not a session is running, so that starting one does not look like a change of world.
        if (mc.level != level) {
            level = mc.level;
            settleTicks = SETTLE_TICKS;
            inventory = null;
        }
        if (settleTicks > 0) settleTicks--;

        SessionRecorder recorder = Lab.recorder();
        if (!recorder.isActive()) return;
        if (--ticksUntilAutosave <= 0) {
            ticksUntilAutosave = AUTOSAVE_TICKS;
            Lab.autosave();
        }
        if (mc.player == null || !recorder.isRecording()) return;

        if (--ticksUntilTabCheck <= 0) {
            ticksUntilTabCheck = TAB_CHECK_TICKS;
            readTabSkills(mc, recorder);
        }
        if (mc.gui.screen() instanceof AbstractContainerScreen<?>) menuSinceCount = true;
        if (settleTicks > 0) {
            inventory = null; // still filling up after the change of world
            return;
        }
        if (--ticksUntilCheck > 0) return;
        ticksUntilCheck = CHECK_TICKS;

        readPurse(mc, recorder);
        readInventory(mc, recorder);
        warnIfNoSackMessages(recorder);
    }

    /**
     * Says once, a couple of minutes into a session, if no sack message has ever been seen. An
     * item that goes straight into a sack never touches the inventory, so with the message
     * switched off in SkyBlock's settings there is nothing to tell the Lab it was gained. It is
     * said once a game launch at most, as plenty of activities fill no sacks.
     */
    private static void warnIfNoSackMessages(SessionRecorder recorder) {
        if (sackMessagesWork || sackWarningGiven) return;
        if (SessionTimes.tracked(recorder.active(), System.currentTimeMillis()) < NO_SACKS_AFTER_MILLIS) return;
        sackWarningGiven = true;
        Lab.say("No [Sacks] message has shown up yet. Items that go straight into your sacks can only be "
                + "counted from that message: if you are gathering into sacks, switch on Sack Notifications "
                + "in the SkyBlock settings. Items that land in your inventory are counted either way.");
    }

    private static void readPurse(Minecraft mc, SessionRecorder recorder) {
        Double now = purseOnScoreboard(mc);
        if (now == null) return; // not on show just now, which says nothing about whether it changed
        recorder.purseSeen();
        if (purse != null && !purse.equals(now)) recorder.addPurseChange(now - purse);
        purse = now;
    }

    private static Double purseOnScoreboard(Minecraft mc) {
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) return null;
        for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
            // Hypixel keeps each line's text in the prefix and suffix of the entry's team.
            String line = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName())
                    .getString();
            Matcher m = PURSE.matcher(plain(line));
            if (!m.find()) continue;
            try {
                return Double.parseDouble(m.group(1).replace(",", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static void readInventory(Minecraft mc, SessionRecorder recorder) {
        InventoryDelta.Snapshot now = count(mc.player.getInventory());
        boolean menu = menuSinceCount || mc.gui.screen() instanceof AbstractContainerScreen<?>;
        if (inventory != null) {
            List<ItemChange> changes = InventoryDelta.between(inventory, now);
            if (!changes.isEmpty()) recorder.recordInventoryChanges(changes, menu);
        }
        inventory = now;
        menuSinceCount = mc.gui.screen() instanceof AbstractContainerScreen<?>;
    }

    private static InventoryDelta.Snapshot count(Inventory inventory) {
        InventoryDelta.Snapshot snapshot = new InventoryDelta.Snapshot();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            snapshot.add(plain(stack.getHoverName().getString()).trim(), skyblockId(stack), stack.getCount());
        }
        return snapshot;
    }

    /** The item's SkyBlock id, or null if it is not a SkyBlock item. */
    private static String skyblockId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        String id = data.copyTag().getStringOr("id", "");
        return id.isEmpty() ? null : id;
    }
}
