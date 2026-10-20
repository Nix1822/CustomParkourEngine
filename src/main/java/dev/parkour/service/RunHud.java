package dev.parkour.service;

import dev.parkour.data.CourseRepository;
import dev.parkour.model.Run;
import dev.parkour.model.Track;
import dev.parkour.util.TimeFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

// action bar timer and pad particles
public final class RunHud {
    private final JavaPlugin plugin;
    private final RunService runs;
    private final CourseRepository courses;

    public RunHud(JavaPlugin plugin, RunService runs, CourseRepository courses) {
        this.plugin = plugin;
        this.runs = runs;
        this.courses = courses;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, Run> entry : runs.activeRuns().entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null) continue;
                Run run = entry.getValue();
                String text = TimeFormat.format(run.getElapsedMs()) + "  |  " + run.getTrack().getName()
                        + "  |  CP " + run.getReached() + "/" + run.getCheckpoints().size();
                player.sendActionBar(Component.text(text, NamedTextColor.YELLOW));
            }
        }, 1L, 1L);

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Track track : courses.all()) {
                particles(track.getStart(), Particle.HAPPY_VILLAGER);
                for (Location loc : track.getCheckpoints().values()) particles(loc, Particle.END_ROD);
                particles(track.getFinish(), Particle.FLAME);
            }
        }, 10L, 10L);
    }

    private void particles(Location loc, Particle particle) {
        if (loc == null || loc.getWorld() == null || loc.getWorld().getPlayers().isEmpty()) return;
        if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;
        loc.getWorld().spawnParticle(particle, loc.clone().add(0.5, 1.2, 0.5), 6, 0.3, 0.3, 0.3, 0.01);
    }
}
