package net.senbler.sentils.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import net.senbler.sentils.client.mining.AutomatonGlow;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    /** Draws the glowing outline on the Automatons the player can see. Nothing is sent to the server. */
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void sentils$glowAutomatons(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (AutomatonGlow.isGlowing(entity)) cir.setReturnValue(true);
    }
}
