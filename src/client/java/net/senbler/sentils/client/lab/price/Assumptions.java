package net.senbler.sentils.client.lab.price;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The choices behind an item valuation. Saved with each session so its report can be reproduced. */
public class Assumptions {

    public enum Mode {
        INSTANT_SELL("Bazaar instant sell", "Insta-sell", "Each item at the highest Bazaar buy order"),
        SELL_ORDER("Bazaar sell order", "Sell order", "Each item at the lowest Bazaar sell offer"),
        NPC("NPC sell price", "NPC", "Each item at what an NPC shop pays"),
        MANUAL_ONLY("Your prices only", "Your prices", "Only the items you have set a price for");

        public final String label;
        /** A word or two, to fit on a button. */
        public final String shortLabel;
        public final String meaning;

        Mode(String label, String shortLabel, String meaning) {
            this.label = label;
            this.shortLabel = shortLabel;
            this.meaning = meaning;
        }

        /** Whether items are valued at Bazaar prices, which is the only time the Bazaar's tax comes off. */
        public boolean usesBazaar() {
            return this == INSTANT_SELL || this == SELL_ORDER;
        }
    }

    public String mode = Mode.INSTANT_SELL.name();
    /** Taken off Bazaar prices, which are listed before tax. The usual rate is 1.25%. */
    public double bazaarTaxPercent = 1.25;
    /** Your own price for an item, by its name in lower case. Used in place of any other price. */
    public Map<String, Double> manualPrices = new LinkedHashMap<>();
    /** Items left out of the value altogether, by name in lower case: ones you keep, say. */
    public Set<String> excluded = new LinkedHashSet<>();

    public Mode modeType() {
        try {
            return Mode.valueOf(this.mode);
        } catch (RuntimeException e) {
            return Mode.INSTANT_SELL;
        }
    }

    public static String key(String itemName) {
        return itemName == null ? "" : itemName.trim().toLowerCase(Locale.ROOT);
    }

    public Assumptions copy() {
        Assumptions copy = new Assumptions();
        copy.mode = this.mode;
        copy.bazaarTaxPercent = this.bazaarTaxPercent;
        copy.manualPrices.putAll(this.manualPrices);
        copy.excluded.addAll(this.excluded);
        return copy;
    }
}
