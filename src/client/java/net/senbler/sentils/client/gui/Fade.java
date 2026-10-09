package net.senbler.sentils.client.gui;

/** A value that glides between 0 and 1, for animating a control as it is hovered or switched. */
public final class Fade {

    private static final long MAX_STEP_MILLIS = 50; // so a control that was not drawn for a while does not jump

    private final float millis;
    private float progress;
    private long lastMillis;

    /** A fade that takes the given time to go all the way, starting at 1 if on and 0 if not. */
    public Fade(float millis, boolean on) {
        this.millis = millis;
        this.progress = on ? 1f : 0f;
    }

    /** Moves towards 1 if on and 0 if not, by the time since the last call. Returns the eased value. */
    public float step(boolean on) {
        long now = System.currentTimeMillis();
        float step = Math.min(now - this.lastMillis, MAX_STEP_MILLIS) / this.millis;
        this.lastMillis = now;
        this.progress = on ? Math.min(1f, this.progress + step) : Math.max(0f, this.progress - step);
        return this.progress * this.progress * (3f - 2f * this.progress);
    }
}
