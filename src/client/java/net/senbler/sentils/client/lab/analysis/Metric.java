package net.senbler.sentils.client.lab.analysis;

/** A way of scoring a session, as an amount per tracked hour, for trends and comparisons. */
public enum Metric {
    NET_PER_HOUR("Est. net coins/hour", "Net/h", "Estimated value of items gained, less items lost and costs"),
    GROSS_PER_HOUR("Est. item value/hour", "Value/h", "Estimated value of items gained"),
    PURSE_PER_HOUR("Purse change/hour", "Purse/h", "Coins actually gained or spent, as seen on the scoreboard"),
    ITEMS_PER_HOUR("Items/hour", "Items/h", "Number of items gained, of any kind"),
    XP_PER_HOUR("Skill XP/hour", "XP/h", "XP in the activity's own skill, or in all skills if it has none"),
    ACTIVITY_PER_HOUR("Activity count/hour", "Count/h",
            "What the activity is counted in: sea creatures fished up, slayer bosses killed");

    public final String label;
    /** A few letters, for the heading of a narrow column. */
    public final String shortLabel;
    public final String meaning;

    Metric(String label, String shortLabel, String meaning) {
        this.label = label;
        this.shortLabel = shortLabel;
        this.meaning = meaning;
    }

    public static Metric parse(String name) {
        try {
            return valueOf(name);
        } catch (RuntimeException e) {
            return NET_PER_HOUR;
        }
    }

    public Metric next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
