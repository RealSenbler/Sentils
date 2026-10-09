package net.senbler.sentils.client.lab.price;

import net.senbler.sentils.client.lab.model.Provenance;

/** Puts a price on one of an item, going by a price snapshot and the player's assumptions. */
public final class Valuation {

    private Valuation() {}

    /**
     * What one of an item is taken to be worth and why. The price is null if there is none to
     * go by, and then the item is left out of every total rather than counted as worth nothing.
     */
    public record Price(Double unit, String source, Provenance provenance, boolean excluded) {
        public boolean isKnown() {
            return this.unit != null;
        }
    }

    private static final Price EXCLUDED = new Price(null, "left out by you", Provenance.UNAVAILABLE, true);
    private static final Price NONE = new Price(null, "no price", Provenance.UNAVAILABLE, false);

    public static Price price(String itemName, String itemId, PriceSnapshot prices, Assumptions assumptions) {
        String key = Assumptions.key(itemName);
        if (assumptions.excluded.contains(key)) return EXCLUDED;

        Double manual = assumptions.manualPrices.get(key);
        if (manual != null) return new Price(manual, "your price", Provenance.MANUAL, false);

        Assumptions.Mode mode = assumptions.modeType();
        PriceSnapshot.Entry entry = prices != null ? prices.get(itemId) : null;
        if (mode == Assumptions.Mode.MANUAL_ONLY || entry == null) return NONE;

        // Bazaar prices are listed before the tax the seller pays.
        double afterTax = 1.0 - Math.clamp(assumptions.bazaarTaxPercent, 0.0, 100.0) / 100.0;
        if (mode == Assumptions.Mode.INSTANT_SELL && entry.instantSell != null) {
            return new Price(entry.instantSell * afterTax, "Bazaar instant sell", Provenance.ESTIMATED, false);
        }
        if (mode == Assumptions.Mode.SELL_ORDER && entry.sellOrder != null) {
            return new Price(entry.sellOrder * afterTax, "Bazaar sell order", Provenance.ESTIMATED, false);
        }
        // An item that is not on the Bazaar can still be worth what an NPC pays for it.
        if (entry.npc != null && entry.npc > 0) {
            String source = mode == Assumptions.Mode.NPC ? "NPC sell price" : "NPC sell price (not on Bazaar)";
            return new Price(entry.npc, source, Provenance.ESTIMATED, false);
        }
        return NONE;
    }
}
