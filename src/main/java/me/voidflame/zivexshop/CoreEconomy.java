package me.voidflame.zivexshop;

import org.bukkit.plugin.Plugin;
import java.lang.reflect.Method;
import java.util.UUID;

public final class CoreEconomy {
    private final ZivexShopPlugin plugin;
    public CoreEconomy(ZivexShopPlugin plugin) { this.plugin = plugin; }

    private Object service() throws Exception {
        String pluginName = plugin.getConfig().getString("economy.core-plugin", "VoidFlame-Core");
        Plugin core = plugin.getServer().getPluginManager().getPlugin(pluginName);
        if (core == null || !core.isEnabled()) throw new IllegalStateException("Core plugin unavailable");
        String getterName = plugin.getConfig().getString("economy.service-getter", "getEconomyService");
        Method getter = core.getClass().getMethod(getterName);
        return getter.invoke(core);
    }

    private Method method(Object target, String name, Class<?>... types) throws Exception {
        return target.getClass().getMethod(name, types);
    }

    public double balance(UUID uuid) {
        try {
            Object s = service();
            Object v = method(s, plugin.getConfig().getString("economy.balance-method", "getBalance"), UUID.class).invoke(s, uuid);
            return ((Number) v).doubleValue();
        } catch (Exception e) { return -1D; }
    }

    public boolean withdraw(UUID uuid, double amount) {
        try {
            Object s = service();
            Method m = method(s, plugin.getConfig().getString("economy.withdraw-method", "withdraw"), UUID.class, double.class);
            Object v = m.invoke(s, uuid, amount);
            return !(v instanceof Boolean b) || b;
        } catch (Exception e) { return false; }
    }

    public boolean deposit(UUID uuid, double amount) {
        try {
            Object s = service();
            Method m = method(s, plugin.getConfig().getString("economy.deposit-method", "deposit"), UUID.class, double.class);
            Object v = m.invoke(s, uuid, amount);
            return !(v instanceof Boolean b) || b;
        } catch (Exception e) { return false; }
    }
}