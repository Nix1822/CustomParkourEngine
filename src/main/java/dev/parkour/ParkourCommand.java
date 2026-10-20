package dev.parkour;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ParkourCommand implements CommandExecutor, TabCompleter {

    private final ParkourManager manager;

    public ParkourCommand(ParkourManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e/parkour leaderboard <name>");
            sender.sendMessage("§7/parkour create <name>, setstart <name>, addcheckpoint <name> <number>");
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("leaderboard")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /parkour leaderboard <name>");
                return true;
            }
            Track track = manager.getTrack(args[1]);
            if (track == null) {
                sender.sendMessage("§cUnknown track.");
            } else {
                manager.showTop(sender, track);
            }
            return true;
        }

        if (sub.equals("create") || sub.equals("setstart") || sub.equals("addcheckpoint")) {
            handleAdmin(sender, sub, args);
            return true;
        }

        sender.sendMessage("§cUnknown subcommand.");
        return true;
    }

    private void handleAdmin(CommandSender sender, String sub, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cSpecify a track name, for example: /parkour " + sub + " mytrack");
            return;
        }
        String trackName = args[1];

        // create works from the console too
        if (sub.equals("create")) {
            if (manager.getTrack(trackName) != null) {
                sender.sendMessage("§cThat track already exists.");
            } else {
                manager.createTrack(trackName);
                sender.sendMessage("§aTrack created: " + trackName);
            }
            return;
        }

        // the other two use the block the player is standing in
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return;
        }
        Player player = (Player) sender;

        Track track = manager.getTrack(trackName);
        if (track == null) {
            player.sendMessage("§cUnknown track. Create it with /parkour create " + trackName);
            return;
        }

        // the block the player is standing in
        Location here = player.getLocation().getBlock().getLocation();

        if (sub.equals("setstart")) {
            track.setStart(here);
        } else if (sub.equals("addcheckpoint")) {
            if (args.length < 3) {
                player.sendMessage("§cUsage: /parkour addcheckpoint <name> <number>");
                return;
            }
            int number;
            try {
                number = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                number = 0;
            }
            if (number < 1) {
                player.sendMessage("§cThe checkpoint number must be 1 or higher.");
                return;
            }
            track.getCheckpoints().put(number, here);
        }

        manager.saveTrack(track);
        player.sendMessage("§aSaved (" + sub + ") for " + track.getName());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> options = new ArrayList<>();

        if (args.length == 1) {
            options.add("leaderboard");
            options.add("create");
            options.add("setstart");
            options.add("addcheckpoint");
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("create")) {
            for (Track track : manager.getTracks()) {
                options.add(track.getName());
            }
        }

        String typed = args[args.length - 1].toLowerCase();
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(typed)) {
                result.add(option);
            }
        }
        return result;
    }
}
