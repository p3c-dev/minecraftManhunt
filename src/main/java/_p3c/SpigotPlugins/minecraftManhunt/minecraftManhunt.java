package _p3c.SpigotPlugins.minecraftManhunt;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class minecraftManhunt extends JavaPlugin implements Listener {

    private boolean resetOnNoTrack = true;

    private UUID speedrunner = null; // only one allowed
    private final Set<UUID> hunters = new HashSet<>();
    private final Map<UUID, Location> lastKnown = new HashMap<>(); // per-hunter last update
    private NamespacedKey compassKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        resetOnNoTrack = getConfig().getBoolean("hunter-compass-reset-when-no-players-to-track", true);
        compassKey = new NamespacedKey(this, "manhunt_compass");
        getServer().getPluginManager().registerEvents(this, this);
        // register commands
        Objects.requireNonNull(getCommand("speedrunner")).setExecutor((sender, cmd, label, args) -> handleSpeedrunnerCommand(sender, args));
        Objects.requireNonNull(getCommand("hunter")).setExecutor((sender, cmd, label, args) -> handleHunterCommand(sender, args));
        Objects.requireNonNull(getCommand("compass")).setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }
            Player p = (Player) sender;
            if (!hunters.contains(p.getUniqueId())) {
                p.sendMessage(colorMessage(ChatColor.RED, "Only hunters can use this command!"));
                return true;
            }
            if (speedrunner == null) {
                p.sendMessage(colorMessage(ChatColor.RED, "No players to track!"));
                return true;
            }
            Player target = Bukkit.getPlayer(speedrunner);
            if (target == null) {
                p.sendMessage(colorMessage(ChatColor.RED, "No players to track!"));
                return true;
            }
            p.sendMessage(colorMessage(ChatColor.AQUA, "Compass pointing to " + target.getName()));
            return true;
        });

        getLogger().info("ManhuntCompass plugin enabled");
    }

    @Override
    public void onDisable() {
        getLogger().info("ManhuntCompass plugin disabled");
    }

    /* ---------- Commands ---------- */
    private boolean handleSpeedrunnerCommand(org.bukkit.command.CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(colorMessage(ChatColor.YELLOW, "Usage: /speedrunner add <playername>, or /speedrunner remove <playername>"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String name = args[1];
        if (sub.equals("add")) {
            if (speedrunner != null) {
                sender.sendMessage(colorMessage(ChatColor.RED, "A speedrunner is already set!"));
                return true;
            }
            Player p = Bukkit.getPlayerExact(name);
            if (p == null) {
                sender.sendMessage(colorMessage(ChatColor.RED, "Invalid player!"));
                return true;
            }
            if (hunters.contains(p.getUniqueId())) {
                sender.sendMessage(colorMessage(ChatColor.RED, "Speedrunner and hunter cannot be the same!"));
                return true;
            }
            speedrunner = p.getUniqueId();
            sender.sendMessage(colorMessage(ChatColor.GREEN, "Speedrunner set to " + p.getName()));
            return true;
        } else if (sub.equals("remove")) {
            Player p = Bukkit.getPlayerExact(name);
            if (p == null || speedrunner == null || !speedrunner.equals(p.getUniqueId())) {
                sender.sendMessage(colorMessage(ChatColor.RED, "Invalid player!"));
                return true;
            }
            speedrunner = null;
            sender.sendMessage(colorMessage(ChatColor.GREEN, "Speedrunner removed."));
            return true;
        }
        sender.sendMessage(colorMessage(ChatColor.YELLOW, "Usage: /speedrunner add <playername>, or /speedrunner remove <playername>"));
        return true;
    }

    private boolean handleHunterCommand(org.bukkit.command.CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(colorMessage(ChatColor.YELLOW, "Usage: /hunter <playername>"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String name = args[1];
        if (sub.equals("add")) {
            Player p = Bukkit.getPlayerExact(name);
            if (p == null) {
                sender.sendMessage(colorMessage(ChatColor.RED, "Invalid player!"));
                return true;
            }
            if (speedrunner != null && speedrunner.equals(p.getUniqueId())) {
                sender.sendMessage(colorMessage(ChatColor.RED, "Speedrunner and hunter cannot be the same!"));
                return true;
            }
            hunters.add(p.getUniqueId());
            giveHunterCompass(p);
            sender.sendMessage(colorMessage(ChatColor.GREEN, "Hunter set to " + p.getName()));
            return true;
        } else if (sub.equals("remove")) {
            Player p = Bukkit.getPlayerExact(name);
            if (p == null) {
                sender.sendMessage(colorMessage(ChatColor.RED, "Invalid player!"));
                return true;
            }
            hunters.remove(p.getUniqueId());
            lastKnown.remove(p.getUniqueId());
            sender.sendMessage(colorMessage(ChatColor.GREEN, "Hunter removed: " + p.getName()));
            return true;
        }
        sender.sendMessage(colorMessage(ChatColor.YELLOW, "Usage: /hunter <playername>"));
        return true;
    }

    /* ---------- Events ---------- */

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // right click with our compass
        if (event.getHand() == EquipmentSlot.OFF_HAND) return; // only main hand
        ItemStack item = event.getItem();
        if (!isManhuntCompass(item)) return;

        Player player = event.getPlayer();

        // speedrunner cannot use this
        if (speedrunner != null && speedrunner.equals(player.getUniqueId())) {
            player.sendMessage(colorMessage(ChatColor.RED, "You are a speedrunner, you can't use this!"));
            event.setCancelled(true);
            return;
        }

        if (!hunters.contains(player.getUniqueId())) {
            // Not a registered hunter — do nothing special
            return;
        }

        // only treat right-click actions
        switch (event.getAction()) {
            case RIGHT_CLICK_AIR:
            case RIGHT_CLICK_BLOCK:
                // If there is no speedrunner set or speedrunner is offline
                if (speedrunner == null) {
                    player.sendMessage(colorMessage(ChatColor.RED, "No players to track!"));
                    if (resetOnNoTrack) {
                        CompassMeta cm = (CompassMeta) item.getItemMeta();
                        cm.setLodestone(null);
                        cm.setLodestoneTracked(false);
                        item.setItemMeta(cm);
                    }
                    event.setCancelled(true);
                    return;
                }
                Player target = Bukkit.getPlayer(speedrunner);
                if (target == null) {
                    player.sendMessage(colorMessage(ChatColor.RED, "No players to track!"));
                    if (resetOnNoTrack) {
                        CompassMeta cm = (CompassMeta) item.getItemMeta();
                        cm.setLodestone(null);
                        cm.setLodestoneTracked(false);
                        item.setItemMeta(cm);
                    }
                    event.setCancelled(true);
                    return;
                }

                // If target is in different world/dimension -> do not update and show no players to track
                World hunterWorld = player.getWorld();
                World targetWorld = target.getWorld();
                if (!hunterWorld.getEnvironment().equals(targetWorld.getEnvironment())) {
                    // different dimension
                    player.sendMessage(colorMessage(ChatColor.RED, "No players to track!"));
                    if (resetOnNoTrack) {
                        CompassMeta cm = (CompassMeta) item.getItemMeta();
                        cm.setLodestone(null);
                        cm.setLodestoneTracked(false);
                        item.setItemMeta(cm);
                    }
                    event.setCancelled(true);
                    return;
                }

                // same dimension and online -> update compass to target's current location
                Location loc = target.getLocation().clone();
                lastKnown.put(player.getUniqueId(), loc);

                // set the compass lodestone so it points to this location
                CompassMeta cm = (CompassMeta) item.getItemMeta();
                cm.setLodestone(loc);
                cm.setLodestoneTracked(false); // track by lodestone, not by world spawn
                item.setItemMeta(cm);

                player.sendMessage(colorMessage(ChatColor.GREEN, "Tracking " + target.getName() + "'s last known location."));
                event.setCancelled(true);
                return;
            default:
                return;
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Remove our compass from drops so it won't be dropped (and also curse of vanishing helps)
        event.getDrops().removeIf(this::isManhuntCompass);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        if (hunters.contains(p.getUniqueId())) {
            // give compass after a tick to ensure inventory is ready
            Bukkit.getScheduler().runTaskLater(this, () -> giveHunterCompass(p), 1L);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (hunters.contains(p.getUniqueId())) {
            // give the compass if player logs in and is a hunter
            Bukkit.getScheduler().runTaskLater(this, () -> giveHunterCompass(p), 10L);
        }
    }

    /* ---------- Utility methods ---------- */
    private void giveHunterCompass(Player p) {
        ItemStack c = createManhuntCompass();
        p.getInventory().addItem(c);
    }

    private ItemStack createManhuntCompass() {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        if (meta == null) return compass;
        meta.setDisplayName(ChatColor.AQUA + "Hunter Compass");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Right click to update last known location.");
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        // mark item with persistent data so we can detect it reliably
        meta.getPersistentDataContainer().set(compassKey, PersistentDataType.BYTE, (byte) 1);
        // Curse of vanishing to prevent it from being dropped on death (user requested)
        meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        compass.setItemMeta(meta);
        return compass;
    }

    private boolean isManhuntCompass(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != Material.COMPASS) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Byte val = meta.getPersistentDataContainer().get(compassKey, PersistentDataType.BYTE);
        return val != null && val == (byte) 1;
    }

    private String colorMessage(ChatColor color, String message) {
        return color + message;
    }
}
