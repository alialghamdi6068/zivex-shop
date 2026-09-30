package me.voidflame.zivexshop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.ConfigurationSection;
import java.util.*;

public final class ShopManager {
    public record ItemDef(String category,String id,Material material,String name,int slot,double price,String currency,String delivery,int amount,String target,List<String> lore) {}
    public record Category(String id,String name,Material material,int slot,List<String> lore,LinkedHashMap<String,ItemDef> items) {}
    public record Pending(ItemDef item,int quantity) {}
    private final ZivexShopPlugin plugin; private final CoreEconomy economy;
    private final LinkedHashMap<String,Category> categories=new LinkedHashMap<>();
    private final Map<UUID,Pending> pending=new HashMap<>();
    public ShopManager(ZivexShopPlugin p,CoreEconomy e){plugin=p;economy=e;}
    public void reload(){
        categories.clear(); ConfigurationSection root=plugin.getConfig().getConfigurationSection("categories"); if(root==null)return;
        Set<Integer> mainSlots=new HashSet<>();
        for(String id:root.getKeys(false)){
            ConfigurationSection c=root.getConfigurationSection(id); if(c==null)continue;
            LinkedHashMap<String,ItemDef> items=new LinkedHashMap<>(); ConfigurationSection is=c.getConfigurationSection("items");
            if(is!=null)for(String itemId:is.getKeys(false)){
                ConfigurationSection x=is.getConfigurationSection(itemId); if(x==null)continue;
                ItemDef d=new ItemDef(id,itemId,material(x.getString("material"),Material.STONE),x.getString("name",itemId),x.getInt("slot",0),x.getDouble("price",0),x.getString("currency","MONEY"),x.getString("delivery","VANILLA"),Math.max(1,x.getInt("amount",1)),x.getString("target",itemId),x.getStringList("lore"));
                if(d.slot()<0||d.slot()>=plugin.getConfig().getInt("settings.category-size",27))plugin.getLogger().warning("Invalid slot for "+id+"/"+itemId+": "+d.slot());
                items.put(itemId,d);
            }
            Category cat=new Category(id,c.getString("display-name",id),material(c.getString("material"),Material.CHEST),c.getInt("slot",0),c.getStringList("lore"),items); categories.put(id,cat);
            if(!mainSlots.add(cat.slot()))plugin.getLogger().warning(plugin.msg("duplicate-slot").replace("{slot}",String.valueOf(cat.slot())));
        }
    }
    public Collection<Category> categories(){return categories.values();}
    public Category category(String id){return categories.get(id.toLowerCase(Locale.ROOT));}
    public ItemDef item(String category,String id){Category c=category(category);return c==null?null:c.items().get(id.toLowerCase(Locale.ROOT));}
    public void openMain(Player p){
        Inventory inv=Bukkit.createInventory(null,27,ZivexShopPlugin.color(plugin.getConfig().getString("settings.main-title","&8Shop")));
        for(Category c:categories.values())inv.setItem(c.slot(),icon(c.material(),c.name(),c.lore())); p.openInventory(inv);
    }
    public void openCategory(Player p,String id){
        Category c=category(id);if(c==null){p.sendMessage(plugin.msg("invalid-category"));return;}
        String title=plugin.getConfig().getString("settings.category-title-format","&8{category} Shop").replace("{category}",ChatText.strip(c.name()));
        Inventory inv=Bukkit.createInventory(null,27,ZivexShopPlugin.color(title));
        for(ItemDef d:c.items().values())inv.setItem(d.slot(),icon(d.material(),d.name(),loreFor(d)));
        inv.setItem(22,icon(Material.ARROW,"&cBack",List.of("&7Return to the main shop")));p.openInventory(inv);
    }
    public void openPurchase(Player p,ItemDef d,int quantity){
        quantity=Math.max(1,Math.min(quantity,plugin.getConfig().getInt("settings.max-quantity",64)));pending.put(p.getUniqueId(),new Pending(d,quantity));
        String title=plugin.getConfig().getString("settings.purchase-title","&8Purchase: {item}").replace("{item}",ChatText.strip(d.name()));
        Inventory inv=Bukkit.createInventory(null,27,ZivexShopPlugin.color(title));
        inv.setItem(13,icon(d.material(),d.name(),List.of("&7Unit price: &f"+money(d.price(),d.currency()),"&7Quantity: &f"+quantity,"&7Total: &f"+money(d.price()*quantity,d.currency()))));
        inv.setItem(11,icon(Material.RED_DYE,"&c-1",List.of("&7Decrease quantity")));inv.setItem(15,icon(Material.LIME_DYE,"&a+1",List.of("&7Increase quantity")));
        inv.setItem(21,icon(Material.GREEN_WOOL,"&aConfirm",List.of("&7Purchase now")));inv.setItem(23,icon(Material.RED_WOOL,"&cCancel",List.of("&7Return to shop")));p.openInventory(inv);
    }
    public Pending pending(Player p){return pending.get(p.getUniqueId());}
    public void clearPending(Player p){pending.remove(p.getUniqueId());}
    private List<String> loreFor(ItemDef d){List<String> l=new ArrayList<>(d.lore());l.add("&7Price: &f"+money(d.price(),d.currency()));l.add("&eClick to purchase");return l;}
    private String money(double n,String currency){String symbol=plugin.getConfig().getString("settings.currency-symbol","$");return currency.equalsIgnoreCase("MONEY")?symbol+trim(n):trim(n)+" "+currency;}
    private String trim(double n){return Math.rint(n)==n?String.format(Locale.US,"%,.0f",n):String.format(Locale.US,"%,.2f",n);}
    private ItemStack icon(Material m,String name,List<String> lore){ItemStack i=new ItemStack(m);ItemMeta meta=i.getItemMeta();meta.setDisplayName(ZivexShopPlugin.color(name));meta.setLore(lore.stream().map(ZivexShopPlugin::color).toList());i.setItemMeta(meta);return i;}
    private Material material(String s,Material fallback){try{return Material.valueOf(s.toUpperCase(Locale.ROOT));}catch(Exception e){plugin.getLogger().warning("Unknown material: "+s);return fallback;}}
    public boolean purchase(Player p,Pending pd){
        ItemDef d=pd.item();int q=pd.quantity();double total=d.price()*q;if(total<0||!Double.isFinite(total))return false;
        if("MONEY".equalsIgnoreCase(d.currency())){
            if(plugin.getConfig().getBoolean("economy.required",true)&&economy.balance(p.getUniqueId())<0){p.sendMessage(plugin.msg("no-economy"));return false;}
            if(economy.balance(p.getUniqueId())<total||!economy.withdraw(p.getUniqueId(),total)){p.sendMessage(plugin.msg("insufficient").replace("{price}",money(total,d.currency())).replace("{currency}","money"));return false;}
        } else if("SHARDS".equalsIgnoreCase(d.currency())) {
            // Shards belong to the separate ZivexShards plugin. Never deliver these items until that economy is integrated.
            p.sendMessage(plugin.msg("no-shard-economy"));return false;
        } else {
            p.sendMessage(plugin.msg("no-shard-economy"));return false;
        }
        boolean delivered=deliver(p,d,q);
        if(!delivered){if("MONEY".equalsIgnoreCase(d.currency())&&plugin.getConfig().getBoolean("settings.refund-on-delivery-failure",true))economy.deposit(p.getUniqueId(),total);p.sendMessage(plugin.msg("delivery-failed"));return false;}
        p.sendMessage(plugin.msg("purchased").replace("{amount}",String.valueOf(q)).replace("{item}",ChatText.strip(d.name())).replace("{total}",money(total,d.currency())).replace("{currency}",d.currency()));return true;
    }
    private boolean deliver(Player p,ItemDef d,int q){
        if("VANILLA".equalsIgnoreCase(d.delivery())){
            int left=q*d.amount();ItemStack stack=new ItemStack(d.material());
            while(left>0){int n=Math.min(left,stack.getMaxStackSize());ItemStack part=stack.clone();part.setAmount(n);Map<Integer,ItemStack> overflow=p.getInventory().addItem(part);if(!overflow.isEmpty())return false;left-=n;}return true;
        }
        if("CRATE_KEY".equalsIgnoreCase(d.delivery())){if(!plugin.getConfig().getBoolean("integrations.phoenix-crate-lite.enabled",true))return false;return dispatch(plugin.getConfig().getString("integrations.phoenix-crate-lite.key-command","crate key give {player} {key} {amount}"),p,d.target(),q);}
        if("SPAWNER".equalsIgnoreCase(d.delivery())){if(!plugin.getConfig().getBoolean("integrations.smart-spawners.enabled",true))return false;return dispatch(plugin.getConfig().getString("integrations.smart-spawners.spawner-command","smartspawners give {player} {type} {amount}"),p,d.target(),q);}
        if("COMMAND".equalsIgnoreCase(d.delivery()))return dispatch(d.target(),p,d.target(),q);
        return false;
    }
    private boolean dispatch(String raw,Player p,String target,int amount){String cmd=raw.replace("{player}",p.getName()).replace("{key}",target).replace("{type}",target).replace("{amount}",String.valueOf(amount));ConsoleCommandSender console=Bukkit.getConsoleSender();return Bukkit.dispatchCommand(console,cmd);}
    public boolean setPrice(String cat,String id,double price){if(item(cat,id)==null)return false;plugin.getConfig().set("categories."+cat+".items."+id+".price",price);plugin.saveConfig();reload();return true;}
    public boolean setSlot(String cat,String id,int slot){if(item(cat,id)==null)return false;plugin.getConfig().set("categories."+cat+".items."+id+".slot",slot);plugin.saveConfig();reload();return true;}
    public void give(Player target,ItemDef d,int amount){deliver(target,d,amount);}
    static final class ChatText{static String strip(String s){return org.bukkit.ChatColor.stripColor(ZivexShopPlugin.color(s));}}
}