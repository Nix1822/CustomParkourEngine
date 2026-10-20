package dev.parkour.listener;

import dev.parkour.model.Run;
import dev.parkour.service.AntiCheat;
import dev.parkour.service.RunService;
import dev.parkour.util.Messages;
import org.bukkit.Location;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.plugin.java.JavaPlugin;

// Bukkit events -> RunService / AntiCheat
public final class ParkourListener implements Listener {
    private final JavaPlugin plugin;
    private final RunService runs;
    private final AntiCheat antiCheat;

    public ParkourListener(JavaPlugin plugin, RunService runs, AntiCheat antiCheat) {
        this.plugin = plugin;
        this.runs = runs;
        this.antiCheat = antiCheat;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        Run run = runs.getRun(player);
        if (run != null && !runs.isPluginTeleport(player)) {
            String reason = antiCheat.inspectMove(player, from, to);
            if (reason != null) { runs.cancelRun(player, reason); return; }
            if (runs.shouldRespawn(player, run, to)) { runs.teleportToLastCheckpoint(player); return; }
        }

        // Pads are the solid block directly under the player's feet.
        Location fromPad = from.clone().subtract(0, 1, 0);
        Location toPad = to.clone().subtract(0, 1, 0);
        boolean changed = fromPad.getBlockX() != toPad.getBlockX()
                || fromPad.getBlockY() != toPad.getBlockY()
                || fromPad.getBlockZ() != toPad.getBlockZ()
                || !from.getWorld().equals(to.getWorld());
        if (changed) runs.handlePad(player, toPad.getBlock());
    }

    @EventHandler
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player) || runs.getRun(player) == null) return;
        if (event.getEntity() instanceof EnderPearl || event.getEntity() instanceof WindCharge) {
            event.setCancelled(true);
            Messages.send(player, "§cYou cannot use that during a run.");
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (runs.getRun(player) == null || runs.isPluginTeleport(player)) return;
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) event.setCancelled(true);
        runs.cancelRun(player, "teleport");
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || runs.getRun(player) == null) return;
        switch (event.getCause()) {
            case FALL -> event.setCancelled(true);
            case ENTITY_EXPLOSION, BLOCK_EXPLOSION, ENTITY_ATTACK, PROJECTILE, MAGIC ->
                    runs.cancelRun(player, "damage or external boost detected");
            default -> { }
        }
    }

    @EventHandler
    public void onVelocity(PlayerVelocityEvent event) {
        Player player = event.getPlayer();
        if (runs.getRun(player) == null || runs.isPluginTeleport(player)) return;
        var v = event.getVelocity();
        if (v.getY() > 0.55 || v.getX() * v.getX() + v.getZ() * v.getZ() > 0.30) {
            runs.cancelRun(player, "external velocity boost detected");
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        for (Entity nearby : event.getEntity().getNearbyEntities(8, 8, 8)) {
            if (nearby instanceof Player p && runs.getRun(p) != null) runs.cancelRun(p, "explosion boost");
        }
    }

    @EventHandler
    public void onRiptide(PlayerRiptideEvent event) {
        if (runs.getRun(event.getPlayer()) != null) runs.cancelRun(event.getPlayer(), "riptide");
    }

    @EventHandler
    public void onGamemode(PlayerGameModeChangeEvent event) {
        if (runs.getRun(event.getPlayer()) != null) runs.cancelRun(event.getPlayer(), "gamemode changed");
    }

    @EventHandler
    public void onFly(PlayerToggleFlightEvent event) {
        if (event.isFlying() && runs.getRun(event.getPlayer()) != null) runs.cancelRun(event.getPlayer(), "flying");
    }

    @EventHandler
    public void onGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player) || !event.isGliding() || runs.getRun(player) == null) return;
        event.setCancelled(true);
        runs.cancelRun(player, "elytra is not allowed");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        runs.removeRun(event.getEntity());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { runs.removeRun(event.getPlayer()); }

    @EventHandler
    public void onKick(PlayerKickEvent event) { runs.removeRun(event.getPlayer()); }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (runs.getRun(player) == null || !plugin.getConfig().getBoolean("anticheat.block-commands", true)) return;
        String label = event.getMessage().substring(1).split(" ")[0].toLowerCase();
        if (!plugin.getConfig().getStringList("anticheat.allowed-commands").contains(label)) {
            event.setCancelled(true);
            Messages.send(player, "§cCommands are blocked during a run. Use /parkour leave first.");
        }
    }
}
