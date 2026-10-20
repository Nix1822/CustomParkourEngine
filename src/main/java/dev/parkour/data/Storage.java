package dev.parkour.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

// SQLite via HikariCP, everything runs on one db thread; close() waits for pending writes
public final class Storage implements AutoCloseable {
    public record TopEntry(String name, long time) {}

    private final HikariDataSource dataSource;
    private final ExecutorService executor;
    private final Logger logger;

    public Storage(File folder, Logger logger) throws SQLException {
        this.logger = logger;
        if (!folder.exists() && !folder.mkdirs()) throw new SQLException("Could not create plugin data folder");
        HikariConfig config = new HikariConfig();
        config.setPoolName("CustomParkourEngine-SQLite");
        config.setJdbcUrl("jdbc:sqlite:" + new File(folder, "parkour.db").getAbsolutePath());
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        config.addDataSourceProperty("busy_timeout", "5000");
        this.dataSource = new HikariDataSource(config);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS times ("
                    + "track TEXT NOT NULL, uuid TEXT NOT NULL, name TEXT NOT NULL, "
                    + "best_ms INTEGER NOT NULL CHECK(best_ms >= 0), PRIMARY KEY (track, uuid))");
            s.execute("CREATE INDEX IF NOT EXISTS idx_times_track_best ON times(track, best_ms ASC)");
        } catch (SQLException ex) {
            dataSource.close();
            throw ex;
        }
        this.executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "CustomParkourEngine-DB"));
    }

    public CompletableFuture<Boolean> saveIfBestAsync(String track, UUID uuid, String name, long ms) {
        return submit(() -> {
            if (ms < 0) throw new IllegalArgumentException("time cannot be negative");
            String sql = "INSERT INTO times(track, uuid, name, best_ms) VALUES(?, ?, ?, ?) "
                    + "ON CONFLICT(track, uuid) DO UPDATE SET name=excluded.name, best_ms=excluded.best_ms "
                    + "WHERE excluded.best_ms < times.best_ms";
            try (Connection c = dataSource.getConnection(); PreparedStatement st = c.prepareStatement(sql)) {
                st.setString(1, track.toLowerCase(Locale.ROOT));
                st.setString(2, uuid.toString());
                st.setString(3, name);
                st.setLong(4, ms);
                return st.executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<List<TopEntry>> getTopAsync(String track, int amount) {
        return submit(() -> {
            List<TopEntry> list = new ArrayList<>();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement st = c.prepareStatement(
                         "SELECT name, best_ms FROM times WHERE track = ? ORDER BY best_ms ASC, name ASC LIMIT ?")) {
                st.setString(1, track.toLowerCase(Locale.ROOT));
                st.setInt(2, Math.max(1, Math.min(amount, 100)));
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) list.add(new TopEntry(rs.getString("name"), rs.getLong("best_ms")));
                }
            }
            return list;
        });
    }

    public CompletableFuture<Void> deleteTrackAsync(String track) {
        return submit(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement st = c.prepareStatement("DELETE FROM times WHERE track = ?")) {
                st.setString(1, track.toLowerCase(Locale.ROOT));
                st.executeUpdate();
            }
            return null;
        });
    }

    @FunctionalInterface
    private interface SqlTask<T> { T run() throws SQLException; }

    private <T> CompletableFuture<T> submit(SqlTask<T> task) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try { return task.run(); } catch (SQLException e) { throw new CompletionException(e); }
            }, executor);
        } catch (RejectedExecutionException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) logger.warning("Database writes did not finish in time.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!dataSource.isClosed()) dataSource.close();
    }
}
