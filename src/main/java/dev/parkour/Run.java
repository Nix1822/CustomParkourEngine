package dev.parkour;

import org.bukkit.Location;

import java.util.List;

public class Run {

    private final Track track;
    private final List<Location> checkpoints;
    private final long startTime = System.nanoTime();
    private int reached = 0;

    public Run(Track track) {
        this.track = track;
        this.checkpoints = track.getCheckpointList();
    }

    public Track getTrack() {
        return track;
    }

    public List<Location> getCheckpoints() {
        return checkpoints;
    }

    public int getReached() {
        return reached;
    }

    public void addReached() {
        reached++;
    }

    public long getElapsedMs() {
        return (System.nanoTime() - startTime) / 1000000;
    }
}
