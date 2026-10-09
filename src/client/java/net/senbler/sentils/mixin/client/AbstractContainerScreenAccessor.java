package net.senbler.sentils.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    /** The slot under the mouse, or null. */
    @Accessor("hoveredSlot")
    Slot sentils$getHoveredSlot();
}
