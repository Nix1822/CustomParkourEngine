package dev.parkour.command;

import dev.parkour.data.CourseRepository;
import dev.parkour.data.Storage;
import dev.parkour.model.Run;
import dev.parkour.model.Track;
import dev.parkour.service.LeaderboardService;
import dev.parkour.service.RunService;
import dev.parkour.util.Messages;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

// /parkour command handling
public final class ParkourCommand implements CommandExecutor, TabCompleter {
    private static final List<String> PLAYER_CMDS = List.of("play", "checkpoint", "restart", "leave", "top", "leaderboard", "list");
    private static final List<String> ADMIN_CMDS = List.of("create", "delete", "setstart", "setfinish", "setcheckpoint", "addcheckpoint", "setfail");

    private final CourseRepository courses;
    private final RunService runs;
    private final Storage storage;
    private final LeaderboardService leaderboard;

    public ParkourCommand(CourseRepository courses, RunService runs, Storage storage, LeaderboardService leaderboard) {
        this.courses = courses;
        this.runs = runs;
        this.storage = storage;
        this.leaderboard = leaderboard;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            dispatch(sender, args);
        } catch (RuntimeException ex) {
            Messages.send(sender, "§cSomething went wrong running that command.");
            org.bukkit.Bukkit.getLogger().warning("[CustomParkourEngine] Command error: " + ex);
        }
        return true;
    }

    private void dispatch(CommandSender sender, String[] args) {
        if (args.length == 0) {
            Messages.send(sender, "§e/parkour <play|leaderboard> <name> | checkpoint | restart | leave | list");
            if (sender.hasPermission("parkour.admin")) Messages.send(sender, "§7Admin: create, delete, setstart, setfinish, addcheckpoint, setfail");
            return;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("list")) {
            List<String> names = new ArrayList<>();
            for (Track t : courses.all()) names.add(t.getName());
            Messages.send(sender, "§eCourses: §f" + (names.isEmpty() ? "none" : String.join(", ", names)));
            return;
        }
        if (sub.equals("top") || sub.equals("leaderboard")) {
            if (args.length != 2) { Messages.send(sender, "§cUsage: /parkour leaderboard <name>"); return; }
            Track track = courses.get(args[1]);
            if (track == null) Messages.send(sender, "§cUnknown course: " + args[1]); else leaderboard.showTop(sender, track);
            return;
        }
        if (ADMIN_CMDS.contains(sub)) {
            if (!sender.hasPermission("parkour.admin")) { Messages.send(sender, "§cYou do not have permission."); return; }
            if (!(sender instanceof Player p)) { Messages.send(sender, "This admin command must be used in-game."); return; }
            admin(p, sub, args);
            return;
        }
        if (!(sender instanceof Player player)) { Messages.send(sender, "This command can only be used by players."); return; }
        playerCommand(player, sub, args);
    }

    private void playerCommand(Player player, String sub, String[] args) {
        switch (sub) {
            case "play" -> {
                if (args.length != 2) { Messages.send(player, "§cUsage: /parkour play <name>"); return; }
                Track track = courses.get(args[1]);
                if (track == null || !track.isComplete()) { Messages.send(player, "§cUnknown or incomplete course."); return; }
                if (runs.startRun(player, track)) runs.teleportSafely(player, track.getStart());
            }
            case "checkpoint", "cp" -> {
                if (runs.getRun(player) == null) Messages.send(player, "§cYou are not in a run.");
                else runs.teleportToLastCheckpoint(player);
            }
            case "restart" -> {
                Run run = runs.getRun(player);
                if (run == null) { Messages.send(player, "§cYou are not in a run."); return; }
                Track track = run.getTrack();
                runs.cancelRun(player, "restarted");
                runs.teleportSafely(player, track.getStart());
                runs.startRun(player, track);
            }
            case "leave" -> runs.cancelRun(player, "you left the course");
            default -> Messages.send(player, "§cUnknown subcommand. Use /parkour for help.");
        }
    }

    private void admin(Player player, String sub, String[] args) {
        if (sub.equals("create")) {
            if (args.length != 2 || !CourseRepository.isValidName(args[1])) {
                Messages.send(player, "§cUsage: /parkour create <name> (letters, numbers, _ and -; max 32 chars)");
            } else if (courses.get(args[1]) != null) {
                Messages.send(player, "§cThat course already exists.");
            } else {
                courses.create(args[1]);
                Messages.send(player, "§aCourse created: " + args[1]);
            }
            return;
        }
        if (args.length < 2) { Messages.send(player, "§cSpecify a course name."); return; }
        Track track = courses.get(args[1]);
        if (track == null) { Messages.send(player, "§cUnknown course. Create it with /parkour create " + args[1]); return; }

        switch (sub) {
            case "delete" -> {
                if (args.length != 2) { Messages.send(player, "§cUsage: /parkour delete <name>"); return; }
                Track removed = courses.delete(args[1]);
                runs.dropRunsFor(removed);
                storage.deleteTrackAsync(removed.getName()).exceptionally(e -> {
                    org.bukkit.Bukkit.getLogger().warning("[CustomParkourEngine] Could not delete times: " + e.getMessage());
                    return null;
                });
                Messages.send(player, "§aCourse deleted.");
            }
            case "setfail" -> {
                if (args.length != 3) { Messages.send(player, "§cUsage: /parkour setfail <name> <y-level>"); return; }
                try {
                    double y = Double.parseDouble(args[2]);
                    if (!Double.isFinite(y) || y < player.getWorld().getMinHeight() || y > player.getWorld().getMaxHeight()) throw new NumberFormatException();
                    track.setFailYLevel(y);
                    courses.save(track);
                    Messages.send(player, "§aSaved fail Y-level: " + y);
                } catch (NumberFormatException ex) {
                    Messages.send(player, "§cY-level must be a valid number in this world's height range.");
                }
            }
            case "addcheckpoint", "setcheckpoint" -> {
                if (args.length != 3) { Messages.send(player, "§cUsage: /parkour addcheckpoint <name> <number>"); return; }
                int number;
                try { number = Integer.parseInt(args[2]); }
                catch (NumberFormatException ex) { Messages.send(player, "§cCheckpoint number must be a positive integer."); return; }
                if (number < 1 || number > 100) { Messages.send(player, "§cCheckpoint number must be between 1 and 100."); return; }
                if (number > 1 && !track.getCheckpoints().containsKey(number - 1)) { Messages.send(player, "§cAdd checkpoint " + (number - 1) + " first."); return; }
                track.getCheckpoints().put(number, padUnderPlayer(player));
                courses.save(track);
                Messages.send(player, "§aSaved checkpoint " + number + " for " + track.getName());
            }
            case "setstart", "setfinish" -> {
                if (args.length != 2) { Messages.send(player, "§cUsage: /parkour " + sub + " <name>"); return; }
                if (sub.equals("setstart")) track.setStart(padUnderPlayer(player)); else track.setFinish(padUnderPlayer(player));
                courses.save(track);
                Messages.send(player, "§aSaved " + sub + " for " + track.getName());
            }
            default -> Messages.send(player, "§cUnknown admin command.");
        }
    }

    private static Location padUnderPlayer(Player player) {
        return player.getLocation().clone().subtract(0, 1, 0).getBlock().getLocation();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(PLAYER_CMDS);
            if (sender.hasPermission("parkour.admin")) options.addAll(ADMIN_CMDS);
        } else if (args.length == 2) {
            for (Track t : courses.all()) options.add(t.getName());
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("addcheckpoint") || args[0].equalsIgnoreCase("setcheckpoint"))) {
            options.addAll(List.of("1", "2", "3", "4", "5"));
        }
        String typed = args[args.length - 1].toLowerCase();
        List<String> result = new ArrayList<>();
        for (String o : options) if (o.toLowerCase().startsWith(typed)) result.add(o);
        return result;
    }
}
