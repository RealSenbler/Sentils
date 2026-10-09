package net.senbler.sentils.client.debug;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import net.senbler.sentils.client.gui.Theme;
import net.senbler.sentils.client.hud.HudWidget;
import net.senbler.sentils.mixin.client.AbstractContainerScreenAccessor;

/**
 * Debug overlay describing the SkyBlock item under the mouse in an inventory: its rarity,
 * reforge, stars, enchantments and the other upgrades Hypixel stores in the item's custom data.
 * Anything in the custom data that isn't recognised is listed raw at the end.
 */
public final class ItemDataHud extends HudWidget {

    private static final int PAD = 4;
    private static final int LINE_H = 9;
    private static final int MAX_LINE_W = 320; // longer lines are wrapped
    private static final String INDENT = "  ";

    // The rarity is only written in the lore, as the last line: "LEGENDARY DUNGEON SWORD".
    private static final Pattern RARITY_LINE = Pattern.compile(
            "\\b(VERY SPECIAL|UNCOMMON|COMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL|SUPREME|ADMIN)\\b(.*)");
    private static final Pattern GEM_SLOT_NUMBER = Pattern.compile("_\\d+$");
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    /** One line of text: a key drawn in the accent color, then the rest in the normal text color. */
    private record Line(String key, String text) {}

    // Shown in the HUD editor, where there is no item to describe.
    private static final List<Line> PLACEHOLDER = List.of(
            new Line("SkyBlock item info", ""),
            new Line("", "Hover over an item in an inventory"));

    // The lines are only rebuilt when the hovered item changes.
    private ItemStack shown = ItemStack.EMPTY;
    private List<Line> lines;
    private int textWidth;

    public ItemDataHud() {
        super("item_data", "SkyBlock item info", 0.008f, 0.06f);
    }

    @Override
    public boolean isSwitchedOn() {
        return DebugSettings.showItemData;
    }

    @Override
    public boolean isEnabled() {
        return isSwitchedOn() && !hoveredStack().isEmpty();
    }

    @Override
    public boolean drawnOver(Screen screen) {
        return true;
    }

    /** The item under the mouse in the open inventory screen, or an empty stack. */
    private static ItemStack hoveredStack() {
        if (Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> screen) {
            Slot slot = ((AbstractContainerScreenAccessor) screen).sentils$getHoveredSlot();
            if (slot != null && slot.hasItem()) return slot.getItem();
        }
        return ItemStack.EMPTY;
    }

    private void refresh(Font font) {
        ItemStack hovered = hoveredStack();
        if (this.lines != null && ItemStack.matches(this.shown, hovered)) return;

        this.shown = hovered.copy();
        this.lines = hovered.isEmpty() ? PLACEHOLDER : new Description(font).build(hovered);
        this.textWidth = 0;
        for (Line line : this.lines) {
            this.textWidth = Math.max(this.textWidth, font.width(line.key() + line.text()));
        }
    }

    /**
     * Builds the lines for one item. Each field is removed from the copy of the custom data
     * as it is shown, so whatever is left at the end is what the overlay doesn't know about.
     */
    private static final class Description {

        private final Font font;
        private final List<Line> lines = new ArrayList<>();
        private CompoundTag data = new CompoundTag();

        Description(Font font) {
            this.font = font;
        }

        List<Line> build(ItemStack stack) {
            String name = stack.getHoverName().getString();
            addWrapped(this.lines, this.font, stack.getCount() > 1 ? name + " x" + stack.getCount() : name, "");

            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) this.data = customData.copyTag();
            if (this.data.isEmpty()) {
                this.lines.add(new Line("", "Not a SkyBlock item"));
                return this.lines;
            }

            boolean skyblockItem = this.data.contains("id");
            text("id", "ID");
            rarity(stack);
            int recombobulated = takeInt("rarity_upgrades");
            if (skyblockItem) row("Recombobulated", recombobulated > 0 ? "Yes" : "No");
            name("modifier", "Reforge");
            stars();
            hotPotatoBooks();
            levels("enchantments", "Enchantments");
            gems();
            scrolls();
            name("power_ability_scroll", "Power scroll");

            flag("art_of_war_count", "Art of War", "Applied");
            flag("artOfPeaceApplied", "Art of Peace", "Applied");
            flag("wood_singularity_count", "Wood Singularity", "Applied");
            flag("jalapeno_count", "Jalapeno Book", "Applied");
            flag("ethermerge", "Etherwarp", "Merged");
            count("farming_for_dummies_count", "Farming for Dummies", "");
            count("mana_disintegrator_count", "Mana Disintegrators", "");
            count("tuned_transmission", "Transmission Tuners", "");
            count("polarvoid", "Polarvoid Books", "");
            count("stats_book", "Book of Stats", " kills");

            levels("runes", "Rune");
            name("dye_item", "Dye");
            name("skin", "Skin");
            name("talisman_enrichment", "Enrichment");
            pet();

            count("baseStatBoostPercentage", "Dungeon stat boost", "%");
            count("item_tier", "Dungeon floor", "");
            flag("donated_museum", "Museum", "Donated");
            name("originTag", "Origin");
            created();
            text("uuid", "UUID");

            other();
            return this.lines;
        }

        private void row(String label, String value) {
            addWrapped(this.lines, this.font, label + ": ", value);
        }

        private int takeInt(String key) {
            Tag tag = this.data.remove(key);
            return tag != null ? tag.asInt().orElse(0) : 0;
        }

        private String takeString(String key) {
            Tag tag = this.data.remove(key);
            return tag != null ? tag.asString().orElse(null) : null;
        }

        /** A text field shown exactly as stored. */
        private void text(String key, String label) {
            String value = takeString(key);
            if (value != null) row(label, value);
        }

        /** A text field holding an id like "IMPLOSION_SCROLL", shown as "Implosion Scroll". */
        private void name(String key, String label) {
            String value = takeString(key);
            if (value != null) row(label, pretty(value));
        }

        /** A number shown with a suffix, when it is above zero. */
        private void count(String key, String label, String suffix) {
            int value = takeInt(key);
            if (value > 0) row(label, value + suffix);
        }

        /** A one-time upgrade, shown as the given word when it has been applied. */
        private void flag(String key, String label, String word) {
            if (takeInt(key) > 0) row(label, word);
        }

        /** A compound of name to level, such as the enchantments: "Sharpness 7, Looting 5". */
        private void levels(String key, String label) {
            Tag tag = this.data.remove(key);
            CompoundTag levels = tag != null ? tag.asCompound().orElse(null) : null;
            if (levels == null || levels.isEmpty()) return;

            List<String> parts = new ArrayList<>();
            for (String name : new TreeSet<>(levels.keySet())) {
                parts.add(pretty(name) + " " + levels.getIntOr(name, 0));
            }
            row(parts.size() > 1 ? label + " (" + parts.size() + ")" : label, String.join(", ", parts));
        }

        private void rarity(ItemStack stack) {
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore == null) return;
            List<Component> loreLines = lore.lines();
            for (int i = loreLines.size() - 1; i >= 0; i--) {
                Matcher m = RARITY_LINE.matcher(readable(loreLines.get(i)));
                if (!m.find()) continue;
                row("Rarity", pretty(m.group(1)));
                String type = m.group(2).trim();
                if (!type.isEmpty()) row("Type", pretty(type));
                return;
            }
        }

        private void stars() {
            // Older items store their stars under dungeon_item_level instead.
            int stars = Math.max(takeInt("upgrade_level"), takeInt("dungeon_item_level"));
            if (stars <= 0) return;
            row("Stars", stars > 5 ? stars + " (5 + " + (stars - 5) + " master)" : Integer.toString(stars));
        }

        private void hotPotatoBooks() {
            int books = takeInt("hot_potato_count");
            if (books <= 0) return;
            row("Hot Potato Books", books > 10 ? "10 + " + (books - 10) + " fuming" : Integer.toString(books));
        }

        private void gems() {
            Tag tag = this.data.remove("gems");
            CompoundTag gems = tag != null ? tag.asCompound().orElse(null) : null;
            if (gems == null) return;

            List<String> parts = new ArrayList<>();
            for (String slot : new TreeSet<>(gems.keySet())) {
                // "<slot>_gem" names the gem in a slot that accepts several kinds; it isn't a slot.
                if (slot.equals("unlocked_slots") || slot.endsWith("_gem")) continue;
                Tag value = gems.get(slot);
                String quality = value.asString()
                        .orElseGet(() -> value.asCompound().map(c -> c.getStringOr("quality", "?")).orElse("?"));
                String gem = gems.getString(slot + "_gem").orElse(GEM_SLOT_NUMBER.matcher(slot).replaceAll(""));
                parts.add(pretty(quality) + " " + pretty(gem));
            }
            if (!parts.isEmpty()) row("Gems", String.join(", ", parts));
        }

        private void scrolls() {
            Tag tag = this.data.remove("ability_scroll");
            ListTag scrolls = tag != null ? tag.asList().orElse(null) : null;
            if (scrolls == null || scrolls.size() == 0) return;

            List<String> parts = new ArrayList<>();
            for (int i = 0; i < scrolls.size(); i++) {
                parts.add(pretty(scrolls.getStringOr(i, "?")));
            }
            row("Scrolls", String.join(", ", parts));
        }

        /** Pets keep their details as a JSON string. */
        private void pet() {
            String json = takeString("petInfo");
            if (json == null) return;
            try {
                JsonObject pet = JsonParser.parseString(json).getAsJsonObject();
                row("Pet", (pretty(jsonString(pet, "tier")) + " " + pretty(jsonString(pet, "type"))).trim());
                if (pet.has("exp")) row("Pet XP", String.format(Locale.ROOT, "%,.0f", pet.get("exp").getAsDouble()));
                if (pet.has("heldItem")) row("Pet item", pretty(jsonString(pet, "heldItem")));
                if (pet.has("skin")) row("Pet skin", pretty(jsonString(pet, "skin")));
                if (pet.has("candyUsed") && pet.get("candyUsed").getAsInt() > 0) {
                    row("Pet candy", Integer.toString(pet.get("candyUsed").getAsInt()));
                }
            } catch (RuntimeException e) {
                row("Pet", json);
            }
        }

        private static String jsonString(JsonObject object, String key) {
            JsonElement value = object.get(key);
            return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
        }

        /** When the item was made: milliseconds on newer items, a ready-made date on older ones. */
        private void created() {
            Tag tag = this.data.remove("timestamp");
            if (tag == null) return;
            if (tag instanceof NumericTag number) {
                row("Created", DATE.format(Instant.ofEpochMilli(number.longValue())));
            } else {
                row("Created", tag.asString().orElseGet(tag::toString));
            }
        }

        private void other() {
            if (this.data.isEmpty()) return;
            this.lines.add(new Line("Other data", ""));
            for (String key : new TreeSet<>(this.data.keySet())) {
                addWrapped(this.lines, this.font, INDENT + key + ": ", String.valueOf(this.data.get(key)));
            }
        }
    }

    /** The text of a lore line, without the scrambled characters Hypixel puts around a rarity. */
    private static String readable(Component line) {
        StringBuilder text = new StringBuilder();
        line.visit((style, part) -> {
            if (!style.isObfuscated()) text.append(part);
            return Optional.empty();
        }, Style.EMPTY);
        return text.toString();
    }

    /** Turns an id like "ULTIMATE_WISE" or "heroic" into "Ultimate Wise" or "Heroic". */
    private static String pretty(String id) {
        StringBuilder result = new StringBuilder();
        for (String word : id.trim().split("[_ ]+")) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return result.toString();
    }

    /** Adds key + text as one line, or as several if it is wider than MAX_LINE_W. */
    private static void addWrapped(List<Line> lines, Font font, String key, String text) {
        String rest = key + text;
        String indent = rest.substring(0, rest.length() - rest.stripLeading().length()) + INDENT;
        boolean first = true;
        while (true) {
            String piece = font.width(rest) > MAX_LINE_W ? font.plainSubstrByWidth(rest, MAX_LINE_W) : rest;
            if (piece.length() <= indent.length()) piece = rest; // too narrow to wrap any further

            if (first && piece.length() >= key.length()) {
                lines.add(new Line(key, piece.substring(key.length())));
            } else {
                lines.add(new Line("", piece));
            }
            if (piece.length() == rest.length()) return;
            rest = indent + rest.substring(piece.length());
            first = false;
        }
    }

    /** How many lines fit on the screen at the current scale. */
    private int lineLimit() {
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        return Math.max(2, (int) ((screenHeight / scale() - PAD * 2) / LINE_H));
    }

    @Override
    public int width(Font font) {
        refresh(font);
        return this.textWidth + PAD * 2;
    }

    @Override
    public int height(Font font) {
        refresh(font);
        return Math.min(this.lines.size(), lineLimit()) * LINE_H - 1 + PAD * 2;
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, Font font) {
        Theme.roundRect(graphics, 0, 0, width(font), height(font), Theme.HUD_BACKDROP);

        int limit = lineLimit();
        boolean cut = this.lines.size() > limit;
        int count = cut ? limit - 1 : this.lines.size();
        int y = PAD;
        for (int i = 0; i < count; i++) {
            Line line = this.lines.get(i);
            graphics.text(font, line.key(), PAD, y, Theme.ACCENT, false);
            graphics.text(font, line.text(), PAD + font.width(line.key()), y, Theme.TEXT, false);
            y += LINE_H;
        }
        if (cut) {
            graphics.text(font, "... " + (this.lines.size() - count) + " more lines", PAD, y, Theme.TEXT_MUTED, false);
        }
    }
}
