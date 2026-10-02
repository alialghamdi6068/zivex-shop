package me.voidflame.zivexshop;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ShopListener implements Listener {
    private final ZivexShopPlugin plugin;
    private final ShopManager shop;
    private final ShopAdminGui adminGui;

    public ShopListener(ZivexShopPlugin plugin, ShopManager shop, ShopAdminGui adminGui) {
        this.plugin = plugin;
        this.shop = shop;
        this.adminGui = adminGui;
    }

    public ShopAdminGui adminGui() { return adminGui; }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        String title = e.getView().getTitle();
        if (adminTitle(title)) {
            e.setCancelled(true);
            adminGui.handle(p, title, e.getRawSlot());
            return;
        }

        if (!(e.getView().getTopInventory().getHolder() instanceof ShopManager.ShopHolder holder)) return;
        e.setCancelled(true);

        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getView().getTopInventory().getSize()) return;

        switch (holder.type()) {
            case MAIN -> {
                for (ShopManager.Category c : shop.categories()) {
                    if (c.slot() == slot) {
                        shop.openCategory(p, c.id());
                        return;
                    }
                }
            }
            case CATEGORY -> {
                ShopManager.Category c = shop.category(holder.id());
                if (c == null) {
                    shop.clearPending(p);
                    p.closeInventory();
                    return;
                }

                int backSlot = plugin.getConfig().getInt("settings.back-slot", 18);
                if (slot == backSlot) {
                    shop.clearPending(p);
                    shop.openMain(p);
                    return;
                }

                for (ShopManager.ItemDef d : c.items().values()) {
                    if (d.slot() == slot && d.enabled()) {
                        shop.openPurchase(p, d, 1);
                        return;
                    }
                }
            }
            case PURCHASE -> handlePurchaseClick(p, slot);
        }
    }

    private void handlePurchaseClick(Player p, int slot) {
        ShopManager.Pending pd = shop.pending(p);
        if (pd == null) {
            p.closeInventory();
            return;
        }

        int q = pd.quantity();
        int max = Math.max(1, plugin.getConfig().getInt("settings.max-quantity", 64));
        int step = Math.max(1, plugin.getConfig().getInt("settings.quantity-step", 1));

        // Donut-style confirmation layout:
        // 10=-10, 11=-1, 13=item, 15=+1, 16=+10, 17=max, 21=cancel, 23=confirm.
        switch (slot) {
            case 10 -> shop.openPurchase(p, pd.item(), Math.max(1, q - 10));
            case 11 -> shop.openPurchase(p, pd.item(), Math.max(1, q - step));
            case 15 -> shop.openPurchase(p, pd.item(), Math.min(max, q + step));
            case 16 -> shop.openPurchase(p, pd.item(), Math.min(max, q + 10));
            case 17 -> shop.openPurchase(p, pd.item(), max);
            case 21 -> {
                shop.clearPending(p);
                shop.openCategory(p, pd.item().category());
            }
            case 23 -> {
                boolean success = shop.purchase(p, pd);
                shop.clearPending(p);
                if (success && plugin.getConfig().getBoolean("settings.close-on-purchase", true)) {
                    p.closeInventory();
                } else if (!success) {
                    shop.openPurchase(p, pd.item(), q);
                }
            }
            default -> { }
        }
    }

    private boolean adminTitle(String title) {
        if (title.equals(ZivexShopPlugin.color(ShopAdminGui.MAIN))) return true;
        for (ShopManager.Category c : shop.categories()) {
            if (title.equals(ZivexShopPlugin.color(
                    ShopAdminGui.CATEGORY.replace("{category}", ShopManager.ChatText.strip(c.name()))))) return true;
            for (ShopManager.ItemDef d : c.items().values()) {
                if (title.equals(ZivexShopPlugin.color(
                        ShopAdminGui.ITEM.replace("{item}", ShopManager.ChatText.strip(d.name()))))) return true;
            }
        }
        return false;
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        if (adminTitle(e.getView().getTitle())) {
            e.setCancelled(true);
            return;
        }
        if (e.getView().getTopInventory().getHolder() instanceof ShopManager.ShopHolder) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;

        if (adminGui.handleClose(p)) {
            adminGui.clear(p);
        }

        if (!(e.getView().getTopInventory().getHolder() instanceof ShopManager.ShopHolder holder)
                || holder.type() != ShopManager.ShopHolder.Type.PURCHASE) return;

        // Opening another purchase screen closes the old inventory first.
        // Check the next inventory one tick later before clearing the pending state.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!(p.getOpenInventory().getTopInventory().getHolder() instanceof ShopManager.ShopHolder next)
                    || next.type() != ShopManager.ShopHolder.Type.PURCHASE) {
                shop.clearPending(p);
            }
        });
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        shop.clearPending(e.getPlayer());
        adminGui.clear(e.getPlayer());
    }
}