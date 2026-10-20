package dev.parkour;

import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    private Storage storage;
    private ParkourManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            storage = new Storage(getDataFolder());
        } catch (Exception e) {
            getLogger().severe("Could not open the database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        manager = new ParkourManager(this, storage);
        getServer().getPluginManager().registerEvents(new ParkourListener(this, manager), this);

        ParkourCommand command = new ParkourCommand(manager);
        getCommand("parkour").setExecutor(command);
        getCommand("parkour").setTabCompleter(command);

        manager.startTasks();
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            storage.close();
        }
    }
}
