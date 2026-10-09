package net.senbler.sentils.client.lab.store;

import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.senbler.sentils.client.lab.model.Session;

/**
 * Brings a session saved by an older version of the mod up to the current format. Each step
 * takes a record from one version to the next, so a record of any age goes through however
 * many it needs. To change the format: add a step here and raise Session.CURRENT_VERSION.
 */
public final class Migrations {

    private Migrations() {}

    /** Changes a record, as JSON, from one version of the format to the next. */
    public interface Step {
        void apply(JsonObject session);
    }

    // STEPS.get(0) takes version 1 to version 2, and so on. There is only version 1 so far.
    private static final List<Step> STEPS = List.of();

    public static boolean upgrade(JsonObject session) {
        return upgrade(session, STEPS, Session.CURRENT_VERSION);
    }

    /**
     * Upgrades the record in place. Returns false, leaving it alone, if it is from a newer
     * version than this one understands: guessing at it could damage it.
     */
    public static boolean upgrade(JsonObject session, List<Step> steps, int current) {
        JsonElement saved = session.get("formatVersion");
        int version = saved != null && saved.isJsonPrimitive() && saved.getAsJsonPrimitive().isNumber()
                ? saved.getAsInt() : 1; // the first version is the only one that could lack the field
        if (version > current || version < 1) return false;
        while (version < current) {
            steps.get(version - 1).apply(session);
            version++;
        }
        session.addProperty("formatVersion", current);
        return true;
    }
}
