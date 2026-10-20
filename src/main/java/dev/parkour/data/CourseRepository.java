package dev.parkour.data;

import dev.parkour.model.Track;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

// courses live in config.yml
public final class CourseRepository {
    private final JavaPlugin plugin;
    private final Map<String, Track> tracks = new LinkedHashMap<>();

    public CourseRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        tracks.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("tracks");
        if (section == null) return;
        double defaultFail = plugin.getConfig().getDouble("void-y", 60.0);
        for (String name : section.getKeys(false)) {
            Track track = new Track(name);
            track.setStart(section.getLocation(name + ".start"));
            track.setFinish(section.getLocation(name + ".finish"));
            track.setFailYLevel(section.getDouble(name + ".fail_y_level", defaultFail));
            ConfigurationSection cps = section.getConfigurationSection(name + ".checkpoints");
            if (cps != null) {
                for (String key : cps.getKeys(false)) {
                    Location loc = cps.getLocation(key);
                    try {
                        if (loc != null) track.getCheckpoints().put(Integer.parseInt(key), loc);
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Bad checkpoint number '" + key + "' in track " + name);
                    }
                }
            }
            if (tracks.containsKey(name.toLowerCase())) {
                plugin.getLogger().warning("Duplicate track name (case-insensitive), skipping: " + name);
                continue;
            }
            tracks.put(name.toLowerCase(), track);
        }
    }

    public void save(Track track) {
        String path = "tracks." + track.getName();
        plugin.getConfig().set(path + ".start", track.getStart());
        plugin.getConfig().set(path + ".finish", track.getFinish());
        plugin.getConfig().set(path + ".fail_y_level", track.getFailYLevel());
        plugin.getConfig().set(path + ".checkpoints", null);
        for (Map.Entry<Integer, Location> e : track.getCheckpoints().entrySet()) {
            plugin.getConfig().set(path + ".checkpoints." + e.getKey(), e.getValue());
        }
        plugin.saveConfig();
    }

    public Track get(String name) { return name == null ? null : tracks.get(name.toLowerCase()); }
    public Collection<Track> all() { return tracks.values(); }
    public static boolean isValidName(String name) { return name != null && name.matches("[A-Za-z0-9_-]{1,32}"); }

    public Track create(String name) {
        Track track = new Track(name);
        tracks.put(name.toLowerCase(), track);
        save(track);
        return track;
    }

    // callers clean up times and live runs
    public Track delete(String name) {
        Track track = tracks.remove(name.toLowerCase());
        if (track == null) return null;
        plugin.getConfig().set("tracks." + track.getName(), null);
        plugin.saveConfig();
        return track;
    }
}
