package net.senbler.sentils.client.keybinds;

import com.mojang.blaze3d.platform.InputConstants;

import gg.essential.universal.UKeyboard;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.senbler.sentils.client.gui.elementa.EButton;
import net.senbler.sentils.client.gui.elementa.EField;
import net.senbler.sentils.client.gui.screen.ListEditorScreen;
import net.senbler.sentils.client.keybinds.KeybindsConfig.Binding;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class KeybindConfigScreen extends ListEditorScreen {

    private static final int KEY_W = 80;

    private final List<Binding> entries = new ArrayList<>();
    private final Map<Binding, EButton> keyButtons = new IdentityHashMap<>();
    private Binding listening; // the entry waiting for a key press, if any

    public KeybindConfigScreen(Screen parent) {
        super(parent, Component.literal("Keybinds"),
                "Send a command or chat message when a key is pressed", "+ Add keybind");

        for (Binding binding : KeybindsConfig.bindings) {
            this.entries.add(new Binding(binding.key, binding.command));
        }
        if (this.entries.isEmpty()) {
            addEntry();
        }
    }

    @Override
    protected void addEntry() {
        this.entries.add(new Binding());
    }

    @Override
    protected void buildRows() {
        this.keyButtons.clear();
        int commandX = this.innerLeft + KEY_W + GAP;

        for (Binding entry : this.entries) {
            EButton keyButton = new EButton(this.innerLeft, 0, KEY_W, FIELD_H, Component.empty(),
                    EButton.Style.SECONDARY, () -> {
                        this.listening = entry;
                        refreshKeyButtons();
                    });
            this.keyButtons.put(entry, keyButton);

            EField command = new EField(this.font, commandX, 0, this.innerRight - commandX, FIELD_H,
                    Component.literal("Command or message"));
            command.setMaxLength(256);
            command.setValue(entry.command);
            command.setHint(Component.literal("/command or chat message"));
            command.setResponder(text -> entry.command = text);

            addRow(() -> {
                this.entries.remove(entry);
                if (this.listening == entry) this.listening = null;
            }, keyButton, command);
        }
        refreshKeyButtons();
    }

    private void refreshKeyButtons() {
        this.keyButtons.forEach((entry, button) -> {
            boolean waiting = entry == this.listening;
            button.setHighlighted(waiting);
            button.setLabel(waiting ? "Press a key..."
                    : entry.key == KeybindsConfig.UNBOUND ? "Not set"
                    : InputConstants.Type.KEYSYM.getOrCreate(entry.key).getDisplayName().getString());
        });
    }

    @Override
    public void onKeyPressed(int keyCode, char typedChar, UKeyboard.Modifiers modifiers) {
        if (this.listening == null) {
            super.onKeyPressed(keyCode, typedChar, modifiers);
            return;
        }
        if (keyCode <= 0) return; // a typed character, or a key GLFW has no code for

        // Escape clears the binding instead of being bound, as in the vanilla controls screen.
        this.listening.key = keyCode == UKeyboard.KEY_ESCAPE ? KeybindsConfig.UNBOUND : keyCode;
        this.listening = null;
        refreshKeyButtons();
    }

    @Override
    public void onMouseClicked(double mouseX, double mouseY, int mouseButton) {
        // A click anywhere stops waiting for a key; one on a key button starts it again just after.
        if (this.listening != null) {
            this.listening = null;
            refreshKeyButtons();
        }
        super.onMouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void save() {
        List<Binding> saved = new ArrayList<>();
        for (Binding entry : this.entries) {
            String command = entry.command.trim();
            if (!command.isEmpty()) {
                saved.add(new Binding(entry.key, command));
            }
        }
        KeybindsConfig.bindings = saved;
        KeybindsConfig.save();
    }

    @Override
    protected void addColumnLabels() {
        columnLabel("KEY", this.innerLeft);
        columnLabel("COMMAND OR MESSAGE", this.innerLeft + KEY_W + GAP);
    }
}
