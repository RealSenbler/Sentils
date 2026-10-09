package net.senbler.sentils.client.lab;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.item.component.CustomData;

import net.senbler.sentils.client.lab.core.Count;
import net.senbler.sentils.client.lab.core.DungeonTracker;
import net.senbler.sentils.client.lab.core.FarmingTracker;
import net.senbler.sentils.client.lab.core.FishingTracker;
import net.senbler.sentils.client.lab.core.ForagingTracker;
import net.senbler.sentils.client.lab.core.MiningTracker;
import net.senbler.sentils.client.lab.core.SessionRecorder;
import net.senbler.sentils.client.lab.core.SecretTracker;
import net.senbler.sentils.client.lab.core.SlayerTracker;
import net.senbler.sentils.client.lab.model.Activity;
import net.senbler.sentils.client.lab.model.Counters;
import net.senbler.sentils.client.skyblock.ItemPickups;
import net.senbler.sentils.client.skyblock.ServerChat;
import net.senbler.sentils.client.skyblock.ServerSounds;
import net.senbler.sentils.client.skyblock.Sidebar;

/**
 * Watches for what is particular to one activity, sea creatures for fishing, bosses for slayer
 * and trees for foraging, and hands it to the session recorder as counts. Like LabCollectors it only ever
 * reads: chat lines and sounds as the server sent them, the scoreboard, and whether the
 * player's own bobber is out.
 */
public final class LabActivityCollectors {

    private LabActivityCollectors() {}

    private static final Pattern FORMATTING_CODES = Pattern.compile("§.");
    private static final String CATCH_SOUND = "entity.experience_orb.pickup";
    private static final float CATCH_VOLUME = 0.5f;
    private static final int SCOREBOARD_TICKS = 10;
    private static final int KEPT_LINES = 25;
    private static final Pattern TAB_OF_INTEREST = Pattern.compile("Powder|Mithril|Gemstone|Glacite|Secrets Found");

    private static final FishingTracker fishing = new FishingTracker();
    private static final SlayerTracker slayer = new SlayerTracker();
    private static final ForagingTracker foraging = new ForagingTracker();
    private static final FarmingTracker farming = new FarmingTracker();
    private static final MiningTracker mining = new MiningTracker();
    private static final DungeonTracker dungeons = new DungeonTracker();
    private static final SecretTracker secrets = new SecretTracker();
    private static final float BAT_VOLUME = 0.1f;
    private static boolean useDown; // the use key was down a tick ago
    private static boolean bobberOut;
    private static int ticksUntilScoreboard;
    // The last lines of chat from the server, for /lab lines: how a line really reads is found out from these.
    private static final ArrayDeque<String> recentChat = new ArrayDeque<>();

    static void init() {
        ServerChat.register(LabActivityCollectors::onChat);
        ServerSounds.register(LabActivityCollectors::onSound);
        ItemPickups.register(() -> {
            if (Lab.recorder().isRecording()) secrets.action(System.currentTimeMillis());
        });
        ClientTickEvents.END_CLIENT_TICK.register(LabActivityCollectors::tick);
        ClientPlayerBlockBreakEvents.AFTER.register((level, player, pos, state) -> onBlockBroken());
    }

    /** Forgets anything half seen, so that what happened while not recording is not counted. */
    static void reset() {
        fishing.reset();
        slayer.reset();
        foraging.reset();
        farming.reset();
        mining.reset();
        secrets.reset();
    }

    private static void record(List<Count> counts) {
        SessionRecorder recorder = Lab.recorder();
        for (Count count : counts) {
            recorder.count(count.key(), count.amount());
            // Catacombs XP only ever shows at the end of a run, never above the hotbar with the skills'.
            if (count.key().equals(Counters.DUNGEONS_CATACOMBS_XP)) recorder.addXp("Catacombs", count.amount(), true);
        }
    }

    private static boolean holdingRod(Minecraft mc) {
        return mc.player != null && (mc.player.getMainHandItem().getItem() instanceof FishingRodItem
                || mc.player.getOffhandItem().getItem() instanceof FishingRodItem);
    }

    private static void onChat(Component message) {
        String text = FORMATTING_CODES.matcher(message.getString()).replaceAll("");
        List<String> hoverTexts = hoverTexts(message);
        // Not the server's answers to other mods asking where the player is, which come every few seconds.
        if (!text.isBlank() && !text.startsWith("{\"server\":")) {
            recentChat.addLast(text);
            for (String hover : hoverTexts) recentChat.addLast("   on hover: " + hover.replace("\n", " | "));
            while (recentChat.size() > KEPT_LINES) recentChat.removeFirst();
        }
        if (!Lab.recorder().isRecording()) return;
        long now = System.currentTimeMillis();
        record(fishing.chat(text, now, holdingRod(Minecraft.getInstance())));
        record(slayer.chat(text, now));
        record(foraging.chat(text, hoverTexts, now));
        record(farming.chat(text, hoverTexts));
        record(mining.chat(text));
        record(dungeons.chat(text));
    }

    /** The texts shown on hovering over the parts of a line, each once, without formatting codes. */
    private static List<String> hoverTexts(Component message) {
        List<String> hoverTexts = new ArrayList<>();
        message.visit((style, part) -> {
            if (style.getHoverEvent() instanceof HoverEvent.ShowText(Component value)) {
                String hover = FORMATTING_CODES.matcher(value.getString()).replaceAll("");
                if (!hoverTexts.contains(hover)) hoverTexts.add(hover);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return hoverTexts;
    }

    private static void onSound(String name, float volume) {
        if (!Lab.recorder().isRecording()) return;
        // A secret bat in a dungeon, hit or killed close by: the server plays it this quietly for those alone.
        if (name.startsWith("entity.bat.") && volume == BAT_VOLUME) secrets.action(System.currentTimeMillis());
        if (!name.equals(CATCH_SOUND) || volume != CATCH_VOLUME) return;
        if (holdingRod(Minecraft.getInstance())) record(fishing.sound(System.currentTimeMillis()));
    }

    /**
     * Whether the player has just clicked on a block that can hold a secret: a chest, or a head,
     * which is what wither essence and a redstone key are. Whether there was a secret in it is
     * for the server's count to say.
     */
    private static boolean clickedSecretBlock(Minecraft mc) {
        boolean down = mc.options.keyUse.isDown();
        boolean pressed = down && !useDown;
        useDown = down;
        if (!pressed || mc.gui.screen() != null || mc.level == null) return false;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return false;
        Block block = mc.level.getBlockState(hit.getBlockPos()).getBlock();
        return block instanceof ChestBlock || block instanceof AbstractSkullBlock;
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null) {
            bobberOut = false;
            return;
        }
        boolean out = mc.player.fishing != null;
        boolean reeled = bobberOut && !out;
        bobberOut = out;

        boolean clicked = clickedSecretBlock(mc);
        if (!Lab.recorder().isRecording()) return;

        long now = System.currentTimeMillis();
        if (reeled) record(fishing.reeled(now));
        if (clicked) secrets.action(now);
        if (--ticksUntilScoreboard > 0) return;
        List<String> tab = LabCollectors.tabLines(mc);
        record(secrets.tab(tab, now));
        record(mining.tab(tab));
        ticksUntilScoreboard = SCOREBOARD_TICKS;
        List<String> sidebar = Sidebar.lines();
        record(slayer.scoreboard(sidebar, now));
        record(foraging.scoreboard(sidebar));
        record(farming.scoreboard(sidebar));
        readTool(mc);
    }

    /** The count a tool keeps on itself: of crops on a farming tool, under one of two names by its age, or of blocks on one with Compact. */
    private static void readTool(Minecraft mc) {
        CustomData data = mc.player.getMainHandItem().get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data != null ? data.copyTag() : null;
        String uuid = tag != null ? tag.getStringOr("uuid", "") : "";
        long harvested = tag != null ? Math.max(tag.getLongOr("farmed_cultivating", -1), tag.getLongOr("mined_crops", -1)) : -1;
        record(farming.tool(uuid.isEmpty() || harvested < 0 ? null : uuid, harvested));
        long mined = tag != null ? tag.getLongOr("compact_blocks", -1) : -1;
        record(mining.tool(uuid.isEmpty() || mined < 0 ? null : uuid, mined));
    }

    /** A block the player broke: counted in a farming session, where it is a crop all but always. */
    private static void onBlockBroken() {
        SessionRecorder recorder = Lab.recorder();
        if (recorder.isRecording() && recorder.active().activityType() == Activity.FARMING) {
            recorder.count(Counters.FARMING_BLOCKS, 1);
        }
    }

    /**
     * The last lines of chat the server sent and the scoreboard as it stands, with every
     * character that is not plain written out as its code. For finding out how a line reads.
     */
    static List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add("Chat from the server, oldest first:");
        if (recentChat.isEmpty()) lines.add("  (none yet)");
        for (String line : recentChat) lines.add("  \"" + LabCollectors.visible(line) + "\"");
        lines.add("Scoreboard, top to bottom:");
        List<String> sidebar = Sidebar.lines();
        if (sidebar.isEmpty()) lines.add("  (none on show)");
        for (String line : sidebar) lines.add("  \"" + LabCollectors.visible(line) + "\"");
        // Only the lines of the tab list that something is counted from: all of it would fill the chat.
        Minecraft mc = Minecraft.getInstance();
        for (String line : LabCollectors.tabLines(mc)) {
            if (TAB_OF_INTEREST.matcher(line).find()) lines.add("  tab: \"" + LabCollectors.visible(line) + "\"");
        }
        return lines;
    }
}
