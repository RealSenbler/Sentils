package net.senbler.sentils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class ListenerActions {

    public static void onServerChatReceived(String message) {
        if (message.contains("x:") && message.contains("y:") && message.contains("z:")) {
            String xString = (message.split("x: ")[1]);
            String yString = (message.split("y: ")[1]);
            String zString = (message.split("z: ")[1]);

            int x = Integer.parseInt(xString.substring(0, xString.indexOf(",")));
            int y = Integer.parseInt(yString.substring(0, yString.indexOf(",")));
            int z = Integer.parseInt(zString);

            Minecraft.getInstance().player.sendSystemMessage(Component.literal("Creating waypoint at " + x + ", " + y + ", " + z));
        }
    }
    public static void onPlayerChatReceived(String message) {

    }
}