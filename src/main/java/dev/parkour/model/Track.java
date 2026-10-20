package dev.parkour.model;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public final class Track {
    private final String name;
    private Location start;
    private Location finish;
    private double failYLevel = 60.0;
    private final TreeMap<Integer, Location> checkpoints = new TreeMap<>();

    public Track(String name) { this.name = name; }

    public String getName() { return name; }
    public Location getStart() { return start; }
    public void setStart(Location start) { this.start = start; }
    public Location getFinish() { return finish; }
    public void setFinish(Location finish) { this.finish = finish; }
    public double getFailYLevel() { return failYLevel; }
    public void setFailYLevel(double failYLevel) { this.failYLevel = failYLevel; }
    public TreeMap<Integer, Location> getCheckpoints() { return checkpoints; }
    public List<Location> getCheckpointList() { return new ArrayList<>(checkpoints.values()); }
    public boolean isComplete() { return start != null && finish != null; }
}
