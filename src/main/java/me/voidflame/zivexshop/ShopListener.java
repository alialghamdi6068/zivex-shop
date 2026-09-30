package me.voidflame.zivexshop;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class ShopListener implements Listener {
    private final ZivexShopPlugin plugin;
    private final ShopManager shop;

    public ShopListener(ZivexShopPlugin plugin, ShopManager shop) {
        this.plugin = plugin;
        this.shop = shop;
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        String title = e.getView().getTitle();
        String main = ZivexShopPlugin.color(plugin.getConfig().getString("settings.main-title", "&8Shop"));

        if (title.equals(main)) {
            e.setCancelled(true);
            int size = Math.max(9, Math.min(54, plugin.getConfig().getInt("settings.main-size", 27)));
            if (e.getRawSlot() >= 0 && e.getRawSlot() < size) {
                for (ShopManager.Category c : shop.categories()) {
                    if (c.slot() == e.getRawSlot()) {
                        shop.openCategory(p, c.id());
                        return;
                    }
                }
            }
            return;
        }

        ShopManager.Pending pd = shop.pending(p);
        if (pd != null) {
            String pt = ZivexShopPlugin.color(
                    plugin.getConfig().getString("settings.purchase-title", "&8Purchase: {item}")
                            .replace("{item}", ShopManager.ChatText.strip(pd.item().name()))
            );

            if (title.equals(pt)) {
                e.setCancelled(true);
                int slot = e.getRawSlot();
                int q = pd.quantity();
                int step = Math.max(1, plugin.getConfig().getInt("settings.quantity-step", 1));
                int max = Math.max(1, plugin.getConfig().getInt("settings.max-quantity", 64));

                if (slot == 11) {
                    shop.openPurchase(p, pd.item(), Math.max(1, q - step));
                } else if (slot == 15) {
                    shop.openPurchase(p, pd.item(), Math.min(max, q + step));
                } else if (slot == 21) {
                    boolean success = shop.purchase(p, pd);
                    shop.clearPending(p);
                    if (success && plugin.getConfig().getBoolean("settings.close-on-purchase", true)) {
                        p.closeInventory();
                    } else if (success) {
                        shop.openPurchase(p, pd.item(), q);
                    } else {
                        shop.openPurchase(p, pd.item(), q);
                    }
                } else if (slot == 23) {
                    shop.clearPending(p);
                    shop.openCategory(p, pd.item().category());
                }
                return;
            }
        }

        for (ShopManager.Category c : shop.categories()) {
            String ct = ZivexShopPlugin.color(
                    plugin.getConfig().getString("settings.category-title-format", "&8{category} Shop")
                            .replace("{category}", ShopManager.ChatText.strip(c.name()))
            );

            if (title.equals(ct)) {
                e.setCancelled(true);
                int backSlot = plugin.getConfig().getInt("settings.back-slot", 22);
                if (e.getRawSlot() == backSlot) {
                    shop.openMain(p);
                    return;
                }

                int size = Math.max(9, Math.min(54, plugin.getConfig().getInt("settings.category-size", 27)));
                if (e.getRawSlot() >= 0 && e.getRawSlot() < size) {
                    for (ShopManager.ItemDef d : c.items().values()) {
                        if (d.slot() == e.getRawSlot()) {
                            shop.openPurchase(p, d, 1);
                            return;
                        }
                    }
                }
                return;
            }
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        String title = e.getView().getTitle();
        String main = ZivexShopPlugin.color(plugin.getConfig().getString("settings.main-title", "&8Shop"));
        String purchasePrefix = ZivexShopPlugin.color(
                plugin.getConfig().getString("settings.purchase-title", "&8Purchase: {item}")
        ).replace("{item}", "");
        String categorySuffix = " Shop";

        if (title.equals(main) || title.startsWith(purchasePrefix) || title.endsWith(categorySuffix)) {
            e.setCancelled(true);
        }
    }
}