package net.senbler.sentils.client.stats;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.senbler.sentils.client.gui.Theme;
import net.senbler.sentils.client.hud.HudWidget;

/** Shows the player's cold and a countdown to when it is expected to reach the maximum. */
public final class ColdHud extends HudWidget {

    private static final int PAD = 4;
    private static final String COLD_LABEL = "Cold: ";
    private static final String TIMER_LABEL = "  " + ColdTracker.MAX_COLD + " in: ";
    private static final long URGENT_MILLIS = 30_000;

    public ColdHud() {
        super("cold", "Cold timer", 0.008f, 0.1f);
    }

    @Override
    public boolean isSwitchedOn() {
        return ColdSettings.coldTimer;
    }

    @Override
    public boolean isEnabled() {
        return isSwitchedOn() && ColdTracker.cold() > 0;
    }

    private static String timer() {
        long millis = ColdTracker.millisUntilMax();
        if (millis < 0) {
            // Also what the HUD editor shows, where there is usually no cold to time.
            if (ColdTracker.cold() == 0) return "-:--";
            return ColdTracker.isStalled() ? "not rising" : "estimating...";
        }
        long seconds = (millis + 999) / 1000;
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    @Override
    public int width(Font font) {
        return font.width(COLD_LABEL + ColdTracker.cold() + TIMER_LABEL + timer()) + PAD * 2;
    }

    @Override
    public int height(Font font) {
        return 8 + PAD * 2;
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, Font font) {
        Theme.roundRect(graphics, 0, 0, width(font), height(font), Theme.HUD_BACKDROP);

        String cold = Integer.toString(ColdTracker.cold());
        long millis = ColdTracker.millisUntilMax();
        int timerColor = millis < 0 ? Theme.TEXT_MUTED : millis <= URGENT_MILLIS ? Theme.DANGER : Theme.ACCENT;

        int x = PAD;
        graphics.text(font, COLD_LABEL, x, PAD, Theme.TEXT_MUTED, false);
        x += font.width(COLD_LABEL);
        graphics.text(font, cold, x, PAD, Theme.ACCENT, false);
        x += font.width(cold);
        graphics.text(font, TIMER_LABEL, x, PAD, Theme.TEXT_MUTED, false);
        x += font.width(TIMER_LABEL);
        graphics.text(font, timer(), x, PAD, timerColor, false);
    }
}
