package dev.parkour.model;

import org.bukkit.Location;

import java.util.List;

// one active run, main thread only
public final class Run {
    private final Track track;
    private final List<Location> checkpoints;
    private final long startNanos = System.nanoTime();
    private int reached = 0; // highest checkpoint index reached + 1

    public Run(Track track) {
        this.track = track;
        this.checkpoints = track.getCheckpointList(); // snapshot, edits mid-run do not affect it
    }

    public Track getTrack() { return track; }
    public List<Location> getCheckpoints() { return checkpoints; }
    public int getReached() { return reached; }

    // returns true if progress changed
    public boolean reach(int index) {
        if (index < reached) return false;
        reached = index + 1;
        return true;
    }

    public Location getLastCheckpoint() {
        return reached > 0 ? checkpoints.get(reached - 1) : track.getStart();
    }

    public long getElapsedMs() { return (System.nanoTime() - startNanos) / 1_000_000L; }
}
