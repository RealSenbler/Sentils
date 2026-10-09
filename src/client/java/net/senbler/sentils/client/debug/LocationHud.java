package net.senbler.sentils.client.debug;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.senbler.sentils.client.gui.Theme;
import net.senbler.sentils.client.hud.HudWidget;
import net.senbler.sentils.client.skyblock.SkyblockLocation;

/** Debug overlay showing the island the mod currently thinks the player is on. */
public final class LocationHud extends HudWidget {

    private static final int PAD = 4;
    private static final String LABEL = "Sentils island: ";

    public LocationHud() {
        super("location", "Detected island", 0.008f, 0.015f);
    }

    @Override
    public boolean isSwitchedOn() {
        return DebugSettings.showLocationHud;
    }

    @Override
    public boolean isEnabled() {
        return isSwitchedOn();
    }

    private static String value() {
        String island = SkyblockLocation.getIsland();
        return island != null ? island : "none detected";
    }

    @Override
    public int width(Font font) {
        return font.width(LABEL) + font.width(value()) + PAD * 2;
    }

    @Override
    public int height(Font font) {
        return 8 + PAD * 2;
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, Font font) {
        Theme.roundRect(graphics, 0, 0, width(font), height(font), Theme.HUD_BACKDROP);
        graphics.text(font, LABEL, PAD, PAD, Theme.TEXT_MUTED, false);
        graphics.text(font, value(), PAD + font.width(LABEL), PAD,
                SkyblockLocation.getIsland() != null ? Theme.ACCENT : Theme.DANGER, false);
    }
}
