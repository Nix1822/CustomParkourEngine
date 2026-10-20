package dev.parkour;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ParkourManager {

    private final Main plugin;
    private final Storage storage;

    private final Map<String, Track> tracks = new LinkedHashMap<>();
    private final Map<UUID, Run> runs = new HashMap<>();
    // players we are teleporting ourselves, so the anti-cheat ignores them
    private final Set<UUID> pluginTeleports = new HashSet<>();

    public ParkourManager(Main plugin, Storage storage) {
        this.plugin = plugin;
        this.storage = storage;
        loadTracks();
    }

    // ---------- tracks ----------

    private void loadTracks() {
        tracks.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("tracks");
        if (section == null) {
            return;
        }
        for (String name : section.getKeys(false)) {
            Track track = new Track(name);
            track.setStart(section.getLocation(name + ".start"));

            ConfigurationSection cps = section.getConfigurationSection(name + ".checkpoints");
            if (cps != null) {
                for (String key : cps.getKeys(false)) {
                    Location loc = cps.getLocation(key);
                    try {
                        if (loc != null) {
                            track.getCheckpoints().put(Integer.parseInt(key), loc);
                        }
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Bad checkpoint number '" + key + "' in track " + name);
                    }
                }
            }
            tracks.put(name.toLowerCase(), track);
        }
    }

    public void saveTrack(Track track) {
        String path = "tracks." + track.getName();
        plugin.getConfig().set(path + ".start", track.getStart());
        plugin.getConfig().set(path + ".checkpoints", null);
        for (Map.Entry<Integer, Location> entry : track.getCheckpoints().entrySet()) {
            plugin.getConfig().set(path + ".checkpoints." + entry.getKey(), entry.getValue());
        }
        plugin.saveConfig();
    }

    public Track getTrack(String name) {
        if (name == null) {
            return null;
        }
        return tracks.get(name.toLowerCase());
    }

    public Collection<Track> getTracks() {
        return tracks.values();
    }

    public Track createTrack(String name) {
        Track track = new Track(name);
        tracks.put(name.toLowerCase(), track);
        saveTrack(track);
        return track;
    }

    public boolean deleteTrack(String name) {
        final Track track = tracks.remove(name.toLowerCase());
        if (track == null) {
            return false;
        }
        plugin.getConfig().set("tracks." + track.getName(), null);
        plugin.saveConfig();

        runs.values().removeIf(run -> run.getTrack() == track);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                storage.deleteTrack(track.getName());
            } catch (Exception e) {
                plugin.getLogger().warning("Could not delete times: " + e.getMessage());
            }
        });
        return true;
    }

    // ---------- helpers ----------

    public static String formatTime(long ms) {
        long minutes = ms / 60000;
        long seconds = (ms / 1000) % 60;
        long millis = ms % 1000;
        return String.format("%02d:%02d.%03d", minutes, seconds, millis);
    }

    private boolean sameBlock(Block block, Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        return block.getWorld().equals(loc.getWorld())
                && block.getX() == loc.getBlockX()
                && block.getY() == loc.getBlockY()
                && block.getZ() == loc.getBlockZ();
    }

    private void send(Player player, String text, NamedTextColor color) {
        player.sendMessage(Component.text(text, color));
    }

    public Run getRun(Player player) {
        return runs.get(player.getUniqueId());
    }

    public boolean isPluginTeleport(Player player) {
        return pluginTeleports.contains(player.getUniqueId());
    }

    // ---------- run logic ----------

    // called when a player moves to a new block
    public void handleBlock(Player player, Block block) {
        Run run = getRun(player);

        for (Track track : tracks.values()) {
            if (sameBlock(block, track.getStart())) {
                // already running: stepping on a start block must not reset the timer
                // (use /parkour restart to start over)
                if (run == null) {
                    startRun(player, track);
                }
                return;
            }
        }

        if (run == null) {
            return;
        }

        List<Location> checkpoints = run.getCheckpoints();
        for (int i = 0; i < checkpoints.size(); i++) {
            if (!sameBlock(block, checkpoints.get(i))) {
                continue;
            }
            if (i == run.getReached()) {
                run.addReached();
                send(player, "Checkpoint " + run.getReached() + "/" + checkpoints.size()
                        + " - " + formatTime(run.getElapsedMs()), NamedTextColor.GREEN);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
            } else if (i > run.getReached()) {
                send(player, "You missed checkpoint " + (run.getReached() + 1) + ", go back to it.",
                        NamedTextColor.RED);
            }
            return;
        }

        if (sameBlock(block, run.getTrack().getFinish())) {
            finishRun(player, run);
        }
    }

    public void startRun(Player player, Track track) {
        if (track.getStart() == null || track.getFinish() == null) {
            send(player, "This track needs a start and at least one checkpoint (the last one is the finish).", NamedTextColor.RED);
            return;
        }
        String problem = getStateProblem(player);
        if (problem != null) {
            send(player, problem, NamedTextColor.RED);
            return;
        }
        runs.put(player.getUniqueId(), new Run(track));
        send(player, "Started " + track.getName() + ". Good luck!", NamedTextColor.YELLOW);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
    }

    private void finishRun(Player player, Run run) {
        int total = run.getCheckpoints().size();
        if (run.getReached() < total) {
            send(player, "You still need " + (total - run.getReached()) + " checkpoint(s) before finishing.",
                    NamedTextColor.RED);
            return;
        }

        final long time = run.getElapsedMs();
        runs.remove(player.getUniqueId());

        long minTime = plugin.getConfig().getLong("anticheat.min-finish-time-ms", 3000);
        if (time < minTime) {
            send(player, "Run cancelled: finish time is not realistic.", NamedTextColor.RED);
            return;
        }

        send(player, "Finished " + run.getTrack().getName() + " in " + formatTime(time), NamedTextColor.GOLD);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.05);

        final UUID uuid = player.getUniqueId();
        final String playerName = player.getName();
        final String trackName = run.getTrack().getName();

        // save off the main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                final boolean best = storage.saveIfBest(trackName, uuid, playerName, time);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player online = Bukkit.getPlayer(uuid);
                    if (online != null && best) {
                        send(online, "New personal best!", NamedTextColor.AQUA);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("Could not save time: " + e.getMessage());
            }
        });
    }

    public void cancelRun(Player player, String reason) {
        if (runs.remove(player.getUniqueId()) != null) {
            send(player, "Run cancelled: " + reason, NamedTextColor.RED);
        }
    }

    public void removeRun(Player player) {
        runs.remove(player.getUniqueId());
    }

    // send the player back to the last checkpoint (or the start)
    public void teleportToLastCheckpoint(Player player) {
        Run run = getRun(player);
        if (run == null) {
            return;
        }
        Location target;
        if (run.getReached() > 0) {
            target = run.getCheckpoints().get(run.getReached() - 1);
        } else {
            target = run.getTrack().getStart();
        }
        teleportSafely(player, target);
    }

    public void teleportSafely(Player player, Location target) {
        if (target == null) {
            return;
        }
        Location dest = target.clone().add(0.5, 0, 0.5);
        dest.setYaw(player.getLocation().getYaw());
        dest.setPitch(player.getLocation().getPitch());

        pluginTeleports.add(player.getUniqueId());
        player.teleport(dest);
        pluginTeleports.remove(player.getUniqueId());

        player.setFallDistance(0);
        player.setFireTicks(0);
        player.setVelocity(new Vector(0, 0, 0));
    }

    // ---------- anti-cheat ----------

    // true if the player is in water or lava (feet or head level)
    public boolean isInLiquid(Player player) {
        if (player.isInWater()) {
            return true;
        }
        Material feet = player.getLocation().getBlock().getType();
        Material head = player.getEyeLocation().getBlock().getType();
        return feet == Material.LAVA || head == Material.LAVA
                || feet == Material.WATER || head == Material.WATER
                || feet == Material.BUBBLE_COLUMN;
    }

    // returns a message if the player is in a state that is not allowed, otherwise null
    private String getStateProblem(Player player) {
        GameMode mode = player.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
            return "You must be in survival or adventure mode.";
        }
        if (player.getAllowFlight() || player.isFlying()) {
            return "Disable flight first.";
        }
        for (PotionEffect effect : player.getActivePotionEffects()) {
            String key = effect.getType().getKey().getKey();
            if (key.contains("speed") || key.contains("jump") || key.contains("levitation")
                    || key.contains("slow_falling")) {
                return "Remove speed/jump/levitation effects first.";
            }
        }
        return null;
    }

    // returns true (and cancels the run) if the move looks illegal
    public boolean checkCheat(Player player, Location from, Location to) {
        if (isPluginTeleport(player)) {
            return false;
        }

        String reason = getStateProblem(player);

        if (reason == null && player.isGliding()) {
            reason = "elytra is not allowed";
        }
        if (reason == null && player.isInsideVehicle()) {
            reason = "vehicles are not allowed";
        }
        if (reason == null && !from.getWorld().equals(to.getWorld())) {
            reason = "world change";
        }
        if (reason == null) {
            double dx = to.getX() - from.getX();
            double dz = to.getZ() - from.getZ();
            double dy = to.getY() - from.getY();
            double horizontal = Math.sqrt(dx * dx + dz * dz);

            double maxHorizontal = plugin.getConfig().getDouble("anticheat.max-horizontal-per-move", 0.95);
            double maxVertical = plugin.getConfig().getDouble("anticheat.max-vertical-per-move", 0.85);

            if (horizontal > maxHorizontal) {
                reason = "moving too fast";
            } else if (dy > maxVertical) {
                reason = "jumping too high";
            }
        }

        if (reason != null) {
            cancelRun(player, reason);
            return true;
        }
        return false;
    }

    // ---------- timers / particles ----------

    public void startTasks() {
        // action bar timer
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, Run> entry : runs.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null) {
                    continue;
                }
                Run run = entry.getValue();
                String text = formatTime(run.getElapsedMs()) + "  |  " + run.getTrack().getName()
                        + "  |  CP " + run.getReached() + "/" + run.getCheckpoints().size();
                player.sendActionBar(Component.text(text, NamedTextColor.YELLOW));
            }
        }, 1L, 1L);

        // particles on start, checkpoints and finish
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Track track : tracks.values()) {
                spawnParticles(track.getStart(), Particle.HAPPY_VILLAGER);
                for (Location loc : track.getCheckpointList()) {
                    spawnParticles(loc, Particle.END_ROD);
                }
                spawnParticles(track.getFinish(), Particle.FLAME);
            }
        }, 10L, 10L);
    }

    private void spawnParticles(Location loc, Particle particle) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            return;
        }
        loc.getWorld().spawnParticle(particle, loc.clone().add(0.5, 0.4, 0.5), 6, 0.3, 0.3, 0.3, 0.01);
    }

    // ---------- leaderboard ----------

    public void showTop(final CommandSender sender, final Track track) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                final List<Storage.TopEntry> top = storage.getTop(track.getName(), 3);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§6Top 3 - " + track.getName());
                    if (top.isEmpty()) {
                        sender.sendMessage("§7No times recorded yet.");
                    }
                    for (int i = 0; i < top.size(); i++) {
                        Storage.TopEntry entry = top.get(i);
                        sender.sendMessage("§e" + (i + 1) + ". " + entry.name + " - " + formatTime(entry.time));
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("Could not load leaderboard: " + e.getMessage());
            }
        });
    }
}
