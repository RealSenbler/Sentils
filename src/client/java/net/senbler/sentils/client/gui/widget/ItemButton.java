package net.senbler.sentils.client.gui.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import net.senbler.sentils.client.gui.Theme;

/** A square button showing an item, for picking one of a row of choices; its name is the tooltip. */
public class ItemButton extends AbstractButton {

    private final ItemStack icon;
    private final boolean selected;
    private final Runnable action;

    public ItemButton(int x, int y, int size, ItemStack icon, Component name, boolean selected, Runnable action) {
        super(x, y, size, size, name);
        this.icon = icon;
        this.selected = selected;
        this.action = action;
        this.setTooltip(Tooltip.create(name));
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.action.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean lit = this.isHoveredOrFocused();
        Theme.roundRect(graphics, this.getX(), this.getY(), this.width, this.height,
                lit ? Theme.CONTROL_HOVER : this.selected ? Theme.CONTROL : Theme.FIELD,
                this.selected ? Theme.ACCENT : Theme.CONTROL_BORDER);
        graphics.item(this.icon, this.getX() + (this.width - 16) / 2, this.getY() + (this.height - 16) / 2);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
