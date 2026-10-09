package net.senbler.sentils.client.lab;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import net.senbler.sentils.client.gui.Theme;
import net.senbler.sentils.client.hud.HudWidget;
import net.senbler.sentils.client.lab.analysis.ActivityStats;
import net.senbler.sentils.client.lab.analysis.Metric;
import net.senbler.sentils.client.lab.analysis.SessionReport;
import net.senbler.sentils.client.lab.core.SessionTimes;
import net.senbler.sentils.client.lab.model.Session;

/**
 * A small readout of the session being recorded: what it is, how long it has run, and the
 * chosen metric so far. A button in its corner, which can be clicked while an inventory or the
 * chat is open, folds it down to the clock alone. It only shows while a session is running, and the Lab works the same
 * with it switched off.
 */
public final class LabHud extends HudWidget {

    private static final int PAD = 4;
    private static final int LINE_H = 9;
    private static final int BUTTON_SIZE = 9;
    private static final int BUTTON_GAP = 6; // between the text of the top row and the button
    private static final long REPORT_MILLIS = 1000; // how often the figures are worked out again

    private SessionReport report;
    private long reportMillis;

    public LabHud() {
        super("lab_session", "Efficiency Lab session", 0.008f, 0.25f);
    }

    @Override
    public boolean isSwitchedOn() {
        return LabSettings.showHud;
    }

    @Override
    public boolean isEnabled() {
        return isSwitchedOn() && Lab.active() != null;
    }

    private String title() {
        Session active = Lab.active();
        // Also what the HUD editor shows, where there is usually no session running.
        if (active == null) return "Farming session";
        return active.displayName();
    }

    private String time() {
        Session active = Lab.active();
        if (active == null) return "0:00:00";
        return Fmt.clock(SessionTimes.tracked(active, System.currentTimeMillis()))
                + (Lab.recorder().isPaused() ? "  paused" : "");
    }

    /** The second of the two figures: skill XP, or coins if XP is already the chosen metric. */
    private static Metric secondMetric() {
        return LabSettings.metric() == Metric.XP_PER_HOUR ? Metric.NET_PER_HOUR : Metric.XP_PER_HOUR;
    }

    /** Whether there is a third figure: the activity's own count, unless it is already one of the two. */
    private static boolean showsOwnCount() {
        Session active = Lab.active();
        return active != null && ActivityStats.primaryKey(active.activityType()) != null
                && LabSettings.metric() != Metric.ACTIVITY_PER_HOUR;
    }

    private String figure(Metric metric) {
        Session active = Lab.active();
        if (active == null) return metric.shortLabel + ": -";
        if (metric == Metric.ACTIVITY_PER_HOUR) {
            report(active);
            return ActivityStats.liveLabel(active.activityType()) + ": " + Fmt.measure(ActivityStats.liveRate(this.report));
        }
        return itemOrXpFigure(metric, active);
    }

    private void report(Session active) {
        long now = System.currentTimeMillis();
        if (this.report == null || this.report.session != active || now - this.reportMillis > REPORT_MILLIS) {
            this.report = Lab.report(active);
            this.reportMillis = now;
        }
    }

    private String itemOrXpFigure(Metric metric, Session active) {
        report(active);
        // Sack items are only reported every half a minute or so. Until something has been seen,
        // the item figures are not zero, they are not known yet.
        boolean ofItems = metric == Metric.NET_PER_HOUR || metric == Metric.GROSS_PER_HOUR || metric == Metric.ITEMS_PER_HOUR;
        if (ofItems && active.buckets.isEmpty()) return metric.shortLabel + ": waiting";
        if (metric == Metric.XP_PER_HOUR && active.xp.isEmpty()) return metric.shortLabel + ": waiting";
        return metric.shortLabel + ": " + Fmt.measure(this.report.rate(metric));
    }

    // Drawn over inventories and the chat so that its button can be clicked there; elsewhere it stays on the HUD.
    @Override
    public boolean drawnOver(Screen screen) {
        return screen instanceof AbstractContainerScreen<?> || screen instanceof ChatScreen;
    }

    @Override
    public int width(Font font) {
        int button = BUTTON_GAP + BUTTON_SIZE;
        if (LabSettings.hudMinimized) return font.width(time()) + button + PAD * 2;
        int width = Math.max(font.width(title()) + button, font.width(time()));
        width = Math.max(width, Math.max(font.width(figure(LabSettings.metric())), font.width(figure(secondMetric()))));
        if (showsOwnCount()) width = Math.max(width, font.width(figure(Metric.ACTIVITY_PER_HOUR)));
        return width + PAD * 2;
    }

    @Override
    public int height(Font font) {
        if (LabSettings.hudMinimized) return LINE_H - 1 + PAD * 2;
        return LINE_H * (showsOwnCount() ? 5 : 4) - 1 + PAD * 2;
    }

    /** The button in the top right corner, which folds the readout down to its clock and opens it again. */
    private void drawButton(GuiGraphicsExtractor graphics, Font font, boolean hovered) {
        int x = width(font) - PAD - BUTTON_SIZE;
        int y = PAD - 1;
        Theme.roundRect(graphics, x, y, BUTTON_SIZE, BUTTON_SIZE,
                hovered ? Theme.CONTROL_HOVER : Theme.CONTROL, hovered ? Theme.ACCENT : Theme.CONTROL_BORDER);
        int color = hovered ? Theme.TEXT : Theme.TEXT_MUTED;
        // A bar to fold it down; a bar crossed with another, a plus, to open it.
        graphics.fill(x + 2, y + 4, x + BUTTON_SIZE - 2, y + 5, color);
        if (LabSettings.hudMinimized) graphics.fill(x + 4, y + 2, x + 5, y + BUTTON_SIZE - 2, color);
    }

    private static boolean isOverTopRow(float mouseY) {
        return mouseY < PAD + LINE_H;
    }

    @Override
    public void extractHovered(GuiGraphicsExtractor graphics, Font font, float mouseX, float mouseY) {
        if (isOverTopRow(mouseY)) drawButton(graphics, font, true);
    }

    /** A click anywhere on the top row presses the button. */
    @Override
    public boolean mouseClicked(Font font, float mouseX, float mouseY, int button) {
        if (button != 0 || !isOverTopRow(mouseY)) return false;
        LabSettings.hudMinimized = !LabSettings.hudMinimized;
        LabSettings.save();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
        return true;
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, Font font) {
        Theme.roundRect(graphics, 0, 0, width(font), height(font), Theme.HUD_BACKDROP);
        boolean paused = Lab.active() != null && Lab.recorder().isPaused();
        drawButton(graphics, font, false);
        if (LabSettings.hudMinimized) {
            graphics.text(font, time(), PAD, PAD, paused ? Theme.DANGER : Theme.ACCENT, false);
            return;
        }
        graphics.text(font, title(), PAD, PAD, Theme.TEXT_MUTED, false);
        graphics.text(font, time(), PAD, PAD + LINE_H, paused ? Theme.DANGER : Theme.ACCENT, false);
        graphics.text(font, figure(LabSettings.metric()), PAD, PAD + LINE_H * 2, Theme.TEXT, false);
        graphics.text(font, figure(secondMetric()), PAD, PAD + LINE_H * 3, Theme.TEXT, false);
        if (showsOwnCount()) {
            graphics.text(font, figure(Metric.ACTIVITY_PER_HOUR), PAD, PAD + LINE_H * 4, Theme.TEXT, false);
        }
    }
}
