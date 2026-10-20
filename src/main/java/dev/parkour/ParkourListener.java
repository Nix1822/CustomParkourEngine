package dev.parkour;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerVelocityEvent;

public class ParkourListener implements Listener {

    private final Main plugin;
    private final ParkourManager manager;

    public ParkourListener(Main plugin, ParkourManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        if (manager.getRun(player) != null) {
            if (manager.checkCheat(player, from, to)) {
                return;
            }
            // water and lava send you back to the last checkpoint
            if (manager.isInLiquid(player)) {
                manager.teleportToLastCheckpoint(player);
                return;
            }
            double voidY = plugin.getConfig().getDouble("void-y", -5);
            if (to.getY() < voidY) {
                manager.teleportToLastCheckpoint(player);
                return;
            }
        }

        // only check when the player enters a new block
        boolean sameBlock = from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ();
        if (sameBlock) {
            return;
        }
        manager.handleBlock(player, to.getBlock());
    }

    // ender pearls and wind charges would skip parts of the course
    @EventHandler
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity().getShooter();
        if (manager.getRun(player) == null) {
            return;
        }
        if (event.getEntity() instanceof EnderPearl || event.getEntity() instanceof WindCharge) {
            event.setCancelled(true);
            player.sendMessage("§cYou cannot use that during a run.");
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (manager.getRun(player) == null || manager.isPluginTeleport(player)) {
            return;
        }
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            event.setCancelled(true);
        }
        manager.cancelRun(player, "teleport");
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (manager.getRun(player) == null) {
            return;
        }

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            manager.cancelRun(player, "explosion boost");
        } else if (cause == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    // any explosion near a runner cancels the run, even with no damage
    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        for (Entity nearby : event.getEntity().getNearbyEntities(8, 8, 8)) {
            if (nearby instanceof Player) {
                Player player = (Player) nearby;
                if (manager.getRun(player) != null) {
                    manager.cancelRun(player, "explosion boost");
                }
            }
        }
    }

    // no building (TNT, blocks to bridge gaps) during a run
    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (manager.getRun(event.getPlayer()) != null) {
            event.setCancelled(true);
        }
    }

    // fireworks can be used as a speed boost
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (manager.getRun(event.getPlayer()) == null || event.getItem() == null) {
            return;
        }
        Material item = event.getItem().getType();
        if (item == Material.FIREWORK_ROCKET || item == Material.TNT || item == Material.FLINT_AND_STEEL
                || item == Material.FIRE_CHARGE || item == Material.TNT_MINECART) {
            event.setCancelled(true);
        }
    }

    // knockback from TNT, wind charges or other players shows up as a big velocity
    @EventHandler
    public void onVelocity(PlayerVelocityEvent event) {
        Player player = event.getPlayer();
        if (manager.getRun(player) == null) {
            return;
        }
        double max = plugin.getConfig().getDouble("anticheat.max-velocity", 1.5);
        if (event.getVelocity().length() > max) {
            manager.cancelRun(player, "launched by an outside force");
        }
    }

    @EventHandler
    public void onRiptide(PlayerRiptideEvent event) {
        if (manager.getRun(event.getPlayer()) != null) {
            manager.cancelRun(event.getPlayer(), "riptide");
        }
    }

    @EventHandler
    public void onGamemode(PlayerGameModeChangeEvent event) {
        if (manager.getRun(event.getPlayer()) != null) {
            manager.cancelRun(event.getPlayer(), "gamemode changed");
        }
    }

    @EventHandler
    public void onFly(PlayerToggleFlightEvent event) {
        if (manager.getRun(event.getPlayer()) != null && event.isFlying()) {
            manager.cancelRun(event.getPlayer(), "flying");
        }
    }

    @EventHandler
    public void onGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (manager.getRun(player) != null && event.isGliding()) {
            event.setCancelled(true);
            manager.cancelRun(player, "elytra is not allowed");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.removeRun(event.getPlayer());
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (manager.getRun(player) == null) {
            return;
        }
        if (!plugin.getConfig().getBoolean("anticheat.block-commands", true)) {
            return;
        }

        String label = event.getMessage().substring(1).split(" ")[0].toLowerCase();
        if (!plugin.getConfig().getStringList("anticheat.allowed-commands").contains(label)) {
            event.setCancelled(true);
            player.sendMessage("§cCommands are blocked during a run. Use /parkour leave first.");
        }
    }
}
