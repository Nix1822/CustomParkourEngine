package dev.parkour.service;

import dev.parkour.data.CourseRepository;
import dev.parkour.data.Storage;
import dev.parkour.model.Run;
import dev.parkour.model.Track;
import dev.parkour.util.Messages;
import dev.parkour.util.TimeFormat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// run state: start, checkpoints, finish, falls
public final class RunService {
    private final JavaPlugin plugin;
    private final CourseRepository courses;
    private final Storage storage;
    private final AntiCheat antiCheat;

    private final Map<UUID, Run> runs = new HashMap<>();
    private final Set<UUID> pluginTeleports = new HashSet<>();

    public RunService(JavaPlugin plugin, CourseRepository courses, Storage storage, AntiCheat antiCheat) {
        this.plugin = plugin;
        this.courses = courses;
        this.storage = storage;
        this.antiCheat = antiCheat;
    }

    public Map<UUID, Run> activeRuns() { return runs; }
    public Run getRun(Player player) { return runs.get(player.getUniqueId()); }
    public boolean isPluginTeleport(Player player) { return pluginTeleports.contains(player.getUniqueId()); }
    public void clearAll() { runs.clear(); }
    public void dropRunsFor(Track track) { runs.values().removeIf(r -> r.getTrack() == track); }
    public void removeRun(Player player) { runs.remove(player.getUniqueId()); }


    public boolean startRun(Player player, Track track) {
        if (!track.isComplete()) { Messages.send(player, "§cThis track is not finished being set up."); return false; }
        String problem = antiCheat.stateProblem(player);
        if (problem != null) { Messages.send(player, "§c" + problem); return false; }
        runs.put(player.getUniqueId(), new Run(track));
        Messages.send(player, "§eStarted " + track.getName() + ". Good luck!");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
        return true;
    }

    public void cancelRun(Player player, String reason) {
        if (runs.remove(player.getUniqueId()) != null) Messages.send(player, "§cRun cancelled: " + reason);
    }

    // called when the block under the player changes
    public void handlePad(Player player, Block block) {
        Run run = getRun(player);
        if (run == null) {
            for (Track track : courses.all()) {
                if (sameBlock(block, track.getStart())) { startRun(player, track); return; }
            }
            return;
        }
        List<Location> cps = run.getCheckpoints();
        for (int i = 0; i < cps.size(); i++) {
            if (!sameBlock(block, cps.get(i))) continue;
            if (run.reach(i)) {
                Messages.send(player, "§aCheckpoint " + (i + 1) + "/" + cps.size() + " - " + TimeFormat.format(run.getElapsedMs()));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
            }
            return;
        }
        if (sameBlock(block, run.getTrack().getFinish())) finishRun(player, run);
    }

    private void finishRun(Player player, Run run) {
        int total = run.getCheckpoints().size();
        if (plugin.getConfig().getBoolean("require-all-checkpoints", false) && run.getReached() < total) {
            Messages.send(player, "§cYou still need to reach checkpoint " + total + " before finishing.");
            return;
        }
        final long time = run.getElapsedMs(); // timer halts here
        runs.remove(player.getUniqueId());

        if (time < plugin.getConfig().getLong("anticheat.min-finish-time-ms", 3000)) {
            Messages.send(player, "§cRun cancelled: finish time is not realistic.");
            return;
        }
        Messages.send(player, "§6Finished " + run.getTrack().getName() + " in " + TimeFormat.format(time));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.05);

        final UUID uuid = player.getUniqueId();
        storage.saveIfBestAsync(run.getTrack().getName(), uuid, player.getName(), time).whenComplete((best, err) -> {
            if (err != null) { plugin.getLogger().warning("Could not save time: " + err.getMessage()); return; }
            if (!best || !plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) Messages.send(online, "§bNew personal best!");
            });
        });
    }


    public boolean shouldRespawn(Player player, Run run, Location to) {
        return to.getY() < run.getTrack().getFailYLevel() || isHazardContact(to);
    }

    private boolean isHazardContact(Location loc) {
        if (loc.getWorld() == null) return false;
        return isHazard(loc.getBlock().getType())
                || isHazard(loc.clone().add(0, 1, 0).getBlock().getType())
                || isHazard(loc.clone().subtract(0, 0.1, 0).getBlock().getType());
    }

    private boolean isHazard(Material m) { return m == Material.WATER || m == Material.LAVA; }

    // timer keeps running
    public void teleportToLastCheckpoint(Player player) {
        Run run = getRun(player);
        if (run != null) teleportSafely(player, run.getLastCheckpoint());
    }

    public void teleportSafely(Player player, Location target) {
        if (target == null) return;
        Location dest = target.clone().add(0.5, 1.0, 0.5);
        dest.setYaw(player.getLocation().getYaw());
        dest.setPitch(player.getLocation().getPitch());
        pluginTeleports.add(player.getUniqueId());
        try {
            player.teleport(dest);
        } finally {
            pluginTeleports.remove(player.getUniqueId());
        }
        player.setFallDistance(0);
        player.setVelocity(new Vector(0, 0, 0));
    }

    private static boolean sameBlock(Block block, Location loc) {
        return loc != null && loc.getWorld() != null && block.getWorld().equals(loc.getWorld())
                && block.getX() == loc.getBlockX() && block.getY() == loc.getBlockY() && block.getZ() == loc.getBlockZ();
    }
}
