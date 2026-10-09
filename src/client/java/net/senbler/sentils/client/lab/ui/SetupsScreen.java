package net.senbler.sentils.client.lab.ui;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.senbler.sentils.client.gui.elementa.EField;
import net.senbler.sentils.client.gui.screen.ListEditorScreen;
import net.senbler.sentils.client.lab.Lab;
import net.senbler.sentils.client.lab.model.Setup;

/**
 * The player's named setups. Everything about one is typed in: the mod does not try to work
 * out what tool or pet is in use.
 */
public class SetupsScreen extends ListEditorScreen {

    private static final String[] COLUMNS = {"NAME", "TOOL", "PET", "ARMOR, BUFFS, ROUTE..."};
    private static final float[] SHARES = {0.24f, 0.22f, 0.2f, 0.34f};

    private final List<Setup> setups = Lab.store().setups();
    private final int[] columnX = new int[COLUMNS.length];
    private final int[] widths = new int[COLUMNS.length];

    public SetupsScreen(Screen parent) {
        super(parent, Component.literal("Setups"), "Name the ways you do an activity, to compare sessions by them",
                "+ Add setup");
        if (this.setups.isEmpty()) addEntry();
    }

    @Override
    protected void addEntry() {
        Lab.newSetup();
    }

    private EField field(int column, String value, Consumer<String> onChange) {
        EField box = new EField(this.font, this.columnX[column], 0, this.widths[column], FIELD_H,
                Component.literal(COLUMNS[column]));
        box.setMaxLength(80);
        box.setValue(value);
        box.setResponder(onChange);
        return box;
    }

    /** Shares the row's width out among the columns. */
    private void measure() {
        int total = this.innerRight - this.innerLeft - GAP * (COLUMNS.length - 1);
        int x = this.innerLeft;
        for (int i = 0; i < COLUMNS.length; i++) {
            this.widths[i] = Math.round(total * SHARES[i]);
            this.columnX[i] = x;
            x += this.widths[i] + GAP;
        }
    }

    @Override
    protected void buildRows() {
        for (Setup setup : this.setups) {
            addRow(() -> this.setups.remove(setup),
                    field(0, setup.name, text -> setup.name = text.trim()),
                    field(1, setup.tool, text -> setup.tool = text.trim()),
                    field(2, setup.pet, text -> setup.pet = text.trim()),
                    field(3, setup.notes, text -> setup.notes = text.trim()));
        }
    }

    @Override
    protected void save() {
        // A setup that was never given a name is not one.
        this.setups.removeIf(setup -> setup.name.isBlank());
        Lab.store().saveSetups();
    }

    @Override
    protected void addColumnLabels() {
        measure();
        for (int i = 0; i < COLUMNS.length; i++) columnLabel(COLUMNS[i], this.columnX[i]);
    }
}
