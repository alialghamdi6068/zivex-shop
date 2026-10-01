package me.voidflame.zivexshop;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;

public final class EconomyStore {
    private final ZivexShopPlugin plugin;
    private Economy economy;

    public EconomyStore(ZivexShopPlugin plugin) {
        this.plugin = plugin;
        refresh();
    }

    public synchronized void refresh() {
        RegisteredServiceProvider<Economy> registration =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        economy = registration == null ? null : registration.getProvider();
        if (economy == null) {
            plugin.getLogger().warning("Vault Economy provider is unavailable. Money purchases are disabled until a provider is available.");
        }
    }

    private Economy provider() {
        if (economy == null) refresh();
        return economy;
    }

    public synchronized double balance(UUID uuid) {
        Economy provider = provider();
        if (provider == null) return -1D;
        try {
            return Math.max(0D, provider.getBalance(Bukkit.getOfflinePlayer(uuid)));
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Vault balance read failed: " + e.getMessage());
            return -1D;
        }
    }

    public synchronized boolean withdraw(UUID uuid, double amount) {
        if (!valid(amount)) return false;
        Economy provider = provider();
        if (provider == null) return false;
        try {
            var response = provider.withdrawPlayer(Bukkit.getOfflinePlayer(uuid), amount);
            return response.transactionSuccess();
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Vault withdraw failed: " + e.getMessage());
            return false;
        }
    }

    public synchronized boolean deposit(UUID uuid, double amount) {
        if (!valid(amount)) return false;
        Economy provider = provider();
        if (provider == null) return false;
        try {
            var response = provider.depositPlayer(Bukkit.getOfflinePlayer(uuid), amount);
            return response.transactionSuccess();
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Vault deposit failed: " + e.getMessage());
            return false;
        }
    }

    public synchronized boolean set(UUID uuid, double amount) {
        if (!valid(amount)) return false;
        Economy provider = provider();
        if (provider == null) return false;
        try {
            double current = provider.getBalance(Bukkit.getOfflinePlayer(uuid));
            if (!Double.isFinite(current) || current < 0) return false;
            double delta = amount - current;
            if (Math.abs(delta) < 0.0000001D) return true;
            var response = delta > 0
                    ? provider.depositPlayer(Bukkit.getOfflinePlayer(uuid), delta)
                    : provider.withdrawPlayer(Bukkit.getOfflinePlayer(uuid), -delta);
            return response.transactionSuccess();
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Vault set failed: " + e.getMessage());
            return false;
        }
    }

    public synchronized void load() {
        refresh();
    }

    public synchronized void close() {
        economy = null;
    }

    private boolean valid(double amount) {
        return Double.isFinite(amount) && amount >= 0D;
    }
}