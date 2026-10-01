package me.voidflame.zivexshop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

public final class ShopCommand implements CommandExecutor, TabCompleter {
    private final ZivexShopPlugin plugin;
    private final ShopManager shop;
    private final ShopAdminGui adminGui;

    public ShopCommand(ZivexShopPlugin plugin, ShopManager shop, ShopAdminGui adminGui) {
        this.plugin = plugin;
        this.shop = shop;
        this.adminGui = adminGui;
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("zivexshop.admin")) return true;
        sender.sendMessage(plugin.msg("no-permission"));
        return false;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean adminCommand = command.getName().equalsIgnoreCase("shopadmin");

        if (!adminCommand) {
            if (!(sender instanceof Player p)) { sender.sendMessage(plugin.msg("player-only")); return true; }
            if (!p.hasPermission("zivexshop.use")) { p.sendMessage(plugin.msg("no-permission")); return true; }
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
            case "add" -> add(sender, args);
            case "delete", "remove" -> delete(sender, args);
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
                + " shards=" + plugin.getServer().getPluginManager().isPluginEnabled("ZivexShards")
                + " vault=" + plugin.getServer().getPluginManager().isPluginEnabled("Vault")
                + " phoenix=" + (plugin.getServer().getPluginManager().isPluginEnabled("PhoenixCrateLite")
                    || plugin.getServer().getPluginManager().isPluginEnabled("PhoenixCrates"))
                + " smartspawner=" + plugin.getServer().getPluginManager().isPluginEnabled("SmartSpawner")));
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

    private void add(CommandSender sender, String[] a) {
        if (a.length < 8) {
            sender.sendMessage(plugin.msg("add-usage"));
            return;
        }

        String category = a[1].toLowerCase(Locale.ROOT);
        String id = a[2].toLowerCase(Locale.ROOT);

        Material material;
        try {
            material = Material.valueOf(a[3].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(plugin.msg("invalid-material"));
            return;
        }

        int slot;
        double price;
        try {
            slot = Integer.parseInt(a[4]);
            price = Double.parseDouble(a[5]);
            if (!Double.isFinite(price) || price < 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            sender.sendMessage(plugin.msg("invalid-number"));
            return;
        }

        String currency = a[6].toUpperCase(Locale.ROOT);
        String delivery = a[7].toUpperCase(Locale.ROOT);
        if (!Set.of("MONEY", "SHARDS").contains(currency)) {
            sender.sendMessage(plugin.msg("unsupported-currency").replace("{currency}", currency));
            return;
        }
        if (!Set.of("VANILLA", "CRATE_KEY", "SPAWNER", "COMMAND").contains(delivery)) {
            sender.sendMessage(plugin.msg("invalid-delivery"));
            return;
        }

        int amount = 1;
        if (a.length >= 9) {
            try {
                amount = Integer.parseInt(a[8]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(plugin.msg("invalid-number"));
                return;
            }
        }

        String target = a.length >= 10 ? a[9] : id;
        String name = prettyName(id);
        boolean success = shop.addItem(category, id, material, name, slot, price, currency, delivery, amount, target);
        sender.sendMessage(plugin.msg(success ? "item-added" : "item-add-failed")
                .replace("{item}", id).replace("{category}", category));
    }

    private void delete(CommandSender sender, String[] a) {
        if (a.length < 3) {
            sender.sendMessage(plugin.msg("delete-usage"));
            return;
        }
        String category = a[1].toLowerCase(Locale.ROOT);
        String id = a[2].toLowerCase(Locale.ROOT);
        boolean success = shop.deleteItem(category, id);
        sender.sendMessage(plugin.msg(success ? "item-deleted" : "invalid-item")
                .replace("{item}", id).replace("{category}", category));
    }

    private String prettyName(String id) {
        String[] parts = id.replace('-', '_').split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.length() == 0 ? id : out.toString();
    }

    private void money(CommandSender sender, String[] a) {
        if (a.length < 3) { sender.sendMessage(ZivexShopPlugin.color("&cUsage: /shopadmin money <balance|give|take|set> <player> [amount]")); return; }
        Player target = Bukkit.getPlayerExact(a[2]);
        if (target == null) { sender.sendMessage(plugin.msg("player-not-found")); return; }
        if (a[1].equalsIgnoreCase("balance")) {
            double balance = shop.money(target);
            if (balance < 0) sender.sendMessage(plugin.msg("no-economy"));
            else sender.sendMessage(ZivexShopPlugin.color("&d" + target.getName() + " &7balance: &f$" + String.format(Locale.US, "%,.2f", balance)));
            return;
        }
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
        if (a.length == 1) return List.of("gui","reload","list","debug","setprice","setslot","enable","disable","add","delete","remove","give","money");
        if (a.length == 2 && a[0].equalsIgnoreCase("money")) return List.of("balance","give","take","set");
        if (a.length == 3 && a[0].equalsIgnoreCase("money")) return Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().toList();
        if ((a[0].equalsIgnoreCase("give") || a[0].equalsIgnoreCase("setprice") || a[0].equalsIgnoreCase("setslot")
                || a[0].equalsIgnoreCase("enable") || a[0].equalsIgnoreCase("disable")
                || a[0].equalsIgnoreCase("delete") || a[0].equalsIgnoreCase("remove")) && a.length == 2)
            return shop.categories().stream().map(ShopManager.Category::id).sorted().toList();
        if ((a[0].equalsIgnoreCase("give") || a[0].equalsIgnoreCase("setprice") || a[0].equalsIgnoreCase("setslot")
                || a[0].equalsIgnoreCase("enable") || a[0].equalsIgnoreCase("disable")
                || a[0].equalsIgnoreCase("delete") || a[0].equalsIgnoreCase("remove")) && a.length == 3
                && shop.category(a[1]) != null)
            return shop.category(a[1]).items().keySet().stream().sorted().toList();
        if (a[0].equalsIgnoreCase("give") && a.length == 4) return List.of("1","16","32","64");
        if (a[0].equalsIgnoreCase("add")) {
            if (a.length == 2) return shop.categories().stream().map(ShopManager.Category::id).sorted().toList();
            if (a.length == 4) return List.of("DIAMOND","EMERALD","ENDER_PEARL","AMETHYST_SHARD","SPAWNER","TRIPWIRE_HOOK");
            if (a.length == 7) return List.of("MONEY","SHARDS");
            if (a.length == 8) return List.of("VANILLA","CRATE_KEY","SPAWNER","COMMAND");
        }
        return List.of();
    }
}
