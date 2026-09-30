package me.voidflame.zivexshop;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class EconomyStore {
    private final ZivexShopPlugin plugin;
    private final File file;
    private YamlConfiguration data;

    public EconomyStore(ZivexShopPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "economy.yml");
        load();
    }

    public synchronized void load() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        data = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized double balance(UUID uuid) {
        return Math.max(0D, data.getDouble("balances." + uuid, 0D));
    }

    public synchronized boolean withdraw(UUID uuid, double amount) {
        if (!Double.isFinite(amount) || amount < 0) return false;
        double current = balance(uuid);
        if (current < amount) return false;
        data.set("balances." + uuid, current - amount);
        return save();
    }

    public synchronized boolean deposit(UUID uuid, double amount) {
        if (!Double.isFinite(amount) || amount < 0) return false;
        double next = balance(uuid) + amount;
        if (!Double.isFinite(next)) return false;
        data.set("balances." + uuid, next);
        return save();
    }

    public synchronized boolean set(UUID uuid, double amount) {
        if (!Double.isFinite(amount) || amount < 0) return false;
        data.set("balances." + uuid, amount);
        return save();
    }

    private boolean save() {
        try { data.save(file); return true; }
        catch (IOException e) {
            plugin.getLogger().severe("Failed to save economy.yml: " + e.getMessage());
            return false;
        }
    }
}
