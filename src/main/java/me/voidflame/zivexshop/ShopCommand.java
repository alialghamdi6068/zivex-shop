package me.voidflame.zivexshop;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

public final class ShopCommand implements CommandExecutor,TabCompleter{
    private final ZivexShopPlugin plugin;private final ShopManager shop;
    public ShopCommand(ZivexShopPlugin p,ShopManager s){plugin=p;shop=s;}
    private boolean admin(CommandSender s){if(s.hasPermission("zivexshop.admin"))return true;s.sendMessage(plugin.msg("no-permission"));return false;}
    @Override public boolean onCommand(CommandSender s,Command c,String l,String[] a){
        if(a.length==0){if(!(s instanceof Player p)){s.sendMessage(plugin.msg("player-only"));return true;}if(!p.hasPermission("zivexshop.use")){s.sendMessage(plugin.msg("no-permission"));return true;}shop.openMain(p);return true;}
        String sub=a[0].toLowerCase(Locale.ROOT);
        if(sub.equals("reload")){if(!admin(s))return true;plugin.reloadShop();s.sendMessage(plugin.msg("reloaded"));return true;}
        if(sub.equals("list")){if(!admin(s))return true;for(ShopManager.Category cat:shop.categories()){s.sendMessage(ZivexShopPlugin.color("&d"+cat.id()+" &7(slot "+cat.slot()+")"));for(ShopManager.ItemDef d:cat.items().values())s.sendMessage(ZivexShopPlugin.color(" &8- &f"+d.id()+" &7slot="+d.slot()+" price="+d.price()+" delivery="+d.delivery()));}return true;}
        if(sub.equals("debug")){if(!admin(s))return true;s.sendMessage(ZivexShopPlugin.color("&dZivexShop &7categories="+shop.categories().size()+" core="+plugin.getServer().getPluginManager().isPluginEnabled("VoidFlame-Core")));return true;}
        if(sub.equals("setprice")&&a.length>=4){if(!admin(s))return true;try{double v=Double.parseDouble(a[3]);s.sendMessage(shop.setPrice(a[1],a[2],v)?"§aPrice updated.":plugin.msg("invalid-item"));}catch(NumberFormatException e){s.sendMessage("§cInvalid price.");}return true;}
        if(sub.equals("setslot")&&a.length>=4){if(!admin(s))return true;try{int v=Integer.parseInt(a[3]);s.sendMessage(shop.setSlot(a[1],a[2],v)?"§aSlot updated.":plugin.msg("invalid-item"));}catch(NumberFormatException e){s.sendMessage("§cInvalid slot.");}return true;}
        if(sub.equals("give")&&a.length>=4){if(!admin(s))return true;Player target=Bukkit.getPlayerExact(a[1]);if(target==null){s.sendMessage("§cPlayer not found.");return true;}ShopManager.ItemDef d=shop.item(a[2],a[3]);if(d==null){s.sendMessage(plugin.msg("invalid-item"));return true;}int amount=a.length>=5?Math.max(1,Integer.parseInt(a[4])):1;shop.give(target,d,amount);s.sendMessage("§aDelivered.");return true;}
        if(s instanceof Player p)shop.openMain(p);else s.sendMessage(plugin.msg("player-only"));return true;
    }
    @Override public List<String> onTabComplete(CommandSender s,Command c,String l,String[] a){
        if(a.length==1)return List.of("reload","list","give","setprice","setslot","debug");
        if(a[0].equalsIgnoreCase("give")&&a.length==2)return Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().toList();
        if((a[0].equalsIgnoreCase("give")||a[0].equalsIgnoreCase("setprice")||a[0].equalsIgnoreCase("setslot"))&&a.length==3)return shop.categories().stream().map(ShopManager.Category::id).toList();
        if((a[0].equalsIgnoreCase("give")||a[0].equalsIgnoreCase("setprice")||a[0].equalsIgnoreCase("setslot"))&&a.length==4&&shop.category(a[2])!=null)return shop.category(a[2]).items().keySet().stream().sorted().toList();
        return List.of();
    }
}