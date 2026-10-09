package net.senbler.sentils.client.debug;

/** Debug switches. These are deliberately not saved, so they are off again after a restart. */
public final class DebugSettings {

    private DebugSettings() {}

    public static boolean showLocationHud = false;
    public static boolean showItemData = false;
    public static boolean showSackChanges = false;
}
