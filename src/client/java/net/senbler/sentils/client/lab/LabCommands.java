package net.senbler.sentils.client.lab;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.SharedSuggestionProvider;

import net.senbler.sentils.client.Log;
import net.senbler.sentils.client.SentilsClient;
import net.senbler.sentils.client.lab.analysis.Metric;
import net.senbler.sentils.client.lab.analysis.SessionReport;
import net.senbler.sentils.client.lab.model.Activity;
import net.senbler.sentils.client.lab.model.Session;
import net.senbler.sentils.client.lab.ui.LabScreen;
import net.senbler.sentils.client.lab.ui.SessionScreen;

/**
 * The /lab commands. They are handled on this computer by the mod: nothing is sent to the
 * server, and none of them does anything in the game world.
 */
public final class LabCommands {

    private LabCommands() {}

    /** The whole command tree, under the given name. */
    public static LiteralArgumentBuilder<FabricClientCommandSource> build(String name) {
        LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal(name);
        if (SentilsClient.debugFeatures) {
            // What the XP readers have seen, for working out why XP is or is not being recorded.
            root.then(ClientCommands.literal("debug").executes(context -> report("debug", LabCollectors.debugLines())));
            // The last chat lines from the server and the scoreboard, for finding out how a line reads.
            root.then(ClientCommands.literal("lines").executes(context -> report("lines", LabActivityCollectors.lines())));
        }
        return root
                .executes(context -> openScreen(() -> new LabScreen(null)))
                .then(ClientCommands.literal("start")
                        .executes(context -> say(start(LabSettings.lastActivity, "")))
                        .then(ClientCommands.argument("activity", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(activityNames(), builder))
                                .executes(context -> say(start(text(context, "activity"), "")))
                                .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                        .executes(context -> say(start(text(context, "activity"), text(context, "name")))))))
                .then(ClientCommands.literal("pause").executes(context -> say(Lab.pause("paused by hand"))))
                .then(ClientCommands.literal("resume").executes(context -> say(Lab.resume())))
                .then(ClientCommands.literal("stop").executes(context -> stop()))
                .then(ClientCommands.literal("cancel").executes(context -> say(Lab.cancel())))
                .then(ClientCommands.literal("status").executes(context -> say(status())))
                .then(ClientCommands.literal("split")
                        .executes(context -> say(Lab.split("")))
                        .then(ClientCommands.argument("label", StringArgumentType.greedyString())
                                .executes(context -> say(Lab.split(text(context, "label"))))))
                .then(ClientCommands.literal("tag")
                        .then(ClientCommands.argument("tag", StringArgumentType.greedyString())
                                .executes(context -> say(tag(text(context, "tag"))))))
                .then(ClientCommands.literal("note")
                        .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                                .executes(context -> say(note(text(context, "text"))))))
                .then(ClientCommands.literal("history")
                        .executes(context -> openScreen(() -> LabScreen.onTab(null, LabScreen.Tab.HISTORY))))
                .then(ClientCommands.literal("compare")
                        .executes(context -> openScreen(() -> LabScreen.onTab(null, LabScreen.Tab.COMPARE))))
                .then(ClientCommands.literal("experiments")
                        .executes(context -> openScreen(() -> LabScreen.onTab(null, LabScreen.Tab.EXPERIMENTS))))
                .then(ClientCommands.literal("export")
                        .executes(context -> say(Lab.exportCsv()))
                        .then(ClientCommands.literal("csv").executes(context -> say(Lab.exportCsv())))
                        .then(ClientCommands.literal("json").executes(context -> say(Lab.exportJson()))))
                .then(ClientCommands.literal("import").executes(context -> say(Lab.importAll())));
    }

    private static String text(CommandContext<FabricClientCommandSource> context, String argument) {
        return StringArgumentType.getString(context, argument);
    }

    private static List<String> activityNames() {
        List<String> names = new ArrayList<>();
        for (Activity activity : Activity.values()) names.add(activity.name().toLowerCase(Locale.ROOT));
        return names;
    }

    private static int say(String message) {
        Lab.say(message);
        return 1;
    }

    /** Shows lines meant for passing on: in chat, in the game's log, and on the clipboard ready to paste. */
    private static int report(String what, List<String> lines) {
        for (String line : lines) {
            Lab.say(line);
            Log.info("Lab " + what + ": " + line);
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(String.join("\n", lines));
        Lab.say("Copied to the clipboard.");
        return 1;
    }

    /** Opens a screen from a command; deferred so it runs after the chat screen has closed. */
    private static int openScreen(Supplier<Screen> screen) {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(screen.get()));
        return 1;
    }

    static String start(String activityName, String name) {
        if (!Activity.isKnown(activityName)) {
            return "\"" + activityName + "\" is not an activity. Try one of: " + String.join(", ", activityNames());
        }
        Activity activity = Activity.parse(activityName);
        String message = Lab.start(activity, name, null);
        if (Lab.active() != null && Lab.active().activityType() == activity) {
            LabSettings.lastActivity = activity.name();
            LabSettings.save();
        }
        return message;
    }

    /** Ends the session, says how it went in a line, and opens its report. */
    static int stop() {
        Session session = Lab.stop();
        if (session == null) return say("There is no session running");
        SessionReport report = Lab.report(session);
        Metric metric = LabSettings.metric();
        say("Saved \"" + session.displayName() + "\": " + Fmt.duration(report.trackedMillis) + " tracked, "
                + metric.label.toLowerCase(Locale.ROOT) + " " + Fmt.measure(report.rate(metric)));
        return openScreen(() -> new SessionScreen(null, session));
    }

    private static String status() {
        Session active = Lab.active();
        if (active == null) return "No session running. Start one with /lab start <activity>";
        SessionReport report = Lab.report(active);
        Metric metric = LabSettings.metric();
        return (Lab.recorder().isPaused() ? "Paused: " : "Recording: ") + active.displayName() + ", "
                + Fmt.duration(report.trackedMillis) + " tracked, " + metric.label.toLowerCase(Locale.ROOT) + " "
                + Fmt.measure(report.rate(metric));
    }

    private static String tag(String tag) {
        Session active = Lab.active();
        if (active == null) return "There is no session running";
        String trimmed = tag.trim();
        if (!active.tags.contains(trimmed)) active.tags.add(trimmed);
        Lab.autosave();
        return "Tagged \"" + trimmed + "\"";
    }

    private static String note(String text) {
        Session active = Lab.active();
        if (active == null) return "There is no session running";
        active.notes = active.notes.isBlank() ? text.trim() : active.notes + "; " + text.trim();
        Lab.autosave();
        return "Note added";
    }
}
