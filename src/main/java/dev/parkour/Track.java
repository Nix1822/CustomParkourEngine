package dev.parkour;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class Track {

    private final String name;
    private Location start;
    // key = checkpoint number, so they are always read in order.
    // The checkpoint with the highest number is the finish.
    private final TreeMap<Integer, Location> checkpoints = new TreeMap<>();

    public Track(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Location getStart() {
        return start;
    }

    public void setStart(Location start) {
        this.start = start;
    }

    public TreeMap<Integer, Location> getCheckpoints() {
        return checkpoints;
    }

    // the last checkpoint, or null if there are none
    public Location getFinish() {
        if (checkpoints.isEmpty()) {
            return null;
        }
        return checkpoints.lastEntry().getValue();
    }

    // every checkpoint except the finish, in order
    public List<Location> getCheckpointList() {
        List<Location> list = new ArrayList<>(checkpoints.values());
        if (!list.isEmpty()) {
            list.remove(list.size() - 1);
        }
        return list;
    }
}
