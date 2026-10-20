package dev.parkour;

import dev.parkour.command.ParkourCommand;
import dev.parkour.data.CourseRepository;
import dev.parkour.data.Storage;
import dev.parkour.listener.ParkourListener;
import dev.parkour.service.AntiCheat;
import dev.parkour.service.LeaderboardService;
import dev.parkour.service.RunHud;
import dev.parkour.service.RunService;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {
    private Storage storage;
    private RunService runs;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            storage = new Storage(getDataFolder(), getLogger());
        } catch (Exception e) {
            getLogger().severe("Could not open the database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (getCommand("parkour") == null) {
            getLogger().severe("Missing /parkour command in plugin.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        CourseRepository courses = new CourseRepository(this);
        AntiCheat antiCheat = new AntiCheat(this);
        runs = new RunService(this, courses, storage, antiCheat);
        LeaderboardService leaderboard = new LeaderboardService(this, storage);

        getServer().getPluginManager().registerEvents(new ParkourListener(this, runs, antiCheat), this);
        ParkourCommand command = new ParkourCommand(courses, runs, storage, leaderboard);
        getCommand("parkour").setExecutor(command);
        getCommand("parkour").setTabCompleter(command);

        new RunHud(this, runs, courses).start();
    }

    @Override
    public void onDisable() {
        if (runs != null) runs.clearAll();
        // Drains queued async writes before closing the pool, so finished runs are not lost on /stop.
        if (storage != null) storage.close();
    }
}
