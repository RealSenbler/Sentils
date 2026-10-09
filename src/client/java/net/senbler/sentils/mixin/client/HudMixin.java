package net.senbler.sentils.mixin.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.senbler.sentils.client.skyblock.ActionBar;
import net.senbler.sentils.client.stats.ColdSettings;

@Mixin(Hud.class)
public class HudMixin {

    @Shadow @Final private static Identifier POWDER_SNOW_OUTLINE_LOCATION;

    /** Skips the frost drawn around the screen while the player is freezing, which is how Hypixel shows cold. */
    @Inject(method = "extractTextureOverlay", at = @At("HEAD"), cancellable = true)
    private void sentils$hideFrostOverlay(GuiGraphicsExtractor graphics, Identifier texture, float alpha,
                                          CallbackInfo ci) {
        if (ColdSettings.hideFrostOverlay && texture == POWDER_SNOW_OUTLINE_LOCATION) ci.cancel();
    }

    /** Passes on the big text shown in the middle of the screen, and the smaller line under it. */
    @Inject(method = "setTitle", at = @At("HEAD"))
    private void sentils$onTitle(Component title, CallbackInfo ci) {
        if (title != null) ActionBar.titleShown(title);
    }

    @Inject(method = "setSubtitle", at = @At("HEAD"))
    private void sentils$onSubtitle(Component subtitle, CallbackInfo ci) {
        if (subtitle != null) ActionBar.titleShown(subtitle);
    }
}
