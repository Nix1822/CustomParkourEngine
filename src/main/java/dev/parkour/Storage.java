package dev.parkour;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Methods here block, so call them from an async task.
public class Storage {

    public static class TopEntry {
        public final String name;
        public final long time;

        public TopEntry(String name, long time) {
            this.name = name;
            this.time = time;
        }
    }

    private final Connection connection;

    public Storage(File folder) throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite driver not found", e);
        }
        folder.mkdirs();
        File file = new File(folder, "parkour.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());

        Statement st = connection.createStatement();
        st.execute("CREATE TABLE IF NOT EXISTS times ("
                + "track TEXT NOT NULL, uuid TEXT NOT NULL, name TEXT, best_ms INTEGER NOT NULL, "
                + "PRIMARY KEY (track, uuid))");
        st.close();
    }

    // returns true if this is a new personal best
    public synchronized boolean saveIfBest(String track, UUID uuid, String name, long ms) throws SQLException {
        PreparedStatement select = connection.prepareStatement(
                "SELECT best_ms FROM times WHERE track = ? AND uuid = ?");
        select.setString(1, track);
        select.setString(2, uuid.toString());
        ResultSet rs = select.executeQuery();
        boolean hasOld = rs.next();
        long old = hasOld ? rs.getLong(1) : 0;
        rs.close();
        select.close();

        if (hasOld && old <= ms) {
            return false;
        }

        PreparedStatement save = connection.prepareStatement(
                "INSERT OR REPLACE INTO times (track, uuid, name, best_ms) VALUES (?, ?, ?, ?)");
        save.setString(1, track);
        save.setString(2, uuid.toString());
        save.setString(3, name);
        save.setLong(4, ms);
        save.executeUpdate();
        save.close();
        return true;
    }

    public synchronized List<TopEntry> getTop(String track, int amount) throws SQLException {
        List<TopEntry> list = new ArrayList<>();
        PreparedStatement select = connection.prepareStatement(
                "SELECT name, best_ms FROM times WHERE track = ? ORDER BY best_ms ASC LIMIT ?");
        select.setString(1, track);
        select.setInt(2, amount);
        ResultSet rs = select.executeQuery();
        while (rs.next()) {
            list.add(new TopEntry(rs.getString(1), rs.getLong(2)));
        }
        rs.close();
        select.close();
        return list;
    }

    public synchronized void deleteTrack(String track) throws SQLException {
        PreparedStatement delete = connection.prepareStatement("DELETE FROM times WHERE track = ?");
        delete.setString(1, track);
        delete.executeUpdate();
        delete.close();
    }

    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            // nothing we can do while shutting down
        }
    }
}
