package net.senbler.sentils.client.lab.model;

/** How a number in a report was come by. A number that could not be come by is UNAVAILABLE, never zero. */
public enum Provenance {
    OBSERVED("observed", "Seen directly by the client"),
    CALCULATED("calculated", "Worked out from observed values alone"),
    ESTIMATED("estimated", "Depends on an assumption, such as a market price"),
    MANUAL("manual", "Entered or corrected by you"),
    UNAVAILABLE("unavailable", "Not enough was observed to say");

    public final String label;
    public final String meaning;

    Provenance(String label, String meaning) {
        this.label = label;
        this.meaning = meaning;
    }

    /** The weaker of two: a value made from an estimate and an observation is an estimate. */
    public Provenance and(Provenance other) {
        if (this == UNAVAILABLE || other == UNAVAILABLE) return UNAVAILABLE;
        if (this == ESTIMATED || other == ESTIMATED) return ESTIMATED;
        if (this == MANUAL || other == MANUAL) return MANUAL;
        if (this == CALCULATED || other == CALCULATED) return CALCULATED;
        return OBSERVED;
    }
}
