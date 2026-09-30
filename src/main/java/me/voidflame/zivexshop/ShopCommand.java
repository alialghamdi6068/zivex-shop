package me.voidflame.zivexshop;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

public final class ShopCommand implements CommandExecutor, TabCompleter {
    private final ZivexShopPlugin plugin;
    private final ShopManager shop;
    private final ShopAdminGui adminGui;

    public ShopCommand(ZivexShopPlugin plugin, ShopManager shop, ShopAdminGui adminGui) { this.plugin = plugin; this.shop = shop; this.adminGui = adminGui; }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("zivexshop.admin")) return true;
        sender.sendMessage(plugin.msg("no-permission"));
        return false;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean adminCommand = command.getName().equalsIgnoreCase("shopadmin");

        if (!adminCommand) {
            if (!(sender instanceof Player p)) { sender.sendMessage(plugin.msg("player-only")); return true; }
            if (!p.hasPermission("zivexshop.use")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length > 0) { p.sendMessage(plugin.msg("player-usage")); return true; }
            shop.openMain(p);
            return true;
        }

        if (!admin(sender)) return true;
        if (args.length == 0) { sender.sendMessage(plugin.msg("admin-usage")); return true; }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "gui" -> {
                if (sender instanceof Player p) adminGui.open(p); else sender.sendMessage(plugin.msg("player-only"));
            }
            case "reload" -> {
                plugin.reloadShop();
                sender.sendMessage(plugin.msg("reloaded"));
            }
            case "list" -> list(sender);
            case "debug" -> debug(sender);
            case "setprice" -> setPrice(sender, args);
            case "setslot" -> setSlot(sender, args);
            case "enable", "disable" -> setEnabled(sender, args, sub.equals("enable"));
            case "give" -> give(sender, args);
            case "money" -> money(sender, args);
            default -> sender.sendMessage(plugin.msg("admin-usage"));
        }
        return true;
    }

    private void list(CommandSender sender) {
        for (ShopManager.Category cat : shop.categories()) {
            sender.sendMessage(ZivexShopPlugin.color("&d" + cat.id() + " &7(slot " + cat.slot() + ")"));
            for (ShopManager.ItemDef d : cat.items().values())
                sender.sendMessage(ZivexShopPlugin.color(" &8- &f" + d.id() + " &7slot=" + d.slot()
                        + " price=" + d.price() + " currency=" + d.currency()
                        + " delivery=" + d.delivery() + " enabled=" + d.enabled()));
        }
    }

    private void debug(CommandSender sender) {
        sender.sendMessage(ZivexShopPlugin.color("&dZivexShop &7categories=" + shop.categories().size()
                + " core=" + plugin.getServer().getPluginManager().isPluginEnabled("VoidFlame-Core")
                + " shards=" + plugin.getServer().getPluginManager().isPluginEnabled("ZivexShards")));
    }

    private void setPrice(CommandSender sender, String[] a) {
        if (a.length < 4) { sender.sendMessage(plugin.msg("admin-usage")); return; }
        try {
            double price = Double.parseDouble(a[3]);
            if (!Double.isFinite(price) || price < 0) throw new NumberFormatException();
            sender.sendMessage(shop.setPrice(a[1], a[2], price) ? plugin.msg("price-updated") : plugin.msg("invalid-item"));
        } catch (NumberFormatException e) { sender.sendMessage(plugin.msg("invalid-price")); }
    }

    private void setSlot(CommandSender sender, String[] a) {
        if (a.length < 4) { sender.sendMessage(plugin.msg("admin-usage")); return; }
        try {
            int slot = Integer.parseInt(a[3]);
            sender.sendMessage(shop.setSlot(a[1], a[2], slot) ? plugin.msg("slot-updated") : plugin.msg("invalid-slot"));
        } catch (NumberFormatException e) { sender.sendMessage(plugin.msg("invalid-slot")); }
    }

    private void setEnabled(CommandSender sender, String[] a, boolean enabled) {
        if (a.length < 3) { sender.sendMessage(plugin.msg("admin-usage")); return; }
        sender.sendMessage(shop.setEnabled(a[1], a[2], enabled) ? plugin.msg("item-updated") : plugin.msg("invalid-item"));
    }

    private void money(CommandSender sender, String[] a) {
        if (a.length < 3) { sender.sendMessage(ZivexShopPlugin.color("&cUsage: /shopadmin money <balance|give|take|set> <player> [amount]")); return; }
        Player target = Bukkit.getPlayerExact(a[2]);
        if (target == null) { sender.sendMessage(plugin.msg("player-not-found")); return; }
        if (a[1].equalsIgnoreCase("balance")) { sender.sendMessage(ZivexShopPlugin.color("&d" + target.getName() + " &7balance: &f$" + shop.money(target))); return; }
        if (a.length < 4) { sender.sendMessage(ZivexShopPlugin.color("&cAmount required.")); return; }
        try {
            double amount = Double.parseDouble(a[3]);
            if (!Double.isFinite(amount) || amount < 0) throw new NumberFormatException();
            boolean ok = switch (a[1].toLowerCase(Locale.ROOT)) {
                case "give" -> shop.moneyDeposit(target, amount);
                case "take" -> shop.moneyWithdraw(target, amount);
                case "set" -> shop.moneySet(target, amount);
                default -> false;
            };
            sender.sendMessage(ZivexShopPlugin.color(ok ? "&aMoney updated successfully." : "&cMoney operation failed."));
        } catch (NumberFormatException e) { sender.sendMessage(plugin.msg("invalid-number")); }
    }

    private void give(CommandSender sender, String[] a) {
        if (a.length < 4) { sender.sendMessage(plugin.msg("admin-usage")); return; }
        Player target = Bukkit.getPlayerExact(a[1]);
        if (target == null) { sender.sendMessage(plugin.msg("player-not-found")); return; }
        ShopManager.ItemDef d = shop.item(a[2], a[3]);
        if (d == null) { sender.sendMessage(plugin.msg("invalid-item")); return; }

        int amount = 1;
        if (a.length >= 5) {
            try { amount = Math.max(1, Integer.parseInt(a[4])); }
            catch (NumberFormatException e) { sender.sendMessage(plugin.msg("invalid-number")); return; }
        }

        if (!shop.give(target, d, amount)) {
            sender.sendMessage(plugin.msg("delivery-failed"));
            return;
        }
        sender.sendMessage(plugin.msg("delivered").replace("{amount}", String.valueOf(amount))
                .replace("{item}", ShopManager.ChatText.strip(d.name())).replace("{player}", target.getName()));
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] a) {
        if (!command.getName().equalsIgnoreCase("shopadmin") || !sender.hasPermission("zivexshop.admin")) return List.of();
        if (a.length == 1) return List.of("gui","reload","list","debug","setprice","setslot","enable","disable","give","money");
        if (a.length == 2 && a[0].equalsIgnoreCase("money")) return List.of("balance","give","take","set");
        if (a.length == 3 && a[0].equalsIgnoreCase("money")) return Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().toList();
        if (a.length == 2 && a[0].equalsIgnoreCase("give")) return Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().toList();
        if ((a[0].equalsIgnoreCase("give") || a[0].equalsIgnoreCase("setprice") || a[0].equalsIgnoreCase("setslot")
                || a[0].equalsIgnoreCase("enable") || a[0].equalsIgnoreCase("disable")) && a.length == 2)
            return shop.categories().stream().map(ShopManager.Category::id).sorted().toList();
        if ((a[0].equalsIgnoreCase("give") || a[0].equalsIgnoreCase("setprice") || a[0].equalsIgnoreCase("setslot")
                || a[0].equalsIgnoreCase("enable") || a[0].equalsIgnoreCase("disable")) && a.length == 3
                && shop.category(a[1]) != null)
            return shop.category(a[1]).items().keySet().stream().sorted().toList();
        if (a[0].equalsIgnoreCase("give") && a.length == 4) return List.of("1","16","32","64");
        return List.of();
    }
}