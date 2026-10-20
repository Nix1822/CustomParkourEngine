package dev.parkour.service;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

// movement/state checks, returns a reason string or null
public final class AntiCheat {
    private final JavaPlugin plugin;

    public AntiCheat(JavaPlugin plugin) { this.plugin = plugin; }

    public String stateProblem(Player player) {
        GameMode mode = player.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) return "You must be in survival or adventure mode.";
        if (player.getAllowFlight() || player.isFlying()) return "Disable flight first.";
        for (PotionEffect effect : player.getActivePotionEffects()) {
            String key = effect.getType().getKey().getKey();
            if (key.contains("speed") || key.contains("jump") || key.contains("levitation") || key.contains("slow_falling")) {
                return "Remove speed/jump/levitation/slow-falling effects first.";
            }
        }
        return null;
    }

    public String inspectMove(Player player, Location from, Location to) {
        String reason = stateProblem(player);
        if (reason != null) return reason;
        if (player.isGliding()) return "elytra is not allowed";
        if (player.isInsideVehicle()) return "vehicles are not allowed";
        if (!from.getWorld().equals(to.getWorld())) return "world change";

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dy = to.getY() - from.getY();
        double maxH = plugin.getConfig().getDouble("anticheat.max-horizontal-per-move", 0.95);
        double maxV = plugin.getConfig().getDouble("anticheat.max-vertical-per-move", 0.85);
        if (Math.sqrt(dx * dx + dz * dz) > maxH) return "moving too fast";
        if (dy > maxV) return "jumping too high";
        return null;
    }
}
