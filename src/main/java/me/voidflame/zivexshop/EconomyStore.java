package me.voidflame.zivexshop;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.UUID;

public final class EconomyStore {
    private static final String VAULT_ECONOMY = "net.milkbowl.vault.economy.Economy";

    private final ZivexShopPlugin plugin;
    private volatile Object economy;
    private volatile Method getBalance;
    private volatile Method withdrawPlayer;
    private volatile Method depositPlayer;
    private volatile Method transactionSuccess;
    private volatile boolean vaultMissing;

    public EconomyStore(ZivexShopPlugin plugin) {
        this.plugin = plugin;
        refresh();
    }

    public synchronized void refresh() {
        economy = null;
        getBalance = null;
        withdrawPlayer = null;
        depositPlayer = null;
        transactionSuccess = null;
        vaultMissing = false;

        final Class<?> type;
        try {
            type = Class.forName(VAULT_ECONOMY);
        } catch (ClassNotFoundException ex) {
            vaultMissing = true;
            plugin.getLogger().info("Vault is not installed; MONEY shop features are unavailable. Shards remain available through ZivexShards.");
            return;
        }

        try {
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(type);
            if (registration == null || registration.getProvider() == null) {
                plugin.getLogger().warning("Vault is installed but no Economy provider is registered. MONEY shop features are unavailable.");
                return;
            }

            Object provider = registration.getProvider();
            getBalance = type.getMethod("getBalance", OfflinePlayer.class);
            withdrawPlayer = type.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            depositPlayer = type.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            Class<?> responseType = withdrawPlayer.getReturnType();
            transactionSuccess = responseType.getMethod("transactionSuccess");
            economy = provider;

            String name = String.valueOf(type.getMethod("getName").invoke(provider));
            plugin.getLogger().info("Vault Economy provider: " + name);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            economy = null;
            getBalance = null;
            withdrawPlayer = null;
            depositPlayer = null;
            transactionSuccess = null;
            plugin.getLogger().warning("Vault Economy integration is unavailable: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private Object provider() {
        Object current = economy;
        if (current == null && !vaultMissing) {
            refresh();
            current = economy;
        }
        return current;
    }

    public double balance(UUID id) {
        Object provider = provider();
        if (provider == null) return -1D;
        try {
            Object value = getBalance.invoke(provider, player(id));
            double balance = value instanceof Number number ? number.doubleValue() : -1D;
            return Double.isFinite(balance) && balance >= 0D ? balance : -1D;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("Vault balance failed: " + ex.getMessage());
            return -1D;
        }
    }

    public boolean withdraw(UUID id, double amount) {
        if (!valid(amount)) return false;
        return transaction(withdrawPlayer, id, amount, "withdraw");
    }

    public boolean deposit(UUID id, double amount) {
        if (!valid(amount)) return false;
        return transaction(depositPlayer, id, amount, "deposit");
    }

    public boolean set(UUID id, double amount) {
        if (!valid(amount)) return false;

        Object provider = provider();
        if (provider == null) return false;

        try {
            OfflinePlayer player = player(id);
            Object rawBalance = getBalance.invoke(provider, player);
            double current = rawBalance instanceof Number number ? number.doubleValue() : -1D;
            if (!Double.isFinite(current) || current < 0D) return false;

            double delta = amount - current;
            if (Math.abs(delta) < 1e-7D) return true;
            return delta > 0D ? deposit(id, delta) : withdraw(id, -delta);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("Vault set failed: " + ex.getMessage());
            return false;
        }
    }

    private boolean transaction(Method operation, UUID id, double amount, String action) {
        Object provider = provider();
        if (provider == null || operation == null || transactionSuccess == null) return false;

        try {
            Object response = operation.invoke(provider, player(id), amount);
            return Boolean.TRUE.equals(transactionSuccess.invoke(response));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("Vault " + action + " failed: " + ex.getMessage());
            return false;
        }
    }

    public void load() {
        refresh();
    }

    public void close() {
        economy = null;
        getBalance = null;
        withdrawPlayer = null;
        depositPlayer = null;
        transactionSuccess = null;
    }

    private OfflinePlayer player(UUID id) {
        return Bukkit.getOfflinePlayer(id);
    }

    private boolean valid(double amount) {
        return Double.isFinite(amount) && amount >= 0D;
    }
}
