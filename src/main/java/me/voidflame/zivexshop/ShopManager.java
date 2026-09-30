package me.voidflame.zivexshop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ShopManager {
    public record ItemDef(String category, String id, Material material, String name, int slot, double price,
                          String currency, String delivery, int amount, String target, List<String> lore, boolean enabled) {}
    public record Category(String id, String name, Material material, int slot, List<String> lore,
                           LinkedHashMap<String, ItemDef> items) {}
    public record Pending(ItemDef item, int quantity) {}

    private final ZivexShopPlugin plugin;
    private final ShardEconomy shards = new ShardEconomy();
    private final LinkedHashMap<String, Category> categories = new LinkedHashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    public ShopManager(ZivexShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        categories.clear();
        pending.clear();

        ConfigurationSection root = plugin.getConfig().getConfigurationSection("categories");
        if (root == null) {
            plugin.getLogger().severe("Missing categories section.");
            return;
        }

        int categorySize = normalizedSize(plugin.getConfig().getInt("settings.category-size", 27));
        int mainSize = normalizedSize(plugin.getConfig().getInt("settings.main-size", 27));
        Set<Integer> mainSlots = new HashSet<>();

        for (String rawId : root.getKeys(false)) {
            String id = rawId.toLowerCase(Locale.ROOT);
            ConfigurationSection c = root.getConfigurationSection(rawId);
            if (c == null) continue;

            LinkedHashMap<String, ItemDef> items = new LinkedHashMap<>();
            ConfigurationSection is = c.getConfigurationSection("items");

            if (is != null) {
                Set<Integer> itemSlots = new HashSet<>();
                for (String rawItemId : is.getKeys(false)) {
                    String itemId = rawItemId.toLowerCase(Locale.ROOT);
                    ConfigurationSection x = is.getConfigurationSection(rawItemId);
                    if (x == null) continue;

                    int slot = x.getInt("slot", -1);
                    if (slot < 0 || slot >= categorySize) {
                        plugin.getLogger().warning("Invalid slot for " + id + "/" + itemId + ": " + slot);
                        continue;
                    }
                    if (!itemSlots.add(slot)) {
                        plugin.getLogger().warning("Duplicate item slot in " + id + ": " + slot);
                        continue;
                    }

                    double price = x.getDouble("price", -1);
                    if (!Double.isFinite(price) || price < 0) {
                        plugin.getLogger().warning("Invalid price for " + id + "/" + itemId + ": " + price);
                        continue;
                    }

                    Material material = material(x.getString("material"), Material.STONE);
                    int amount = Math.max(1, x.getInt("amount", 1));
                    String delivery = x.getString("delivery", "VANILLA");
                    boolean enabled = x.getBoolean("enabled", true);

                    ItemDef d = new ItemDef(id, itemId, material, x.getString("name", itemId), slot, price,
                            x.getString("currency", "MONEY"), delivery, amount,
                            x.getString("target", itemId), x.getStringList("lore"), enabled);
                    items.put(itemId, d);
                }
            }

            int mainSlot = c.getInt("slot", -1);
            if (mainSlot < 0 || mainSlot >= mainSize) {
                plugin.getLogger().warning("Invalid main slot for category " + id + ": " + mainSlot);
            } else if (!mainSlots.add(mainSlot)) {
                plugin.getLogger().warning(plugin.msg("duplicate-slot").replace("{slot}", String.valueOf(mainSlot)));
            }

            categories.put(id, new Category(id, c.getString("display-name", id),
                    material(c.getString("material"), Material.CHEST), mainSlot,
                    c.getStringList("lore"), items));
        }

        validateFixedMainSlots();
    }

    private void validateFixedMainSlots() {
        Map<String, Integer> expected = Map.of("end", 11, "nether", 12, "gear", 13, "food", 14, "shard_shop", 15);
        for (Map.Entry<String, Integer> e : expected.entrySet()) {
            Category c = categories.get(e.getKey());
            if (c == null) plugin.getLogger().warning("Missing required category: " + e.getKey());
            else if (c.slot() != e.getValue()) plugin.getLogger().warning(
                    "Category " + e.getKey() + " must use main slot " + e.getValue() + ", found " + c.slot());
        }
    }

    public Collection<Category> categories() { return categories.values(); }
    public Category category(String id) { return id == null ? null : categories.get(id.toLowerCase(Locale.ROOT)); }
    public ItemDef item(String category, String id) {
        Category c = category(category);
        return c == null || id == null ? null : c.items().get(id.toLowerCase(Locale.ROOT));
    }

    public void openMain(Player p) {
        int size = normalizedSize(plugin.getConfig().getInt("settings.main-size", 27));
        Inventory inv = Bukkit.createInventory(null, size,
                color(plugin.getConfig().getString("settings.main-title", "&8Shop")));
        for (Category c : categories.values()) {
            if (c.slot() >= 0 && c.slot() < size)
                inv.setItem(c.slot(), icon(c.material(), c.name(), c.lore()));
        }
        p.openInventory(inv);
        sound(p, "open");
    }

    public void openCategory(Player p, String id) {
        Category c = category(id);
        if (c == null) { p.sendMessage(plugin.msg("invalid-category")); return; }

        int size = normalizedSize(plugin.getConfig().getInt("settings.category-size", 27));
        String title = plugin.getConfig().getString("settings.category-title-format", "&8{category} Shop")
                .replace("{category}", ChatText.strip(c.name()));
        Inventory inv = Bukkit.createInventory(null, size, color(title));

        for (ItemDef d : c.items().values()) {
            if (!d.enabled()) continue;
            inv.setItem(d.slot(), icon(d.material(), d.name(), loreFor(d)));
        }

        int backSlot = plugin.getConfig().getInt("settings.back-slot", 22);
        if (backSlot >= 0 && backSlot < size)
            inv.setItem(backSlot, icon(Material.ARROW, "&cBack", List.of("&7Return to the main shop")));

        p.openInventory(inv);
        sound(p, "category");
    }

    public void openPurchase(Player p, ItemDef d, int quantity) {
        if (!d.enabled()) { p.sendMessage(plugin.msg("item-disabled")); return; }
        int max = Math.max(1, plugin.getConfig().getInt("settings.max-quantity", 64));
        quantity = Math.max(1, Math.min(quantity, max));
        pending.put(p.getUniqueId(), new Pending(d, quantity));

        int size = normalizedSize(plugin.getConfig().getInt("settings.purchase-size", 27));
        String title = plugin.getConfig().getString("settings.purchase-title", "&8Purchase: {item}")
                .replace("{item}", ChatText.strip(d.name()));
        Inventory inv = Bukkit.createInventory(null, size, color(title));

        double total = d.price() * quantity;
        inv.setItem(13, icon(d.material(), d.name(), List.of(
                "&7Unit price: &f" + money(d.price(), d.currency()),
                "&7Quantity: &f" + quantity,
                "&7Total: &f" + money(total, d.currency()),
                "&7Balance: &f" + balanceText(p, d.currency())
        )));
        inv.setItem(11, icon(Material.RED_DYE, "&c-1", List.of("&7Decrease quantity")));
        inv.setItem(15, icon(Material.LIME_DYE, "&a+1", List.of("&7Increase quantity")));
        inv.setItem(21, icon(Material.GREEN_WOOL, "&aConfirm", List.of("&7Purchase now")));
        inv.setItem(23, icon(Material.RED_WOOL, "&cCancel", List.of("&7Return to shop")));

        p.openInventory(inv);
        sound(p, "purchase");
    }

    public Pending pending(Player p) { return pending.get(p.getUniqueId()); }
    public void clearPending(Player p) { pending.remove(p.getUniqueId()); }

    private List<String> loreFor(ItemDef d) {
        List<String> l = new ArrayList<>(d.lore());
        l.add("&7Price: &f" + money(d.price(), d.currency()));
        l.add("&7Per purchase: &f" + d.amount() + " item(s)");
        l.add("&eClick to purchase");
        return l;
    }

    private String balanceText(Player p, String currency) {
        if ("MONEY".equalsIgnoreCase(currency)) {
            double value = 0;
            return value < 0 ? "&cUnavailable" : money(value, currency);
        }
        if ("SHARDS".equalsIgnoreCase(currency)) {
            long value = shards.balance(p.getUniqueId());
            return value < 0 ? "&cUnavailable" : money(value, currency);
        }
        return "&cUnavailable";
    }

    private String money(double n, String currency) {
        String symbol = plugin.getConfig().getString("settings.currency-symbol", "$");
        if ("MONEY".equalsIgnoreCase(currency)) return symbol + trim(n);
        return trim(n) + " " + plugin.getConfig().getString("settings.shard-currency-name", currency);
    }

    private String trim(double n) {
        return Math.rint(n) == n ? String.format(Locale.US, "%,.0f", n) : String.format(Locale.US, "%,.2f", n);
    }

    private ItemStack icon(Material m, String name, List<String> lore) {
        ItemStack i = new ItemStack(m);
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(color(name));
        meta.setLore(lore.stream().map(ZivexShopPlugin::color).toList());
        i.setItemMeta(meta);
        return i;
    }

    private Material material(String s, Material fallback) {
        if (s == null) return fallback;
        try { return Material.valueOf(s.toUpperCase(Locale.ROOT)); }
        catch (Exception e) { plugin.getLogger().warning("Unknown material: " + s); return fallback; }
    }

    public boolean purchase(Player p, Pending pd) {
        UUID id = p.getUniqueId();
        if (!processing.add(id)) {
            p.sendMessage(plugin.msg("purchase-processing"));
            return false;
        }

        try {
            ItemDef d = pd.item();
            int q = pd.quantity();
            long deliveryAmount;
            try { deliveryAmount = Math.multiplyExact((long) q, (long) d.amount()); }
            catch (ArithmeticException ex) { p.sendMessage(plugin.msg("invalid-amount")); return false; }

            double total = d.price() * q;
            if (!Double.isFinite(total) || total < 0) { p.sendMessage(plugin.msg("invalid-price")); return false; }

            if ("VANILLA".equalsIgnoreCase(d.delivery()) && !canFit(p, createDeliveryItem(d), deliveryAmount)) {
                p.sendMessage(plugin.msg("inventory-full"));
                sound(p, "fail");
                return false;
            }

            boolean charged;
            if ("MONEY".equalsIgnoreCase(d.currency())) {
                double balance = 0;
                if (balance < 0) { p.sendMessage(plugin.msg("no-economy")); return false; }
                charged = balance >= total;
            } else if ("SHARDS".equalsIgnoreCase(d.currency())) {
                long shardPrice;
                try { shardPrice = Math.multiplyExact(Math.round(d.price()), (long) q); }
                catch (ArithmeticException ex) { p.sendMessage(plugin.msg("invalid-shard-price")); return false; }
                if (d.price() != Math.rint(d.price()) || shardPrice < 0) {
                    p.sendMessage(plugin.msg("invalid-shard-price")); return false;
                }
                long balance = shards.balance(id);
                if (balance < 0) { p.sendMessage(plugin.msg("no-shard-economy")); return false; }
                charged = balance >= shardPrice && shards.withdraw(id, shardPrice);
            } else {
                p.sendMessage(plugin.msg("unsupported-currency").replace("{currency}", d.currency()));
                return false;
            }

            if (!charged) {
                p.sendMessage(plugin.msg("insufficient").replace("{price}", money(total, d.currency()))
                        .replace("{currency}", d.currency()));
                sound(p, "fail");
                return false;
            }

            boolean delivered = deliver(p, d, (int) deliveryAmount);
            if (!delivered) {
                refund(p, d.currency(), total, d, q);
                p.sendMessage(plugin.msg("delivery-failed"));
                sound(p, "fail");
                return false;
            }

            p.sendMessage(plugin.msg("purchased")
                    .replace("{amount}", String.valueOf(deliveryAmount))
                    .replace("{item}", ChatText.strip(d.name()))
                    .replace("{total}", money(total, d.currency()))
                    .replace("{currency}", d.currency()));
            sound(p, "success");
            return true;
        } finally {
            processing.remove(id);
        }
    }

    private void refund(Player p, String currency, double total, ItemDef d, int quantity) {
        if (!plugin.getConfig().getBoolean("settings.refund-on-delivery-failure", true)) return;
        if ("MONEY".equalsIgnoreCase(currency)) {
            // Money delivery is handled by the server economy integration.
        } else if ("SHARDS".equalsIgnoreCase(currency)) {
            long amount = Math.round(d.price() * quantity);
            if (!shards.deposit(p.getUniqueId(), amount))
                plugin.getLogger().severe("CRITICAL: failed to refund " + amount + " shards to " + p.getName());
        }
    }

    private ItemStack createDeliveryItem(ItemDef d) {
        ItemStack stack = new ItemStack(d.material(), 1);
        if ("slow_falling_arrow".equalsIgnoreCase(d.id()) || "Arrow of Slow Falling".equalsIgnoreCase(ChatText.strip(d.name()))) {
            if (stack.getItemMeta() instanceof PotionMeta meta) {
                meta.setBasePotionType(org.bukkit.potion.PotionType.LONG_SLOW_FALLING);
                stack.setItemMeta(meta);
            }
        }
        return stack;
    }

    private boolean canFit(Player p, ItemStack template, long amount) {
        if (amount <= 0) return false;
        long free = 0;
        int maxStack = Math.max(1, template.getMaxStackSize());
        for (ItemStack current : p.getInventory().getStorageContents()) {
            if (current == null || current.getType().isAir()) free += maxStack;
            else if (current.isSimilar(template)) free += Math.max(0, maxStack - current.getAmount());
            if (free >= amount) return true;
        }
        return false;
    }

    private boolean deliver(Player p, ItemDef d, int amount) {
        if ("VANILLA".equalsIgnoreCase(d.delivery())) {
            ItemStack stack = createDeliveryItem(d);
            int max = Math.max(1, stack.getMaxStackSize());
            int left = amount;
            while (left > 0) {
                int n = Math.min(left, max);
                ItemStack part = stack.clone();
                part.setAmount(n);
                if (!p.getInventory().addItem(part).isEmpty()) return false;
                left -= n;
            }
            return true;
        }

        if ("CRATE_KEY".equalsIgnoreCase(d.delivery())) {
            if (!plugin.getConfig().getBoolean("integrations.phoenix-crate-lite.enabled", true)) return false;
            return dispatch(plugin.getConfig().getString("integrations.phoenix-crate-lite.key-command",
                    "/crates giveKey {key} {player} {amount}"), p, d.target(), amount);
        }

        if ("SPAWNER".equalsIgnoreCase(d.delivery())) {
            if (!plugin.getConfig().getBoolean("integrations.smart-spawners.enabled", true)) return false;
            return dispatch(plugin.getConfig().getString("integrations.smart-spawners.spawner-command",
                    "/ss give {player} smart_spawner {type} {amount}"), p, d.target(), amount);
        }

        if ("COMMAND".equalsIgnoreCase(d.delivery())) return dispatch(d.target(), p, d.target(), amount);
        return false;
    }

    private boolean dispatch(String raw, Player p, String target, int amount) {
        if (raw == null || raw.isBlank()) return false;
        String cmd = raw.trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        cmd = cmd.replace("{player}", p.getName()).replace("{key}", target)
                .replace("{type}", target).replace("{amount}", String.valueOf(amount));
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    private void sound(Player p, String key) {
        if (!plugin.getConfig().getBoolean("settings.sounds", true)) return;
        String raw = plugin.getConfig().getString("sounds." + key + ".sound", "");
        if (raw == null || raw.isBlank()) return;
        try {
            Sound sound = Sound.valueOf(raw.toUpperCase(Locale.ROOT));
            p.playSound(p.getLocation(), sound,
                    (float) plugin.getConfig().getDouble("sounds." + key + ".volume", 1),
                    (float) plugin.getConfig().getDouble("sounds." + key + ".pitch", 1));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown configured sound for " + key + ": " + raw);
        }
    }

    private int normalizedSize(int size) {
        if (size < 9) return 9;
        if (size > 54) return 54;
        return size - (size % 9);
    }

    public boolean setPrice(String cat, String id, double price) {
        if (item(cat, id) == null || !Double.isFinite(price) || price < 0) return false;
        plugin.getConfig().set("categories." + cat + ".items." + id + ".price", price);
        plugin.saveConfig(); reload(); return true;
    }

    public boolean setSlot(String cat, String id, int slot) {
        int size = normalizedSize(plugin.getConfig().getInt("settings.category-size", 27));
        Category c = category(cat);
        if (c == null || item(cat, id) == null || slot < 0 || slot >= size) return false;
        for (ItemDef other : c.items().values())
            if (!other.id().equalsIgnoreCase(id) && other.enabled() && other.slot() == slot) return false;
        plugin.getConfig().set("categories." + cat + ".items." + id + ".slot", slot);
        plugin.saveConfig(); reload(); return true;
    }

    public boolean setEnabled(String cat, String id, boolean enabled) {
        if (item(cat, id) == null) return false;
        plugin.getConfig().set("categories." + cat + ".items." + id + ".enabled", enabled);
        plugin.saveConfig(); reload(); return true;
    }

    public boolean give(Player target, ItemDef d, int quantity) {
        if (quantity < 1) return false;
        long amount;
        try { amount = Math.multiplyExact((long) quantity, d.amount()); } catch (ArithmeticException e) { return false; }
        if ("VANILLA".equalsIgnoreCase(d.delivery()) && !canFit(target, createDeliveryItem(d), amount)) return false;
        return deliver(target, d, (int) amount);
    }

    static String color(String s) { return ZivexShopPlugin.color(s); }

    static final class ChatText {
        static String strip(String s) { return org.bukkit.ChatColor.stripColor(ZivexShopPlugin.color(s)); }
    }
}
