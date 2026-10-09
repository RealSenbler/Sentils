package net.senbler.sentils.client.notifications;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.senbler.sentils.client.gui.elementa.EField;
import net.senbler.sentils.client.gui.screen.ListEditorScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NotificationConfigScreen extends ListEditorScreen {

    private static final int TICKS_W = 40;
    private static final String DEFAULT_TICKS = "20";

    private final Map<String, String> titles;
    private final List<Entry> entries = new ArrayList<>();

    private int flexW;
    private int titleX;
    private int ticksX;

    /** The values being edited; written back to the titles map on close. */
    private static class Entry {
        String trigger;
        String title;
        String ticks;

        Entry(String trigger, String title, String ticks) {
            this.trigger = trigger;
            this.title = title;
            this.ticks = ticks;
        }
    }

    public NotificationConfigScreen(Screen parent, Map<String, String> titles) {
        super(parent, Component.literal("Chat Notifications"),
                "Show a title when a chat message contains a trigger", "+ Add trigger");
        this.titles = titles;

        for (Map.Entry<String, String> entry : titles.entrySet()) {
            String[] parts = entry.getValue().split("\\|", 2);
            this.entries.add(new Entry(entry.getKey(), parts[0], parts.length > 1 ? parts[1] : DEFAULT_TICKS));
        }
        if (this.entries.isEmpty()) {
            addEntry();
        }
    }

    @Override
    protected void addEntry() {
        this.entries.add(new Entry("", "", DEFAULT_TICKS));
    }

    @Override
    protected void buildRows() {
        for (Entry entry : this.entries) {
            EField trigger = new EField(this.font, this.innerLeft, 0, this.flexW, FIELD_H, Component.literal("Message"));
            trigger.setMaxLength(100);
            trigger.setValue(entry.trigger);
            trigger.setHint(Component.literal("message trigger"));
            trigger.setResponder(text -> entry.trigger = text);

            EField title = new EField(this.font, this.titleX, 0, this.flexW, FIELD_H, Component.literal("Title"));
            title.setMaxLength(100);
            title.setValue(entry.title);
            title.setHint(Component.literal("title to show"));
            title.setResponder(text -> entry.title = text);

            EField ticks = new EField(this.font, this.ticksX, 0, TICKS_W, FIELD_H, Component.literal("Duration"));
            ticks.setDigitsOnly(true);
            ticks.setMaxLength(4);
            ticks.setValue(entry.ticks);
            ticks.setHint(Component.literal(DEFAULT_TICKS));
            ticks.setResponder(text -> entry.ticks = text);

            addRow(() -> this.entries.remove(entry), trigger, title, ticks);
        }
    }

    @Override
    protected void save() {
        this.titles.clear();
        for (Entry entry : this.entries) {
            String trigger = entry.trigger.trim();
            String title = entry.title.trim();

            int ticks;
            try {
                ticks = Integer.parseInt(entry.ticks.trim());
            } catch (NumberFormatException e) {
                ticks = Integer.parseInt(DEFAULT_TICKS);
            }

            if (!trigger.isEmpty() && !title.isEmpty()) {
                this.titles.put(trigger.toLowerCase(), title + "|" + ticks);
            }
        }
        TitlesConfig.save(this.titles);
    }

    @Override
    protected void addColumnLabels() {
        this.flexW = (this.innerRight - this.innerLeft - TICKS_W - GAP * 2) / 2;
        this.titleX = this.innerLeft + this.flexW + GAP;
        this.ticksX = this.titleX + this.flexW + GAP;
        columnLabel("MESSAGE", this.innerLeft);
        columnLabel("TITLE", this.titleX);
        columnLabel("TICKS", this.ticksX);
    }
}
