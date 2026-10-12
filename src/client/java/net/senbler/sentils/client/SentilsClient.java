package net.senbler.sentils.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.senbler.sentils.client.debug.ItemDataHud;
import net.senbler.sentils.client.debug.LocationHud;
import net.senbler.sentils.client.debug.SackChangeHud;
import net.senbler.sentils.client.dungeons.CrackedBrickHighlight;
import net.senbler.sentils.client.dungeons.DungeonSettings;
import net.senbler.sentils.client.farming.FortuneScreen;
import net.senbler.sentils.client.gui.screen.SettingsScreen;
import net.senbler.sentils.client.hud.HudEditScreen;
import net.senbler.sentils.client.hud.HudManager;
import net.senbler.sentils.client.keybinds.KeybindConfigScreen;
import net.senbler.sentils.client.keybinds.KeybindHandler;
import net.senbler.sentils.client.keybinds.KeybindsConfig;
import net.senbler.sentils.client.lab.Lab;
import net.senbler.sentils.client.lab.LabCommands;
import net.senbler.sentils.client.lab.LabHud;
import net.senbler.sentils.client.lab.LabKeys;
import net.senbler.sentils.client.mining.AutomatonGlow;
import net.senbler.sentils.client.mining.MetalDetectorSolver;
import net.senbler.sentils.client.mining.MiningSettings;
import net.senbler.sentils.client.notifications.NotificationConfigScreen;
import net.senbler.sentils.client.notifications.TitlesConfig;
import net.senbler.sentils.client.safari.SafariSettings;
import net.senbler.sentils.client.safari.party.HotspotAnnouncer;
import net.senbler.sentils.client.safari.alerts.SparklingAlert;
import net.senbler.sentils.client.safari.trackers.SafariShardHud;
import net.senbler.sentils.client.safari.trackers.SafariShardTracker;
import net.senbler.sentils.client.safari.trackers.ShardIcons;
import net.senbler.sentils.client.safari.waypoints.BellWaypoints;
import net.senbler.sentils.client.skyblock.Hypixel;
import net.senbler.sentils.client.skyblock.Party;
import net.senbler.sentils.client.skyblock.SkyblockLocation;
import net.senbler.sentils.client.stats.ColdHud;
import net.senbler.sentils.client.stats.ColdSettings;
import net.senbler.sentils.client.stats.ColdTracker;
import net.senbler.sentils.client.theme.ThemeSettings;
import net.senbler.sentils.client.waypoints.BlockHighlightRenderer;
import net.senbler.sentils.client.waypoints.WaypointRenderer;
import net.senbler.sentils.client.waypoints.WaypointSettings;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SentilsClient implements ClientModInitializer {

    public static final String developmentApiKey = null;
    // Whether what needs that key (the farming fortune checker) can be reached: its settings and its
    // command. Off until players have a way of giving a key of their own.
    public static final boolean apiKeyFeatures = false;
    // Whether the tools for working on the mod can be reached: the Debug settings and the /lab debug and
    // /lab lines commands. On when the game is run from the project, or started with -Dsentils.debug=true.
    public static final boolean debugFeatures =
            FabricLoader.getInstance().isDevelopmentEnvironment() || Boolean.getBoolean("sentils.debug");

    private static final Pattern COORDS = Pattern.compile(
            "x:?\\s*(-?\\d+(?:\\.\\d+)?)[,\\s]+y:?\\s*(-?\\d+(?:\\.\\d+)?)[,\\s]+z:?\\s*(-?\\d+(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE);

    // Hypixel prefixes its non-public channels; anything without one of these is treated as all chat.
    private static final Pattern FORMATTING_CODES = Pattern.compile("§.");
    private static final String[] OTHER_CHANNEL_PREFIXES = {"Guild > ", "Officer > ", "Co-op > ", "From ", "To "};

    public static Map<String, String> titles = new HashMap<>();

    @Override
    public void onInitializeClient() {

        titles = TitlesConfig.load();

        ThemeSettings.load();
        WaypointSettings.load();
        WaypointRenderer.init();
        KeybindsConfig.load();
        KeybindHandler.init();
        SkyblockLocation.init();
        Party.init();
        SparklingAlert.init();
        SafariSettings.load();
        ColdSettings.load();
        ColdTracker.init();
        MiningSettings.load();
        MetalDetectorSolver.init();
        AutomatonGlow.init();
        DungeonSettings.load();
        BlockHighlightRenderer.init();
        CrackedBrickHighlight.init();
        Lab.init();
        LabKeys.init();
        HudManager.register(new LabHud());
        HudManager.register(new ColdHud());
        SafariShardTracker.init();
        HotspotAnnouncer.init();
        BellWaypoints.init();
        ShardIcons.load();
        HudManager.register(new SafariShardHud());
        HudManager.register(new ItemDataHud());
        SackChangeHud.init();
        HudManager.register(new SackChangeHud());
        HudManager.init();

        // Commands: /sentils opens the settings; everything else is a /sentils subcommand.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("sentils")
                    .executes(context -> openScreen(() -> new SettingsScreen(null, Component.literal("Sentils"))))
                    .then(ClientCommands.literal("notifications")
                            .executes(context -> openScreen(() -> new NotificationConfigScreen(null, titles))))
                    .then(ClientCommands.literal("keybinds")
                            .executes(context -> openScreen(() -> new KeybindConfigScreen(null))))
                    .then(ClientCommands.literal("hud")
                            .executes(context -> openScreen(() -> new HudEditScreen(null)))));
            if (!apiKeyFeatures) return;
            // /sentils ff [username] [profile]: farming fortune, of the player themselves by default
            dispatcher.register(ClientCommands.literal("sentils").then(ClientCommands.literal("ff")
                    .executes(context -> openScreen(() -> FortuneScreen.forPlayer(null, null, null)))
                    .then(ClientCommands.argument("username", StringArgumentType.word())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                    context.getSource().getOnlinePlayerNames(), builder))
                            .executes(context -> {
                                String username = StringArgumentType.getString(context, "username");
                                return openScreen(() -> FortuneScreen.forPlayer(null, username, null));
                            })
                            .then(ClientCommands.argument("profile", StringArgumentType.word())
                                    .executes(context -> {
                                        String username = StringArgumentType.getString(context, "username");
                                        String profile = StringArgumentType.getString(context, "profile");
                                        return openScreen(() -> FortuneScreen.forPlayer(null, username, profile));
                                    })))));
        });

        // /lab is the Efficiency Lab; /sentils lab is the same thing, in case another mod has /lab.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(LabCommands.build("lab"));
            dispatcher.register(ClientCommands.literal("sentils").then(LabCommands.build("lab")));
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            checkAndShowTitle(message.getString());
            ListenerActions.onServerChatReceived(message.getString());
        });
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, timestamp) -> handleMessage(message));
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) handleMessage(message);
        });

    }

    /** Opens a screen from a command; deferred so it runs after the chat screen has closed. */
    private static int openScreen(Supplier<Screen> screen) {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(screen.get()));
        return 1;
    }

    private void checkAndShowTitle(String msg) {
        if (!Hypixel.featuresAllowed()) return;
        String lower = msg.toLowerCase();
        for (String key : titles.keySet()) {
            if (lower.contains(key)) {
                String[] parts = titles.get(key).split("\\|", 2);
                int ticks = 20;
                try {
                    if (parts.length > 1) ticks = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException ignored) {
                }
                Minecraft.getInstance().gui.hud.setTitle(Component.literal(parts[0]));
                Minecraft.getInstance().gui.hud.setTimes(0, ticks, 0);
                break;
            }
        }
    }

    /** Whether the chat channel this message came from is allowed to create waypoints. */
    private static boolean isWaypointSourceEnabled(String text) {
        String plain = FORMATTING_CODES.matcher(text).replaceAll("").stripLeading();
        if (plain.startsWith("Party > ")) return WaypointSettings.fromPartyChat;
        for (String prefix : OTHER_CHANNEL_PREFIXES) {
            if (plain.startsWith(prefix)) return true; // no setting for these yet
        }
        return WaypointSettings.fromAllChat;
    }

    private static void handleMessage(Component message) {
        String text = message.getString();

        Matcher m = COORDS.matcher(text);
        if (m.find() && Hypixel.featuresAllowed() && isWaypointSourceEnabled(text)) {
            int x = (int) Math.floor(Double.parseDouble(m.group(1)));
            int y = (int) Math.floor(Double.parseDouble(m.group(2)));
            int z = (int) Math.floor(Double.parseDouble(m.group(3)));
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && WaypointSettings.renderWaypoints) {
                mc.player.sendSystemMessage(
                        Component.literal("Sentils: ").withStyle(ChatFormatting.DARK_AQUA)
                                .append(Component.literal("Rendering waypoint at (" + x + ", " + y + ", " + z + ")")
                                        .withStyle(ChatFormatting.WHITE)));
            }
            WaypointRenderer.showWaypoint(x, y, z, WaypointSettings.durationSeconds * 1000L);
        }
    }
}