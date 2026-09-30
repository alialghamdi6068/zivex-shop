package me.voidflame.zivexshop;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class ShopListener implements Listener {
    private final ZivexShopPlugin plugin; private final ShopManager shop;
    public ShopListener(ZivexShopPlugin p,ShopManager s){plugin=p;shop=s;}
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        String title=e.getView().getTitle();
        String main=ZivexShopPlugin.color(plugin.getConfig().getString("settings.main-title","&8Shop"));
        if(title.equals(main)){
            e.setCancelled(true);
            if(e.getRawSlot()>=0&&e.getRawSlot()<27)for(ShopManager.Category c:shop.categories())if(c.slot()==e.getRawSlot()){shop.openCategory(p,c.id());return;}
            return;
        }
        ShopManager.Pending pending=shop.pending(p);
        if(pending!=null){
            String pt=ZivexShopPlugin.color(plugin.getConfig().getString("settings.purchase-title","&8Purchase: {item}").replace("{item}",ShopManager.ChatText.strip(pending.item().name())));
            if(title.equals(pt)){
                e.setCancelled(true);int slot=e.getRawSlot();int q=pending.quantity();int step=Math.max(1,plugin.getConfig().getInt("settings.quantity-step",1));
                if(slot==11)shop.openPurchase(p,pending.item(),Math.max(1,q-step));
                else if(slot==15)shop.openPurchase(p,pending.item(),Math.min(plugin.getConfig().getInt("settings.max-quantity",64),q+step));
                else if(slot==21){shop.purchase(p,pending);shop.clearPending(p);if(plugin.getConfig().getBoolean("settings.close-on-purchase",true))p.closeInventory();else shop.openPurchase(p,pending.item(),q);}
                else if(slot==23){shop.clearPending(p);shop.openCategory(p,pending.item().category());}
                return;
            }
        }
        for(ShopManager.Category c:shop.categories()){
            String ct=ZivexShopPlugin.color(plugin.getConfig().getString("settings.category-title-format","&8{category} Shop").replace("{category}",ShopManager.ChatText.strip(c.name())));
            if(title.equals(ct)){
                e.setCancelled(true);
                if(e.getRawSlot()==22){shop.openMain(p);return;}
                if(e.getRawSlot()>=0&&e.getRawSlot()<27)for(ShopManager.ItemDef d:c.items().values())if(d.slot()==e.getRawSlot()){shop.openPurchase(p,d,1);return;}
                return;
            }
        }
    }
    @EventHandler public void drag(InventoryDragEvent e){String title=e.getView().getTitle();if(title.equals(ZivexShopPlugin.color(plugin.getConfig().getString("settings.main-title","&8Shop")))||title.contains(" Shop")||title.contains("Purchase:"))e.setCancelled(true);}
}