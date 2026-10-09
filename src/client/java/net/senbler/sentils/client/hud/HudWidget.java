package net.senbler.sentils.client.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/**
 * Something drawn on the in-game HUD that the player can move and resize in the HUD editor.
 * Register an instance with HudManager and it is drawn, saved and editable automatically.
 */
public abstract class HudWidget {

    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 4f;

    private final String id;
    private final String name;
    private final float defaultX;
    private final float defaultY;

    // Top-left corner as a fraction of the screen, so the layout survives window and GUI scale changes.
    private float x;
    private float y;
    private float scale = 1f;

    /**
     * @param id       key used in the saved layout file; never change it once released
     * @param name     label shown in the HUD editor
     * @param defaultX default left edge, as a fraction of the screen width
     * @param defaultY default top edge, as a fraction of the screen height
     */
    protected HudWidget(String id, String name, float defaultX, float defaultY) {
        this.id = id;
        this.name = name;
        this.defaultX = defaultX;
        this.defaultY = defaultY;
        reset();
    }

    /** Whether the player has the widget's setting switched on. Only then does the editor offer it. */
    public abstract boolean isSwitchedOn();

    /** Whether the widget should be drawn in game right now: switched on, and with something to show. */
    public abstract boolean isEnabled();

    /**
     * Whether the widget is drawn on top of the given open screen rather than on the in-game
     * HUD underneath it. isEnabled still decides whether it is drawn at all.
     */
    public boolean drawnOver(Screen screen) {
        return false;
    }

    /**
     * Draws anything extra for while the mouse is over the widget on a screen it is drawn over.
     * Called after extract, with the same position and scale applied. The mouse position is
     * measured from the widget's top-left corner, in the units extract draws in.
     */
    public void extractHovered(GuiGraphicsExtractor graphics, Font font, float mouseX, float mouseY) {
    }

    /**
     * Called when the widget is clicked on a screen it is drawn over, with the mouse position as
     * for extractHovered. Returns whether the click was used; the screen does not get one that was.
     */
    public boolean mouseClicked(Font font, float mouseX, float mouseY, int button) {
        return false;
    }

    /** Unscaled width of what extract draws. */
    public abstract int width(Font font);

    /** Unscaled height of what extract draws. */
    public abstract int height(Font font);

    /** Draws the widget with its top-left corner at (0, 0); position and scale are already applied. */
    public abstract void extract(GuiGraphicsExtractor graphics, Font font);

    public String id() {
        return this.id;
    }

    public String name() {
        return this.name;
    }

    public float x() {
        return this.x;
    }

    public float y() {
        return this.y;
    }

    public float scale() {
        return this.scale;
    }

    public void reset() {
        place(this.defaultX, this.defaultY, 1f);
    }

    public void place(float x, float y, float scale) {
        this.x = Float.isFinite(x) ? Math.clamp(x, 0f, 1f) : this.defaultX;
        this.y = Float.isFinite(y) ? Math.clamp(y, 0f, 1f) : this.defaultY;
        this.scale = Float.isFinite(scale) ? Math.clamp(scale, MIN_SCALE, MAX_SCALE) : 1f;
    }

    public int scaledWidth(Font font) {
        return Math.round(width(font) * this.scale);
    }

    public int scaledHeight(Font font) {
        return Math.round(height(font) * this.scale);
    }

    /** Left edge in screen pixels, kept on screen. */
    public int pixelX(Font font, int screenWidth) {
        return Math.clamp(Math.round(this.x * screenWidth), 0, Math.max(0, screenWidth - scaledWidth(font)));
    }

    /** Top edge in screen pixels, kept on screen. */
    public int pixelY(Font font, int screenHeight) {
        return Math.clamp(Math.round(this.y * screenHeight), 0, Math.max(0, screenHeight - scaledHeight(font)));
    }

    /** Whether the screen pixel is on the widget. */
    public boolean isOver(Font font, double mouseX, double mouseY, int screenWidth, int screenHeight) {
        int left = pixelX(font, screenWidth);
        int top = pixelY(font, screenHeight);
        return mouseX >= left && mouseX < left + scaledWidth(font)
                && mouseY >= top && mouseY < top + scaledHeight(font);
    }

    /** A screen x measured from the widget's left edge, in the units extract draws in. */
    public float localX(Font font, double mouseX, int screenWidth) {
        return (float) (mouseX - pixelX(font, screenWidth)) / this.scale;
    }

    /** A screen y measured from the widget's top edge, in the units extract draws in. */
    public float localY(Font font, double mouseY, int screenHeight) {
        return (float) (mouseY - pixelY(font, screenHeight)) / this.scale;
    }

    /** Moves the top-left corner to the given screen pixel, kept on screen. */
    public void moveTo(Font font, int pixelX, int pixelY, int screenWidth, int screenHeight) {
        int maxX = Math.max(0, screenWidth - scaledWidth(font));
        int maxY = Math.max(0, screenHeight - scaledHeight(font));
        place((float) Math.clamp(pixelX, 0, maxX) / screenWidth,
                (float) Math.clamp(pixelY, 0, maxY) / screenHeight, this.scale);
    }
}
