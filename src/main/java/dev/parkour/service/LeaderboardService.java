package dev.parkour.service;

import dev.parkour.data.Storage;
import dev.parkour.model.Track;
import dev.parkour.util.Messages;
import dev.parkour.util.TimeFormat;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class LeaderboardService {
    private final JavaPlugin plugin;
    private final Storage storage;

    public LeaderboardService(JavaPlugin plugin, Storage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    // top 3 from the db (async), shown on the main thread
    public void showTop(CommandSender sender, Track track) {
        storage.getTopAsync(track.getName(), 3).whenComplete((top, err) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (err != null) {
                    plugin.getLogger().warning("Could not load leaderboard: " + err.getMessage());
                    Messages.send(sender, "§cCould not load the leaderboard, try again later.");
                    return;
                }
                render(sender, track, top);
            });
        });
    }

    private void render(CommandSender sender, Track track, List<Storage.TopEntry> top) {
        Messages.send(sender, "§6Top 3 - " + track.getName());
        if (top.isEmpty()) Messages.send(sender, "§7No times recorded yet.");
        for (int i = 0; i < top.size(); i++) {
            Storage.TopEntry e = top.get(i);
            Messages.send(sender, "§e" + (i + 1) + ". " + e.name() + " - " + TimeFormat.format(e.time()));
        }
    }
}
