package me.voidflame.zivexshop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ZivexShopPlugin extends JavaPlugin {
    private ShopManager shop;
    private CoreEconomy economy;

    @Override public void onEnable() {
        saveDefaultConfig();
        economy = new CoreEconomy(this);
        shop = new ShopManager(this, economy);
        shop.reload();
        Bukkit.getPluginManager().registerEvents(new ShopListener(this, shop), this);
        ShopCommand command = new ShopCommand(this, shop);
        PluginCommand shopCommand = getCommand("shop");
        PluginCommand adminCommand = getCommand("shopadmin");
        if (shopCommand != null) { shopCommand.setExecutor(command); shopCommand.setTabCompleter(command); }
        if (adminCommand != null) { adminCommand.setExecutor(command); adminCommand.setTabCompleter(command); }
        getLogger().info("ZivexShop enabled. Main GUI: 27 slots. Shard economy intentionally disabled.");
    }

    public void reloadShop() { reloadConfig(); shop.reload(); }
    public ShopManager shop() { return shop; }
    public String msg(String key) { return color(getConfig().getString("messages." + key, key)); }
    public static String color(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
}