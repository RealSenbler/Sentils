package net.senbler.sentils.client.lab.model;

/**
 * A named way of doing an activity, described by the player: the mod does not try to detect any
 * of it. Sessions point at a setup so that they can be grouped and compared by it.
 */
public class Setup {
    public String id;
    public String name = "";
    public String tool = "";
    public String pet = "";
    public String notes = ""; // armor, buffs, route: whatever else sets it apart
}
