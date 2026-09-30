package me.voidflame.zivexshop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public final class ShopManager {
    public record ItemDef(String category, String id, Material material, String name, int slot, double price,
                          String currency, String delivery, int amount, String target, List<String> lore) {}
    public record Category(String id, String name, Material material, int slot, List<String> lore,
                           LinkedHashMap<String, ItemDef> items) {}
    public record Pending(ItemDef item, int quantity) {}

    private final ZivexShopPlugin plugin;
    private final CoreEconomy economy;\n    private final ShardEconomy shards = new ShardEconomy();
    private final LinkedHashMap<String, Category> categories = new LinkedHashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();

    public ShopManager(ZivexShopPlugin plugin, CoreEconomy economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    public void reload() {
        categories.clear();

        ConfigurationSection root = plugin.getConfig().getConfigurationSection("categories");
        if (root == null) {
            plugin.getLogger().severe("Missing categories section.");
            return;
        }

        int categorySize = normalizedSize(plugin.getConfig().getInt("settings.category-size", 27));
        Set<Integer> mainSlots = new HashSet<>();

        for (String rawId : root.getKeys(false)) {
            String id = rawId.toLowerCase(Locale.ROOT);
            ConfigurationSection c = root.getConfigurationSection(rawId);
            if (c == null) continue;

            LinkedHashMap<String, ItemDef> items = new LinkedHashMap<>();
            ConfigurationSection is = c.getConfigurationSection("items");

            if (is != null) {
                for (String rawItemId : is.getKeys(false)) {
                    String itemId = rawItemId.toLowerCase(Locale.ROOT);
                    ConfigurationSection x = is.getConfigurationSection(rawItemId);
                    if (x == null) continue;

                    int slot = x.getInt("slot", -1);
                    if (slot < 0 || slot >= categorySize) {
                        plugin.getLogger().warning("Invalid slot for " + id + "/" + itemId + ": " + slot);
                        continue;
                    }

                    double price = x.getDouble("price", -1);
                    if (!Double.isFinite(price) || price < 0) {
                        plugin.getLogger().warning("Invalid price for " + id + "/" + itemId + ": " + price);
                        continue;
                    }

                    ItemDef d = new ItemDef(
                            id,
                            itemId,
                            material(x.getString("material"), Material.STONE),
                            x.getString("name", itemId),
                            slot,
                            price,
                            x.getString("currency", "MONEY"),
                            x.getString("delivery", "VANILLA"),
                            Math.max(1, x.getInt("amount", 1)),
                            x.getString("target", itemId),
                            x.getStringList("lore")
                    );

                    items.put(itemId, d);
                }
            }

            int mainSlot = c.getInt("slot", -1);
            if (mainSlot < 0 || mainSlot >= normalizedSize(plugin.getConfig().getInt("settings.main-size", 27))) {
                plugin.getLogger().warning("Invalid main slot for category " + id + ": " + mainSlot);
            } else if (!mainSlots.add(mainSlot)) {
                plugin.getLogger().warning(plugin.msg("duplicate-slot").replace("{slot}", String.valueOf(mainSlot)));
            }

            Category cat = new Category(
                    id,
                    c.getString("display-name", id),
                    material(c.getString("material"), Material.CHEST),
                    mainSlot,
                    c.getStringList("lore"),
                    items
            );
            categories.put(id, cat);
        }

        validateFixedMainSlots();
    }

    private void validateFixedMainSlots() {
        Map<String, Integer> expected = Map.of(
                "end", 11,
                "nether", 12,
                "gear", 13,
                "food", 14,
                "shard_shop", 15
        );
        for (Map.Entry<String, Integer> e : expected.entrySet()) {
            Category c = categories.get(e.getKey());
            if (c == null) {
                plugin.getLogger().warning("Missing required category: " + e.getKey());
            } else if (c.slot() != e.getValue()) {
                plugin.getLogger().warning("Category " + e.getKey() + " must use main slot " + e.getValue() + ", found " + c.slot());
            }
        }
    }

    public Collection<Category> categories() {
        return categories.values();
    }

    public Category category(String id) {
        return id == null ? null : categories.get(id.toLowerCase(Locale.ROOT));
    }

    public ItemDef item(String category, String id) {
        Category c = category(category);
        return c == null || id == null ? null : c.items().get(id.toLowerCase(Locale.ROOT));
    }

    public void openMain(Player p) {
        int size = normalizedSize(plugin.getConfig().getInt("settings.main-size", 27));
        Inventory inv = Bukkit.createInventory(null, size,
                ZivexShopPlugin.color(plugin.getConfig().getString("settings.main-title", "&8Shop")));

        for (Category c : categories.values()) {
            if (c.slot() >= 0 && c.slot() < size) {
                inv.setItem(c.slot(), icon(c.material(), c.name(), c.lore()));
            }
        }

        p.openInventory(inv);
        sound(p, "open");
    }

    public void openCategory(Player p, String id) {
        Category c = category(id);
        if (c == null) {
            p.sendMessage(plugin.msg("invalid-category"));
            return;
        }

        int size = normalizedSize(plugin.getConfig().getInt("settings.category-size", 27));
        String title = plugin.getConfig().getString("settings.category-title-format", "&8{category} Shop")
                .replace("{category}", ChatText.strip(c.name()));

        Inventory inv = Bukkit.createInventory(null, size, ZivexShopPlugin.color(title));

        Set<Integer> used = new HashSet<>();
        for (ItemDef d : c.items().values()) {
            if (!used.add(d.slot())) {
                plugin.getLogger().warning("Duplicate item slot in " + c.id() + ": " + d.slot());
                continue;
            }
            inv.setItem(d.slot(), icon(d.material(), d.name(), loreFor(d)));
        }

        int backSlot = plugin.getConfig().getInt("settings.back-slot", 22);
        if (backSlot >= 0 && backSlot < size && !used.contains(backSlot)) {
            inv.setItem(backSlot, icon(Material.ARROW, "&cBack", List.of("&7Return to the main shop")));
        }

        p.openInventory(inv);
        sound(p, "category");
    }

    public void openPurchase(Player p, ItemDef d, int quantity) {
        int max = Math.max(1, plugin.getConfig().getInt("settings.max-quantity", 64));
        quantity = Math.max(1, Math.min(quantity, max));
        pending.put(p.getUniqueId(), new Pending(d, quantity));

        int size = normalizedSize(plugin.getConfig().getInt("settings.purchase-size", 27));
        String title = plugin.getConfig().getString("settings.purchase-title", "&8Purchase: {item}")
                .replace("{item}", ChatText.strip(d.name()));

        Inventory inv = Bukkit.createInventory(null, size, ZivexShopPlugin.color(title));

        inv.setItem(13, icon(d.material(), d.name(), List.of(
                "&7Unit price: &f" + money(d.price(), d.currency()),
                "&7Quantity: &f" + quantity,
                "&7Total: &f" + money(d.price() * quantity, d.currency())
        )));

        inv.setItem(11, icon(Material.RED_DYE, "&c-1", List.of("&7Decrease quantity")));
        inv.setItem(15, icon(Material.LIME_DYE, "&a+1", List.of("&7Increase quantity")));
        inv.setItem(21, icon(Material.GREEN_WOOL, "&aConfirm", List.of("&7Purchase now")));
        inv.setItem(23, icon(Material.RED_WOOL, "&cCancel", List.of("&7Return to shop")));

        p.openInventory(inv);
        sound(p, "purchase");
    }

    public Pending pending(Player p) {
        return pending.get(p.getUniqueId());
    }

    public void clearPending(Player p) {
        pending.remove(p.getUniqueId());
    }

    private List<String> loreFor(ItemDef d) {
        List<String> l = new ArrayList<>(d.lore());
        l.add("&7Price: &f" + money(d.price(), d.currency()));
        l.add("&eClick to purchase");
        return l;
    }

    private String money(double n, String currency) {
        String symbol = plugin.getConfig().getString("settings.currency-symbol", "$");
        if ("MONEY".equalsIgnoreCase(currency)) return symbol + trim(n);
        return trim(n) + " " + plugin.getConfig().getString("settings.shard-currency-name", currency);
    }

    private String trim(double n) {
        return Math.rint(n) == n
                ? String.format(Locale.US, "%,.0f", n)
                : String.format(Locale.US, "%,.2f", n);
    }

    private ItemStack icon(Material m, String name, List<String> lore) {
        ItemStack i = new ItemStack(m);
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(ZivexShopPlugin.color(name));
        meta.setLore(lore.stream().map(ZivexShopPlugin::color).toList());
        i.setItemMeta(meta);
        return i;
    }

    private Material material(String s, Material fallback) {
        if (s == null) return fallback;
        try {
            return Material.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            plugin.getLogger().warning("Unknown material: " + s);
            return fallback;
        }
    }

    public boolean purchase(Player p, Pending pd) {
        ItemDef d = pd.item();
        int q = pd.quantity();
        double total = d.price() * q;

        if (total < 0 || !Double.isFinite(total)) return false;

        if ("MONEY".equalsIgnoreCase(d.currency())) {
            double balance = economy.balance(p.getUniqueId());
            if (balance < 0) {
                p.sendMessage(plugin.msg("no-economy"));
                return false;
            }
            if (balance < total || !economy.withdraw(p.getUniqueId(), total)) {
                p.sendMessage(plugin.msg("insufficient")
                        .replace("{price}", money(total, d.currency()))
                        .replace("{currency}", "money"));
                sound(p, "fail");
                return false;
            }
        } else if ("SHARDS".equalsIgnoreCase(d.currency())) {
            p.sendMessage(plugin.msg("no-shard-economy"));
            sound(p, "fail");
            return false;
        } else {
            p.sendMessage(plugin.msg("unsupported-currency").replace("{currency}", d.currency()));
            sound(p, "fail");
            return false;
        }

        boolean delivered = deliver(p, d, q);
        if (!delivered) {
            if ("MONEY".equalsIgnoreCase(d.currency())
                    && plugin.getConfig().getBoolean("settings.refund-on-delivery-failure", true)) {
                economy.deposit(p.getUniqueId(), total);
            }
            p.sendMessage(plugin.msg("delivery-failed"));
            sound(p, "fail");
            return false;
        }

        p.sendMessage(plugin.msg("purchased")
                .replace("{amount}", String.valueOf(q * d.amount()))
                .replace("{item}", ChatText.strip(d.name()))
                .replace("{total}", money(total, d.currency()))
                .replace("{currency}", d.currency()));

        sound(p, "success");
        return true;
    }

    private boolean deliver(Player p, ItemDef d, int q) {
        if ("VANILLA".equalsIgnoreCase(d.delivery())) {
            int left = q * d.amount();
            ItemStack stack = new ItemStack(d.material());

            while (left > 0) {
                int n = Math.min(left, stack.getMaxStackSize());
                ItemStack part = stack.clone();
                part.setAmount(n);
                Map<Integer, ItemStack> overflow = p.getInventory().addItem(part);
                if (!overflow.isEmpty()) {
                    for (ItemStack item : overflow.values()) {
                        p.getWorld().dropItemNaturally(p.getLocation(), item);
                    }
                }
                left -= n;
            }
            return true;
        }

        if ("CRATE_KEY".equalsIgnoreCase(d.delivery())) {
            if (!plugin.getConfig().getBoolean("integrations.phoenix-crate-lite.enabled", true)) return false;
            return dispatch(
                    plugin.getConfig().getString("integrations.phoenix-crate-lite.key-command", "/crates giveKey {key} {player} {amount}"),
                    p, d.target(), q
            );
        }

        if ("SPAWNER".equalsIgnoreCase(d.delivery())) {
            if (!plugin.getConfig().getBoolean("integrations.smart-spawners.enabled", true)) return false;
            return dispatch(
                    plugin.getConfig().getString("integrations.smart-spawners.spawner-command", "/ss give {player} smart_spawner {type} {amount}"),
                    p, d.target(), q
            );
        }

        if ("COMMAND".equalsIgnoreCase(d.delivery())) {
            return dispatch(d.target(), p, d.target(), q);
        }

        return false;
    }

    private boolean dispatch(String raw, Player p, String target, int amount) {
        if (raw == null || raw.isBlank()) return false;

        String cmd = raw.trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);

        cmd = cmd.replace("{player}", p.getName())
                .replace("{key}", target)
                .replace("{type}", target)
                .replace("{amount}", String.valueOf(amount));

        ConsoleCommandSender console = Bukkit.getConsoleSender();
        return Bukkit.dispatchCommand(console, cmd);
    }

    private void sound(Player p, String key) {
        if (!plugin.getConfig().getBoolean("settings.sounds", true)) return;
        String raw = plugin.getConfig().getString("sounds." + key + ".sound", "");
        if (raw == null || raw.isBlank()) return;
        try {
            Sound sound = Sound.valueOf(raw.toUpperCase(Locale.ROOT));
            float volume = (float) plugin.getConfig().getDouble("sounds." + key + ".volume", 1.0);
            float pitch = (float) plugin.getConfig().getDouble("sounds." + key + ".pitch", 1.0);
            p.playSound(p.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
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
        plugin.saveConfig();
        reload();
        return true;
    }

    public boolean setSlot(String cat, String id, int slot) {
        int size = normalizedSize(plugin.getConfig().getInt("settings.category-size", 27));
        if (item(cat, id) == null || slot < 0 || slot >= size) return false;
        plugin.getConfig().set("categories." + cat + ".items." + id + ".slot", slot);
        plugin.saveConfig();
        reload();
        return true;
    }

    public boolean give(Player target, ItemDef d, int amount) {
        return deliver(target, d, amount);
    }

    static final class ChatText {
        static String strip(String s) {
            return org.bukkit.ChatColor.stripColor(ZivexShopPlugin.color(s));
        }
    }
}