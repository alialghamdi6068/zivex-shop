package me.voidflame.zivexshop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

final class ShopAdminGui {
    static final String MAIN = "&8Shop Admin";
    static final String CATEGORY = "&8Edit: {category}";
    static final String ITEM = "&8Edit Item: {item}";

    private final ZivexShopPlugin plugin;
    private final ShopManager shop;
    private final Map<UUID, String> moving = new HashMap<>();
    private final Set<UUID> suppressCloseClear = new HashSet<>();

    ShopAdminGui(ZivexShopPlugin plugin, ShopManager shop) {
        this.plugin = plugin;
        this.shop = shop;
    }

    void open(Player p) {
        moving.remove(p.getUniqueId());
        if (!plugin.getConfig().getBoolean("settings.editor-enabled", true)) {
            p.sendMessage(plugin.msg("editor-disabled"));
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 27, color(MAIN));
        fillBorder(inv, new int[]{0,1,2,3,4,5,6,7,8,9,10,16,17,19,20,21,23,24,25,26});
        for (ShopManager.Category c : shop.categories())
            if (c.slot() >= 0 && c.slot() < 27) inv.setItem(c.slot(), icon(c.material(), c.name(), c.lore()));
        inv.setItem(18, icon(Material.COMPASS, "&dReload Config", List.of("&7Reload the shop configuration")));
        inv.setItem(22, icon(Material.BARRIER, "&cClose", List.of("&7Close the editor")));
        openInventory(p, inv);
    }

    void openCategory(Player p, ShopManager.Category c) {
        Inventory inv = Bukkit.createInventory(null, 27, color(CATEGORY.replace("{category}", ShopManager.ChatText.strip(c.name()))));
        fillBorder(inv, new int[]{0,1,2,3,4,5,6,7,8,9,17,18,24,25,26});
        for (ShopManager.ItemDef d : c.items().values()) {
            if (d.slot() >= 0 && d.slot() < 27)
                inv.setItem(d.slot(), icon(d.material(), d.name(), List.of(
                        "&7Price: &f" + price(d),
                        "&7Enabled: " + (d.enabled() ? "&aYes" : "&cNo"),
                        "&eClick to edit"
                )));
        }
        inv.setItem(18, icon(Material.ARROW, "&cBack", List.of("&7Back to admin")));
        p.openInventory(inv);
    }

    void openItem(Player p, ShopManager.ItemDef d) {
        Inventory inv = Bukkit.createInventory(null, 27,
                color(ITEM.replace("{item}", ShopManager.ChatText.strip(d.name()))));
        fillBorder(inv, new int[]{0,1,2,3,4,5,6,7,8,9,17,18,19,21,23,25,26});
        inv.setItem(4, icon(d.material(), d.name(), List.of(
                "&7Price: &f" + price(d),
                "&7Slot: &f" + d.slot(),
                "&7Amount: &f" + d.amount(),
                "&7Enabled: " + (d.enabled() ? "&aYes" : "&cNo"),
                "&7Currency: &f" + d.currency(),
                "&7Delivery: &f" + d.delivery()
        )));
        inv.setItem(10, icon(Material.REDSTONE_BLOCK, "&c-" + small(), List.of("&7Decrease price")));
        inv.setItem(11, icon(Material.RED_DYE, "&c-1", List.of("&7Decrease price by 1")));
        inv.setItem(15, icon(Material.LIME_DYE, "&a+1", List.of("&7Increase price by 1")));
        inv.setItem(16, icon(Material.EMERALD_BLOCK, "&a+" + large(), List.of("&7Increase price")));
        inv.setItem(13, icon(d.enabled() ? Material.LIME_WOOL : Material.RED_WOOL,
                d.enabled() ? "&aEnabled" : "&cDisabled",
                List.of("&7Click to toggle this item")));
        inv.setItem(20, icon(Material.COMPARATOR, "&eMove Item", List.of("&7Click, then click a slot in the item editor")));
        inv.setItem(22, icon(Material.ARROW, "&cBack", List.of("&7Back to category")));
        inv.setItem(24, icon(Material.BARRIER, "&cClose", List.of("&7Close the editor")));
        p.openInventory(inv);
    }

    boolean handle(Player p, String title, int slot) {
        String main = color(MAIN);
        if (title.equals(main)) {
            if (slot == 18) { plugin.reloadShop(); open(p); return true; }
            if (slot == 22) { p.closeInventory(); return true; }
            for (ShopManager.Category c : shop.categories())
                if (c.slot() == slot) { openCategory(p, c); return true; }
            return true;
        }

        for (ShopManager.Category c : shop.categories()) {
            String ct = color(CATEGORY.replace("{category}", ShopManager.ChatText.strip(c.name())));
            if (!title.equals(ct)) continue;

            if (slot == 18) { open(p); return true; }

            String movingKey = moving.get(p.getUniqueId());
            if (movingKey != null) {
                String[] parts = movingKey.split(":", 2);
                String sourceCategory = parts[0];
                String movingId = parts.length == 2 ? parts[1] : "";
                if (!c.id().equalsIgnoreCase(sourceCategory)) {
                    p.sendMessage(plugin.msg("invalid-slot"));
                    return true;
                }
                if (slot >= 0 && slot < 27 && shop.setSlot(c.id(), movingId, slot)) {
                    moving.remove(p.getUniqueId());
                    openCategory(p, shop.category(c.id()));
                    p.sendMessage(plugin.msg("slot-updated"));
                } else {
                    p.sendMessage(plugin.msg("invalid-slot"));
                }
                return true;
            }

            if (slot >= 0 && slot < 54) {
                for (ShopManager.ItemDef d : c.items().values())
                    if (d.slot() == slot) { openItem(p, d); return true; }
            }
            return true;
        }

        for (ShopManager.Category c : shop.categories()) {
            for (ShopManager.ItemDef d : c.items().values()) {
                String it = color(ITEM.replace("{item}", ShopManager.ChatText.strip(d.name())));
                if (!title.equals(it)) continue;

                switch (slot) {
                    case 10 -> changePrice(p, d, -large());
                    case 11 -> changePrice(p, d, -small());
                    case 15 -> changePrice(p, d, small());
                    case 16 -> changePrice(p, d, large());
                    case 13 -> {
                        shop.setEnabled(d.category(), d.id(), !d.enabled());
                        openItem(p, shop.item(d.category(), d.id()));
                    }
                    case 20 -> {
                        moving.put(p.getUniqueId(), c.id() + ":" + d.id());
                        openCategory(p, c);
                        p.sendMessage(plugin.msg("move-item"));
                    }
                    case 22 -> openCategory(p, c);
                    case 24 -> p.closeInventory();
                    default -> {}
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Opens another editor view without treating the InventoryCloseEvent from the
     * previous view as a real editor close. This preserves Move Item state.
     */
    private void openInventory(Player p, Inventory inv) {
        suppressCloseClear.add(p.getUniqueId());
        p.openInventory(inv);
    }

    boolean handleClose(Player p) {
        return !suppressCloseClear.remove(p.getUniqueId());
    }

    void clear(Player p) {
        UUID id = p.getUniqueId();
        suppressCloseClear.remove(id);
        moving.remove(id);
    }

    private void changePrice(Player p, ShopManager.ItemDef d, double delta) {
        double next = Math.max(0, d.price() + delta);
        if (!shop.setPrice(d.category(), d.id(), next)) {
            p.sendMessage(plugin.msg("invalid-price"));
            return;
        }
        openItem(p, shop.item(d.category(), d.id()));
        p.sendMessage(plugin.msg("price-updated"));
    }

    private int small() { return Math.max(1, plugin.getConfig().getInt("settings.editor-price-step-small", 1)); }
    private int large() { return Math.max(1, plugin.getConfig().getInt("settings.editor-price-step-large", 100)); }

    private String price(ShopManager.ItemDef d) {
        if ("MONEY".equalsIgnoreCase(d.currency()))
            return plugin.getConfig().getString("settings.currency-symbol", "$") + String.format(Locale.US, "%,.2f", d.price());
        return String.format(Locale.US, "%,.0f", d.price()) + " " + plugin.getConfig().getString("settings.shard-currency-name", "Shards");
    }

    private void fillBorder(Inventory inventory, int[] slots) {
        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot : slots) if (slot >= 0 && slot < inventory.getSize() && inventory.getItem(slot) == null) {
            inventory.setItem(slot, filler.clone());
        }
    }

    private ItemStack icon(Material m, String name, List<String> lore) {
        ItemStack i = new ItemStack(m);
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(color(name));
        meta.setLore(lore.stream().map(ZivexShopPlugin::color).toList());
        i.setItemMeta(meta);
        return i;
    }

    private String color(String s) { return ZivexShopPlugin.color(s); }
}
