package me.voidflame.zivexshop;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import java.util.UUID;
public final class EconomyStore {
 private final ZivexShopPlugin plugin; private volatile Economy economy;
 public EconomyStore(ZivexShopPlugin plugin){this.plugin=plugin;refresh();}
 public synchronized void refresh(){RegisteredServiceProvider<Economy> r=Bukkit.getServicesManager().getRegistration(Economy.class);economy=r==null?null:r.getProvider();if(economy==null)plugin.getLogger().warning("No Vault Economy provider is registered.");else plugin.getLogger().info("Vault Economy provider: "+economy.getName());}
 private Economy provider(){Economy e=economy;if(e==null){refresh();e=economy;}return e;}
 public double balance(UUID id){Economy e=provider();if(e==null)return -1D;try{double v=e.getBalance(player(id));return Double.isFinite(v)&&v>=0?v:-1D;}catch(RuntimeException x){plugin.getLogger().warning("Vault balance failed: "+x.getMessage());return -1D;}}
 public boolean withdraw(UUID id,double amount){if(!valid(amount))return false;Economy e=provider();if(e==null)return false;try{return e.withdrawPlayer(player(id),amount).transactionSuccess();}catch(RuntimeException x){plugin.getLogger().warning("Vault withdraw failed: "+x.getMessage());return false;}}
 public boolean deposit(UUID id,double amount){if(!valid(amount))return false;Economy e=provider();if(e==null)return false;try{return e.depositPlayer(player(id),amount).transactionSuccess();}catch(RuntimeException x){plugin.getLogger().warning("Vault deposit failed: "+x.getMessage());return false;}}
 public boolean set(UUID id,double amount){if(!valid(amount))return false;Economy e=provider();if(e==null)return false;try{OfflinePlayer p=player(id);double cur=e.getBalance(p);if(!Double.isFinite(cur)||cur<0)return false;double d=amount-cur;if(Math.abs(d)<1e-7)return true;return d>0?e.depositPlayer(p,d).transactionSuccess():e.withdrawPlayer(p,-d).transactionSuccess();}catch(RuntimeException x){plugin.getLogger().warning("Vault set failed: "+x.getMessage());return false;}}
 public void load(){refresh();} public void close(){economy=null;}
 private OfflinePlayer player(UUID id){return Bukkit.getOfflinePlayer(id);} private boolean valid(double a){return Double.isFinite(a)&&a>=0D;}
}