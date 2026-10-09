package net.senbler.sentils.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;

import net.senbler.sentils.client.skyblock.ActionBar;
import net.senbler.sentils.client.skyblock.ItemPickups;
import net.senbler.sentils.client.skyblock.ServerChat;
import net.senbler.sentils.client.skyblock.ServerSounds;

/**
 * Reads chat lines and sounds as the server sent them, and with them the text for above the
 * hotbar, straight from the packet. Other
 * mods edit that text on its way to the screen: SkyHanni, for one, takes the skill XP out of it
 * to show in a display of its own. Reading it here, before any of that, is what lets this mod
 * see the XP as well. Nothing is changed or held back; the packet goes on as it was.
 */
@Mixin(value = ClientPacketListener.class, priority = 500)
public class ClientPacketListenerMixin {

    // A packet's handler is called once on the network thread, only to hand itself over to the
    // game's thread, and then again there. The text is passed on the second time.

    /** A chat packet: flagged for the action bar, which is how Hypixel usually sends that, or a line of chat. */
    @Inject(method = "handleSystemChat", at = @At("HEAD"))
    private void sentils$onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) return;
        if (packet.overlay()) ActionBar.shown(packet.content());
        else ServerChat.shown(packet.content());
    }

    /** An item on the ground being picked up, by this player or by another near them. */
    @Inject(method = "handleTakeItemEntity", at = @At("HEAD"))
    private void sentils$onTakeItem(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread() && mc.player != null && packet.getPlayerId() == mc.player.getId()) ItemPickups.pickedUp();
    }

    /** A sound played at a place in the world, which is how Hypixel plays the ones that mark something. */
    @Inject(method = "handleSoundEvent", at = @At("HEAD"))
    private void sentils$onSound(ClientboundSoundPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) {
            ServerSounds.played(packet.getSound().value().location().getPath(), packet.getVolume());
        }
    }

    /** The packet that is for the action bar and nothing else. */
    @Inject(method = "setActionBarText", at = @At("HEAD"))
    private void sentils$onActionBarText(ClientboundSetActionBarTextPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) ActionBar.shown(packet.text());
    }
}
