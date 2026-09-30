package me.voidflame.zivexshop;

import java.io.File;
import java.sql.*;
import java.util.UUID;

public final class EconomyStore {
    private final ZivexShopPlugin plugin;
    private final File file;
    private Connection connection;

    public EconomyStore(ZivexShopPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), plugin.getConfig().getString("settings.database-file", "../Zivex/database.db"));
        open();
    }

    private synchronized void open() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) throw new SQLException("Could not create database directory");
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            try (Statement s = connection.createStatement()) {
                s.execute("PRAGMA journal_mode=DELETE");
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("PRAGMA busy_timeout=5000");
                s.execute("CREATE TABLE IF NOT EXISTS economy (uuid TEXT PRIMARY KEY, balance REAL NOT NULL DEFAULT 0 CHECK(balance >= 0))");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not open ZivexShop SQLite database", e);
        }
    }

    public synchronized void load() {
        if (connection == null) open();
    }

    public synchronized double balance(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT balance FROM economy WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Math.max(0D, rs.getDouble(1)) : 0D; }
        } catch (SQLException e) { plugin.getLogger().severe("Economy read failed: " + e.getMessage()); return -1D; }
    }

    public synchronized boolean withdraw(UUID uuid, double amount) {
        if (!valid(amount)) return false;
        try (PreparedStatement ps = connection.prepareStatement("UPDATE economy SET balance=balance-? WHERE uuid=? AND balance>=?")) {
            ps.setDouble(1, amount); ps.setString(2, uuid.toString()); ps.setDouble(3, amount);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) { plugin.getLogger().severe("Economy withdraw failed: " + e.getMessage()); return false; }
    }

    public synchronized boolean deposit(UUID uuid, double amount) {
        if (!valid(amount)) return false;
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO economy(uuid,balance) VALUES(?,?) ON CONFLICT(uuid) DO UPDATE SET balance=balance+excluded.balance")) {
            ps.setString(1, uuid.toString()); ps.setDouble(2, amount); return ps.executeUpdate() == 1;
        } catch (SQLException e) { plugin.getLogger().severe("Economy deposit failed: " + e.getMessage()); return false; }
    }

    public synchronized boolean set(UUID uuid, double amount) {
        if (!valid(amount)) return false;
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO economy(uuid,balance) VALUES(?,?) ON CONFLICT(uuid) DO UPDATE SET balance=excluded.balance")) {
            ps.setString(1, uuid.toString()); ps.setDouble(2, amount); return ps.executeUpdate() == 1;
        } catch (SQLException e) { plugin.getLogger().severe("Economy set failed: " + e.getMessage()); return false; }
    }

    public synchronized void close() {
        try { if (connection != null && !connection.isClosed()) connection.close(); }
        catch (SQLException e) { plugin.getLogger().warning("Failed to close economy database: " + e.getMessage()); }
    }

    private boolean valid(double amount) { return Double.isFinite(amount) && amount >= 0D; }
}