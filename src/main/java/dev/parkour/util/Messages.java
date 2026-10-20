package dev.parkour.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public final class Messages {
    private static final String PREFIX = "&8[&6Maghreb SMP&8] &r";

    private Messages() {}

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', PREFIX + message));
    }
}
